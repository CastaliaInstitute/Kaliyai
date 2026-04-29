"""SQLite cosine search parity with Android OfflineRagStore (no Gemini API)."""

from __future__ import annotations

import json
import sqlite3
import struct
from pathlib import Path

import pytest

from kaliyai_rag.sqlite_export import floats_to_blob
from kaliyai_rag.sqlite_search import search_sqlite


def _write_eval_db(path: Path, rows: list[tuple[list[float], str, dict]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    conn = sqlite3.connect(str(path))
    try:
        conn.execute(
            """
            CREATE TABLE chunks (
              _id INTEGER PRIMARY KEY AUTOINCREMENT,
              chunk_index INTEGER NOT NULL DEFAULT 0,
              body TEXT NOT NULL,
              meta TEXT NOT NULL,
              embedding_dim INTEGER NOT NULL,
              embedding BLOB NOT NULL
            )
            """
        )
        for i, (vec, body, meta) in enumerate(rows):
            blob = floats_to_blob(vec)
            conn.execute(
                """
                INSERT INTO chunks (chunk_index, body, meta, embedding_dim, embedding)
                VALUES (?, ?, ?, ?, ?)
                """,
                (i, body, json.dumps(meta), len(vec), blob),
            )
        conn.commit()
    finally:
        conn.close()


def test_search_orders_by_cosine_and_domain_filter(tmp_path: Path) -> None:
    dim = 32
    a = [0.0] * dim
    a[0] = 1.0
    b = [0.0] * dim
    b[1] = 1.0
    db = tmp_path / "t.db"
    _write_eval_db(
        db,
        [
            (a, "chunk A governance", {"domain": "governance", "source": "eval"}),
            (b, "chunk B other", {"domain": "other", "source": "eval"}),
        ],
    )

    hits_all = search_sqlite(str(db), a, top_k=2, domain=None)
    assert len(hits_all) == 2
    assert hits_all[0].similarity >= hits_all[1].similarity
    assert hits_all[0].similarity == pytest.approx(1.0, abs=0.001)

    hits_gov = search_sqlite(str(db), a, top_k=4, domain="governance")
    assert len(hits_gov) == 1
    assert "governance" in hits_gov[0].body

    hits_miss = search_sqlite(str(db), a, top_k=4, domain="nonexistent_domain_xyz")
    assert hits_miss == []


def test_blob_layout_matches_le_float32() -> None:
    """Same little-endian float32 packing as Android VectorMath / export."""
    vals = [0.25, -1.0, 3.5]
    blob = floats_to_blob(vals)
    assert struct.unpack("<3f", blob) == tuple(vals)
