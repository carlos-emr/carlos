# OSCAR 19 → CARLOS Clinic Migration Plan (Turnkey Import)

**Status:** Plan / design document (implementation tracked separately)
**Scope:** Migrating a live clinic from OSCAR 19 (oscaremr) to CARLOS EMR using two
artifacts produced on the old server: a full `mysqldump` of the OSCAR database and a
tar of the OscarDocument tree. The goal is a one-command import on a freshly
provisioned CARLOS system.

---

## 1. Source of truth for the OSCAR 19 schema

- Upstream repository: `https://bitbucket.org/oscaremr/oscar.git`
- OSCAR 19 is the `master` branch of that repo (release tag `OSCAR_19_RC1`; master
  HEAD as analyzed: `a7900d5`, 2020-03-09).
- OSCAR 19 has **no Flyway or automated schema migration**. A live database is the
  product of `database/mysql/oscarinit.sql` + `oscarinit_on.sql` (or `_bc`) +
  `caisi/initcaisi.sql` + `olis/olisinit.sql` + whatever subset of
  `database/mysql/updates/*.sql` (and vendor/OSP patches) was hand-applied over the
  years. **Two real O19 clinics rarely have identical schemas.** The importer must
  therefore be schema-tolerant and driven by *introspection of the actual dump*, not
  by a fixed expectation.

CARLOS, by contrast, has a single deterministic schema: Flyway `V1` baseline +
`V1.0.N` forward migrations (`database/mysql/migration/`), per
`docs/database-schema-management.md`.

## 2. Schema comparison findings (Ontario profile)

Comparison of the full O19 Ontario live-table superset (init + CAISI + OLIS +
updates) against CARLOS `common` + `on` migrations:

| Category | Count | Consequence |
|---|---|---|
| Tables present in both | ~397 | Bulk of clinic data; copied by the importer |
| O19-only tables (dropped/pruned in CARLOS) | ~202 | Mostly removed modules; a minority hold patient data and need archival (see §5) |
| CARLOS-only tables (new features) | ~31 | Nothing to import; left as Flyway creates them |
| Shared tables with column differences | 83 | Need explicit column mapping |
| Shared tables where O19 has columns CARLOS dropped | 39 | Dropped columns logged; a few need review (see §4.3) |

Key core-table deltas (O19 → CARLOS):

- `demographic`: CARLOS adds gender/pronoun fields, residential address, rostering
  fields; O19 `preferred_lang` has no direct CARLOS column (map or drop — verify
  against CARLOS `official_lang`/language handling during implementation).
- `security`: CARLOS drops `storageVersion`, adds MFA/OneID columns. O19 legacy
  SHA-1 password hashes are **accepted by CARLOS and auto-upgraded to
  `{bcrypt}` on first login** (see `docs/Password_System.md`), so provider logins
  survive migration. PINs carry over.
- `document`: CARLOS adds `receivedDate`, `abnormal`, `report_media`,
  `sent_date_time`; drops `fileSignature`.
- `drugs`: CARLOS adds pharmacy/protocol columns; drops `dispensingUnits`
  (drug-dispensing module removed); `outside_provider_name`/`outside_provider_ohip`
  survive and copy.
- `provider`, `tickler`, `preventions`, `consultationRequests`: additive only —
  straight copy of shared columns.
- Charset: O19 init scripts declare no charset (live DBs are typically the MySQL 5.x
  server default `latin1`, sometimes mixed after years of patches). CARLOS is
  uniformly `utf8mb4`. Conversion happens implicitly during cross-schema
  `INSERT…SELECT`, but double-encoded UTF-8 (a classic OSCAR artifact) must be
  detected and repaired (§4.4).

Full generated table lists are in Appendix A/B. The per-column diff is regenerated
by the importer tooling at build time (§6) rather than frozen here.

## 3. Architecture: staging-schema ETL, not direct restore

A raw O19 mysqldump **cannot** be restored into the CARLOS schema: `mysqldump`
emits column-less `INSERT INTO t VALUES (…)` rows, so any column-count difference
(83 shared tables) breaks the load, and 202 tables no longer exist. The turnkey
flow is therefore:

```
 o19.sql.gz ──► restore verbatim ──► staging schema `o19_import`
                                            │
 Flyway (common + province) ──► fresh `carlos` schema (reference data from CARLOS)
                                            │
                    ETL: INSERT INTO carlos.t (cols…) SELECT cols… FROM o19_import.t
                                            │
 o19-documents.tar.gz ──► /var/lib/OscarDocument/carlos/… + reconciliation
                                            │
                              verification report + archival schema `o19_archive`
```

Principles:

1. **The dump restores verbatim** into `o19_import` with
   `SET sql_mode=''`/`FOREIGN_KEY_CHECKS=0` and the dump's own charset directives,
   so nothing is lost or mangled at ingest.
2. **CARLOS reference data wins.** ICD-9/10, OHIP service codes, measurement
   map, prevention config etc. come from Flyway, not from the dump — except
   tables in the "clinic-authored" list (§4.2). Seeded lookup tables the clinic
   also edits are `replace_seed` or `merge` (§4.1), never silently dropped.
3. **Clinic data copies with explicit column lists** from a generated, versioned
   mapping manifest (§6). Copies preserve primary keys (`demographic_no`,
   `provider_no`, document ids, …) because cross-table references in OSCAR are by
   raw id with no FK constraints.
4. **Nothing is silently discarded.** Every dropped table/column with non-trivial
   data lands in `o19_archive` (kept read-only) and in the final report.
5. **The live CARLOS schema must be empty of clinic data** (fresh Flyway install)
   — the importer refuses to run otherwise, except for the known seed rows it
   explicitly reconciles (§4.5).

## 4. ETL rules

### 4.1 Table classification (driven by the manifest)

| Class | Handling |
|---|---|
| `copy` | Shared table, clinic data → copy shared columns; CARLOS-added columns take their defaults |
| `reference` | Shared table, CARLOS-seeded reference data → keep CARLOS rows, ignore dump (e.g. `icd9`, `icd10`, `secPrivilege`; the full list is `CLASS_REFERENCE` in `overrides_schema.py`) |
| `merge` | Shared table containing both seed and clinic rows → anti-join on a natural key: CARLOS seed rows win, clinic rows append (e.g. `property`, `encounterForm`, private billing codes in `billingservice`, the role matrix `secObjPrivilege`/`secObjectName`, `lst_gender`) |
| `archive` | O19-only table with patient/clinic data → copy into `o19_archive` + CSV export (see §5) |
| `drop` | O19-only table from removed infrastructure with no clinical value → recorded in report only (Integrator, sharing/XDS, `cr_*` cookie-revolver, report-runner templates, temp tables) |

### 4.2 Clinic-authored data that must copy even in "reference-looking" tables

- `eform`, `eform_data`, `eform_groups`, `eform_values` — the clinic's eForm
  library and every filled instance.
- Custom/private billing codes and clinic fee overrides in `billingservice`
  (copy rows not matching CARLOS-seeded official codes), `billing_on_*` claim
  history, `teleplan*`/BC equivalents for BC clinics.
- `measurementType`/`measurementMap` customizations, custom `preventions` config
  (`preventionsConfig`-style properties), `mygroup` schedule groups,
  `scheduletemplate*`, `appointmentType`, `lookup` lists edited by the clinic.
- `property` (the `UserProperty` entity) merges on `(name, provider_no)` with
  removed-module keys pruned (§4.5); `ProviderPreference` copies as clinic data.

### 4.3 Column-level rules

- **CARLOS-added columns**: rely on schema defaults; where semantics need a value
  (`document.receivedDate` ≈ `observationdate`, `tickler.creation_date` ≈
  legacy update timestamp) the manifest carries a per-column SQL expression.
- **O19-dropped columns** (39 tables): logged per-table with a non-null/non-default
  row count. Ones worth an explicit implementation decision:
  `demographic.preferred_lang`, `document.fileSignature`,
  `drugs.dispensingUnits`, `formLabReq07` PSA/FOBT tick-boxes,
  `formRourke2009` "No" checkbox variants, `eChart` legacy form columns. Where a
  CARLOS equivalent exists, map; otherwise the values go to `o19_archive` shadow
  tables.
- **Renames**: the manifest supports `source_column → target_column` mappings so
  drift (e.g. language fields) is a data fix, not code.
- **Type tightening**: values that violate CARLOS types/strict mode are sanitized
  during copy: zero dates (`0000-00-00`) → NULL where CARLOS allows, out-of-range
  enums → default + report line, over-length strings → error (never silent
  truncation of PHI).

### 4.4 Charset / mojibake

- Restore staging with the dump's declared charset; copy via `INSERT…SELECT` into
  utf8mb4 targets (server converts).
