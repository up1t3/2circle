#!/usr/bin/env bash
#
# build_tiles.sh — produce tiles.mbtiles from an OSM PBF via tilemaker.
#
# tilemaker reads a JSON config (layer→schema) and a Lua process script (tag→attribute
# mapping). We use the 2circle-specific pair under config/ and lua/, which:
#   - keep `surface`, `smoothness`, `highway`, `name` on roads (so the client can colour
#     them and label them)
#   - drop buildings, addresses, and other city clutter that bloats the package
#   - keep hydrography, boundaries, landcover, and named places
#
# Output zoom range: 6–14. Below 6 the whole planet fits in one tile and we'd waste
# space; above 14 the mbtiles balloons for a bicycle use case where the rider rarely
# needs street-level micro-detail.

set -euo pipefail

PBF=""
OUT=""
CONFIG=""
PROCESS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pbf)     PBF="$2"; shift 2 ;;
        --out)     OUT="$2"; shift 2 ;;
        --config)  CONFIG="$2"; shift 2 ;;
        --process) PROCESS="$2"; shift 2 ;;
        *) echo "build_tiles: unknown arg $1" >&2; exit 3 ;;
    esac
done

for v in PBF OUT CONFIG PROCESS; do
    [[ -z "${!v:-}" ]] && { echo "build_tiles: --${v,,} is required" >&2; exit 3; }
done
[[ -f "$PBF" ]]     || { echo "build_tiles: PBF not found: $PBF" >&2; exit 3; }
[[ -f "$CONFIG" ]]  || { echo "build_tiles: config not found: $CONFIG" >&2; exit 3; }
[[ -f "$PROCESS" ]] || { echo "build_tiles: Lua process not found: $PROCESS" >&2; exit 3; }

mkdir -p "$(dirname "$OUT")"

echo "[build_tiles] tilemaker → $OUT"
# tilemaker v3: combine is now controlled by `combine_below` in the JSON config;
# the `--combine` flag was removed. `--process` defines the Lua tag-filtering hooks.
# We use 2circle-specific config + Lua that keeps surface/smoothness/highway tags and
# drops buildings/addresses (5–10× smaller mbtiles for bicycle use).
tilemaker \
    --input "$PBF" \
    --output "$OUT" \
    --config "$CONFIG" \
    --process "$PROCESS"

if [[ ! -s "$OUT" ]]; then
    echo "[build_tiles] tilemaker produced no output" >&2
    exit 4
fi
echo "[build_tiles] done: $(du -h "$OUT" | cut -f1)"
