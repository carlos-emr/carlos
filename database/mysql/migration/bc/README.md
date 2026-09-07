# `bc/` — British Columbia migrations

The frozen British Columbia slice of the V1 genesis baseline:
- `V1.0.1__bc_schema.sql` — BC-only tables (structure)
- `V1.0.2__bc_data.sql` — BC reference data (full: shared + BC rows, incl. billing/specialist/pharmacy catalogs)

Forward deltas (not part of the frozen baseline):
- `V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql` — restored live BC legacy tables/reference data and corrected reporting grants
- `V1.0.19__bc_billingmaster_indexes.sql` — `billingmaster` indexes for the BC billing DAO query shapes (see `docs/database-index-review-2026-09-04.md`)

Applied together with `common/` for a BC install (`flyway.locations=filesystem:.../migration/common,filesystem:.../migration/bc` (see `flyway.conf` for the real paths)). New BC-only
changes go here as `V1.0.N__short_description.sql` (sequential, next free version number). The
version line is global across `common` + `bc`, so the next free BC-only number is `V1.0.20` — see
`../README.md` for why a lower number must never be reused.
