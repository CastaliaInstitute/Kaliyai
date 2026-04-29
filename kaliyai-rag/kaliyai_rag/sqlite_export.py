"""Export embedded JSONL to SQLite for Android OfflineRagStore (SD card)."""

from __future__ import annotations

import json
import sqlite3
import struct
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1


def floats_to_blob(values: list[float]) -> bytes:
    """Little-endian float32 (matches Android ByteOrder.LITTLE_ENDIAN)."""
    return struct.pack(f"<{len(values)}f", *values)


def export_embedded_jsonl_to_sqlite(
    embedded_jsonl: Path,
    out_db: Path,
    *,
    replace: bool = True,
) -> int:
    """
    Read JSONL rows with keys text, meta (object), embedding (list[float]).
    Writes table `chunks` compatible with OfflineRagStore.kt.
    """
    out_db.parent.mkdir(parents=True, exist_ok=True)
    if replace and out_db.exists():
        out_db.unlink()

    conn = sqlite3.connect(str(out_db))
    try:
        conn.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
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
        n = 0
        with embedded_jsonl.open(encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                row: dict[str, Any] = json.loads(line)
                body = row.get("text")
                if not isinstance(body, str):
                    body = row.get("body")
                meta = row.get("meta")
                emb = row.get("embedding")
                if not isinstance(body, str) or meta is None or emb is None:
                    continue
                if isinstance(meta, dict):
                    meta_str = json.dumps(meta, ensure_ascii=False)
                elif isinstance(meta, str):
                    meta_str = meta
                else:
                    continue
                if not isinstance(emb, list) or not emb:
                    continue
                floats = [float(x) for x in emb]
                dim = len(floats)
                blob = floats_to_blob(floats)
                chunk_index = int(row.get("chunk_index", 0))
                conn.execute(
                    """
                    INSERT INTO chunks (chunk_index, body, meta, embedding_dim, embedding)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    (chunk_index, body, meta_str, dim, blob),
                )
                n += 1
        conn.commit()
        return n
    finally:
        conn.close()
