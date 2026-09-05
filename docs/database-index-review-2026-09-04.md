# Database index review (2026-09-04)

A review of the state of the CARLOS `oscar` schema's indexes against the query patterns the
application actually issues, with the resulting changes shipped as two forward migrations:

- `database/mysql/migration/common/V1.0.18__performance_indexes_2.sql`
- `database/mysql/migration/bc/V1.0.19__bc_billingmaster_indexes.sql`

Target engine: MariaDB 11.8.x (what dev, CI `db-schema-verify.yml`, and the container/podman
production stack run — see `database-schema-management.md`).

## TL;DR

The schema was already in good shape. `common/V1.0.3__performance_indexes.sql` (plus
`on/V1.0.4`, `bc/V1.0.6`, `on/V1.0.11`/`V1.0.12`, `common/V1.0.16`) had done a DAO-justified index
pass with the right engineering discipline: composite-first design, documented redundancy drops,
idempotent MariaDB DDL, and a guard-before-drop ordering that keeps every queried column indexed
even if a migration dies mid-way. This review is therefore a **delta**: it cross-checked all 389
baseline tables and every forward migration against the hot DAO predicates and found a short list
of real gaps (tables that are still PK-only despite being queried by a non-PK column), a few
left-over redundant indexes, and a set of query shapes that no index can help.

## Method

1. Inventoried every `PRIMARY KEY` / `KEY` / `UNIQUE KEY` / `CREATE INDEX` / `DROP INDEX` in
   `common/V1__baseline_schema.sql`, `on/V1.0.1__on_schema.sql`, `bc/V1.0.1__bc_schema.sql` and all
   `V1.0.N` forward migrations.
2. Mined the WHERE / JOIN / ORDER BY column sets from the hottest DAOs (appointments, demographic
   search, clinical notes, prescriptions, labs, documents, ticklers, measurements, ON and BC
   billing, audit log, messaging). DAOs are ~9:1 JPQL to native SQL, so entity property names were
   mapped back to real column names through the `@Column` mappings before comparing to the DDL.
3. Verified every proposed table/column/index name directly against the DDL, checked that no
   proposed index name exists anywhere under `database/`, and confirmed no `FOREIGN KEY` depends on
   any index being dropped.

## Current state (before this change)

| Metric | Value |
|---|---|
| Tables in the common baseline | 389 (ON adds 21, BC adds 8) |
| Storage engine / charset | 100% InnoDB, `utf8mb4` / `utf8mb4_general_ci` (one accepted legacy Aria table in ON forms) |
| Tables with at least one secondary index | 130 |
| Tables with a PK only | 250 |
| Tables with no key at all | 9 (all small config/link tables — `providerExt` is the one that matters, see below) |
| Secondary `KEY` lines in the baseline | 262, of which 179 (68%) are composites |
| `UNIQUE` indexes in the baseline | 16 (business uniqueness is mostly enforced in Java) |
| FULLTEXT / SPATIAL indexes | none |

The dominant pattern is "auto-increment surrogate PK, secondary indexes only on the ~130 hot
tables inherited from OSCAR", with the `*_ikey` / `*_integrator` names marking indexes added for
the Integrator sync paths. The coverage on the truly hot tables after V1.0.3 is good: appointment
(provider day sheet and patient history), tickler (best-covered domain in the schema), drugs,
measurements, casemgmt_note, patientLabRouting/providerLabRouting and ON billing all have an index
matching their dominant DAO shape.

## Changes made

### `common/V1.0.18__performance_indexes_2.sql`

New indexes (each justified by an actual DAO predicate):

