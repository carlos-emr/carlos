# Migrating a clinic from OSCAR 19 (experimental)

`carlos-ctl import-o19` imports an OSCAR 19 clinic backup into a **stock
initial deploy** of the CARLOS Debian package. The feature is
**(experimental)**: every migration's output should receive a technical
review — verification report, spot checks, UI smoke — before clinical use.

Design and rationale: `docs/oscar19-to-carlos-migration-plan.md`.
Development tooling (manifest generator, rehearsal fixtures):
`scripts/migration/o19/README.md`.

## 1. Assess the clinic first (before any backup is shipped)

Copy ONE file to the OSCAR 19 server and run it against the live database:

```bash
scp /usr/lib/carlos-emr/carlos_ctl/o19_preflight.py o19-server:
ssh o19-server python3 o19_preflight.py --db oscar \
    --mysql-cmd mysql --mysql-arg=-uroot --mysql-password-file /root/.o19pw \
    --properties /path/to/oscar.properties --json preflight.json \
    --digests o19-digests.json
```

`--digests` takes a content digest of every table — a SHA-256 per row,
aggregated two independent ways — and writes it (0600) alongside the
report. Ship that file in the bundle: it is what lets the CARLOS host
prove the dump, the transfer and the restore carried every **value**,
not merely the right number of rows. It costs one full scan of the
database (measured at roughly 200k rows/s, so about two minutes for a
20M-row clinic) and can be run outside the cutover window, as long as it
runs against the same data the dump is taken from.

Client options that start with `-` need the `--mysql-arg=VALUE` form. The
password goes through `--mysql-password-file` (handed to the client as
`MYSQL_PWD`) or a client defaults file
(`--mysql-arg=--defaults-extra-file=/root/.my.cnf`); a bare interactive
`-p` is refused because every check runs its own client process.

Exit 0 = go. Exit 1 = go, once the listed `--accept` sign-offs are agreed
with the clinic (data in removed modules becomes archive-only). Exit 2 =
no-go until remediated — notably **LDAP authentication** (CARLOS has none;
provision local credentials first), **password-protected casemgmt notes**,
and the not-yet-curated **BC** profile. The report doubles as the
clinic-facing feasibility statement: what migrates, what becomes
archive-only (OLIS, form modules that were removed, antenatal ONAR forms),
and what must be decided before cutover.

## 2. Produce the three inputs on the OSCAR 19 server

During the cutover window, with Tomcat stopped on the OSCAR 19 server:

```bash
mysqldump --single-transaction --quick --skip-triggers oscar \
    | gzip > o19.sql.gz
# Tomcat MUST already be stopped: OSCAR 19 tables are usually MyISAM, and
# --single-transaction gives a consistent snapshot only for transactional
# tables — against a live database it would produce a dump whose tables
# disagree with each other, which no check downstream can detect.
# --skip-triggers: mysqldump emits triggers by default and a trigger
# carries a DEFINER clause the schema-scoped restore account cannot set.
# one database, named as an argument: never --databases/--all-databases
# (the importer refuses a dump that names its own schema); on MySQL 5.6+
# add --set-gtid-purged=OFF
tar -C /var/lib/OscarDocument -czf o19-documents.tar.gz <context-dir>
# context-dir is the directory holding document/, eform/images/, ... —
# often oscar, oscar_mcmaster, or the database name
cp /path/to/oscar.properties .
# and the content digests from step 1, if they were not taken then:
python3 o19_preflight.py --db oscar --mysql-cmd mysql --mysql-arg=-uroot \
    --mysql-password-file /root/.o19pw --digests o19-digests.json
```

Bundle them (recommended single-file handoff, encrypted):

```bash
tar -czf - o19.sql.gz o19-documents.tar.gz oscar.properties \
      o19-digests.json \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
      -pass file:PASSFILE -out o19-bundle.tar.gz.enc
```

The members sit at the archive root as plain files, with no directory
prefix: exactly one `*.sql` or `*.sql.gz`, exactly one `*.properties`, at
most one `*.tar`/`*.tar.gz` of documents, and at most one `*.json` of
content digests. Anything else in the archive, a member reached through a
path, or a name beginning with `-`, is refused. Two files of the same
role are refused as well — the importer never guesses which of two dumps
(or two digest documents) describes the clinic. If the content digests
were shipped separately rather than in the bundle, pass them to the
importer with `--o19-digests PATH`. The bundle file itself must be named `.tar`, `.tar.gz`,
`.tar.enc` or `.tar.gz.enc`.

```bash
sha256sum o19-bundle.tar.gz.enc
```

Two different things are called a digest here, and only one of them
travels out of band:

