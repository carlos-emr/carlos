# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Rendering-contract checks only. Citation existence does not establish clinical truth."""
import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import unicodedata

SECTION_TITLES = {
    "clinical_overview": "Clinical overview",
    "active_problems": "Active problems",
    "medications_allergies": "Medications and allergies",
    "results_observations": "Results and observations",
    "plan_follow_up": "Plan and follow-up",
}
SOURCE_METADATA = re.compile(
    r"(?:\b(source (?:note|admission|patient) id|demographic (?:number|id)|recorded gender identity|"
    r"synthetic nhs test patient|imported development fixture|silver data|nmc number|gmc number|"
    r"fixture revision)\b|\b(?:subject|type)\s*:)", re.IGNORECASE)
COMMON_WORDS = {
    "about", "after", "also", "and", "are", "been", "being", "clinical", "current", "for",
    "from", "had", "has", "have", "into", "more", "new", "noted", "patient", "recorded",
    "report", "reported", "source", "that", "the", "their", "there", "this", "was", "were",
    "with", "without",
}
LEXICAL_EXPANSIONS = (
    (re.compile(r"\bhr\b", re.IGNORECASE), "heart rate"),
    (re.compile(r"\bbp\b", re.IGNORECASE), "blood pressure"),
    (re.compile(r"\brr\b", re.IGNORECASE), "respiratory rate"),
    (re.compile(r"\bspo2\b", re.IGNORECASE), "oxygen saturation"),
    (re.compile(r"\bhf\b", re.IGNORECASE), "heart failure"),
    (re.compile(r"\bf/u\b", re.IGNORECASE), "follow up"),
    (re.compile(r"\bwks?\b", re.IGNORECASE), "weeks"),
)


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


def normalized(value):
    return " ".join(re.findall(r"[^\W_]+", unicodedata.normalize("NFKC", value).lower(), re.UNICODE))


def words(value):
    expanded = unicodedata.normalize("NFKC", value).lower()
    for pattern, replacement in LEXICAL_EXPANSIONS:
        expanded = pattern.sub(replacement, expanded)
    return {word for word in re.findall(r"[^\W_]+", expanded, re.UNICODE)
            if len(word) >= 3}


def validate_generated(generated, sources, allow_empty=False):
    require(isinstance(generated, dict) and set(generated) == {"sections", "claims", "coverage"},
            "Model output must contain only sections, claims, and coverage")
    sections = array(generated, "sections")
    claims = array(generated, "claims")
    coverage = array(generated, "coverage")
    require(len(sections) <= len(SECTION_TITLES) and len(claims) <= 20 and 1 <= len(coverage) <= 60,
            "Invalid generated collection size")
    require(allow_empty or bool(claims), "Empty agent draft")
    require(bool(sections) == bool(claims), "Clinical sections and claims must both be present")

    section_ids = set()
    for section in sections:
        require(isinstance(section, dict) and set(section) == {"id", "title", "claim_ids"},
                "Invalid section fields")
        section_id = text(section, "id")
        require(SECTION_TITLES.get(section_id) == text(section, "title")
                and section_id not in section_ids, "Invalid clinical section")
        section_ids.add(section_id)
        require(1 <= len(array(section, "claim_ids")) <= 20, "Clinical sections must not be empty")

    source_words = {}
    for source in sources:
        source_words[text(source, "id")] = words(" ".join(
            text(source, key) for key in ("title", "date", "text")))
    unique_claims = set()
    for claim in claims:
        require(isinstance(claim, dict) and set(claim) == {"id", "text", "source_ids"},
                "Invalid claim fields")
        claim_text = text(claim, "text").strip()
        normalized_claim = normalized(claim_text)
        require(len(claim_text) <= 240 and "\n" not in claim_text and "\r" not in claim_text
                and not SOURCE_METADATA.search(claim_text) and normalized_claim not in unique_claims,
                "Unreadable or duplicate clinical claim")
        unique_claims.add(normalized_claim)
        cited = array(claim, "source_ids")
        require(1 <= len(cited) <= 8, "Invalid claim citations")
        cited_words = set()
        for source_id in cited:
            require(isinstance(source_id, str) and source_id in source_words,
                    "Unknown claim citation")
            cited_words.update(source_words[source_id])
        claim_words = words(claim_text) - COMMON_WORDS
        require(bool(claim_words) and bool(claim_words & cited_words),
                "Claim lacks lexical support in cited sources")

    reasons = set()
    for entry in coverage:
        require(isinstance(entry, dict) and set(entry) == {"source_id", "status", "reason"},
                "Invalid coverage fields")
        reason = text(entry, "reason").strip()
        normalized_reason = normalized(reason)
        require(len(reason) <= 160 and "\n" not in reason and "\r" not in reason
                and normalized_reason not in reasons, "Unreadable or duplicate coverage reason")
        reasons.add(normalized_reason)
    return generated


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
