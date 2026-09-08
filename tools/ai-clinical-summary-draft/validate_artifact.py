# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Rendering-contract checks only. Citation existence does not establish clinical truth."""
import argparse
from datetime import datetime
import json
from pathlib import Path
import re


def require(condition, message):
    if not condition:
        raise ValueError(message)


def text(item, key):
    require(isinstance(item, dict), "Expected object")
    value = item.get(key)
    require(isinstance(value, str) and bool(value.strip()), "Expected text: " + key)
    return value


def array(item, key):
    require(isinstance(item, dict) and isinstance(item.get(key), list), "Expected array: " + key)
    return item[key]


def index(item, key):
    result = {}
    for row in array(item, key):
        identifier = text(row, "id")
        require(re.fullmatch(r"[A-Za-z0-9_-]{1,80}", identifier), "Invalid identifier")
        require(identifier not in result, "Duplicate identifier")
        result[identifier] = row
    return result


def references(item, key, known, nonempty=True):
    values = array(item, key)
    require(not nonempty or bool(values), "Missing citation")
    result = set()
    for value in values:
        require(isinstance(value, str) and value in known and value not in result,
                "Unknown or duplicate reference")
        result.add(value)
    return result


def validate(artifact):
    require(isinstance(artifact, dict), "Artifact must be an object")
    require(type(artifact.get("schema_version")) is int and artifact["schema_version"] == 1,
            "Unsupported schema version")
    for key in ("artifact_id", "generated_at", "model", "workflow"):
        text(artifact, key)
    timestamp = datetime.fromisoformat(artifact["generated_at"].replace("Z", "+00:00"))
    require(timestamp.tzinfo is not None, "Generation timestamp must have a timezone")
    patient = artifact.get("patient_context")
    require(isinstance(patient, dict) and patient.get("synthetic") is True,
            "Prototype requires synthetic data")
    patient_id = text(patient, "id")
    text(patient, "label")
    sources, claims, facts, sections = (index(artifact, key) for key in
                                       ("sources", "claims", "fact_ledger", "sections"))
    require(bool(sources), "Sources must not be empty")
    for source in sources.values():
        require(text(source, "patient_id") == patient_id, "Source belongs to another patient")
        for key in ("title", "date", "text"):
            text(source, key)
    cited = set()
    for claim in claims.values():
        text(claim, "text")
        cited.update(references(claim, "source_ids", sources))
    for fact in facts.values():
        text(fact, "text")
        text(fact, "category")
        references(fact, "source_ids", sources)
    placed = set()
    for section in sections.values():
        text(section, "title")
        ids = references(section, "claim_ids", claims, False)
        require(not placed.intersection(ids), "Claim appears in multiple sections")
        placed.update(ids)
    require(placed == set(claims), "Every claim must appear in a section")
    covered = set()
    for entry in array(artifact, "coverage"):
        source_id = text(entry, "source_id")
        require(source_id in sources and source_id not in covered, "Invalid or duplicate coverage source")
        covered.add(source_id)
        status = text(entry, "status")
        require(status in ("cited", "reviewed_not_cited", "excluded"), "Invalid coverage status")
        require((source_id in cited) == (status == "cited"), "Coverage disagrees with citations")
        text(entry, "reason")
    require(covered == set(sources), "Coverage must account for every source")
    for finding in array(artifact, "validation"):
        require(text(finding, "severity") in ("pass", "warning", "error"), "Invalid severity")
        text(finding, "code")
        text(finding, "message")
        references(finding, "source_ids", sources, False)
    return artifact


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    args = parser.parse_args()
    try:
        validate(json.loads(args.artifact.read_text(encoding="utf-8")))
    except (ValueError, OSError) as error:
        parser.exit(1, f"Artifact rejected: {error}\n")
    print("Artifact reference/coverage checks passed; clinical correctness remains unverified.")


if __name__ == "__main__":
    main()
