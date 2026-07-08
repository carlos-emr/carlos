# `bc/` — British Columbia migrations

The frozen British Columbia slice of the V1 genesis baseline:
- `V1.0.1__bc_schema.sql` — BC-only tables (structure)
- `V1.0.2__bc_data.sql` — BC reference data (full: shared + BC rows, incl. billing/specialist/pharmacy catalogs)

Applied together with `common/` for a BC install (`flyway.locations=...common,...bc`). New BC-only
changes go here as `VYYYY.MM.DD[.N]__short_description.sql`.
