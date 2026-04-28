# CLI Setup Guide: Cloudflare + GitHub for kaliyai.castalia.institute

This guide covers using `wrangler` (Cloudflare CLI) and `gh` (GitHub CLI) to set up the custom domain.

## Prerequisites

### Install CLIs

```bash
# Install wrangler (Cloudflare CLI)
npm install -g wrangler

# Install gh (GitHub CLI) - macOS
brew install gh

# Or download from: https://cli.github.com/
```

### Authenticate

```bash
# Login to Cloudflare
wrangler login

# Login to GitHub
gh auth login
```

## Quick Setup

### Option 1: All-in-one script (uses wrangler API)

```bash
./scripts/setup-with-wrangler.sh
```

### Option 2: Flexibile script (uses CLOUDFLARE_API_TOKEN)

```bash
# Get API token from Cloudflare Dashboard → My Profile → API Tokens
# Required permissions: Zone → DNS:Edit for zone castalia.institute

export CLOUDFLARE_API_TOKEN="your-token-here"
./scripts/setup-kaliyai-domain.sh
```

### Option 3: Manual CLI commands

#### Step 1: DNS with wrangler

```bash
# Find your Zone ID (Cloudflare Dashboard → Overview → Zone ID)
export ZONE_ID="your-zone-id"

# Create CNAME record using wrangler's API access
wrangler api --method POST "/zones/${ZONE_ID}/dns_records" \
  --data '{
    "type": "CNAME",
    "name": "kaliyai",
    "content": "castaliainstitute.github.io",
    "proxied": true,
    "ttl": 1
  }'
```

Or use the Cloudflare Dashboard manually:
- **Type**: CNAME
- **Name**: `kaliyai`
- **Target**: `castaliainstitute.github.io`
- **Proxy**: Enabled (orange cloud)

#### Step 2: GitHub Pages with gh

```bash
# Set custom domain
gh api --method PUT repos/CastaliaInstitute/Kaliyai/pages --input - <<'JSON'
{
  "cname": "kaliyai.castalia.institute",
  "build_type": "workflow"
}
JSON

# Check status
gh api repos/CastaliaInstitute/Kaliyai/pages | jq '{ cname, html_url, https_enforced }'
```

#### Step 3: Enable HTTPS (after DNS propagates)

```bash
# Wait for DNS propagation
dig +short kaliyai.castalia.institute CNAME
# Should show: castaliainstitute.github.io

# Enable HTTPS enforcement
gh api --method PUT repos/CastaliaInstitute/Kaliyai/pages --input - <<'JSON'
{
  "cname": "kaliyai.castalia.institute",
  "https_enforced": true,
  "build_type": "workflow"
}
JSON
```

## Verification Commands

```bash
# Check DNS
dig +short kaliyai.castalia.institute CNAME
dig +short kaliyai.castalia.institute A

# Check GitHub Pages config
gh api repos/CastaliaInstitute/Kaliyai/pages | jq

# Test HTTP response
curl -I https://kaliyai.castalia.institute

# Check Cloudflare record with wrangler
wrangler api --method GET "/zones/${ZONE_ID}/dns_records?type=CNAME&name=kaliyai.castalia.institute"
```

## Troubleshooting

### "Not logged in" error with wrangler
```bash
wrangler login
```

### "Not authenticated" error with gh
```bash
gh auth login
# Or check status: gh auth status
```

### Zone ID lookup fails
Find it manually in Cloudflare Dashboard:
1. Go to your domain
2. Look for "Zone ID" in the right sidebar
3. Use directly: `export ZONE_ID="your-id"`

### HTTPS can't be enabled yet
GitHub needs to verify DNS and issue an SSL certificate first. Wait 1-5 minutes after DNS propagation, then retry.

## Scripts Overview

| Script | Description |
|--------|-------------|
| `setup-kaliyai-domain.sh` | Flexible script using CLOUDFLARE_API_TOKEN |
| `setup-with-wrangler.sh` | Uses wrangler CLI for all Cloudflare operations |
| `gh-pages-custom-domain.sh` | Minimal script for GitHub Pages only |
| `add-kaliyai-cname-cloudflare.sh` | Direct API script for DNS only |

## Resources

- [Wrangler documentation](https://developers.cloudflare.com/workers/wrangler/)
- [GitHub CLI documentation](https://cli.github.com/manual/)
- [Cloudflare API tokens](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/)