* the **bundle SHA-256** — the `sha256sum` above, of the encrypted file.
  It proves the bundle arrived unaltered, so it must reach the CARLOS
  operator through a channel **separate** from the file itself (it is
  passed to the importer as `--bundle-sha256`).
* the **content digests** — `o19-digests.json`, per-table hashes of the
  clinic's data. They prove the dump carried every value, and they ride
  *inside* the bundle like the other inputs; sending them separately buys
  nothing, because they share the file's own channel either way.

Ship the bundle to the CARLOS host; send the password **and the bundle
SHA-256** through a separate channel. `openssl enc` provides
confidentiality only (no integrity check): that SHA-256, conveyed apart
from the file, is what proves the bundle arrived unaltered, and the
importer refuses to open a
bundle whose SHA-256 does not match.

## 3. Import on the CARLOS host

Prerequisites, all of them before the command below:

1. A **fresh install** of the carlos-emr package with the schema migrated
   (`carlos-ctl db-migrate`) and **never used clinically**. The importer
   verifies this mechanically and refuses anything else; there is no
   override, and the way back is
   `carlos-ctl destroy-data --confirm <server name>` followed by
   `carlos-ctl db-users` and `carlos-ctl db-migrate`.
2. **Do not log in to that deploy.** Verify it with `carlos-ctl check`.
   A successful login writes rows the sweep tolerates in the audit table
   only; anything else a session touches is a refusal.
3. **Stop the service**: `carlos-ctl stop`. A real run or a `--resume` is
   refused while `carlos-emr` is running *or starting* — its startup
   listener writes rows the row-parity gate rejects, and a live session
   could read a half-copied chart.
4. **Configured backups.** The pre-import restic snapshot is the rollback
   point, and it now covers `/var/lib/carlos-emr/o19-import` as well as
   the database and the documents tree, so a restore rewinds the run's
   ledgers with the data they describe. The break-glass credential note
   is deliberately excluded.
5. **Keep the bundle, its passfile and the bundle SHA-256** until
   `--cleanup`.
   Every `--resume` repeats the whole command with `--resume` appended,
   and re-reads the bundle.

Only one import may work in `/var/lib/carlos-emr/o19-import` at a time;
a second invocation is refused and names the pid of the one holding it.

```bash
sudo carlos-ctl import-o19 \
    --bundle /srv/migration/o19-bundle.tar.gz.enc \
    --bundle-sha256 <the SHA-256 the clinic conveyed separately> \
    --bundle-pass file:/srv/migration/passfile \
    --admin-user <break-glass-admin-name> \
    [--accept CLASS ...]        # the sign-offs preflight listed
```

`--admin-user` names the break-glass administrator created before the
seeded clinician is removed. It must start with a letter or a digit and
run to at most 30 characters of letters, digits, `_`, `.`, `@` or `-`; it may not be the seeded `carlosdoc`; and it
may not collide with a login the dump already carries.

The host needs, before the run: roughly 2.5 times the **uncompressed** dump
free on the server's data directory (staging restore, the copy into the
target and the archive schema) — the import asks the server for
`@@datadir` rather than assuming, because MariaDB on Ubuntu 26.04, the
platform this package ships against, uses `/var/lib/mariadb` and not the
older `/var/lib/mysql`, and twice the bundle plus twice the expanded documents tar
free on `/var/lib/carlos-emr`. MariaDB must be 10.5 or newer, because the
restore runs under a schema-scoped account that needs `BINLOG ADMIN`, and
the server must have no replicas attached: the import's binlog-off bulk copy
is not replica-safe, and a server with replicas is refused.

Every blocker is cleared by one explicit `--accept` class, recorded in the
report as the clinic's sign-off. There are twelve:

| class | what it acknowledges |
|---|---|
| `archived-forms` | patient data in removed-module tables is preserved but has no UI |
| `unknown-as-archive` | tables and columns the manifest does not know are preserved whole, not migrated |
| `dropped-columns` | columns CARLOS has no home for held data (preserved as `import_archived_<column>`) |
| `charset-repair` | double-encoded text is repaired row by row during the copy |
| `olis-gone` | OLIS was in use; CARLOS has no OLIS module |
| `no-documents` | import without the documents tree (with `--skip-documents`) |
| `no-pre-backup` | no pre-import snapshot, or the backup unit failed |
| `unverified-bundle` | open a bundle whose SHA-256 was never conveyed |
| `carry-credentials` | live OAuth secrets and signing keys are copied verbatim |
| `content-transfer` | the restored staging schema does not match the content digests the clinic took before the dump |
| `no-content-digests` | the transfer's content could not be fully verified (usually: no content digests were shipped) |
| `content-migration` | a preserved copy holds the right number of rows but not the same values |

