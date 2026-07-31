# CARLOS Flyway migrations (`database/mysql/migration/`)

The **single source of truth** for the CARLOS `oscar` schema. A fresh `flyway migrate` produces a
complete, working database — schema **and** required reference data — with no legacy scripts in the
loop. Dead tables from removed modules are pruned (see `pruned-tables.txt`).

## Layout (Flyway-native: shared + per-province locations)

```text
migration/
  flyway.conf                     # non-secret defaults (locations, baseline); creds passed at run time
  pruned-tables.txt               # dead tables excluded from the baseline (removed-module cruft)
  common/  V1__baseline_schema.sql          # province-neutral tables (structure)
           V1.0.3__performance_indexes.sql  # forward delta: shared performance indexes
           V1.0.5__restore_live_legacy_common_tables.sql
  on/      V1.0.1__on_schema.sql            # Ontario-only tables (structure)
           V1.0.2__on_data.sql              # Ontario reference data (rows)
           V1.0.4__on_performance_indexes.sql
           V1.0.6__restore_reporting_privilege.sql
  bc/      V1.0.1__bc_schema.sql            # British Columbia-only tables (structure)
           V1.0.2__bc_data.sql              # British Columbia reference data (rows)
           V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql
```

The **genesis baseline** is `V1` + the province `V1.0.1`/`V1.0.2` files (frozen). Everything from
`V1.0.3` onward is a forward delta. The **highest version currently shipped is `V1.0.6`**, so the
next free number for a new migration is `V1.0.7` (the version line is global across `common` + the
selected province — see below).

A database applies **`common` + exactly one province** location, selected by `flyway.locations`:

| Target | locations |
|---|---|
| Ontario | `common, on` |
| British Columbia | `common, bc` |

Versions order globally across the selected locations: `V1` (common schema) → `V1.0.1` (province
schema) → `V1.0.2` (province data). Because a run only ever combines `common` + one province, the two
provinces' `V1.0.x` files never collide. The province data file carries the full reference rows
(shared + province). Later forward migrations restore live lookup/reference tables, ICD-10 data,
performance indexes, and corrected reporting grants that were missing from the first generated
baseline.

Demo/patient data is **not** in this baseline — it belongs in a dev-only `demo` location (see
`docs/database-schema-management.md`).

## Conventions

- **New schema changes** are Flyway migrations named `V1.0.N__short_description.sql` (sequential, next free number) in
  `common/` (shared) or `on/`/`bc/` (province-specific). Never edit the `V1*` baseline files or add to
  `../updates/` (frozen — see `../updates/README.md`).
- **The `V1` baseline is COMPLETE and frozen** (schema + required reference data): it is the genesis
  of the CARLOS schema. It was captured once (demo-free, dead-pruned per `pruned-tables.txt`) at the
  Flyway cutover; the legacy `createdatabase_*.sh` / `oscarinit*` / `oscardata*` build it replaced has
  been retired (recoverable from git history). Do not regenerate it — evolve the schema forward.
- **drugref2 is a separate database** — not managed here (keeps `../development-drugref.sql` + `../drugref/*.sql`).

## Evolving the schema

Add a forward migration under the right location — `common/` for shared changes, `on/`/`bc/` for
province-specific ones — named `V1.0.N__short_description.sql` (next free number), and make it idempotent. A
fresh `flyway migrate` applies `V1` then your delta; existing databases apply only the new delta.
See `docs/database-schema-management.md` for the model and CI verification (`db-schema-verify.yml`).
