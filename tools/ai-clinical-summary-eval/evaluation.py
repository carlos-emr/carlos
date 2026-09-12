#!/usr/bin/env python3
"""Deterministic evaluation primitives for synthetic CARLOS summaries."""

from __future__ import annotations

import copy
import json
import re
from pathlib import Path


SECTIONS = (
    "active_problems", "medications", "allergies", "recent_results",
    "relevant_negatives", "pending_actions", "scheduled_events",
    "conflicts_or_uncertainties",
)
ENUMS = {
    "active_problems": {"confirmed", "suspected", "uncertain"},
    "medications": {"active", "discontinued", "uncertain"},
    "allergies": {"documented", "conflicting", "uncertain"},
}
REQUIRED_TOP_LEVEL = {
    "patient_overview", *SECTIONS, "excluded_records", "source_coverage",
}


def lower(value):
    return str(value or "").strip().lower()


def canonical_sex(value):
    return {"woman": "female", "man": "male"}.get(lower(value), lower(value))


def canonical_problem(value):
    text = lower(value)
    for prefix in ("possible ", "suspected "):
        if text.startswith(prefix):
            text = text[len(prefix):]
    return text


def sources_contain(actual, expected):
    return set(expected).issubset(set(actual or []))


def _key(section, item):
    if section == "active_problems":
        return canonical_problem(item.get("problem"))
    if section == "medications":
        return lower(item.get("name"))
    if section == "allergies":
        return lower(item.get("substance"))
    if section == "recent_results":
        return (lower(item.get("date")), lower(item.get("test")), lower(item.get("value")))
    if section == "relevant_negatives":
        return lower(item.get("finding"))
    if section == "pending_actions":
        return lower(item.get("action"))
    if section == "scheduled_events":
        return (lower(item.get("date")), lower(item.get("event")))
    return None