- Pre-copy scan flags double-encoded UTF-8 (bytes matching
  `Ã[€-¿]`-class patterns in name/note/text columns) and applies the standard
  `CONVERT(BINARY CONVERT(col USING latin1) USING utf8mb4)` repair per column,
  gated by a dry-run sample report the operator approves.

### 4.5 Seed-row reconciliation (fresh CARLOS DB vs incoming dump)

Both systems seed a default doctor at `provider_no 999998` (O19 `oscardoc`,
CARLOS `carlosdoc`) plus matching `security`, schedule and property rows. The
importer must, in order: delete the CARLOS `carlosdoc` seed rows (provider,
security, preferences) **after** creating a fresh break-glass admin account chosen
by the operator, then copy the clinic's providers/security verbatim. The same
reconciliation applies to `property` defaults (CARLOS value kept unless the
clinic explicitly overrode the key in O19; `SystemPreferences` is CARLOS-only
and has nothing to reconcile).

Post-copy: set `security.forcePasswordReset = 1` for every imported user (legacy
SHA-1 hashes still work for the first login and auto-upgrade to BCrypt; the forced
reset moves everyone onto CARLOS password policy immediately).

#### Roles, privileges and CARLOS-required rows (M8)

CARLOS's privilege check is exact-match and deny-by-default: `hasPrivilege` reads
the `secUserRole` rows with `activeyn = 1`, joins them to `secObjPrivilege` by role
NAME, and has no parent fallback (`_admin.fax` does not inherit `_admin`). The
importer therefore reconciles the role matrix instead of choosing one side:

- `secObjPrivilege` and `secObjectName` are **merge** tables on their primary key:
  CARLOS's seeded grants win on a `(role, object)` collision (deliberate CARLOS
  policy such as `doctor`/`_eform` = `w`), the clinic's own rows append — custom
  roles, per-provider overrides (`roleUserGroup` = provider_no), patient-scoped
  lockouts (`_eChart$<demo>`, `_all`), document-queue objects (`_queue.<id>`).
  Grants on objects no CARLOS code checks (the removed traceability report's
  object, `_newCasemgmt.clearTempNotes`, …; the explicit `MERGE_EXCLUDE` list)
  are not carried. Every clinic grant the seed overrode is itemised in
  `privilege-diff.txt` (root-only) for the technical review.
- `secRole`/`secUserRole` stay **replace_seed** (clinic ids are referenced by
  `program_provider.role_id`); the post-step re-adds CARLOS-only roles (`HRMAdmin`,
  `Site Manager`, `Partner Doctor`) by name and guarantees `doctor` and `admin`
  exist (startup dereferences `doctor`).
- A **clinic-custom role** (not a CARLOS stock role name) receives the CARLOS-era
  grants — objects the clinic's O19 never had (`_fax`, `_email`, `_rx.editPharmacy`,
  `_admin.flowsheet`, …) — of the stock role its imported privilege set resembles
  most (Jaccard over (object, privilege) pairs against stock role names only —
  never the seed's `-1`/provider pseudo-groups — floor `ROLE_TEMPLATE_MIN_JACCARD`
  = 0.3, ties alphabetical), `INSERT IGNORE` so nothing imported is overwritten;
  `--role-template CUSTOM=STOCK` (case-insensitive, like the column) overrides the
  choice. The mapping is recorded in the ETL ledger once it has validated and
  bound once the backfill has decided on it; until it is bound a resume may
  add or change the flag (reported), after that a different mapping is refused and a resume
  without the flag continues with the recorded one — a typo therefore never
  wedges the import. Below the floor nothing is guessed and the role is reported
  for manual grants; a template that holds none of the CARLOS-era objects (every
  nurse/receptionist-class stock role: the seed grants them to `doctor`/`admin`
  only) adds nothing and says so; a custom role holding no imported grant is
  left alone (P7 lists it as granting nothing if an active account holds it).
  Role names are compared case-insensitively (and trailing-space-insensitively,
  like the column's PAD SPACE collation) throughout — importer and preflight
  alike (`Doctor` is `doctor`). `admin` as a template is applied automatically
  only above similarity 0.5 and flagged in the report; below that the role is
  held for an explicit `--role-template 'X=admin'`.
- `secUserRole.activeyn` NULL grants nothing in CARLOS; the import sets it to 1 for
  assignments of providers whose account is active (`status = '1'` with a
  `security` row) and lists them (provider = role) in `roles-details.txt`, with a
  per-role tally in the report — except `admin` assignments: CARLOS treats a NULL
  admin row as inactive on purpose (`SecUserRoleDaoImpl`), so those stay dormant
  and are listed for an explicit decision. Active assignments to the CARLOS-only
  roles the step re-adds (they granted nothing in O19) are counted in the report
  and listed privately. The break-glass admin's cloned roles are created active.
- Case management fails closed without `program_provider` membership, and the
  webapp's first start creates program `OSCAR` plus a membership for the seeded
  clinician (deleted by the import). The post-step creates `OSCAR` if absent, gives
  every active provider without any membership a row there (role_id = the clinic's
  own `secRole.role_no`: `doctor` when held, else the active role with the most
  privilege rows; a provider with no active role gets the clinic role with the
  fewest — least privilege until an administrator assigns one — counted in the
  report and listed in `roles-details.txt`), and links every active provider to
  the first enabled `Facility`. The membership report counts what this run
  actually inserted per statement. An import with no enabled `Facility` or no
  `clinic` row — or a dump lacking any table the step reads (`Facility`,
  `clinic`, `provider`, `security`, `secRole`, `secUserRole`, `secObjPrivilege`,
  `secObjectName`, `program`, `program_provider`, `provider_facility`,
  `preventions`, `eform`, `property`) — is refused by the ETL pre-checks against
  staging, before the first write (preflight reports the Facility/clinic
  conditions as blockers). The same pre-check refuses a server whose `sql_mode`
  carries `NO_BACKSLASH_ESCAPES` or `ANSI_QUOTES` (the plain client quotes clinic
  values with backslash escapes), and P0 refuses a non-Ontario province up front
  (the seed floors are Ontario's).
- The P0 sweep tolerates exactly the rows the webapp creates on first start
  (`STARTUP_CREATED_ROWS`: the `OSCAR` program and membership, the `Main Clinic`
  site and its provider link) and the seed script deletes them before the copy —
  a packaged host has always booted once before an import.
- `property` merges on `(name, provider_no)` (CARLOS defaults stand, clinic keys
  append; O19 writes `''` and CARLOS NULL for a global key — `NULLIF` makes them
  one key) and rows of removed modules (`PREFLIGHT_DROPPED_PROP_PREFIXES`) are
  pruned.
- The privilege merge keeps every CARLOS seed grant, appends every clinic grant
  whose (role, object) the seed does not hold, and omits only grants on objects
  no CARLOS code checks and no seed grants (`MERGE_EXCLUDE`, an explicit list —
  the `_pmm.*` objects the program-management code still checks are carried;
  only `_pmm.editProgram.schedules` and `_pmm.functionalCentre` are listed).
  `privilege-diff.txt` itemises four things for the review: clinic grants
  CARLOS's seed overrode on the same (role, object); seed grants on roles the
  clinic has whose (role, object) the clinic's matrix did not hold at all (a
  grant the clinic removed comes back, so the role can do more than in O19);
  clinic grants on stock roles the seed does not hold (appended, so a stock role
  is wider than the seed — appends on `_admin*` objects are named in the report
  line); and the excluded grants.
- Data normalisation CARLOS ships only as post-baseline scripts never reaches
  imported rows through Flyway (it ran on the empty deploy first), so the post-step
  applies it: `preventions.prevention_type` legacy codes follow the generated
  `PREVENTION_TYPE_MAP` (from `update-2026-03-10-standardize-prevention-types.sql`)
  with a **binary** compare — the column's collation is case-insensitive and the
  map's `dTaP` would otherwise drag the valid pediatric `DTaP` into `Tdap` (the
  upstream script has that latent defect; codes the map does not know stay as
  they are and are reported) — and the Rich Text Letter eForm is brought to
  2026.3.0 with the packaged scripts under `/usr/share/carlos-emr/schema/o19-fixups/`
  run through the ETL executor (`sql_mode=''`). The scripts address
  `form_name = 'Rich Text Letter'` with `subject LIKE 'Rich Text Letter Generator%'`
  only — evaluated by the database under the column collation, so the planner
  and the scripts agree on which row is canonical — so: the v1 seed runs first
  when no such row exists (the clinic then gets a new, ENABLED Rich Text Letter,
  and the seeded `eform` row is recorded for row parity); only the route fix runs
  when a marked row still calls a dead JSP route; a canonical row that was
  disabled (O19's own seed ships it so) is re-disabled afterwards; a row whose
  subject was edited is reported, not claimed fixed; after the scripts the step
  re-reads the rows and reports "modernised" only if a canonical row carries the
  marker with live routes (P7 repeats that check as an advisory). The plan (rows
  to disable, scripts, rows to re-disable) is persisted before the first write,
  so a crash between the enable script and the re-disable cannot make a hidden
  form visible on resume. Missing fixup scripts are a broken package install:
  the step fails closed and resumes once they are back. Any other RTL-titled row
  such as O19's 2010 `letter` is disabled — it carries the `RptByExample.do`
  sink.
- `value_exprs` now also cover `document.receivedDate` (= `observationdate`) and
  `tickler.creation_date` (= legacy `update_date`, else `service_date`, else the
  fixed `UNKNOWN_DATE_SENTINEL` `1970-01-02 00:00:00` rather than the import time,
  so a re-import is reproducible and the value is visibly unknown; O19's
  `0001-01-01` column default and MySQL zero dates count as absent), and a
  synthesized column is always part of the copied column list (the earlier
  `pharmacyInfo.uid` entry was never written).
- P7 verifies the guarantees (roles present, admin usable, facility/clinic/program
  rows, memberships, seed floor) and reports clinic-data conditions as advisories
  (active accounts with no active role, roles granting nothing, expired logins,
  empty `OscarJobType`, RTL not current). Row parity tolerates the appended rows
  the roles ledger recorded, and only those. The ledger records what the database
  holds AFTER each write (the same "rows without a staging twin" count parity
  measures) and persists every decision (who gets activated, who gets the
  fallback role, which template, which counts) BEFORE the write, so a crash
  between a write and its mark leaves nothing unrecorded on resume.
- P0's tolerance of startup-created rows is capped at one row per table (the
  webapp creates each exactly once); the seed floors count `INSERT IGNORE`
  tuples (V1.0.5 seeds `bed_type`/`lst_*` only that way) — a first cut that
  skipped them made P0 refuse every Flyway-built host, masked in rehearsal by
  the dev-target advisory sweep.

