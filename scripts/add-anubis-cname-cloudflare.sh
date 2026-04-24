#!/usr/bin/env bash
# Add (or update) the CNAME for anubis.castalia.institute → castaliainstitute.github.io
# so GitHub Pages can serve the custom domain.
#
# Requires: CLOUDFLARE_API_TOKEN with Zone: DNS:Edit on castalia.institute
# (Create in Cloudflare Dashboard → My Profile → API Tokens → Create Custom Token.)
#
# Usage:
#   export CLOUDFLARE_API_TOKEN="..."
#   ./scripts/add-anubis-cname-cloudflare.sh
#
# Optional: CLOUDFLARE_ZONE_ID=...  (if set, skips zone lookup by name)

set -euo pipefail

NAME="anubis"
TARGET="castaliainstitute.github.io"
ZONE_NAME="castalia.institute"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "Set CLOUDFLARE_API_TOKEN (see Cloudflare → API Tokens → DNS:Edit for this zone)." >&2
  exit 1
fi

api() {
  curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" -H "Content-Type: application/json" "$@"
}

if [[ -n "${CLOUDFLARE_ZONE_ID:-}" ]]; then
  ZONE_ID="${CLOUDFLARE_ZONE_ID}"
else
  ZR=$(api "https://api.cloudflare.com/client/v4/zones?name=${ZONE_NAME}")
  if ! echo "$ZR" | jq -e '.success' >/dev/null 2>&1; then
    echo "Zone lookup failed:" >&2
    echo "$ZR" | jq . >&2
    exit 1
  fi
  COUNT=$(echo "$ZR" | jq '.result | length')
  if [[ "$COUNT" -ne 1 ]]; then
    echo "Expected one zone for ${ZONE_NAME}, got ${COUNT}" >&2
    exit 1
  fi
  ZONE_ID=$(echo "$ZR" | jq -r '.result[0].id')
fi

echo "Zone ID: ${ZONE_ID}"

LIST=$(api "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records?type=CNAME&name=${NAME}.${ZONE_NAME}")
EXISTS_ID=$(echo "$LIST" | jq -r '(.result // []) | map(select(.name == "'"${NAME}.${ZONE_NAME}"'")) | .[0].id? // empty')

PAYLOAD=$(jq -n \
  --arg name "$NAME" \
  --arg target "$TARGET" \
  '{type:"CNAME", name:$name, content:$target, proxied:true, ttl:1}')

if [[ -n "$EXISTS_ID" ]] && [[ "$EXISTS_ID" != "null" ]]; then
  echo "Updating existing CNAME id=${EXISTS_ID}"
  OUT=$(api -X PATCH "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records/${EXISTS_ID}" -d "$PAYLOAD")
else
  echo "Creating CNAME ${NAME}.${ZONE_NAME} → ${TARGET}"
  OUT=$(api -X POST "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records" -d "$PAYLOAD")
fi

if echo "$OUT" | jq -e '.success' >/dev/null; then
  echo "$OUT" | jq .
  echo "Done. Check: dig +short ${NAME}.${ZONE_NAME} CNAME"
  echo "Then GitHub → CastaliaInstitute/anubis → Settings → Pages → Custom domain: anubis.castalia.institute"
else
  echo "API error:" >&2
  echo "$OUT" | jq . >&2
  exit 1
fi
