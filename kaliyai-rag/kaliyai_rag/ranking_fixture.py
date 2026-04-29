"""
Deterministic multi-chunk SQLite fixture for ranking / distractor tests (no Gemini).

Uses L2-normalized random unit vectors in R^dim (shared seed) so the query vector
identical to chunk 0’s embedding always ranks first — tests real cosine + top-k
selection with competing rows.
"""

from __future__ import annotations

import json
import math
import random
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from kaliyai_rag.sqlite_export import floats_to_blob

# Keep in sync with tests + Android resources if you change the fixture.
FIXTURE_SEED = 42
FIXTURE_DIM = 64
FIXTURE_N_CHUNKS = 5

# Bodies are deliberately off-topic except index 0 (for substring checks only).
FIXTURE_BODIES: tuple[str, ...] = (
    "GOVERNANCE_TARGET NIST CSF identity protect detect respond test chunk zero",
    "RECIPE_DISTRACTOR pizza dough flour yeast water salt olive oil knead rise",
    "SPORT_DISTRACTOR baseball innings home run strikeout World Series",
    "MUSIC_DISTRACTOR jazz blues scale modes improvisation chord progression",
    "TRAVEL_DISTRACTOR airport gate layover visa passport jetlag backpack hostel",
)

FIXTURE_FIXTURE_IDS: tuple[str, ...] = ("gov-0", "d1", "d2", "d3", "d4")


def l2_normalize(v: list[float]) -> list[float]:
    s = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / s for x in v]


def unit_vectors(*, seed: int, dim: int, n: int) -> list[list[float]]:
    rng = random.Random(seed)
    out: list[list[float]] = []
    for _ in range(n):
        v = [rng.gauss(0.0, 1.0) for _ in range(dim)]
        out.append(l2_normalize(v))
    return out


@dataclass(frozen=True)
class FixtureChunk:
    chunk_index: int
    body: str
    fixture_id: str
    embedding: list[float]


def build_fixture_chunks(
    *,
    seed: int = FIXTURE_SEED,
    dim: int = FIXTURE_DIM,
) -> list[FixtureChunk]:
    if len(FIXTURE_BODIES) != FIXTURE_N_CHUNKS or len(FIXTURE_FIXTURE_IDS) != FIXTURE_N_CHUNKS:
        raise ValueError("fixture body/id tuples must match FIXTURE_N_CHUNKS")
    vecs = unit_vectors(seed=seed, dim=dim, n=FIXTURE_N_CHUNKS)
    rows: list[FixtureChunk] = []
    for i in range(FIXTURE_N_CHUNKS):
        meta = {"fixture_id": FIXTURE_FIXTURE_IDS[i], "domain": "test", "source": "ranking_fixture"}
        rows.append(
            FixtureChunk(
                chunk_index=i,
                body=FIXTURE_BODIES[i],
                fixture_id=FIXTURE_FIXTURE_IDS[i],
                embedding=vecs[i],
            ),
        )
    return rows


def write_ranking_sqlite(out_db: Path, *, chunks: list[FixtureChunk] | None = None) -> int:
    chunks = chunks or build_fixture_chunks()
    out_db.parent.mkdir(parents=True, exist_ok=True)
    if out_db.exists():
        out_db.unlink()
    conn = sqlite3.connect(str(out_db))
    try:
        conn.execute("PRAGMA user_version = 1")
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
        for ch in chunks:
            meta_str = json.dumps(
                {"fixture_id": ch.fixture_id, "domain": "test", "source": "ranking_fixture"},
                ensure_ascii=False,
            )
            blob = floats_to_blob(ch.embedding)
            conn.execute(
                """
                INSERT INTO chunks (chunk_index, body, meta, embedding_dim, embedding)
                VALUES (?, ?, ?, ?, ?)
                """,
                (ch.chunk_index, ch.body, meta_str, len(ch.embedding), blob),
            )
        conn.commit()
        return len(chunks)
    finally:
        conn.close()


def manifest_dict(chunks: list[FixtureChunk]) -> dict[str, Any]:
    q0 = chunks[0].embedding
    return {
        "version": 1,
        "embedding_dim": len(q0),
        "seed": FIXTURE_SEED,
        "num_chunks": len(chunks),
        "chunks": [
            {
                "chunk_index": c.chunk_index,
                "fixture_id": c.fixture_id,
                "body_prefix": c.body[:40],
            }
            for c in chunks
        ],
        "query_same_as_chunk_index": 0,
        "expected_rank_one_fixture_id": chunks[0].fixture_id,
    }


def write_query_vectors_json(path: Path, chunks: list[FixtureChunk]) -> None:
    """Vectors keyed by label for Android / cross-language tests."""
    data = {
        "embedding_dim": len(chunks[0].embedding),
        "same_as_chunk_0": chunks[0].embedding,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=0), encoding="utf-8")


def refresh_all_fixture_outputs(
    *,
    repo_root: Path,
    copy_android: bool,
) -> tuple[Path, Path, Path]:
    """
    Write kaliyai_ranking.db, manifest.json, query_vectors.json under tests/fixtures/ranking/.
    Optionally copy DB + query JSON into nethunter-gemini-mcp test resources.
    """
    out_db = repo_root / "tests" / "fixtures" / "ranking" / "kaliyai_ranking.db"
    out_manifest = repo_root / "tests" / "fixtures" / "ranking" / "manifest.json"
    out_queries = repo_root / "tests" / "fixtures" / "ranking" / "query_vectors.json"

    chunks = build_fixture_chunks()
    write_ranking_sqlite(out_db, chunks=chunks)
    out_manifest.write_text(json.dumps(manifest_dict(chunks), indent=2), encoding="utf-8")
    write_query_vectors_json(out_queries, chunks)

    if copy_android:
        mono = repo_root.parent
        android_base = mono / "nethunter-gemini-mcp" / "app" / "src" / "test" / "resources" / "fixtures" / "ranking"
        android_db = android_base / "kaliyai_ranking.db"
        android_q = android_base / "query_vectors.json"
        android_base.mkdir(parents=True, exist_ok=True)
        android_db.write_bytes(out_db.read_bytes())
        android_q.write_text(out_queries.read_text(encoding="utf-8"), encoding="utf-8")

    return out_db, out_manifest, out_queries
