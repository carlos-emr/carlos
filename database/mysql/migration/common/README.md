# `common/` — shared migrations

`V1__baseline_schema.sql` is the province-neutral schema baseline (structure only) — the tables
identical across Ontario and BC. It is the frozen genesis of the CARLOS schema (see `../README.md`).

`V1.0.3__performance_indexes.sql` is the first shared forward delta (performance indexes).
`V1.0.5__restore_live_legacy_common_tables.sql` restores live lookup/reference tables and ICD-10
data omitted from the generated baseline.
`V1.0.7__restore_phcp_diagnosis_groups.sql` restores the PHCP encounter report's diagnosis
grouping table and seeds numeric billing diagnoses with ICD-9 chapter categories.

Applied together with the selected province (`common` + `on`, or `common` + `bc`). Put **genuinely
shared future schema changes** here as `V1.0.N__short_description.sql` (sequential, next free version number) so one migration
covers both provinces. The version line is global across `common` + the selected province, so the
next free number accounts for province deltas too — the highest currently shipped is `V1.0.7`, so
the next is `V1.0.8` (see `../README.md`).
