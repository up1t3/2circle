#!/usr/bin/env bash
#
# check_prerequisites.sh — verify the host has the tools the pipeline needs.
#
# Exits non-zero if anything is missing. Use --quiet to suppress per-tool output
# (e.g. when calling from build_region.sh which prints its own error).

set -euo pipefail

quiet=""
[[ "${1:-}" == "--quiet" ]] && quiet=1

tools=(
    "tilemaker:tilemaker --version"
    "java:java -version"
    "python3:python3 --version"
    "sqlite3:sqlite3 --version"
    "zip:zip -v"
    "sha256sum:sha256sum --version"
    "jq:jq --version"
    "curl:curl --version"
)

missing=()
for entry in "${tools[@]}"; do
    name="${entry%%:*}"
    cmd="${entry#*:}"
    if ! $cmd >/dev/null 2>&1; then
        missing+=("$name")
        [[ -z "$quiet" ]] && echo "MISSING: $name  (try: $cmd)"
    fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
    [[ -z "$quiet" ]] && echo "Missing prerequisites: ${missing[*]}" >&2
    exit 1
fi

[[ -z "$quiet" ]] && echo "All prerequisites satisfied."
exit 0