| Index | Table (columns) | Justification |
|---|---|---|
| `idx_demographicArchive_demographic_no` | `demographicArchive` (`demographic_no`) | `DemographicArchiveDaoImpl` lines 57, 73, 112, 126 — all four queries filter `demographicNo`; runs on every demographic edit/history view. Table was PK-only, so each call scanned the whole ever-growing archive. |
| `idx_demographic_chart_no` | `demographic` (`chart_no`) | `chart_no LIKE` searches (`DemographicDaoImpl` 1377, 1887, 1996, and the wildcard branch of the main search at 2774/2813) plus the chart-number sort mode (2861). The main search's default operator is `REGEXP`, which no B-tree index can serve (see "Query shapes"). |
| `idx_providerExt_provider_no` | `providerExt` (`provider_no`) | Table has no PK and no index; the `ProviderExt` entity maps `provider_no` as `@Id` and `providerExtDao.find()` runs on signature renders (`CaseManagementManagerImpl:2054`, `ProSignatureData`). |
| `idx_remoteAttachments_demo_message` | `remoteAttachments` (`demographic_no`, `messageid`) | `RemoteAttachmentsDaoImpl` 51, 58 — `demographicNo [+ messageId]`; PK-only table. |
| `idx_billing_provider_date` | `billing` (`provider_no`, `billing_date`) | `BillingDaoImpl` 525, 563, 582, 601 — `provider_no + billing_date BETWEEN ... ORDER BY billing_date`. The table had eight single-column indexes but no composite for this range shape. |
| `idx_casemgmt_note_provider_observation` | `casemgmt_note` (`provider_no`, `observation_date`) | Note-count reports `CaseManagementNoteDAOImpl` 585, 621 — `provider_no AND observation_date BETWEEN`. |
| `idx_messagelisttbl_provider_status` | `messagelisttbl` (`provider_no`, `status`) | Messenger inbox / unread badge `MessageListDaoImpl` 111, 119 — `providerNo + status`. |
| `idx_hl7TextInfo_obr_date` | `hl7TextInfo` (`obr_date`) | Lab inbox date-range predicates and `ORDER BY info.obr_date DESC` with offset pagination (`Hl7TextInfoDaoImpl` inbox builder, ~320–500). The column is `varchar(20)` holding HL7 timestamps; they sort lexically so the index is valid (see hygiene note). |
| `idx_document_observationdate` | `document` (`observationdate`) | Patient document list `ORDER BY observationdate DESC` (`DocumentDaoImpl:527`) and the per-provider `observationdate BETWEEN` report (`:206`). |

Redundant indexes dropped (each is a strict left-prefix of a composite created earlier in the same
file, or an exact duplicate of the PK, so the drop is read-equivalent and saves write
amplification on every INSERT/UPDATE):

| Dropped | Why |
|---|---|
| `billing`.`provider_no` | left-prefix of `idx_billing_provider_date` |
| `casemgmt_note`.`FKA8D537806CCA0FC` | Hibernate-named `(provider_no)` index; left-prefix of `idx_casemgmt_note_provider_observation`. `casemgmt_note` declares no `FOREIGN KEY` on `provider_no`, so no constraint depends on it. |
| `messagelisttbl`.`provider_no` | left-prefix of `idx_messagelisttbl_provider_status` |
| `eform`.`id` (UNIQUE) | exact duplicate of the PK `fid` — the sibling of the `eform_data.id` drop V1.0.3 already made |

### `bc/V1.0.19__bc_billingmaster_indexes.sql`

`billingmaster` shipped with only its PK and `wcb_id` indexed, yet the BC billing DAO joins and
filters it by other columns on every reconcile / claim lookup:

| Index | Justification |
|---|---|
| `idx_billingmaster_billing_no_status` (`billing_no`, `billingstatus`) | `BillingmasterDAO` 73, 85, 182, 190 (WCB), 202, 298 and the `BillingDaoImpl` reconcile join `b.billing_no = bm.billing_no [AND bm.billingstatus ...]` |
| `idx_billingmaster_demographic_no` (`demographic_no`) | `BillingmasterDAO` 309 (`demographicNo + billingCode + billingstatus NOT IN`), 333 (`demographicNo ORDER BY billingmaster_no DESC`) |

No Ontario migration was needed: `billing_on_cheader1` / `billing_on_item` query shapes are covered
by the V1.0.1 keys plus `on/V1.0.4` (`billing_on_item` has no demographic column; all its DAO
queries are `ch1_id`-based).

### Numbering

The version line is global across `common` + the applied province and must never go at or below
the high-water mark (`migration/README.md`). On the `release/2026.08` line the previous high-water
mark is `common/V1.0.17` (`V1.0.17__enable_digital_signatures_by_default.sql`, published in
`2026.08.0-alpha9` and therefore checksum-frozen), so this change takes `common/V1.0.18` and
`bc/V1.0.19`; the next free number for **any** location is now `V1.0.20`.
`database/mysql/migration/README.md` and `common/README.md` (the migration registry and high-water
mark) were updated in the same change. When this line is forward-merged into `develop`, these
numbers must stay as they are: `develop` has no V1.0.17 yet, and the release policy says only an
unreleased migration on the newer line may be renumbered.

## Evaluated and rejected

Candidates that looked like gaps but are not, recorded so the next reviewer does not redo the work:

