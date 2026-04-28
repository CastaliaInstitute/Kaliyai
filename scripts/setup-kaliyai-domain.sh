#!/usr/bin/env bash
# Setup kaliyai.castalia.institute with Cloudflare (wrangler) and GitHub (gh) CLIs
#
# Prerequisites:
#   - wrangler: npm install -g wrangler && wrangler login
#   - gh: brew install gh && gh auth login
#   - CLOUDFLARE_API_TOKEN with Zone:DNS:Edit for castalia.institute (optional, for API fallback)
#
# Usage:
#   ./scripts/setup-kaliyai-domain.sh

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
NC='\033[0m' # No Color

log() { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Check prerequisites
check_prereqs() {
    log "Checking prerequisites..."

    if ! command -v gh &>/dev/null; then
        error "GitHub CLI (gh) not found. Install: https://cli.github.com/"
        exit 1
    fi

    if ! command -v wrangler &>/dev/null; then
        error "Cloudflare CLI (wrangler) not found. Install: npm install -g wrangler"
        exit 1
    fi

    if ! gh auth status &>/dev/null; then
        error "GitHub CLI not authenticated. Run: gh auth login"
        exit 1
    fi

    log "CLIs found and GitHub authenticated"
}

# Get Zone ID from Cloudflare
get_zone_id() {
    log "Looking up Cloudflare zone ID for ${ZONE}..."

    # Try using wrangler API with token if available
    if [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]]; then
        ZONE_ID=$(curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
            "https://api.cloudflare.com/client/v4/zones?name=${ZONE}" | \
            jq -r '.result[0].id // empty')

        if [[ -n "$ZONE_ID" && "$ZONE_ID" != "null" ]]; then
            success "Zone ID: ${ZONE_ID}"
            echo "$ZONE_ID"
            return 0
        fi
    fi

    warn "Could not get zone ID automatically. Using wrangler, you may need to specify zone ID manually."
    warn "Find your zone ID in Cloudflare Dashboard → Overview (right sidebar)"
    return 1
}

# Add DNS record using Cloudflare API (works with API token)
add_dns_record() {
    local zone_id=$1

    log "Adding CNAME record: ${CNAME} → ${TARGET}"

    if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
        error "CLOUDFLARE_API_TOKEN not set. Get one from Cloudflare Dashboard → My Profile → API Tokens"
        error "Required permissions: Zone → DNS:Edit for zone ${ZONE}"
        exit 1
    fi

    # Check if record already exists
    EXISTING=$(curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
        "https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records?type=CNAME&name=${DOMAIN}")

    RECORD_ID=$(echo "$EXISTING" | jq -r '.result[0].id // empty')

    PAYLOAD=$(jq -n \
        --arg name "$CNAME" \
        --arg target "$TARGET" \
        '{type:"CNAME", name:$name, content:$target, proxied:true, ttl:1, comment:"GitHub Pages for Kaliyai"}')

    if [[ -n "$RECORD_ID" && "$RECORD_ID" != "null" ]]; then
        log "Updating existing DNS record..."
        RESULT=$(curl -sS -X PATCH \
            -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
            -H "Content-Type: application/json" \
            "https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records/${RECORD_ID}" \
            -d "$PAYLOAD")
    else
        log "Creating new DNS record..."
        RESULT=$(curl -sS -X POST \
            -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN}" \
            -H "Content-Type: application/json" \
            "https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records" \
            -d "$PAYLOAD")
    fi

    if echo "$RESULT" | jq -e '.success' >/dev/null; then
        success "DNS record configured successfully!"
        echo "$RESULT" | jq '.result | {id, name, type, content, proxied}'
    else
        error "Failed to create DNS record:"
        echo "$RESULT" | jq . >&2
        exit 1
    fi
}

