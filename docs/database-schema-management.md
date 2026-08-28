# CARLOS Database Schema Management

## Why this exists

Historically CARLOS built its database by running raw SQL through shell scripts with **no version
anchor**: `createdatabase_generic.sh` loaded `oscarinit.sql` + province + `oscardata*` + ICD + caisi
+ `oscarinit_2025.sql`, and the devcontainer then replayed a hand-picked subset of
`database/mysql/updates/*.sql`. The dated `updates/` patches had no manifest and were never
replayed in order on a fresh install — they were already folded into `oscarinit.sql`. Nothing tracked
"what schema version is this database at."

Since every new CARLOS install is effectively **fresh**, and any OpenO / OSCAR-19 datadir carried
over is treated as a **conversion**, CARLOS now uses **Flyway** for schema management: a consolidated
`V1` baseline plus forward-only sequential migrations, with a `flyway_schema_history` version table as the
single source of truth. The legacy script build (`createdatabase_*.sh`, `oscarinit*.sql`,
`oscardata*.sql`, `icd*.sql`, `measurementMapData.sql`, `caisi/initcaisi*.sql`, `olis/olisinit.sql`,
`bc_*.sql`) has been **retired** — it is recoverable from git history but no longer in any build path.

## The authoritative baseline (V1)

> V1 is the **complete, frozen genesis** of the CARLOS `carlos` schema — structure **and** required
> reference data — **minus demo data** (`development.sql`, FAKE-name/RTL demo seeds) **and minus
> dead tables** from removed modules (`migration/pruned-tables.txt`).

It was captured once, at the Flyway cutover, by `mysqldump` of the live database the (now-retired)
legacy scripts produced, then verified byte-for-byte against that build (see **Verification**). It is
**not** derived from the Spring/Hibernate `@Entity` mappings: those **drift** from the real column
set, so generating DDL from them (or turning on `hibernate.hbm2ddl.auto`) would produce a schema the
running application has never actually used. `hibernate.hbm2ddl.auto` stays unset in production;
Flyway owns the schema, Hibernate only maps it.

A fresh `flyway migrate` yields a runnable DB (structure + reference rows the app needs). Demo/patient
data is loaded separately (dev-only). The baseline is never regenerated — schema changes ship as new
forward migrations (see **Evolving the schema**).

**Known, intentional guideline exception:** many legacy-captured tables in the baseline predate
the "every table includes `lastUpdateUser`/`lastUpdateDate` audit columns" convention. The baseline
preserves the schema the application actually runs; retrofitting audit columns is application work
(entity + DAO + backfill per table), not a baseline edit. New tables added via forward migrations
MUST include the audit columns.

### Security note — seeded default credentials

The baseline reference data (province `V1.0.2` files) seeds the **documented default clinician
login** `carlosdoc` / `carlos2026` (PIN `2026`) — inherited from the legacy `oscardata` seed, not
introduced by the Flyway cutover. The `.deb` installer prints a prominent rotation warning after
schema creation. Any non-dev deployment MUST rotate or disable this account before entering real
patient data; installer/app-forced rotation at first login is tracked as application-level
follow-up work.

### Database engine requirement — MariaDB

CARLOS targets **MariaDB** (11.8.x LTS is what dev, CI, and the container/podman production stack
all run). The migrations rely on MariaDB-only idempotent DDL (`CREATE INDEX IF NOT EXISTS`,
`ALTER TABLE ... DROP INDEX IF EXISTS`) — the repo's established idiom — and the dev/CI tooling
uses the `mariadb`/`mariadb-dump` clients (MariaDB 11.x no longer ships the `mysql` symlinks). The
`.deb` package now depends on `mariadb-server`, `mariadb-client`, and `libmariadb-java`; any
remaining script compatibility shims are for legacy local environments, not an advertised MySQL
runtime target.

### Dead-table pruning

`migration/pruned-tables.txt` lists tables with **zero references anywhere in `src/`** (case-insensitive
substring, incl. tests/JSP/XML) and zero references in the repo outside `database/` — the schema cruft
of removed/legacy modules (BORN, OCAN, generic-intake, PHR, Integrator, Sharing/XDS, Eyeform, HRM,
MDS, drug-dispensing, CAISI reporting/bed/room/security). These were excluded when V1 was captured; the
file is retained as documentation of what was dropped and why.

## Layout

- `database/mysql/migration/` — the Flyway migration set (single source of truth).
  See its `README.md` for the `common` / `on` / `bc` location split and the version-number grammar
  (`V1` baseline, `V1.0.1`/`V1.0.2` province schema/reference, then sequential `V1.0.N__desc` forward deltas).
- `database/mysql/updates/` — **frozen**, historical/reference only (see its `README.md`). A few
  entries are still applied for demo seeding (RTL eform) and read by regression tests.
- `database/mysql/build-demo.sh` — the filter that trims the demo dataset to the live (pruned)
  schema. Its output, the dev-only demo dataset `development.sql`, is written to (and lives at)
  `.devcontainer/db/scripts/development.sql`, not under `database/mysql/`.
- The legacy build files (`createdatabase_*.sh`, `oscarinit*.sql`, `oscardata*.sql`, `icd*.sql`,
  `measurementMapData.sql`, `caisi/initcaisi*.sql`, `olis/olisinit.sql`, `bc_*.sql`) and the old
  `build-baseline.sh` generator have been removed — recover them from git history if ever needed.

