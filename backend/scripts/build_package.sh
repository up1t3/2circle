#!/usr/bin/env bash
#
# build_package.sh — bundle a built region into a .zip + emit manifest.json.
#
# Reads: <dir>/tiles.mbtiles, routing.rd5, search.db (built by prior stages).
# Writes: <dir>/manifest.json (per-region entry, matching the client's RegionEntry)
#         <dir>/region-<id>-v<version>.zip (the delivery artifact)
#
# The zip layout is flat — files at the archive root, no subdirectory — because the
# client's RegionDownloader.extractTo() does `File(entry.name).name` to strip paths.

set -euo pipefail

ID=""
NAME=""
VERSION=""
BOUNDS=""
DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --id)      ID="$2"; shift 2 ;;
        --name)    NAME="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --bounds)  BOUNDS="$2"; shift 2 ;;
        --dir)     DIR="$2"; shift 2 ;;
        *) echo "build_package: unknown arg $1" >&2; exit 3 ;;
    esac
done

for v in ID NAME VERSION BOUNDS DIR; do
    [[ -z "${!v:-}" ]] && { echo "build_package: --${v,,} is required" >&2; exit 3; }
done

if ! [[ "$BOUNDS" =~ ^-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+$ ]]; then
    echo "build_package: bounds must be minLat,minLon,maxLat,maxLon" >&2
    exit 3
fi

[[ -d "$DIR" ]] || { echo "build_package: dir not found: $DIR" >&2; exit 3; }

# Verify all three artefacts exist.
for f in tiles.mbtiles routing.rd5 search.db; do
    if [[ ! -s "$DIR/$f" ]]; then
        echo "build_package: missing or empty artefact: $DIR/$f" >&2
        exit 4
    fi
done

IFS=',' read -r MINLAT MINLON MAXLAT MAXLON <<< "$BOUNDS"

ZIP_NAME="region-${ID}-v${VERSION}.zip"
ZIP_PATH="$DIR/$ZIP_NAME"

# Build the delivery zip. Store, not deflate — .mbtiles already gzip-compresses its
# internal tiles, double-compression wastes CPU for negligible gain.
# Use whichever archiver is available: `zip` on Linux/macOS, 7-Zip on Windows.
echo "[build_package] zipping → $ZIP_PATH"
if command -v zip >/dev/null 2>&1; then
    (
        cd "$DIR"
        zip -q -X "$ZIP_NAME" tiles.mbtiles routing.rd5 search.db
    )
elif [ -x "/c/Program Files/7-Zip/7z.exe" ]; then
    # 7-Zip in its default install path. Use an array to avoid word-splitting on the
    # space in "Program Files". -mx0 = store (no compression).
    (
        cd "$DIR"
        "/c/Program Files/7-Zip/7z.exe" a -bd -mx0 "$ZIP_NAME" tiles.mbtiles routing.rd5 search.db >/dev/null
    )
elif command -v 7z >/dev/null 2>&1; then
    ( cd "$DIR" && 7z a -bd -mx0 "$ZIP_NAME" tiles.mbtiles routing.rd5 search.db >/dev/null )
else
    echo "[build_package] no zip or 7z available — install one or set ZIP_TOOL" >&2
    exit 2
fi

# Compute sha256 — the client verifies this before installing.
SHA256="$(sha256sum "$ZIP_PATH" | awk '{print $1}')"
# File size: `wc -c < file` is portable across Linux/macOS/Windows-bash.
SIZE="$(wc -c < "$ZIP_PATH" | tr -d ' ')"

# Per-region manifest entry. The publish_index.sh script rolls these up into the
# top-level catalog; for local/CI hand-off this file is what the publisher reads.
# The download_url is left blank here — the static host's publishing step fills it in
# based on where the zip actually gets served from.
MANIFEST="$DIR/manifest.json"
cat > "$MANIFEST" <<EOF
{
  "schema": 1,
  "regions": [
    {
      "id": "$ID",
      "name": "$NAME",
      "version": $VERSION,
      "sizeBytes": $SIZE,
      "bounds": {
        "minLat": $MINLAT,
        "minLon": $MINLON,
        "maxLat": $MAXLAT,
        "maxLon": $MAXLON
      },
      "download_url": "",
      "sha256": "$SHA256"
    }
  ]
}
EOF

echo "[build_package] manifest written: $MANIFEST"
echo "[build_package] sha256: $SHA256"
echo "[build_package] size: $SIZE bytes"
echo "[build_package] done"