Still maintainer-side (Flyway, for stock installs): promote the prevention-type
script **with a binary compare** (`update-2026-03-10-standardize-prevention-types.sql`
rewrites `DTaP` on a `_ci` collation; decide `H1N1 -> Inf` while `H1N1` is a
rendered code) and the RTL scripts, seed the `OSCAR` program and default site,
and fix the seed's `_masterlink`/`_masterLink` split and the ~30 code-referenced
objects with no seeded grant.

### 4.6 O19 modules removed from CARLOS — data disposition

| O19 module (tables) | Disposition |
|---|---|
| Antenatal forms `formAR`, `formONAR`, `formONAREnhancedRecord(+Ext1/2)` | **archive** — patient data; see `docs/migration-deprecated-form-tables.md`; export CSV + `o19_archive` |
| Generic intake (`intake*`, `formIntakeHx`), OCAN (`Ocan*`), eyeform (`eyeform*`, `Eyeform*`, `specshis`, `procedurebook`…) | **archive** if row counts > 0 |
| HSFO study (`hsfo_*`, `form_hsfo_visit`), CAISI beds/rooms (`bed*`, `room_*`, `vacancy`, `complaint`, `incident`) | **archive** if used (CHC-style sites), else drop |
| OLIS (`OLIS*`) | **archive** query prefs/log; OLIS module does not exist in CARLOS — flag to clinic |
| Integrator/sharing/PHR/Indivo/BORN (`Integrator*`, `sharing_*`, `phr_*`, `indivoDocs`, `BORN*`), cookie-revolver `cr_*`, report-runner `report_*`, MDS raw (`mdsZCL`, `mdsZCT`), `RedirectLink*`, temp/backup tables | **drop** (report only) |

Everything classified `archive` also produces a per-table CSV under
`…/o19_archive_export/` inside the documents tree so the clinic holds a readable
copy independent of MariaDB.

## 5. Documents tar

What to tar on the O19 server — the **entire** OscarDocument context tree, not
just `document/`:

```
tar -C /var/lib/OscarDocument -czf o19-documents.tar.gz <contextname>/
```

Import steps:

1. Untar under `BASE_DOCUMENT_DIR` (default `/var/lib/OscarDocument/`), renaming
   the O19 context directory (often `oscar`, `oscar_mcmaster`, or the clinic db
   name) to `carlos` to match `carlos.properties`
   (`DOCUMENT_DIR=/var/lib/OscarDocument/carlos/document/`,
   `INCOMINGDOCUMENT_DIR=…/carlos/incomingdocs`). Subtrees that ride along:
   `document/`, `eform/images/` (eForm image assets), `incomingdocs/`,
   `billing/download/`, HRM report files, faxes, export dirs.