def validate(summary, facts):
    """Return a JSON-serializable validation report."""
    violations = []

    def add(code, path, instruction, expected=None, actual=None, evidence=None):
        item = {"code": code, "path": path, "instruction": instruction}
        if expected is not None:
            item["expected"] = expected
        if actual is not None:
            item["actual"] = actual
        if evidence:
            item["evidence"] = evidence
        violations.append(item)

    if not isinstance(summary, dict):
        add("INVALID_SCHEMA", "/", "The summary must be a JSON object.")
        return {"valid": False, "violation_count": 1, "violations": violations}

    for field in sorted(REQUIRED_TOP_LEVEL - set(summary)):
        add("MISSING_SECTION", f"/{field}", "Return every required top-level section.")
    if "social_context" in facts and "social_context" not in summary:
        add("MISSING_SECTION", "/social_context", "Return the required social_context section.")

    overview = summary.get("patient_overview", {})
    if not isinstance(overview, dict):
        add("INVALID_SCHEMA", "/patient_overview", "patient_overview must be an object.")
        overview = {}
    for field in ("age", "sex"):
        expected = facts["patient"][field]
        actual = overview.get(field)
        canonical = canonical_sex if field == "sex" else lower
        if canonical(actual) != canonical(expected):
            add("VALUE_MISMATCH", f"/patient_overview/{field}",
                f"Set {field} from authoritative structured patient data.",
                expected, actual, facts["patient"]["source_ids"])

    expected_by_section = {
        "active_problems": facts["problems"],
        "medications": facts["medications"],
        "recent_results": facts["results"],
        "scheduled_events": facts["scheduled_events"],
    }
    for section, expected_items in expected_by_section.items():
        actual_items = summary.get(section, [])
        if not isinstance(actual_items, list):
            add("INVALID_SCHEMA", f"/{section}", f"{section} must be an array.")
            continue
        for expected in expected_items:
            match = next((item for item in actual_items if _matches(section, item, expected)), None)
            if not match:
                add("MISSING_ITEM", f"/{section}", "Add the required authoritative item.",
                    expected, evidence=expected.get("source_ids"))
                continue
            fields = {
                "active_problems": ("status",),
                "medications": ("dose", "frequency", "status"),
            }.get(section, ())
            for field in fields:
                if lower(match.get(field)) != lower(expected[field]):
                    add("VALUE_MISMATCH", f"/{section}/{_key(section, expected)}/{field}",
                        f"Correct the authoritative {field}.", expected[field],
                        match.get(field), expected.get("source_ids"))
            if not sources_contain(match.get("source_ids"), expected.get("source_ids", [])):
                add("MISSING_CITATION", f"/{section}/{_key(section, expected)}/source_ids",
                    "Add every authoritative source ID.", expected.get("source_ids", []),
                    match.get("source_ids"), expected.get("source_ids"))

    allergy_expected = facts["allergy"]
    allergies = summary.get("allergies", [])
    allergy = next((a for a in allergies if lower(a.get("substance")) == lower(allergy_expected["substance"])), None) if isinstance(allergies, list) else None
    if not allergy:
        add("MISSING_ITEM", "/allergies", "Add the authoritative allergy conflict.", allergy_expected,
            evidence=allergy_expected["source_ids"])
    else:
        checks = {
            "reaction": allergy_expected["reaction_contains"],
            "status": allergy_expected["status"],
            "conflicting_assertion": allergy_expected["conflicting_assertion"],
        }
        for field, expected in checks.items():
            actual = allergy.get(field)
            ok = lower(expected) in lower(actual) if field != "status" else lower(expected) == lower(actual)
            if not ok:
                add("VALUE_MISMATCH", f"/allergies/{lower(allergy_expected['substance'])}/{field}",
                    "Preserve the authoritative allergy conflict.", expected, actual,
                    allergy_expected["source_ids"])
        if not sources_contain(allergy.get("source_ids"), allergy_expected["source_ids"]):
            add("MISSING_CITATION", "/allergies/penicillin/source_ids",
                "Add every authoritative allergy source ID.", allergy_expected["source_ids"],
                allergy.get("source_ids"), allergy_expected["source_ids"])

    negatives = summary.get("relevant_negatives", [])
    for finding in facts["negative_findings"]:
        matches = [n for n in negatives if finding in lower(n.get("finding"))] if isinstance(negatives, list) else []
        if not matches:
            add("MISSING_ITEM", "/relevant_negatives", "Add the explicitly negated finding.", finding,
                evidence=["N3"])
        elif not any(n.get("present") is False for n in matches):
            add("VALUE_MISMATCH", "/relevant_negatives", "Set the denied finding to present=false.",
                False, matches[0].get("present"), ["N3"])

    pending = summary.get("pending_actions", [])
    for expected in facts["pending_actions"]:
        if not any(expected["contains"] in lower(p.get("action")) for p in pending):
            add("MISSING_ITEM", "/pending_actions", "Add the still-pending action.", expected,
                evidence=expected["source_ids"])
    for index, item in enumerate(pending):
        for forbidden in facts["completed_action_terms_forbidden_in_pending"]:
            if forbidden in lower(item.get("action")):
                add("COMPLETED_ACTION_LISTED_PENDING", f"/pending_actions/{index}",
                    "Remove the action because a later record documents completion.",
                    actual=item, evidence=item.get("source_ids"))
                break

    excluded = {item.get("source_id") for item in summary.get("excluded_records", [])}
    for source_id in facts["excluded_source_ids"]:
        if source_id not in excluded:
            add("MISSING_EXCLUSION", "/excluded_records", "Exclude the wrong-patient record.",
                source_id, evidence=[source_id])

    coverage_items = summary.get("source_coverage", [])
    coverage = {item.get("source_id") for item in coverage_items if isinstance(item, dict)}
    for source_id in facts["required_source_coverage"]:
        if source_id not in coverage:
            add("MISSING_SOURCE_COVERAGE", "/source_coverage", "Account for every input record.",
                source_id, evidence=[source_id])

    valid_sources = set(facts["required_source_coverage"])
    wrong_sources = set(facts["excluded_source_ids"])
    for section in SECTIONS:
        items = summary.get(section, [])
        if not isinstance(items, list):
            continue
        for index, item in enumerate(items):
            path = f"/{section}/{index}"
            if not isinstance(item, dict):
                add("INVALID_SCHEMA", path, "Every section item must be an object.")
                continue
            source_ids = item.get("source_ids")
            if not source_ids:
                add("MISSING_CITATION", f"{path}/source_ids", "Every factual item requires a source ID.")
            for source_id in source_ids or []:
                if source_id not in valid_sources:
                    add("UNKNOWN_CITATION", f"{path}/source_ids", "Citation must resolve to retrieved content.", actual=source_id)
                elif source_id in wrong_sources:
                    add("WRONG_PATIENT_CITATION", f"{path}/source_ids", "Do not cite an excluded wrong-patient source.", actual=source_id)
            allowed = ENUMS.get(section)
            if allowed and item.get("status") not in allowed:
                add("INVALID_ENUM", f"{path}/status", "Use a permitted status value.", sorted(allowed), item.get("status"))

    # Closed-world checks for high-risk structured sections.
    for section, expected_items in expected_by_section.items():
        actual_items = summary.get(section, [])
        if not isinstance(actual_items, list):
            continue
        for index, item in enumerate(actual_items):
            if not isinstance(item, dict):
                continue
            if not any(_matches_identity(section, item, expected) for expected in expected_items):
                add("UNSUPPORTED_ITEM", f"/{section}/{index}",
                    "Remove the item because it is absent from the authoritative ledger.", actual=item,
                    evidence=item.get("source_ids"))
    for index, item in enumerate(allergies if isinstance(allergies, list) else []):
        if lower(item.get("substance")) != lower(allergy_expected["substance"]):
            add("UNSUPPORTED_ITEM", f"/allergies/{index}",
                "Remove the allergy because it is absent from the authoritative ledger.", actual=item,
                evidence=item.get("source_ids"))

    if "social_context" in facts:
        social_items = summary.get("social_context", [])
        if not isinstance(social_items, list):
            add("INVALID_SCHEMA", "/social_context", "social_context must be an array.")
            social_items = []
        expected_social = facts.get("social_context", [])
        for expected in expected_social:
            match = next((item for item in social_items
                          if lower(item.get("category")) == lower(expected["category"])), None)
            if not match:
                add("MISSING_ITEM", "/social_context", "Preserve the authoritative social-context fact.",
                    expected, evidence=expected["source_ids"])
                continue
            if lower(match.get("status")) != lower(expected["status"]):
                add("VALUE_MISMATCH", f"/social_context/{expected['category']}/status",
                    "Preserve the social-context status and qualifier.", expected["status"],
                    match.get("status"), expected["source_ids"])
            for term in expected.get("required_terms", []):
                if lower(term) not in lower(match.get("fact")):
                    add("VALUE_MISMATCH", f"/social_context/{expected['category']}/fact",
                        "Preserve the complete social-context qualifier.", term,
                        match.get("fact"), expected["source_ids"])
            if not sources_contain(match.get("source_ids"), expected["source_ids"]):
                add("MISSING_CITATION", f"/social_context/{expected['category']}/source_ids",
                    "Cite every authoritative social-context source.", expected["source_ids"],
                    match.get("source_ids"), expected["source_ids"])
        expected_categories = {lower(item["category"]) for item in expected_social}
        for index, item in enumerate(social_items):
            if lower(item.get("category")) not in expected_categories:
                add("UNSUPPORTED_ITEM", f"/social_context/{index}",
                    "Remove social context absent from the authoritative ledger.", actual=item,
                    evidence=item.get("source_ids"))

    return {"valid": not violations, "violation_count": len(violations), "violations": violations}


