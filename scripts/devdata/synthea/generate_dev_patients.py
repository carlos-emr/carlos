#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Builds the development-only synthetic patients (#6148) from Synthea's ready-made CSV sample.

    python3 -I scripts/devdata/synthea/generate_dev_patients.py \\
        --zip <synthea_sample_data_csv_latest.zip> \\
        --out .devcontainer/db/scripts/demo-synthetic-patients.sql

The zip is the pinned sample (SAMPLE_URL, SAMPLE_SHA256); it stays out of the repository. The
output is deterministic: the same zip always gives the same file, byte for byte. See README.md for
the selection, the FAKE identities, the mapping and the clean-up rules.

Development data only: populate_db.sh loads the file after development.sql. It is never applied to a
production database.
"""
import argparse
import csv
import hashlib
import io
import random
import re
import sys
import zipfile
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import codemaps as cm  # noqa: E402  (beside this script; -I keeps the script's folder off sys.path)

SAMPLE_URL = ("https://raw.githubusercontent.com/synthetichealth/synthea-sample-data/"
              "84f59805d481b9f01f3141ef39e68bdbbeb80132/downloads/latest/synthea_sample_data_csv_latest.zip")
SAMPLE_SHA256 = "990d52064037bbf83d10f13163da428964b56ae673b7f39bf549d78c1e7eda16"

REFERENCE = date(2026, 11, 1)  # the pinned sample's records run to October 2026
FIRST_NO, LAST_NO = 90001, 90099  # demographic_no range owned by this file (development.sql uses 1-3000)
COUNT = 22
SEED = 6148
PROGRAM = 10034  # the demo program every development.sql patient is admitted to
UNKNOWN_CODE = "4"  # allergy severity / onset "Unknown", as the allergy form saves it
PROVIDERS = [str(n) for n in range(7, 21)]  # development.sql's demo doctors
NOTE_ROLE = "2"
NOTE_LIMIT = 12
MEASURE_LIMIT = 6
MEASURE_YEARS = 6
MED_YEARS = 3
NOTE_YEARS = 3
VACCINE_YEARS = 10
TOWNS = ("Testville", "Sampleford", "Exampleton", "Mockton", "Demoburg", "Placeholder Falls", "Fictionburg")
STREETS = ("Example Road", "Sample Avenue", "Placeholder Street", "Test Crescent", "Mock Lane", "Demo Drive",
           "Fixture Boulevard", "Stub Court")
ONE_PER_CLASS = ("statin", "raas", "beta-blocker", "p2y12", "asa", "contraceptive", "nsaid", "antihistamine", "snri")
FOOTER = "Synthetic test patient (FAKE): built from Synthea sample data for development and AI testing (#6148)."

RULES = {
    "R1": "HbA1c below 4.0% (below 5.0% with diabetes) replaced: about 7-9% with diabetes, 5.4% otherwise",
    "R2": "Two medicines of one class taken at once: the one started last is kept, the other ends the day before",
    "R3": "P2Y12 inhibitor (clopidogrel, prasugrel, ticagrelor) ends 12 months after it started",
    "R4": "Antihistamine or short-term medicine still open since childhood dropped",
    "R5": "Anemia moved to past history when the last hemoglobin is normal",
    "R6": "One creatinine per day (serum first); eGFR recomputed from it (CKD-EPI 2021); CKD stage dropped",
    "R7": "Short courses left open (antibiotics and the like) end 10 days after they started",
    "R8": "Yearly renewals of one medicine merged into one course",
    "C1": "US-only products replaced by the usual Canadian ones (see codemaps.canadian)",
    "S": "A sample medicine with no code-map entry is left out (never guessed)",
}


# --- Reading -------------------------------------------------------------------------------------------

def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read_sample(zip_path, names=("patients", "conditions", "medications", "observations", "encounters",
                                 "allergies", "immunizations")):
    """The CSV tables of the zip, as lists of dicts. Read as data only."""
    tables = {}
    with zipfile.ZipFile(zip_path) as archive:
        members = {Path(n).name: n for n in archive.namelist() if n.endswith(".csv")}
        for name in names:
            with archive.open(members[f"{name}.csv"]) as raw:
                tables[name] = list(csv.DictReader(io.TextIOWrapper(raw, "utf-8", newline="")))
    return tables


def day(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).date() if value else None


def stamp(value):
    """A Synthea timestamp as MariaDB DATETIME (UTC, seconds)."""
    return datetime.fromisoformat(value.replace("Z", "+00:00")).strftime("%Y-%m-%d %H:%M:%S")


def by_patient(rows):
    out = defaultdict(list)
    for row in rows:
        out[row["PATIENT"]].append(row)
    return out


def age_on(birth, when):
    return when.year - birth.year - ((when.month, when.day) < (birth.month, birth.day))


# --- Choosing patients ---------------------------------------------------------------------------------

def is_chronic(description):
    entry = cm.CONDITIONS.get(description)
    if entry:
        return entry[2] == "chronic"
    lower = description.lower()
    return "(disorder)" in description and not any(w in lower for w in cm.SKIP_CONDITION_WORDS)


def choose(tables, count=COUNT):
    """The `count` living adults with the richest histories, best first (ties by birth date)."""
    cond, meds, obs, enc = (by_patient(tables[n]) for n in ("conditions", "medications", "observations", "encounters"))
    scored = []
    for p in tables["patients"]:
        pid = p["Id"]
        if p["DEATHDATE"] or age_on(date.fromisoformat(p["BIRTHDATE"]), REFERENCE) < 25:
            continue
        chronic = {c["CODE"] for c in cond[pid] if not c["STOP"] and is_chronic(c["DESCRIPTION"])}
        mapped = {m["DESCRIPTION"] for m in meds[pid] if m["DESCRIPTION"] in cm.MEDICINES}
        labs = {o["CODE"] for o in obs[pid] if o["CATEGORY"] == "laboratory"}
        if len(chronic) < 2 or len(mapped) < 3:
            continue
        score = len(chronic) * 3 + len(mapped) * 2 + min(len(labs), 30) + min(len(enc[pid]), 40) // 4
        scored.append((-score, p["BIRTHDATE"], p["LAST"], pid))
    scored.sort()
    return [s[3] for s in scored[:count]]


# --- FAKE identity --------------------------------------------------------------------------------------

def _luhn_ok(digits):
    total = 0
    for i, d in enumerate(reversed([int(x) for x in digits])):
        if i % 2:
            d *= 2
            d -= 9 if d > 9 else 0
        total += d
    return total % 10 == 0


def ohip_failing(seed):
    """A 10-digit Ontario-style health number that fails the Luhn check."""
    base = f"{(seed * 7919 + 1234567) % 10**8:08d}"
    for last in range(10):
        number = "4" + base + str(last)
        if not _luhn_ok(number):
            return number
    raise AssertionError("unreachable: one of ten check digits always fails")


def plain_name(value):
    return re.sub(r"\d+", "", value or "").strip() or "Test"


def identity(index, patient, rng_order):
    n = FIRST_NO + index
    slot = rng_order[index]
    sex = "F" if patient["GENDER"] == "F" else "M"
    return dict(
        no=n, sex=sex, title="MS" if sex == "F" else "MR",
        first="FAKE-" + plain_name(patient["FIRST"]), last="FAKE-" + plain_name(patient["LAST"]),
        birth=date.fromisoformat(patient["BIRTHDATE"]),
        address=f"{10 + 7 * slot} {STREETS[slot % len(STREETS)]}", city=TOWNS[slot % len(TOWNS)],
        postal=f"Z{slot % 10}Z {(slot * 3) % 10}Z{(slot * 7) % 10}",
        phone=f"905-555-01{index:02d}", hin=ohip_failing(n), provider=PROVIDERS[index % len(PROVIDERS)],
    )


# --- Clinical content -----------------------------------------------------------------------------------

class Log:
    """What the clean-up rules changed, per patient (written into the SQL as comments)."""

    def __init__(self):
        self.lines, self.counts = [], Counter()

    def add(self, rule, text):
        self.lines.append(f"{rule}: {text}")
        self.counts[rule] += 1


def conditions(rows, log):
    """Active problems, past history and dx codes: {label: start}, {label: (start, stop)}, {icd9: (label, start)}."""
    active, history, dx = {}, {}, {}
    for c in sorted(rows, key=lambda r: r["START"]):
        desc = c["DESCRIPTION"]
        if desc in cm.CONDITIONS:
            label, icd, kind = cm.CONDITIONS[desc]
        elif is_chronic(desc):
            label, icd, kind = desc.replace(" (disorder)", ""), None, "chronic"
        else:
            continue
        if kind == "skip":
            continue
        start, stop = day(c["START"]), day(c["STOP"])
        if kind == "history" or stop:
            history.setdefault(label, (start, stop))
            if icd == "412":  # an old heart attack stays a coded condition
                dx.setdefault(icd, (label, start))
        else:
            active.setdefault(label, start)
            if icd:
                dx.setdefault(icd, (label, start))
        if desc.startswith("Chronic kidney disease stage"):
            log.add("R6", f"'{desc}' listed as chronic kidney disease, stage dropped")
    for label in list(history):
        if label in active:
            del history[label]
    return active, history, dx


def courses(rows, birth, log):
    """Medicine courses after R2-R8 and C1: dicts with entry, start, end (None = ongoing)."""
    by_desc = defaultdict(list)
    for m in rows:
        by_desc[m["DESCRIPTION"]].append(m)
    out = []
    for desc, ms in by_desc.items():
        if any(w.lower() in desc.lower() for w in cm.SKIP_MEDICINE_WORDS):
            continue
        if desc not in cm.MEDICINES:
            log.add("S", f"medicine not in the code maps, left out: {desc}")
            continue
        entry = cm.MEDICINES[desc]
        runs = []
        for m in sorted(ms, key=lambda r: r["START"]):
            start, stop = day(m["START"]), day(m["STOP"])
            if runs and runs[-1]["end"] is not None and (start - runs[-1]["end"]).days <= 31:
                runs[-1]["end"] = stop
                runs[-1]["merged"] += 1
            else:
                runs.append(dict(entry=entry, start=start, end=stop, merged=0))
        out.extend(runs)
    # R7: a short course left open ends after 10 days.
    for c in out:
        if c["end"] is None and not c["entry"]["long_term"] and c["entry"]["route"] == "PO" \
                and c["entry"]["atc"].startswith("J01"):
            c["end"] = c["start"] + timedelta(days=10)
            log.add("R7", f"{c['entry']['name']} ended 10 days after {c['start']}")
    # R4: an antihistamine or short-term medicine open since childhood is dropped.
    kept = []
    for c in out:
        if c["end"] is None and age_on(birth, c["start"]) < 18 and \
                (c["entry"]["cls"] == "antihistamine" or not c["entry"]["long_term"]):
            log.add("R4", f"{c['entry']['name']} open since age {age_on(birth, c['start'])} dropped")
            continue
        kept.append(c)
    out = kept
    # R3: P2Y12 inhibitors end 12 months after they started.
    for c in out:
        if c["entry"]["cls"] == "p2y12":
            limit = c["start"] + timedelta(days=365)
            if (c["end"] is None and limit < REFERENCE) or (c["end"] is not None and c["end"] > limit):
                c["end"] = limit
                log.add("R3", f"{c['entry']['name']} from {c['start']} ends {limit}")
    # R2: one ongoing medicine per class (and per generic): the latest start wins.
    for key in ONE_PER_CLASS + tuple(sorted({c["entry"]["generic"] for c in out})):
        group = [c for c in out if c["end"] is None and key in (c["entry"]["cls"], c["entry"]["generic"])]
        if len(group) < 2:
            continue
        group.sort(key=lambda c: (c["start"], c["entry"]["name"]))
        latest = group[-1]
        for c in group[:-1]:
            c["end"] = max(c["start"], latest["start"] - timedelta(days=1))
            log.add("R2", f"{c['entry']['name']} ends {c['end']}: {latest['entry']['name']} started {latest['start']}")
    since = REFERENCE - timedelta(days=365 * MED_YEARS)
    kept = sorted((c for c in out if c["end"] is None or c["end"] >= since), key=lambda c: (c["start"], c["entry"]["name"]))
    for c in kept:
        if c["merged"]:
            log.add("R8", f"{c['entry']['name']}: {c['merged'] + 1} renewals merged into one course from {c['start']}")
        if c["entry"]["canadian"]:
            log.add("C1", c["entry"]["canadian"])
    return kept


def egfr(creatinine_umol, sex, age):
    """CKD-EPI 2021 (race-free), mL/min/1.73 m2."""
    scr = creatinine_umol / 88.4
    k, a = (0.7, -0.241) if sex == "F" else (0.9, -0.302)
    value = 142 * min(scr / k, 1) ** a * max(scr / k, 1) ** -1.200 * 0.9938 ** age
    return round(value * (1.012 if sex == "F" else 1))


def measurements(rows, who, diabetic, index, log):
    """{(type, date): (value text, observed datetime)} after R1 and R6, last MEASURE_LIMIT per type."""
    since = REFERENCE - timedelta(days=365 * MEASURE_YEARS)
    picked, twin_creatinine, replaced = {}, set(), []
    pressure = defaultdict(dict)
    for o in sorted(rows, key=lambda r: r["DATE"]):
        when = day(o["DATE"])
        if when < since or when > REFERENCE:
            continue
        code = o["CODE"]
        if code in ("8480-6", "8462-4"):
            pressure[when]["sys" if code == "8480-6" else "dia"] = (o["VALUE"], stamp(o["DATE"]))
            continue
        if code not in cm.MEASUREMENTS:
            continue
        try:
            raw = float(o["VALUE"])
        except ValueError:
            continue
        ctype, factor, decimals, _, _ = cm.MEASUREMENTS[code]
        key = (ctype, when)
        rank = cm.PREFERRED.index(code) if code in cm.PREFERRED else len(cm.PREFERRED)
        value = round(raw * factor, decimals)
        if key in picked:
            if ctype == "SCR" and picked[key][0] != value:
                twin_creatinine.add(when)
            if picked[key][2] <= rank:
                continue
        picked[key] = (value, stamp(o["DATE"]), rank)
    out = {}
    for (ctype, when), (value, observed, _) in picked.items():
        out[(ctype, when)] = (value, observed)
    for when, parts in pressure.items():
        if "sys" in parts and "dia" in parts:
            sys_v, dia_v = (round(float(parts[k][0])) for k in ("sys", "dia"))
            out[("BP", when)] = (f"{sys_v}/{dia_v}", parts["sys"][1])
    # R6: eGFR from the day's creatinine.
    for (ctype, when), (value, observed) in list(out.items()):
        if ctype == "SCR":
            out[("EGFR", when)] = (egfr(value, who["sex"], age_on(who["birth"], when)), observed)
    # R1: an impossible HbA1c, or one far too low for diabetes: the sample keeps some patients on insulin at a flat
    # 3.1% or 4.0% at every visit.
    a1c = sorted((k for k in out if k[0] == "A1C"), key=lambda k: k[1])
    base = 7.0 + 0.3 * (index % 8)
    limit = 5.0 if diabetic else 4.0
    for n, key in enumerate(a1c):
        value, observed = out[key]
        if value < limit:
            new = round(base + (0.0, -0.2, 0.1, -0.1, 0.2, 0.0)[n % 6], 1) if diabetic else 5.4
            out[key] = (new, observed)
            replaced.append((value, new))
    if replaced:
        olds = sorted({v for v, _ in replaced})
        news = sorted(n for _, n in replaced)
        span = f"{news[0]:.1f}%" if news[0] == news[-1] else f"{news[0]:.1f}-{news[-1]:.1f}%"
        log.add("R1", f"{len(replaced)} HbA1c value(s) of {', '.join(f'{v:g}' for v in olds)}% -> {span}")
    if twin_creatinine:
        log.add("R6", f"{len(twin_creatinine)} days with two different creatinine values: the serum one kept, "
                      "eGFR recomputed")
    keep = {}
    for ctype in sorted({k[0] for k in out}):
        dates = sorted(k[1] for k in out if k[0] == ctype)[-MEASURE_LIMIT:]
        for when in dates:
            keep[(ctype, when)] = out[(ctype, when)]
    return keep


DECIMALS = {ctype: decimals for ctype, _, decimals, _, _ in cm.MEASUREMENTS.values()}


def text_value(ctype, value):
    """A measurement as CARLOS stores it: the type's own decimal places ("7.0", "2.53", "97")."""
    if isinstance(value, float):
        decimals = DECIMALS.get(ctype, 1)
        return f"{value:.{decimals}f}" if decimals else str(round(value))
    return str(value)


