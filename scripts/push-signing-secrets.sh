#!/usr/bin/env bash
# Push local release keystore credentials to GitHub Actions secrets.
# Reads ./keystore.properties by default (never printed). Run from repo root.

set -euo pipefail

REPO="${1:-LTechnologies0/Secure-File-Manager}"
PROPS="${SIGNING_PROPS:-keystore.properties}"

if [[ ! -f "$PROPS" ]]; then
  echo "ERROR: $PROPS not found. Set SIGNING_PROPS or copy keystore.properties.example."
  exit 1
fi

# shellcheck disable=SC1090
while IFS='=' read -r key value; do
  value="${value//$'\r'/}"
  case "$key" in
    storeFile) storeFile="$value" ;;
    storePassword) storePassword="$value" ;;
    keyAlias) keyAlias="$value" ;;
    keyPassword) keyPassword="$value" ;;
  esac
done < <(grep -E '^(storeFile|storePassword|keyAlias|keyPassword)=' "$PROPS")

KEYSTORE_PATH="$storeFile"
if [[ "$KEYSTORE_PATH" != /* ]]; then
  KEYSTORE_PATH="$(cd "$(dirname "$PROPS")" && pwd)/$KEYSTORE_PATH"
fi

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "ERROR: keystore not found at $KEYSTORE_PATH"
  exit 1
fi

echo "Setting secrets on $REPO (keystore: $(basename "$KEYSTORE_PATH"), alias: $keyAlias)"

base64 -w0 "$KEYSTORE_PATH" 2>/dev/null | gh secret set RELEASE_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$storePassword" | gh secret set RELEASE_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$keyAlias" | gh secret set RELEASE_KEY_ALIAS --repo "$REPO"
printf '%s' "${keyPassword:-$storePassword}" | gh secret set RELEASE_KEY_PASSWORD --repo "$REPO"

echo "Done. Trigger a release: git tag v1.0.0-alpha && git push origin v1.0.0-alpha"
echo "Or: gh workflow run release.yml -f tag=v1.0.0-alpha"
