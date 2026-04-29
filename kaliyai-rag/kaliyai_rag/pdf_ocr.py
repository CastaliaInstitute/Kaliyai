"""PDF → text via Tesseract OCR (render pages with Poppler, OCR with Tesseract)."""

from __future__ import annotations

from pathlib import Path
from typing import Literal

Mode = Literal["ocr", "auto"]


def _extract_text_layer(path: Path, max_pages: int | None = None) -> tuple[str, int]:
    from pypdf import PdfReader

    reader = PdfReader(path)
    n = len(reader.pages)
    cap = n if max_pages is None else min(n, max_pages)
    parts: list[str] = []
    for i in range(cap):
        t = reader.pages[i].extract_text()
        if t:
            parts.append(t)
    return "\n\n".join(parts).strip(), n


def pdf_to_text_ocr(
    path: Path | str,
    *,
    lang: str = "eng",
    dpi: int = 200,
    max_pages: int | None = None,
) -> str:
    """
    Rasterize each PDF page (Poppler) and OCR with Tesseract.

    Requires system packages:
      - tesseract (and optional language packs, e.g. `tesseract-lang` on Debian)
      - poppler (pdftoppm): macOS `brew install poppler`, Debian `poppler-utils`
    """
    from pdf2image import convert_from_path
    import pytesseract

    path = Path(path)
    kwargs: dict = {"dpi": dpi}
    if max_pages is not None:
        kwargs["last_page"] = max_pages

    images = convert_from_path(str(path), **kwargs)
    out: list[str] = []
    for i, img in enumerate(images):
        page_no = i + 1
        text = pytesseract.image_to_string(img, lang=lang)
        out.append(f"--- Page {page_no} ---\n{text.strip()}")
    return "\n\n".join(out).strip()


def pdf_to_text(
    path: Path | str,
    *,
    mode: Mode = "ocr",
    lang: str = "eng",
    dpi: int = 200,
    max_pages: int | None = None,
    auto_min_chars_per_page: int = 80,
) -> str:
    """
    :param mode:
      - ``ocr``: always Tesseract (good for scanned NIST PDFs).
      - ``auto``: try embedded text layer first; if sparse, fall back to OCR for whole document.
    """
    path = Path(path)
    if mode == "ocr":
        return pdf_to_text_ocr(path, lang=lang, dpi=dpi, max_pages=max_pages)

    embedded, n = _extract_text_layer(path, max_pages=max_pages)
    n = max(n, 1)
    # Compare against page count we actually extracted for the threshold
    n_compare = n if max_pages is None else min(n, max_pages)
    n_compare = max(n_compare, 1)
    if len(embedded) >= auto_min_chars_per_page * n_compare:
        return embedded
    return pdf_to_text_ocr(path, lang=lang, dpi=dpi, max_pages=max_pages)
