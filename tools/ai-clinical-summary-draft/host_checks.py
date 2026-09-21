# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
"""Deterministic host checks that hold whichever model, provider or local server wrote the draft.

Models and serving stacks differ in what they drop or misdate. These checks use only facts the host
already has: each note's authoritative date and its verbatim text. They never rewrite model prose.
"""
import copy
import datetime
import json
import os
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
# A value may end a sentence ("SpO2 98."), but "36" is never read out of "36.8".
FINDER = re.compile("|".join(f"(?P<{kind}>{label}{JOIN}(?P<{kind}_value>{value})(?![\\d/]|\\.\\d))"
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


def day_writer(claims):
    """Write a note's date in the draft's own format, so a host statement never makes the formats mixed."""
    prose = " ".join(claim["text"] for claim in claims)
    iso = bool(re.search(r"\b\d{4}-\d{2}-\d{2}\b", prose)) and not re.search(r"\b\d{2}/\d{2}/\d{2}\b", prose)

    def write(date):
        stamp = datetime.date.fromisoformat(date[:10])
        return stamp.isoformat() if iso else stamp.strftime("%d/%m/%y")
    return write


def restore_observations(output, sources):
    """Add back, verbatim and labelled, each observation set the draft omitted. Returns (output, added)."""
    result = copy.deepcopy(output)
    added = {}
    write = day_writer(result["claims"])
    for source in sources:
        cited_by = [claim["text"] for claim in result["claims"] if source["id"] in claim["source_ids"]]
        day = write(source["date"])
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


MEDICATIONS = {"id": "medications_allergies", "title": "Medications and allergies"}
# An order, not a mention: a dose beside the name, or an ordering verb before it.
DOSE = r"[^.;\n]{0,40}?\d[\d,.]*\s*(?:mg|mcg|micrograms?|g|units?|iu|ml|mmol)\b"
ORDERED = r"(?:start|commenc|prescrib|continu|give|administer|initiat)\w*[^.;\n]{0,30}?$"
# Anchored to the start of a word: an unanchored "stop" is found inside "postoperative".
CHANGE = r"\b(?:stop|discontinu|ceas|withh|held\b|switch|chang|replac|instead of|convert|transition)"
REPORTED = r"conflict|discrepan|inconsisten|unresolved"
NEAR = 60  # Characters either side of a drug name in which a stop or switch counts as recorded for it.


_NAME_PATTERNS = {}
_CLASSES = {}


def configured_classes():
    """The drug-name to ATC-code table named by CARLOS_DRUG_CLASSES, or None so the check is skipped.

    The table is derived from the site's drug reference database and is not committed: the ATC
    classification belongs to the WHO Collaborating Centre for Drug Statistics Methodology.
    """
    path = os.environ.get("CARLOS_DRUG_CLASSES")
    if not path:
        return None
    if path not in _CLASSES:
        with open(path, encoding="utf-8") as stream:
            table = json.load(stream)
        if not (isinstance(table, dict) and all(isinstance(k, str) and isinstance(v, str) and len(v) >= 5
                                                for k, v in table.items())):
            raise ValueError("Invalid drug class table")
        _CLASSES[path] = {name.lower(): code for name, code in table.items()}
    return _CLASSES[path]


def name_pattern(classes):
    """One compiled alternation per class table; tables hold well over a thousand names."""
    key = id(classes)
    if key not in _NAME_PATTERNS:
        names = sorted(classes, key=len, reverse=True)
        _NAME_PATTERNS[key] = re.compile(r"\b(" + "|".join(re.escape(name) for name in names) + r")\b", re.IGNORECASE)
    return _NAME_PATTERNS[key]


def drug_mentions(text, classes):
    """Each class-table name in the text: (name, whether it is ordered there, whether a change is recorded)."""
    for match in name_pattern(classes).finditer(text):
        before, after = text[max(0, match.start() - NEAR):match.start()], text[match.end():match.end() + 80]
        ordered = bool(re.match(DOSE, after, re.IGNORECASE) or re.search(ORDERED, before, re.IGNORECASE))
        changed = bool(re.search(CHANGE, before + after[:NEAR], re.IGNORECASE))
        yield match.group(1).lower(), ordered, changed


def medication_conflicts(sources, classes):
    """Two drugs of one class, each ordered somewhere in the record, with no recorded stop or switch.

    The class table maps a drug name to its ATC code; drugs sharing the first five characters are one
    chemical subgroup, such as the heparins. With no table there is nothing to compare, so no findings.
    """
    if not classes:
        return []
    groups, changed = {}, set()
    for source in sources:
        for name, ordered, change in drug_mentions(source["text"], classes):
            if ordered:
                groups.setdefault(classes[name][:5], {}).setdefault(name, []).append(source["id"])
            if change:
                changed.add(name)
    return [{"group": group, "drugs": {name: list(dict.fromkeys(ids)) for name, ids in drugs.items()}}
            for group, drugs in groups.items() if len(drugs) > 1 and not changed & set(drugs)]


def note_medication_conflicts(output, sources, classes):
    """State each same-class conflict the draft did not report itself. Returns (output, added)."""
    result = copy.deepcopy(output)
    write = day_writer(result["claims"])
    dates = {source["id"]: write(source["date"]) for source in sources}
    added = []
    for conflict in medication_conflicts(sources, classes):
        names = list(conflict["drugs"])
        if any(all(re.search(r"\b" + re.escape(name) + r"\b", claim["text"], re.IGNORECASE) for name in names)
               and re.search(REPORTED, claim["text"], re.IGNORECASE) for claim in result["claims"]):
            continue
        described = [f"{name} ({', '.join(dict.fromkeys(dates[i] for i in ids))})" for name, ids in conflict["drugs"].items()]
        text = ("Medication records conflict, as found by the host: " + " and ".join(described)
                + f" belong to the same drug class (ATC {conflict['group']}) and no note records either being "
                "stopped, so the record does not show which is intended.")
        ids = list(dict.fromkeys(i for listed in conflict["drugs"].values() for i in listed))
        added.append({"id": f"host-med-{len(added) + 1}", "source_ids": ids, "text": text})
    if not added:
        return result, 0
    section = next((row for row in result["sections"] if row["id"] == MEDICATIONS["id"]), None)
    if section is None:
        section = dict(MEDICATIONS, claim_ids=[])
        result["sections"].append(section)
    for claim in added:
        result["claims"].append(claim)
        section["claim_ids"].append(claim["id"])
    return result, len(added)


def undocumented_changes(output, sources, classes):
    """Claims that say one drug of a conflicting pair was switched or changed when no note records it."""
    findings = []
    for conflict in medication_conflicts(sources, classes):
        names = sorted(conflict["drugs"])
        for claim in output["claims"]:
            if claim["id"].startswith("host-"):
                continue
            for name in names:
                match = re.search(r"\b" + re.escape(name) + r"\b", claim["text"], re.IGNORECASE)
                if match and re.search(r"\b(?:switch|chang|convert|transition|replac)",
                                       claim["text"][max(0, match.start() - NEAR):match.end() + NEAR], re.IGNORECASE):
                    findings.append({"claim_id": claim["id"], "drugs": names, "source_ids": list(claim["source_ids"])})
                    break
    return findings