# --- SQL ------------------------------------------------------------------------------------------------

def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return f"{value:g}"
    text = str(value).replace("\\", "\\\\").replace("'", "''").replace("\r", "").replace("\n", "\\n")
    return f"'{text}'"


def insert(table, columns, rows):
    if not rows:
        return []
    values = ",\n".join("(" + ",".join(q(v) for v in row) + ")" for row in rows)
    return [f"INSERT INTO `{table}` ({','.join('`' + c + '`' for c in columns)}) VALUES\n{values};"]


def note_sql(who, observed, text, encounter_type="", issue_var=None, position=0):
    cols = ("update_date", "observation_date", "demographic_no", "provider_no", "note", "signed",
            "include_issue_innote", "signing_provider_no", "encounter_type", "billing_code", "program_no",
            "reporter_caisi_role", "reporter_program_team", "history", "locked", "archived", "position")
    vals = (observed, observed, who["no"], who["provider"], text, 1, 1, who["provider"], encounter_type, "",
            str(PROGRAM), NOTE_ROLE, "0", text, "0", 0, position)
    sql = [f"INSERT INTO `casemgmt_note` ({','.join('`' + c + '`' for c in cols)},`uuid`) VALUES "
           f"({','.join(q(v) for v in vals)},UUID());"]
    if issue_var:
        sql.append(f"INSERT INTO `casemgmt_issue_notes` (`id`,`note_id`) VALUES ({issue_var},LAST_INSERT_ID());")
    return sql


