# `common/` — shared migrations

`V1__baseline_schema.sql` is the province-neutral schema baseline (structure only) — the tables
identical across Ontario and BC. It is the frozen genesis of the CARLOS schema (see `../README.md`).

`V1.0.3__performance_indexes.sql` is the first shared forward delta (performance indexes).
`V1.0.5__restore_live_legacy_common_tables.sql` restores live lookup/reference tables and ICD-10
data omitted from the generated baseline.
`V1.0.7__restore_phcp_diagnosis_groups.sql` restores the PHCP encounter report's diagnosis
grouping table and seeds numeric billing diagnoses with ICD-9 chapter categories.
`V1.0.8__expand_appointment_type_location.sql` expands appointment type locations.
`V1.0.9__remove_carlosdoc_schedule_group_denial.sql` removes the obsolete explicit denial.
`V1.0.10__seed_default_measurement_groups.sql` seeds the default measurement groups.
`V1.0.13__fix_phcp_diagnosis_group_backfill_collation.sql` re-runs the V1.0.7 dxphcpgroup
backfill with a collation-pinned cast. Once reached, it repairs missing rows where a
non-general_ci session collation caused V1.0.7 to abort and an operator bypassed that error. A
normal fail-fast CLI loop cannot reach V1.0.13 after that failure; use the
[same-session V1.0.7 recovery procedure](../README.md#mariadb-cli-recovery-for-v107), then continue
in version order.
`V1.0.14__widen_faxes_jobid_to_bigint.sql`, `V1.0.15__add_faxes_direction.sql` and
`V1.0.16__add_faxes_jobid_index.sql` evolve the `faxes` table for the SRFax importer.
`V1.0.17__enable_digital_signatures_by_default.sql` turns digital signatures on for the seeded
Default Facility (a deliberate one-time default change; see the file header).
`V1.0.18__performance_indexes_2.sql` is the second DAO-justified index pass (a delta on V1.0.3;
rationale in `docs/database-index-review-2026-09-04.md`).
`V1.0.20__widen_fax_destination_for_international_numbers.sql` preserves international fax
destinations by widening the queue column to 32 characters without changing existing values.
`V1.0.21__serialize_missing_lab_routing_creation.sql` adds transaction-scoped coordination
keys for acknowledgement/status writes, including missing routing rows and unassigned-row
cleanup. It preserves all existing routing records and comments. All application nodes must
run the updated acknowledgement code to participate in this coordination protocol.
`V1.0.22__add_lab_routing_lock_audit_columns.sql` also supplies the standard audit
metadata when the idempotent V1.0.21 creation finds a pre-existing coordination table.
`V1.0.24__add_email_consent_audit.sql` records the consent decision enforced for each
provider-to-patient email attempt.
`V1.0.25__add_sms_system_of_record.sql` creates the SMS system-of-record tables and seeds the
`_msgSMS` security object (read a stored message body).

`V1.0.26__widen_email_config_for_encrypted_credentials.sql` expands email configuration storage
to TEXT so encrypted credentials fit even when the plaintext configuration filled the old column.

`V1.0.27__prepare_outbound_email_archive_reference_engines.sql` preflights `document`,
`emailConfig`, and `emailLog` and converts legacy storage engines to InnoDB before archive
foreign keys are introduced. It is a no-op for existing InnoDB tables; see the migration header
for backup, maintenance-window, and partial-DDL recovery guidance. The dependent archive
migration is `V1.0.28__outbound_email_archive.sql`.

`V1.0.28__outbound_email_archive.sql` adds the archive, attachment, deletion tombstone,
and legal-hold event tables, plus the admin eDoc deletion grant. Existing customized
grants are preserved. Retirement retains the eDoc and its bytes; SMTP/SendGrid wiring
and archive UI are separate follow-up work. Earlier development copies of this schema
must be rebuilt or explicitly reconciled before adopting this migration: Flyway
`repair` alone does not apply changed table definitions. See the
[archive operations guide](../../../../docs/outbound-email-archive.md) for permissions,
retirement semantics, and failure investigation.

`V1.0.31__add_sms_security_objects.sql` seeds the `_sms` and `_admin.sms` security objects and
their default grants (admin and doctor on `_sms`, admin on `_admin.sms`); existing clinic grants
are preserved. See the [SMS backend guide](../../../../docs/architecture/sms-backend.md#security-objects).

`V1.0.32__add_sms_consent.sql` seeds the dedicated `sms_communication_consent` consent type and the
`sms_communication` property that points outbound SMS at it, and adds the consent audit snapshot
columns (`consent_status`, `consent_id`, `consent_last_update_date`) to `sms_transaction`. The consent
type is seeded inactive until its wording has compliance sign-off, so SMS stays blocked as not
configured until it is activated, and then for each patient until their consent is recorded.
See the [SMS backend guide](../../../../docs/architecture/sms-backend.md#patient-consent).

`V1.0.57__one_live_consent_per_type.sql` leaves at most one live `Consent` row per patient and
consent type, then adds a unique key that keeps it that way. It first fills NULL flags, retires
the extra live rows with the rule `ConsentRecords.effective` uses (an opt-out first, then a consent
the patient confirmed directly, then the latest edit, a zero or empty date counting as undated, then
the higher id), and makes `explicit`, `optout` and `deleted` NOT NULL. Retired duplicates change only
`deleted`; a row that had a NULL flag also gets that flag filled, and every row the migration
changes has its earlier values kept in `Consent_migration_audit`. The key sits on an invisible generated column, `live_demographic_no`,
because deleted rows legitimately repeat and MariaDB has no partial index.
It was merged as `V1.0.33` (#3917) and then renumbered to `V1.0.57`, above the highest version on
both develop and `release/2026.08`: a lower number never runs on an already-migrated database.

`V1.0.41__patient_portal_security_objects.sql` seeds the `_portal.*` security objects
used by the patient portal client and grants them to `admin` only.
Versions up to `V1.0.56` are not free: `release/2026.08` holds them and they arrive with that
forward-merge.

`V1.0.42__portal_email_delivery.sql` adds the portal password lifecycle columns to `emailLog`
(state, opaque source reference, secret ID, original portal origin and clinic). It never stores a
password.

`V1.0.58__patient_portal_invite_delivery.sql` adds `patient_portal_invite_delivery`, one row per
attempt to deliver a portal invitation, recording how far the prepare, store, commit and send
sequence got. It never stores the invitation code.
It was merged as `V1.0.43` (#3856) and then renumbered to `V1.0.58`, above the highest version on
both develop and `release/2026.08`: that line has its own, different `V1.0.43`, and a lower number
never runs on an already-migrated database.

`V1.0.54__activate_sms_consent.sql` replaces the seeded draft description of the SMS consent type
with its approved wording (#3848) and, in the same statement, switches the type on where the
`sms_communication` property still points at it. A clinic's own wording is left alone, and a clinic
that turned SMS consent off by deleting, clearing or repointing that property keeps it off. Its
number is set when it merges: it must be above the highest version on both develop and
`release/2026.08`.

Applied together with the selected province (`common` + `on`, or `common` + `bc`). Put **genuinely
shared future schema changes** here as `V1.0.N__short_description.sql` (sequential, next free version number) so one migration
covers both provinces. The version line is global across `common` + the selected province, so the
next free number accounts for province deltas too. The highest version in use is `common/V1.0.58`
(also the highest shared one), and `release/2026.08` holds versions up to `V1.0.56`, so the next
free version for ANY location is `V1.0.59` (see `../README.md`).
