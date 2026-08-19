# `on/` — Ontario migrations

The frozen Ontario slice of the V1 genesis baseline:
- `V1.0.1__on_schema.sql` — Ontario-only tables (structure)
- `V1.0.2__on_data.sql` — Ontario reference data (full: shared + Ontario rows)

Forward deltas (not part of the frozen baseline):
- `V1.0.4__on_performance_indexes.sql` — Ontario performance indexes
- `V1.0.6__restore_reporting_privilege.sql` — corrected doctor reporting privilege seed
- `V1.0.11__billing_filename_unique_indexes.sql` — published alpha1 index migration (checksum-frozen)
- `V1.0.12__portable_billing_filename_unique_indexes.sql` — portable guarded follow-up for MySQL/MariaDB

Applied together with `common/` for an Ontario install (`flyway.locations=filesystem:.../migration/common,filesystem:.../migration/on` (see `flyway.conf` for the real paths)). New
Ontario-only changes go here as `V1.0.N__short_description.sql` (sequential, next free version number).
The version line is global across `common` + `on`, so the next free number is `V1.0.13` (see
`../README.md`).