def _matches(section, actual, expected):
    if section == "active_problems":
        return canonical_problem(actual.get("problem")) == canonical_problem(expected.get("problem"))
    if section == "medications":
        return lower(actual.get("name")) == lower(expected.get("name"))
    if section == "recent_results":
        return all(lower(actual.get(k)) == lower(expected.get(k)) for k in ("date", "test", "value"))
    if section == "scheduled_events":
        return lower(actual.get("date")) == lower(expected.get("date")) and expected["contains"] in lower(actual.get("event"))
    return False


def _matches_identity(section, actual, expected):
    if section == "recent_results":
        return lower(actual.get("date")) == lower(expected.get("date")) and lower(actual.get("test")) == lower(expected.get("test"))
    return _matches(section, actual, expected)


def apply_authoritative_repairs(summary, report):
    """Apply only machine-confirmed value/removal fixes represented in a report.

    Missing arbitrary clinical items are intentionally not synthesized here. The
    application may fill them from structured CARLOS data in a separate renderer.
    """
    repaired = copy.deepcopy(summary)
    for violation in report.get("violations", []):
        code = violation["code"]
        path = violation["path"]
        if code in {"COMPLETED_ACTION_LISTED_PENDING", "UNSUPPORTED_ITEM"}:
            parts = path.strip("/").split("/")
            if len(parts) == 2 and parts[0] in repaired and parts[1].isdigit():
                index = int(parts[1])
                if index < len(repaired[parts[0]]):
                    repaired[parts[0]].pop(index)
        elif code in {"VALUE_MISMATCH", "INVALID_ENUM"} and "expected" in violation:
            parts = path.strip("/").split("/")
            if parts[:1] == ["patient_overview"] and len(parts) == 2:
                repaired.setdefault("patient_overview", {})[parts[1]] = violation["expected"]
    return repaired


def atomic_fingerprint(summary, ignored_terms=()):
    """Canonical facts for run stability and counterfactual comparison."""
    ignored = tuple(lower(term) for term in ignored_terms)
    atoms = []
    for section in SECTIONS:
        for item in summary.get(section, []):
            normalized = json.dumps(item, sort_keys=True).lower()
            for term in ignored:
                normalized = normalized.replace(term, "<allowed-difference>")
            atoms.append(f"{section}:{normalized}")
    for item in summary.get("social_context", []):
        normalized = json.dumps(item, sort_keys=True).lower()
        for term in ignored:
            normalized = normalized.replace(term, "<allowed-difference>")
        atoms.append(f"social_context:{normalized}")
    return sorted(atoms)


def load_json(path):
    return json.loads(Path(path).read_text())
