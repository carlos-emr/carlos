# `database/mysql/updates/` — FROZEN (historical reference)

As of the Flyway migration-management adoption, this directory is **frozen** and kept
for historical and conversion reference only.

- These dated `update-YYYY-MM-DD-*.sql` patches are already folded into the consolidated
  baseline (`database/mysql/migration/common/V1__baseline_schema.sql` plus the province
  schema/data files under `migration/on/` and `migration/bc/`), which is what fresh installs
  load. They are **not** replayed on a fresh install.
- **Do not add new schema changes here.** New schema changes are Flyway migrations under
  `database/mysql/migration/` using the `VYYYY.MM.DD[.N]__description.sql` convention.
- A handful of files here are still referenced by dev-only tooling (the resetsql skill and
  the devcontainer RTL/demo seed steps); those references are intentional and must remain.

See `docs/database-schema-management.md` for the full build/migration model.