2. `chown -R` to the Tomcat service user (directories 2750, files 0640).
3. **Reconciliation report** (blocking, not advisory):
   - every `document.docfilename` row → file exists, non-zero size;
   - every `eform` referencing a `${oscar_image_path}` asset → file exists
     (the reference is decoded the way the image route receives it: whole
     attribute value, entities and percent-encoding undone);
   - orphan files (on disk, no DB row) counted in the report, named in the
     root-only details file;
   - HRM report files are moved out of the whole `hrm/` tree (O19 keeps them
     under `hrm/sftp_downloads/<date>/decrypted/`) into `document/`, and every
     `HRMDocument.reportFile` is rewritten to its basename inside the CARLOS
     `DOCUMENT_DIR` (the only location CARLOS's HRM reader trusts); two rows
     reaching one basename through different paths are refused.

## 6. Packaging: one-command turnkey import

Two coordinated deliverables:

**(a) `carlos` repo — `scripts/migration/o19/` (the engine)**

- `o19-schema-map.yaml` — the versioned manifest: table classes (§4.1), column
  mappings, per-column value expressions, archive/drop lists. Generated initially
  from the schema diff (this analysis), then hand-curated and code-reviewed.
- `o19_import.py` (or shell+SQL) — phases: `preflight`, `stage`, `etl`,
  `documents`, `verify`, each idempotent and resumable; writes a single
  machine+human readable report.
- `preflight` — the go/no-go gate, specified in §6.1.
- `verify` (§7) gate.

### 6.1 Preflight gate — go/no-go blocker detection

Preflight is a **read-only feasibility check** that decides whether this
migration path is viable for a given clinic *before* anything is written. It
runs in two modes with identical checks:

- **Assessment mode** — a standalone script (`o19-preflight`, no CARLOS install
  required) run against the *live O19 database + properties file at the clinic*,
  before backups are ever shipped. This is how a migration is quoted/approved.
- **Import mode** — the same checks re-run automatically by `import-o19` against
  the restored `o19_import` staging schema, since the shipped dump may differ
  from what was assessed.

Every finding has a severity; the outcome is the worst severity found:

**BLOCKER — the import refuses to proceed until remediated or explicitly
signed off:**

| # | Check | Detail |
|---|---|---|
| B1 | **Patient data in tables CARLOS doesn't have** | Row counts on the curated patient-data subset of the 202 O19-only tables (`formONAREnhancedRecord*`, `formAR`, `formONAR`, `formType2Diabetes`, `formIntakeHx`, `intake*`, `Ocan*`, `eyeform*`/`Eyeform*`, `hsfo_*`, CAISI bed/room/admission-adjacent tables). Non-zero rows → blocker naming the table, its row count, and the disposition on offer (archive + CSV, §4.6). Cleared only by the operator passing an explicit per-class acknowledgement (e.g. `--accept archived-forms`), which is recorded in the report as the clinic's sign-off |
| B2 | **Unknown tables/columns with data** | Anything in the dump not classified in `o19-schema-map.yaml` (vendor forks — WELL/KAI, OMD patches) with rows → blocker until a human classifies it (manifest update or `--accept unknown-as-archive`). Unknown never silently drops |
| B3 | **Data in O19 columns CARLOS dropped** | Non-null/non-default counts on the flagged columns of the 39 tables in Appendix C (e.g. `drugs.dispensingUnits` ≠ empty means the dispensing workflow was in use). Above-threshold usage → blocker with per-column counts |
| B4 | **Encrypted casemgmt notes** | Encryption markers in `casemgmt_note` / site config → blocker (key handling not in scope of the standard path) |
| B5 | **LDAP authentication in use** | `ldap.enabled=true` in properties → blocker: staff cannot log in to CARLOS via LDAP; local credentials must be provisioned first |
| B6 | **Live credentials carried** | rows in `ServiceClient` / `oscarKeys` / `publicKeys` (OAuth consumer secrets, signing keys) are copied verbatim and keep working → blocker cleared by `--accept carry-credentials`; rotate or verify before go-live |
| B6 | **Target not pristine** (import mode only) | CARLOS schema contains clinic data beyond the known seed rows → refuse |
| B7 | **Capacity/compatibility** | Insufficient disk for staging + archive + documents; dump collations unavailable on the target MariaDB; dump truncated/incomplete (missing `-- Dump completed`) |
| B8 | **Unrepairable text encoding** | Charset sampling (§4.4) finds mixed/double-encoded text the standard repair can't normalize deterministically |

**ADVISORY — reported prominently, does not stop the import:**

- Removed-module data that auto-archives without workflow impact (OLIS logs,
  Integrator/sharing tables, report-runner templates) — itemized so the clinic
  knows what becomes archive-only.
- **Properties fallout**: every clinic-set key classified `dropped-flag` (§8.1
  rule 6) — the "many keys no longer needed" list — grouped by module, plus the
  `deploy-owned` keys that will be ignored. OLIS and eRx keys escalate the
  matching module advisory; `ldap.*` escalates to B5.
- Providers with no `security` row, disabled accounts, stale `secUserRole`
  entries — hygiene items that surface as confusing login behavior later
  (the roles advisories of §4.5).
- Documents are not assessed by the preflight (no tar is read); full
  reconciliation runs as a hard gate in the `documents` phase (§5).

**INFO:** table/row inventory, database size, provider/patient counts (sanity
anchors for §7 row-parity).

**Report contract.** Preflight always emits a machine-readable JSON verdict
(`go` / `no-go` / `go-with-acknowledgements`) plus a human report listing every
blocker with its remediation and every advisory. `import-o19` runs it first and
stops on `no-go`; acknowledgements are CLI flags so the sign-off is explicit,
auditable, and can't happen by default. Assessment-mode output doubles as the
clinic-facing feasibility statement: what migrates, what becomes archive-only,
what stops working (OLIS, eRx, MyOSCAR, LDAP), and what must be decided before
cutover.

**(b) `carlos-podman` repo — `carlos-ctl import-o19` (the turnkey wrapper)**

> Superseded: the deb's `carlos-ctl import-o19` (§9a) is the shipped form —
> `--admin-user` is mandatory, `--province` may only restate the host's
> configured province, the application is never restarted by the import and
> the properties fragment is applied by hand. The sketch below is kept as the
> original design; the podman wrapper is still pending.

`carlos_ctl` already owns db provisioning, Flyway migrate, backup and dump
(`dbops.py`, `backup.py`). New subcommand:

```
carlos-ctl import-o19 \
    --dump /srv/migration/o19.sql.gz \
    --documents /srv/migration/o19-documents.tar.gz \
    --province on \
    --properties /srv/migration/oscar.properties \
    [--dry-run]
```

which: verifies the target is a fresh install (schema fingerprint), snapshots a
restic backup point, creates `o19_import`/`o19_archive`, runs the engine phases
inside the db container, untars documents into the app volume, restarts the app,
and prints the verification report. `--dry-run` runs `preflight` + row-count/
charset analysis only. Rollback = restore the pre-import snapshot (already a
`carlos_ctl` capability).

## 7. Verification gate (must pass before go-live)

- **Row parity**: per-table `o19_import` vs `carlos` counts for every `copy`
  table, with expected deltas (seed reconciliation) itemized.
- **Referential spot checks**: demographics ↔ appointments ↔ notes ↔ documents ↔
  drugs joined on ids for N random patients; billing invoice totals per fiscal
  year match to the cent.
- **Files**: document reconciliation (§5.3) clean.
- **App-level smoke**: the existing Playwright UI test suite (`ui-tests` skills,
  tests 1–9: login, demographic search, appointments, Rx, ticklers, encounter,
  ON billing, labs, preventions) run against the migrated database — this is the
  strongest "the clinic can work tomorrow" signal.
- **Login**: sample migrated provider logs in with O19 credentials, hash upgrades
  to `{bcrypt}`, forced reset flow completes.

## 8. What else to collect from the O19 server (advisory checklist)

Beyond the mysqldump and the OscarDocument tar, capture:

| Item | Why / where it goes |
|---|---|
| `oscar.properties` (+ any override file, often in `$CATALINA_HOME` or `/usr/share/oscar*`) | **Required input** — translated (not copied) into a reviewed properties fragment (`o19-derived-carlos.properties`, appended by the operator to the deployment's override file — `/etc/carlos-emr/carlos.properties` on the deb) by the importer's `props` phase; see §8.1 |
| `drugref.properties` / drugref DB | **Not needed** — CARLOS runs its own fresh drugref (`drugref2026`); no clinic data lives there |
| MCEDT/EDT credentials (MOH GO-Secure user, MCEDT keystore/cert if configured) | Ontario billing upload/download continuity |
| HRM SFTP private key + HRM decryption key (`OMD HRM` config) | HRM feed continuity (CARLOS retains HRM tables) |
| Lab feed credentials (Excelleris/LifeLabs/MDS/CML poller configs, certs, any `hl7` dropbox paths, CDS/OLIS certs) | Re-provision feeds; note OLIS module is **not** in CARLOS — clinic must be told before cutover |
| Fax setup details (Hylafax host or SRFax account) | CARLOS supports SRFax natively; Hylafax sites need an SRFax (or legacy relay) decision pre-cutover |
| Tomcat `server.xml`/TLS keystores | Reference only; CARLOS/podman terminates TLS differently |
| Crontabs & backup scripts | Inventory of scheduled jobs (label reprints, backups, custom exports) that need CARLOS equivalents |
| Any custom JSP/eForm assets living *outside* OscarDocument (rare; some sites drop images into the webapp) | Sweep `webapps/oscar*/eform*` and `images/` for clinic files |
| MyOSCAR/PHR registration details | Decommission notice — PHR/Integrator removed in CARLOS |
| Final pre-cutover incremental dump | Take the *real* migration dump during the cutover window after stopping O19 Tomcat, so no appointments/notes are lost; the earlier dump is for rehearsal |

Recommended cutover shape: **rehearse on a copy** (dump A) → fix manifest fall-out
→ freeze O19 → dump B + documents rsync top-up → import → verify → go live. The
importer being idempotent/re-runnable from `stage` makes the rehearsal cheap.

### 8.1 `oscar.properties` → CARLOS configuration translation

The clinic's deployed `oscar.properties` is a **required migration input** (it is
the third artifact alongside the dump and the documents tar). It is never copied
into place; the importer *translates* it.

**Where the output goes.** CARLOS loads the baked-in `/carlos.properties` from the
WAR, then applies the file named by the `carlos_override_properties` system
property (`over_ride_config.properties` in the devcontainer/podman deployments) —
see `io.github.carlos_emr.CarlosProperties`. The importer therefore emits a
reviewed fragment, `o19-derived-carlos.properties`, that the operator
merges into the deployment's override file. The WAR's `carlos.properties` is
never edited.

**Key universe** (repo defaults compared; a real clinic file is a customized
superset/subset of the O19 default): O19 ships ~450 active keys, CARLOS ~338
active (~432 documented). ~260 keys exist on both sides; ~190 active O19 keys
have no CARLOS counterpart at all.

**Translation rules, applied in order by a `props` importer phase driven by a
`o19-properties-map.yaml` manifest:**

1. **Baseline-diff first.** Only keys whose clinic value differs from the O19
   *default* value (repo `oscar_mcmaster.properties`) are considered — a clinic
   file is mostly untouched defaults, and CARLOS defaults should win wherever the
   clinic never made a choice.
2. **`carry`** — clinic identity and workflow keys copied verbatim when present
   in CARLOS: `billregion`, `billcenter`/`default_bill_center`, `clinic_no`,
   `dataCenterId`, `phoneprefix`, `visitlocation`/`visittype`, scheduling and
   caseload defaults, consultation/rx/tickler/eform feature toggles, label
   printer geometry (`label.*`), DX quick lists, `Support_Contact`,
   `instance_type`-style region flags, `isNewONbilling`, prenatal screening
   eform bindings, `lab.handler.*.enabled` toggles (still read by CARLOS code
   even though absent from the default file).
3. **`carry-secret`** — copied but printed in a separate "credentials imported —
   rotate/verify" list, never echoed to the main report: `mcedt.service.*` +
   `mcedt.keystore.*` (MOH EDT), `hcv.*` (health-card validation), `email.*`,
   PGP keys (`PGP_*`), Teleplan credentials on BC profiles.
4. **`translate`** — value rewritten, not copied:
   - any value containing the O19 document root
     (`/var/lib/OscarDocument/<oldctx>/…`, `/usr/local/…/OscarDocument/…`) →
     the CARLOS context path `/var/lib/OscarDocument/carlos/…`
     (`ONEDT_INBOX/OUTBOX/SENT/ARCHIVE`, `INVOICE_DIR`, `hl7_a04_build_dir`,
     `INCOMINGDOCUMENT_DIR`, eform image paths);
   - `drugref_url` → the new drugref2026 endpoint for this deployment;
   - fax configuration → CARLOS `FaxConfig` is DB-backed with SRFax as the
     supported provider; O19 `faxURI`/Hylafax values become an operator prompt,
     not a copied key.
5. **`deploy-owned` (never carried, importer refuses even if present):**
   `db_uri`/`db_username`/`db_password`/`db_driver` and all pool keys,
   `hibernate.*`, `tomcat_path`, `project_home`, `oscar_port`,
   `TOMCAT_KEYSTORE_*`/`TOMCAT_TRUSTSTORE_*`, `BASE_DOCUMENT_DIR`/`DOCUMENT_DIR`
   (set by the CARLOS deployment), `backup_path`, `buildtag`/`version*`.
   These belong to carlos-podman provisioning, and copying an O19 value would
   break or weaken the new install. (`login_local_ip` — the local-network
   ranges exempt from the failed-login lockout — is carried, as is
   `resource_base_url`, the clinic's provider-menu link target; the URL is
   validated as a plain http(s) URL because the provider JSPs render it into
   script.)
6. **`dropped-flag`** — O19 keys for modules CARLOS removed are *not* carried but
   are itemized in the report so nobody assumes the feature still works:
   BORN (`born*`), Integrator (`INTEGRATOR_*`), MyOSCAR/PHR (`MY_OSCAR`,
   `myoscar_*`), CBI (`CBI_*`), OLIS (`OLIS_*`, `olis_*`), eRx (`util.erx.*`,
   `RX3`), Clinicaid, Indivica/sharing center, Spire, `redirectstudysite_*`,
   `cr_security` (cookie revolver), login branding (`loginlogo`/`logintext`/
   `logintitle`). Two get an explicit **advisory** severity because they change
   how staff work on day one: `ldap.*` (LDAP auth — no CARLOS equivalent;
   clinics authenticating via LDAP must move to local credentials + MFA) and
   OLIS (lab querying gone).
7. **`unknown`** — keys in the clinic file matching none of the above (vendor
   forks add their own): reported for human classification; never silently
   dropped, never carried.

**Report contract.** The `props` phase always produces: the generated override
fragment; a table of every clinic-modified key with its disposition
(`carry`/`carry-secret`/`translate`/`deploy-owned`/`dropped-flag`/`unknown`) and
old→new values (secrets masked); and the advisory list. The fragment is inert
until the operator merges it — properties translation is deliberately the one
non-automatic step in the turnkey flow, because it is where machine-specific and
clinic-specific configuration meet.

`o19-properties-map.yaml` lives beside `o19-schema-map.yaml` in
`scripts/migration/o19/` and is seeded from the key diff summarized above, then
curated in review like the schema manifest.

## 9. Risks & open decisions

1. **Schema drift in the wild** — vendor forks add tables/columns. Mitigated by
   preflight introspection + archive-by-default for unknowns.
2. **BC profile** — this analysis is Ontario-first; BC (Teleplan tables,
   `oscarinit_bc`) needs its own manifest pass before a BC clinic migrates.
3. **`demographic.preferred_lang` and other mapped columns** — each needs a
   confirmed CARLOS destination during implementation, not assumed.
4. **Encrypted casemgmt notes** — if the source site enabled note encryption,
   key material handling must be added to the checklist (rare; detect via
   `casemgmt_note` content and site config in preflight).
5. **Antenatal (ONAR Enhanced) users** — CARLOS dropped the form; obstetrical
   practices must accept archive-only access or the form must be revived before
   such a clinic migrates. This is a go/no-go conversation per clinic.
6. **Database size** — multi-year clinics run 10–100+ GB with `hl7TextMessage`
   and `document` dominating; the ETL must stream (`INSERT…SELECT` server-side,
   no client round-trips) and disable binary logging on the staging schema during
   import.

## 9a. Implementation status (experimental)

The importer is being built into the Debian deployment's carlos-ctl first
(carlos-podman catches up later). The feature is **(experimental)**: every
migration's output should receive a technical review — verification report, spot
checks, UI smoke — before clinical use.

**Milestone 1 — manifests, generator, fixtures (done):**

- `scripts/migration/o19/generate_manifests.py` parses the O19 schema sources
  (init + data/ICD scripts + CAISI + OLIS + updates, statements applied in
  document order with quote-aware comment stripping) and the CARLOS Flyway set
  (read-only), deep-merges the curated overlays (`overrides_schema.py`,
  `overrides_props.py`), and emits the shipped manifests
  `debian/assets/carlos_ctl/o19map_schema.py` / `o19map_props.py`
  (`SCHEMA_MAP_VERSION` is a plain `o19map-N` token, deliberately not
  CalVer-shaped). `test_manifest_integrity.py` (22 checks, stdlib unittest)
  refuses any unclassified table.
- Current classification (580 O19 tables at commit `a7900d5`): 338 copy /
  31 merge / 27 reference / 156 archive (patient-data subset flagged for the
  B1 blocker; includes the three shared OAuth/session token tables, which
  are archived rather than restored live) / 28 drop / 0 unknown. Three
  copy-class credential tables (`ServiceClient`, `oscarKeys`, `publicKeys`)
  are named in the ETL report under a rotate/verify advisory.
- **Analysis corrections found during generation:**
  - `demographic.preferred_lang` is NOT a dropped column: O19's own
    `update-2009-02-23` renamed it to `official_lang`, which is shared and
    copies (§9.3 resolved). Pre-2009 unpatched databases surface it through
    preflight's unknown-column flow.
  - `icd9` exists in BOTH systems (created by O19 `icd9.sql`); the data/ICD
    scripts are part of the generator's source set so preflight never flags it.
  - Legacy/entity twin tables: no O19 table was renamed away; the twins still
    present in a patched O19 db (`group_note_link`, `recycle_bin`,
    `report_filter`) are archived, and twins O19's own updates already dropped
    (`facility`, `Vacancy`) fall to the unknown-table flow on unpatched sites.
- Seeded shared tables get three explicit treatments: `reference` (CARLOS
  wins: ICD, `secPrivilege`, error codes…), **`replace_seed`** (seed rows
  deleted, clinic rows copied id-intact because clinic data references those
  ids: `issue`, `program`, `clinic`, schedule config, role matrix), and
  `merge` on a natural key with surrogate-id reassignment flagged for the ETL
  (`measurementType`, `billingservice`, lookup lists…).
- Vendored fixtures (`scripts/migration/o19/fixtures/`, provenance in
  `PROVENANCE.md`): O19 `release/demo.sql` (GPL v2 header preserved), stock
  `oscar_mcmaster.properties`, a synthetic clinic-example properties file
  covering every props disposition, and a documents-tree manifest + generator
  (no binaries committed; includes a deliberate missing-file row and an orphan
  file). `build-o19-fixture.sh` builds the latin1 rehearsal database and emits
  the three turnkey inputs.

**Milestone 2 — standalone preflight (done):**
`debian/assets/carlos_ctl/o19_preflight.py` — one self-contained file
(old-python compatible, stdlib only, drives the mysql/mariadb CLI) that runs
the §6.1 gate in assessment mode at the clinic and is imported by carlos-ctl
for import mode (column-level unknown detection activates when the schema
manifest is passed). Verdict contract: exit 0 `go`, 1
`go-with-acknowledgements` (each blocker names its `--accept` flag), 2
`no-go` (LDAP, encrypted notes, BC, or unknowns needing classification);
`--json` emits the machine report. The generator rewrites its embedded data
block, and `test_preflight.py` (fake-SQL runner) plus a drift-lock
test in `test_manifest_integrity.py` pin the behavior. B4 detection keys off
`casemgmt_note` rows that carry a password (the property
`casemgmt.note.password.enabled=true` is the stock O19 default and alone is
an advisory); the report prints the canonical bundle
command so the O19 side produces what the CARLOS side expects.

**Milestone 3 — verbs, staging, bundle (done):**
`carlos-ctl import-o19` and `carlos-ctl o19-preflight` are registered
(lazy-imported so the manifest parse cost stays off every other verb) with
`(experimental)` denotations in `_USAGE` and `carlos-ctl.8`, plus the
`o19-import` state dir in tmpfiles (0700 — it holds secrets).
`o19import.py` implements the phase state machine (`state.json` ledger,
`--resume`, digests, persisted `--accept` sign-offs) and phases P0–P3: the
stock-initial-deploy pristine sweep (manifest seed counts, no accept flag,
`--dev-target`/connection-seam downgrade for dev databases, replica refusal,
disk headroom that never silently skips), single-pass streamed dump restore
into `o19_import` with binlog off + truncation-marker and collation
pre-checks (failed restores drop the schema), import-mode preflight (dry-run
reports the verdict instead of erroring), and the pre-import backup via the
systemd unit (`--accept no-pre-backup` for unconfigured boxes). `o19bundle.py`
implements `--bundle` per the plan: extension-classified members with hard
errors on ambiguity/traversal, magic-byte cross-checks, openssl
password-based decryption with canonical `-pbkdf2 -iter 200000` defaults and
loud wrong-key/derivation-mismatch guidance. Fixtures gain
`make-o19-bundle.sh` (all four variants); `test_bundle.py` (17 cases incl.
real openssl/tar end-to-end and a derivation-mismatch case) and
`test_state.py` (12 cases: ledger, pristine gate, disk, collations) bring
the suite to 73 passing tests. P4–P7 stop with an explicit
"milestone not built yet" error — never a silent no-op.

**Milestone 4 — ETL engine (done):**
`o19etl.py` — pure statement generation over runtime `information_schema`
introspection of both schemas, executed with the binlog-off session prelude:
`copy` (explicit column lists, renames, `value_exprs`; zero-date NULLIF only
where the target is nullable; enum out-of-set values fall to NULL/first
member; charset repair wraps only confirmed-mojibake columns after a
round-trip check that hard-blocks as B8), PK-window chunking with
`etl-progress.json` checkpoints, `merge` anti-joins on natural keys with
surrogate-id reassignment, `replace_seed` delete-then-copy, `archive` +
dropped-column shadow tables into `o19_archive`. Loud pre-checks run before
the first write: over-length values ERROR (never truncate PHI), and
CARLOS-added NOT NULL columns without defaults abort naming the
`value_exprs` curation needed. Seed reconciliation is strictly ordered
(break-glass admin created file-first with cloned seed roles → manifest
seed deletes → copies → global `forcePasswordReset`), and seed-group
retries exclude the admin. P4 wiring in `o19import.py` ends with a
row-parity report (admin delta itemized; any mismatch stops the run).
`test_etl_sql.py` + `test_seed_reconciliation.py` bring the suite to 106
passing tests, including generation over every real manifest entry.

**Milestone 5 — documents phase (done):**
`o19docs.py` — single-context-dir detection (loose files or two contexts
refuse), merge-move into `OscarDocument/carlos/` that never clobbers a
non-empty subtree, derived-cache directories (`document_cache`) skipped with
a report line, service-user ownership (2750 dirs / 0640 files),
`HRMDocument.reportFile` rewrite keyed on the old context marker with
unmatched rows counted, then the BLOCKING reconciliation: every
`document.docfilename` must exist non-empty, every eForm
`${oscar_image_path}` asset must resolve (mariadb batch-mode escaping
unescaped correctly), orphans report-only. The `o19_archive` schema is
exported as per-table CSV inside the documents tree. Idempotent by tar
sha256 — a rerun re-runs reconciliation only. `--accept no-documents`
records the documents-less sign-off. `test_docs.py` brings the suite to
118 passing tests.

**Milestone 6 — props phase + verify (done):**
`o19props.py` runs the §8.1 pipeline over the clinic's oscar.properties:
baseline-diff against the stock O19 defaults, manifest dispositions, docpath
values rewritten onto the CARLOS tree (both O19 layout roots), `drugref_url`
resolved to the deployment's own endpoint, deploy-owned refusal,
module-grouped dropped-key advisories and loud `unknown` reporting. The
reviewed fragment lands 0600 in the state dir and is NEVER applied
automatically; the report masks secrets and lists the imported credentials
for rotation. Dry runs produce the same fragment flagged DRY RUN. P7 verify
completes the pipeline: row-parity re-assertion, referential spot checks on
random patients across appointments/notes/drugs/preventions, and per-fiscal-
year billing totals compared to the cent — failures stop the run with state
left for diagnosis (rollback = pre-import snapshot). The full P0–P7 chain
now runs end to end; `test_props.py` (pinned against the committed
clinic-example fixture) brings the suite to 131 passing tests.

**Milestone 7 — end-to-end rehearsal + operator guide (done):**
The full turnkey flow was rehearsed on a real MariaDB 10.11 with a fixture
built exactly per this plan: `build-o19-fixture.sh` created a latin1 O19
database from the Bitbucket init scripts (MyISAM-era engine default —
`formONAREnhancedRecord` exceeds InnoDB's row limit, which is precisely why
real O19 installs hold it as MyISAM), loaded the vendored `release/demo.sql`
demo dataset + fixture document rows, and emitted the three turnkey inputs;
the CARLOS target was provisioned from the Flyway files in version order.
Both input paths ran to completion: separate flags AND the encrypted
`--bundle .tar.gz.enc` handoff. Every gate fired as designed: the planted
`ldap.enabled` produced a hard no-go (remediated like a real clinic),
`--accept` sign-offs were demanded and recorded, the ETL pre-check caught
`pharmacyInfo.uid` (curated via `value_exprs`), row parity passed for 340
copy tables with the break-glass delta itemized, the documents gate caught
the planted missing file and passed after remediation + `--resume`,
verification passed, and `--cleanup` kept `o19_archive` (113 tables + CSVs)
while dropping staging. The standalone `o19_preflight.py` ran from a bare
directory with correct exit codes.

**Rehearsal fall-out fixed and pinned by tests:** DDL parsing now applies
statements in document order, models `CREATE TABLE IF NOT EXISTS` as a
no-op (CARLOS side) and re-issued CREATEs as column UNIONS (O19 patch-soup
side), parses parenthesized multi-column `ADD (a,b)` alters, and matches
columns case-insensitively (MySQL semantics); the ETL intersects manifest
columns with the actual dump at runtime (patch-level variance reported, not
fatal); every Flyway-seeded copy-class table must reconcile its seeds (new
integrity test, after `clinic_location` collided live); the break-glass
admin's auto-ids are bumped above the clinic's range;
`measurementGroup`/`measurementGroupStyle` became merge-class (seeded via
statements the seed counter cannot count). Operator guide:
`docs/o19-import-deb.md`. Suite: 132 tests passing.

**Review round (PR #3583) — hardening from automated review, re-rehearsed
(done):** the bot findings (Copilot, Codex, cubic) that verified as real
were fixed and pinned by tests; the full bundle rehearsal was rerun on a
freshly provisioned Flyway target with the final code (verification passed,
`--cleanup` ran). What changed in behaviour:

- **Surrogate-id remap (was a data-loss bug):** every merge-class table with
  a surrogate PK now records `o19_archive.<table>__idmap` (old → new id,
  built from the natural-key join), and children curated in
  `FK_REMAP` (`LookupListItem`, `criteria_type_option`, `criteria`,
  `consultationRequests`, `serviceSpecialists`, `tickler`,
  `measurementType.validation`) read their key through the map; parents
  are ordered before children. The rehearsal showed 10 parents reassigning
  ids (e.g. 305 `measurementType` rows) with zero dangling children.
- **Unknown schema is preserved, not dropped:** staging tables the manifest
  does not know are archived whole; unmapped columns of known tables are
  shadow-captured as `<table>__unknown_cols` — so the `unknown-as-archive`
  sign-off is a real promise.
- **Resume semantics:** a rerun over recorded state REQUIRES `--resume`;
  once the ETL has started, a resumed run skips only the emptiness sweep
  (schema/replica/disk gates still run); the ETL ledger is bound to the
  staged dump's digest; seed reconciliation is resumable in two recorded
  steps (partial admin rows are cleared and re-created); a resumed chunked
  copy clears its first unconfirmed PK window before re-copying.
- **Fail-closed checks:** a preflight count that errors is a hard no-go
  (`query-errors`, no accept flag) and every unknown table is counted
  (identifiers are quoted, not filtered); a failed charset-scan query
  aborts instead of passing as clean; enum fallbacks use the introspected
  column default and are counted in the report; the row-parity gate
  tolerates exactly the break-glass admin's own rows, nothing else.
- **Pristine sweep is class-aware:** copy-class tables must hold exactly
  their counted Flyway seeds (else none); merge-class reference tables
  must hold at least them (later migrations grow them with
  `INSERT … SELECT`, invisible to a static count). The seed counter now
  strips comments between VALUES tuples (`appointment_status` was
  under-counted 7 vs 15, which would have refused every fresh deb host).
- **Input hardening:** bundle and documents archives are listed verbosely
  and any symlink/hardlink/device member, absolute or `..` name is refused
  before extraction (`--no-same-owner --no-same-permissions`); v7 tars are
  validated by header checksum; `-pass stdin`/`fd:N` work (bundle read via
  `-in`); document rows and eForm image references must resolve inside the
  restored tree; the HRM context name is validated and SQL/LIKE-escaped;
  `--admin-user` is validated and escaped; docpath translation refuses
  traversal; the properties parser follows `java.util.Properties`
  (continuations, escapes, trailing whitespace preserved) and the fragment
  re-escapes values.
- **No credentials in the repo or the manifest:** the vendored stock
  `oscar_mcmaster.properties` has every credential-bearing value replaced
  with `<redacted-in-fixture>` (noted in `PROVENANCE.md`), and the generator
  never emits such keys' defaults (`SECRET_DEFAULT_KEYS`); the props phase
  always surfaces them for review. `INCOMINGDOCUMENT_RECYCLEBIN` (boolean)
  and `mcedt.last.downloadedID.file` (bare filename) carry instead of being
  mis-translated as document paths.
- **Tooling:** the disk check measures the dump's real uncompressed size
  and includes the documents tar; the restore pipeline survives a client
  that exits early; the standalone preflight refuses an interactive `-p`
  and takes `--mysql-password-file` (MYSQL_PWD); the fixture builder
  refuses `-pSECRET` in argv; the bundle script requires exactly one dump;
  `--check` covers the preflight block and outputs carry no wall-clock
  stamp; Flyway files are parsed in numeric version order;
  `ADD COLUMN IF NOT EXISTS` parses. Suite: 201 tests passing.

**Multi-agent review round (slices + crosscuts), re-rehearsed (done):**
nine independent review passes (bundle, docs, ETL, pipeline, props and
preflight, generator and fixtures; security, fail-closed lifecycle,
docs/tests consistency) were run over the PR; every substantiated finding
was fixed and pinned by tests. What changed in behaviour:

- **Nothing in a dump can reach the live schema:** the staged restore runs
  as a throwaway account whose grants stop at `o19_import` (dropped again
  right after), with the client's `--one-database` switch; the whole dump
  stream is scanned and a `USE` / `CREATE DATABASE` (a `--databases`
  dump) or `GTID_PURGED` directive is refused before a byte is sent. GNU
  tar's option permutation is closed off (`--` before member names, and
  dash-prefixed member names refused) so an archive can no longer smuggle
  `--checkpoint-action`. The pre-import backup now runs BEFORE staging.
- **Charset repair is per row and byte-aligned** (round-trips to latin1
  unchanged, the latin1 bytes form valid UTF-8, non-ASCII present) instead
  of a hex-substring match that flagged `1,800`; the B8 marker uses the
  same predicate. The rehearsal then showed the first version was
  silently inert: the SQL regex literals reached the server with a single
  backslash (the string parser eats it, so `[^\x00-\x7F]` became
  `[^x00-x7F]`) and the BINARY round-trip compared a latin1 staging value
  against its utf8mb4 re-encoding, so every mojibake row read as clean.
  Both are fixed (the non-ASCII test is a byte-vs-character length
  comparison — a `REGEXP` class with `\x` escapes is misread by the Spencer
  engine of MySQL < 8 / MariaDB < 10.0.5, the assessment hosts; the value is
  normalised to utf8mb4 first) and proven on MariaDB 10.11 against latin1
  and utf8mb4 tables:
  `Ã‰lise CÃ´tÃ©` repairs to `Élise Côté`; legitimate accents, ASCII,
  `1,800` and CJK pass through untouched.
- **Documents:** the deb's nested skeleton (`incomingdocs/1/Fax`, …) is
  merged recursively rather than refused; `HRMDocument.reportFile` is
  rewritten to the basename inside `DOCUMENT_DIR` (the only path
  `HRMReportParser` trusts) and the `hrm/` files are moved there;
  URL-encoded `${oscar_image_path}` spellings reconcile; batch rows split
  on `\n` only (a CRLF eForm is data); ownership is re-applied on every
  pass; a different tar after a restore is a clear refusal.
- **ETL:** surrogate-id maps pair natural-key twins by `ROW_NUMBER()`
  (a `MIN()` join folded twins onto one id); a nullable child key that no
  map knows becomes NULL and is counted, a NOT NULL one keeps the raw id;
  merge anti-joins compare the sanitized expression the insert writes and
  are ordered; shadow capture prunes columns absent at this patch level;
  the ledger is bound to the dump digest AND the manifest version, and to
  the break-glass admin name; a resumed chunked copy clears its first
  unconfirmed window; `provider_no` width is checked; `encounterForm`
  merges on its PK `form_value`, `app_lookuptable` on `tableid`; the
  generator refuses stale B3/`VALUE_EXPRS` entries and non-PK merge keys
  on surrogate-less tables. The OSCAR 19 OAuth/session token tables are
  archived instead of copied; `ServiceClient`/`oscarKeys`/`publicKeys` are
  flagged for rotation.
- **Lifecycle:** `--cleanup` is allowed after verification or before the
  copy started (never on a mid-import workspace; `--dry-run` grants
  nothing) and retires `state.json`; a dry run's `--accept` flags are not
  recorded; `o19-preflight` runs the capacity checks and returns the
  verdict as its exit code without recording one; `--restage` clears the
  verdict; only a staged dump may be left behind without `--resume`; the
  P0 resume skip requires a recorded PASS; `--dev-target`/`--mariadb-arg`
  are refused on a packaged host and `--dev-target` needs the seam; the
  disk check uses the documents archive's expanded size; per-patient spot
  check lines go to root-only `verify-details.txt`.
- **Preflight/props:** exit 3 is a tool error distinct from the verdict;
  password-bearing client arguments are refused without echoing them;
  malformed `\uXXXX` escapes are errors; keys and non-Latin-1 values are
  escaped in the fragment; `eform_image` is carried as `EFORM_IMAGES_DIR`;
  `login_local_ip` carries and `resource_base_url` carries only as a plain
  http(s) URL (CARLOS's provider JSPs place it in a JavaScript string
  unencoded — a sink for a separate encoding fix), `faxLogo`/
  `oscarMeasurement_css` are dropped (no reader left). Suite: 242 tests
  passing; the bundle rehearsal was rerun on a freshly provisioned target.
- **Round 3 (cubic on the head above):** no `SUPER` fallback for the staging
  account (MariaDB < 10.5 is refused); the account and its credential file
  are revoked in a `finally`; paired identity options (`--user root`) are
  stripped from the restore argv; a non-absent-object error in the spot
  checks fails verification; a ledger with marks but no manifest version is
  refused; FK remaps are dropped for columns the dump lacks; surplus
  natural-key twins map to the target's first row; the preflight prefers
  exact-case table names, reports case-colliding twins as a blocker and keys
  column metadata by the manifest spelling; bad CLI arguments exit 3;
  non-BMP characters escape as surrogate pairs; the documents merge
  pre-checks every collision before moving anything, HRM relocation runs on
  every pass, and `HRMDocument.reportFile` containment is checked on the
  full path.
- **Round 4 (cubic on the head above):** `o19-preflight` exits 3, not 1,
  when the server is unreachable (1 is a verdict); a recorded
  `unverified-bundle` sign-off survives `--resume` without the flag or
  digest, but only for the file whose digest the ledger recorded (a
  replacement bundle needs its own digest or a fresh sign-off); eForm
  image references are reconciled on the name CARLOS's image route looks
  up — the browser drops a `#fragment`, but a `?query` stays inside the
  `imagefile` value, so `logo.png?v=2` is a broken image and is reported
  with the reason; the properties fragment is `fchmod`ed before any
  credential is written.

**M8 — roles, privileges and CARLOS-required rows (done):** the review of
the imported clinic against CARLOS's stricter privilege model found that the
clinic's role catalogue arrived without CARLOS-era grants, that clinic-custom
roles and patient lockouts were discarded with the `reference`-class role
matrix, that the break-glass admin's cloned roles were inert (`activeyn`
NULL), that a target whose webapp had booted once was refused by P0 and
would fail at startup after the import (the `OSCAR` program membership of a
deleted provider), and that case management needs `program_provider` rows.
§4.5 documents the reconciliation; the manifest is `o19map-2`. The rehearsal
fixture gained `roles.sql` (a custom role, NULL `activeyn` rows including a
dormant admin one, an expired login, a queue object, a patient lockout, a
grant on a dead object, legacy prevention codes `Flu` and `dTaP` next to the
valid `DTaP`, a removed-module property key, a clinic override of a stock
grant, an indicator template naming a removed table after a line break).
Three review rounds followed (seed floors, binary prevention compare,
crash-safe ledger, a single batch decoder, the template-binding order, the
persisted RTL plan, the seeded `eform` row in row parity, dormant admin rows
left alone, a fourth privilege-diff section).

