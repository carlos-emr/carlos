# Synthetic development patients from Synthea (#6148)

`generate_dev_patients.py` turns Synthea's ready-made sample patients into an additive SQL file for
the **development** database: 22 adults with long, consistent histories (several conditions,
medicine changes over years, lab values, vaccines and visits), for testing the AI chart tools and
anything else that needs a full chart.

- Output: `.devcontainer/db/scripts/demo-synthetic-patients.sql`, loaded by
  `.devcontainer/db/scripts/populate_db.sh` after `development.sql` and the name sanitization.
- **Never applied to a production database.** The packaged demo-data load (`carlos-ctl demo-data`)
  does not use it yet.
- Synthea is not run during the build: the sample is converted once, and the generated SQL file is
  committed.

## Source (pinned)

| | |
|---|---|
| Sample | Synthea CSV sample "latest" from [synthea-sample-data](https://github.com/synthetichealth/synthea-sample-data), 111 synthetic patients |
| Commit | `84f59805d481b9f01f3141ef39e68bdbbeb80132` (the sample repo's `latest` file changes often; this commit fixes it) |
| URL | https://raw.githubusercontent.com/synthetichealth/synthea-sample-data/84f59805d481b9f01f3141ef39e68bdbbeb80132/downloads/latest/synthea_sample_data_csv_latest.zip |
| SHA-256 | `990d52064037bbf83d10f13163da428964b56ae673b7f39bf549d78c1e7eda16` (5,446,315 bytes) |
| Licence | Synthea is Apache-2.0; its output is synthetic. Attribution in `NOTICE.md` |

The zip stays **out of the repository**. To rebuild, download it to a folder of its own, treat it as
data only, and run (from the repository root):

```bash
python3 -I scripts/devdata/synthea/generate_dev_patients.py \
    --zip /path/to/synthea_sample_data_csv_latest.zip \
    --out .devcontainer/db/scripts/demo-synthetic-patients.sql
```

The script refuses a zip with another SHA-256 (`--allow-other-sample` overrides, for trying a newer
sample). The same zip always gives the same file, byte for byte.

## What a patient looks like in CARLOS

| Sample table | CARLOS | Notes |
|---|---|---|
| patients | `demographic` + `admission` (program 10034, as every demo patient) | See "FAKE identities" |
| conditions | CPP **Ongoing Concerns** (one note each), **Medical History** (past events with their years), `dxresearch` (ICD-9) | `codemaps.CONDITIONS`; dental, acute and minor-injury conditions are left out |
| medications | `drugs` (custom drugs: generic name, strength, instructions, ATC, `long_term`) | `codemaps.MEDICINES`; hospital-only products left out; renewals merged into courses; courses that ended more than 3 years ago left out |
| allergies | `allergies` (`nonDrug` for food and environment; "No Known Drug Allergies" when none) | |
| immunizations | `preventions` + `preventionsExt` (name, dose, route, site, comment) | `codemaps.IMMUNIZATIONS`, CARLOS prevention names; last 10 years |
| observations | `measurements` in Canadian units (BP as "sys/dia", A1C, LDL, SCR, EGFR, ACR, WT, HT, BMI and more) | `codemaps.MEASUREMENTS`; last 6 values per type within 6 years |
| encounters | short visit notes (`casemgmt_note`): reason, new diagnoses, vitals, results, medicines started or stopped, vaccines | Only what the sample recorded at that visit; newest 12 within 3 years |

Each patient also gets a **Reminders** note saying it is a synthetic test patient. Lab reports as
HL7 messages are a possible follow-up.

## FAKE identities

- Names: the sample's first and last names without its digits, both prefixed `FAKE-` (as
  `demo-name-sanitization.sql` does for every demo patient, so a re-run of it changes nothing).
- Health numbers: 10-digit Ontario-style numbers that **fail the check digit**.
- Addresses, towns, postal codes (`Z9Z 9Z9` style) and phones (`905-555-01xx`) are invented.
- No Synthea identifier is carried over: every id in the file is a CARLOS integer, and note
  `uuid`s are made by MariaDB's `UUID()` at load time.
- `demographic_no` **90001–90099** belongs to this file (`development.sql` uses 1–3000). The file
  deletes that range first, so loading it again is safe. MariaDB moves the `demographic`
  AUTO_INCREMENT past 90022, so patients added in a development database after this load are numbered
  from 90023.
- Each patient's family doctor is one of `development.sql`'s demo doctors (providers 7–20).
- An existing development database (a volume that never re-runs `populate_db.sh`) can load it by hand:
  `mariadb -h db -u root carlos < .devcontainer/db/scripts/demo-synthetic-patients.sql`.

## Clean-up rules

The raw sample contradicts itself in places. Every change is made by one of these rules, and each
patient's block in the SQL file lists the rules that fired for it.

| Rule | What | Why |
|---|---|---|
| R1 | An HbA1c below 4.0% becomes 7.0–9.1% for a patient with diabetes (a fixed value per patient, small visit-to-visit steps), 5.4% otherwise | The sample gives 3.1% at every visit to a patient on insulin |
| R2 | Two medicines of one class (statin, ACE inhibitor/ARB, beta-blocker, P2Y12 inhibitor, ASA, contraceptive, NSAID, antihistamine, SNRI) or two strengths of one generic taken at once: the one started last is kept, the other ends the day before | Two statins, or lisinopril 10 mg and 40 mg, both "current" |
| R3 | A P2Y12 inhibitor (clopidogrel, prasugrel, ticagrelor) ends 12 months after it started | Prasugrel open for ten years after a heart attack |
| R4 | An antihistamine or short-term medicine left open since childhood is dropped | Diphenhydramine "current" since age 5 |
| R5 | Anemia moves to past history when the last hemoglobin is normal (≥ 120 g/L women, ≥ 130 g/L men) | "Anemia" beside a hemoglobin of 168 g/L |
| R6 | One creatinine per day (serum first); eGFR recomputed from it with CKD-EPI 2021; chronic kidney disease listed without its stage | Creatinine 1.8 mg/dL beside an eGFR of 114, and "stage 1" beside an eGFR of 32 |
| R7 | An antibiotic course left open ends 10 days after it started | |
| R8 | Monthly or yearly renewals of one medicine are merged into one course | The sample renews every prescription at each visit |
| C1 | US-only products are replaced by the usual Canadian ones (Humulin 70/30 → 30/70, albuterol → salbutamol, hydrocodone/acetaminophen → acetaminophen/codeine, milnacipran → duloxetine, …) | Each entry says so in `codemaps.py` (`canadian=`) |
| S | A sample medicine with no code-map entry is left out, never guessed | |

## Tests

```bash
python3 -m unittest discover -s scripts/devdata/synthea/tests -t .
```

The tests build a tiny sample in memory (no download): determinism, health numbers that fail the
check digit, no UUID or long hex string in the output, the id range, the clean-up rules and the SQL
quoting.
