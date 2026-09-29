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

Applied together with the selected province (`common` + `on`, or `common` + `bc`). Put **genuinely
shared future schema changes** here as `V1.0.N__short_description.sql` (sequential, next free version number) so one migration
covers both provinces. The version line is global across `common` + the selected province, so the
next free number accounts for province deltas too. The highest version in this branch is `common/V1.0.41`.
The next unallocated version for ANY location is `V1.0.42` (see `../README.md`).
PR #3996's proposed `V1.0.37` and `V1.0.38` are absent from alpha16 and must be renumbered
above the then-current high-water mark before that feature merges after this promotion.

Messenger membership coordination (#3964) adds
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
Deploy the migrations present in the release in version order, ending with V1.0.36,
V1.0.39 and V1.0.40 on both provinces. V1.0.39 has no dependency on the unmerged attachment migrations
V1.0.37/V1.0.38 from PR #3996. Its original SQL header describes the planned merge order;
that comment is retained to preserve the migration checksum. The release ordering here supersedes it.

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
