# `bc/` — British Columbia migrations

The frozen British Columbia slice of the V1 genesis baseline:
- `V1.0.1__bc_schema.sql` — BC-only tables (structure)
- `V1.0.2__bc_data.sql` — BC reference data (full: shared + BC rows, incl. billing/specialist/pharmacy catalogs)

Applied together with `common/` for a BC install (`flyway.locations=filesystem:.../migration/common,filesystem:.../migration/bc` (see `flyway.conf` for the real paths)). New BC-only
changes go here as `V1.0.N__short_description.sql` (sequential, next free version number).