The assessment can evaluate only six of them (`archived-forms`,
`unknown-as-archive`, `olis-gone`, `dropped-columns`, `carry-credentials`,
`charset-repair`) plus `unverified-bundle`; the rest belong to phases it
never runs.

Phases (state under `/var/lib/carlos-emr/o19-import/`): stock-deploy gate
→ pre-import backup → staged restore → preflight → data copy with
row-parity gate → documents restore with blocking reconciliation →
properties translation → verify. The backup runs before the staged
restore so the rollback point exists before any clinic-supplied SQL
executes. The restore itself runs as a throwaway database account whose
grants stop at the `o19_import` schema, with the client's `--one-database`
switch on, and a dump carrying `USE` / `CREATE DATABASE` statements (a
`mysqldump --databases` dump) is refused: the head of the stream is
checked before the client starts, and the rest is scanned as it is fed,
with the staging schema dropped on a hit. The account's grants and
`--one-database` are the backstops, so nothing in the dump can address the
live schema either way. A rerun over existing
state requires `--resume` (a staged dump left behind by a dry run or an
assessment does not count); it is never continued implicitly. Once the
data copy has started, a resumed run re-checks the schema, replica and
disk gates but not the emptiness sweep (the target is mid-import by
design) — the row-parity gate still verifies the outcome. `--restage`
drops and re-restores the staging schema and clears the recorded preflight
verdict with it.

Clinic-defined lookup lists, waiting-list criteria and similar merge-class
rows may receive new ids where a CARLOS seed already holds the old one;
their dependent rows are remapped through `o19_archive.<table>__idmap`, and
the report itemizes every table whose ids changed. Nothing is dropped, whether the
manifest knows it or not. A table CARLOS has no home for — `archive`,
removed-module (`drop`), or a name the manifest has never seen — is
preserved twice: `o19_archive.<table>` and
`<emr-schema>.import_archived_<table>`. A column CARLOS has no home for —
curated as dropped, or added by a clinic's own fork — joins the live
table as `import_archived_<column>` with the source type and every row,
and is shadow-captured to `o19_archive` as well. Tables the manifest
classifies `reference` keep the CARLOS seed in the live table; the
clinic's rows go to `o19_archive.<table>`, where a locally curated code
can still be found. The verification counts all of it before it passes,
and then digests it: every preserved copy must hold the same **values**
as staging, not merely the same number of rows (a mismatch is a blocker
cleared only by `--accept content-migration`). The preflight sweeps the
archive-class, patient-data and removed-module tables, the last as an
advisory naming each one that holds rows.

What the import does with credentials: every clinic login keeps working
(legacy password hashes upgrade to bcrypt on first login) but **all users
get a forced password reset**; the seeded `carlosdoc` account is removed
after the break-glass administrator named by `--admin-user` is created
(credentials in `/var/lib/carlos-emr/o19-import/admin-credentials.txt`,
root-only).

Roles and privileges: CARLOS checks privileges exactly (no parent fallback)
and counts only active role assignments, so the import merges the clinic's
role matrix under CARLOS's seeded grants (CARLOS wins on the same
role/object, clinic rows append), gives clinic-custom roles that hold at
least one grant the CARLOS-era privileges of the closest stock role
(`--role-template 'Custom Role=doctor'` overrides the choice, repeatable,
case-insensitive; once the backfill has decided on it the mapping is
recorded and a `--resume` continues with it — a different one is refused;
before that a resume may add or correct the flag), activates
`activeyn`-NULL assignments of live accounts — except `admin` assignments,
which CARLOS deliberately treats as inactive when NULL: those stay dormant
and are listed — and creates the program membership and facility link
every **active** provider lacks. Exceptions the report states explicitly: a
custom role no stock role resembles (similarity below 0.3) gets **no**
CARLOS-era grants and is listed for manual grants in Administration; a
template that holds none of the CARLOS-era objects (every nurse- or
receptionist-class stock role — the seed grants them to doctor/admin only)
adds nothing; a provider with no active role gets a membership with the
least-privileged clinic role and is listed in `roles-details.txt`. The
`roles:` lines of the report say what was done; `privilege-diff.txt`
(root-only) itemises the clinic grants the CARLOS seed overrode, the seed
grants on the clinic's roles that the clinic's own matrix did not hold (a
removed grant comes back), the clinic grants on stock roles the seed does
not hold (appended; those on `_admin*` objects are named in the report),
and the grants on objects CARLOS no longer checks (not carried);
`roles-details.txt` names the providers whose assignments were activated
(provider = role), whose dormant admin rows were left alone, who received
the fallback membership, who hold an assignment to a re-added CARLOS-only
role, who hold no role, and whose logins are expired. Prevention type codes
the importer knows (`Flu`, `VZ`, …) are
normalised to the Health Canada codes with an exact, case-sensitive match;
codes it cannot map stay as they are and are listed for review (they render
as unconfigured). The Rich Text Letter eForm is brought to 2026.3.0 from the
packaged scripts under `/usr/share/carlos-emr/schema/o19-fixups/` when the
clinic's row is the stock one (`form_name` `Rich Text Letter`, subject
starting `Rich Text Letter Generator`, as the database compares them); the
step re-reads the row afterwards and reports "modernised" only when it is,
otherwise it says what to apply by hand (an edited subject). A form that
was disabled stays disabled; a clinic with no stock row gets a new, enabled
Rich Text Letter. The per-run files are retired with the same
`.completed-<timestamp>` suffix as `state.json` when the import is cleaned
up, so a later import in the same directory starts its own: `report.txt`,
`import-report.txt`, `import-report.json`, `etl-progress.json`,
`preflight.txt`, `preflight.json`, `o19-digests.json` (the run's own
0600 copy of the clinic's content digests — P2 measures the copy it took,
never the operator's file, and the ledger binds its sha256 so a resume
cannot substitute another), `content-transfer.json` (the P2 comparison of
those digests against the restored staging schema), the `*-details.txt`
files, `privilege-diff.txt`, `o19-archive-export/`,
`o19-derived-carlos.properties` and its dry-run twin
`o19-derived-carlos.properties.dry-run`. `admin-credentials.txt` is deliberately not among them; a
previous run's copy is set aside as `admin-credentials.txt.previous-<stamp>`
rather than overwritten.