**Verified multi-agent review round (eight slice/crosscut reports, findings
re-verified against the code, plus a CodeRabbit pass), re-rehearsed (done):**

- **Identifiers from the dump are never trusted:** every table or column
  name the staged dump chooses (unknown tables, vendor-fork columns, the
  archive tables derived from them) is backtick-quoted with doubled
  backticks, and the ETL pre-checks refuse any staged name outside
  `[A-Za-z0-9_$]` before the first write — every statement runs as the
  database root, so a crafted name must stay a name. The archive CSV export
  applies the same class to file names and tells SQL NULL from a stored
  `NULL` string by a companion flag (the batch client prints both alike).
- **Preflight B4 is judged on the data:** `casemgmt.note.password.enabled=
  true` is the stock O19 default; only `casemgmt_note` rows carrying a
  password are the blocker, the property alone is an advisory. The
  non-ASCII test is a byte-vs-character length comparison (the Spencer
  regex engine of MySQL < 8 misreads a `\x` class), the report is written as
  UTF-8 bytes after the JSON so a C-locale Python 3.4 cannot lose it, and a
  `--accept` typo is refused rather than recorded.
- **Resume safety:** the Rich Text Letter v1 seed (a bare INSERT) is never
  replayed once a canonical row exists, P7 fails on more than one canonical
  row and row parity bounds the synthesised `eform`/`program` rows; the
  documents restore records `restoring` before the first move and an
  interrupted merge completes on `--resume` (identical files verified, not
  replaced); `--skip-documents` cannot retire a restored tree; a dry run or
  assessment refuses a workspace whose import is in progress instead of
  rewriting its inputs and bundle; a corrupt ledger is fatal, never "fresh";
  cleanup marks itself, retires the ETL ledger and the properties fragment
  with the run, and the break-glass credentials file is set aside rather
  than overwritten by a later import.
