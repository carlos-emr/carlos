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
    --properties /path/to/oscar.properties --json preflight.json
```

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
archive-only (OLIS, eForm modules that were removed, antenatal ONAR forms),
and what must be decided before cutover.

## 2. Produce the three inputs on the OSCAR 19 server

During the cutover window, with Tomcat stopped:

```bash
mysqldump --single-transaction --quick oscar | gzip > o19.sql.gz
# one database, named as an argument: never --databases/--all-databases
# (the importer refuses a dump that names its own schema); on MySQL 5.6+
# add --set-gtid-purged=OFF
tar -C /var/lib/OscarDocument -czf o19-documents.tar.gz <context-dir>
# context-dir is the directory holding document/, eform/images/, ... —
# often oscar, oscar_mcmaster, or the database name
cp /path/to/oscar.properties .
```

Bundle them (recommended single-file handoff, encrypted):

```bash
tar -czf - o19.sql.gz o19-documents.tar.gz oscar.properties \
  | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
      -pass file:PASSFILE -out o19-bundle.tar.gz.enc
```

```bash
sha256sum o19-bundle.tar.gz.enc
```

Ship the bundle to the CARLOS host; send the password **and the digest**
through a separate channel. `openssl enc` provides confidentiality only
(no integrity check): the digest, conveyed apart from the file, is what
proves the bundle arrived unaltered, and the importer refuses to open a
bundle whose digest does not match.

## 3. Import on the CARLOS host

Prerequisites: a **fresh install** of the carlos-emr package — schema
migrated (`carlos-ctl db-migrate`), never used clinically (the importer
verifies this mechanically and refuses anything else; the only remedy is a
fresh schema, there is no override) — and configured backups
(the pre-import restic snapshot is the rollback point).

```bash
sudo carlos-ctl import-o19 \
    --bundle /srv/migration/o19-bundle.tar.gz.enc \
    --bundle-sha256 <digest the clinic conveyed separately> \
    --bundle-pass file:/srv/migration/passfile \
    --admin-user <break-glass-admin-name> \
    [--accept CLASS ...]        # the sign-offs preflight listed
