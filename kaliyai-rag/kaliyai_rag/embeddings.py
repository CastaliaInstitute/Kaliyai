"""Gemini text embeddings via Generative Language REST API."""

from __future__ import annotations

import os
from typing import Any

import httpx


# Matches Generative Language API examples (ai.google.dev); text-embedding-004 may 404 on some keys.
DEFAULT_MODEL = "models/gemini-embedding-001"
DEFAULT_DIM = 768


def _api_key() -> str:
    key = (os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "").strip()
    if not key:
        raise RuntimeError(
            "Set GEMINI_API_KEY (or GOOGLE_API_KEY) for embeddings.",
        )
    return key


def embed_texts(
    texts: list[str],
    *,
    model: str | None = None,
    task_type: str = "RETRIEVAL_DOCUMENT",
    output_dimensionality: int | None = None,
    client: httpx.Client | None = None,
) -> list[list[float]]:
    """
    Embed a batch of strings. Uses one HTTP request per input (API batching varies).

    task_type: RETRIEVAL_DOCUMENT for corpus chunks; RETRIEVAL_QUERY at query time.
    output_dimensionality: if set, must match DB vector column (e.g. 256, 512, 768).
    """
    m = model or os.environ.get("KALIYAI_EMBEDDING_MODEL", DEFAULT_MODEL)
    dim = output_dimensionality
    if dim is None:
        raw = os.environ.get("KALIYAI_EMBEDDING_DIMENSION")
        dim = int(raw) if raw else None

    own_client = client is None
    c = client or httpx.Client(timeout=120.0)
    try:
        key = _api_key()
        out: list[list[float]] = []
        for text in texts:
            url = f"https://generativelanguage.googleapis.com/v1beta/{m}:embedContent"
            body: dict[str, Any] = {
                "model": m,
                "content": {"parts": [{"text": text}]},
                "taskType": task_type,
            }
            if dim is not None:
                body["outputDimensionality"] = dim
            r = c.post(url, params={"key": key}, json=body)
            r.raise_for_status()
            data = r.json()
            emb = data.get("embedding")
            if isinstance(emb, dict) and "values" in emb:
                vals = emb["values"]
            else:
                # Some responses nest differently; normalize to list of floats.
                vals = data.get("embeddings")
                if isinstance(vals, list) and vals and isinstance(vals[0], dict):
                    vals = vals[0].get("values", vals[0])
                if not isinstance(vals, list):
                    raise RuntimeError(f"unexpected embedContent response keys: {list(data.keys())}")
            out.append([float(x) for x in vals])
        return out
    finally:
        if own_client:
            c.close()


def embed_query(text: str, **kwargs: Any) -> list[float]:
    """Single query embedding with RETRIEVAL_QUERY."""
    kw = dict(kwargs)
    kw["task_type"] = "RETRIEVAL_QUERY"
    return embed_texts([text], **kw)[0]