On the CARLOS host the `carlos-emr` service must be stopped for the
whole import (`carlos-ctl stop`); a real run or `--resume` refuses while it
is active, because CARLOS's startup listener writes rows (program, site,
memberships) that the row-parity gate would then reject, and a session could
read a half-copied chart. Start it again only after the verified import and
the properties fragment have been applied.

Useful variants: `--dry-run` (stage + preflight + properties report only;
it still runs the P0 gates — a non-stock target is refused — and it is
refused over a workspace whose import is in progress, because it would
re-extract the bundle and rewrite the recorded inputs; its `--accept` flags
are not recorded — sign-offs persist only from a real run),
`--dump/--documents/--properties` instead of a bundle,
`--bundle-openssl-opt` for bundles encrypted by an older openssl
(`-md md5`, no `-pbkdf2`) — note that any `--bundle-openssl-opt` **replaces**
the default `-pbkdf2 -iter 200000` rather than adding to it, so pass the
creator's complete derivation options — `--skip-documents` with `--accept no-documents`,
`--accept unverified-bundle` to open a bundle whose SHA-256 was never
conveyed (a recorded sign-off, never a default), `--accept
carry-credentials` when the dump holds live OAuth consumer secrets or
signing keys (`ServiceClient`, `oscarKeys`, `publicKeys` rows are copied
verbatim and keep working against the migrated system — the preflight
reports it as blocker B9 and the ETL pre-checks refuse without the
sign-off; rotate or verify them before go-live), and
`--statement-timeout SECONDS` to bound every SQL statement of the import,
the staged restore's own client included (MariaDB `max_statement_time`; a
sparse or crafted dump cannot then hold one statement forever; 0, the
default, means no bound). `--bundle-cipher NAME` names the openssl cipher an
`.enc` bundle was made with (default `aes-256-cbc`), and `--province` may
only restate the host's configured province.

Unlike the assessment, `import-o19` has no verdict codes: it exits 0 when the
import completed and 1 on any refusal or failure.

`carlos-ctl o19-preflight` is the assessment-only form on the CARLOS host:
capacity checks, staged restore and the go/no-go report, with the exit
code as the verdict (0 go, 1 acknowledgements required, 2 no-go) and 3 for
any failure of the tool itself (an unreachable server, bad flags, a refused
dump, insufficient disk — never a verdict code); it records no verdict and
no sign-off, and refuses a workspace whose import is in progress.
`--province` may only restate the host's configured province: a value that
differs from `/etc/carlos-emr/carlos-emr.env` is refused before anything is
staged. On a host configured for BC the assessment stages first and then
reports the province as a no-go.

Invoke it the same way as the import, minus the writing flags:

```bash
sudo carlos-ctl o19-preflight \
    --bundle /srv/migration/o19-bundle.tar.gz.enc \
    --bundle-sha256 <SHA-256> --bundle-pass file:/srv/migration/passfile
```

(or `--dump` plus `--properties` instead of a bundle). It accepts only the
blocker classes it can evaluate — `archived-forms`, `unknown-as-archive`,
`olis-gone`, `dropped-columns`, `carry-credentials`, `charset-repair` —
plus
`unverified-bundle` for its own bundle intake; the phase sign-offs belong to
phases it never runs, and nothing it is passed is recorded.

