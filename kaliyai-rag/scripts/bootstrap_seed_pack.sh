#!/usr/bin/env bash
# Download Kali&AI RAG seed corpus: git mirrors, NIST PDFs, CISA KEV feeds.
# Artifacts go under kaliyai-rag/sources/ (gitignored). Re-run to pull updates.
#
# Usage:
#   ./bootstrap_seed_pack.sh              # git + nist PDFs + feeds
#   ./bootstrap_seed_pack.sh --git-only
#   ./bootstrap_seed_pack.sh --nist-only
#   ./bootstrap_seed_pack.sh --feeds-only
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SOURCES="${RAG_ROOT}/sources"
GIT_ROOT="${SOURCES}/git"
FEEDS="${SOURCES}/feeds"
NIST="${SOURCES}/nist"

UA="KaliyaiRAG-seed-pack/1.0 (+https://github.com/CastaliaInstitute/Kaliyai; corpus ingestion)"

DO_GIT=1
DO_NIST=1
DO_FEEDS=1

for arg in "$@"; do
  case "${arg}" in
    --git-only) DO_NIST=0; DO_FEEDS=0 ;;
    --nist-only) DO_GIT=0; DO_FEEDS=0 ;;
    --feeds-only) DO_GIT=0; DO_NIST=0 ;;
    --help|-h)
      grep '^#' "$0" | grep -v '^#!/' | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

mkdir -p "${GIT_ROOT}" "${FEEDS}" "${NIST}"

clone_shallow() {
  local url="$1"
  local name="$2"
  local dest="${GIT_ROOT}/${name}"
  if [[ -d "${dest}/.git" ]]; then
    echo "==> pull ${name}"
    git -C "${dest}" pull --ff-only || echo "    (pull failed; fix manually)"
  else
    echo "==> clone ${name}"
    git clone --depth 1 "${url}" "${dest}"
  fi
}

if [[ "${DO_GIT}" -eq 1 ]]; then
  echo "==> GitHub mirrors under ${GIT_ROOT}"
  clone_shallow https://github.com/mitre-attack/attack-stix-data.git attack-stix-data
  clone_shallow https://github.com/mitre/cti.git mitre-cti
  clone_shallow https://github.com/cisagov/kev-data.git kev-data
  clone_shallow https://github.com/OWASP/Top10.git owasp-top10
  clone_shallow https://github.com/OWASP/CheatSheetSeries.git owasp-cheat-sheet-series
  clone_shallow https://github.com/OWASP/wstg.git owasp-wstg
  clone_shallow https://github.com/OWASP/ASVS.git owasp-asvs
  clone_shallow https://github.com/OWASP/API-Security.git owasp-api-security
  clone_shallow https://github.com/SigmaHQ/sigma.git sigma
  clone_shallow https://github.com/elastic/detection-rules.git elastic-detection-rules
  clone_shallow https://github.com/splunk/security_content.git splunk-security_content
  clone_shallow https://github.com/Azure/Azure-Sentinel.git azure-sentinel
  echo "==> nuclei-templates (optional; index with strict safety policy — lab / authorized testing only)"
  clone_shallow https://github.com/projectdiscovery/nuclei-templates.git nuclei-templates || true
fi

if [[ "${DO_NIST}" -eq 1 ]]; then
  echo "==> NIST PDFs under ${NIST}"
  curl -fsSL -A "${UA}" -o "${NIST}/NIST_CSF_2_0.pdf" \
    "https://nvlpubs.nist.gov/nistpubs/CSWP/NIST.CSWP.29.pdf"
  curl -fsSL -A "${UA}" -o "${NIST}/NIST_SP_800_115.pdf" \
    "https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-115.pdf"
  curl -fsSL -A "${UA}" -o "${NIST}/NIST_SP_800_53r5.pdf" \
    "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-53r5.pdf"
  curl -fsSL -A "${UA}" -o "${NIST}/NIST_SP_800_61r3.pdf" \
    "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-61r3.pdf"
  echo "    NIST PDFs saved."
fi

if [[ "${DO_FEEDS}" -eq 1 ]]; then
  echo "==> Public feeds under ${FEEDS}"
  curl -fsSL -A "${UA}" -o "${FEEDS}/cisa_kev.json" \
    "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
  curl -fsSL -A "${UA}" -o "${FEEDS}/cisa_kev.csv" \
    "https://www.cisa.gov/sites/default/files/csv/known_exploited_vulnerabilities.csv"
  echo "    CISA KEV JSON/CSV saved."
  cat > "${FEEDS}/README.txt" << 'EOF'
NVD/CVE JSON bulk feeds and EPSS scores are large and versioned — refresh on a schedule,
not every clone. See:
  https://nvd.nist.gov/vuln/data-feeds
  https://www.first.org/epss/

Prefer APIs or incremental sync for production; store structured intel separately from prose RAG chunks.
EOF
fi

echo ""
echo "Done. Root: ${SOURCES}"
echo "See kaliyai-rag/docs/CORPUS_SEED_PACK.md for collections and ingestion notes."
