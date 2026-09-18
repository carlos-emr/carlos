#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Score an NHSSYN001 trial artifact for labelled-fact recall and the known defect classes.

Engineering checks over an invented record. Passing here is not clinical validation and
says nothing about statements the labelled ledger does not cover.
"""
import argparse
import itertools
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import openrouter_agent as agent  # noqa: E402

LEDGER = json.loads((ROOT / "quality/nhssyn001-facts.json").read_text())
STOP = {"the", "a", "an", "on", "of", "and", "with", "no", "to", "in", "at", "was", "were", "for", "is"}


def source_dates():
    """Map note IDs to their authoritative source date plus any date literally in the body."""
    dates = {}
    for index, (fixture, date, body) in enumerate(
            (n for n in agent.SyntheticNotes().notes if n[0] == "NHSSYN001"), start=1):
        iso = date
        day, month, year = iso[8:10], iso[5:7], iso[2:4]
        literal = set(re.findall(r"\b\d{2}/\d{2}/\d{2}\b", body))
        dates[f"note-{index}"] = {"iso": iso, "slash": f"{day}/{month}/{year}", "literal": literal}
    return dates


def claim_dates(text):
    """Dates a claim asserts, normalized to dd/mm/yy for comparison."""
    found = set(re.findall(r"\b\d{2}/\d{2}/\d{2}\b", text))
    for iso in re.findall(r"\b(\d{4})-(\d{2})-(\d{2})\b", text):
        found.add(f"{iso[2]}/{iso[1]}/{iso[0][2:]}")
    return found


def tokens(text):
    return set(re.findall(r"[a-z0-9.]+", text.lower())) - STOP


def score(artifact):
    claims = artifact["claims"]
    section_of = {cid: s["title"] for s in artifact["sections"] for cid in s["claim_ids"]}
    dates = source_dates()
    findings = []

    matched = set()
    for fact in LEDGER["facts"]:
        for claim in claims:
            if all(any(re.search(p, claim["text"], re.IGNORECASE) for p in group)
                   for group in fact["pattern_groups"]):
                matched.add(fact["id"])
                break
    critical = [f["id"] for f in LEDGER["facts"] if f["critical"]]
    missing_critical = [f for f in critical if f not in matched]
    for fact_id in missing_critical:
        findings.append({"code": "MISSING_CRITICAL_FACT", "fact_id": fact_id})

    # A claim may only assert a date its cited sources actually carry.
    date_errors = 0
    for claim in claims:
        allowed = set()
        for sid in claim["source_ids"]:
            entry = dates.get(sid)
            if entry:
                allowed |= {entry["slash"]} | entry["literal"]
        for asserted in claim_dates(claim["text"]):
            if asserted not in allowed:
                date_errors += 1
                findings.append({"code": "DATE_NOT_IN_CITED_SOURCE", "claim_id": claim["id"],
                                 "asserted": asserted, "allowed": sorted(allowed)})

    leaks = [{"code": "NAME_LEAK", "claim_id": c["id"], "term": n}
             for c in claims for n in LEDGER["staff_names"] if n in c["text"]]
    identity = [{"code": "IDENTITY_LEAK", "claim_id": c["id"], "term": t}
                for c in claims for t in LEDGER["identity_terms"] if t in c["text"]]
    findings += leaks + identity

    duplicates = []
    for one, two in itertools.combinations(claims, 2):
        if section_of.get(one["id"]) == section_of.get(two["id"]):
            continue
        if not set(one["source_ids"]) & set(two["source_ids"]):
            continue
        first, second = tokens(one["text"]), tokens(two["text"])
        overlap = len(first & second) / max(1, len(first | second))
        if overlap >= 0.30:
            duplicates.append({"code": "CROSS_SECTION_DUPLICATE", "claim_ids": [one["id"], two["id"]],
                               "jaccard": round(overlap, 2)})
    findings += duplicates

    formats = set()
    for claim in claims:
        if re.search(r"\b\d{2}/\d{2}/\d{2}\b", claim["text"]):
            formats.add("slash")
        if re.search(r"\b\d{4}-\d{2}-\d{2}\b", claim["text"]):
            formats.add("iso")
    if len(formats) > 1:
        findings.append({"code": "MIXED_DATE_FORMATS", "formats": sorted(formats)})

    metrics = {
        "claims": len(claims),
        "fact_recall": round(len(matched) / len(LEDGER["facts"]), 3),
        "critical_recall": round((len(critical) - len(missing_critical)) / len(critical), 3),
        "missing_critical": missing_critical,
        "date_errors": date_errors,
        "name_leaks": len(leaks),
        "identity_leaks": len(identity),
        "cross_section_duplicates": len(duplicates),
        "mixed_date_formats": len(formats) > 1,
    }
    # The gate deliberately excludes readability, which these checks cannot measure.
    metrics["gate_pass"] = (not missing_critical and date_errors == 0 and not leaks
                            and not identity and len(duplicates) == 0 and len(formats) <= 1)
    return {"metrics": metrics, "findings": findings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    data = json.loads(args.artifact.read_text())
    artifact = data.get("output", data)
    if "claims" not in artifact:
        # A rejected trial has no artifact to score; report why rather than crashing.
        print(json.dumps({"status": data.get("status", "rejected"), "error": data.get("error")}))
        return 1
    result = score(artifact)
    print(json.dumps(result["metrics"], indent=None if args.quiet else 2))
    if not args.quiet:
        for finding in result["findings"][:20]:
            print("  ", json.dumps(finding))
    return 0 if result["metrics"]["gate_pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
