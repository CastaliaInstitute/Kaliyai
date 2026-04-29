"""Simple character-based chunking with overlap (no extra dependencies)."""

from __future__ import annotations


def chunk_text(
    text: str,
    *,
    chunk_size: int = 1800,
    chunk_overlap: int = 200,
) -> list[str]:
    """
    Split plain text into overlapping segments.

    chunk_size/overlap are character counts — tune per corpus (markdown vs JSON).
    """
    t = text.strip()
    if not t:
        return []
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    overlap = max(0, min(chunk_overlap, chunk_size - 1))
    out: list[str] = []
    start = 0
    n = len(t)
    while start < n:
        end = min(start + chunk_size, n)
        piece = t[start:end].strip()
        if piece:
            out.append(piece)
        if end >= n:
            break
        start = end - overlap
        if start <= 0:
            start = end
    return out
