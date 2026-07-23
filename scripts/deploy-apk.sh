#!/usr/bin/env bash
# Deploys the signed release APK to the 2circle VPS region-file server.
#
# Target: http://72.56.238.106:8765/apk/twocircle-0.8.0.apk
#
# The :8765 service is `python -m http.server` rooted at the directory containing
# `manifest.json` and the per-region folders (andorra/, ural-russia/, …). Dropping an
# `apk/` folder next to those makes the file instantly downloadable via directory serving.
#
# Usage:
#   ./scripts/deploy-apk.sh                 # prompts for root password
#   SSHPASS=... ./scripts/deploy-apk.sh     # non-interactive (CI)
#
# Prereq: ./gradlew :app:assembleRelease produced app/build/outputs/apk/release/app-release.apk
set -euo pipefail

VPS_HOST="72.56.238.106"
VPS_USER="root"
REMOTE_ROOT="/root/2circle-server"   # adjusted below: detected from the running http.server
APK_LOCAL="app/build/outputs/apk/release/app-release.apk"
APK_NAME="twocircle-0.8.0.apk"
REMOTE_DIR_REL="apk"                 # becomes <root>/apk/ — served at http://host:8765/apk/

if [[ ! -f "$APK_LOCAL" ]]; then
  echo "ERROR: $APK_LOCAL not found. Run ./gradlew :app:assembleRelease first." >&2
  exit 1
fi

echo "=== Deploying $APK_NAME ($(du -h "$APK_LOCAL" | cut -f1)) to $VPS_HOST ==="

# Detect the http.server root by asking the running process where it chdir'd.
# Falls back to /srv/2circle if detection fails (the launcher script's known default).
REMOTE_ROOT=$(ssh "${VPS_USER}@${VPS_HOST}" \
  "ps -C python3 -o args= 2>/dev/null | grep -oE 'http\\.server [^ ]+|http\\.server$' | head -1; \
   pgrep -af 'http.server' | grep -oE '\\-d [^ ]+' | head -1 | cut -d' ' -f2" 2>/dev/null || true)
# Simpler & robust: the server is launched with a cwd; resolve via /proc.
REMOTE_ROOT=$(ssh "${VPS_USER}@${VPS_HOST}" \
  "for pid in \$(pgrep -f http.server); do echo \$(readlink /proc/\$pid/cwd); done | head -1" 2>/dev/null || true)

if [[ -z "${REMOTE_ROOT:-}" ]]; then
  echo "WARN: could not auto-detect http.server root; assuming /srv/2circle" >&2
  REMOTE_ROOT="/srv/2circle"
fi
echo "Detected http.server root: $REMOTE_ROOT"

# Create the apk dir, copy, fix perms so SimpleHTTPServer can read it.
ssh "${VPS_USER}@${VPS_HOST}" "mkdir -p '$REMOTE_ROOT/$REMOTE_DIR_REL' && chmod 755 '$REMOTE_ROOT/$REMOTE_DIR_REL'"

sshpass=scp
if [[ -n "${SSHPASS:-}" ]]; then
  sshpass -e scp -o StrictHostKeyChecking=accept-new "$APK_LOCAL" \
    "${VPS_USER}@${VPS_HOST}:$REMOTE_ROOT/$REMOTE_DIR_REL/$APK_NAME"
else
  scp -o StrictHostKeyChecking=accept-new "$APK_LOCAL" \
    "${VPS_USER}@${VPS_HOST}:$REMOTE_ROOT/$REMOTE_DIR_REL/$APK_NAME"
fi

ssh "${VPS_USER}@${VPS_HOST}" "chmod 644 '$REMOTE_ROOT/$REMOTE_DIR_REL/$APK_NAME'"

URL="http://${VPS_HOST}:8765/${REMOTE_DIR_REL}/${APK_NAME}"
echo "=== Verifying $URL ==="
sleep 1
curl -sIL "$URL" | grep -iE "^HTTP|^Content-Length|^Content-Type|^Last-Modified" || true

echo
echo "DONE. APK available at:"
echo "  $URL"
echo "  Direct install (phone): open the URL in the browser, or:"
echo "    adb install -r $APK_LOCAL   # if phone is connected locally"
