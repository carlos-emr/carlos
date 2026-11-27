# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Deterministic scoring for synthetic clinical-summary experiments."""

from __future__ import annotations

from difflib import SequenceMatcher
import re
import statistics


def normalized(value):
    value = " ".join(str(value or "").lower().split())
    # Treat common UK/US clinical spellings as equivalent in synthetic scoring.
    return re.sub(r"\borthopnea\b", "orthopnoea", value)


def _matches(text, pattern_groups):
    return all(any(re.search(pattern, text, re.IGNORECASE) for pattern in group)
               for group in pattern_groups)


def evaluate(generated, sources, expectations):
    """Return findings and metrics against a closed synthetic fact ledger."""
    findings = []

    def add(severity, code, detail, claim_id=None, fact_id=None):
        finding = {"severity": severity, "code": code, "detail": detail}
        if claim_id is not None:
            finding["claim_id"] = claim_id
        if fact_id is not None:
            finding["fact_id"] = fact_id
        findings.append(finding)

    facts = expectations["facts"]
    fact_by_id = {fact["id"]: fact for fact in facts}
    sections = {claim_id: section["id"] for section in generated["sections"]
                for claim_id in section["claim_ids"]}
    matched_fact_ids = set()
    fact_claim_counts = {}
    citation_expected = 0
    citation_present = 0
    citation_actual = 0
    citation_relevant = 0

    for claim in generated["claims"]:
        claim_text = normalized(claim["text"])
        matches = [fact for fact in facts if _matches(claim_text, fact["pattern_groups"])]
        if not matches:
            add("error", "UNSUPPORTED_CLAIM", "Claim matches no allowed synthetic fact.", claim["id"])
            continue
        if len(matches) > 1:
            add("error", "COMBINED_FACTS", "Claim combines more than one labelled fact.", claim["id"])
        for fact in matches:
            matched_fact_ids.add(fact["id"])
            fact_claim_counts[fact["id"]] = fact_claim_counts.get(fact["id"], 0) + 1
            expected = set(fact["required_source_ids"])
            allowed = set(fact.get("allowed_source_ids", fact["required_source_ids"]))
            actual = set(claim["source_ids"])
            citation_expected += len(expected)
            citation_present += len(expected & actual)
            citation_actual += len(actual)
            citation_relevant += len(actual & allowed)
            missing = sorted(expected - actual)
            extra = sorted(actual - allowed)
            if missing:
                add("error", "MISSING_FACT_CITATION", "Missing: " + ", ".join(missing),
                    claim["id"], fact["id"])
            if extra:
                add("error", "IRRELEVANT_FACT_CITATION", "Irrelevant: " + ", ".join(extra),
                    claim["id"], fact["id"])
            if sections.get(claim["id"]) not in fact["section_ids"]:
                add("error", "WRONG_SECTION", "Expected one of: " + ", ".join(fact["section_ids"]),
                    claim["id"], fact["id"])

    for fact_id, count in fact_claim_counts.items():
        if count > 1:
            add("error", "DUPLICATE_FACT_CLAIM", f"Fact appears in {count} claims.", fact_id=fact_id)

    for pattern in expectations.get("forbidden_patterns", []):
        for claim in generated["claims"]:
            if re.search(pattern, claim["text"], re.IGNORECASE):
                add("error", "FORBIDDEN_CONTENT", "Matched forbidden pattern: " + pattern,
                    claim["id"])

    excluded = set(expectations.get("excluded_source_ids", []))
    for claim in generated["claims"]:
        bad = sorted(excluded & set(claim["source_ids"]))
        if bad:
            add("error", "EXCLUDED_SOURCE_CITED", "Excluded: " + ", ".join(bad), claim["id"])

    coverage = {entry["source_id"]: entry["status"] for entry in generated["coverage"]}
    for source_id, statuses in expectations.get("coverage", {}).items():
        if coverage.get(source_id) not in statuses:
            add("error", "WRONG_COVERAGE_STATUS",
                f"{source_id} expected one of {statuses}, got {coverage.get(source_id)}")

    required = [fact for fact in facts if fact.get("required", True)]
    critical = [fact for fact in required if fact.get("critical", False)]
    missing_required = [fact for fact in required if fact["id"] not in matched_fact_ids]
    for fact in missing_required:
        add("error" if fact.get("critical", False) else "warning", "MISSING_FACT",
            "Required synthetic fact was omitted.", fact_id=fact["id"])

    required_recall = ((len(required) - len(missing_required)) / len(required)) if required else 1.0
    missing_critical = [fact for fact in critical if fact["id"] not in matched_fact_ids]
    critical_recall = ((len(critical) - len(missing_critical)) / len(critical)) if critical else 1.0
    if required_recall < 0.90:
        add("error", "LOW_FACT_RECALL", f"Fact recall {required_recall:.3f} is below 0.900.")

    source_texts = [normalized(source["text"]) for source in sources]
    copy_ratios = [max((SequenceMatcher(None, normalized(claim["text"]), source).ratio()
                        for source in source_texts), default=0.0)
                   for claim in generated["claims"]]
    lengths = [len(claim["text"]) for claim in generated["claims"]]
    metrics = {
        "hard_gate_pass": not any(item["severity"] == "error" for item in findings),
        "critical_fact_recall": round(critical_recall, 4),
        "required_fact_recall": round(required_recall, 4),
        "citation_completeness": round(citation_present / citation_expected, 4) if citation_expected else 1.0,
        "citation_precision": round(citation_relevant / citation_actual, 4) if citation_actual else 1.0,
        "claim_count": len(generated["claims"]),
        "average_claim_chars": round(statistics.mean(lengths), 2) if lengths else 0,
        "maximum_claim_chars": max(lengths, default=0),
        "maximum_source_copy_ratio": round(max(copy_ratios, default=0.0), 4),
        "warning_count": sum(item["severity"] == "warning" for item in findings),
        "error_count": sum(item["severity"] == "error" for item in findings),
    }
    return {"metrics": metrics, "findings": findings,
            "matched_fact_ids": sorted(matched_fact_ids & set(fact_by_id))}
