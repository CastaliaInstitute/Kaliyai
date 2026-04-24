#!/usr/bin/env bash
# Set GitHub Pages custom domain and workflow build for this repo, using the GitHub CLI.
# Prerequisites: `gh auth login` with a token that can manage the repo; repo admin.
#
# Usage (from anubis repo, or with GH_REPO=owner/name):
#   export GH_REPO=CastaliaInstitute/anubis   # optional; defaults from git remote
#   ./scripts/gh-pages-custom-domain.sh
#
# The API rejects combined updates that include `https_enforced: true` until a TLS
# cert exists. First run this script (cname + workflow), fix DNS in Cloudflare, wait
# for GitHub’s cert check, then:
#   gh api --method PUT "repos/CastaliaInstitute/anubis/pages" --input - <<'JSON'
#   { "cname": "anubis.castalia.institute", "https_enforced": true, "build_type": "workflow" }
#   JSON

set -euo pipefail

CNAME="anubis.castalia.institute"
REPO="${GH_REPO:-CastaliaInstitute/anubis}"

if ! command -v gh &>/dev/null; then
  echo "Install GitHub CLI: https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status &>/dev/null; then
  echo "Run: gh auth login" >&2
  exit 1
fi

echo "==> Setting Pages custom domain on ${REPO} (PAT must allow repo settings)..."
# Omit https_enforced and source: including them can return 404 "certificate does not exist yet" before DNS + cert.
gh api --method PUT "repos/${REPO}/pages" --input - <<JSON
{ "cname": "${CNAME}", "build_type": "workflow" }
JSON

echo "==> Current Pages config:"
gh api "repos/${REPO}/pages" | jq '{ cname, html_url, build_type, https_enforced, source }'
echo ""
echo "Next: (1) CNAME in Cloudflare: anubis -> castaliainstitute.github.io (proxied)."
echo "       (2) After GitHub issues a cert, Enforce HTTPS in the UI or re-run API with https_enforced: true."
echo "          gh api --method PUT repos/${REPO}/pages -f cname='${CNAME}' -F https_enforced=true -f build_type=workflow"