| Candidate | Verdict |
|---|---|
| `measurementsExt (keyval, val)` | Already indexed: `measurements_ext_keyval_val (keyval, val(100))` — a prefix index on the `TEXT` column; the DAO's `keyVal = ? AND val = ?` lookups use it. |
| `demographicExt (demographic_no, key_val, date_time)` covering index for the four correlated `max(date_time)` subqueries in `OscarAppointmentDaoImpl:900-906` | `uk_demo_ext` is UNIQUE on `(demographic_no, key_val)`, so each subquery is a single-row lookup already; a covering index would only save one row fetch per key. |
| Dropping `demographicExt`.`demographic_no` (left-prefix of `uk_demo_ext`) | Technically redundant, but the guard-before-drop rule would require creating a UNIQUE index first, which can fail on a converted datadir with duplicate rows. Recommend-only; drop it once `uk_demo_ext` is confirmed present on every managed database. |
| `dxresearch_integrator (demographic_no, update_date)` vs `dxresearch_ikey (demographic_no, status, update_date)` | Not redundant — the second column differs; the integrator index serves `demographic_no + update_date >` range scans that the `_ikey` cannot. |
| `patientLabRouting`.`lab_no_index` | Not a prefix of `all_index (lab_type, lab_no, demographic_no)`; legitimately kept. |
| `messagetbl`.`date` / `type` (`MsgDemoMapDaoImpl:82,90` ORDER BY) | Per-patient message threads are small result sets; the sort cost is negligible. |
| `billinglocation`, `billingvisit`, `specialty`, `custom_filter_*`, `ProviderPreferenceAppointmentScreen*`, `InstitutionDepartment`, `serviceSpecialists` (no keys at all) | Tiny configuration / link tables; a full scan is cheaper than index maintenance. |
| Anything on the `log` audit table | `log` already has `(dateTime, provider_no)`, `(provider_no, dateTime)`, `demographic_no`, `action`, `content`, `contentId`. `OscarLogDaoImpl:250` carries a `FORCE INDEX (datetime)` hint precisely because the optimizer once mis-chose a provider-leading index on large audit tables — the table is deliberately left untouched. |
| Descending indexes (MariaDB 10.8+) | Evaluated for every `ORDER BY … DESC` path. All hot DESC sorts are uniform-direction (e.g. `billingDate DESC, billingTime DESC, id DESC`), which MariaDB serves with a backward index scan; DESC indexes only pay off for mixed-direction composites, which no CARLOS query uses. |
| `ALGORITHM=INPLACE, LOCK=NONE` clauses | Every statement is an InnoDB secondary-index add/drop, which MariaDB already executes INPLACE; the clauses were omitted to stay in the established V1.0.3 idiom (migrations run in operator-gated upgrade windows). |

## Query shapes that indexes cannot fix

These need query changes, not indexes, and are listed for follow-up issues (no Java was changed in
this review):

| Location | Shape | Why it defeats indexing |
|---|---|---|
| `DemographicDaoImpl:2771-2774, 2812-2833` | Main patient search uses `REGEXP` unless the keyword contains `*` / `%`, and wraps names in `lower(d.last_name)` / `lower(d.first_name)` | `REGEXP` and a function on the column both force a scan of `demographic`, so the `name(last_name, first_name)` index is bypassed on the most common search. `utf8mb4_general_ci` is already case-insensitive, so the `lower()` is unnecessary; a prefix `LIKE` would use the index. |
| `DemographicDaoImpl:628, 677` | DOB search as three separate `varchar` `LIKE`s (`year_of_birth`, `month_of_birth`, `date_of_birth`) | No single index can serve three independent LIKE predicates. |
| `DemographicDaoImpl:773` | `(d.phone LIKE ? OR d.phone2 LIKE ?)` | OR across two columns; would need two indexes and an index-merge, and the LIKE is typically leading-wildcard. |
| `BillingDaoImpl:312, 318` | `to_days(service_date) >= to_days(?)` | Function on the column; rewrite as `service_date >= ?`. |
| `OscarAppointmentDaoImpl:861` | `BINARY status NOT LIKE 'B%'` | `BINARY` cast makes the predicate non-sargable. |
| `DocumentDaoImpl:461, 495` | `d.status NOT LIKE 'D'`, `c.module LIKE 'demographic'` on constants | Harmless but should be `=` / `<>`. |
| `Hl7TextInfoDaoImpl` inbox (~320–500) | `LIMIT page*pageSize, pageSize` over a joined, filtered result ordered by `obr_date` / `created` | Offset pagination re-sorts and discards rows on every page; keyset pagination on `(obr_date, lab_no)` would make deep pages O(page) instead of O(offset). |
| Text search generally | `LIKE '%…%'` on notes, document descriptions, drug names | Leading-wildcard LIKE cannot use a B-tree. InnoDB FULLTEXT indexes are available in MariaDB 11.8 if the team ever wants indexed text search; it requires query changes (`MATCH … AGAINST`). |