`--fixups-dir` overrides where the packaged Rich Text Letter fixup
scripts are read from; it exists so the checked-out tree can be exercised
without installing the package, and a packaged host has no reason to pass
it.

`--dev-target` and `--mariadb-arg` exist for development databases only
(the devcontainer, where the database is a separate container reached over
TCP and carries the demo dataset): `--dev-target` needs `--mariadb-arg`,
and both are refused outright on a packaged host, where the stock-deploy
gate has no override.

## 4. After the import

1. **Properties**: review
   `/var/lib/carlos-emr/o19-import/o19-derived-carlos.properties` (0600;
   contains imported credentials — rotate/verify them), append the approved
   lines to `/etc/carlos-emr/carlos.properties`, then `carlos-ctl restart`.
   This step is deliberately never automatic.
2. `carlos-ctl backup full` — the post-import snapshot.
3. **Technical review before clinical use**: start at
   `/var/lib/carlos-emr/o19-import/import-report.txt` — the validation
   report, written at the end of verification and built for exactly this
   review. It carries a header (manifest, dump digest, target schema,
   timestamps), a verdict line, **what arrived** (per-table row counts for
   every class: copy tables to the row with the break-glass delta itemized,
   merge tables in reverse — every staging row has a target twin —,
   preserved tables counted in every home they have (three for an
   archived, removed-module or unclassified table; two for a reference
   or merge table, which get no `import_archived_` twin — a merge
   table's eligible rows land in the live CARLOS table itself, while
   rows the manifest lists in `merge_exclude` stay archive-only),
   preserved columns counted by
   non-null value), **what did not arrive and where it went instead**
   (absent tables, reference tables CARLOS's own data won, removed-module
   and unclassified tables, preserved columns), then findings ordered by
   severity and the remaining operator steps.
   `import-report.json` is the same content machine-readable, for diffing
   two imports or feeding a checklist. `report.txt` next to it is the
   running phase log — chronological, and the place to look for what a
   given phase did. Add manual spot checks and a UI smoke of the migrated
   charts. The report is written to be shareable; the
   per-patient lines of the spot check (which name patient identifiers) go
   to `verify-details.txt` next to it, root-only, as do `privilege-diff.txt`
   and `roles-details.txt`. Confirm each clinic-custom role's privileges in
   Administration > Security (the report names the template role used),
   and deal with expired or role-less accounts before go-live.
