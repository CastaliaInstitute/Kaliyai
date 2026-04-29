"""Tests for deterministic multi-chunk ranking fixture (parity with eval-ranking CLI)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from kaliyai_rag.ranking_fixture import FIXTURE_N_CHUNKS, build_fixture_chunks, write_ranking_sqlite
from kaliyai_rag.sqlite_search import search_sqlite

FIXTURE_DB = Path(__file__).resolve().parents[1] / "tests" / "fixtures" / "ranking" / "kaliyai_ranking.db"


def test_committed_fixture_db_exists() -> None:
    assert FIXTURE_DB.is_file(), "run: python -m kaliyai_rag.cli refresh-ranking-fixture"


def test_ranking_query_chunk_zero_first() -> None:
    chunks = build_fixture_chunks()
    q = chunks[0].embedding
    hits = search_sqlite(str(FIXTURE_DB), q, top_k=5, domain=None)
    assert len(hits) == FIXTURE_N_CHUNKS
    meta = json.loads(hits[0].meta_json)
    assert meta.get("fixture_id") == "gov-0"
    assert "GOVERNANCE_TARGET" in hits[0].body


def test_domain_filter_returns_empty() -> None:
    chunks = build_fixture_chunks()
    q = chunks[0].embedding
    hits = search_sqlite(str(FIXTURE_DB), q, top_k=5, domain="nonexistent_domain_xyz")
    assert hits == []


def test_query_aligned_with_second_chunk_ranks_it_first() -> None:
    chunks = build_fixture_chunks()
    q = chunks[1].embedding
    hits = search_sqlite(str(FIXTURE_DB), q, top_k=5, domain=None)
    meta = json.loads(hits[0].meta_json)
    assert meta.get("fixture_id") == "d1"


def test_temp_db_matches_search_helpers(tmp_path: Path) -> None:
    chunks = build_fixture_chunks()
    db = tmp_path / "x.db"
    write_ranking_sqlite(db, chunks=chunks)
    hits = search_sqlite(str(db), chunks[2].embedding, top_k=3, domain=None)
    assert json.loads(hits[0].meta_json).get("fixture_id") == "d2"