## MariaDB 11.8 features considered

- **Online DDL** — used implicitly: all adds/drops are InnoDB secondary-index operations (INPLACE,
  no table copy). The migration headers state this so operators can size the upgrade window.
- **`IGNORED` indexes** (`ALTER TABLE … ALTER INDEX … IGNORED`, MariaDB 10.6+) — not needed here
  because every drop is provably plan-equivalent (strict left-prefix or exact duplicate). It is the
  right tool for any *future* drop where equivalence is not provable: mark the index `IGNORED`,
  watch the slow log for a release, then drop. The `log.datetime` `FORCE INDEX` incident is the
  cautionary example.
- **Descending indexes** — evaluated, not needed (see the rejected table).
- **InnoDB FULLTEXT** — available; noted above as the engine-level option for text search.

## Hygiene findings (not changed here)

| Finding | Recommendation |
|---|---|
| `providerExt` has no PRIMARY KEY (`provider_no` is nullable) while the entity maps it as `@Id` | A plain index was added now. Add `PRIMARY KEY (provider_no)` in a later migration after a data-quality check for NULL/duplicate rows on converted datadirs. |
| `hl7TextInfo.obr_date` is `varchar(20)` | The index works because HL7 timestamps sort lexically, but a typed `DATETIME` column would make range predicates robust and cheaper. Entity + DAO change plus a backfill. |
| `DemographicDaoImpl` search wraps columns in `lower()` | Redundant under `utf8mb4_general_ci`; removing it (and preferring prefix `LIKE` over `REGEXP` for plain keywords) is the single biggest win available for patient search. |
| `demographicExt`.`demographic_no` shadows `uk_demo_ext` | Drop once `uk_demo_ext` is confirmed present on all managed databases (see rejected table). |
| `CREATE INDEX IF NOT EXISTS` no-ops on a same-name, different-column index | Shared caveat of the whole idiom. All names in V1.0.18/V1.0.19 are new and repo-unique; after migrating a converted datadir, spot-check `SHOW INDEX` for the tables above. |

## Sibling repositories

- **drugref2026** rebuilds its `cd_*` tables on every DPD import (`DPDImport.java`) and indexes
  `drug_code` on all of them plus the `cd_drug_search` columns — adequately indexed for its access
  pattern. Its `LIKE '%…%'` name searches are the same B-tree limitation noted above.
- **carlos-podman** carries no schema.

## Verification performed

Run locally on 2026-09-04, mirroring `db-schema-verify.yml` (same image, server config and Flyway
CLI version):

1. `mariadb:11.8.8` started with the CARLOS `my.cnf` (`innodb_page_size` asserted = 32768).
2. Flyway CLI 11.14.0 (SHA-256 pinned as in CI) `migrate` + `validate` with
   `filesystem:migration/common,filesystem:migration/<province>` for both provinces:
   Ontario — 19 migrations applied, schema at v1.0.18, validated; British Columbia — 17 migrations
   applied, schema at v1.0.19, validated.
3. `information_schema.statistics` assertions in both databases: all 9 common indexes (and both
   `billingmaster` indexes in BC) present with the intended column order; `billing.provider_no`,
   `casemgmt_note.FKA8D537806CCA0FC`, `messagelisttbl.provider_no` and `eform.id` absent; every
   PRIMARY KEY and every other pre-existing index on the touched tables intact.
4. Idempotency: V1.0.18 re-applied to both databases and V1.0.19 re-applied to BC through the
   `mariadb` client — all three runs completed with exit status 0. With `--show-warnings` the only
   output is `Note 1061 Duplicate key name` / `Note 1091 Can't DROP INDEX` for every statement,
   i.e. the `IF NOT EXISTS` / `IF EXISTS` no-op path; index counts were unchanged.
5. `EXPLAIN` spot-checks: `demographicArchive WHERE demographic_no`, `billing WHERE provider_no AND
   billing_date BETWEEN`, `messagelisttbl WHERE provider_no AND status`, `billingmaster WHERE
   billing_no AND billingstatus`, `providerExt WHERE provider_no` and `demographic WHERE chart_no
   LIKE 'prefix%'` each chose the new index (`key:` column). On empty tables this proves the index
   is selectable for the shape; production plan choice depends on statistics.

The `db-schema-verify` workflow re-runs `flyway migrate` + `validate` for both provinces on every
`database/mysql/**` change and is the authoritative gate.
