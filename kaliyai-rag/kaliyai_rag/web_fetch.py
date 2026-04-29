"""Download pages from the web and extract main text for RAG ingestion."""

from __future__ import annotations

import re
import time
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import urlparse

import httpx

USER_AGENT = (
    "KaliyaiRAG/0.1 (+https://github.com/CastaliaInstitute/Kaliyai; corpus ingestion; respectful crawling)"
)


def _slug_from_url(url: str) -> str:
    p = urlparse(url.strip())
    host = re.sub(r"[^a-zA-Z0-9._-]+", "-", p.netloc or "host")
    path = (p.path or "/").strip("/").replace("/", "_")
    if not path:
        path = "index"
    base = f"{host}_{path}"[:180]
    return re.sub(r"_+", "_", base).strip("_") + ".md"


def fetch_text(url: str, *, timeout: float = 60.0) -> tuple[str, str | None, str]:
    """
    Fetch [url] and return (markdown_body, title_or_none, final_url).

    Uses trafilatura for HTML; falls back to plain text / UTF-8 decode for non-HTML.
    """
    import trafilatura

    headers = {"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8"}
    with httpx.Client(timeout=timeout, follow_redirects=True, headers=headers) as client:
        r = client.get(url)
        r.raise_for_status()
        final_url = str(r.url)
        ctype = (r.headers.get("content-type") or "").split(";")[0].strip().lower()

        if ctype in ("text/plain", "text/markdown", "application/json") or "json" in ctype:
            raw = r.content.decode(r.encoding or "utf-8", errors="replace")
            title = Path(urlparse(final_url).path).name or "document"
            if ctype == "application/json" or "json" in ctype:
                inner = f"```\n{raw[:500_000]}\n```\n"
            else:
                inner = raw
            body = f"# {title}\n\n{inner}\n\n---\nSource: {final_url}\n"
            return body, title, final_url

        html = r.text
        meta = trafilatura.extract_metadata(html)
        title = meta.title if meta and meta.title else None
        text = trafilatura.extract(
            html,
            url=final_url,
            favor_precision=True,
            include_comments=False,
            include_tables=True,
        )
        if not text or not text.strip():
            raise RuntimeError("no extractable text (empty page or blocked content)")

        header = f"# {title}\n\n" if title else f"# {final_url}\n\n"
        footer = f"\n\n---\nSource: {final_url}\n"
        return header + text.strip() + footer, title, final_url


def write_fetched_markdown(
    url: str,
    out_path: Path | None,
    *,
    timeout: float = 60.0,
) -> Path:
    """Fetch URL and write markdown file; returns path written."""
    body, _title, final_url = fetch_text(url, timeout=timeout)
    ts = datetime.now(UTC).strftime("%Y-%m-%dT%H:%MZ")
    full = f"{body}\nFetched: {ts}\n"
    path = out_path or Path(_slug_from_url(final_url))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(full, encoding="utf-8")
    return path


def fetch_urls_file(
    urls_file: Path,
    out_dir: Path,
    *,
    delay_sec: float = 1.5,
    timeout: float = 60.0,
) -> list[tuple[str, Path | None, str | None]]:
    """
    Read one URL per line (skip empty and # comments). Write one .md per URL under out_dir.
    Returns list of (url, path_or_none_if_failed, error_or_none).
    """
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = urls_file.read_text(encoding="utf-8").splitlines()
    results: list[tuple[str, Path | None, str | None]] = []
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        url = line.split()[0]
        try:
            body, _title, final_url = fetch_text(url, timeout=timeout)
            ts = datetime.now(UTC).strftime("%Y-%m-%dT%H:%MZ")
            full = f"{body}\nFetched: {ts}\n"
            name = _slug_from_url(final_url)
            path = out_dir / name
            path.write_text(full, encoding="utf-8")
            results.append((url, path, None))
        except Exception as e:
            results.append((url, None, str(e)))
        if delay_sec > 0:
            time.sleep(delay_sec)
    return results