- **Roles step:** the CARLOS-only-role listing counts the NULL rows the
  import activates; the activeyn report no longer double-counts dormant
  admin rows; the seed's `-1` system pseudo-provider gets no membership,
  link or review entry; duplicate logins count once; an RTL-titled clinic
  copy without the `RptByExample.do` sink or a dead route is reported, not
  disabled; a custom role resembling `admin` below similarity 0.5 is held
  for an explicit `--role-template`; form names stay out of the report.
- **HRM and eForm assets as CARLOS reads them:** the whole `hrm/` tree
  (O19 nests reports under `sftp_downloads/<date>/decrypted/`) is walked,
  identical duplicates folded, differing copies of one name refused, and two
  `HRMDocument` rows reaching one basename through different paths are
  refused before the rewrite; eForm image references are decoded the way
  the image route receives them (whole attribute value, entities and
  percent-encoding, a second query parameter cut) and a subdirectory
  reference is a blocking problem.
- **Orchestration:** the preflight verb exits 3 on every tool failure
  (never a verdict code); credential literals are masked in every SQL error
  text and a half-created staging account is dropped; `--province` may only
  restate the packaged host's configured province; a running `carlos-emr`
  service is refused for a real run; a leftover `o19_archive` of a previous
  import is refused on a fresh run; the manifest-mismatch message offers the
  package-downgrade path; sign-off hints carry `--resume` when needed.
