#!/usr/bin/env python3
"""Fail-closed retrieval-manifest checks, independent of model output."""

from collections import Counter, defaultdict


def validate_manifest(manifest):
    failures = []

    def add(code, source_id=None, detail=None):
        item = {"code": code}
        if source_id is not None:
            item["source_id"] = source_id
        if detail is not None:
            item["detail"] = detail
        failures.append(item)

    expected_patient = manifest.get("patient_id")
    expected_encounter = manifest.get("encounter_id")
    sources = manifest.get("sources", [])
    ids = Counter(source.get("source_id") for source in sources)
    for source_id, count in ids.items():
        if not source_id:
            add("MISSING_SOURCE_ID")
        elif count > 1:
            add("DUPLICATE_SOURCE_ID", source_id, count)

    hashes = defaultdict(list)
    for source in sources:
        source_id = source.get("source_id")
        status = source.get("ingestion_status")
        if status not in {"retrieved", "excluded", "failed", "truncated"}:
            add("INVALID_INGESTION_STATUS", source_id, status)
        if status in {"failed", "truncated"}:
            add("INCOMPLETE_RETRIEVAL", source_id, status)
        if status == "retrieved" and not source.get("text_sha256"):
            add("MISSING_CONTENT_HASH", source_id)
        if source.get("patient_id") != expected_patient and status != "excluded":
            add("WRONG_PATIENT_NOT_EXCLUDED", source_id, source.get("patient_id"))
        if expected_encounter and source.get("encounter_id") not in {expected_encounter, None} and status != "excluded":
            add("WRONG_ENCOUNTER_NOT_EXCLUDED", source_id, source.get("encounter_id"))
        if source.get("text_sha256"):
            hashes[source["text_sha256"]].append(source_id)
    for digest, source_ids in hashes.items():
        if len(source_ids) > 1:
            add("DUPLICATE_CONTENT", detail=sorted(source_ids))

    declared = set(manifest.get("expected_source_ids", []))
    actual = set(ids)
    for source_id in sorted(declared - actual):
        add("MISSING_EXPECTED_SOURCE", source_id)
    for source_id in sorted(actual - declared):
        add("UNDECLARED_SOURCE", source_id)
    if manifest.get("context_truncated"):
        add("CONTEXT_TRUNCATED")
    return {"ready_for_generation": not failures, "failure_count": len(failures), "failures": failures}
