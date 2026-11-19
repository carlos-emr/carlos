# Report connection lease lifetime — release/2026.08

Fixes #6088. The shipped datasource enabled abandoned-connection removal after
15 seconds, while Report by Template allows 30 seconds by default and up to
300 seconds. A borrow near pool capacity can therefore abort a legitimate
executing report before its own deadline.

The default is now 360 seconds: the maximum supported report query deadline plus
one minute for cancellation and cleanup. Reclamation on borrow and maintenance,
and abandoned-lease logging, remain enabled. Explicit site overrides still win.
Choose an override above the longest legitimate query or transaction lifetime.
Earlier Debian packages copied the 15-second default to
`/etc/carlos-emr/carlos.properties`. The upgrade warns when that file still
sets 15 and leaves the file intact. Remove the setting or set it to 360, then
run `sudo carlos-ctl restart` to adopt the new default.
This trades slower recovery of genuinely leaked leases for allowing bounded
legitimate work to finish. It does not extend the report query deadline.
Historical O19 defaults and migration fixtures remain historical records.

## Executed regression

The test loads the actual production carlos.properties and datasource bean in
spring_jpa.xml beside the compiled OscarTrackingBasicDataSource class, bypassing
similarly named test resources. It instantiates that actual datasource, changing
only the disposable database credentials and reducing the pool to three leases.
It observes the running query in the database, then borrows a third connection
at 16 seconds while a 20-second query and a second lease are active.

On release defaults, MariaDB aborts the executing query with a
SQLNonTransientConnectionException (socket error); the active lease is reclaimed
before its 30-second deadline. With the change, it completes. The same three
cases run against H2 and MariaDB 11.8.8 with 32 KiB InnoDB pages on Java 25.
The other controls retain actual reclamation with an explicit one-second
idle timeout and preserve a 17-second override plus all leak-detection flags.
H2's JDBC abort is a no-op, so the test checks the public active-lease count on
both databases and additionally verifies the physical retired handle on MariaDB.
Each run owns and drops its schema; each physical server is retired afterward.

| Evidence | Cases | Failures | Errors/skips | JUnit SHA-256 |
| --- | ---: | ---: | --- | --- |
| report-connection-lease-valid-before-h2 | 3 | 1 | 0/0 | `59d6f6117120c19c63adca67926ef95c44aab461916203c66c2a0b533a88b35b` |
| report-connection-lease-valid-before-mariadb | 3 | 1 | 0/0 | `38694a435eb88ef54f1a7f8039197bdffd8f29c31e08e0807dfff5fae8555ad5` |
| report-connection-lease-final-h2 | 3 | 0 | 0/0 | `611ef028502e7ebbc00c8f34bddb8cf5acae7262cd8f94c21dba53a993c2b658` |
| report-connection-lease-final-mariadb | 3 | 0 | 0/0 | `3b4335a0705676840383ed081113fc20a1ee46daa82043e4ef89313740374da2` |

The original before/after tests were reformatted and tagged mariadb-native;
final-source H2 and MariaDB retakes above both pass. No production Java changes.
The physical MariaDB CI job explicitly selects this suite and its evidence
verifier requires all three passing case identities. The default Maven test
selection also discovers it. The verifier's existing regression suite is run
unchanged; its first sparse-checkout attempt lacked an unrelated MSP test source,
so its raw failure is retained. The complete-source retake passes all 26 cases.

These are selected-source component regressions. Qualification of the next
combined installed Ubuntu 26.04 DEBs, including this changed default resource,
is pending; prior installed builds do not qualify this revision.
