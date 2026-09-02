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

Ship the bundle and the password (separately) to the CARLOS host.

## 3. Import on the CARLOS host

Prerequisites: a **fresh install** of the carlos-emr package — schema
migrated (`carlos-ctl db-migrate`), never used clinically (the importer
verifies this mechanically and refuses anything else; the only remedy is a
fresh schema, there is no override) — and configured backups
(the pre-import restic snapshot is the rollback point).

```bash
sudo carlos-ctl import-o19 \
    --bundle /srv/migration/o19-bundle.tar.gz.enc \
    --bundle-pass file:/srv/migration/passfile \
    --admin-user <break-glass-admin-name> \
    [--accept CLASS ...]        # the sign-offs preflight listed
```

Phases (state under `/var/lib/carlos-emr/o19-import/`): stock-deploy gate
→ staged restore → preflight → pre-import backup → data copy with
row-parity gate → documents restore with blocking reconciliation →
properties translation → verify. A rerun over existing state requires
`--resume`; it is never continued implicitly. Once the data copy has
started, a resumed run re-checks the schema, replica and disk gates but not
the emptiness sweep (the target is mid-import by design) — the row-parity
gate still verifies the outcome.

Clinic-defined lookup lists, waiting-list criteria and similar merge-class
rows may receive new ids where a CARLOS seed already holds the old one;
their dependent rows are remapped through `o19_archive.<table>__idmap`, and
the report itemizes every table whose ids changed. Tables and columns the
manifest does not know are never dropped: whole tables are archived under
`o19_archive`, unmapped columns of known tables are shadow-captured as
`<table>__unknown_cols`.

What the import does with credentials: every clinic login keeps working
(legacy password hashes upgrade to bcrypt on first login) but **all users
get a forced password reset**; the seeded `carlosdoc` account is removed
after the break-glass administrator named by `--admin-user` is created
(credentials in `/var/lib/carlos-emr/o19-import/admin-credentials.txt`,
root-only).

Useful variants: `--dry-run` (stage + preflight + properties report only),
`--dump/--documents/--properties` instead of a bundle,
`--bundle-openssl-opt` for bundles encrypted by an older openssl
(`-md md5`, no `-pbkdf2`), `--skip-documents` with `--accept no-documents`.

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
   fiscal year, documents reconciliation, archive/dropped inventory), plus
   manual spot checks and a UI smoke of the migrated charts.
4. `carlos-ctl import-o19 --cleanup` — drops the staging schema and the
   extracted bundle; the `o19_archive` schema (removed-module data +
   dropped-column shadows) and its CSV export under
   `OscarDocument/carlos/o19_archive_export/` are kept for the clinic.

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
- *documents reconciliation FAILED* — a `document` row's file is missing or
  empty in the tar. Fix the tree (or re-ship the tar) and `--resume`; the
  import never goes live with unreadable documents.
- *ETL pre-checks failed: NOT NULL target column(s)* — the CARLOS schema
  gained a required column the manifest doesn't cover yet; report it (the
  fix is a `value_exprs` curation entry + regenerated manifest).
- A failed run keeps its state for diagnosis; the documented rollback is
  restoring the pre-import restic snapshot.
