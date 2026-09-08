# NHS synthetic development patients

Three fictional patients and all 56 of their clinical notes from the NHS England
[Synthetic Clinical Notes dataset](https://huggingface.co/datasets/NHSEDataScience/synthetic_clinical_notes).
Source revision: `368a5bd2a55090a0bae3436f2823d606c5077158`, silver tier.
The dataset is MIT licensed; retain [LICENSE.txt](LICENSE.txt) with this seed.
Source CSV checksums and selected person IDs are pinned in
`../build_nhs_synthetic_seed.py`. No download or AI call occurs during seeding.

| Chart Number | Display Name | Notes |
| --- | --- | --- |
| NHSSYN001 | FAKE-NHS Wells, Judith Ada | 19 |
| NHSSYN002 | FAKE-NHS Edwards, Andrew Michael | 20 |
| NHSSYN003 | FAKE-NHS Suzuki, Ren | 17 |

These are the first three patients in the pinned source CSV, not a representative
clinical sample. Upstream's dataset card describes 70 patients; the pinned CSV
actually contains 69 rows. The generated notes have not been individually checked
by clinicians. Original text, timestamps, typos, encoding artifacts and clinical
inconsistencies are preserved. Synthetic source NHS numbers remain only in original
note text; they are not assigned as Canadian health-card numbers.

The demographic birth dates are imported, but source ages are not recalculated
inside note text. Sex, contact information and Canadian insurance fields are left
unrecorded. Source gender identity is retained in note provenance without inferring
sex. No medication orders, structured allergies, bills, appointments or messages
are created from narrative text.

Notes carry a prominent synthetic-data warning and source patient/admission/note
IDs. CARLOS's `signed` flag is set solely so imported fixtures appear in the
read-only overview; it is explicitly **not a clinician signature**. They belong
to the existing development provider `999998`, OSCAR program `10034`, doctor role
`2`. No clinical permissions are changed.

## Loading

This is development data only, not a production migration. Fresh dev databases
and existing devcontainer bootstrap runs load `patients.sql`. For a running dev
database, use the normal dev credentials through `MYSQL_PWD`, not command arguments:

```bash
mariadb -h db -u root oscar < .devcontainer/db/scripts/nhs-synthetic/patients.sql
```

The seed requires the known development account/program, acquires an exclusive
seed lock, and uses a transaction on InnoDB. Chart-number collisions fail closed.
Demographic IDs are allocated by the database. Repeated runs skip existing charts
and stable note UUIDs, preserving edits to previously seeded records. It never
truncates or updates existing charts. Do not use the client's `--force` option,
which would continue past failed safety checks.

## Rebuilding

Download `silver/patients.csv`, `silver/admissions.csv` and
`silver/synthetic_clinical_notes.csv` from the pinned Hugging Face revision into a
temporary directory, then run:

```bash
python3 .devcontainer/db/scripts/build_nhs_synthetic_seed.py \
  --source-dir /tmp/nhs-synthetic-source \
  --output .devcontainer/db/scripts/nhs-synthetic/patients.sql \
  --manifest-output src/main/resources/clinical/summary/nhs-generation-fixtures.json
```

The generator verifies all three SHA-256 hashes, parses CSV with UTF-8 BOM support,
validates patient/admission ownership and note counts, and deterministically
encodes text as UTF-8 SQL hex literals. The SQL contains the original licensed
note text plus CARLOS-specific provenance headers.
The companion manifest pins fixture identity and every note text/date hash for
the optional local AI generation gate. It contains no note text. Edited or
incomplete seeded charts are intentionally ineligible for runtime generation.

Run generator unit tests without database access or network access:

```bash
python3 -m unittest discover -s .devcontainer/db/scripts -p 'test_nhs_synthetic_seed.py' -v
```

The seed has also been checked in an isolated MariaDB schema for fresh insertion,
repeat-import stability, nonnull CARLOS appointment defaults, and collision refusal.
All three imported patients were verified through eChart and patient overview,
including original source text and desktop/mobile layouts.

Dataset citation: Poulett et al. (2026), *A Pipeline for Generating Longitudinal
Synthetic Clinical Notes Using Large Language Models*, arXiv:2606.26879.
