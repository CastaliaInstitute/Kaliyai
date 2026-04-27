#!/usr/bin/env bash
# Create or update a CNAME for affiliate GitHub Pages under affiliates.castalia.institute.
# Works in either (a) delegated zone "affiliates.castalia.institute" (record name = SLUG only) or
# (b) parent "castalia.institute" (record name = "SLUG.affiliates").
#
# Examples:
#   # Child zone (name is just the org label):
#   export ZONE_NAME=affiliates.castalia.institute
#   export DNS_NAME_MODE=child
#   export CLOUDFLARE_API_TOKEN="..."
#   ./scripts/add-affiliate-cname-cloudflare.sh aurnova yourorg.github.io
#
#   # Parent castalia zone (FOLDED name aurnova.affiliates):
#   export DNS_NAME_MODE=parent
#   export ZONE_NAME=castalia.institute
#   ./scripts/add-affiliate-cname-cloudflare.sh aurnova yourorg.github.io
#
# After DNS, add the custom domain in the GitHub repo (Settings → Pages) and this Worker
# in affiliates-edge/ as the first route on the proxied hostnames.
#
# Requires: CLOUDFLARE_API_TOKEN with Zone → DNS:Edit. Optional CLOUDFLARE_ZONE_ID.

set -euo pipefail

SLUG="${1:?first arg: affiliate slug, e.g. aurnova (demo org)}"
TARGET="${2:?second arg: GitHub Pages hostname, e.g. myorg.github.io}"
ZONE_NAME="${ZONE_NAME:-affiliates.castalia.institute}"
DNS_NAME_MODE="${DNS_NAME_MODE:-child}" # child | parent

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "Set CLOUDFLARE_API_TOKEN" >&2
  exit 1
fi

if [[ "$DNS_NAME_MODE" == "child" ]]; then
  NAME="$SLUG"
  FULL_FQDN="${SLUG}.affiliates.castalia.institute"
elif [[ "$DNS_NAME_MODE" == "parent" ]]; then
  NAME="${SLUG}.affiliates"
  FULL_FQDN="${SLUG}.affiliates.castalia.institute"
else
  echo "DNS_NAME_MODE must be child or parent" >&2
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

# Cloudflare list filter: name may be FQDN depending on how records were created
PAYLOAD=$(jq -n --arg name "$NAME" --arg target "$TARGET" \
  '{type:"CNAME", name:$name, content:$target, proxied:true, ttl:1}')

LIST=$(api "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records?type=CNAME&name=${FULL_FQDN}")
EXISTS_ID=$(echo "$LIST" | jq -r '(.result // []) | map(select(.name == $FULL)) | .[0].id? // empty' --arg FULL "$FULL_FQDN")

if [[ -z "$EXISTS_ID" || "$EXISTS_ID" == "null" ]]; then
  LIST2=$(api "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records?type=CNAME")
  EXISTS_ID=$(echo "$LIST2" | jq -r --arg FULL "$FULL_FQDN" '(.result // []) | map(select(.name == $FULL)) | .[0].id? // empty')
fi

if [[ -n "$EXISTS_ID" && "$EXISTS_ID" != "null" && "$EXISTS_ID" != "" ]]; then
  echo "Updating CNAME id=${EXISTS_ID} ${FULL_FQDN} → ${TARGET} (proxied)"
  OUT=$(api -X PATCH "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records/${EXISTS_ID}" -d "$PAYLOAD")
else
  echo "Creating CNAME ${NAME} in zone ${ZONE_NAME} → ${TARGET} (→ ${FULL_FQDN})"
  OUT=$(api -X POST "https://api.cloudflare.com/client/v4/zones/${ZONE_ID}/dns_records" -d "$PAYLOAD")
fi

if echo "$OUT" | jq -e '.success' >/dev/null; then
  echo "$OUT" | jq .
  echo "Verify: dig +short ${FULL_FQDN} CNAME"
  echo "Then: GitHub repo → Settings → Pages → Custom domain: ${FULL_FQDN}"
  echo "Deploy the Worker in affiliates-edge/ and add routes for *.affiliates.castalia.institute (and *.* as needed) in that zone — see wrangler.toml."
else
  echo "API error:" >&2
  echo "$OUT" | jq . >&2
  exit 1
fi
