"""CLI: chunk files, embed with Gemini, export JSONL, optional Supabase upsert."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, cast

import yaml

from kaliyai_rag.chunker import chunk_text
from kaliyai_rag.embeddings import embed_texts
from kaliyai_rag.jsonl_io import chunk_records_to_json_rows, write_records
from kaliyai_rag.schema import ChunkMetadata, ChunkRecord


def _load_meta_yaml(path: Path) -> ChunkMetadata:
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"Meta YAML must be a mapping: {path}")
    return ChunkMetadata.model_validate(raw)


def cmd_chunk_file(args: argparse.Namespace) -> int:
    text = Path(args.file).read_text(encoding="utf-8", errors="replace")
    meta = _load_meta_yaml(Path(args.meta_yaml))
    uri_base = meta.uri
    pieces = chunk_text(text, chunk_size=args.chunk_size, chunk_overlap=args.overlap)
    records: list[ChunkRecord] = []
    for i, p in enumerate(pieces):
        m = (
            meta
            if uri_base is None
            else meta.model_copy(update={"uri": f"{uri_base}#chunk-{i}"})
        )
        records.append(ChunkRecord(text=p, chunk_index=i, meta=m))
    rows = chunk_records_to_json_rows(records)
    out = Path(args.output)
    write_records(out, rows)
    print(f"Wrote {len(rows)} chunks -> {out}", file=sys.stderr)
    return 0


def cmd_embed_jsonl(args: argparse.Namespace) -> int:
    from kaliyai_rag.jsonl_io import iter_records

    inp = Path(args.input)
    out_rows: list[dict[str, Any]] = []
    batch_texts: list[str] = []
    batch_rows: list[dict[str, Any]] = []
    batch_size = max(1, int(args.batch_size))

    def flush() -> None:
        nonlocal batch_texts, batch_rows, out_rows
        if not batch_texts:
            return
        vectors = embed_texts(batch_texts, task_type="RETRIEVAL_DOCUMENT")
        if len(vectors) != len(batch_rows):
            raise RuntimeError("embedding batch size mismatch")
        for row, vec in zip(batch_rows, vectors):
            row["embedding"] = vec
            out_rows.append(row)
        batch_texts = []
        batch_rows = []

    for row in iter_records(inp):
        text = row.get("text")
        if not isinstance(text, str) or not text.strip():
            continue
        batch_texts.append(text)
        batch_rows.append(row)
        if len(batch_texts) >= batch_size:
            flush()
    flush()

    out_path = Path(args.output)
    write_records(out_path, out_rows)
    print(f"Embedded {len(out_rows)} rows -> {out_path}", file=sys.stderr)
    return 0


def cmd_upsert_jsonl(args: argparse.Namespace) -> int:
    from kaliyai_rag.jsonl_io import iter_records
    from kaliyai_rag.supabase_store import upsert_embedded_rows

    rows = list(iter_records(Path(args.input)))
    upsert_embedded_rows(rows, table=args.table)
    print(f"Upserted {len(rows)} rows -> {args.table}", file=sys.stderr)
    return 0


def cmd_query(args: argparse.Namespace) -> int:
    from kaliyai_rag.embeddings import embed_query
    from kaliyai_rag.supabase_store import rpc_match

    q = embed_query(args.text)
    domain = (args.domain or "").strip()
    rows = rpc_match(
        q,
        match_count=args.top_k,
        filter_domain=domain if domain else None,
    )
    sys.stdout.write(json.dumps(rows, ensure_ascii=False, indent=2) + "\n")
    return 0


def cmd_export_sqlite(args: argparse.Namespace) -> int:
    from kaliyai_rag.sqlite_export import export_embedded_jsonl_to_sqlite

    outp = Path(args.output)
    if outp.exists() and args.no_replace:
        print(f"Refusing to overwrite existing file: {outp}", file=sys.stderr)
        return 1

    n = export_embedded_jsonl_to_sqlite(
        Path(args.input),
        outp,
        replace=not args.no_replace,
    )
    print(f"Exported {n} chunks -> {args.output}", file=sys.stderr)
    return 0


def cmd_pdf_to_text(args: argparse.Namespace) -> int:
    from kaliyai_rag.pdf_ocr import Mode, pdf_to_text

    inp = Path(args.input)
    mode: Mode = cast(Mode, args.mode.strip().lower())
    text = pdf_to_text(
        inp,
        mode=mode,
        lang=args.lang.strip() or "eng",
        dpi=int(args.dpi),
        max_pages=args.max_pages,
        auto_min_chars_per_page=int(args.auto_min_chars),
    )
    outp = Path(args.output)
    outp.parent.mkdir(parents=True, exist_ok=True)
    outp.write_text(text, encoding="utf-8")
    print(f"Wrote {len(text)} chars -> {outp}", file=sys.stderr)
    return 0


def cmd_fetch_url(args: argparse.Namespace) -> int:
    from kaliyai_rag.web_fetch import write_fetched_markdown

    out = Path(args.output) if getattr(args, "output", None) else None
    path = write_fetched_markdown(args.url, out, timeout=float(args.timeout))
    print(str(path.resolve()), file=sys.stderr)
    return 0


def cmd_fetch_mvp_sources(args: argparse.Namespace) -> int:
    from pathlib import Path

    from kaliyai_rag.mvp_fetch import fetch_mvp_yaml, write_report

    cfg = (getattr(args, "config", None) or "").strip()
    yaml_path = Path(cfg) if cfg else Path(__file__).resolve().parents[1] / "config" / "sources_mvp.yaml"
    out_dir = Path(args.out_dir)
    ok, fail = fetch_mvp_yaml(
        yaml_path,
        out_dir,
        delay_sec=float(args.delay),
        timeout=float(args.timeout),
    )
    rep = write_report(out_dir, ok, fail, yaml_path=yaml_path)
    print(rep.resolve(), file=sys.stderr)
    print(f"Done: {len(ok)} downloaded, {len(fail)} failed (see report).", file=sys.stderr)
    return 0


def cmd_fetch_urls(args: argparse.Namespace) -> int:
    from kaliyai_rag.web_fetch import fetch_urls_file

    results = fetch_urls_file(
        Path(args.input),
        Path(args.out_dir),
        delay_sec=float(args.delay),
        timeout=float(args.timeout),
    )
    errs = 0
    for url, path, err in results:
        if err:
            errs += 1
            print(f"FAIL {url}\n  {err}", file=sys.stderr)
        else:
            print(f"OK {url} -> {path}", file=sys.stderr)
    print(f"Fetched {len(results) - errs}/{len(results)} pages -> {args.out_dir}", file=sys.stderr)
    return 1 if errs else 0


def cmd_eval_retrieval(args: argparse.Namespace) -> int:
    """Embed natural-language queries and assert golden phrases appear in top-ranked chunks."""
    import json as json_module

    from kaliyai_rag.embeddings import embed_query
    from kaliyai_rag.sqlite_search import search_sqlite

    db_path = Path(args.db)
    if not db_path.is_file():
        print(f"SQLite DB not found: {db_path}", file=sys.stderr)
        return 1
    cases_path = Path(args.cases)
    if not cases_path.is_file():
        print(f"Cases JSON not found: {cases_path}", file=sys.stderr)
        return 1
    raw = json_module.loads(cases_path.read_text(encoding="utf-8"))
    case_list = raw.get("cases")
    if not isinstance(case_list, list):
        print("Invalid eval JSON: missing 'cases' array", file=sys.stderr)
        return 1

    failures: list[str] = []
    for i, case in enumerate(case_list):
        if not isinstance(case, dict):
            failures.append(f"case[{i}]: not an object")
            continue
        cid = case.get("id", f"case_{i}")
        query = case.get("query")
        if not isinstance(query, str) or not query.strip():
            failures.append(f"{cid}: missing query")
            continue
        top_k = int(case.get("top_k", 8))
        domain_raw = case.get("domain")
        domain: str | None
        if domain_raw is None or domain_raw == "":
            domain = None
        elif isinstance(domain_raw, str):
            domain = domain_raw.strip() or None
        else:
            domain = None
        match_within = int(case.get("match_within_rank", top_k))
        match_within = max(1, min(match_within, top_k))

        vec = embed_query(query.strip())
        hits = search_sqlite(str(db_path.resolve()), vec, top_k=top_k, domain=domain)
        if not hits:
            failures.append(f"{cid}: no hits (domain={domain!r})")
            continue

        needles = case.get("body_contains_any")
        if isinstance(needles, list) and needles:
            blob = " ".join(h.body for h in hits[:match_within])
            blob_lower = blob.lower()
            if not any(str(n).lower() in blob_lower for n in needles if str(n).strip()):
                failures.append(
                    f"{cid}: expected one of {needles} in top-{match_within} bodies "
                    f"(got sim={hits[0].similarity:.4f} first)",
                )
                continue

        msrc = case.get("meta_source_contains")
        if isinstance(msrc, str) and msrc.strip():
            want = msrc.strip().lower()
            ok_meta = False
            for h in hits[:match_within]:
                try:
                    meta = json_module.loads(h.meta_json)
                    src = ""
                    if isinstance(meta, dict):
                        src = str(meta.get("source", ""))
                    if want in src.lower():
                        ok_meta = True
                        break
                except json_module.JSONDecodeError:
                    continue
            if not ok_meta:
                failures.append(f"{cid}: meta.source does not contain {msrc!r} in top-{match_within}")

    if failures:
        print("Retrieval eval FAILURES:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print(f"Retrieval eval OK: {len(case_list)} case(s) -> {db_path}", file=sys.stderr)
    return 0


def cmd_refresh_ranking_fixture(_: argparse.Namespace) -> int:
    from kaliyai_rag.ranking_fixture import refresh_all_fixture_outputs

    root = Path(__file__).resolve().parents[1]
    db, man, qv = refresh_all_fixture_outputs(repo_root=root, copy_android=True)
    print(f"Ranking fixture refreshed: {db}", file=sys.stderr)
    print(f"  {man}", file=sys.stderr)
    print(f"  {qv}", file=sys.stderr)
    return 0


def cmd_eval_ranking(args: argparse.Namespace) -> int:
    """Multi-chunk cosine ranking with deterministic vectors (no API)."""
    import json as json_module

    from kaliyai_rag.ranking_fixture import build_fixture_chunks
    from kaliyai_rag.sqlite_search import search_sqlite

    root = Path(__file__).resolve().parents[1]
    cases_path = Path(args.cases)
    raw = json_module.loads(cases_path.read_text(encoding="utf-8"))
    rel_db = raw.get("fixture_db")
    if not isinstance(rel_db, str) or not rel_db.strip():
        print("Invalid ranking JSON: missing fixture_db", file=sys.stderr)
        return 1
    db_path = root / rel_db.strip()
    if not db_path.is_file():
        print(f"Fixture DB not found: {db_path} (run: python -m kaliyai_rag.cli refresh-ranking-fixture)", file=sys.stderr)
        return 1

    case_list = raw.get("cases")
    if not isinstance(case_list, list):
        print("Invalid ranking JSON: missing cases array", file=sys.stderr)
        return 1

    chunks = build_fixture_chunks()
    failures: list[str] = []

    for i, case in enumerate(case_list):
        if not isinstance(case, dict):
            failures.append(f"case[{i}]: not an object")
            continue
        cid = case.get("id", f"case_{i}")
        qidx = case.get("query_same_as_chunk_index")
        if not isinstance(qidx, int) or qidx < 0 or qidx >= len(chunks):
            failures.append(f"{cid}: bad query_same_as_chunk_index")
            continue
        query_vec = chunks[qidx].embedding
        top_k = int(case.get("top_k", 8))
        domain_raw = case.get("domain")
        domain: str | None
        if domain_raw is None or domain_raw == "":
            domain = None
        elif isinstance(domain_raw, str):
            domain = domain_raw.strip() or None
        else:
            domain = None

        hits = search_sqlite(str(db_path.resolve()), query_vec, top_k=top_k, domain=domain)

        if case.get("expect_empty_hits") is True:
            if hits:
                failures.append(f"{cid}: expected no hits, got {len(hits)} (first sim={hits[0].similarity:.4f})")
            continue

        if not hits:
            failures.append(f"{cid}: no hits")
            continue

        want_id = case.get("expect_rank_one_fixture_id")
        if isinstance(want_id, str) and want_id.strip():
            try:
                meta0 = json_module.loads(hits[0].meta_json)
                got_id = meta0.get("fixture_id", "") if isinstance(meta0, dict) else ""
            except json_module.JSONDecodeError:
                got_id = ""
            if str(got_id) != want_id.strip():
                failures.append(f"{cid}: rank1 fixture_id want {want_id!r} got {got_id!r}")

        subs = case.get("expect_body_contains")
        if isinstance(subs, str) and subs.strip():
            if subs not in hits[0].body:
                failures.append(f"{cid}: rank1 body missing substring {subs!r}")

    if failures:
        print("Ranking eval FAILURES:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print(f"Ranking eval OK: {len(case_list)} case(s) -> {db_path}", file=sys.stderr)
    return 0


def cmd_env_check(_: argparse.Namespace) -> int:
    import os

    ok = True
    key = (os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "").strip()
    if not key:
        print("GEMINI_API_KEY (or GOOGLE_API_KEY) is not set.", file=sys.stderr)
        ok = False
    else:
        print("Gemini API key: present")
    print(
        "Optional: SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY for DB upsert / RPC search.",
    )
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    # Load .env from cwd or kaliyai-rag/ when running as module
    try:
        from dotenv import load_dotenv

        load_dotenv()
        root = Path(__file__).resolve().parents[1]
        load_dotenv(root / ".env")
    except ImportError:
        pass

    p = argparse.ArgumentParser(prog="kaliyai-rag", description="Kaliyai corpus ingestion")
    sub = p.add_subparsers(dest="cmd", required=True)

    c1 = sub.add_parser("chunk-file", help="Split a text file into JSONL chunks using a meta YAML")
    c1.add_argument("file", help="Path to UTF-8 text or markdown")
    c1.add_argument("--meta-yaml", required=True, help="YAML matching ChunkMetadata fields")
    c1.add_argument("-o", "--output", required=True, help="Output JSONL path")
    c1.add_argument("--chunk-size", type=int, default=1800)
    c1.add_argument("--overlap", type=int, default=200)
    c1.set_defaults(func=cmd_chunk_file)

    c2 = sub.add_parser("embed-jsonl", help="Add embeddings to JSONL (text + meta rows)")
    c2.add_argument("-i", "--input", required=True)
    c2.add_argument("-o", "--output", required=True)
    c2.add_argument("--batch-size", type=int, default=8)
    c2.set_defaults(func=cmd_embed_jsonl)

    c3 = sub.add_parser("env-check", help="Verify embedding-related environment variables")
    c3.set_defaults(func=cmd_env_check)

    c4 = sub.add_parser(
        "upsert-jsonl",
        help="POST embedded JSONL rows (text/body, meta, embedding) to Supabase",
    )
    c4.add_argument("-i", "--input", required=True)
    c4.add_argument(
        "--table",
        default="kaliyai_rag_chunks",
        help="PostgREST table name (default: kaliyai_rag_chunks)",
    )
    c4.set_defaults(func=cmd_upsert_jsonl)

    c5 = sub.add_parser(
        "query",
        help="Embed a question and run match_kaliyai_rag_chunks RPC (requires Supabase env)",
    )
    c5.add_argument("text", help="Natural-language query")
    c5.add_argument("--top-k", type=int, default=8, dest="top_k")
    c5.add_argument("--domain", default="", help="Optional meta.domain filter")
    c5.set_defaults(func=cmd_query)

    c6 = sub.add_parser(
        "export-sqlite",
        help="Build Android offline DB from embedded JSONL (copy .db to SD card)",
    )
    c6.add_argument("-i", "--input", required=True, help="chunks.embedded.jsonl")
    c6.add_argument("-o", "--output", required=True, help="kaliyai_rag.db")
    c6.add_argument(
        "--no-replace",
        action="store_true",
        help="Fail if output exists instead of overwriting",
    )
    c6.set_defaults(func=cmd_export_sqlite)

    c7 = sub.add_parser(
        "fetch-url",
        help="Download one URL and save extracted main text as Markdown (HTML via trafilatura)",
    )
    c7.add_argument("url", help="https://…")
    c7.add_argument(
        "-o",
        "--output",
        help="Output .md path (default: derived from URL under cwd)",
    )
    c7.add_argument("--timeout", type=float, default=60.0)
    c7.set_defaults(func=cmd_fetch_url)

    c8 = sub.add_parser(
        "fetch-urls",
        help="Download many URLs from a text file (one URL per line; # comments ok)",
    )
    c8.add_argument("-i", "--input", required=True, help="urls.txt")
    c8.add_argument(
        "-o",
        "--out-dir",
        required=True,
        help="Directory for one .md file per successful fetch",
    )
    c8.add_argument(
        "--delay",
        type=float,
        default=1.5,
        help="Seconds to sleep between requests (be polite to servers)",
    )
    c8.add_argument("--timeout", type=float, default=60.0)
    c8.set_defaults(func=cmd_fetch_urls)

    c9 = sub.add_parser(
        "fetch-mvp-sources",
        help="Fetch all unique https URLs from config/sources_mvp.yaml and write FETCH_REPORT.md",
    )
    c9.add_argument(
        "--config",
        default="",
        help="Path to sources YAML (default: package config/sources_mvp.yaml)",
    )
    c9.add_argument(
        "-o",
        "--out-dir",
        required=True,
        help="Output directory for .md files + FETCH_REPORT.md",
    )
    c9.add_argument("--delay", type=float, default=1.5)
    c9.add_argument("--timeout", type=float, default=90.0)
    c9.set_defaults(func=cmd_fetch_mvp_sources)

    c10 = sub.add_parser(
        "pdf-to-text",
        help="Extract text from PDF (default: Tesseract OCR via pdf2image + Poppler)",
    )
    c10.add_argument("input", help="Path to .pdf")
    c10.add_argument("-o", "--output", required=True, help="Output .txt path")
    c10.add_argument(
        "--mode",
        choices=("ocr", "auto"),
        default="ocr",
        help="ocr = always Tesseract; auto = try text layer first, then OCR if sparse",
    )
    c10.add_argument("--lang", default="eng", help="Tesseract language(s), e.g. eng, deu+eng")
    c10.add_argument("--dpi", type=int, default=200, help="Rasterization DPI for OCR")
    c10.add_argument("--max-pages", type=int, default=None, dest="max_pages")
    c10.add_argument(
        "--auto-min-chars",
        type=int,
        default=80,
        dest="auto_min_chars",
        help="In auto mode: min total chars per page to skip OCR",
    )
    c10.set_defaults(func=cmd_pdf_to_text)

    c11 = sub.add_parser(
        "eval-retrieval",
        help="Run golden JSON queries against an offline kaliyai_rag.db (embed + cosine search)",
    )
    c11.add_argument(
        "--db",
        required=True,
        help="Path to SQLite from export-sqlite",
    )
    default_cases = Path(__file__).resolve().parents[1] / "config" / "evals" / "retrieval_golden.json"
    c11.add_argument(
        "--cases",
        default=str(default_cases),
        help=f"Golden eval JSON (default: {default_cases})",
    )
    c11.set_defaults(func=cmd_eval_retrieval)

    c12 = sub.add_parser(
        "refresh-ranking-fixture",
        help="Regenerate tests/fixtures/ranking/*.db + manifest + query_vectors (+ copy to Android test resources)",
    )
    c12.set_defaults(func=cmd_refresh_ranking_fixture)

    c13 = sub.add_parser(
        "eval-ranking",
        help="Run retrieval_ranking.json against multi-chunk fixture DB (no Gemini)",
    )
    default_rank = Path(__file__).resolve().parents[1] / "config" / "evals" / "retrieval_ranking.json"
    c13.add_argument(
        "--cases",
        default=str(default_rank),
        help=f"Ranking eval JSON (default: {default_rank})",
    )
    c13.set_defaults(func=cmd_eval_ranking)

    args = p.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
