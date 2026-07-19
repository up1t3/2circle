#!/usr/bin/env bash
#
# check_prerequisites.sh — verify the host has the tools the pipeline needs.
#
# Cross-platform: on Linux/macOS we expect `zip`, `python3`, `sqlite3` on PATH.
# On Windows (Git Bash / MSYS2) we accept 7-Zip in lieu of `zip`, the Astral/UV
# Python launcher, and winget-installed sqlite3.
#
# Exits non-zero if anything critical is missing. Use --quiet to suppress per-tool
# output (e.g. when calling from build_region.sh which prints its own error).

set -euo pipefail

quiet=""
[[ "${1:-}" == "--quiet" ]] && quiet=1

# Each entry: "display_name|test_command"
# We run the test_command and consider the tool present if it exits 0.
tools=(
    "tilemaker|tilemaker --help"
    "java|java -version"
    "python3|python3 --version"
    "sqlite3|sqlite3 --version"
    "sha256sum|sha256sum --version"
    "jq|jq --version"
    "curl|curl --version"
)

missing=()
for entry in "${tools[@]}"; do
    name="${entry%%|*}"
    cmd="${entry#*|}"
    if ! $cmd >/dev/null 2>&1; then
        missing+=("$name")
        [[ -z "$quiet" ]] && echo "MISSING: $name  (try: $cmd)"
    fi
done

# Zip — accept either `zip` or `7z` (Windows).
if ! command -v zip >/dev/null 2>&1; then
    if command -v 7z >/dev/null 2>&1 || [ -x "/c/Program Files/7-Zip/7z.exe" ]; then
        [[ -z "$quiet" ]] && echo "OK (zip): using 7-Zip instead of Info-ZIP"
    else
        missing+=("zip (or 7z)")
        [[ -z "$quiet" ]] && echo "MISSING: zip or 7z"
    fi
fi

if [[ ${#missing[@]} -gt 0 ]]; then
    [[ -z "$quiet" ]] && echo "Missing prerequisites: ${missing[*]}" >&2
    exit 1
fi

[[ -z "$quiet" ]] && echo "All prerequisites satisfied."
exit 0