def cpp_issue_sql(who, code, var, when):
    return [
        "INSERT INTO `casemgmt_issue` (`demographic_no`,`issue_id`,`acute`,`certain`,`major`,`resolved`,"
        "`program_id`,`type`,`update_date`) "
        f"SELECT {who['no']},`issue_id`,0,0,0,0,{PROGRAM},'doctor',{q(when)} FROM `issue` WHERE `code` = {q(code)};",
        # No issue row means no link at all: a NULL id fails the load instead of reusing the last id.
        f"SET {var} = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);",
    ]


ENCOUNTER_TYPES = {"virtual": "telephone encounter with client"}
CLASS_TITLES = {"wellness": "Office visit", "ambulatory": "Office visit", "outpatient": "Office visit",
                "urgentcare": "Walk-in clinic visit", "emergency": "Emergency department visit",
                "inpatient": "Hospital admission", "virtual": "Virtual visit", "home": "Home visit"}


def clean_description(text):
    return re.sub(r"\s*\((procedure|environment|finding|disorder|situation|regime/therapy)\)", "", text or "").strip()


def encounter_notes(who, tables_p, meds, measures, log):
    """Short notes for recent visits, from what the sample recorded at each one (newest NOTE_LIMIT)."""
    since = REFERENCE - timedelta(days=365 * NOTE_YEARS)
    obs_by_enc = defaultdict(list)
    for o in tables_p["observations"]:
        obs_by_enc[o["ENCOUNTER"]].append(o)
    cond_by_enc = defaultdict(list)
    for c in tables_p["conditions"]:
        cond_by_enc[c["ENCOUNTER"]].append(c)
    shots_by_enc = defaultdict(list)
    for i in tables_p["immunizations"]:
        shots_by_enc[i["ENCOUNTER"]].append(i)
    notes = []
    for e in sorted(tables_p["encounters"], key=lambda r: r["START"]):
        when = day(e["START"])
        if when < since or when > REFERENCE or "dental" in (e["DESCRIPTION"] + e["REASONDESCRIPTION"]).lower():
            continue
        lines = []
        reason = e["REASONDESCRIPTION"]
        if reason and not any(w in reason.lower() for w in cm.SKIP_CONDITION_WORDS):
            lines.append("Reason: " + (cm.CONDITIONS.get(reason, (clean_description(reason),))[0]))
        new = [cm.CONDITIONS.get(c["DESCRIPTION"], (clean_description(c["DESCRIPTION"]), None, "chronic"))
               for c in cond_by_enc[e["Id"]] if is_chronic(c["DESCRIPTION"]) or c["DESCRIPTION"] in cm.CONDITIONS]
        new = [n[0] for n in new if n[2] != "skip"]
        if new:
            lines.append("Diagnosed: " + ", ".join(dict.fromkeys(new)))
        vitals, results = [], []
        for ctype, label_order in (("BP", "BP"), ("HR", "HR"), ("WT", "Wt"), ("BMI", "BMI")):
            if (ctype, when) in measures:
                unit = {"WT": " kg"}.get(ctype, "")
                vitals.append(f"{label_order} {text_value(ctype, measures[(ctype, when)][0])}{unit}")
        for code, (ctype, _, _, label, unit) in cm.MEASUREMENTS.items():
            if ctype in ("WT", "HT", "BMI", "HR", "RESP", "TEMP", "02"):
                continue
            if (ctype, when) in measures and any(o["CODE"] == code for o in obs_by_enc[e["Id"]]):
                gap = "" if unit in ("", "%") else " "
                entry = f"{label} {text_value(ctype, measures[(ctype, when)][0])}{gap}{unit}"
                if entry not in results:
                    results.append(entry)
        if ("EGFR", when) in measures and any(r.startswith("Creatinine") for r in results):
            results.append(f"eGFR {measures[('EGFR', when)][0]}")
        if vitals:
            lines.append("Vitals: " + ", ".join(vitals))
        if results:
            lines.append("Results: " + ", ".join(results))
        started = [c["entry"]["name"] for c in meds if c["start"] == when]
        stopped = [c["entry"]["name"] for c in meds if c["end"] == when and c["end"] < REFERENCE]
        if started:
            lines.append("Started: " + ", ".join(dict.fromkeys(started)))
        if stopped:
            lines.append("Stopped: " + ", ".join(dict.fromkeys(stopped)))
        shots = [cm.IMMUNIZATIONS[i["DESCRIPTION"]][1] for i in shots_by_enc[e["Id"]] if i["DESCRIPTION"] in cm.IMMUNIZATIONS]
        if shots:
            lines.append("Immunizations: " + ", ".join(dict.fromkeys(shots)))
        if len(lines) < 2:
            continue
        title = f"{CLASS_TITLES.get(e['ENCOUNTERCLASS'], 'Visit')}: {clean_description(e['DESCRIPTION'])}"
        header = f"[{when.strftime('%d-%b-%Y')} .: {title}]"
        notes.append((stamp(e["START"]), ENCOUNTER_TYPES.get(e["ENCOUNTERCLASS"], "face to face encounter with client"),
                      "\n".join([header, *lines, "", FOOTER])))
    return notes[-NOTE_LIMIT:]


