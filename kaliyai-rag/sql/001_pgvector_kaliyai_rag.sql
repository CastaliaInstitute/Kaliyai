-- Kaliyai RAG storage on Postgres + pgvector (Supabase-compatible).
-- Apply in the SQL editor or via migration tooling after enabling the `vector` extension.

create extension if not exists vector;

-- Default 768 dims for Gemini text-embedding-004. If you use outputDimensionality, recreate column + index.
create table if not exists public.kaliyai_rag_chunks (
  id uuid primary key default gen_random_uuid(),
  body text not null,
  meta jsonb not null default '{}'::jsonb,
  embedding vector(768) not null,
  ingested_at timestamptz not null default now()
);

-- HNSW works well without IVFFLAT-style training passes; tune params under heavy load.
create index if not exists kaliyai_rag_chunks_embedding_hnsw
  on public.kaliyai_rag_chunks
  using hnsw (embedding vector_cosine_ops);

create index if not exists kaliyai_rag_chunks_meta_domain
  on public.kaliyai_rag_chunks ((meta->>'domain'));

comment on table public.kaliyai_rag_chunks is
  'Kaliyai/Kali&AI RAG chunks; meta follows kaliyai_rag.schema.ChunkMetadata JSON.';

-- Semantic search: prefer cosine distance (<=>).
create or replace function public.match_kaliyai_rag_chunks(
  query_embedding vector(768),
  match_count int default 8,
  filter_domain text default null
)
returns table (
  id uuid,
  body text,
  meta jsonb,
  similarity double precision
)
language sql
stable
parallel safe
as $$
  select
    c.id,
    c.body,
    c.meta,
    1::double precision - (c.embedding <=> query_embedding)::double precision as similarity
  from public.kaliyai_rag_chunks c
  where filter_domain is null
    or (c.meta->>'domain') = filter_domain
  order by c.embedding <=> query_embedding
  limit greatest(match_count, 1);
$$;

comment on function public.match_kaliyai_rag_chunks is
  'Cosine-distance retrieval over kaliyai_rag_chunks; supply query_embedding from Gemini RETRIEVAL_QUERY.';