- **Properties parsing** ends lines only at `\n`, `\r`, `\r\n`, strips only
  space, tab and form feed, keeps a record whose last line ends in a
  continuation backslash and joins UTF-16 escape pairs — in the props
  phase and the standalone preflight alike.
- **Follow-ups folded in rather than deferred:** every stock O19 property
  key now has a disposition (the test suite pins zero `unknown` stock
  keys); merge tables get a reverse parity check (every staging row has a
  target twin, excluded removed-module rows aside); a text-in-O19 /
  number-in-CARLOS column is pre-checked so no value is coerced to 0; the
  per-table report lines (id maps, dangling keys, shadows, unknown tables)
  are kept in the ETL ledger so a resumed run's report is complete; a
  resume after the copy started does not re-run the preflight;
  `--statement-timeout` bounds every statement (`max_statement_time`);
  carrying live credentials (`ServiceClient`, `oscarKeys`, `publicKeys`)
  is the `carry-credentials` sign-off — preflight blocker B6 and an ETL
  pre-check refusal without it.

**All milestones complete.** Next steps beyond this round: run the
Playwright UI suite against a migrated database under a full app deploy,
the BC manifest pass (§10.6), and the carlos-podman `import-o19` catch-up.

## 10. Implementation work breakdown

1. `carlos`: commit generated schema-diff tooling + `o19-schema-map.yaml` seed
   (from this analysis), engine skeleton with `preflight`+`stage`; ship
   `preflight` as a standalone assessment-mode script (§6.1) usable at a clinic
   before backups are shipped.