def patient_sql(index, who, tp, log):
    """Every statement for one patient, after its comment block."""
    birth = who["birth"]
    active, history, dx = conditions(tp["conditions"], log)
    meds = courses(tp["medications"], birth, log)
    diabetic = any("diabetes" in label.lower() for label in active) or any(
        c["entry"]["atc"].startswith("A10") for c in meds)
    measures = measurements(tp["observations"], who, diabetic, index, log)
    hb = sorted((k for k in measures if k[0] == "Hb"), key=lambda k: k[1])
    if "Anemia" in active and hb and measures[hb[-1]][0] >= (120 if who["sex"] == "F" else 130):
        history["Anemia (resolved)"] = (active.pop("Anemia"), hb[-1][1])
        dx.pop("2859", None)
        log.add("R5", f"anemia moved to past history: last hemoglobin {measures[hb[-1]][0]:g} g/L")
    first_visit = min((day(e["START"]) for e in tp["encounters"]), default=REFERENCE)
    joined = max(first_visit, date(2010, 1, 1))
    joined_at = f"{joined} 09:00:00"
    sql = []
    demo_cols = ("demographic_no", "title", "last_name", "first_name", "address", "city", "province", "postal",
                 "phone", "phone2", "email", "year_of_birth", "month_of_birth", "date_of_birth", "hin", "ver",
                 "roster_status", "patient_status", "patient_status_date", "date_joined", "chart_no",
                 "official_lang", "spoken_lang", "provider_no", "sex", "hc_type", "family_doctor", "lastUpdateUser",
                 "lastUpdateDate", "middleNames", "gender", "pref_name")
    sql += insert("demographic", demo_cols, [(
        who["no"], who["title"], who["last"], who["first"], who["address"], who["city"], "ON", who["postal"],
        who["phone"], "", "", f"{birth.year:04d}", f"{birth.month:02d}", f"{birth.day:02d}", who["hin"], "AA",
        "", "AC", str(joined), str(joined), "", "English", "English", who["provider"], who["sex"], "ON",
        "<rdohip></rdohip><rd></rd>", "999998", joined_at, "", who["sex"], "")])
    sql += insert("admission", ("client_id", "program_id", "provider_no", "admission_date", "admission_from_transfer",
                                "discharge_from_transfer", "admission_status", "temporary_admission_flag",
                                "automatic_discharge", "lastUpdateDate"),
                  [(who["no"], PROGRAM, who["provider"], joined_at, 0, 0, "current", 0, 0, joined_at)])
    # Allergies (a "No Known Drug Allergies" row when the sample lists none).
    allergy_rows = []
    for a in sorted(tp["allergies"], key=lambda r: (r["START"], r["DESCRIPTION"])):
        if a["STOP"] or a["DESCRIPTION"] == "Allergic disposition (finding)":
            continue
        name = re.sub(r"\s*\((substance|organism|edible)\)", "", a["DESCRIPTION"]).replace("Eggs", "Egg")[:50]
        # CARLOS codes (the allergy form, AddReaction2.jsp): 1 mild, 2 moderate, 3 severe, 4 unknown; onset 4 unknown.
        severity = {"MILD": "1", "MODERATE": "2", "SEVERE": "3"}.get(a["SEVERITY1"], UNKNOWN_CODE)
        reaction = clean_description(a["DESCRIPTION1"]) or None
        start = day(a["START"])
        allergy_rows.append((who["no"], str(start), name, 0, reaction, 0, str(start), severity, UNKNOWN_CODE,
                             len(allergy_rows),
                             f"{start} 09:00:00", who["provider"], 0 if a["CATEGORY"] == "medication" else 1))
    if not allergy_rows:
        allergy_rows.append((who["no"], str(joined), "No Known Drug Allergies", 0, None, 0, str(joined), UNKNOWN_CODE,
                             UNKNOWN_CODE, 0,
                             joined_at, who["provider"], 0))
    sql += insert("allergies", ("demographic_no", "entry_date", "DESCRIPTION", "TYPECODE", "reaction", "archived",
                                "start_date", "severity_of_reaction", "onset_of_reaction", "position",
                                "lastUpdateDate", "providerNo", "nonDrug"), allergy_rows)
    # Medicines.
    drug_rows = []
    for c in meds:
        e = c["entry"]
        ongoing = c["end"] is None
        end = c["end"] or (REFERENCE + timedelta(days=90))
        days = max(1, (end - c["start"]).days) if not ongoing else 90
        qty, repeats = ("90", 3) if ongoing and e["long_term"] else ("", 0)
        method = {"INJ": "Inj", "MDI": "Inhal", "DPI": "Inhal", "NEB": "Inhal", "PATCH": "Apply", "RING": "Insrt",
                  "IUS": "Insrt", "IMPL": "Insrt"}.get(e["form"], "Take")
        special = f"{e['name']}\n{e['instructions']}" + (f"\nQty:{qty} Repeats:{repeats}" if qty else "")
        drug_rows.append((who["provider"], who["no"], str(c["start"]), str(end), str(c["start"]), "", 0, e["name"],
                          e["takemin"], e["takemax"], e["freq"], str(min(days, 9999)), "D", qty, repeats, 0, e["prn"],
                          special, 0, e["generic"], e["atc"], e["unit"], method, e["route"], e["form"],
                          f"{c['start']} 09:00:00", e["strength"], 0, bool(ongoing and e["long_term"]), 0,
                          f"{c['start']} 09:00:00", 0))
    sql += insert("drugs", ("provider_no", "demographic_no", "rx_date", "end_date", "written_date", "BN", "GCN_SEQNO",
                            "customName", "takemin", "takemax", "freqcode", "duration", "durunit", "quantity",
                            "repeat", "nosubs", "prn", "special", "archived", "GN", "ATC", "unit", "method", "route",
                            "drug_form", "create_date", "dosage", "custom_instructions", "long_term", "position",
                            "lastUpdateDate", "dispenseInternal"), drug_rows)
    # Disease registry.
    sql += insert("dxresearch", ("demographic_no", "start_date", "update_date", "status", "dxresearch_code",
                                 "coding_system", "association", "providerNo"),
                  [(who["no"], str(start), f"{start} 09:00:00", "A", icd, "icd9", 0, who["provider"])
                   for icd, (_, start) in sorted(dx.items(), key=lambda kv: (kv[1][1], kv[0]))])
    # Measurements.
    sql += insert("measurements", ("type", "demographicNo", "providerNo", "dataField", "measuringInstruction",
                                   "comments", "dateObserved", "dateEntered"),
                  [(ctype, who["no"], who["provider"], text_value(ctype, value), "", "", observed, observed)
                   for (ctype, _), (value, observed) in sorted(measures.items(), key=lambda kv: (kv[0][1], kv[0][0]))])
    # Preventions (vaccines in the last VACCINE_YEARS), each with its extension rows.
    since = REFERENCE - timedelta(days=365 * VACCINE_YEARS)
    seen = set()
    for n, i in enumerate(sorted(tp["immunizations"], key=lambda r: r["DATE"])):
        if i["DESCRIPTION"] not in cm.IMMUNIZATIONS or day(i["DATE"]) < since:
            continue
        ptype, display, route, dose = cm.IMMUNIZATIONS[i["DESCRIPTION"]]
        if (ptype, day(i["DATE"])) in seen:
            continue
        seen.add((ptype, day(i["DATE"])))
        given = stamp(i["DATE"])
        sql += insert("preventions", ("demographic_no", "creation_date", "prevention_date", "provider_no",
                                      "prevention_type", "deleted", "refused", "never", "creator", "lastUpdateDate"),
                      [(who["no"], given, given, who["provider"], ptype, "0", "0", "0", int(who["provider"]), given)])
        sql.append("SET @carlos_synthea_prevention = LAST_INSERT_ID();")
        ext = (("name", display), ("dose", dose), ("route", route),
               ("location", "Left deltoid" if n % 2 else "Right deltoid"), ("comments", FOOTER))
        sql.append("INSERT INTO `preventionsExt` (`prevention_id`,`keyval`,`val`) VALUES\n" + ",\n".join(
            f"(@carlos_synthea_prevention,{q(k)},{q(v)})" for k, v in ext) + ";")
    # CPP: ongoing concerns, past history, and a reminder that the patient is synthetic.
    if active:
        sql += cpp_issue_sql(who, "Concerns", "@carlos_synthea_concerns", joined_at)
        for pos, (label, start) in enumerate(sorted(active.items(), key=lambda kv: (kv[1], kv[0]))):
            sql += note_sql(who, f"{start} 09:00:00", label, issue_var="@carlos_synthea_concerns", position=pos)
    if history:
        sql += cpp_issue_sql(who, "MedHistory", "@carlos_synthea_history", joined_at)
        for pos, (label, (start, stop)) in enumerate(sorted(history.items(), key=lambda kv: (kv[1][0], kv[0]))):
            years = f"{start.year}" + (f"-{stop.year}" if stop and stop.year != start.year else "")
            sql += note_sql(who, f"{start} 09:00:00", f"{label} ({years})", issue_var="@carlos_synthea_history",
                            position=pos)
    sql += cpp_issue_sql(who, "Reminders", "@carlos_synthea_reminders", joined_at)
    sql += note_sql(who, joined_at, FOOTER, issue_var="@carlos_synthea_reminders")
    # Visit notes.
    for observed, encounter_type, text in encounter_notes(who, tp, meds, measures, log):
        sql += note_sql(who, observed, text, encounter_type=encounter_type)
    stats = dict(problems=len(active), history=len(history), dx=len(dx), drugs=len(drug_rows),
                 allergies=len(allergy_rows), measurements=len(measures), preventions=len(seen))
    return sql, stats


