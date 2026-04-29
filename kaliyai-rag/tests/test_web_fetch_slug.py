from kaliyai_rag.web_fetch import _slug_from_url


def test_slug_from_url_basic():
    s = _slug_from_url("https://owasp.org/www-project-top-ten/")
    assert s.endswith(".md")
    assert "owasp.org" in s
