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
`V1.0.14` through `V1.0.16` widen and index fax job identifiers and add fax direction.
`V1.0.17__add_email_consent_audit.sql` records the consent decision enforced for each
provider-to-patient email attempt.

Applied together with the selected province (`common` + `on`, or `common` + `bc`). Put **genuinely
shared future schema changes** here as `V1.0.N__short_description.sql` (sequential, next free version number) so one migration
covers both provinces. The version line is global across `common` + the selected province, so the
next free number accounts for province deltas too. The highest version in use is the shared
`V1.0.17`, so the next shared (or Ontario) version is `V1.0.18` (see `../README.md`).