4. `carlos-ctl import-o19 --cleanup` — drops the staging schema and the
   throwaway staging account, removes the extracted bundle and retires the
   run's `state.json`, ETL ledger, report, private files and the properties
   fragment (renamed with one `.completed-<timestamp>` suffix, so the finished
   run can neither be resumed nor mistaken for a fresh one; only
   `admin-credentials.txt` stays under its own name); the `o19_archive` schema
   (the verification copies: preserved tables under their own names, the
   dropped- and unknown-column shadows, the id maps, and the snapshot of
   CARLOS's own privilege seed) is kept for the clinic, as are the
   `import_archived_` tables and columns in the EMR schema, and so is
   its CSV export — but the export directory is retired with the rest of the
   run, so after `--cleanup` collect it from
   `/var/lib/carlos-emr/o19-import/o19-archive-export.completed-<timestamp>/`
   (root-only), not the unsuffixed path. It is renamed rather than kept in
   place so a second clinic's import into the same workspace cannot leave
   the first clinic's tables sitting beside its own; copy it out
   deliberately when handing it over.
   Cleanup is allowed after a passed verification, or while nothing has
   been written to the target (after a dry run or an aborted assessment) —
   never on a mid-import workspace, whose only resume ledger it would
   destroy, and never while any staging table still holds rows with no
   verified copy outside it (the refusal names the tables). That second
   check is asked of the *data* rather than of the run, so `--dev-target`
   does not waive it.

## What is preserved rather than migrated

CARLOS has no home for some of what an OSCAR 19 database holds: modules it
removed, tables a clinic's own fork added, columns that no longer exist.
None of it is discarded. Every such table and column is preserved in two
places, and the verification counts both before it passes — then compares
each copy against staging by content digest, so a copy with the right
number of rows but the wrong values fails too:

| what | where it lands |
|---|---|
| a table CARLOS does not have (removed module, clinic fork) | `o19_archive.<table>` **and** `<emr-schema>.import_archived_<table>` |
| a reference table CARLOS seeds itself | `o19_archive.<table>` — CARLOS's own rows win in the live table |
| a merge table (CARLOS seeds it, the clinic also has rows) | `o19_archive.<table>` — every clinic row, including the ones a CARLOS seed row overrode |
| a column CARLOS does not have (dropped or clinic fork) | `<emr-schema>.<table>.import_archived_<column>`, source type and every row, plus an `o19_archive` shadow capture |

A merge is the one place where a *live* row can be a CARLOS row rather
than the clinic's. Merge policy is that CARLOS's seed wins on a shared
natural key — the clinic's row on that key is never inserted, so its
other columns (an edited encounter template, a local fee on a seeded
billing code, a customised measuring instruction) do not become live.
That is deliberate, and it is not the same thing as discarding them: the
whole staging table goes to `o19_archive.<table>`, the report names how
many rows the seed overrode on each table, and `--cleanup` will not drop
staging until that copy has been counted. There is no
`import_archived_` twin for a merge table, for the same reason a
reference table has none — the live table is not missing, it is holding
CARLOS's rows.

The two homes answer different needs. `o19_archive` is the verification
copy and the source of the CSV export the clinic is handed; it is dropped
by hand once they hold that export, and the nightly `carlos-emr-backup`
does not dump it. The `import_archived_` objects live in the EMR schema,
so they *are* in that backup, they survive `--cleanup`, and they are
reachable with an ordinary SQL query a year later. Nothing in the
application reads either: no UI, no report, no API. `carlos-ctl
destroy-data` drops the EMR schema and takes the `import_archived_`
objects with it.

### What this means for Flyway

The import writes into the schema Flyway owns, so the contract is worth
stating exactly:

- **Flyway's history is untouched.** No migration is added, removed or
  re-checksummed, so `flyway validate` — and the application's boot gate
  (`carlos.flyway.onBoot=validate`), which is that same check — pass
  after an import exactly as before. Flyway compares its history table
  against the migration files; it does not diff the schema, so tables and
  columns the import adds are invisible to it.
- **Forward migrations keep applying.** `import_archived_` names cannot
  collide with a CARLOS column or table name, and the columns are added
  last, nullable and without a default.
- **One rule this places on future migrations: name your columns.** A
  column-less `INSERT INTO t VALUES (…)` binds by position, so it fails
  with `ER_WRONG_VALUE_COUNT_ON_ROW` against a table the import widened —
  aborting the migration, leaving a failed row in
  `flyway_schema_history`, and (under the boot gate) stopping the
  application until someone runs `flyway repair`. Thirteen tables the
  genesis files seed positionally are in the manifest's curated set alone
  (`security`, `property`, `secRole`, `Facility`, `ProviderPreference`,
  the `lst_*` lookups …), and a clinic's own fork can widen any copied
  table. `MigrationColumnListContractUnitTest` fails the build on a new
  migration that inserts positionally; the genesis and restore files are
  grandfathered, because every deployment applies them long before an
  import can widen anything.
- **Row width is checked before the first write.** MySQL refuses an
  `ALTER TABLE … ADD COLUMN` that would push a row past 65,535 declared
  bytes, and it refuses it mid-import. The manifest's curated columns
  leave every CARLOS table under half that ceiling (the widest,
  `formLabReq07`, reaches about 17 KB of the 64 KB), but a fork's
  columns are
  unbounded, so the ETL measures and refuses up front rather than failing
  part-way.
- **A migration that drops and recreates a table** would take its
  `import_archived_` columns with it. That is why `o19_archive` keeps its
  own copy of everything preserved: the live columns are the convenient
  copy, not the only one.

Archive-only data has no UI even where its table looks familiar:
deprecated antenatal forms (ONAR/AR), generic intake, OCAN, eyeform, drug
dispensing records, CAISI beds/rooms, PHR copies, Integrator consents,
report-runner templates, OLIS preferences. The preflight report enumerates
exactly which of these hold rows for a given clinic — that list is the
clinic's sign-off.

## Troubleshooting

- *"the import runs ONLY on a stock initial deploy"* — the target holds
  demo or clinical rows. Reprovision a fresh schema; no flag overrides this.
- *"decryption failed"* on a bundle — wrong password, or the bundle was
  made by an older openssl: match its derivation, e.g.
  `--bundle-openssl-opt -md --bundle-openssl-opt md5`.
- *"the dump carries a USE / CREATE DATABASE statement"* — it was taken
  with `--databases` or `--all-databases`. Re-take it as
  `mysqldump <o19-db> > o19.sql` (see §2); the importer never lets a dump
  choose its own schema.
- *"restore into o19_import failed"* mentioning DEFINER clauses, GRANTs or
  server-wide SET statements — the restore runs as an account limited to the staging
  schema. Re-take the dump with `--skip-triggers --set-gtid-purged=OFF`
  (and without `--databases`); OSCAR 19 keeps nothing in triggers or views.
- *documents reconciliation FAILED* — a `document` row's file is missing,
  empty, named with a subdirectory or with a leading dot (CARLOS opens the
  basename only and refuses dot-leading names), an eForm references an
  image asset that is not there, or an HRM report is missing; the names
  are in `documents-details.txt` (root-only), the console and the report
  carry counts. Fix the tree **in place** under
  `/var/lib/carlos-emr/OscarDocument/carlos/` and `--resume`; the import
  never goes live with unreadable documents. Restoring a *different* tar
  is not a recovery path — the phase records the tar it restored and
  refuses another one; that needs the pre-import snapshot first.
  An eForm image reference that escapes `eform/images` is blocking too.
  Separately reported, not blocking: references CARLOS cannot route to
  because the form names a subdirectory or carries a query suffix while
  the asset itself is present. No tar can fix those — each needs
  `eform.form_html` edited in the target after go-live. HRM report files are moved from the whole `hrm/`
  tree (O19 nests them under `hrm/sftp_downloads/<date>/decrypted/`) into
  `document/` and every `HRMDocument.reportFile` is rewritten to its
  basename there; identical copies of one name are folded, differing
  copies of one name (or one name reached through two paths) are refused
  with the names listed privately.
- *the previous restore of the documents tar was interrupted* — a crash
  mid-merge; the same tar re-extracts and files already in place are
  verified rather than replaced, so a plain `--resume` completes it. Only
  a file whose content differs from the tar stops it.
- *--skip-documents cannot retire it* — the tree was already restored from
  a tar in this run; resume with that tar (reconciliation is what is left).
- *--resume: no import is recorded* — the workspace holds nothing beyond a
  staged dump (what a dry run or assessment leaves); run the import without
  `--resume`. The flag never starts a fresh import.
- *insufficient disk under … to open the bundle* — the state volume takes a
  private copy of the bundle (and its decrypted form, for an encrypted one)
  before the members can be sized; free space there and re-run.
- *ETL pre-checks failed: NOT NULL target column(s)* — the CARLOS schema
  gained a required column the manifest doesn't cover yet; report it (the
  fix is a `value_exprs` curation entry + regenerated manifest).
