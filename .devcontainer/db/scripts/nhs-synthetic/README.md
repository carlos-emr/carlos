# NHS synthetic development patients

All 50 fictional patients and all 1,602 of their clinical notes from the NHS England
[Synthetic Clinical Notes dataset](https://huggingface.co/datasets/NHSEDataScience/synthetic_clinical_notes).
Source revision: `368a5bd2a55090a0bae3436f2823d606c5077158`, silver tier.
The dataset is MIT licensed; retain [LICENSE.txt](LICENSE.txt) with this seed.
Source CSV checksums and every selected person ID and note count are pinned in
`../build_nhs_synthetic_seed.py`. No download or AI call occurs during seeding.

This is the whole dataset, not a sample. Upstream's dataset card describes 70
patients; the pinned `patients.csv` has 69 rows, which are 50 people: the source
repeats a patient row for each admission, and 19 people were admitted twice. Each
person is one CARLOS chart holding every stay, and each note keeps the source
admission ID it was written in. A person whose name, date of birth or gender
identity differed between admissions would be refused; none does. Charts are
numbered in source order, so NHSSYN001 to NHSSYN003 are unchanged from the original
three-patient seed and their note hashes are identical.

The generated notes have not been individually checked by clinicians. Original
text, timestamps, typos, encoding artifacts and clinical inconsistencies are
preserved. Synthetic source NHS numbers remain only in original note text; they are
not assigned as Canadian health-card numbers.

| Chart Number | Display Name | Admissions | Notes |
| --- | --- | --- | --- |
| NHSSYN001 | FAKE-NHS Wells, Judith Ada | 1 | 19 |
| NHSSYN002 | FAKE-NHS Edwards, Andrew Michael | 1 | 20 |
| NHSSYN003 | FAKE-NHS Suzuki, Ren | 1 | 17 |
| NHSSYN004 | FAKE-NHS Richards, Bertram Adam | 1 | 32 |
| NHSSYN005 | FAKE-NHS Cook, Philip Paul | 1 | 37 |
| NHSSYN006 | FAKE-NHS Dobson, Rachael Grace | 1 | 11 |
| NHSSYN007 | FAKE-NHS Amin, Anthony Stephen | 1 | 29 |
| NHSSYN008 | FAKE-NHS Osborne, Jane Jill | 1 | 35 |
| NHSSYN009 | FAKE-NHS Morris, Brian William | 1 | 26 |
| NHSSYN010 | FAKE-NHS Scott, Phyllis Marian | 1 | 25 |
| NHSSYN011 | FAKE-NHS Ali, Hazel Brenda | 1 | 27 |
| NHSSYN012 | FAKE-NHS Gill, John Michael | 2 | 46 |
| NHSSYN013 | FAKE-NHS Nyoni, Mandikudza | 2 | 52 |
| NHSSYN014 | FAKE-NHS Campbell, Sharon Christina | 2 | 20 |
| NHSSYN015 | FAKE-NHS Jenkins, Rhys | 2 | 66 |
| NHSSYN016 | FAKE-NHS Miles, Paula Amanda | 2 | 24 |
| NHSSYN017 | FAKE-NHS Watanabe, Haruto | 2 | 70 |
| NHSSYN018 | FAKE-NHS Robinson, Allan Victor | 2 | 66 |
| NHSSYN019 | FAKE-NHS Akter, Fatima | 2 | 60 |
| NHSSYN020 | FAKE-NHS Fakayode, Lolade | 2 | 50 |
| NHSSYN021 | FAKE-NHS Reddy, Dia | 2 | 44 |
| NHSSYN022 | FAKE-NHS Pan, Bin | 2 | 68 |
| NHSSYN023 | FAKE-NHS Hodgson, Jonathan Edward | 2 | 44 |
| NHSSYN024 | FAKE-NHS Gurung, Georgina Claire | 2 | 38 |
| NHSSYN025 | FAKE-NHS Bonsu, Abena | 2 | 90 |
| NHSSYN026 | FAKE-NHS Kumar, Manveer | 2 | 28 |
| NHSSYN027 | FAKE-NHS Bond, Frank Scott | 2 | 72 |
| NHSSYN028 | FAKE-NHS Holland, Margaret Sarah | 2 | 50 |
| NHSSYN029 | FAKE-NHS Chinwo, Hope | 2 | 46 |
| NHSSYN030 | FAKE-NHS Daniels, Charlene Lauren | 2 | 54 |
| NHSSYN031 | FAKE-NHS Stewart, Keith James | 1 | 21 |
| NHSSYN032 | FAKE-NHS Stephens, Trevor Antony | 1 | 15 |
| NHSSYN033 | FAKE-NHS Osborne, Philip Clive | 1 | 17 |
| NHSSYN034 | FAKE-NHS Ellis, Tomos | 1 | 15 |
| NHSSYN035 | FAKE-NHS Holmes, Georgina Sophie | 1 | 19 |
| NHSSYN036 | FAKE-NHS Zhao, Lei | 1 | 18 |
| NHSSYN037 | FAKE-NHS Stephens, Trevor Antony | 1 | 12 |
| NHSSYN038 | FAKE-NHS Adewale, Oluwadamilola | 1 | 18 |
| NHSSYN039 | FAKE-NHS Rehman, George Alfred | 1 | 17 |
| NHSSYN040 | FAKE-NHS Xu, Yu | 1 | 17 |
| NHSSYN041 | FAKE-NHS Yoshida, Minato | 1 | 11 |
| NHSSYN042 | FAKE-NHS Usman, Muhammad | 1 | 15 |
| NHSSYN043 | FAKE-NHS Suzuki, Ren | 1 | 10 |
| NHSSYN044 | FAKE-NHS Kaur, Katie Catherine | 1 | 35 |
| NHSSYN045 | FAKE-NHS O'Brien, Paul Stuart | 1 | 14 |
| NHSSYN046 | FAKE-NHS Thakur, Krishna | 1 | 16 |
| NHSSYN047 | FAKE-NHS Gray, Jack Geoffrey | 1 | 15 |
| NHSSYN048 | FAKE-NHS Matsumoto, Yuri | 1 | 13 |
| NHSSYN049 | FAKE-NHS Adams, Victoria Edith | 1 | 22 |
| NHSSYN050 | FAKE-NHS Perry, Irene Amy | 1 | 16 |

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
mariadb -h db -u root carlos < .devcontainer/db/scripts/nhs-synthetic/patients.sql
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
The original three patients were verified through eChart and patient overview,
including original source text and desktop/mobile layouts; the other 47 were loaded
into a development database and counted, not individually opened.

Dataset citation: Poulett et al. (2026), *A Pipeline for Generating Longitudinal
Synthetic Clinical Notes Using Large Language Models*, arXiv:2606.26879.
