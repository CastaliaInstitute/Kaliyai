"""JSONL helpers for offline chunks (with optional embedding vectors)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterator

from kaliyai_rag.schema import ChunkRecord


def write_records(path: Path, records: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for row in records:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def iter_records(path: Path) -> Iterator[dict[str, Any]]:
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            yield json.loads(line)


def chunk_records_to_json_rows(records: list[ChunkRecord]) -> list[dict[str, Any]]:
    """Serialize chunks for JSONL (no embedding)."""
    out: list[dict[str, Any]] = []
    for r in records:
        out.append(
            {
                "text": r.text,
                "chunk_index": r.chunk_index,
                "meta": r.meta.model_dump(mode="json"),
            },
        )
    return out
