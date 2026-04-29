"""Upsert embedded chunks to Supabase PostgREST (pgvector)."""

from __future__ import annotations

import os
from typing import Any

import httpx


def _headers(service_role: str) -> dict[str, str]:
    return {
        "apikey": service_role,
        "Authorization": f"Bearer {service_role}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }


def upsert_embedded_rows(
    rows: list[dict[str, Any]],
    *,
    table: str = "kaliyai_rag_chunks",
    client: httpx.Client | None = None,
) -> None:
    """
    POST JSON rows to PostgREST. Each row should include body (text), meta (object), embedding (list[float]).

    Environment:
      SUPABASE_URL — project URL, e.g. https://xxxx.supabase.co
      SUPABASE_SERVICE_ROLE_KEY — service role (server-side only; never ship to clients)
    """
    base = (os.environ.get("SUPABASE_URL") or "").rstrip("/")
    key = (os.environ.get("SUPABASE_SERVICE_ROLE_KEY") or "").strip()
    if not base or not key:
        raise RuntimeError("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY for upsert.")

    url = f"{base}/rest/v1/{table}"
    own = client is None
    c = client or httpx.Client(timeout=120.0)
    try:
        # Batch in chunks to avoid URL/body limits
        batch = 50
        for i in range(0, len(rows), batch):
            chunk = rows[i : i + batch]
            payload = []
            for r in chunk:
                body = r.get("body") if isinstance(r.get("body"), str) else r.get("text")
                meta = r.get("meta")
                emb = r.get("embedding")
                if not isinstance(body, str) or meta is None or emb is None:
                    continue
                payload.append(
                    {
                        "body": body,
                        "meta": meta,
                        "embedding": emb,
                    },
                )
            r = c.post(url, headers=_headers(key), json=payload)
            r.raise_for_status()
    finally:
        if own:
            c.close()


def rpc_match(
    query_embedding: list[float],
    *,
    match_count: int = 8,
    filter_domain: str | None = None,
    fn_name: str = "match_kaliyai_rag_chunks",
    client: httpx.Client | None = None,
) -> list[dict[str, Any]]:
    """Call Supabase RPC for vector search (service role or anon + RLS policies)."""
    base = (os.environ.get("SUPABASE_URL") or "").rstrip("/")
    key = (os.environ.get("SUPABASE_SERVICE_ROLE_KEY") or os.environ.get("SUPABASE_ANON_KEY") or "").strip()
    if not base or not key:
        raise RuntimeError("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (or anon) for RPC.")

    url = f"{base}/rest/v1/rpc/{fn_name}"
    own = client is None
    c = client or httpx.Client(timeout=60.0)
    try:
        body: dict[str, Any] = {
            "query_embedding": query_embedding,
            "match_count": match_count,
        }
        if filter_domain is not None:
            body["filter_domain"] = filter_domain
        r = c.post(url, headers=_headers(key), json=body)
        r.raise_for_status()
        data = r.json()
        return data if isinstance(data, list) else []
    finally:
        if own:
            c.close()
