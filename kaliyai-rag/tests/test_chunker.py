from kaliyai_rag.chunker import chunk_text


def test_chunk_overlap():
    s = "a" * 100
    parts = chunk_text(s, chunk_size=40, chunk_overlap=10)
    assert len(parts) >= 2
    assert all(len(p) <= 40 for p in parts)


def test_empty():
    assert chunk_text("   ") == []
