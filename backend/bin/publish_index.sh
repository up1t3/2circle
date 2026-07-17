#!/usr/bin/env bash
#
# publish_index.sh — roll per-region manifest.json files into the published catalog.
#
# Each region dir under regions/ contains a manifest.json with a single entry. This
# script collects them all, fills in the download_url based on the publishing base URL,
# and writes the merged catalog to --output. The output is what the static host serves
# at /regions/manifest.json (the URL baked into the client's RegionCatalog).
#
# Idempotent: re-running from scratch produces the same catalog. Regions are emitted
# sorted by id for stable diffs in version control.

set -euo pipefail

OUTPUT=""
BASE_URL="${BASE_URL:-}"   # e.g. "https://2circle.example.org/regions"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGIONS_DIR="$ROOT_DIR/regions"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --output)   OUTPUT="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        *) echo "publish_index: unknown arg $1" >&2; exit 3 ;;
    esac
done

[[ -n "$OUTPUT" ]]   || { echo "publish_index: --output is required" >&2; exit 3; }
[[ -n "$BASE_URL" ]] || {
    echo "publish_index: --base-url (or BASE_URL env) is required" >&2
    echo "  e.g. --base-url https://2circle.example.org/regions" >&2
    exit 3
}
BASE_URL="${BASE_URL%/}"

# Collect per-region entries, patching download_url to point at the static host layout:
#   <base-url>/<region-id>/region-<id>-v<version>.zip
entries=()
shopt -s nullglob
for region_manifest in "$REGIONS_DIR"/*/manifest.json; do
    entry_json="$(jq -c '.regions[0] // empty' "$region_manifest")"
    [[ -z "$entry_json" ]] && continue
    id="$(jq -r '.id'           <<<"$entry_json")"
    version="$(jq -r '.version' <<<"$entry_json")"
    zip_name="region-${id}-v${version}.zip"
    download_url="${BASE_URL}/${id}/${zip_name}"
    entries+=("$(jq -c --arg url "$download_url" '. + {download_url: $url}' <<<"$entry_json")")
done

if [[ ${#entries[@]} -eq 0 ]]; then
    echo "publish_index: no region manifests found under $REGIONS_DIR" >&2
    exit 4
fi

# Merge into one catalog object, sorted by id for deterministic output.
printf '%s\n' "${entries[@]}" \
    | jq -s '{schema: 1, regions: sort_by(.id)}' > "$OUTPUT"

count="$(jq '.regions | length' "$OUTPUT")"
echo "[publish_index] wrote $OUTPUT with $count region(s)"