- *ETL pre-checks failed: the dump has no enabled Facility row* / *no
  `clinic` row* / *the dump has no X table — not an OSCAR 19 clinic dump*
  — nothing was written. CARLOS cannot log anyone in without an enabled
  `Facility`, letterheads and requisitions dereference the `clinic` row,
  and the roles step reads a fixed set of core tables; enable or create
  them on the OSCAR 19 side and re-export (preflight reports the Facility
  and clinic conditions as blockers).
- *ETL pre-checks failed: the server's sql_mode carries
  NO_BACKSLASH_ESCAPES* (or `ANSI_QUOTES`) — the import quotes clinic
  values with backslash escapes and refuses to run under those modes;
  clear them in the server configuration for the import.
- *ETL pre-checks failed: the staged dump carries N table/column name(s)
  outside the accepted identifier class* — a table or column whose name
  is not plain `[A-Za-z0-9_$]` (no OSCAR 19 schema has one); every
  statement runs as the database root, so such a name is refused rather
  than quoted. Rename it in the source and re-export.
- *manifest column(s) not in the target schema* — the installed package's
  manifest names a column the deployed Flyway level lacks; report it.
- *ETL pre-checks failed (nothing was written)* — the copy refused before
  touching the target. On a `--resume` whose copy had already started the
  same message reads *(no further writes were made)*: the earlier phases'
  writes stand. Either way, fix the condition and `--resume`.
- *ETL pre-checks failed: … value(s) longer than the target column* — the
  clinic's column is wider than CARLOS's. The import refuses to truncate
  clinical text; shorten the values on the OSCAR 19 side, or report the
  column so the manifest can carry it.
- *double-encoded text detected in: …* — the clinic's OSCAR 19 stored latin1
  bytes as UTF-8 (classic mojibake). Re-run with `--accept charset-repair`
  to apply the per-row latin1 to utf8mb4 repair during the copy. The
  preflight reports it as a BLOCKER, not an advisory: the ETL refuses
  without the flag, and it refuses at P4 — after the pre-import snapshot
  and the whole staging restore — so an assessment that called this
  advisory would cost the clinic a cutover window.
- *B8: text that looks double-encoded but does not round-trip …* — thrice
  encoded or otherwise unrepairable text. No flag overrides this; the rows
  need manual investigation on the OSCAR 19 side.
- *the dump uses collation(s) unavailable on this server* — the OSCAR 19
  server had collations this MariaDB lacks. Re-take the dump on a server
  whose collations match, or install them.
- *the dump has no '-- Dump completed' trailer* — it is truncated or the
  mysqldump was interrupted. Take a fresh one.
