# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Deterministic host checks that hold whichever model, provider or local server wrote the draft.

Models and serving stacks differ in what they drop or misdate. These checks use only facts the host
already has: each note's authoritative date and its verbatim text. They never rewrite model prose.
"""
import copy
import datetime
import re

RESULTS = {"id": "results_observations", "title": "Results and observations"}
# A label, an optional joining word or punctuation, then the recorded value.
JOIN = r"\s*(?:of|is|was|at|:|=|-)?\s*"
MEASUREMENTS = (
    ("hr", r"\b(?:HR|heart rate|pulse)\b", r"\d{1,3}"),
    ("bp", r"\b(?:BP|blood pressure)\b", r"\d{2,3}/\d{2,3}"),
    ("rr", r"\b(?:RR|resp(?:iratory)? rate)\b", r"\d{1,2}"),
    ("temp", r"\b(?:temp(?:erature)?)\b", r"\d{2}(?:\.\d)?"),
    ("spo2", r"\b(?:SpO2|SaO2|O2 sats?|sats?|oxygen saturations?)\b", r"\d{2,3}"),
)
FINDER = re.compile("|".join(f"(?P<{kind}>{label}{JOIN}(?P<{kind}_value>{value})(?![\\d/.]))"
                             for kind, label, value in MEASUREMENTS), re.IGNORECASE)
LABELS = {kind: label for kind, label, _value in MEASUREMENTS}
MAX_GAP = 80  # Characters between neighbouring measurements of one observation set.
MIN_KINDS = 3


def claim_dates(text):
    """Dates a claim asserts, normalized to dd/mm/yy for comparison."""
    # A longer slash run such as "note-10/12/13/14" lists note IDs, and 14/15/18 is no calendar date.
    found = {f"{day}/{month}/{year}"
             for day, month, year in re.findall(r"(?<![\d/])(\d{2})/(\d{2})/(\d{2})(?![\d/])", text)
             if 1 <= int(day) <= 31 and 1 <= int(month) <= 12}
    for iso in re.findall(r"\b(\d{4})-(\d{2})-(\d{2})\b", text):
        found.add(f"{iso[2]}/{iso[1]}/{iso[0][2:]}")
    return found


def supported_dates(date, text):
    """The note's own date, any date written in it, and a stated tomorrow or 48 hours."""
    stamp = datetime.date.fromisoformat(date[:10])
    allowed = {stamp.strftime("%d/%m/%y")} | claim_dates(text)
    # These records abbreviate heavily, so accept the shorthand forms too. Anything further adrift
    # is still unsupported, which keeps a misdating with no relative wording caught.
    if re.search(r"tomorrow|tmrw|tmw|next day|following (morning|day)|24 hours", text, re.IGNORECASE):
        allowed.add((stamp + datetime.timedelta(days=1)).strftime("%d/%m/%y"))
    if re.search(r"48 hours|two days|day after tomorrow", text, re.IGNORECASE):
        allowed.add((stamp + datetime.timedelta(days=2)).strftime("%d/%m/%y"))
    return allowed


def date_findings(output, sources):
    """Report each date a claim asserts that none of its cited notes carries."""
    allowed = {source["id"]: supported_dates(source["date"], source["text"]) for source in sources}
    findings = []
    for claim in output["claims"]:
        carried = set().union(*(allowed.get(source_id, set()) for source_id in claim["source_ids"]))
        for asserted in sorted(claim_dates(claim["text"]) - carried):
            findings.append({"claim_id": claim["id"], "asserted": asserted,
                             "allowed": sorted(carried, key=lambda value: value.split("/")[::-1]),
                             "source_ids": list(claim["source_ids"])})
    return findings


def observation_sets(text):
    """Clusters of at least three kinds of vital-sign measurement recorded close together."""
    sets, current = [], []

    def close():
        if len({kind for kind, _value, _span in current}) >= MIN_KINDS:
            sets.append({"measurements": [(kind, value) for kind, value, _span in current],
                         "text": "; ".join(re.sub(r"\s+", " ", span) for _kind, _value, span in current)})

    previous_end = None
    for match in FINDER.finditer(text):
        if previous_end is not None and match.start() - previous_end > MAX_GAP:
            close()
            current = []
        kind = match.lastgroup if match.lastgroup in LABELS else next(k for k in LABELS if match.group(k))
        current.append((kind, match.group(kind + "_value"), match.group(kind)))
        previous_end = match.end()
    close()
    return sets


def reports(claim_text, measurements):
    """Whether one claim states every measurement of a set beside a recognizable label."""
    return all(re.search(LABELS[kind] + r"[^.;]{0,40}?(?<![\d/.])" + re.escape(value) + r"(?![\d/])",
                         claim_text, re.IGNORECASE) for kind, value in measurements)


def restore_observations(output, sources):
    """Add back, verbatim and labelled, each observation set the draft omitted. Returns (output, added)."""
    result = copy.deepcopy(output)
    added = {}
    prose = " ".join(claim["text"] for claim in result["claims"])
    # Follow the draft's own date format so a host statement never makes the formats mixed.
    iso = bool(re.search(r"\b\d{4}-\d{2}-\d{2}\b", prose)) and not re.search(r"\b\d{2}/\d{2}/\d{2}\b", prose)
    for source in sources:
        cited_by = [claim["text"] for claim in result["claims"] if source["id"] in claim["source_ids"]]
        stamp = datetime.date.fromisoformat(source["date"][:10])
        day = stamp.isoformat() if iso else stamp.strftime("%d/%m/%y")
        for entry in observation_sets(source["text"]):
            if any(reports(text, entry["measurements"]) for text in cited_by):
                continue
            text = (f"Observations recorded on {day}, restored verbatim by the host because the draft "
                    f"omitted them: {entry['text']}.")
            if text in added:
                if source["id"] not in added[text]["source_ids"]:
                    added[text]["source_ids"].append(source["id"])
                continue
            added[text] = {"id": f"host-obs-{len(added) + 1}", "source_ids": [source["id"]], "text": text}
            cited_by.append(text)
    if not added:
        return result, 0
    section = next((row for row in result["sections"] if row["id"] == RESULTS["id"]), None)
    if section is None:
        section = dict(RESULTS, claim_ids=[])
        result["sections"].append(section)
    for claim in added.values():
        result["claims"].append(claim)
        section["claim_ids"].append(claim["id"])
    return result, len(added)
