# CARLOS Database Schema Management

## Why this exists

Historically CARLOS built its database by running raw SQL through shell scripts with **no version
anchor**: `createdatabase_generic.sh` loaded `oscarinit.sql` + province + `oscardata*` + ICD + caisi
+ `oscarinit_2025.sql`, and the devcontainer then replayed a hand-picked subset of
`database/mysql/updates/*.sql`. The 651 dated `updates/` patches have no manifest and are never
replayed in order on a fresh install — they are already folded into `oscarinit.sql`. Nothing tracked
"what schema version is this database at."

Since every new CARLOS install is effectively **fresh**, and any OpenO / OSCAR-19 datadir carried
over is treated as a **conversion**, CARLOS uses **Flyway** for schema management: a consolidated
`V1` baseline plus forward-only dated migrations, with a `flyway_schema_history` version table as the
single source of truth.

## The authoritative schema

> The authoritative schema is **what the devcontainer build scripts produce** —
> `createdatabase_<prov>.sh` plus the currently-required `updates/*.sql` — **excluding demo data**
> (`development.sql` and the FAKE-name/RTL demo seeds).

It is captured by `mysqldump` of that live, script-built database. It is **not** derived from the
Spring/Hibernate `@Entity` mappings: those currently **drift** from the real column set, so
generating DDL from them (or turning on `hibernate.hbm2ddl.auto`) would produce a schema the running
application has never actually used. `hibernate.hbm2ddl.auto` stays unset in production; Flyway owns
the schema, Hibernate only maps it.

## Layout

- `database/mysql/migration/` — the Flyway migration set (single source of truth going forward).
  See its `README.md` for the `common` / `on` / `bc` location split and the version-number grammar
  (`V1` baseline, `V1.x` seed/reference, `VYYYY.MM.DD[.N]__desc` deltas).
- `database/mysql/build-baseline.sh` — maintainer tool that regenerates the `V1*` files from the
  script-built database.
- `database/mysql/updates/` — **frozen**, historical/reference only (see its `README.md`).
- `database/mysql/oscarinit*.sql`, `oscardata*.sql`, `createdatabase_*.sh` — retained as the
  baseline generator inputs and a fallback build path.

Migrations are also copied onto the WAR classpath at `db/migration` (a build `<resource>` in
`pom.xml`) so the boot-time gate can read them.

## Where migrations run

| Context | How | Notes |
|---|---|---|
| Devcontainer / CI dev DB | `flyway migrate` (or `carlos.flyway.onBoot=migrate`) | Disposable, single-node. Demo data loaded separately, after migrate. |
| CI schema check | `.github/workflows/db-schema-verify.yml` | Diffs the legacy-script schema against the Flyway schema; dormant until `V1` is committed. |
| Production | `carlos-ctl db migrate` (operator-gated, after `carlos-ctl db-backup`) | Never on app boot. The app runs `carlos.flyway.onBoot=validate` and refuses to start if the schema is behind. |
| OpenO/oscar19 conversion | `carlos-ctl db baseline --version=1` then `carlos-ctl db migrate` | Stamps the existing datadir at the baseline; only forward CARLOS migrations apply. |

The boot gate is `io.github.carlos_emr.carlos.db.FlywaySchemaValidator`, wired in `spring_jpa.xml`
and controlled by `carlos.flyway.onBoot` (`off` | `validate` | `migrate`, default `off`) and
`carlos.flyway.locations` in `carlos.properties`.

## Regenerating the baseline

Run in a devcontainer (which has MariaDB):

```bash
database/mysql/build-baseline.sh
```

It builds a throwaway database from `createdatabase_on.sh` / `createdatabase_bc.sh` + the required
recent updates, dumps normalized schema and seed, and writes `migration/_generated_*.sql`. Split
those into the committed `V1*` files (province-neutral vs province-specific), then verify and commit.

## Verification

The guarantee is **schema identity**: a Flyway-built database equals a legacy-script-built database.

1. **Schema diff (core proof).** Build DB-A the legacy way and DB-B with `flyway migrate`, normalize
   both (`mysqldump --no-data --skip-comments --skip-dump-date`, strip `AUTO_INCREMENT=`, sort), and
   assert an empty diff. Repeat for `on` and `bc`. This is exactly what `db-schema-verify.yml` runs.
2. **Seed parity.** Compare row counts on reference/required tables (`icd10`, `measurementMap`,
   lookup tables, the `carlosdoc` `security`/`provider` seed).
3. **Runtime proof.** Boot the devcontainer against the Flyway-built DB, log in as
   `carlosdoc` / `carlos2026`, run the UI smoke test and a couple of DAO integration tests.
4. **Conversion proof.** On a copy of an OpenO/oscar19 datadir, `flyway baseline -baselineVersion=1`
   then `flyway migrate`; confirm only forward migrations run and the app boots.

## Rollout status

This is staged so each step leaves the tree buildable:

- **Landed:** Flyway dependencies + Maven plugin, the boot-time validate gate (default `off`), the
  migration directory + config, `build-baseline.sh`, the CI schema-verify workflow (dormant until
  `V1` lands), the frozen-`updates/` policy, and the `carlos-ctl db migrate|baseline|info` verbs.
- **Next (needs a devcontainer DB):** run `build-baseline.sh`, commit the `V1*` files (activates CI),
  then cut `populate_db.sh` over to `flyway migrate` + a clearly separated dev-only seed block, and
  switch production to `carlos.flyway.onBoot=validate`.
