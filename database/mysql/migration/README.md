# CARLOS Flyway migrations (`database/mysql/migration/`)

The **single source of truth** for the CARLOS `oscar` schema. A fresh `flyway migrate` produces a
complete, working database — schema **and** required reference data — with no legacy scripts in the
loop. Dead tables from removed modules are pruned (see `pruned-tables.txt`).

## Layout (Flyway-native: shared + per-province locations)

```text
migration/
  flyway.conf                     # non-secret defaults (locations, baseline); creds passed at run time
  pruned-tables.txt               # dead tables excluded from the baseline (removed-module cruft)
  common/  V1__baseline_schema.sql          # province-neutral tables (structure)
           V1.0.3__performance_indexes.sql  # forward delta: shared performance indexes
           V1.0.5__restore_live_legacy_common_tables.sql
           V1.0.7__restore_phcp_diagnosis_groups.sql
           V1.0.8__expand_appointment_type_location.sql
           V1.0.9__remove_carlosdoc_schedule_group_denial.sql
           V1.0.10__seed_default_measurement_groups.sql
           V1.0.13__fix_phcp_diagnosis_group_backfill_collation.sql
           V1.0.14__widen_faxes_jobid_to_bigint.sql
           V1.0.15__add_faxes_direction.sql
           V1.0.16__add_faxes_jobid_index.sql
           V1.0.17__enable_digital_signatures_by_default.sql
           V1.0.18__performance_indexes_2.sql  # forward delta: second DAO-justified index pass
           V1.0.20__widen_fax_destination_for_international_numbers.sql
           V1.0.21__serialize_missing_lab_routing_creation.sql
           V1.0.22__add_lab_routing_lock_audit_columns.sql
           V1.0.23.1__enforce_provider_signature_identity.sql
  on/      V1.0.1__on_schema.sql            # Ontario-only tables (structure)
           V1.0.2__on_data.sql              # Ontario reference data (rows)
           V1.0.4__on_performance_indexes.sql
           V1.0.6__restore_reporting_privilege.sql
           V1.0.11__billing_filename_unique_indexes.sql
           V1.0.12__portable_billing_filename_unique_indexes.sql
           V1.0.23__activate_legacy_consultation_services.sql
  bc/      V1.0.1__bc_schema.sql            # British Columbia-only tables (structure)
           V1.0.2__bc_data.sql              # British Columbia reference data (rows)
           V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql
           V1.0.19__bc_billingmaster_indexes.sql
```

The **genesis baseline** is `V1` + the province `V1.0.1`/`V1.0.2` files (frozen). Everything from
`V1.0.3` onward is a forward delta. The release/2026.08 high-water mark is
`common/V1.0.23.1`. This maintenance fix follows Ontario's `V1.0.23` without taking
`V1.0.24`–`V1.0.28`, already allocated on develop. Check both active branch inventories
before allocating another version; the next unused integer at this revision is `V1.0.29`.
Do not use that integer on this maintenance line without planning the subsequent upgrade:
a release database must still be able to apply develop's intervening migrations.

The version line is global across `common` + one province. Flyway with `outOfOrder=false`
cannot apply a newly introduced version below a database's installed high-water mark.
When forwarding this maintenance fix to a develop database already past `1.0.23.1`,
plan and validate that upgrade explicitly; a clean-install check alone is insufficient.
Do not silently enable out-of-order migration or renumber any published migration.
See [the release process](../../../docs/release-process.md) for branch promotion rules.

### Provider signature identity repair (1.0.23.1)

Stop all application nodes and take a database backup before upgrade. The migration
copies signatures into a temporary table with a unique provider key before changing
`providerExt`. Exact byte-for-byte duplicates collapse; distinct text, including case
or a NULL versus text value, produces a duplicate-key failure with the source untouched.
Confirm the intended text with the affected provider and resolve the conflicting rows
from the backup; do not pick an arbitrary signature. For the DEB deployment, inspect
`sudo carlos-ctl db-info` and the migration error. Once the data conflict is resolved and
all published migration files are unchanged, run `sudo carlos-ctl db-repair`, then
`sudo carlos-ctl db-migrate` and `sudo carlos-ctl db-validate`. Do not use repair to accept
a checksum mismatch or conceal an unrelated migration failure. Successful application preserves signature text
and enforces one row per non-NULL provider identifier, matching the Hibernate entity ID.
Legacy unassigned NULL-provider rows are preserved.

