# `on/` — Ontario migrations

The frozen Ontario slice of the V1 genesis baseline:
- `V1.0.1__on_schema.sql` — Ontario-only tables (structure)
- `V1.0.2__on_data.sql` — Ontario reference data (full: shared + Ontario rows)

Forward deltas (not part of the frozen baseline):
- `V1.0.4__on_performance_indexes.sql` — Ontario performance indexes
- `V1.0.6__restore_reporting_privilege.sql` — corrected doctor reporting privilege seed
- `V1.0.11__billing_filename_unique_indexes.sql` — published alpha1 index migration (checksum-frozen)
- `V1.0.12__portable_billing_filename_unique_indexes.sql` — portable guarded follow-up for MySQL/MariaDB
- `V1.0.23__activate_legacy_consultation_services.sql` — guarded repair of the untouched consultation service seed
- `V1.0.34__add_oma_uninsured_service_fees.sql` — OMA uninsured service fees on the PRIVATE billing form (OntarioMD PC13.19)

Applied together with `common/` for an Ontario install (`flyway.locations=filesystem:.../migration/common,filesystem:.../migration/on` (see `flyway.conf` for the real paths)). New
Ontario-only changes go here as `V1.0.N__short_description.sql` (sequential, next free version number).
The version line is global across every location. The highest migration in this branch is
`common/V1.0.36`; `V1.0.35` is allocated to PR #3996, so the next unallocated version is
`V1.0.37`. See `../README.md` for migration ordering and why numbers at or below the
global high-water mark must never be reused.
