# CARLOS Flyway migrations (`database/mysql/migration/`)

The **single source of truth** for the CARLOS `oscar` schema. A fresh `flyway migrate` produces a
complete, working database — schema **and** required reference data — with no legacy scripts in the
loop. Dead tables from removed modules are pruned (see `pruned-tables.txt`).

## Layout (Flyway-native: shared + per-province locations)

```
migration/
  flyway.conf                     # non-secret defaults (locations, baseline); creds passed at run time
  pruned-tables.txt               # dead tables excluded from the baseline (removed-module cruft)
  common/  V1__baseline_schema.sql        # province-neutral tables (structure)
  on/      V1.0.1__on_schema.sql          # Ontario-only tables (structure)
           V1.0.2__on_data.sql            # Ontario reference data (rows)
  bc/      V1.0.1__bc_schema.sql          # British Columbia-only tables (structure)
           V1.0.2__bc_data.sql            # British Columbia reference data (rows)
```

A database applies **`common` + exactly one province** location, selected by `flyway.locations`:

| Target | locations |
|---|---|
| Ontario | `common, on` |
| British Columbia | `common, bc` |

Versions order globally across the selected locations: `V1` (common schema) → `V1.0.1` (province
schema) → `V1.0.2` (province data). Because a run only ever combines `common` + one province, the two
provinces' `V1.0.x` files never collide. The province data file carries the full reference rows
(shared + province), so `common` holds structure only.

Demo/patient data is **not** in this baseline — it belongs in a dev-only `demo` location (see
`docs/database-schema-management.md`).

## Conventions

- **New schema changes** are Flyway migrations named `VYYYY.MM.DD[.N]__short_description.sql` in
  `common/` (shared) or `on/`/`bc/` (province-specific). Never edit the `V1*` baseline files or add to
  `../updates/` (frozen — see `../updates/README.md`).
- **The `V1` baseline is COMPLETE** (schema + required reference data), regenerated only by
  `../build-baseline.sh`. It excludes the dead tables in `pruned-tables.txt`.
- **drugref2 is a separate database** — not managed here (keeps `../development-drugref.sql` + `../drugref/*.sql`).

## Regenerating

Run in a devcontainer (MariaDB): `database/mysql/build-baseline.sh`. It builds the demo-free,
dead-pruned database per province and dumps the split baseline. See
`docs/database-schema-management.md` for the model and the schema+data verification.
