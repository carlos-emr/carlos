# CARLOS Flyway migrations (`database/mysql/migration/`)

This is the **single source of truth** for the CARLOS `oscar` schema going forward. It replaces the
old "run every init script, then hand-pick a subset of `updates/*.sql`" build with a versioned,
checksummed Flyway migration set.

## Layout

```
migration/
  flyway.conf                 # non-secret defaults (locations, baseline); credentials passed at run time
  common/                     # RESERVED for genuinely shared FUTURE migrations (currently empty)
  on/
    V1__baseline_schema.sql          # Ontario schema baseline (structure only) — generated
    V2026.MM.DD__<desc>.sql          # NEW Ontario schema changes go here
  bc/
    V1__baseline_schema.sql          # British Columbia schema baseline (structure only) — generated
```

A database applies **`common` + exactly one province location** (`on` OR `bc`). The `V1` baseline is
**province-complete and structure-only** (`mysqldump --no-data`): each province dir carries its full
schema, so `flyway migrate` reproduces that province's script-built schema exactly (verified — the
only difference from the legacy build is Flyway's own `flyway_schema_history` table). Because a run
only ever combines `common` + one province, the two `V1` files never collide.

Seed / reference / demo data is **not** in the baseline (the authoritative "schema" is the table
structure, not the default rows). If seed/reference migrations are adopted later they are added as
separate `V1.x`/`R__` files; `common/` is where genuinely shared future migrations live.

## Conventions

- **New schema changes** are Flyway migrations named `VYYYY.MM.DD[.N]__short_description.sql`
  (1:1 with the old `update-YYYY-MM-DD-desc` cadence). Never edit `oscarinit*.sql` or add files to
  `../updates/` — that directory is frozen (see `../updates/README.md`).
- **The `V1` baseline is structure only.** Seed / reference data (ICD, measurement maps, province
  lookups) and the default accounts are not baked in. If adopted later, large static reference is a
  versioned load-once migration (`V1.x`), **not** a repeatable `R__` (which would replay multi-MB
  inserts on any checksum change).
- **drugref2 is a separate database** and is NOT managed here; it keeps its own
  `../development-drugref.sql` + `../drugref/*.sql`.
- **Dev/demo seed data stays OUT** of this migration set. `development.sql`, the FAKE-name patch,
  and RTL demo seeds are applied only by the devcontainer / resetsql skill, after `flyway migrate`.

## Generating the baseline

The `V1__baseline_schema.sql` files are **generated**, not hand-written. Run the maintainer tool in a
devcontainer (which has MariaDB):

```
database/mysql/build-baseline.sh
```

It builds a known-good database from `createdatabase_generic.sh <prov> 9` + the currently-required
`updates/*.sql`, `mysqldump --no-data`s the schema, normalizes it, and writes
`on/V1__baseline_schema.sql` and `bc/V1__baseline_schema.sql`. See
`docs/database-schema-management.md` for the full model and verification steps.
