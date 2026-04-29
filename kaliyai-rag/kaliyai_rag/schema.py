"""Chunk metadata aligned with Kali&AI analyst workflows (filtering + reporting)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


OffensiveRisk = Literal["low", "medium", "high"]
AllowedUse = Literal["defensive", "authorized", "lab"]


class ChunkMetadata(BaseModel):
    """Per-chunk metadata for retrieval filters and safe generation."""

    source: str = Field(..., description="Human-readable corpus name, e.g. OWASP WSTG")
    version: str | None = Field(None, description="Doc/version label, e.g. CSF 2.0, v8.1")
    domain: str | None = Field(
        None,
        description="Topic slug: web-security, vuln-intel, cloud-aws, ir-detection, ...",
    )
    task_type: list[str] = Field(
        default_factory=list,
        description="analyst tasks: test-plan, interpretation, remediation, detection, ...",
    )
    framework: str | None = Field(None, description="Owning body: NIST, OWASP, MITRE, ...")
    maps_to: list[str] = Field(
        default_factory=list,
        description="Crosswalk IDs: OWASP-A01, CWE-284, MITRE-T1550, NIST.CSF-DE.AE, ...",
    )
    offensive_risk: OffensiveRisk | None = Field(
        None,
        description="How sensitive retrieved content is if quoted out of context",
    )
    allowed_use: AllowedUse | None = Field(
        None,
        description="defensive | authorized (pentest ROE) | lab",
    )
    evidence_type: list[str] = Field(
        default_factory=list,
        description="http-request, header, log, screenshot, config, pcap, ...",
    )
    audience: list[str] = Field(
        default_factory=list,
        description="student, analyst, engineer, executive",
    )
    uri: str | None = Field(None, description="Stable URL or path within corpus")
    extra: dict[str, object] = Field(
        default_factory=dict,
        description="Additional JSON-safe fields (tier, mvp_priority, ...)",
    )


class ChunkRecord(BaseModel):
    """One chunk ready for embedding and storage."""

    text: str
    chunk_index: int = 0
    meta: ChunkMetadata
