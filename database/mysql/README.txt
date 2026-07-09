CARLOS EMR database

Schema management is Flyway. The single source of truth is the migration set under
`migration/` — a consolidated V1 genesis baseline (complete schema + required reference data)
plus forward-only migrations, tracked in a `flyway_schema_history` version table.

New install:
  flyway migrate  (locations = migration/common + one province: migration/on OR migration/bc)
A fresh migrate yields a runnable database (structure + reference rows the app needs). Demo/patient
data is dev-only (development.sql, filtered by build-demo.sh) and is NOT part of the baseline.

Schema changes:
  Add a forward migration V1.0.N__short_description.sql (sequential, next free number) under
  migration/common (shared) or migration/on / migration/bc (province-specific). Make it
  idempotent. Never edit the V1* baseline.

Notes:
- InnoDB is required (foreign keys). Tables use the utf8mb4 character set.
- `updates/` holds the FROZEN legacy dated patches (historical reference; a few are still applied
  for demo seeding). The legacy script build (createdatabase_*.sh, oscarinit*.sql, oscardata*.sql,
  icd*.sql, measurementMapData.sql, caisi/initcaisi*.sql, olis/olisinit.sql, bc_*.sql) has been
  retired — recover from git history if ever needed.

See docs/database-schema-management.md and migration/README.md for the full model.
