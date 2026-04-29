"""Batch-fetch MVP source URLs from config/sources_mvp.yaml and write a status report."""

from __future__ import annotations

import re
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
import yaml

from kaliyai_rag.web_fetch import _slug_from_url, fetch_text


def _safe_filename(prefix: str, url: str, idx: int) -> str:
    slug = _slug_from_url(url).replace(".md", "")
    pfx = re.sub(r"[^a-zA-Z0-9._-]+", "-", prefix)[:50].strip("-") or "src"
    return f"{idx:02d}_{pfx}_{slug}.md"


@dataclass(frozen=True)
class FetchRowOk:
    source: str
    uri: str
    path: Path
    final_url: str


@dataclass(frozen=True)
class FetchRowFail:
    source: str
    uri: str
    error: str


def load_mvp_sources(yaml_path: Path) -> list[tuple[str, str]]:
    raw = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
    rows: list[tuple[str, str]] = []
    seen: set[str] = set()
    for item in raw.get("sources") or []:
        if not isinstance(item, dict):
            continue
        uri = (item.get("uri") or "").strip()
        if not uri.startswith("http"):
            continue
        if uri in seen:
            continue
        seen.add(uri)
        src = str(item.get("source") or "?").strip()
        rows.append((src, uri))
    return rows


def fetch_mvp_yaml(
    yaml_path: Path,
    out_dir: Path,
    *,
    delay_sec: float = 1.5,
    timeout: float = 90.0,
) -> tuple[list[FetchRowOk], list[FetchRowFail]]:
    """Fetch each unique MVP URI; write Markdown files and return ok/fail lists."""
    items = load_mvp_sources(yaml_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    ok: list[FetchRowOk] = []
    fail: list[FetchRowFail] = []
    for idx, (source, uri) in enumerate(items, start=1):
        try:
            body, _title, final_url = fetch_text(uri, timeout=timeout)
            ts = datetime.now(UTC).strftime("%Y-%m-%dT%H:%MZ")
            full = f"{body}\nFetched: {ts}\nSource label: {source}\n"
            name = _safe_filename(source, final_url, idx)
            path = out_dir / name
            path.write_text(full, encoding="utf-8")
            ok.append(FetchRowOk(source=source, uri=uri, path=path, final_url=final_url))
        except Exception as e:
            fail.append(FetchRowFail(source=source, uri=uri, error=f"{type(e).__name__}: {e}"))
        if delay_sec > 0:
            time.sleep(delay_sec)
    return ok, fail


def write_report(
    out_dir: Path,
    ok: list[FetchRowOk],
    fail: list[FetchRowFail],
    *,
    yaml_path: Path,
) -> Path:
    """Write FETCH_REPORT.md next to downloaded files."""
    now = datetime.now(UTC).strftime("%Y-%m-%d %H:%M UTC")
    lines: list[str] = [
        f"# MVP corpus web fetch",
        f"",
        f"- Config: `{yaml_path.as_posix()}`",
        f"- Run: {now}",
        f"",
        f"## Summary",
        f"",
        f"- **Downloaded:** {len(ok)}",
        f"- **Not downloaded (failed):** {len(fail)}",
        f"",
    ]
    lines += ["## Downloaded", "", "| # | Source | Request URL | Saved file |", "|---|--------|-------------|------------|"]
    for i, r in enumerate(ok, 1):
        lines.append(
            f"| {i} | {r.source} | {r.uri} | `{r.path.name}` |",
        )
    lines += ["", "## Not downloaded", ""]
    if not fail:
        lines.append("*(none)*")
    else:
        lines += ["| # | Source | URL | Error |", "|---|--------|-----|-------|"]
        for i, r in enumerate(fail, 1):
            err = r.error.replace("|", "\\|").replace("\n", " ")[:500]
            lines.append(f"| {i} | {r.source} | {r.uri} | {err} |")
    lines.append("")
    report_path = out_dir / "FETCH_REPORT.md"
    report_path.write_text("\n".join(lines), encoding="utf-8")
    return report_path