# Configure GitHub Pages custom domain
configure_github_pages() {
    log "Configuring GitHub Pages custom domain..."

    # Check current configuration
    CURRENT=$(gh api "repos/${REPO}/pages" 2>/dev/null || echo '{}')
    CURRENT_CNAME=$(echo "$CURRENT" | jq -r '.cname // empty')

    if [[ "$CURRENT_CNAME" == "$DOMAIN" ]]; then
        success "GitHub Pages already configured with custom domain: ${DOMAIN}"
    else
        log "Setting GitHub Pages custom domain to ${DOMAIN}..."
        gh api --method PUT "repos/${REPO}/pages" --input - <<JSON
{ "cname": "${DOMAIN}", "build_type": "workflow" }
JSON
        success "GitHub Pages custom domain set to ${DOMAIN}"
    fi

    # Show current config
    log "Current GitHub Pages configuration:"
    gh api "repos/${REPO}/pages" | jq '{ cname, html_url, build_type, https_enforced, source }'
}

# Enable HTTPS enforcement
enable_https() {
    log "Checking if we can enable HTTPS enforcement..."

    CURRENT=$(gh api "repos/${REPO}/pages" 2>/dev/null || echo '{}')
    HTTPS_ENFORCED=$(echo "$CURRENT" | jq -r '.https_enforced // false')

    if [[ "$HTTPS_ENFORCED" == "true" ]]; then
        success "HTTPS already enforced"
        return 0
    fi

    # Check if DNS is resolving (GitHub needs this to issue cert)
    log "Checking DNS resolution for ${DOMAIN}..."
    if ! dig +short "$DOMAIN" CNAME | grep -q "github"; then
        warn "DNS not yet resolving to GitHub Pages. You may need to wait for propagation."
        warn "Check with: dig +short ${DOMAIN} CNAME"
        warn "Then re-run: ./scripts/setup-kaliyai-domain.sh --https-only"
        return 1
    fi

    log "Enabling HTTPS enforcement..."
    gh api --method PUT "repos/${REPO}/pages" --input - <<JSON
{ "cname": "${DOMAIN}", "https_enforced": true, "build_type": "workflow" }
JSON

    success "HTTPS enforcement enabled!"
}

# Verify setup
verify_setup() {
    log "Verifying setup..."

    echo ""
    echo "=== DNS Check ==="
    dig +short "$DOMAIN" CNAME || true

    echo ""
    echo "=== GitHub Pages Check ==="
    gh api "repos/${REPO}/pages" | jq '{ cname, html_url, https_enforced, build_type }'

    echo ""
    echo "=== HTTP Response ==="
    curl -sI "https://${DOMAIN}" 2>/dev/null | head -5 || warn "Site not yet accessible via HTTPS"
}

# Main execution
main() {
    local https_only=false

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --https-only)
                https_only=true
                shift
                ;;
            --help|-h)
                echo "Usage: $0 [--https-only]"
                echo ""
                echo "Options:"
                echo "  --https-only    Only enable HTTPS enforcement (skip DNS setup)"
                echo "  --help, -h      Show this help"
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    echo "========================================"
    echo "  Setting up ${DOMAIN}"
    echo "========================================"
    echo ""

    check_prereqs

    if [[ "$https_only" == "true" ]]; then
        enable_https
        verify_setup
        exit 0
    fi

    # Get zone ID
    ZONE_ID=$(get_zone_id)

    if [[ -z "$ZONE_ID" ]]; then
        echo ""
        warn "Could not automatically determine Zone ID."
        read -rp "Enter your Cloudflare Zone ID for ${ZONE} (from Cloudflare Dashboard): " ZONE_ID
    fi

    # Setup DNS
    add_dns_record "$ZONE_ID"

    # Configure GitHub Pages
    configure_github_pages

    # Try to enable HTTPS
    enable_https || true

    echo ""
    echo "========================================"
    success "Setup complete!"
    echo "========================================"

    verify_setup

    echo ""
    echo "Next steps if HTTPS is not yet enforced:"
    echo "  1. Wait 1-5 minutes for DNS propagation"
    echo "  2. Run: ./scripts/setup-kaliyai-domain.sh --https-only"
    echo ""
    echo "Or manually check:"
    echo "  dig +short ${DOMAIN} CNAME"
    echo "  curl -I https://${DOMAIN}"
}

main "$@"
