#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Score an NHS synthetic trial artifact for labelled-fact recall and the known defect classes.

Engineering checks over an invented record. Passing here is not clinical validation and
says nothing about statements the labelled ledger does not cover.
"""
import argparse
import datetime
import itertools
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from host_checks import claim_dates
import openrouter_agent as agent  # noqa: E402

FIXTURES = ("NHSSYN001", "NHSSYN002", "NHSSYN003")


def ledger_for(fixture):
    return json.loads((ROOT / f"quality/facts/{fixture.lower()}.json").read_text())
STOP = {"the", "a", "an", "on", "of", "and", "with", "no", "to", "in", "at", "was", "were", "for", "is"}


def source_dates(fixture):
    """Map note IDs to their authoritative source date plus any date literally in the body."""
    dates = {}
    for index, (_fixture, date, body) in enumerate(
            (n for n in agent.SyntheticNotes().notes if n[0] == fixture), start=1):
        iso = date
        day, month, year = iso[8:10], iso[5:7], iso[2:4]
        literal = set(re.findall(r"\b\d{2}/\d{2}/\d{2}\b", body))
        # A note that plans something for 'tomorrow' or 'in 48 hours' supports the corresponding
        # absolute date, so allow that derivation. Anything further is still an unsupported date,
        # which keeps the note-12 style misdating (three days adrift, no relative wording) caught.
        derived = set()
        stamp = datetime.date.fromisoformat(iso)
        # These records abbreviate heavily, so accept the shorthand forms too.
        if re.search(r"tomorrow|tmrw|tmw|next day|following (morning|day)|24 hours",
                     body, re.IGNORECASE):
            derived.add((stamp + datetime.timedelta(days=1)).strftime("%d/%m/%y"))
        if re.search(r"48 hours|two days|day after tomorrow", body, re.IGNORECASE):
            derived.add((stamp + datetime.timedelta(days=2)).strftime("%d/%m/%y"))
        dates[f"note-{index}"] = {"iso": iso, "slash": f"{day}/{month}/{year}",
                                  "literal": literal | derived}
    return dates


def tokens(text):
    return set(re.findall(r"[a-z0-9.]+", text.lower())) - STOP


def score(artifact, fixture):
    ledger = ledger_for(fixture)
    claims = artifact["claims"]
    section_of = {cid: s["title"] for s in artifact["sections"] for cid in s["claim_ids"]}
    dates = source_dates(fixture)
    findings = []

    matched = set()
    for fact in ledger["facts"]:
        for claim in claims:
            if all(any(re.search(p, claim["text"], re.IGNORECASE) for p in group)
                   for group in fact["pattern_groups"]):
                matched.add(fact["id"])
                break
    critical = [f["id"] for f in ledger["facts"] if f["critical"]]
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
             for c in claims for n in ledger["staff_names"] if n in c["text"]]
    identity = [{"code": "IDENTITY_LEAK", "claim_id": c["id"], "term": t}
                for c in claims for t in ledger["identity_terms"] if t in c["text"]]
    forbidden = [{"code": "FORBIDDEN_ASSERTION", "claim_id": c["id"], "rule": rule["id"],
                  "why": rule["why"]}
                 for c in claims for rule in ledger.get("forbidden_patterns", [])
                 if re.search(rule["pattern"], c["text"], re.IGNORECASE)]
    findings += leaks + identity + forbidden

    duplicates = []
    for one, two in itertools.combinations(claims, 2):
        if section_of.get(one["id"]) == section_of.get(two["id"]):
            continue
        if not set(one["source_ids"]) & set(two["source_ids"]):
            continue
        first, second = tokens(one["text"]), tokens(two["text"])
        shared = first & second
        overlap = len(shared) / max(1, len(first | second))
        # Jaccard alone flags distinct facts that merely share a condition name and date, such as a
        # lab result beside the drug prescribed for it. Genuine restatements also largely contain
        # one another: on labelled pairs the false ones sat at 0.50 containment and real ones at
        # 0.60-0.83. Requiring both costs two borderline true positives and removes the false ones.
        containment = len(shared) / max(1, min(len(first), len(second)))
        if overlap >= 0.30 and containment >= 0.60:
            duplicates.append({"code": "CROSS_SECTION_DUPLICATE", "claim_ids": [one["id"], two["id"]],
                               "jaccard": round(overlap, 2), "containment": round(containment, 2)})
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
        "fact_recall": round(len(matched) / len(ledger["facts"]), 3),
        "critical_recall": round((len(critical) - len(missing_critical)) / len(critical), 3),
        "missing_critical": missing_critical,
        "date_errors": date_errors,
        "name_leaks": len(leaks),
        "identity_leaks": len(identity),
        "forbidden_assertions": len(forbidden),
        "cross_section_duplicates": len(duplicates),
        "mixed_date_formats": len(formats) > 1,
    }
    # The gate deliberately excludes readability, which these checks cannot measure.
    metrics["gate_pass"] = (not missing_critical and date_errors == 0 and not leaks
                            and not identity and not forbidden and len(duplicates) == 0
                            and len(formats) <= 1)
    return {"metrics": metrics, "findings": findings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--fixture", choices=FIXTURES)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    data = json.loads(args.artifact.read_text())
    artifact = data.get("output", data)
    if "claims" not in artifact:
        # A rejected trial has no artifact to score; report why rather than crashing.
        print(json.dumps({"status": data.get("status", "rejected"), "error": data.get("error")}))
        return 1
    fixture = args.fixture or data.get("fixture")
    if fixture not in FIXTURES:
        parser.exit(2, "Pass --fixture; the report does not name one.\n")
    result = score(artifact, fixture)
    print(json.dumps(result["metrics"], indent=None if args.quiet else 2))
    if not args.quiet:
        for finding in result["findings"][:20]:
            print("  ", json.dumps(finding))
    return 0 if result["metrics"]["gate_pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
