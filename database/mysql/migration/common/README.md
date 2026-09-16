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

`V1.0.23.1__enforce_provider_signature_identity.sql` repairs exact duplicate provider
signature rows and enforces the mapped provider identity. Conflicting signatures fail
before source changes. See the parent README for preparation and recovery instructions.

Applied together with exactly one province. The release high-water mark is `1.0.23.1`;
this maintenance slot precedes develop's allocated `1.0.24`–`1.0.28`. Consult the parent
inventory and all active branch inventories before assigning a migration version. Never
edit a published migration or silently enable out-of-order application during promotion.
