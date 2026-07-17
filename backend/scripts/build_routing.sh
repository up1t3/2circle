#!/usr/bin/env bash
#
# build_routing.sh — produce routing.rd5 from an OSM PBF via BRouter.
#
# BRouter's `prepare` subcommand reads a PBF and a profile script, slices the planet
# into ~5,000 fixed rd5 tiles, and writes only the tiles that touch the PBF. The
# resulting .rd5 file is the offline routing graph: small (tens of MB per region) and
# already weighted by the profile's cost model — that's what makes the client's
# "honest ETA" honest.
#
# Profile files live in profiles/ as .brf (BRouter's custom expression language). We
# map our enum (touring/fastbike/mtb) to the canonical upstream profiles.

set -euo pipefail

PBF=""
OUT=""
PROFILE="touring"
WORK=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pbf)     PBF="$2"; shift 2 ;;
        --out)     OUT="$2"; shift 2 ;;
        --profile) PROFILE="$2"; shift 2 ;;
        --work)    WORK="$2"; shift 2 ;;
        *) echo "build_routing: unknown arg $1" >&2; exit 3 ;;
    esac
done

for v in PBF OUT PROFILE WORK; do
    [[ -z "${!v:-}" ]] && { echo "build_routing: --${v,,} is required" >&2; exit 3; }
done
[[ -f "$PBF" ]] || { echo "build_routing: PBF not found: $PBF" >&2; exit 3; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE_FILE="$ROOT_DIR/profiles/${PROFILE}.brf"
if [[ ! -f "$PROFILE_FILE" ]]; then
    echo "build_routing: profile not found: $PROFILE_FILE" >&2
    echo "available: $(ls "$ROOT_DIR/profiles/" 2>/dev/null)" >&2
    exit 3
fi

mkdir -p "$WORK" "$(dirname "$OUT")"

# BRouter expects a specific directory layout: segments/ (output) + a profile dir.
# We mirror that under $WORK.
SEGS="$WORK/segments"
PROFILE_DIR="$WORK/profiles"
mkdir -p "$SEGS" "$PROFILE_DIR"
cp "$PROFILE_FILE" "$PROFILE_DIR/"

# Find the BRouter jar. Allow override via BROUTER_JAR env var.
BROUTER_JAR="${BROUTER_JAR:-}"
if [[ -z "$BROUTER_JAR" ]]; then
    # Common locations; expand the list as we encounter more distributions.
    for candidate in \
        /opt/brouter/brouter.jar \
        "$ROOT_DIR/bin/brouter.jar" \
        "$(command -v brouter.jar 2>/dev/null || true)"; do
        if [[ -f "$candidate" ]]; then
            BROUTER_JAR="$candidate"
            break
        fi
    done
fi
if [[ -z "$BROUTER_JAR" ]]; then
    cat >&2 <<EOF
build_routing: BRouter jar not found.
Set BROUTER_JAR to the absolute path of brouter.jar, e.g.:
    BROUTER_JAR=/opt/brouter/brouter.jar $0 ...
Download from https://github.com/abrensch/brouter/releases
EOF
    exit 2
fi

echo "[build_routing] BRouter prepare → $OUT"
# The 'prepare' command line is finicky; these are the upstream-documented arguments.
# --segments  : write rd5 segment files
# --wayoints  : no — we don't want the waypoint CSV
# --profileFile : the .brf that defines the cost model
# --nodeTable  : the planet slice index (we point at the PBF, BRouter figures out tiles)
java -jar "$BROUTER_JAR" "prepare" \
    "--segments=$SEGS" \
    "--profileFile=$PROFILE_FILE" \
    "--nodeTable=$PBF" || {
        echo "[build_routing] BRouter prepare failed" >&2
        exit 4
    }

# BRouter writes many .rd5 tile files; we concatenate into a single bundle.
# The client opens this as one file (PointPipeline's RegionAssets expects "routing.rd5").
shopt -s nullglob
rd5_files=("$SEGS"/*.rd5)
if [[ ${#rd5_files[@]} -eq 0 ]]; then
    echo "[build_routing] BRouter produced no .rd5 files" >&2
    exit 4
fi
cat "${rd5_files[@]}" > "$OUT"

echo "[build_routing] done: $(du -h "$OUT" | cut -f1) ($((${#rd5_files[@]})) tile(s))"
