#!/usr/bin/env bash
#
# build_region.sh — orchestrate the 2circle region pipeline.
#
# Inputs : a Geofabrik .osm.pbf file for the region's area.
# Outputs: a self-contained region-<id>/ directory with tiles.mbtiles, routing.rd5,
#          search.db, manifest.json, and the zipped delivery package.
#
# Each stage is idempotent: if its output already exists and is non-empty, the stage
# is skipped. Re-runs after a failure resume from the last successful stage. This is
# critical for long-running PBF processing — a 10-hour build shouldn't restart from
# zero because the network dropped at the packaging step.
#
# Exit codes:
#   0 — success
#   2 — missing prerequisite (run scripts/check_prerequisites.sh for details)
#   3 — invalid arguments
#   4 — a stage failed; see logs in work/<id>/*.log

set -euo pipefail

# ─── defaults ──────────────────────────────────────────────────────────────────
REGION_ID=""
REGION_NAME=""
PBF=""
BOUNDS=""          # "minLat,minLon,maxLat,maxLon"
PROFILE="touring"  # trekking | fastbike | mtb → maps to BRouter profile
VERSION="1"
SKIP_ROUTING=""
SKIP_SEARCH=""
SKIP_TILES=""
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ─── arg parsing ───────────────────────────────────────────────────────────────
usage() {
    cat <<'EOF'
Usage: build_region.sh --id ID --name NAME --pbf PBF --bounds MINLAT,MINLON,MAXLAT,MAXLON
                       [--profile touring|fastbike|mtb] [--version N]
                       [--skip-routing] [--skip-search] [--skip-tiles]

Required:
  --id        Stable region identifier (matches on-disk dir + manifest id).
  --name      Human-readable region name.
  --pbf       Path to the .osm.pbf source file.
  --bounds    Bounding box "minLat,minLon,maxLat,maxLon" in WGS84 degrees.

Optional:
  --profile   BRouter profile (default: touring).
  --version   Package version (default: 1; bump when rebuilding).
  --skip-*    Skip a stage — useful when iterating on one artefact.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --id)         REGION_ID="$2"; shift 2 ;;
        --name)       REGION_NAME="$2"; shift 2 ;;
        --pbf)        PBF="$2"; shift 2 ;;
        --bounds)     BOUNDS="$2"; shift 2 ;;
        --profile)    PROFILE="$2"; shift 2 ;;
        --version)    VERSION="$2"; shift 2 ;;
        --skip-routing) SKIP_ROUTING=1; shift ;;
        --skip-search)  SKIP_SEARCH=1; shift ;;
        --skip-tiles)   SKIP_TILES=1; shift ;;
        -h|--help)    usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage; exit 3 ;;
    esac
done

# ─── validate ──────────────────────────────────────────────────────────────────
missing=()
[[ -z "$REGION_ID" ]]    && missing+=(--id)
[[ -z "$REGION_NAME" ]]  && missing+=(--name)
[[ -z "$PBF" ]]          && missing+=(--pbf)
[[ -z "$BOUNDS" ]]       && missing+=(--bounds)
if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Missing required arguments: ${missing[*]}" >&2
    usage
    exit 3
fi
if [[ ! -f "$PBF" ]]; then
    echo "PBF not found: $PBF" >&2
    exit 3
fi
if ! [[ "$BOUNDS" =~ ^-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+$ ]]; then
    echo "Bounds must be minLat,minLon,maxLat,maxLon; got: $BOUNDS" >&2
    exit 3
fi

# ─── prerequisites ─────────────────────────────────────────────────────────────
if ! "$ROOT_DIR/scripts/check_prerequisites.sh" --quiet; then
    echo "Prerequisites not met." >&2
    "$ROOT_DIR/scripts/check_prerequisites.sh" || true
    exit 2
fi

# ─── prepare working dirs ──────────────────────────────────────────────────────
WORK="$ROOT_DIR/work/$REGION_ID"
OUT="$ROOT_DIR/regions/$REGION_ID"
mkdir -p "$WORK" "$OUT"
LOG="$WORK/build.log"
exec > >(tee -a "$LOG") 2>&1
echo "=== build_region.sh $(date -u +%FT%TZ) ==="
echo "id=$REGION_ID  name=$REGION_NAME  profile=$PROFILE  version=$VERSION"
echo "pbf=$PBF"
echo "bounds=$BOUNDS"

# Split bounds into individual components for later stages.
IFS=',' read -r MINLAT MINLON MAXLAT MAXLON <<< "$BOUNDS"

# ─── stage 1: tiles.mbtiles ────────────────────────────────────────────────────
TILES="$OUT/tiles.mbtiles"
if [[ -z "$SKIP_TILES" && ! -s "$TILES" ]]; then
    echo "--- stage 1: tiles.mbtiles ---"
    "$ROOT_DIR/scripts/build_tiles.sh" \
        --pbf "$PBF" \
        --out "$TILES" \
        --config "$ROOT_DIR/config/tilemaker.json" \
        --process "$ROOT_DIR/lua/2circle-process.lua"
else
    echo "--- stage 1: tiles.mbtiles (skipped; exists) ---"
fi

# ─── stage 2: routing.rd5 ──────────────────────────────────────────────────────
RD5="$OUT/routing.rd5"
if [[ -z "$SKIP_ROUTING" && ! -s "$RD5" ]]; then
    echo "--- stage 2: routing.rd5 ---"
    "$ROOT_DIR/scripts/build_routing.sh" \
        --pbf "$PBF" \
        --out "$RD5" \
        --profile "$PROFILE" \
        --work "$WORK/brouter"
else
    echo "--- stage 2: routing.rd5 (skipped; exists) ---"
fi

# ─── stage 3: search.db ────────────────────────────────────────────────────────
SEARCH="$OUT/search.db"
if [[ -z "$SKIP_SEARCH" && ! -s "$SEARCH" ]]; then
    echo "--- stage 3: search.db ---"
    "$ROOT_DIR/scripts/build_search_db.py" \
        --pbf "$PBF" \
        --out "$SEARCH" \
        --bounds "$BOUNDS"
else
    echo "--- stage 3: search.db (skipped; exists) ---"
fi

# ─── stage 4: manifest.json + zip ──────────────────────────────────────────────
echo "--- stage 4: manifest.json + zip ---"
"$ROOT_DIR/scripts/build_package.sh" \
    --id "$REGION_ID" \
    --name "$REGION_NAME" \
    --version "$VERSION" \
    --bounds "$BOUNDS" \
    --dir "$OUT"

echo "=== done $(date -u +%FT%TZ) ==="
echo "Output: $OUT"
ls -la "$OUT"
