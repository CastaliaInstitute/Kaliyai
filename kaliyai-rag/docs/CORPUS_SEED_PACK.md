# Kali&AI corpus seed pack

Bulk artifacts are **not** committed: run `scripts/bootstrap_seed_pack.sh` from `kaliyai-rag/` (or repo root with path below). Output layout:

```text
kaliyai-rag/sources/
  git/           # shallow git clones (MITRE, OWASP, Sigma, Elastic, …)
  nist/          # NIST PDFs (CSF 2.0, SP 800-115, 800-53r5, 800-61r3)
  feeds/         # CISA KEV JSON/CSV + README for NVD/EPSS strategy
```

## Three collections

Mapping lives in `config/corpus_collections.yaml`.

| Collection | Role |
|------------|------|
| **cyber_standards** | NIST PDFs, OWASP repos (Top 10, WSTG, ASVS, Cheat Sheets, API Security), CIS/CISA prose as you add it. |
| **cyber_intel_structured** | ATT&CK STIX (`attack-stix-data`), MITRE CTI STIX, KEV feeds — treat as **structured** data (JSON/STIX/CSV), not only prose chunks. |
| **cyber_tools_and_detection** | Sigma, Elastic, Splunk, Sentinel YAML/TOML/KQL; **Nuclei** templates only with lab/authorization policy. |

## First high-value pass (product focus)

MITRE ATT&CK + CISA KEV + OWASP WSTG + Cheat Sheets + NIST CSF + NIST 800-115 + Nmap/Burp/ZAP docs + Sigma — bridges interpretation → safe next steps.

## Ingestion notes

- **Git repos**: Convert MD/HTML/STIX to text offline; chunk with collection-specific `meta.domain` / `task_type`. STIX is often large — consider selective paths (e.g. enterprise-attack objects) before embedding everything.
- **PDFs (Tesseract)**: Install **Tesseract** + **Poppler** (`pdftoppm`), then:

  ```bash
  pip install -r requirements.txt
  # macOS: brew install tesseract poppler
  # Debian/Ubuntu: sudo apt install tesseract-ocr poppler-utils
  python -m kaliyai_rag.cli pdf-to-text sources/nist/NIST_CSF_2_0.pdf -o data/out/NIST_CSF_2_0.txt --mode ocr
  ```

  Use `--mode auto` to prefer embedded text layers when dense, and OCR only when sparse.
- **KEV / NVD / EPSS**: Ideal for **tabular** stores or filtered JSONL chunks keyed by CVE; refresh on a schedule.
- **Nuclei**: Index metadata/summaries for defensive context; avoid promoting exploit execution outside authorized labs.

## Commands

```bash
cd kaliyai-rag
chmod +x scripts/bootstrap_seed_pack.sh   # once
./scripts/bootstrap_seed_pack.sh          # full download
./scripts/bootstrap_seed_pack.sh --feeds-only
```
