#!/usr/bin/env bash
#
# add_region.sh — quick-add a new region to the catalog.
#
# Downloads a Geofabrik PBF, runs the pipeline, publishes to manifest. Use this
# when you want to add a new region for the app to download.
#
# Usage:
#   ./bin/add_region.sh --geofabrik russia/central-fed-district --name "Central Russia"
#   ./bin/add_region.sh --pbf /path/to/custom.osm.pbf --name "My Area" --bounds 50,30,55,40
#
# With --geofabrik: the script fetches the PBF from download.geofabrik.de automatically.
#   Format: subpath without extension, e.g. "russia/central-fed-district" → central-fed-district-latest.osm.pbf
#
# The region id is derived from the name (or --id can override).

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GEOFABRIK_SUBPATH=""
PBF=""
NAME=""
ID=""
BOUNDS=""
PROFILE="touring"
VERSION="1"

usage() {
    cat <<'EOF'
Usage: add_region.sh --name NAME [--geofabrik PATH | --pbf FILE --bounds B] [options]

Required:
  --name        Human-readable region name.

Source (one of):
  --geofabrik   Geofabrik subpath, e.g. "russia/central-fed-district"
                (downloads from download.geofabrik.de/<path>-latest.osm.pbf)
  --pbf         Path to a local .osm.pbf file (requires --bounds).

Optional:
  --id          Region identifier (default: derived from name or geofabrik path).
  --bounds      "minLat,minLon,maxLat,maxLon" (required with --pbf; auto-detected from --geofabrik).
  --profile     Routing profile (default: touring).
  --version     Package version (default: 1).

Examples:
  # Central Federal District (Russia)
  ./bin/add_region.sh --geofabrik russia/central-fed-district --name "Central Russia"

  # Custom area from your own PBF
  ./bin/add_region.sh --pbf /data/my-area.osm.pbf --name "My Area" --bounds 55,37,56,38

  # Another Russian federal district
  ./bin/add_region.sh --geofabrik russia/south-fed-district --name "South Russia"
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --geofabrik) GEOFABRIK_SUBPATH="$2"; shift 2 ;;
        --pbf)       PBF="$2"; shift 2 ;;
        --name)      NAME="$2"; shift 2 ;;
        --id)        ID="$2"; shift 2 ;;
        --bounds)    BOUNDS="$2"; shift 2 ;;
        --profile)   PROFILE="$2"; shift 2 ;;
        --version)   VERSION="$2"; shift 2 ;;
        -h|--help)   usage; exit 0 ;;
        *) echo "Unknown: $1" >&2; usage; exit 3 ;;
    esac
done

[[ -z "$NAME" ]] && { echo "--name is required" >&2; usage; exit 3; }
[[ -z "$GEOFABRIK_SUBPATH" && -z "$PBF" ]] && { echo "Need --geofabrik or --pbf" >&2; usage; exit 3; }

# Derive id from name or geofabrik path.
if [[ -z "$ID" ]]; then
    if [[ -n "$GEOFABRIK_SUBPATH" ]]; then
        ID="$(basename "$GEOFABRIK_SUBPATH")"
    else
        ID="$(echo "$NAME" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g' | sed 's/--*/-/g' | head -c 40)"
    fi
fi

# ─── download PBF if needed ────────────────────────────────────────────────────
if [[ -n "$GEOFABRIK_SUBPATH" ]]; then
    PBF="$ROOT_DIR/pbf/$(basename "$GEOFABRIK_SUBPATH")-latest.osm.pbf"
    if [[ ! -s "$PBF" ]]; then
        URL="https://download.geofabrik.de/${GEOFABRIK_SUBPATH}-latest.osm.pbf"
        echo "=== downloading $URL ==="
        curl --noproxy '*' -L -o "$PBF" "$URL"
    else
        echo "=== PBF already cached: $PBF ==="
    fi
    # Auto-detect bounds from Geofabrik known regions (rough approximations).
    if [[ -z "$BOUNDS" ]]; then
        case "$(basename "$GEOFABRIK_SUBPATH")" in
            central-fed-district)   BOUNDS="52.0,32.0,59.0,49.0" ;;
            northwestern-fed-district) BOUNDS="58.0,19.0,72.0,62.0" ;;
            southern-fed-district)  BOUNDS="43.0,37.0,52.0,50.0" ;;
            north-caucasus-fed-district) BOUNDS="41.0,37.0,47.0,49.0" ;;
            volga-fed-district)     BOUNDS="50.0,38.0,60.0,58.0" ;;
            ural-fed-district)      BOUNDS="50.0,55.0,68.0,73.0" ;;
            siberian-fed-district)  BOUNDS="52.0,72.0,72.0,95.0" ;;
            far-eastern-fed-district) BOUNDS="48.0,135.0,72.0,180.0" ;;
            kaliningrad)            BOUNDS="54.0,19.0,55.5,23.0" ;;
            crimean-fed-district)   BOUNDS="44.0,32.0,46.0,36.0" ;;
            *) echo "WARNING: unknown Geofabrik region — please provide --bounds manually" >&2
               BOUNDS="0,0,0,0" ;;
        esac
    fi
fi

echo ""
echo "=== building region: $ID ($NAME) ==="
exec "$ROOT_DIR/bin/build_region.sh" \
    --id "$ID" \
    --name "$NAME" \
    --pbf "$PBF" \
    --bounds "$BOUNDS" \
    --profile "$PROFILE" \
    --version "$VERSION" \
    --skip-routing
