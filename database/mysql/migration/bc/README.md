# `bc/` — British Columbia migrations

The frozen British Columbia slice of the V1 genesis baseline:
- `V1.0.1__bc_schema.sql` — BC-only tables (structure)
- `V1.0.2__bc_data.sql` — BC reference data (full: shared + BC rows, incl. billing/specialist/pharmacy catalogs)

Forward deltas (not part of the frozen baseline):
- `V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql` — restored live BC legacy tables/reference data and corrected reporting grants
- `V1.0.19__bc_billingmaster_indexes.sql` — `billingmaster` indexes for the BC billing DAO query shapes (see `docs/database-index-review-2026-09-04.md`)
- `V1.0.59.3__restore_wcb_reference_data.sql` — restore 85 WorkSafeBC body parts, 82 injury codes and four sides from the retired legacy seed (#5062). Match existing rows by `code`/`sidetype`, preserving local IDs, descriptions and notes. Reapplying the SQL adds no duplicates. Verify with `python3 scripts/test-wcb-reference-migration.py` against a disposable local MariaDB/MySQL server.

Applied together with `common/` for a BC install (`flyway.locations=filesystem:.../migration/common,filesystem:.../migration/bc` (see `flyway.conf` for the real paths)). New BC-only
changes go here as `V1.0.N__short_description.sql` (sequential, next free version number). The
version line is global across every location. The highest migration in this branch, including
on the BC path, is `bc/V1.0.59.3`; `V1.0.48`–`V1.0.51` and `V1.0.59.1` are claimed by open pull requests, so the
next unallocated version is `V1.0.59.4` (below the newer release line's `V1.0.60` floor).
Numbers used only by Ontario are still taken — see
`../README.md` for migration ordering, and why numbers at or below the global high-water
mark must never be reused.
