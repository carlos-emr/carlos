<!-- SPDX-License-Identifier: GPL-2.0-or-later -->

# JDBC connector differential probe

A development tool, not part of the build or of CI. It runs the same reads,
writes, statements and failures through two JDBC drivers against one physical
MariaDB server and prints one `section<TAB>key<TAB>outcome` line per
observation, so the two outputs can be diffed.

It was written for the review recorded in
`docs/superpowers/specs/2026-11-19-mariadb-driver-switch-findings.md`, to
compare MySQL Connector/J 9.6.0 (the driver CARLOS shipped before #5460) with
the CARLOS compatibility adapter over MariaDB Connector/J 3.5.11. Run it again
before any MariaDB Connector/J upgrade.

MySQL Connector/J is used here only as a reference on a developer machine. It
must not be added to `pom.xml`, to a lock file or to any package.

## Files

| File | Purpose |
|---|---|
| `gen_setup.py` | Writes the fixture SQL: table `zz_probe` with one column per column type the CARLOS schema uses (and a few it does not), plus scratch tables. |
| `Probe.java` | The probe. Sections: `SESSION`, `DBMETA`, `RSMETA` (every column of every table), `VALUES` (column type x getter), `EXPR`, `LABELS`, `WRITE` (setter x target column), `STMT`, `TIMEOUT`, `EXC`, `PARSE`, `DMLQ` (write statements through query APIs), `READONLY`, `FORMS` (legacy form save/load/update over every `form*` table). |
| `GuardOracle.java` | Records, per statement, whether `Statement.executeQuery` was refused by the driver and whether the database changed. Source of `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`. |
| `guard_reference.py` | Reference model of the adapter's `executeQuery` statement rule. `check ORACLE.tsv` verifies the model against every row of the fixture; `record CJ.tsv ADAPTER.tsv` prints rows from two `GuardOracle` outputs. |
| `diff.py`, `matrix.py` | Compare two probe outputs (`summary`, `list`, `rsmeta`, `values` modes; compact column-type x getter matrix). |

## Running it

1. Start a disposable MariaDB 11.8.8 with the packaged settings (`sql_mode=""`,
   `innodb_strict_mode=0`, `character-set-server=utf8mb4`,
   `collation-server=utf8mb4_general_ci`, 32K pages) and the host time zone you
   want to test. Never point the probe at a database that holds patient data:
   it creates and drops `zz_*` tables and writes rows into every `form*` table
   for demographic number 987654.
2. Create database `carlos`, a user `probe`/`probe` with `ALL` on `carlos.*`, a
   user `probe_ro`/`probe` with `SELECT` only, and load
   `database/mysql/migration/common/V1__baseline_schema.sql` and
   `database/mysql/migration/on/V1.0.1__on_schema.sql`.
3. `python3 gen_setup.py > probe_setup.sql` and load it.
4. Compile the three adapter classes
   (`src/main/java/io/github/carlos_emr/carlos/utility/MariaDb*.java`) against
   `mariadb-java-client-3.5.11.jar`, copy
   `src/main/resources/META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin`
   beside them, then compile `Probe.java` and `GuardOracle.java`.
5. Run once per driver, with the JVM in the clinic's time zone:

   ```bash
   java -Duser.timezone=America/Toronto -Dprobe.host=127.0.0.1:3306 \
     -cp classes:mariadb-java-client-3.5.11.jar:mysql-connector-j-9.6.0.jar \
     Probe cj text buffered cj.tsv
   java -Duser.timezone=America/Toronto -Dprobe.host=127.0.0.1:3306 \
     -cp classes:mariadb-java-client-3.5.11.jar:mysql-connector-j-9.6.0.jar \
     Probe adapter text buffered adapter.tsv
   python3 diff.py cj.tsv adapter.tsv ALL summary
   python3 matrix.py cj.tsv adapter.tsv VALUES getString,getObject,getInt,getLong
   ```

   Arguments: driver (`cj`, `adapter`, `adapter-native`, `raw`), protocol
   (`text`, `binary`), fetch (`buffered`, `stream`), output file, optional
   comma-separated section list. `-Dprobe.extra=&name=value` appends URL options.

To check the statement rule against the recorded oracle, with no database:

```bash
python3 guard_reference.py check ../../src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv
```

It prints the row counts and exits with status 1 if the model disagrees with a
row, or if a statement that MySQL Connector/J refused and that changes data
would be sent. The fixture's `unrecorded` rows were written by hand and were
not run against MySQL Connector/J.

The probe uses the packaged URL options
(`zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false`).
Server version matters: Connector/J reads MariaDB 10.x as "5.5.5" and changes
behaviour (no fractional seconds, no server-side read-only), so compare on the
server version CARLOS ships.
