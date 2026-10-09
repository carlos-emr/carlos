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
           V1.0.23.1__widen_email_config.sql
           V1.0.29__rename_placeholder_demo_clinic.sql
           V1.0.30__add_ocean_setting.sql
           V1.0.32__add_nrtf_tuning_fork_measurement_type.sql
           V1.0.33__aacp_provided_revised_reviewed_validation.sql
           V1.0.36__serialize_messenger_membership_changes.sql
           V1.0.39__track_automatic_mrp_routing.sql
           V1.0.40__widen_lab_labels.sql
           V1.0.41__rtl_provider_stamp_fields.sql
           V1.0.42__tickler_docs.sql
           V1.0.43__consultation_eform_lab_sources.sql
           V1.0.45__consultation_request_indexes.sql
           V1.0.46__field_note_report_privilege.sql
           V1.0.47__align_measurements_deleted_columns.sql
           V1.0.52__enforce_provider_signature_identity.sql
           V1.0.53__decode_diagnostic_description_apostrophes.sql
           V1.0.54__allow_dashboard_exclusion_history.sql
           V1.0.55__preserve_remittance_health_number_version.sql
           # Develop-only migrations, in develop's order, above the release line's V1.0.56. All were
           # renumbered at the 2026.08.0-alpha19 forward merge; "was" names develop's earlier number.
           V1.0.66__add_email_consent_audit.sql  # was V1.0.24
           V1.0.67__add_sms_system_of_record.sql  # was V1.0.25
           V1.0.68__widen_email_config_for_encrypted_credentials.sql  # was V1.0.26
           V1.0.69__prepare_outbound_email_archive_reference_engines.sql  # was V1.0.27
           V1.0.70__outbound_email_archive.sql  # was V1.0.28
           V1.0.71__add_sms_security_objects.sql  # was V1.0.31
           V1.0.72__add_sms_consent.sql  # was V1.0.32
           V1.0.73__patient_portal_security_objects.sql  # was V1.0.41
           V1.0.74__portal_email_delivery.sql  # was V1.0.42
           V1.0.75__patient_portal_invite_delivery.sql  # was V1.0.43 (#3856), then V1.0.58 (#4440)
           V1.0.76__activate_sms_consent.sql  # was V1.0.54
           V1.0.77__one_live_consent_per_type.sql  # was V1.0.33 (#3917), then V1.0.57 (#4382)
           V1.0.78__add_sms_config.sql  # was V1.0.59 (#3927)
  on/      V1.0.1__on_schema.sql            # Ontario-only tables (structure)
           V1.0.2__on_data.sql              # Ontario reference data (rows)
           V1.0.4__on_performance_indexes.sql
           V1.0.6__restore_reporting_privilege.sql
           V1.0.11__billing_filename_unique_indexes.sql
           V1.0.12__portable_billing_filename_unique_indexes.sql
           V1.0.23__activate_legacy_consultation_services.sql
           V1.0.34__add_oma_uninsured_service_fees.sql
           V1.0.56__preserve_claim_item_explanations.sql
  bc/      V1.0.1__bc_schema.sql            # British Columbia-only tables (structure)
           V1.0.2__bc_data.sql              # British Columbia reference data (rows)
           V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql
           V1.0.19__bc_billingmaster_indexes.sql
