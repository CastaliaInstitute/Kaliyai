#!/usr/bin/env bash
# Alternative: Setup kaliyai.castalia.institute using wrangler CLI for Cloudflare
#
# This uses wrangler's built-in commands. Requires wrangler login first.
#
# Prerequisites:
#   npm install -g wrangler
#   wrangler login
#   gh auth login
#
# Usage:
#   ./scripts/setup-with-wrangler.sh

set -euo pipefail

CNAME="kaliyai"
DOMAIN="${CNAME}.castalia.institute"
TARGET="castaliainstitute.github.io"
ZONE="castalia.institute"
REPO="CastaliaInstitute/Kaliyai"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

check_prereqs() {
    log "Checking prerequisites..."

    if ! command -v wrangler &>/dev/null; then
        error "wrangler not found. Install: npm install -g wrangler"
        exit 1
    fi

    if ! command -v gh &>/dev/null; then
        error "gh not found. Install: brew install gh"
        exit 1
    fi

    # Check wrangler auth
    if ! wrangler whoami &>/dev/null; then
        error "wrangler not authenticated. Run: wrangler login"
        exit 1
    fi

    if ! gh auth status &>/dev/null; then
        error "gh not authenticated. Run: gh auth login"
        exit 1
    fi

    success "All CLIs authenticated"
}

# Use wrangler to manage DNS
setup_dns_with_wrangler() {
    log "Setting up DNS with wrangler..."
    warn "Note: wrangler dns commands may vary by version. Falling back to API if needed."

    # Wrangler doesn't have a direct DNS record management command in all versions
    # We use the API with the token wrangler provides

    if ! wrangler whoami &>/dev/null; then
        error "wrangler not logged in. Run: wrangler login"
        return 1
    fi

    # Get zone ID using wrangler's zone lookup
    log "Looking up zone: ${ZONE}"

    # Try to get zone ID via wrangler API call
    ZONE_RESULT=$(wrangler api --method GET "/zones?name=${ZONE}" 2>/dev/null || echo '{}')
    ZONE_ID=$(echo "$ZONE_RESULT" | jq -r '.result[0].id // empty')

    if [[ -z "$ZONE_ID" || "$ZONE_ID" == "null" ]]; then
        warn "Could not get zone ID via wrangler"
        warn "Find your Zone ID in Cloudflare Dashboard → Overview → Zone ID (right sidebar)"
        read -rp "Enter Zone ID: " ZONE_ID
    fi

    success "Zone ID: ${ZONE_ID}"

    # Create/update DNS record using wrangler API
    log "Creating CNAME record: ${CNAME} → ${TARGET}"

    # Check if record exists
    RECORDS=$(wrangler api --method GET "/zones/${ZONE_ID}/dns_records?type=CNAME&name=${DOMAIN}" 2>/dev/null || echo '{}')
    RECORD_ID=$(echo "$RECORDS" | jq -r '.result[0].id // empty')

    PAYLOAD=$(jq -n \
        --arg name "$CNAME" \
        --arg target "$TARGET" \
        '{type:"CNAME", name:$name, content:$target, proxied:true, ttl:1}')

    if [[ -n "$RECORD_ID" && "$RECORD_ID" != "null" ]]; then
        log "Updating existing record..."
        RESULT=$(wrangler api --method PATCH "/zones/${ZONE_ID}/dns_records/${RECORD_ID}" --data "$PAYLOAD" 2>/dev/null || echo '{}')
    else
        log "Creating new record..."
        RESULT=$(wrangler api --method POST "/zones/${ZONE_ID}/dns_records" --data "$PAYLOAD" 2>/dev/null || echo '{}')
    fi

    if echo "$RESULT" | jq -e '.success' >/dev/null 2>&1; then
        success "DNS record created/updated successfully!"
    else
        error "Failed to create DNS record:"
        echo "$RESULT" | jq . >&2
        return 1
    fi
}

# Configure GitHub Pages with gh
setup_github_pages() {
    log "Configuring GitHub Pages with gh CLI..."

    # Set custom domain
    log "Setting custom domain: ${DOMAIN}"
    gh api --method PUT "repos/${REPO}/pages" --input - <<JSON
{ "cname": "${DOMAIN}", "build_type": "workflow" }
JSON

    success "GitHub Pages custom domain configured"

    # Check if we can enable HTTPS
    log "Checking DNS propagation..."
    if dig +short "$DOMAIN" CNAME | grep -q "github"; then
        log "DNS propagating. Enabling HTTPS..."
        gh api --method PUT "repos/${REPO}/pages" --input - <<JSON
{ "cname": "${DOMAIN}", "https_enforced": true, "build_type": "workflow" }
JSON
        success "HTTPS enforcement enabled!"
    else
        warn "DNS not yet propagated. HTTPS will need to be enabled manually later."
        warn "Re-run: gh api --method PUT repos/${REPO}/pages -f cname='${DOMAIN}' -F https_enforced=true -f build_type=workflow"
    fi
}

# Verify setup
verify() {
    echo ""
    log "Verification:"
    echo "  DNS: dig +short ${DOMAIN} CNAME"
    dig +short "$DOMAIN" CNAME 2>/dev/null || true

    echo ""
    log "GitHub Pages status:"
    gh api "repos/${REPO}/pages" | jq '{ cname, html_url, https_enforced }' 2>/dev/null || warn "Could not fetch GitHub Pages status"
}

main() {
    echo "========================================"
    echo "  Setup ${DOMAIN} (using wrangler + gh)"
    echo "========================================"
    echo ""

    check_prereqs
    setup_dns_with_wrangler
    setup_github_pages
    verify

    echo ""
    success "Setup complete!"
    echo ""
    echo "Site will be available at: https://${DOMAIN}"
    echo "(May take a few minutes for DNS propagation and SSL certificate issuance)"
}

main "$@"