def build(tables, sha):
    chosen = choose(tables)
    patients = {p["Id"]: p for p in tables["patients"]}
    split = {name: by_patient(tables[name]) for name in tables if name != "patients"}
    order = list(range(len(chosen)))
    random.Random(SEED).shuffle(order)
    out = [
        "-- ---------------------------------------------------------------------------",
        "-- CARLOS development data: synthetic patients with full histories (#6148)",
        "--",
        "-- GENERATED by scripts/devdata/synthea/generate_dev_patients.py: do not edit by hand.",
        "-- Source: Synthea's ready-made CSV sample (synthetic, Apache-2.0), pinned by commit and",
        "-- SHA-256 in scripts/devdata/synthea/README.md" + ("" if sha == SAMPLE_SHA256 else " (NOT the pinned zip)") + ".",
        "-- Loaded by .devcontainer/db/scripts/populate_db.sh after development.sql and the name",
        "-- sanitization. Development data only: never applied to a production database.",
        "--",
        f"-- {len(chosen)} FAKE patients, demographic_no {FIRST_NO}-{FIRST_NO + len(chosen) - 1}; this file owns "
        f"{FIRST_NO}-{LAST_NO}.",
        "-- Re-running it deletes and re-inserts that range, so it is idempotent.",
        "-- Names carry FAKE-, health numbers fail the Ontario check digit, addresses and phones are invented.",
        "-- No Synthea identifier is carried over.",
        "--",
        "-- Clean-up rules (README.md lists them; each patient's block says which ones fired):",
        *[f"--   {k}  {v}" for k, v in RULES.items()],
        "-- ---------------------------------------------------------------------------",
        "",
        "START TRANSACTION;",
        "",
        f"SET @carlos_synthea_first = {FIRST_NO}, @carlos_synthea_last = {LAST_NO};",
        "DELETE cin FROM `casemgmt_issue_notes` cin JOIN `casemgmt_note` n ON n.`note_id` = cin.`note_id`",
        "  WHERE n.`demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE cin FROM `casemgmt_issue_notes` cin JOIN `casemgmt_issue` i ON i.`id` = cin.`id`",
        "  WHERE i.`demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `casemgmt_note` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `casemgmt_issue` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE pe FROM `preventionsExt` pe JOIN `preventions` p ON p.`id` = pe.`prevention_id`",
        "  WHERE p.`demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `preventions` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `measurements` WHERE `demographicNo` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `dxresearch` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `drugs` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `allergies` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `admission` WHERE `client_id` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "DELETE FROM `demographic` WHERE `demographic_no` BETWEEN @carlos_synthea_first AND @carlos_synthea_last;",
        "",
    ]
    totals, rule_totals = Counter(), Counter()
    for index, pid in enumerate(chosen):
        who = identity(index, patients[pid], order)
        tp = {name: split[name][pid] for name in split}
        log = Log()
        sql, stats = patient_sql(index, who, tp, log)
        totals.update(stats)
        rule_totals.update(log.counts)
        age = age_on(who["birth"], REFERENCE)
        out.append(f"-- {who['no']}: {who['first']} {who['last']}, {who['sex']} {age}; provider {who['provider']}; "
                   + ", ".join(f"{k} {v}" for k, v in stats.items()))
        out += [f"--   {line}" for line in sorted(set(log.lines))]
        out += sql
        out.append("")
    out.append("COMMIT;")
    out.append("")
    out.append("-- Totals: " + ", ".join(f"{k} {v}" for k, v in sorted(totals.items())))
    out.append("-- Rules fired: " + ", ".join(f"{k} {v}" for k, v in sorted(rule_totals.items())))
    return "\n".join(out) + "\n", totals, rule_totals


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--zip", required=True, type=Path, help="the pinned Synthea CSV sample zip")
    parser.add_argument("--out", required=True, type=Path, help="the SQL file to write")
    parser.add_argument("--allow-other-sample", action="store_true",
                        help="build from a zip whose SHA-256 differs from the pinned one (for trying a newer sample)")
    args = parser.parse_args(argv)
    sha = sha256_of(args.zip)
    if sha != SAMPLE_SHA256 and not args.allow_other_sample:
        parser.error(f"{args.zip} has SHA-256 {sha}, not the pinned {SAMPLE_SHA256}; download {SAMPLE_URL}")
    text, totals, rules = build(read_sample(args.zip), sha)
    args.out.write_text(text, encoding="utf-8")
    print(f"wrote {args.out}: " + ", ".join(f"{k} {v}" for k, v in sorted(totals.items())))
    print("rules fired: " + ", ".join(f"{k} {v}" for k, v in sorted(rules.items())))
    return 0


if __name__ == "__main__":
    sys.exit(main())