```

The **genesis baseline** is `V1` + the province `V1.0.1`/`V1.0.2` files (frozen). Everything from
`V1.0.3` onward is a forward delta. The highest version in use is `V1.0.78` (`common/V1.0.78`,
shared by both provinces), so the next free number for ANY location — shared or province — is
`V1.0.79`. Open draft pull requests #4136 and #4487 were numbered `V1.0.60` and `V1.0.61` before
this line reached `V1.0.78`; they must be renumbered above the high-water mark when they merge.

`release/2026.08` and this line allocated versions independently. The 2026.08.0-alpha19 forward merge
brought the release line's migrations (up to `on/V1.0.56`) here. The release files are published in
a tag, so their numbers and checksums are frozen. Every migration that existed only on this line was
renumbered to `V1.0.66`–`V1.0.78`, in this line's order (see the listing above). Two reasons:

- Some of this line's numbers had been used by the release line for different files.
- A database upgraded through `release/2026.08` has run `V1.0.56`, and Flyway (no `outOfOrder`)
  would never apply this line's `V1.0.24`–`V1.0.28` and `V1.0.31` there. Startup validation rejects
  such ignored migrations, so the upgrade would refuse to start.

Renumbering only the low ones was not enough. `V1.0.72` (SMS consent type) alters the
`sms_transaction` table that `V1.0.67` creates, and `V1.0.76` rewords the consent type that `V1.0.72`
seeds, so the whole chain moved together. No release migration depends on one of this line's. The
release `emailConfig` widening (`V1.0.23.1`) still runs before this line's (`V1.0.68`).

Each rename is byte-identical, so comments inside the files still cite the old numbers. A scratch or
staging schema that ran this line's files under their old numbers with Flyway fails validation
afterwards and must be rebuilt. The devcontainer databases are built without Flyway history and are
unaffected.

PR #3996's unpublished attachment migrations were renumbered from V1.0.37/V1.0.38 to
V1.0.42/V1.0.43 so databases already running release migrations through V1.0.41 apply them.
Gaps in the sequence are intentional: they are numbers once claimed by pull requests that were
renumbered or not merged. Apply the present migrations in version order;
do not enable `outOfOrder` to fill gaps. The version line is global:
the shared `common/` line is in EVERY database's path, and on an **already-migrated database**
Flyway (no `outOfOrder`) never applies a new migration numbered below the highest it has already
run — `common/V1.0.78` on both provinces. A hypothetical new `bc/V1.0.11` would
apply fine on a fresh install (version order places it before `common/V1.0.78`) but would silently
never run on existing BC databases and would fail `flyway validate` there — so never number a new
migration at or below the global high-water mark, even if that number was only ever used under the
other province. Check every active branch inventory (release and develop) before allocating a
version, never renumber a published migration, and do not silently enable out-of-order migration
during promotion. See [the release process](../../../docs/release-process.md) for branch promotion
rules.

### Provider signature identity repair (V1.0.52)

Stop all application nodes and take a database backup before upgrade. The migration
copies signatures into a temporary table with a unique provider key before changing
`providerExt`. Exact byte-for-byte duplicates for an assigned provider collapse;
distinct text for that provider, including case, trailing-space
or a NULL versus text value, produces a duplicate-key failure with the source untouched.
Confirm the intended text with the affected provider and resolve the conflicting rows
from the backup; do not pick an arbitrary signature.
The same duplicate-key failure, with the source untouched, also occurs when two provider
numbers differ in bytes but compare equal under the column collation (for example `T099`,
`t099` and `T099 `), even when their signatures match. The rows are not merged, because they
may belong to different providers. Compare each value byte for byte with the provider record,
for example `SELECT provider_no FROM provider WHERE BINARY provider_no = BINARY '<value>'`; a plain
comparison uses the same collation and would match every variant. In the shipped schema
`provider.provider_no` is the primary key under that same collation, so at most one variant can
match a provider byte for byte. Keep that row, correct the others to the exact provider number
or remove them, and then retry; if their signatures then differ, resolve them as a conflicting
signature above. If no variant matches, find the intended provider with the site before
changing anything. If several variants match distinct provider records, the site's `provider`
table uses a collation that distinguishes them while `providerExt` does not, so those
providers cannot share this identity rule as installed: stop and resolve the schema mismatch
with the site rather than editing rows. For the DEB deployment, inspect
`sudo carlos-ctl db-info` and the migration error. Once the data conflict is resolved and
all published migration files are unchanged, run `sudo carlos-ctl db-repair`, then
`sudo carlos-ctl db-migrate` and `sudo carlos-ctl db-validate`. Do not use repair to accept
a checksum mismatch or conceal an unrelated migration failure. Successful application preserves signature text
and enforces one row per non-NULL provider identifier, matching the Hibernate entity ID.
Every legacy unassigned NULL-provider row is preserved, including identical rows
and rows with a NULL signature. These rows are outside the provider identity rule.

The repair rewrites rows through `provider_no` and `signature` only. If an adopted
`providerExt` has any other column, the migration stops before changing anything with an
unknown-column error naming `providerExt_has_unexpected_columns_resolve_before_signature_repair`.
Values in such a column would otherwise be replaced by defaults. MySQL's invisible generated
`my_row_id` primary key carries no data and is not counted. Decide with the site whether
the extra column is still needed, move or drop it from a backed-up database, and then use the
same repair-and-retry steps.

The final step names its unique index `providerExt_provider_no_uq`. If an adopted
`providerExt` already has an index with that name that is not a full-column unique index on
`provider_no` alone, the migration stops before changing anything with an unknown-column error
naming `providerExt_provider_no_uq_name_taken_resolve_before_signature_repair`. Otherwise the
index creation would fail only after the repaired rows were committed. Rename or drop that site
index on a backed-up database, then use the same repair-and-retry steps.

The executable isolated-database regression is
`python3 scripts/test-provider-signature-migration.py` from the repository root, using a
disposable local MariaDB/MySQL server and a CREATE/DROP DATABASE-capable account. The
client reads its usual option file or `MYSQL_PWD`; `MYSQL_HOST` must be local and
`MYSQL_USER` defaults to root. The test removes only its randomly named databases.
Both province CI jobs run it before their full Flyway migration/upgrade checks.

**Provider signature identity numbered `V1.0.52` (release 2026.08).** `V1.0.31` was reserved
for PR #3694, but `V1.0.32`–`V1.0.34` and `V1.0.36` merged first, so databases on this line may
already have run past `V1.0.31` and Flyway (no `outOfOrder`) would never apply it there. Per the
rule above, PR #3694 was renumbered above the high-water mark, first to `V1.0.40` and then to
`V1.0.44` after release 2026.08 shipped `V1.0.40`–`V1.0.43`. Release 2026.08 then merged
`common/V1.0.45` (consultation request indexes, #3976) and `common/V1.0.46` (field-note report
privilege), so databases on this line may already have run past `V1.0.44`. With `V1.0.47`–`V1.0.51`
then claimed by open pull requests (`V1.0.47` has since merged), PR #3694 now uses `V1.0.52`.
`V1.0.31` and `V1.0.44`, like the other gaps, stay unused.

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

Messenger membership coordination (PR #3986, issue #3964) adds
`common/V1.0.36__serialize_messenger_membership_changes.sql`. Automatic MRP routing adds
`common/V1.0.39__track_automatic_mrp_routing.sql`. Full lab labels add
`common/V1.0.40__widen_lab_labels.sql`. The Rich Text Letter signature-stamp inputs reach
upgraded installs through `common/V1.0.41__rtl_provider_stamp_fields.sql`. Tickler and
consultation/eForm attachment sources add `common/V1.0.42` and `common/V1.0.43`. The
Consultations list filter indexes (#3976) add `common/V1.0.45`, and the field-note report privilege
adds `common/V1.0.46`. `common/V1.0.47` aligns the `measurementsDeleted` columns with
`measurements`. Provider signature identity is enforced by
`common/V1.0.52__enforce_provider_signature_identity.sql`. The next unallocated version is
`V1.0.53`. Apply/merge these forward migrations in version order; if their merge
order changes after a release, renumber the still-unreleased migration before
shipping it. The coordination table contains no clinical data and does not
rewrite legacy memberships. All application instances must run the serialized
membership writer before relying on cross-instance duplicate prevention.