```

Phases (state under `/var/lib/carlos-emr/o19-import/`): stock-deploy gate
→ pre-import backup → staged restore → preflight → data copy with
row-parity gate → documents restore with blocking reconciliation →
properties translation → verify. The backup runs before the staged
restore so the rollback point exists before any clinic-supplied SQL
executes. The restore itself runs as a throwaway database account whose
grants stop at the `o19_import` schema, with the client's `--one-database`
switch on, and a dump carrying `USE` / `CREATE DATABASE` statements (a
`mysqldump --databases` dump) is refused before a byte reaches the server:
nothing in the dump can address the live schema. A rerun over existing
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
the report itemizes every table whose ids changed. Tables and columns the
manifest does not know are never dropped: whole tables are archived under
`o19_archive`, and unmapped columns of copied or merged tables are
shadow-captured as `<table>__unknown_cols`. Tables the manifest classifies
`reference` keep the CARLOS seed (their O19 rows, every column, are not
copied), `archive` tables are archived whole, and `drop` tables are
report-only by declaration — the preflight and ETL reports name each one
that holds rows.

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
Rich Text Letter. The per-run files (`report.txt`, the `*-details.txt`
files, `privilege-diff.txt`, the preflight outputs) are retired with the
same `.completed-<timestamp>` suffix as `state.json` when the import is
cleaned up, so a later import in the same directory starts its own.

Useful variants: `--dry-run` (stage + preflight + properties report only;
its `--accept` flags are not recorded — sign-offs persist only from a real
run), `--dump/--documents/--properties` instead of a bundle,
`--bundle-openssl-opt` for bundles encrypted by an older openssl
(`-md md5`, no `-pbkdf2`), `--skip-documents` with `--accept no-documents`,
`--accept unverified-bundle` to open a bundle whose digest was never
conveyed (a recorded sign-off, never a default).

`carlos-ctl o19-preflight` is the assessment-only form on the CARLOS host:
capacity checks, staged restore and the go/no-go report, with the exit
code as the verdict (0 go, 1 acknowledgements required, 2 no-go, 3 tool
error); it records no verdict and no sign-off.

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
3. **Technical review before clinical use**:
   `/var/lib/carlos-emr/o19-import/report.txt` (row parity with the
   break-glass delta itemized, referential spot checks, billing totals per
   fiscal year, documents reconciliation, archive/dropped inventory, the
   credential tables copied verbatim to rotate/verify, the `roles:` lines
   and the verify advisories), plus manual spot checks and a UI smoke of
   the migrated charts. The report is written to be shareable; the
   per-patient lines of the spot check (which name patient identifiers) go
   to `verify-details.txt` next to it, root-only, as do `privilege-diff.txt`
   and `roles-details.txt`. Confirm each clinic-custom role's privileges in
   Administration > Security (the report names the template role used),
   and deal with expired or role-less accounts before go-live.
4. `carlos-ctl import-o19 --cleanup` — drops the staging schema and the
   extracted bundle and retires the run's `state.json` (renamed to
   `state.json.completed-<time>`, so the finished run can neither be
   resumed nor mistaken for a fresh one); the `o19_archive` schema
   (removed-module data + dropped-column shadows + the OSCAR 19 token
   tables, which are never copied live) and its CSV export under
   `OscarDocument/carlos/o19_archive_export/` are kept for the clinic.
   Cleanup is allowed after a passed verification, or while nothing has
   been written to the target (after a dry run or an aborted assessment) —
   never on a mid-import workspace, whose only resume ledger it would
   destroy.

## What is archive-only after migration

Data from modules CARLOS removed is preserved in the `o19_archive` schema
and as CSV, but has no UI: deprecated antenatal forms (ONAR/AR), generic
intake, OCAN, eyeform, drug dispensing records, CAISI beds/rooms, PHR
copies, Integrator consents, report-runner templates, OLIS preferences.
The preflight report enumerates exactly which of these hold rows for a
given clinic — that list is the clinic's sign-off.

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
- *"restore into o19_import failed"* mentioning DEFINER, SUPER or a
  server-wide `SET` — the restore runs as an account limited to the staging
  schema. Re-take the dump with `--skip-triggers --set-gtid-purged=OFF`
  (and without `--databases`); OSCAR 19 keeps nothing in triggers or views.
- *documents reconciliation FAILED* — a `document` row's file is missing or
  empty in the tar. Fix the tree (or re-ship the tar) and `--resume`; the
  import never goes live with unreadable documents.
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
- *province 'bc': the OSCAR 19 import supports Ontario deployments only* —
  P0 refuses a non-Ontario host before sweeping it (the seed floors are
  generated from the Ontario migration set).
- *`--role-template 'X': not a clinic-custom role with imported grants`* /
  *`'Y' is not a CARLOS stock role`* — the flag names a role the dump does
  not have, a stock role name (those need no template), a role with no
  grants, or an unknown template. Fix the flag and `--resume`; nothing was
  written for that step and the bad mapping was not recorded. Once the
  backfill has decided, a `--resume` that passes a *different* mapping is
  refused; pass the same mapping or none.
- *roles: Rich Text Letter fixup script(s) missing* — the package is
  incomplete (they ship under `/usr/share/carlos-emr/schema/o19-fixups/`);
  reinstall `carlos-emr` and `--resume`. *scripts ran but no row carries
  the 2026.3.0 marker* — not fatal: the import completes and P7 lists it
  as an advisory; apply the scripts by hand (they address the row named
  `Rich Text Letter` with a subject starting `Rich Text Letter Generator`;
  a clinic-edited subject needs a manual edit first).
- *this import completed under manifest X … run --cleanup* — a package
  upgrade after a finished import; there is nothing to resume, retire the
  run with `--cleanup`.
- *P5 reconciliation FAILURES* — the offending document names are in
  `documents-details.txt` (root-only); the report carries the count.
- A failed run keeps its state for diagnosis; the documented rollback is
  restoring the pre-import restic snapshot.
