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

## The authoritative baseline (V1)

> V1 is the **complete** working database — schema **and** required reference data — that the
> devcontainer build scripts produce (`createdatabase_<prov>.sh` + the currently-required
> `updates/*.sql`), **minus demo data** (`development.sql`, FAKE-name/RTL demo seeds) **and minus
> dead tables** from removed modules (`migration/pruned-tables.txt`).

It is captured by `mysqldump` of that live, script-built database. It is **not** derived from the
Spring/Hibernate `@Entity` mappings: those **drift** from the real column set, so generating DDL from
them (or turning on `hibernate.hbm2ddl.auto`) would produce a schema the running application has never
actually used. `hibernate.hbm2ddl.auto` stays unset in production; Flyway owns the schema, Hibernate
only maps it.

A fresh `flyway migrate` therefore yields a runnable DB (structure + reference rows the app needs).
Demo/patient data is loaded separately (dev-only).

### Dead-table pruning

`migration/pruned-tables.txt` lists tables with **zero references anywhere in `src/`** (case-insensitive
substring, incl. tests/JSP/XML) and zero references in the repo outside `database/` — the schema cruft
of removed/legacy modules (BORN, OCAN, generic-intake, PHR, Integrator, Sharing/XDS, Eyeform, HRM,
MDS, drug-dispensing, CAISI reporting/bed/room/security). 20 of them are already `DROP`ped by the
project's own `updates/*-removal.sql` scripts, which just aren't in the fresh-install path. V1 omits
them; `build-baseline.sh` and `db-schema-verify.yml` drop the same set so the parity check holds.

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
| Devcontainer dev DB | `populate_db.sh` loads the migration `.sql` files via the `mysql` CLI (common + on), then demo | The MariaDB initdb temp server is socket-only and Flyway needs TCP, so the devcontainer applies the SAME migration files with the mysql client (dev DBs are disposable — no `flyway_schema_history` needed). Demo (`development.sql`, filtered to the live schema by `build-demo.sh`) loads after, dev-only. Native reset: rebuild the container, or `flyway clean && migrate` on a TCP connection. |
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

It builds a throwaway database per province from `createdatabase_generic.sh <prov> 9` + the required
recent updates, **drops the dead tables** in `pruned-tables.txt`, splits the live tables into shared
vs province-only, and writes the complete baseline: `common/V1__baseline_schema.sql`,
`on/V1.0.1__on_schema.sql` + `on/V1.0.2__on_data.sql`, and the BC equivalents. Commit the regenerated
files.

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
  migration directory + config, `build-baseline.sh`, the CI schema-verify workflow, the
  frozen-`updates/` policy, and the `carlos-ctl db migrate|baseline|info` verbs.
- **Landed + verified:** the complete, dead-pruned, province-split `V1` baseline (Ontario 410 live
  tables, BC 397). Verified that `flyway migrate` (common + province) reproduces the pruned
  script-built database **exactly** — schema identical AND every table's exact row count identical —
  for both provinces (the only schema difference is Flyway's own `flyway_schema_history`, excluded by
  the CI diff).
- **Next:** add a dev-only `demo` location (development.sql) selected in the devcontainer, cut
  `populate_db.sh` over to `flyway migrate` + demo, and switch production to
  `carlos.flyway.onBoot=validate`. The 2 held tables (`HL7HandlerMSHMapping`, `billing_on_cheader2`)
  await a manual keep/drop decision.