- *the dump carries SET @@GLOBAL.GTID_PURGED* — a MySQL 5.6+ GTID directive
  MariaDB rejects. Re-take it with `mysqldump --set-gtid-purged=OFF`.
- *this database server has replicas attached* — the binlog-off bulk copy is
  not replica-safe. Detach them for the duration of the import.
- *cannot grant BINLOG ADMIN to the staging account* — the server is older
  than MariaDB 10.5; the schema-scoped restore account needs that privilege.
  Once the copy has started a resume does not re-run the preflight either
  (its verdict was recorded before the first write).
- *the dump carries live credentials* — `ServiceClient` / `oscarKeys` /
  `publicKeys` rows; acknowledge with `--accept carry-credentials` (the
  preflight lists it as B9) and rotate or verify them before go-live.
- *non-numeric value(s) in a column CARLOS stores as a number* — the copy
  would store 0 for them under the import's `sql_mode=''`; curate a
  `value_exprs` entry or fix the values in the source.
- *province 'bc': the OSCAR 19 import supports Ontario deployments only* —
  P0 refuses a non-Ontario host before sweeping it (the seed floors are
  generated from the Ontario migration set).
- *`--role-template 'X': not a clinic-custom role with imported grants`* /
  *`'Y' is not a CARLOS stock role`* — on a fresh run this surfaces as
  *ETL pre-checks failed (nothing was written): --role-template …*, on a
  resume as *roles: --role-template …*; either way the flag names a role
  the dump does not have, a stock role name (those need no template), a
  role with no grants, or an unknown template. Fix the flag and `--resume`;
  nothing was written for that step and the bad mapping was not recorded.
  A custom role whose closest stock role is `admin` with a similarity
  below 0.5 is *held for the operator*: pass `--role-template 'X=admin'`
  to grant the administrator objects, or grant them by hand. Once the
  backfill has decided, a `--resume` that passes a *different* mapping is
  refused; pass the same mapping or none.
- *the staging schema o19_import already holds rows, and this workspace
  has no record of staging the dump offered now* — P1 will not drop a
  populated staging schema it cannot attribute: those rows may be the
  only copy of a dump whose file the operator has since deleted. Export
  or drop `o19_import` yourself if they are finished with, or pass
  `--restage` to say so. (`--restage` is also refused once the ETL has
  copied — at that point re-restoring staging would contradict the
  target.)
- *the target schema … already holds N table(s) preserved by a previous
  import* — the second arm of the inherited-import refusal. The first
  arm catches an `o19_archive` schema left by an earlier run; this one
  catches the `import_archived_` tables that survive `--cleanup` by
  design and live in the EMR schema. Both mean a previous clinic's data
  is still here: finish with it (`carlos-ctl destroy-data`, or drop the
  named objects) before importing a second clinic.
- *roles: Rich Text Letter fixup script(s) missing* — the package is
  incomplete (they ship under `/usr/share/carlos-emr/schema/o19-fixups/`);
  reinstall `carlos-emr` and `--resume`. *scripts ran but no row carries
  the 2026.3.0 marker* — not fatal: the import completes and P7 lists it
  as an advisory; apply the scripts by hand (they address the row named
  `Rich Text Letter` with a subject starting `Rich Text Letter Generator`;
  a clinic-edited subject needs a manual edit first).
- *this import completed under manifest X … run --cleanup* — a package
  upgrade after a finished import; there is nothing to resume, retire the
  run with `--cleanup`. *A finished ETL cannot be continued under a
  different manifest* — the upgrade landed mid-run; the lossless path is to
  reinstall the package version that shipped the recorded manifest,
  `--resume`, `--cleanup`, then upgrade.
- *the archive schema o19_archive of a previous import exists* — a fresh
  run on a host that imported a clinic before; `--cleanup` that run (or
  drop the schema once the clinic holds its CSV export) first, or the old
  archive tables would be exported into the new clinic's document tree.
- *carlos-emr is running — stop it for the duration of the import* — see
  §3; stop the service and re-run (or `--resume`).
- *a previous --cleanup was interrupted — run --cleanup again* — cleanup
  marks itself before dropping anything; repeat it.
- *cannot read state.json / the ETL ledger* — a corrupt ledger is fatal on
  purpose (a "fresh" reading would re-sweep a mid-import target); restore
  the pre-import snapshot if an import was in progress.
- *P5 reconciliation FAILURES* — the offending document names are in
  `documents-details.txt` (root-only); the report and the console carry
  the count. The batch client prints SQL NULL as the word `NULL`; the
  archive CSV export tells it from a stored `NULL` string by a companion
  flag, so an empty CSV field is a real NULL.
- A failed run keeps its state for diagnosis; the documented rollback is
  restoring the pre-import restic snapshot.
