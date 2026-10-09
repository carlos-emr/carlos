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
`V1.0.32__add_nrtf_tuning_fork_measurement_type.sql` seeds the NRTF measurement type
("Neurological exam: 128Hz tuning fork D1") used by the diabetes flowsheets for OntarioMD
DE16.066; the insert is existence-guarded because `measurementType.type` is not unique.
`V1.0.33__aacp_provided_revised_reviewed_validation.sql` moves the Asthma Action Plan (AACP)
measurement type from Yes/No/NA to a Provided/Revised/Reviewed validation (OntarioMD DE16.098).
`V1.0.42__tickler_docs.sql` creates `ticklerdocs`, the multi-attachment tickler store behind the
shared attachment picker (#3984), with the standard `lastUpdateUser` / `lastUpdateDate` audit pair,
and backfills it idempotently from `tickler_link` (creator and creation date preserved, lab source
kept in `lab_type`); `tickler_link` stays read-only for one release.

`V1.0.43__consultation_eform_lab_sources.sql` preserves the lab source in consultation request,
response and eForm attachments (#4024). It backfills only sources uniquely routed to the parent
patient; ambiguous legacy rows remain unresolved and require confirmation before printing.
These unpublished migrations were renumbered from V1.0.37/V1.0.38 after the release reached
V1.0.41. Apply V1.0.42 and then V1.0.43 with normal Flyway ordering; no `outOfOrder` is needed.

`V1.0.23.1__widen_email_config.sql` widens `emailConfig.configDetails` to `TEXT` to match the
entity mapping. `V1.0.29__rename_placeholder_demo_clinic.sql` replaces the seeded placeholder
clinic name.
`V1.0.52__enforce_provider_signature_identity.sql` repairs exact duplicate provider
signature rows for assigned providers and enforces the mapped provider identity.
Every unassigned NULL-provider row is retained, including identical rows. Conflicting signatures,
provider numbers that differ in bytes but compare equal under the column collation, a
`providerExt` with columns beyond `provider_no` and `signature`, and a site index already named
`providerExt_provider_no_uq` that is not the provider identity all fail before source changes.
See the parent README for preparation and recovery instructions.

## Develop-only migrations (`V1.0.66`–`V1.0.78`)

These were written on develop and renumbered at the 2026.08.0-alpha19 forward merge, keeping
develop's order, so that they all sit above `release/2026.08`'s `V1.0.56`: a lower number never runs
on a database already upgraded through that release. Their earlier numbers were `V1.0.24`–`V1.0.28`,
`V1.0.31`, `V1.0.32`, `V1.0.41`–`V1.0.43`, `V1.0.54`, `V1.0.57` and `V1.0.59`. The renames are
byte-identical, so comments inside the files still cite the old numbers. See `../README.md`.

`V1.0.66__add_email_consent_audit.sql` records the consent decision enforced for each
provider-to-patient email attempt.
`V1.0.67__add_sms_system_of_record.sql` creates the SMS system-of-record tables and seeds the
`_msgSMS` security object (read a stored message body).


`V1.0.68__widen_email_config_for_encrypted_credentials.sql` expands email configuration storage
to TEXT so encrypted credentials fit even when the plaintext configuration filled the old column.

`V1.0.69__prepare_outbound_email_archive_reference_engines.sql` preflights `document`,
`emailConfig`, and `emailLog` and converts legacy storage engines to InnoDB before archive
foreign keys are introduced. It is a no-op for existing InnoDB tables; see the migration header
for backup, maintenance-window, and partial-DDL recovery guidance. The dependent archive
migration is `V1.0.70__outbound_email_archive.sql`.

`V1.0.70__outbound_email_archive.sql` adds the archive, attachment, deletion tombstone,
and legal-hold event tables, plus the admin eDoc deletion grant. Existing customized
grants are preserved. Retirement retains the eDoc and its bytes; SMTP/SendGrid wiring
and archive UI are separate follow-up work. Earlier development copies of this schema
must be rebuilt or explicitly reconciled before adopting this migration: Flyway
`repair` alone does not apply changed table definitions. See the
[archive operations guide](../../../../docs/outbound-email-archive.md) for permissions,
retirement semantics, and failure investigation.

`V1.0.71__add_sms_security_objects.sql` seeds the `_sms` and `_admin.sms` security objects and
their default grants (admin and doctor on `_sms`, admin on `_admin.sms`); existing clinic grants
are preserved. See the [SMS backend guide](../../../../docs/architecture/sms-backend.md#security-objects).

`V1.0.72__add_sms_consent.sql` seeds the dedicated `sms_communication_consent` consent type and the
`sms_communication` property that points outbound SMS at it, and adds the consent audit snapshot
columns (`consent_status`, `consent_id`, `consent_last_update_date`) to `sms_transaction`. The consent
type is seeded inactive until its wording has compliance sign-off, so SMS stays blocked as not
configured until it is activated, and then for each patient until their consent is recorded.
See the [SMS backend guide](../../../../docs/architecture/sms-backend.md#patient-consent).

`V1.0.73__patient_portal_security_objects.sql` seeds the `_portal.*` security
objects used by the patient portal client. It grants all of them to `admin`, and read-only
`_portal.account` and full `_portal.secret` to the doctor, locum, psychiatrist and nursing roles.

`V1.0.74__portal_email_delivery.sql` adds the portal password lifecycle columns
to `emailLog` (state, opaque source reference, secret ID, original portal origin and clinic). It
never stores a password.

`V1.0.75__patient_portal_invite_delivery.sql` adds `patient_portal_invite_delivery`, one row per
attempt to deliver a portal invitation, recording how far the prepare, store, commit and send
sequence got. It never stores the invitation code. It runs after `V1.0.73`, which seeds the
`_portal.*` objects it grants.

`V1.0.76__activate_sms_consent.sql` replaces the seeded draft description of the SMS consent type
with its approved wording (#3848) and, in the same statement, switches the type on where the
`sms_communication` property still points at it. A clinic's own wording is left alone, and a clinic
that turned SMS consent off by deleting, clearing or repointing that property keeps it off.

`V1.0.77__one_live_consent_per_type.sql` leaves at most one live `Consent` row per patient and
consent type, then adds a unique key that keeps it that way. It first fills NULL flags, retires
the extra live rows with the rule `ConsentRecords.effective` uses (an opt-out first, then a consent
the patient confirmed directly, then the latest edit, a zero or empty date counting as undated, then
the higher id), and makes `explicit`, `optout` and `deleted` NOT NULL. Retired duplicates change only
`deleted`; a row that had a NULL flag also gets that flag filled, and every row the migration
changes has its earlier values kept in `Consent_migration_audit`. The key sits on an invisible generated column, `live_demographic_no`,
because deleted rows legitimately repeat and MariaDB has no partial index.

`V1.0.78__add_sms_config.sql` adds `sms_config`, the settings saved from Administration > SMS
(provider, sending and scheduler switches, sender number, and the encrypted webhook secret and
provider credentials). It seeds no row, so the `sms.*` properties keep applying until an
administrator saves the page. See the [SMS backend guide](../../../../docs/architecture/sms-backend.md#configuration-and-validation).

## Adding a shared migration

Applied together with the selected province (`common` + `on`, or `common` + `bc`). Put **genuinely
shared future schema changes** here as `V1.0.N__short_description.sql` (sequential, next free version number) so one migration
covers both provinces. The version line is global across `common` + the selected province, so the
next free number accounts for province deltas too. The highest version in use is `common/V1.0.78`
(also the highest shared one), so the next free version for ANY location is `V1.0.79`; open draft
pull requests numbered `V1.0.60` and `V1.0.61` must renumber above it (see `../README.md`).
Consult every active branch inventory before assigning a version. Never edit a published migration
or silently enable out-of-order application during promotion.

Messenger membership coordination (PR #3986, issue #3964) adds
`common/V1.0.36__serialize_messenger_membership_changes.sql`. Apply/merge these forward migrations
in version order; if their merge order changes after a release, renumber the still-unreleased
migration before shipping it. The coordination table contains no clinical data and does not
rewrite legacy memberships. All application instances must run the serialized
membership writer before relying on cross-instance duplicate prevention.

## V1.0.39 — Automatic MRP routing provenance

PR #4000 adds nullable `mrpDemographicNo` to `HRMDocumentToProvider` and
`providerLabRouting`. Existing rows remain independent (`NULL`); new automatic MRP and
forwarded access can then be revoked safely when a patient match is corrected or removed.
The two `ADD COLUMN IF NOT EXISTS` statements can be retried after interrupted DDL.
Deploy the migrations present in the release in version order. V1.0.39 has no dependency on
the attachment migrations now numbered V1.0.42/V1.0.43. Its original SQL header describes the
planned merge order; that comment is retained to preserve the migration checksum. The release
ordering here supersedes it.

## V1.0.45 — Consultation request list indexes

Issue #3976 adds Consultant and Provider (MRP) filters to the Consultations list.
`V1.0.45__consultation_request_indexes.sql` adds idempotent `CREATE INDEX IF NOT EXISTS`
secondary indexes on `consultationRequests` for `(status, referalDate)`,
`(status, appointmentDate)`, `(specId)` and `(serviceId)`; V1.0.3 already covers
`(demographicNo)` and `(sendTo, status)`. Schema-only, no data change, online InnoDB index adds.

## V1.0.40 — Complete lab labels

Widen `hl7TextInfo.label` from `VARCHAR(255)` to nullable `TEXT`. PATHL7 panels and
labels merged from earlier report versions can exceed 255 characters, rejecting the entire
import under strict SQL mode. Existing labels (including empty and NULL values) are retained.
The widening can be rerun safely. Apply before deploying the application; shared Ontario/BC
schema paths both require it. Verify in a disposable database with
`scripts/lab-label-migration-check.sql` from the repository root.

## V1.0.41 — Rich Text Letter signature-stamp inputs on upgraded installs

Adds the hidden `user_id`, `user_ohip_no` and `doctor_provider_no` inputs to the canonical Rich Text
Letter's `form_html`, which `editControl2.js` reads to stamp a letter with the signer's
`consult_sig_<provider_no>.png`. The same edit ships as
`updates/update-2026-09-20-rtl-provider-stamp-fields.sql`, but that script is only replayed by the
demo-data load and the OSCAR 19 importer; a package upgrade runs Flyway alone, so a letter seeded by
an earlier package never received the inputs. The `UPDATE` is identical to the script's (pinned by
`scripts/rtl-signature-stamp.test.js`), matches nothing on a fresh schema, skips customized or
already-patched rows, and can be rerun safely.