The executable isolated-database regression is
`python3 scripts/test-provider-signature-migration.py` from the repository root, using a
disposable local MariaDB/MySQL server and a CREATE/DROP DATABASE-capable account. The
client reads its usual option file or `MYSQL_PWD`; `MYSQL_HOST` must be local and
`MYSQL_USER` defaults to root. The test removes only its randomly named databases.
Both province CI jobs run it before their full Flyway migration/upgrade checks.

A database applies **`common` + exactly one province** location, selected by `flyway.locations`:

| Target | locations |
|---|---|
| Ontario | `common, on` |
| British Columbia | `common, bc` |

Versions order globally across the selected locations: `V1` (common schema) → `V1.0.1` (province
schema) → `V1.0.2` (province data). Because a run only ever combines `common` + one province, the two
provinces' `V1.0.x` files never collide. The province data file carries the full reference rows
(shared + province). Later forward migrations restore live lookup/reference tables, ICD-10 data,
performance indexes, and corrected reporting grants that were missing from the first generated
baseline.

Demo/patient data is **not** in this baseline — it belongs in a dev-only `demo` location (see
`docs/database-schema-management.md`).

## Conventions

- **New schema changes** are Flyway migrations named `V1.0.N__short_description.sql` (sequential, next free number) in
  `common/` (shared) or `on/`/`bc/` (province-specific). Never edit any versioned migration after it
  ships in a release tag: Flyway records its checksum, so corrections must use a new forward migration.
  Never add to `../updates/` (frozen — see `../updates/README.md`).
- **The `V1` baseline is COMPLETE and frozen** (schema + required reference data): it is the genesis
  of the CARLOS schema. It was captured once (demo-free, dead-pruned per `pruned-tables.txt`) at the
  Flyway cutover; the legacy `createdatabase_*.sh` / `oscarinit*` / `oscardata*` build it replaced has
  been retired (recoverable from git history). Do not regenerate it — evolve the schema forward.
- **drugref2 is a separate database** — not managed here (keeps `../development-drugref.sql` + `../drugref/*.sql`).

## MariaDB CLI recovery for V1.0.7

Flyway/JDBC runs negotiate a compatible connection collation, and the development bootstrap pins
one before applying forward migrations. A manual `mysql`/`mariadb` run through `carlos-ctl db`,
however, may use `utf8mb4_uca1400_ai_ci` on MariaDB 11.4 or newer. In that session V1.0.7 can stop
with `ERROR 1267 (Illegal mix of collations)` after its DDL and before its guarded backfill inserts.

If that happens, rerun V1.0.7 with the compatible collation established in the **same client
session**:

```bash
{
  printf '%s\n' 'SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;'
  cat common/V1.0.7__restore_phcp_diagnosis_groups.sql
} | sudo EMR_HOME=/usr/local/emr carlos-ctl db oscar
```

Run this from `database/mysql/migration/`, then continue with V1.0.8 through V1.0.13 in global
version order. V1.0.7 is safe to rerun: its DDL is repeatable and both inserts exclude rows already
present. A separate `SET NAMES` invocation does **not** work because the setting ends with that
client process. Do not add `--force`; continuing after an unrelated SQL error could leave the
schema in an unknown state.

V1.0.13 is an idempotent no-op after the pinned V1.0.7 rerun. It also fills the missing rows when
an operator previously bypassed the V1.0.7 error and continued directly to later migrations.
Automatic protection in the deployment CLI is tracked in
[carlos-podman #17](https://github.com/carlos-emr/carlos-podman/issues/17).

## Evolving the schema

Add a forward migration under the right location — `common/` for shared changes, `on/`/`bc/` for
province-specific ones — named `V1.0.N__short_description.sql` (next free number), and make it idempotent. A
fresh `flyway migrate` applies `V1` then your delta; existing databases apply only the new delta.
See `docs/database-schema-management.md` for the model and CI verification (`db-schema-verify.yml`).