2. `carlos`: ETL phase for the top-20 core tables + seed reconciliation +
   forced-reset; verification row-parity report.
3. `carlos`: documents phase + reconciliation; archive/CSV export phase;
   `props` phase + `o19-properties-map.yaml` seed (§8.1).
4. `carlos-podman`: `carlos-ctl import-o19` wrapper (snapshot, phases, restart,
   report), `--dry-run`.
5. End-to-end rehearsal against a seeded O19 test database built from the
   Bitbucket init scripts + demo data; then against a real anonymized clinic dump.
6. BC manifest variant.

---

## Appendix A — O19-only tables (Ontario superset, absent in CARLOS)

Integrator*, sharing_*, phr_*/indivoDocs, BORN*, Ocan*, Eyeform*/eyeform*,
intake* (generic intake), hsfo_*, cr_* (cookie revolver), report_* (report
runner), CAISI facility/bed/room (`bed*`, `room_*`, `vacancy`, `complaint`,
`incident`, `agency`-adjacent `lst_*` CAISI lookups), OLIS*, MDS raw (mdsZCL,
mdsZCT), forms dropped by CARLOS (formAR, formONAR, formONAREnhancedRecord+Ext1/2,
formType2Diabetes, formAdf, formIntakeHx, formBCAR2007, formfollowup,
formovulation, form_hsfo_visit), plus assorted temp/dead tables (icd10_temp,
log_temp, measurementTypeTEMP, secUserRole_tmp, tmpdiagnosticcode, test,
survey_test_*, recycle_bin, uploadfile_from, oscar_annotations, onCallClinicDates,
oncall_questionnaire, queue_provider_link, scheduledaytemplate, demographicSite,
secSite, doc_category, doc_manager, RedirectLink*, RemoteDataRetrievalLog,
DrugDispensing*, ProductLocation, ContactType/EncounterType/ProgramContactType/
ProgramEncounterType, MyGroupProgram, IntakeRequiredFields, functionalCentreAdmission,
caisi_editor, caisi_form_instance_tmpsave, caisi_form_question, group_note_link,
bed_check_time, bed_demographic_*, preventionsBilling, resident_oscarMsg,
ocularprocedurehis, specshis, procedurebook, testbookrecord, formONAREnhanced,
formONAREnhancedRecordExt1/2 — full machine list regenerated by the diff tool).

## Appendix B — CARLOS-only tables (no import needed)

CVC* (vaccine catalog), DHIRSubmissionLog, DocumentExtraReviewer, EFormDocs,
ISO36612, LookupCodeValue, PreventionReport, ServiceOAuthNonce, SystemPreferences,
billing_preferences, consultationRequestExtArchive, consultationRequestsArchive,
documentDescriptionTemplate, document_review, dxCodeTranslations, dxphcpgroup,
emailAttachment, emailConfig, emailLog, erefer_attachment(+_data),
formRourke2017, formRourke2020, form_boolean_value, icd9 (restructured),
incomingLabRulesType, rbt_groups.

## Appendix C — shared tables where O19 carries columns CARLOS dropped (39)

CdsClientForm, Contact, DemographicContact, EFormReportTool, Facility,
FunctionalCentre, ProviderPreference, agency, app_lookuptable_fields, demographic,
demographicArchive, document, drugs, eChart, favorites, formDischargeSummary,
formLabReq07, formRourke2009, icd10, issue, lst_admission_status,
lst_discharge_reason, lst_field_category, lst_gender, lst_organization,
lst_program_type, lst_sector, lst_service_restriction, professionalSpecialists,
program, program_provider, property, provider_facility, reportTemplates,
secObjectName, secRole, secUserRole, security, vacancy.
