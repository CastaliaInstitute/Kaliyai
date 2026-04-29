"""Offline SQLite full-scan cosine search (parity with Android OfflineRagStore)."""

from __future__ import annotations

import json
import math
import sqlite3
import struct
from dataclasses import dataclass


@dataclass(frozen=True)
class SqliteHit:
    body: str
    meta_json: str
    similarity: float


def _l2_normalize(values: list[float]) -> list[float]:
    s = math.sqrt(sum(x * x for x in values)) or 1.0
    return [x / s for x in values]


def _blob_to_floats(blob: bytes) -> list[float]:
    n = len(blob) // 4
    return list(struct.unpack(f"<{n}f", blob))


def _dot(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


def search_sqlite(
    db_path: str,
    query_embedding: list[float],
    *,
    top_k: int = 8,
    domain: str | None = None,
) -> list[SqliteHit]:
    """
    Full scan + cosine similarity; meta.domain filter when ``domain`` is set.
    Skips rows whose embedding dimension mismatches the query vector (same as Kotlin).
    """
    q = _l2_normalize(query_embedding)
    k = max(1, min(64, top_k))
    conn = sqlite3.connect(db_path)
    try:
        cur = conn.execute(
            "SELECT body, meta, embedding_dim, embedding FROM chunks",
        )
        hits: list[SqliteHit] = []
        for body, meta_str, dim_i, emb_blob in cur:
            meta_str = meta_str or "{}"
            if domain is not None:
                try:
                    md = json.loads(meta_str)
                    d = md.get("domain", "") if isinstance(md, dict) else ""
                except json.JSONDecodeError:
                    d = ""
                if str(d) != domain:
                    continue
            dim = int(dim_i)
            blob = emb_blob or b""
            vec = _blob_to_floats(blob)
            if len(vec) != dim or len(vec) != len(q):
                continue
            nv = _l2_normalize(vec)
            sim = max(-1.0, min(1.0, _dot(q, nv)))
            body_s = body if isinstance(body, str) else ""
            hits.append(SqliteHit(body=body_s, meta_json=meta_str, similarity=float(sim)))
        hits.sort(key=lambda h: h.similarity, reverse=True)
        return hits[:k]
    finally:
        conn.close()
