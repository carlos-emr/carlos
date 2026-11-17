# Query by Example: least-privileged database access

Issue [#3372](https://github.com/carlos-emr/carlos/issues/3372) follows
[PR #3339](https://github.com/carlos-emr/carlos/pull/3339), which made Query by Example safe to
enable at the application layer. This document records the threat model, the database-layer
boundary chosen for it, and what operators and developers must do to deploy, upgrade, roll back
and rotate it.

## Summary

- Every Query by Example query now runs as a dedicated read-only database account,
  `carlos_report`. It never runs as the application account (`db_username`), and there is no
  fallback to it.
- MariaDB grants `carlos_report` `SELECT` only on the tables that
  [`src/main/resources/db/reporting/carlos_report_grants.sql`](../src/main/resources/db/reporting/carlos_report_grants.sql)
  lists as **reportable**. Tables holding credentials, tokens, keys, live session identifiers or
  integration settings are **restricted** and never granted. A table that no one has classified,
  for example one a future migration adds, is not granted either.
- The application validator from PR #3339 keeps its restricted-table list as defense in depth.
  The build fails if that list and the grant list disagree, if a migration creates a table the
  grant list does not classify, or if a column whose name looks like a credential would be
  granted.
- Before each query the application reads the account's own `SHOW GRANTS` and checks every line
  against the same grant list, which the WAR carries. It refuses an account that can read a
  restricted or unclassified table, a hidden column, or a table with hidden columns as a whole, or
  that holds anything beyond `SELECT`, including privileges inherited from a role or from `PUBLIC`.
- Packaged installs provision the account automatically (`carlos-ctl` 1.2.0, `db-users` and
  `db-migrate`). Where the account is not provisioned, Query by Example says so and runs nothing.

## Threat model

### What is being protected

| Asset | Where it lives |
|---|---|
| Login credentials: password hashes, PINs, MFA secrets, reset tokens | `security`, `SecurityArchive`, `SecurityToken` |
| REST API OAuth material: client secrets, access and request tokens, nonces | `ServiceClient`, `ServiceAccessToken`, `ServiceRequestToken`, `ServiceOAuthNonce` |
| Private keys | `oscarKeys`, `publicKeys` |
| Live HTTP session identifiers, which are enough to take over a clinician's session | `PageMonitor.session`, `casemgmt_note_lock.session_id`, `report_date.sessionid` |
| Integration credentials and configuration | `fax_config`, `emailConfig`, `AppDefinition`, `AppUser`, `oscarcommlocations`, `professionalSpecialists` (eData keys), `property`, `studylogin`, `OceanSetting`, `OscarJob` |
| Integrity of the clinical record | every table |

The clinical record itself (PHI) is **not** an asset this boundary withholds. Query by Example is
a reporting tool over patient data, gated by the `_admin` or `_report` read privilege, and that is
unchanged.

### Who can reach it

1. An authorized Query by Example user acting outside their remit (insider), or an attacker who
   has taken over such a user's session.
2. A validator bypass: a query that JSqlParser and MariaDB read differently, so that the
   application validator accepts something MariaDB executes as more than one safe `SELECT`.
3. Process failure: a developer adds a table holding secrets and does not add it to the
   validator's restricted list.

### Threats and the controls that answer them

| Threat | Before #3372 (application only) | Now (application and database) |
|---|---|---|
| Read a credential, token, key, session or integration table | Validator restricted list | Same list, **and** the reporting account has no grant on those tables |
| A new sensitive table is added | Readable until someone remembers the list | Not granted until classified; the build fails until it is |
| A secret column inside an otherwise reportable table | Readable | Column hidden by a column-level grant (`casemgmt_note.password`, `DigitalSignature.signatureImage`); build heuristic flags credential-like column names |
| Validator bypass leading to writes, DDL or file access | JDBC read-only session; app account has full rights on the schema | Reporting account has no write, DDL, `FILE`, `PROCESS`, routine or other-schema privilege |
| Runaway or parallel queries | 15-second JDBC timeout, row and output caps | Same, plus `MAX_STATEMENT_TIME 30` and `MAX_USER_CONNECTIONS 6` on the account |
| Someone grants the reporting account more by hand, through a role or through `PUBLIC` | n/a | The application refuses such an account on every query; the grant script revokes hand-made grants and roles and refuses to run while `PUBLIC` reaches the schema; `carlos-ctl check` reports it |

### Reporting-query findings fixed along the way

Classifying every table for the grant list turned up tables the PR #3339 restricted list missed.
They are now restricted at both layers:

- `PageMonitor` and `casemgmt_note_lock` store `request.getSession().getId()`, the live
  `JSESSIONID` of clinicians viewing pages or editing notes. A Query by Example user could read
  them and take over those sessions.
- `report_date` is an unused legacy table keyed by session identifier.
- `studylogin` holds research-portal usernames and passwords.
- `OceanSetting` holds the Ocean integration's settings document; `OscarJob` holds free-form
  scheduled-job configuration. Neither has reporting value, and either can carry credentials.

## Decision

**A dedicated read-only database account with an explicit per-table grant list.**

Options considered:

| Option | Why not chosen |
|---|---|
| Reporting views in a separate schema | A `SELECT *` view freezes its column list and breaks when a migration drops a column; views need a definer account that exists wherever a backup is restored; table names change, breaking saved favourites. |
| Roles on the application account, with `SET ROLE` per query | Shared pool state: a missed reset leaves an application connection in the reporting role or vice versa. It still needs root to provision, and gains nothing over a second account. |
| An application-side allowlist only | Not an independent boundary: a validator bypass defeats it. |
| A grant on the whole schema minus some tables | MariaDB privileges are additive; there is no "deny". New tables would be exposed by default. |

Per-table grants keep every existing query and saved favourite working. Unqualified table names
resolve exactly as before because the account connects to the same schema, and what a report may
read becomes a reviewed list that the build checks.

## How it works

### The grant list

`carlos_report_grants.sql` is the single source of truth. It holds one classification row per
table (`reportable`, or `restricted` with a reason, optionally with hidden columns), followed by a
MariaDB block that rebuilds the account's privileges from that list:

1. For each host entry of the account it revokes every privilege and every `PROXY` grant
   (`REVOKE ALL` leaves those), revokes every granted role, clears the default role, and sets
   `MAX_USER_CONNECTIONS 6` and `MAX_STATEMENT_TIME 30`.
2. It refuses to go on if `PUBLIC` holds any privilege that reaches the schema, because every
   account would inherit it. Step 1 has already run, so the refused account is left with nothing.
3. It grants `SELECT` on each reportable table that exists in the selected schema. Where columns
   are hidden it grants `SELECT (every other column)` instead, so `SELECT *` on that table is
   refused and a report names its columns.
4. It asserts that nothing outside the list survived, and fails loudly if anything did.

It runs as a MariaDB administrator with the CARLOS schema as the default database, needs MariaDB
10.4 or later, and is idempotent. Re-running it is how a change to the list reaches a database. It
runs with `sql_log_bin = 0`, so grants never ride the binary log into a point-in-time restore.

### The application

- `ReportingConnectionService` reads `db_reporting_username` and `db_reporting_password` from
  `carlos.properties` and connects to the same `db_uri` + `db_name` as the application. It refuses,
  without connecting, when the settings are missing, or when the username is the application
  account or `root`.
- Before returning the connection it runs `SHOW GRANTS`, which MariaDB answers with everything the
  session can use: the account's own grants, granted roles, the default role and grants to
  `PUBLIC`. Each line is checked against the grant list the WAR ships
  (`WEB-INF/classes/db/reporting/carlos_report_grants.sql`, read by `ReportingGrantList`). Only
  `USAGE ON *.*` and `SELECT` on a table the list calls reportable, granted to the account itself
  without `GRANT OPTION`, are accepted; a table with hidden columns only as a column-list grant
  that names none of them. Table names are compared exactly, or lower-cased when the server's
  `lower_case_table_names` is not 0. Anything else (a restricted or unclassified table, a
  table-wide grant on a table with hidden columns, a hidden column, any other privilege) closes
  the connection, and so does a grant list the application cannot read. The log names the kind
  of grant, never the line, because that line carries the password hash.
- `RptByExampleData` still validates first with `QueryByExampleSqlValidator`, still runs read-only
  with the 15-second timeout and row and output limits, and audits each outcome by query hash. Two
  outcomes are new: `unavailable` (no usable reporting account, nothing ran) and `denied` (MariaDB
  refused a table or column the account is not granted).
- The page tells the user which case applies. When the account is not configured, the warning
  appears as soon as the page opens.

### The build guards

`QueryByExampleReportingClassificationUnitTest` reads the grant list and every Flyway migration and
fails when:

- a table that any migration creates is not classified, spelled exactly as the schema spells it;
- a row names a table no migration creates (a typo would otherwise leave the real table
  unclassified);
- the restricted rows differ from `QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES`;
- a hidden column does not exist (a typo would grant the real column);
- a reportable table has a column whose name parts mark a credential (`password`, `secret`,
  `token`, `session`, `pin`, `mfa`, `private key`, ...) that is not hidden.

`RptByExampleDataReportingAccountIntegrationTest` runs Query by Example against an isolated H2
database through a restricted account provisioned from the same list. It shows that an
unclassified table and the restricted tables stay unreadable even when the application's
restricted list is removed, and that the account cannot write. H2 has no column privileges, so the
column-level behaviour is covered against MariaDB by carlos-ctl's live tests and by the browser
check below.

### Classifying a new table

When a migration adds a table, add one row to `carlos_report_grants.sql`, in alphabetical order:

- `('<table>', 'reportable', NULL, NULL),` for clinical, administrative or reference data;
- `('<table>', 'restricted', NULL, '<reason>'),` for anything holding credentials, tokens, keys,
  session identifiers or integration configuration. Add the lower-cased name to
  `QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES` as well;
- `('<table>', 'reportable', '<column>[,<column>]', '<reason>'),` for a reportable table with a
  secret column.

When in doubt, restrict. A restricted table can be reclassified later; a leaked secret cannot be
taken back.

## Deployment

### Packaged installs (Debian/Ubuntu)

Nothing to do by hand. `carlos-emr` depends on `carlos-ctl (>= 1.2.0)`, whose:

- `db-users` creates `carlos_report` on `localhost` and `127.0.0.1` with a generated password,
  records it as `db_reporting_username` and `db_reporting_password` in
  `/etc/carlos-emr/carlos.properties`, and applies the grant list shipped in the deployed webapp
  (`/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes/db/reporting/carlos_report_grants.sql`)
  when the schema has tables;
- `db-migrate` (and every other migrating path: the postinst, `finish-install`) applies it again
  after each successful migration, so a reportable table that a migration adds is granted once it
  exists. A list that fails to apply is reported and never fails the migration;
- `check` reports the account: present on exactly its two hosts, named in `carlos.properties`,
  able to log in with the recorded password, and granted on each host exactly what the list
  gives it (every reportable table, the permitted columns of a table with hidden columns),
  with no restricted or unclassified table, hidden column, table-wide grant on a table with
  hidden columns, write privilege, role, `PROXY` or `PUBLIC` grant.

### Devcontainer

A fresh devcontainer database gets `carlos_report@'%'` from
`.devcontainer/db/scripts/populate_db.sh`, and the dev `carlos.properties` already names it. For a
database volume built before this change, or after adding a table, run this in the application
container and then `server restart`:

```bash
qbe-reporting-account
```

### Other deployments

As a MariaDB administrator, with your schema name in place of `carlos`:

```sql
CREATE USER 'carlos_report'@'<app host>' IDENTIFIED BY '<generated password>';
```

```bash
# WEBAPP: the deployed CARLOS webapp, for example /usr/local/tomcat/webapps/carlos
mariadb -u root carlos < "$WEBAPP/WEB-INF/classes/db/reporting/carlos_report_grants.sql"
```

Then set `db_reporting_username` and `db_reporting_password` in `carlos.properties` and restart.
Re-run the script after every schema migration. `carlos-podman` ships its own `carlos-ctl`; until
it provisions the account, Query by Example on that deployment reports that it is unavailable.

## Upgrading

On a packaged upgrade the postinst runs `db-users` (account created, list applied to the existing
tables), then `db-migrate` (list applied again), then restarts the application. Query by Example
keeps working without operator action. What users notice:

- Saved favourites keep working unless they read a restricted table.
- Queries reading `PageMonitor`, `casemgmt_note_lock`, `report_date`, `studylogin`, `OceanSetting`
  or `OscarJob` are now refused, in addition to the tables PR #3339 already refused.
- `SELECT *` on `casemgmt_note` or `DigitalSignature` is refused by the database. Name the columns
  instead, for example `SELECT note_id, demographic_no, observation_date, note FROM casemgmt_note`.

## Rollback

- **Package downgrade** to a release without this change: the older application ignores the
  `db_reporting_*` keys and runs Query by Example on the application account again, as it did
  before. The `carlos_report` account stays behind with its read-only grants and is used by
  nothing. Remove it if you want with `DROP USER 'carlos_report'@'localhost',
  'carlos_report'@'127.0.0.1';`.
- **Turning Query by Example off** without a downgrade: set `QUERY_BY_EXAMPLE_ENABLED = false` in
  `carlos.properties` and run `carlos-ctl restart`.
- There is deliberately no switch that sends Query by Example back to the application account
  while this release is installed.

## Credential rotation

- `sudo carlos-ctl rotate` rotates the `carlos_report` password together with the other generated
  passwords, writes it to `carlos.properties`, and restarts the application.
- `sudo carlos-ctl db-users --new-passwords` does the same without the restart. Query by Example
  is unavailable until `carlos-ctl restart`, while the rest of the application keeps working.
- Manual deployments: `ALTER USER` the account, update `db_reporting_password`, restart.
- If the password leaks: rotate it. The account can read only reportable data, the same data an
  authorized Query by Example user already sees, and it can change nothing. Still treat a leak as
  a PHI exposure, because reportable data includes patient records.

## Feature flag during rollout

`QUERY_BY_EXAMPLE_ENABLED` stays **`true`** by default. The decision:

- On packaged installs the account is provisioned by the same upgrade that ships this code, so
  Query by Example arrives already hardened and needs no opt-in.
- Where the account is missing (an unprovisioned devcontainer volume, a manual install, a podman
  deployment before its CLI catches up), the tool does not fall back to the application account.
  It shows that it is unavailable and runs nothing. Enabled-but-unprovisioned therefore fails
  closed. It never runs with only the application-level controls.
- `QUERY_BY_EXAMPLE_ENABLED = false` remains the switch for a site that wants the tool off
  entirely.

## Verification

| Layer | What |
|---|---|
| Unit | `ReportingConnectionServiceUnitTest` (settings, login refusal, `SHOW GRANTS` audit against the grant list, no password hash in logs), `ReportingGrantListUnitTest` (the reader the audit and the build checks share), `RptByExampleDataUnitTest` (`denied` and `unavailable` outcomes, no fallback), `ReportActionSecurityMigrationUnitTest` (messages), `QueryByExampleSqlValidatorUnitTest`, `QueryByExampleReportingClassificationUnitTest` |
| Integration (H2) | `RptByExampleDataReportingAccountIntegrationTest` |
| Live MariaDB | carlos-ctl `tests/test_reporting.py` with `CARLOS_TEST_MARIADB_SOCKET` and `CARLOS_SRC`: the shipped list against a real server, including column grants, re-sync revoking hand-made and `PROXY` grants, and the `PUBLIC` refusal leaving no stale grant |
| Browser, packaged install | `scripts/report-query-by-example-least-privilege-playwright-checks.js` through the front door |
| Deployment | `sudo carlos-ctl check` |

## Known limits

- The boundary withholds secrets and protects integrity. It does not narrow which patient data an
  authorized Query by Example user can read.
- A new column added to a reportable table is granted at the next sync. The build heuristic
  catches credential-like names; a secret under an innocuous name needs a reviewer.
- A table created at run time rather than by a Flyway migration is never granted.
- The grant script is MariaDB-specific (10.4 or later). The application's `SHOW GRANTS` audit also
  understands MySQL's output format.
- The audit reads the grants when it opens the connection for a query. A privilege a database
  administrator adds while that one query runs is caught by the next query, not that one. The
  audit finds drift; it is not a lock on `GRANT`, and whoever can grant can already read the data.