Migrations are also copied onto the WAR classpath at `db/migration` (a build `<resource>` in
`pom.xml`) so the boot-time gate can read them.

## Where migrations run

| Context | How | Notes |
|---|---|---|
| Devcontainer dev DB | `populate_db.sh` loads the migration `.sql` files via the `mariadb` client (common + on), then demo | The MariaDB initdb temp server is socket-only and Flyway needs TCP, so the devcontainer applies the SAME migration files with the `mariadb` client (MariaDB 11.x has no `mysql` symlink; dev DBs are disposable — no `flyway_schema_history` needed). Demo (`development.sql`, filtered to the live schema by `build-demo.sh`) loads after, dev-only. Native reset: rebuild the container, or `flyway clean && migrate` on a TCP connection. |
| CI schema check | `.github/workflows/db-schema-verify.yml` | Runs `flyway migrate` + `validate` for both provinces and smoke-checks a populated schema (table count + reference rows). |
| Production (.deb) | `release/postinst` runs the bundled Flyway CLI `migrate` (common + province) on new installs | The .deb bundles `migration/` + a pinned, offline Flyway CLI. Upgrades apply the frozen `updates/*.sql`. |
| Production (container) | `carlos-ctl db migrate` (operator-gated, after `carlos-ctl db-backup`) | Never on app boot. Recommended target posture: set `carlos.flyway.onBoot=validate` so the app refuses to start if the schema is behind (shipped default is `off` — see Rollout status). |
| OpenO/oscar19 conversion | `carlos-ctl db baseline --version=1.0.2` then `carlos-ctl db migrate` | Stamps the existing datadir at the FULL genesis (common `V1` + province `V1.0.1`/`V1.0.2`) so only forward migrations (`V1.0.3`+) apply. **Never stamp at 1**: Flyway would then re-run the province files — `V1.0.1` DROPs and recreates province tables (data loss) and `V1.0.2` re-inserts reference rows (abort). |

The boot gate is `io.github.carlos_emr.carlos.db.FlywaySchemaValidator`, wired in `spring_jpa.xml`
and controlled by `carlos.flyway.onBoot` (`off` | `validate` | `migrate`, default `off`) and
`carlos.flyway.locations` in `carlos.properties`.

## Evolving the schema

The `V1` baseline is frozen and never regenerated. To change the schema, add a **forward migration**:

- Pick the location: `migration/common/` for a shared change, `migration/on/` or `migration/bc/` for a
  province-specific one.
- Name it `V1.0.N__short_description.sql` using the next free version number across common + your province (versions must be unique within the applied set; provinces never co-apply, but avoid reusing numbers to prevent confusion).
- Make it **idempotent** (guard `ALTER`/`INSERT` with existence checks) so re-runs are safe.

A fresh `flyway migrate` applies `V1` then your delta; existing databases apply only the new delta.
`db-schema-verify.yml` re-runs `flyway migrate` + `validate` for both provinces on any
`database/mysql/**` change.

## Verification

The original cutover guarantee was **schema + data identity**: the Flyway-built database equalled the
legacy-script-built one. That was proven once (PR #3150) — normalized `mysqldump --no-data` schema
diffs and exact per-table row counts matched for both provinces (only `flyway_schema_history` differs),
with the legacy build now retired, that proof lives in git history.

Ongoing verification:

1. **Baseline applies (CI).** `db-schema-verify.yml` runs `flyway migrate` + `validate` for `on` and
   `bc` on a MariaDB 11.8.8 service DB and smoke-checks a populated schema (table count ≥ 300, the
   `carlosdoc` admin privilege seed, and diagnostic codes present).
2. **Runtime proof.** Boot the devcontainer against the Flyway-built DB, log in as
   `carlosdoc` / `carlos2026`, run the UI smoke test and a couple of DAO integration tests.
3. **Conversion proof.** On a copy of an OpenO/oscar19 datadir, `flyway baseline -baselineVersion=1.0.2`
   then `flyway migrate`; confirm only forward migrations (`V1.0.3`+) run and the app boots.

## Rollout status

- **Landed + verified:** the complete, dead-pruned, province-split `V1` baseline (Ontario 410 live
  tables, BC 397), verified to reproduce the pruned script-built database **exactly** (schema and every
  table's row count) for both provinces at cutover. Flyway dependencies + Maven plugin, the boot-time
  validate gate (default `off`), the migration directory + config, the frozen-`updates/` policy, and
  the `carlos-ctl db migrate|baseline|info` verbs.
- **Landed:** the devcontainer builds its dev DB from the migration set (+ filtered demo), and the
  legacy script build has been retired — `createdatabase_*.sh` / `oscarinit*` / `oscardata*` / `icd*` /
  `measurementMapData` / `caisi/initcaisi*` / `olis/olisinit` / `bc_*` and `build-baseline.sh` removed.
  The `.deb` `release/postinst` now creates new-install schemas via the bundled Flyway CLI instead of
  `createdatabase_*.sh`.
- **Open:** the 2 held tables (`HL7HandlerMSHMapping`, `billing_on_cheader2`) await a manual keep/drop
  decision; switching production to `carlos.flyway.onBoot=validate` by default.
