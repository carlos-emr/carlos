<!-- SPDX-License-Identifier: GPL-2.0-or-later -->

# MariaDB Driver Switch Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One pull request on `release/2026.08` that closes the seven open driver-switch issues (#5922, #5968, #5969, #5770, #5863, #5744, #6088), does the driver part of the documentation work now tracked in #3053 (formerly #5768), corrects the eight further differences (N1 to N8) found by the differential review, and closes the places where report and dashboard SQL was assembled from request or saved text (spec section 11).

**Architecture:** The branch is based on the release head and merges the three open pull requests that already fix four of the issues. Everything else is small: three additions inside the existing JDBC adapter classes (a statement guard for `executeQuery`, session defaults, two value and exception parities), four call-site corrections, documentation, and the same adapter mirrored into DrugRef with a build check that keeps the two copies identical. Every new physical-MariaDB case goes into a test class the MariaDB CI lane already runs, so no workflow file changes.

**Tech Stack:** Java 25, MariaDB Connector/J 3.5.11 behind `MariaDbCompatibilityDriver`, MariaDB 11.8.8, Spring 7.0.10, Hibernate 7.4.14, JUnit 5 / AssertJ / Mockito, Maven; Python 3 for CI bookkeeping and packaging tests.

**Spec:** `docs/superpowers/specs/2026-11-19-mariadb-driver-switch-findings.md` (read it first; section numbers below refer to it)

**Dry run:** Tasks 2 to 9, the test of Task 10 and the counts of Task 12 were carried out in a scratch clone, on top of the merges of Task 1, and run on MariaDB 11.8.8 (spec section 10). The test code below is the code that compiled and passed there. The production changes are described, not transcribed: the dry run shows they can be written from these descriptions. Not dry-run: the documentation edits of Task 10, Task 11, the coverage records of Task 12, the browser check of Task 9 Step 9 and the full MariaDB lanes after Task 9 (its database pieces were run on their own).

## Global Constraints

- Target branch `release/2026.08`. Work branch `claude/brave-ptolemy-yfxbw5`, based on `origin/release/2026.08`, never on `main` (`main` has three commits the release branch lacks). The branch already exists on the remote: the release head of 2026-11-19 (`8872cdd3`) plus commits that hold this plan, the spec, the probe and the oracle fixture. Bring it up to date by merging; never rebase or force-push it.
- Do not edit anything under `.github/` or `database/`. Consequences: new physical cases go only into `JdbcConnectorMariaDbIntegrationTest` and `MariaDbDaoHarnessIntegrationTest` (the MariaDB lane's class list is duplicated in `.github/workflows/maven-project.yml`); the one workflow line PR #6295 changes arrives by merging that PR, not by editing; the assistant files under `.github/` named in #3053 are left for a maintainer and listed in the pull request body.
- **The adapter is three source files and stays three:** `MariaDbCompatibilityDriver`, `MariaDbJdbcCompatibility`, `MariaDbPreferredTlsPlugin`. New adapter code goes inside them; a nested class is fine, a new top-level class is not. `debian/build-flyway-runner.sh`, `CdsRulesetFingerprint` and the DrugRef package check each name exactly these three and take their nested classes with them (spec section 6). The three files must be identical in `carlos` and `drugref2026` apart from the package name (`io.github.carlos_emr.carlos.utility` / `io.github.carlos_emr.drugref2026.util`).
- MariaDB Connector/J stays at 3.5.11. MySQL Connector/J must not appear in `pom.xml`, a lock file or a package. The guard is written from spec section 6 and the recorded oracle; do not copy code from MySQL Connector/J.
- No statement text, URL, credential or row value in any exception message or log line this work adds.
- Task 9 is a security fix in a public repository. Its commit messages, code comments and the pull request describe what each check enforces, not how the missing check could have been used. No example input that would have worked goes into a test name, a comment or the pull request.
- Exact values: refusal SQLState `S1009`, message `executeQuery() cannot run a statement that does not produce a result set`; session statement and its rules as in spec section 8 (`net_write_timeout=600`); lock-wait mapping error `1205` to SQLState `40001`; option message `Invalid database connection configuration: option <name>; <requirement>` with SQLState `08001`.
- Tests: JUnit 5, AssertJ, names `should<Action>_<context>` with exactly one underscore, `@Tag("unit")` or `@Tag("integration")`. `java.sql.SQLException` is an `Iterable`, so `assertThat(sqlException)` does not compile: assert on `getMessage()`, `getSQLState()` and `getCause()`. The build runs Checkstyle before compiling and fails on an unused import. New files carry the CARLOS copyright header (`docs/copyright-header-carlos.md`), no `@author`. New classes follow `docs/architecture/layer-names.md` (no `*Helper`, `*Utils`, `*Manager`).
- Conventional Commits. End every commit message with the attribution trailers the session supplies. Commit after each task; push only when the maintainer asks; do not open the pull request until asked.

## Review Focus

1. A read the guard refuses: a saved report or a code path whose statement starts with a comment, a parenthesis, `WITH`, lower case, white space, `SHOW`, `EXPLAIN` or `{call …}`, or mentions `INTO` only inside a quoted value or a quoted name. It must run exactly as before. Pinned in Task 2 (every `SENT` row of the oracle, and `shouldSendTheReadShapesCarlosUses_withoutRefusal`).
2. An operator's own `sessionVariables` or `initSql` in the JDBC URL, in any letter case. Their settings must still apply and must win over the adapter's defaults. Pinned in Task 4.
3. `db_name` with no `?`, with a `?` and nothing after it, blank, or missing. The application must start; only the audit purge may refuse, with a clear message. Pinned in Task 7.
4. A Query by Example result whose binary cell holds bytes that are not text (an image in `client_image`). The page must render within its existing budget instead of failing. Pinned in Task 3.
5. A server whose own default `sql_mode` contains `IGNORE_SPACE` or `ANSI`. The session must keep it, as it did under MySQL Connector/J. Pinned in Task 4.
6. A configured report whose filter the user ticks, run as HTML or as CSV, including a filter on the deleted-report list, and a saved demographic query written before this change. Each must give the same rows as before; only a request that names a filter the report does not have, or a column the report does not offer, is refused. Pinned in Task 9.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java` | Gains the nested `QueryStatementValidator` and the call to it; SMALLINT objects; error 1205. | 2, 5 |
| `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java` | Session defaults; names a refused option. | 4, 6 |
| `src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java` | Stops replacing the option message. | 6 |
| `src/main/java/io/github/carlos_emr/carlos/report/data/RptResultStruct.java` | Reads binary cells as text. | 3 |
| `src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java` | Reads the schema name safely. | 7 |
| `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoImpl.java` | Recognises both product names. | 8 |
| `src/main/java/io/github/carlos_emr/carlos/report/data/RptDemographicColumnNames.java`, `RptDemographicQuery2Builder.java`, `RptDemographicQueryBuilder.java`, `RptDemographicQuery2Saver.java`, `DemographicAgeMonths.java` (new); `prev/reports/ReportBuilder.java` | Demographic and prevention report columns and ages. | 9 |
| `src/main/java/io/github/carlos_emr/carlos/report/data/ReportFilterResolver.java` (new), `commn/dao/ReportFilterDao.java`, `ReportFilterDaoImpl.java`, `report/pageUtil/RptFormQuery.java`, `report/pageUtil/RptDownloadCSVServlet.java` | Configured-report filters and the CSV download. | 9 |
| `src/main/java/io/github/carlos_emr/carlos/managers/DashboardManagerImpl.java`, `dashboard/handler/IndicatorTemplateXML.java` | The dashboard provider number. | 9 |
| `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java` | All new physical JDBC cases (15). | 2, 3, 4, 5, 9, 10 |
| `src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java` | One physical JPA case. | 2 |
| `debian/assets/flyway-runner/src/test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerJdbcTest.java` | Proves at package build that the launcher carries the statement rule. | 2 |
| `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`, `scripts/jdbc-connector-probe/` (already on the branch) | Oracle fixture (212 rows); the probe and reference model behind it. | 2 |
| `docs/mariadb-connector-migration.md`, `docs/release-notes-2026.08.md`, packaging READMEs, `CLAUDE.md`, `GEMINI.md` | Documentation. | 10 |
| `debian/check-drugref-adapter-sources.py` (new), `debian/rules`, `debian/drugref.pin` | DrugRef copy check and pin. | 11 |
| `scripts/ci/verify-mariadb-dao-reports.py`, `scripts/ci/test_mariadb_report_evidence.py`, `scripts/ci/test_release_plan_5700.py`, `docs/testing/release-2026.08-verification.json`, `scripts/coverage/floors/**`, `docs/testing/coverage-floor-policy.md` | Required-case counts and coverage floors. | 12 |

Commands used throughout (JDK 25 is required; the build does not run on an older JDK):

- Unit tests: `mvn -B -ntp test -Dtest='<Class>'`
- Physical cases: `CARLOS_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:3306/ CARLOS_TEST_MARIADB_USER=<user> CARLOS_TEST_MARIADB_PASSWORD=<password> mvn -B -ntp test -Dtest=JdbcConnectorMariaDbIntegrationTest -Dgroups=mariadb-native` against a disposable MariaDB 11.8.8. The account needs `CREATE` and `DROP` on any database; each test creates and drops its own schema.

---

### Task 1: Integration branch with the three open pull requests

**Files:**
- Modify (merge conflict only): `scripts/ci/verify-mariadb-dao-reports.py`

**Interfaces:**
- Produces: the branch every later task builds on. From PR #6238: `ReportJdbcTextReader.open(ResultSet rows, int column, int jdbcType)` returning a `Reader` or `null`. From PR #6113: the adapter with `legacyNumberValue`, `columnClassName`, `invokeNative`, `wrapResult`; `JdbcConnectorMariaDbIntegrationTest` with 47 required cases (23 parameterized methods, 1 plain); `debian/drugref.pin` at `703e3f081587b0e3d764d2146c0c548e13cd8cdf`. From PR #6295: `db_remove_abandoned_timeout=360`, `ReportConnectionLeaseIntegrationTest`.

- [ ] **Step 1: Fetch the work branch, the release branch and the three pull request branches**

```bash
git fetch origin claude/brave-ptolemy-yfxbw5 release/2026.08 claude/jolly-albattani-aotrv2 \
  codex/release-2026-08-report-blob-readers codex/release-2026-08-report-connection-lease
git rev-parse origin/claude/jolly-albattani-aotrv2 \
  origin/codex/release-2026-08-report-blob-readers origin/codex/release-2026-08-report-connection-lease
```

Expected: `94d42b243db8308e96db2bb701892f7d77895e3e`, `b6956045d4d16caca0a0715a9a2d678b7d843c01`, `e053ecaa75681a4b0f97a7a3bdb79f73827efc9a`. If a head differs, read `git log <expected>..<head>` before merging it. If a pull request has already merged into the release branch, skip its merge.

- [ ] **Step 2: Bring the work branch up to the release head and merge #6113 and #6238**

```bash
git checkout claude/brave-ptolemy-yfxbw5
git merge --ff-only origin/claude/brave-ptolemy-yfxbw5
git merge --no-edit origin/release/2026.08
git merge --no-ff --no-edit origin/claude/jolly-albattani-aotrv2
git merge --no-ff --no-edit origin/codex/release-2026-08-report-blob-readers
```

Expected: the release merge says "Already up to date" or merges cleanly; the two pull request merges are clean.

- [ ] **Step 3: Merge #6295 and resolve its one conflict**

Run `git merge --no-ff --no-edit origin/codex/release-2026-08-report-connection-lease`. Expected: a conflict in `scripts/ci/verify-mariadb-dao-reports.py` only, in the `REQUIRED` table. Keep the incoming side (it adds `"ReportConnectionLeaseIntegrationTest": 3,`) and change its `"JdbcConnectorMariaDbIntegrationTest": 32` to `47`. Then `git add scripts/ci/verify-mariadb-dao-reports.py && git commit --no-edit`.

- [ ] **Step 4: Verify the merged tree**

```bash
python3 -m unittest discover -s scripts/ci -p 'test_mariadb_report_evidence.py'
python3 -m unittest discover -s scripts/ci -p 'test_release_plan_5700.py'
python3 scripts/coverage/check-coverage-floors.py --freshness-only \
  --floors scripts/coverage/release-coverage-floors.json \
  --base "$(python3 -c 'import json; print(json.load(open("docs/testing/release-2026.08-verification.json"))["baselines"]["currentMain"])')" \
  --output /tmp/freshness.json
mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,ReportBinaryReadersUnitTest,RptResultStructUnitTest'
```

Expected: `OK` twice, then `{"fresh": true, …, "problems": []}`, then `BUILD SUCCESS`. The merges are this task's commits; there is nothing else to commit.

### Task 2: Refuse statements that produce no result set in `executeQuery` (N1)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java` (`wrap` :116, `JdbcHandler` :122-135, `invoke` :138-178, `wrapResult` :500-510, and a new nested class at the end of the file)
- Create: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidatorUnitTest.java`
- Create: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverQueryGuardUnitTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java`
- Modify: `debian/assets/flyway-runner/src/test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerJdbcTest.java`
- Uses (already on the branch): `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`

**Interfaces:**
- Produces, nested in `MariaDbCompatibilityDriver` (DrugRef gets its copy in Task 11):

```java
static final class QueryStatementValidator {
    static final String REFUSAL_STATE = "S1009";
    static final String REFUSAL_MESSAGE = "executeQuery() cannot run a statement that does not produce a result set";
    static boolean producesResultSet(String sql);   // spec section 6; null is refused
    static SQLException refusal();                   // new SQLException(REFUSAL_MESSAGE, REFUSAL_STATE)
}
```

- [ ] **Step 1: Write the failing validator test**

The fixture has 212 data rows: tab-separated `connectorj`, `carlos`, `changes`, `statement`; `#` lines and the header are skipped; `\n` and `\t` in the statement are escaped.

```java
import io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver.QueryStatementValidator;

@Tag("unit")
class MariaDbQueryStatementValidatorUnitTest {
    record Row(String connectorJ, String carlos, boolean changes, String statement) { }

    static List<Row> oracle() throws IOException {
        try (var in = MariaDbQueryStatementValidatorUnitTest.class
                .getResourceAsStream("/jdbc/connector-j-9.6.0-executequery-oracle.tsv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.startsWith("#") && !line.startsWith("connectorj\t"))
                    .map(line -> line.split("\t", 4))
                    .map(f -> new Row(f[0], f[1], f[2].equals("yes"), f[3].replace("\\n", "\n").replace("\\t", "\t")))
                    .toList();
        }
    }

    @ParameterizedTest
    @MethodSource("oracle")
    void shouldMatchTheRecordedDecision_forEveryOracleStatement(Row row) {
        assertThat(QueryStatementValidator.producesResultSet(row.statement()))
                .as(row.statement()).isEqualTo(row.carlos().equals("SENT"));
    }

    @Test
    void shouldRefuseEveryRecordedWrite_whenConnectorJRefusedIt() throws IOException {
        assertThat(oracle()).hasSize(212);
        var writes = oracle().stream().filter(row -> row.changes() && row.connectorJ().equals("REFUSED")).toList();
        assertThat(writes).hasSize(26).allSatisfy(row ->
                assertThat(QueryStatementValidator.producesResultSet(row.statement())).as(row.statement()).isFalse());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select 1", "\n\t SELECT 1", "(SELECT 1) UNION (SELECT 2)",
            "/* load Demographic */ select d.demographic_no from demographic d", "-- report\nSELECT 1",
            "WITH c AS (SELECT 1 AS a) SELECT a FROM c", "SHOW GRANTS", "EXPLAIN SELECT 1",
            "{call some_procedure(?)}", "{?= call some_function(?)}", "SELECT 1 FOR UPDATE",
            "SELECT last_name FROM demographic WHERE last_name = 'INSERT INTO'",
            "SELECT `into` FROM t", "SELECT t.into FROM t"})
    void shouldSendTheReadShapesCarlosUses_withoutRefusal(String sql) {
        assertThat(QueryStatementValidator.producesResultSet(sql)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "/* only */", "-- only", "# only"})
    void shouldRefuseText_withNoStatementInIt(String sql) {
        assertThat(QueryStatementValidator.producesResultSet(sql)).isFalse();
    }

    @Test
    void shouldCarryNoStatementText_inTheRefusal() {
        SQLException refusal = QueryStatementValidator.refusal();
        assertThat(refusal.getMessage()).isEqualTo("executeQuery() cannot run a statement that does not produce a result set");
        assertThat(refusal.getSQLState()).isEqualTo("S1009");
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=MariaDbQueryStatementValidatorUnitTest`
Expected: compilation failure, `QueryStatementValidator` not found.

- [ ] **Step 3: Implement `MariaDbCompatibilityDriver.QueryStatementValidator`**

A `static final class` at the end of `MariaDbCompatibilityDriver`, with a private constructor. One left-to-right scan of the characters, no regular expression over the whole text, following spec section 6 exactly. `scripts/jdbc-connector-probe/guard_reference.py` is a working model of the same rule in Python (it is ours; reading it is fine). Class Javadoc: what the guard restores, that it mirrors a driver refusal and is not a read-only guarantee, why it is nested (spec section 6), the pointer to the oracle, and that the statement text must never reach a message or a log.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest=MariaDbQueryStatementValidatorUnitTest`
Expected: PASS, 234 cases (212 oracle rows and the others). Also run `python3 scripts/jdbc-connector-probe/guard_reference.py check src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`; expected: `rows=212 recorded=181 deviations-from-connectorj=5 model-disagreements=0 unsafe=0`.

- [ ] **Step 5: Write the failing adapter test**

`MariaDbCompatibilityDriverQueryGuardUnitTest` opens the adapter over a mocked MariaDB driver, as `MariaDbCompatibilityDriverNumberUnitTest.withNativeRows` does.

```java
@Tag("unit")
class MariaDbCompatibilityDriverQueryGuardUnitTest {
    private static final String WRITE = "INSERT INTO t VALUES (1)";
    private static final String READ = "SELECT 1";

    @FunctionalInterface
    interface Check {
        void accept(Connection connection, org.mariadb.jdbc.Connection physical) throws Exception;
    }

    private static void withConnection(Check check) throws Exception {
        org.mariadb.jdbc.Connection physical = mock(org.mariadb.jdbc.Connection.class, Answers.RETURNS_DEEP_STUBS);
        when(physical.getContext().getConnectionTimeZone()).thenReturn(TimeZone.getTimeZone("UTC"));
        when(physical.unwrap(org.mariadb.jdbc.Connection.class)).thenReturn(physical);
        try (MockedConstruction<org.mariadb.jdbc.Driver> drivers = mockConstruction(org.mariadb.jdbc.Driver.class,
                (driver, context) -> when(driver.connect(anyString(), any(Properties.class))).thenReturn(physical))) {
            Connection connection = new MariaDbCompatibilityDriver().connect("jdbc:mysql://127.0.0.1:3306/probe", new Properties());
            assertThat(drivers.constructed()).isNotEmpty();
            check.accept(connection, physical);
        }
    }

    private static void assertRefused(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SQLException.class, refusal -> {
            assertThat(refusal.getSQLState()).isEqualTo("S1009");
            assertThat(refusal.getMessage()).isEqualTo("executeQuery() cannot run a statement that does not produce a result set");
        });
    }

    @Test
    void shouldRefuseBeforeSending_whenStatementExecuteQueryIsGivenAWrite() throws Exception {
        withConnection((connection, physical) -> {
            org.mariadb.jdbc.Statement nativeStatement = mock(org.mariadb.jdbc.Statement.class);
            when(physical.createStatement()).thenReturn(nativeStatement);
            Statement statement = connection.createStatement();
            assertRefused(() -> statement.executeQuery("UPDATE demographic SET last_name = 'x'"));
            verify(nativeStatement, never()).executeQuery(anyString());
        });
    }

    @Test
    void shouldRefuseBeforeSending_whenAPreparedWriteIsRunAsAQuery() throws Exception {
        withConnection((connection, physical) -> {
            PreparedStatement nativePrepared = mock(PreparedStatement.class);
            when(physical.prepareStatement("DELETE FROM log WHERE id = ?")).thenReturn(nativePrepared);
            PreparedStatement prepared = connection.prepareStatement("DELETE FROM log WHERE id = ?");
            assertRefused(prepared::executeQuery);
            verify(nativePrepared, never()).executeQuery();
            prepared.executeUpdate();
            verify(nativePrepared).executeUpdate();
        });
    }

    /** Every prepare overload of java.sql.Connection, given the statement text. */
    private static List<Statement> prepareEveryWay(Connection connection, String sql) throws SQLException {
        return List.of(
                connection.prepareStatement(sql),
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS),
                connection.prepareStatement(sql, 1003, 1007),
                connection.prepareStatement(sql, 1003, 1007, 2),
                connection.prepareStatement(sql, new int[]{1}),
                connection.prepareStatement(sql, new String[]{"id"}),
                connection.prepareCall(sql),
                connection.prepareCall(sql, 1003, 1007),
                connection.prepareCall(sql, 1003, 1007, 2));
    }

    private static PreparedStatement stubEveryPrepare(org.mariadb.jdbc.Connection physical) throws SQLException {
        // One native statement answers all nine overloads; a CallableStatement is also a PreparedStatement.
        CallableStatement nativeStatement = mock(CallableStatement.class);
        when(physical.prepareStatement(anyString())).thenReturn(nativeStatement);
        when(physical.prepareStatement(anyString(), anyInt())).thenReturn(nativeStatement);
        when(physical.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(nativeStatement);
        when(physical.prepareStatement(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(nativeStatement);
        when(physical.prepareStatement(anyString(), any(int[].class))).thenReturn(nativeStatement);
        when(physical.prepareStatement(anyString(), any(String[].class))).thenReturn(nativeStatement);
        when(physical.prepareCall(anyString())).thenReturn(nativeStatement);
        when(physical.prepareCall(anyString(), anyInt(), anyInt())).thenReturn(nativeStatement);
        when(physical.prepareCall(anyString(), anyInt(), anyInt(), anyInt())).thenReturn(nativeStatement);
        return nativeStatement;
    }

    @Test
    void shouldSendAPreparedRead_forEveryPrepareOverload() throws Exception {
        withConnection((connection, physical) -> {
            PreparedStatement nativeStatement = stubEveryPrepare(physical);
            for (Statement prepared : prepareEveryWay(connection, READ)) ((PreparedStatement) prepared).executeQuery();
            verify(nativeStatement, times(9)).executeQuery();
        });
    }

    @Test
    void shouldRefuseAPreparedWrite_forEveryPrepareOverload() throws Exception {
        withConnection((connection, physical) -> {
            PreparedStatement nativeStatement = stubEveryPrepare(physical);
            for (Statement prepared : prepareEveryWay(connection, WRITE)) assertRefused(((PreparedStatement) prepared)::executeQuery);
            verify(nativeStatement, never()).executeQuery();
        });
    }

    @Test
    void shouldLeaveExecuteAndExecuteUpdateAlone_forWrites() throws Exception {
        withConnection((connection, physical) -> {
            org.mariadb.jdbc.Statement nativeStatement = mock(org.mariadb.jdbc.Statement.class);
            when(physical.createStatement()).thenReturn(nativeStatement);
            Statement statement = connection.createStatement();
            statement.execute("UPDATE t SET a = 1");
            statement.executeUpdate("UPDATE t SET a = 1");
            verify(nativeStatement).execute("UPDATE t SET a = 1");
            verify(nativeStatement).executeUpdate("UPDATE t SET a = 1");
        });
    }
}
```

- [ ] **Step 6: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=MariaDbCompatibilityDriverQueryGuardUnitTest`
Expected: FAIL; the three refusal cases see no exception.

- [ ] **Step 7: Call the validator from the adapter**

In `MariaDbCompatibilityDriver`:

- `JdbcHandler` gains `private final boolean refusesExecuteQuery;`, set by a fifth constructor argument. `wrap(Object, Class<?>, Connection, Statement, ConversionPolicy)` keeps its signature and passes `false`; add an overload with a trailing `boolean refusesExecuteQuery`.
- `wrapResult(Object result, Object proxy)` becomes `wrapResult(Object result, Object proxy, String name, Object[] args)`. When `target` is a `Connection` and `name` is `prepareStatement` or `prepareCall`, the new statement proxy is created with `!QueryStatementValidator.producesResultSet((String) args[0])`. The statement is classified once, here; the handler keeps the answer, not the text.
- In `invoke`, after the `setFetchSize` block: when `target` is a `Statement` and `name` is `executeQuery`, throw `QueryStatementValidator.refusal()` if the call has a `String` argument that does not produce a result set, or has no argument (`args` is then `null`) and `refusesExecuteQuery` is set.

- [ ] **Step 8: Run the adapter unit tests**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest,CdsRulesetFingerprintMappingUnitTest'`
Expected: PASS, including the classes that existed before.

- [ ] **Step 9: Add the physical JDBC case**

In `JdbcConnectorMariaDbIntegrationTest`:

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldRefuseWritesThroughQueryApis_andLeaveTheRowUnchanged(boolean legacy) throws Exception {
    try (var source = source(legacy, "jdbcCompliantTruncation=false");
         Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE guard_probe (id INT PRIMARY KEY, v INT)");
        statement.executeUpdate("INSERT INTO guard_probe VALUES (1, 1)");
        for (String write : java.util.List.of("UPDATE guard_probe SET v = 2", "/* c */ DELETE FROM guard_probe",
                "/*! INSERT INTO guard_probe VALUES (2, 2) */", "INSERT INTO guard_probe VALUES (3, 3) RETURNING id",
                "DROP TABLE guard_probe", "SET @guard_probe = 1", "SELECT v INTO @guard_probe FROM guard_probe")) {
            assertThatThrownBy(() -> statement.executeQuery(write)).as(write)
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("S1009"));
            try (java.sql.PreparedStatement prepared = connection.prepareStatement(write)) {
                assertThatThrownBy(prepared::executeQuery).as(write)
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("S1009"));
            }
        }
        assertThatThrownBy(() -> new org.springframework.jdbc.core.JdbcTemplate(source)
                .queryForList("UPDATE guard_probe SET v = 9"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        for (String read : java.util.List.of("SELECT v FROM guard_probe", "(SELECT 1) UNION (SELECT 2)",
                "WITH c AS (SELECT 1 AS a) SELECT a FROM c", "SHOW TABLES", "/* c */ SELECT 1", "EXPLAIN SELECT 1")) {
            try (ResultSet rows = statement.executeQuery(read)) {
                assertThat(rows.next()).as(read).isTrue();
            }
        }
        try (ResultSet rows = statement.executeQuery("SELECT id, v FROM guard_probe")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
            assertThat(rows.getInt(2)).isEqualTo(1);
            assertThat(rows.next()).isFalse();
        }
    }
}
```

- [ ] **Step 10: Add the physical JPA case**

In `MariaDbDaoHarnessIntegrationTest` (it runs only with `CARLOS_TEST_MARIADB_DAO=true`). The statement changes no row even where it is executed.

```java
@Test
void shouldRefuseANativeWrite_whenItIsRunAsAQuery() {
    var manager = entityManagerFactory.createEntityManager();
    try {
        assertThatThrownBy(() -> manager
                .createNativeQuery("UPDATE measurements SET dataField = dataField WHERE 1 = 0").getResultList())
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .hasMessage("executeQuery() cannot run a statement that does not produce a result set");
    } finally {
        manager.close();
    }
}
```

- [ ] **Step 11: Make the package build prove the launcher jar carries the rule**

In `FlywayRunnerJdbcTest.main`, before the first `FlywayRunner.jdbcUrl` call. `debian/build-flyway-runner.sh` runs this test with exactly the launcher jar's contents on the class path.

```java
// The launcher jar carries the driver and its nested classes by name (debian/build-flyway-runner.sh).
// The executeQuery statement rule must be one of them, so that the jar holds the whole driver and does
// not depend on the WAR's classes also being on the class path.
Class.forName("io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver$QueryStatementValidator");
```

- [ ] **Step 12: Run both physical cases**

Run the physical command from the top of this plan (expected: 49 cases pass), then the same with `-Dtest=MariaDbDaoHarnessIntegrationTest -Dgroups=integration` and `CARLOS_TEST_MARIADB_DAO=true CARLOS_TEST_MARIADB_SCHEMA=generated` (expected: 2 pass). If either refusal case had passed before Step 7, stop: the case is not testing the guard.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidatorUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverQueryGuardUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java \
  src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java \
  debian/assets/flyway-runner/src/test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerJdbcTest.java
git commit -m "fix(db): refuse statements that produce no result set in executeQuery"
```

### Task 3: Query by Example reads binary cells as text (N2)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/report/data/RptResultStruct.java:186-187`
- Test: `src/test/java/io/github/carlos_emr/carlos/report/data/RptResultStructUnitTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`

**Interfaces:**
- Consumes: `ReportJdbcTextReader.open(ResultSet rows, int column, int jdbcType)` (Task 1).

- [ ] **Step 1: Write the failing unit tests**

```java
private static ResultSet oneBinaryCell(int type) throws Exception {
    var rows = mock(ResultSet.class);
    var metadata = mock(ResultSetMetaData.class);
    when(rows.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnCount()).thenReturn(1);
    when(metadata.getColumnLabel(1)).thenReturn("query");
    when(metadata.getColumnType(1)).thenReturn(type);
    when(rows.next()).thenReturn(true, false);
    return rows;
}

@ParameterizedTest
@ValueSource(ints = {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB})
void shouldRenderBinaryCellsAsUtf8Text_withoutTheCharacterCodec(int type) throws Exception {
    var rows = oneBinaryCell(type);
    when(rows.getBinaryStream(1)).thenReturn(new ByteArrayInputStream("select <1> é".getBytes(StandardCharsets.UTF_8)));
    when(rows.getCharacterStream(1)).thenThrow(new SQLDataException("Data type BLOB cannot be decoded as Reader"));
    assertThat(RptResultStruct.getStructure(rows)).contains("<td>select &lt;1&gt; é</td>");
    verify(rows, never()).getCharacterStream(anyInt());
}

@Test
void shouldRenderAnEmptyCell_whenTheBinaryValueIsNull() throws Exception {
    var rows = oneBinaryCell(Types.BLOB);
    when(rows.getBinaryStream(1)).thenReturn(null);
    assertThat(RptResultStruct.getStructure(rows)).contains("<td></td>");
}

@Test
void shouldRenderWithinTheBudget_whenBinaryBytesAreNotText() throws Exception {
    var rows = oneBinaryCell(Types.BLOB);
    when(rows.getBinaryStream(1)).thenReturn(new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 'A'}));
    assertThat(RptResultStruct.getStructure(rows)).contains("A</td>");
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest=RptResultStructUnitTest`
Expected: the first and third fail with `SQLDataException` or a null stream; the existing case still passes.

- [ ] **Step 3: Open the cell through `ReportJdbcTextReader`**

In `appendCell`, replace `resultSet.getCharacterStream(column)` with `ReportJdbcTextReader.open(resultSet, column, columnType)`. `supportsCharacterStream` is unchanged.

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -B -ntp test -Dtest='RptResultStructUnitTest,RptByExampleDataUnitTest,ReportBinaryReadersUnitTest'`
Expected: PASS.

- [ ] **Step 5: Add the physical case**

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldRenderBinaryBlobCells_inQueryByExampleResults(boolean legacy) throws Exception {
    try (var source = source(legacy, "jdbcCompliantTruncation=false");
         Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE qbe_blob (id INT PRIMARY KEY, query BLOB, note TEXT)");
        statement.executeUpdate("INSERT INTO qbe_blob VALUES (1, 'select <1>', 'text'), (2, NULL, NULL)");
        try (ResultSet rows = statement.executeQuery("SELECT query, note FROM qbe_blob ORDER BY id")) {
            assertThat(io.github.carlos_emr.carlos.report.data.RptResultStruct.getStructure(rows))
                    .contains("<td>select &lt;1&gt;</td>").contains("<td>text</td>").contains("<td></td>");
        }
    }
}
```

Run the physical command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/report/data/RptResultStruct.java \
  src/test/java/io/github/carlos_emr/carlos/report/data/RptResultStructUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java
git commit -m "fix(report): read binary cells as text in Query by Example results"
```

### Task 4: Session defaults: no `IGNORE_SPACE`, write timeout 600 (N3, N4)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java` (`settings` :200-241, `canonicalOptionKey` :394-417)
- Test: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibilityUnitTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`

**Interfaces:**
- Produces:

```java
/** The session mode without the token the client capability adds; spec section 8. */
static final String SESSION_SQL_MODE = "IF(FIND_IN_SET('IGNORE_SPACE',@@GLOBAL.sql_mode)>0,@@SESSION.sql_mode,"
        + "TRIM(BOTH ',' FROM REPLACE(CONCAT(',',@@SESSION.sql_mode,','),',IGNORE_SPACE,',',')))";
private static void applySessionDefaults(Properties properties);
```

- [ ] **Step 1: Write the failing unit tests**

```java
private static final String PACKAGED =
        "jdbc:mysql://db/carlos?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false";
private static final String DEFAULTS = "SET SESSION net_write_timeout=600, sql_mode=" + MariaDbJdbcCompatibility.SESSION_SQL_MODE;

private static String initSql(String url) throws Exception {
    return MariaDbJdbcCompatibility.settings(url, new Properties()).properties().getProperty("initSql");
}

@Test
void shouldRestoreConnectorJSessionDefaults_forPackagedOptions() throws Exception {
    assertThat(initSql(PACKAGED)).isEqualTo(DEFAULTS);
}

@Test
void shouldApplyTheSameDefaults_forNativeMariaDbUrls() throws Exception {
    assertThat(initSql("jdbc:mariadb://db/carlos")).isEqualTo(DEFAULTS);
}

@Test
void shouldRunTheOperatorsInitSqlLast_whenOneIsConfigured() throws Exception {
    assertThat(initSql(PACKAGED + "&initSql=SET%20%40a%3D1")).isEqualTo(DEFAULTS + ";SET @a=1");
}

@ParameterizedTest
@ValueSource(strings = {"sessionVariables", "sessionvariables", "SESSIONVARIABLES"})
void shouldLeaveSqlModeToTheOperator_whenSessionVariablesSetIt(String spelling) throws Exception {
    var properties = MariaDbJdbcCompatibility.settings(
            PACKAGED + "&" + spelling + "=SQL_MODE%3D%27IGNORE_SPACE%27", new Properties()).properties();
    assertThat(properties.getProperty("initSql")).isEqualTo("SET SESSION net_write_timeout=600");
    assertThat(properties.getProperty("sessionVariables")).isEqualTo("SQL_MODE='IGNORE_SPACE'");
}

@Test
void shouldLeaveTheWriteTimeoutToTheOperator_whenInitSqlSetsIt() throws Exception {
    assertThat(initSql(PACKAGED + "&INITSQL=SET%20NET_WRITE_TIMEOUT%3D120"))
            .isEqualTo("SET SESSION sql_mode=" + MariaDbJdbcCompatibility.SESSION_SQL_MODE + ";SET NET_WRITE_TIMEOUT=120");
}

@Test
void shouldAddNothing_whenTheOperatorSetsBoth() throws Exception {
    assertThat(initSql(PACKAGED + "&sessionVariables=net_write_timeout%3D120,sql_mode%3D%27%27")).isNull();
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest=MariaDbJdbcCompatibilityUnitTest`
Expected: compilation failure, `SESSION_SQL_MODE` not found.

- [ ] **Step 3: Implement**

`canonicalOptionKey` maps `initsql` to `initSql` and `sessionvariables` to `sessionVariables`. `settings()` calls `applySessionDefaults(properties)` directly after `properties.putIfAbsent("permitRedirect", FALSE)`. `applySessionDefaults` follows spec section 8: it looks for `net_write_timeout` and `sql_mode` (lower-cased substring) in the operator's `sessionVariables` and `initSql`, builds `SET SESSION ` plus the parts not claimed, joined by `", "`, and stores it in `initSql` followed by `";"` and the operator's `initSql` when there is one. A comment says why `sessionVariables` cannot carry the expression.

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,JdbcMetadataDefaultUnitTest,CdsJdbcConnectionTimeoutsUnitTest'`
Expected: PASS. In the dry run no existing test needed a change for this task.

- [ ] **Step 5: Add the physical cases**

The class fixture's `source()` resets `sql_mode` itself, so these open connections directly.

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldOpenSessionsWithConnectorJDefaults_forSqlModeAndWriteTimeout(boolean legacy) throws Exception {
    String url = (legacy ? server.replace("jdbc:mariadb:", "jdbc:mysql:") : server) + database + "?jdbcCompliantTruncation=false";
    try (Connection connection = new MariaDbCompatibilityDriver().connect(url, credentials);
         Statement statement = connection.createStatement();
         ResultSet session = statement.executeQuery("SELECT @@SESSION.net_write_timeout,"
                 + " FIND_IN_SET('IGNORE_SPACE', @@SESSION.sql_mode) > 0, FIND_IN_SET('IGNORE_SPACE', @@GLOBAL.sql_mode) > 0")) {
        assertThat(session.next()).isTrue();
        assertThat(session.getInt(1)).isEqualTo(600);
        assertThat(session.getBoolean(2)).isEqualTo(session.getBoolean(3));
    }
    try (Connection connection = new MariaDbCompatibilityDriver().connect(
                 url + "&sessionVariables=sql_mode%3D%27IGNORE_SPACE%27,net_write_timeout%3D120", credentials);
         Statement statement = connection.createStatement();
         ResultSet session = statement.executeQuery("SELECT @@SESSION.net_write_timeout, @@SESSION.sql_mode")) {
        assertThat(session.next()).isTrue();
        assertThat(session.getInt(1)).isEqualTo(120);
        assertThat(session.getString(2)).isEqualTo("IGNORE_SPACE");
    }
}

@Test
void shouldKeepIgnoreSpace_whenTheServerDefaultHasIt() throws Exception {
    String ansi = "REAL_AS_FLOAT,PIPES_AS_CONCAT,ANSI_QUOTES,IGNORE_SPACE,ANSI";
    try (Connection connection = DriverManager.getConnection(server, credentials);
         Statement statement = connection.createStatement()) {
        assertThat(sessionModeFor(statement, "IGNORE_SPACE", "")).isEmpty();
        assertThat(sessionModeFor(statement, "ANSI_QUOTES,IGNORE_SPACE,STRICT_ALL_TABLES", "")).isEqualTo("ANSI_QUOTES,STRICT_ALL_TABLES");
        assertThat(sessionModeFor(statement, "STRICT_ALL_TABLES", "")).isEqualTo("STRICT_ALL_TABLES");
        assertThat(sessionModeFor(statement, "IGNORE_SPACE,STRICT_ALL_TABLES", "IGNORE_SPACE")).isEqualTo("IGNORE_SPACE,STRICT_ALL_TABLES");
        assertThat(sessionModeFor(statement, ansi, ansi)).isEqualTo(ansi);
    }
}

/** Evaluates the adapter's own expression on the server with literals in place of the two system variables. */
private static String sessionModeFor(Statement statement, String session, String global) throws SQLException {
    String expression = MariaDbJdbcCompatibility.SESSION_SQL_MODE
            .replace("@@GLOBAL.sql_mode", "'" + global + "'").replace("@@SESSION.sql_mode", "'" + session + "'");
    try (ResultSet rows = statement.executeQuery("SELECT " + expression)) {
        rows.next();
        return rows.getString(1);
    }
}
```

The second case changes no server setting; it must not use `SET GLOBAL`, because other test forks share the server. Run the physical command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibilityUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java
git commit -m "fix(db): open sessions without IGNORE_SPACE and with the streaming write timeout"
```

### Task 5: SMALLINT objects and lock-wait timeouts (N5, N6)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java` (`legacyNumberValue` :336-355, `columnClassName` :475-482, `invokeNative` :485-497)
- Test: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverNumberUnitTest.java`
- Create: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverLockTimeoutUnitTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`

**Interfaces:**
- Produces: `private static SQLException lockWaitTimeout(SQLException failure)` in `JdbcHandler`.

- [ ] **Step 1: Write the failing SMALLINT tests**

In `MariaDbCompatibilityDriverNumberUnitTest`, using its `withNativeRows(ResultSet, RowsCheck)`:

```java
@Test
void shouldReturnIntegerObjects_forSmallIntColumns() throws Exception {
    ResultSet nativeRows = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);
    when(nativeRows.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnType(1)).thenReturn(Types.SMALLINT);
    when(metadata.getColumnClassName(1)).thenReturn("java.lang.Short");
    when(nativeRows.getObject(1)).thenReturn((short) 5);
    when(nativeRows.getInt(1)).thenReturn(5);
    withNativeRows(nativeRows, rows -> {
        assertThat(rows.getObject(1)).isInstanceOf(Integer.class).isEqualTo(5);
        assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo("java.lang.Integer");
    });
}

@Test
void shouldKeepSqlNull_forSmallIntObjects() throws Exception {
    ResultSet nativeRows = mock(ResultSet.class);
    ResultSetMetaData metadata = mock(ResultSetMetaData.class);
    when(nativeRows.getMetaData()).thenReturn(metadata);
    when(metadata.getColumnType(1)).thenReturn(Types.SMALLINT);
    when(nativeRows.getInt(1)).thenReturn(0);
    when(nativeRows.wasNull()).thenReturn(true);
    withNativeRows(nativeRows, rows -> {
        assertThat(rows.getObject(1)).isNull();
        assertThat(rows.wasNull()).isTrue();
    });
}
```

- [ ] **Step 2: Write the failing lock-wait tests**

```java
@Tag("unit")
class MariaDbCompatibilityDriverLockTimeoutUnitTest {
    @FunctionalInterface
    interface Check {
        void accept(Statement statement, org.mariadb.jdbc.Statement nativeStatement) throws Exception;
    }

    private static void withStatement(Check check) throws Exception {
        org.mariadb.jdbc.Statement nativeStatement = mock(org.mariadb.jdbc.Statement.class);
        org.mariadb.jdbc.Connection physical = mock(org.mariadb.jdbc.Connection.class, Answers.RETURNS_DEEP_STUBS);
        when(physical.createStatement()).thenReturn(nativeStatement);
        when(physical.getContext().getConnectionTimeZone()).thenReturn(TimeZone.getTimeZone("UTC"));
        when(physical.unwrap(org.mariadb.jdbc.Connection.class)).thenReturn(physical);
        try (MockedConstruction<org.mariadb.jdbc.Driver> drivers = mockConstruction(org.mariadb.jdbc.Driver.class,
                (driver, context) -> when(driver.connect(anyString(), any(Properties.class))).thenReturn(physical))) {
            Connection connection = new MariaDbCompatibilityDriver().connect("jdbc:mysql://127.0.0.1:3306/probe", new Properties());
            assertThat(drivers.constructed()).isNotEmpty();
            check.accept(connection.createStatement(), nativeStatement);
        }
    }

    @Test
    void shouldReportALockWaitTimeoutAsARollback_whenTheServerSendsHy000() throws Exception {
        SQLException server = new SQLException("(conn=7) Lock wait timeout exceeded; try restarting transaction", "HY000", 1205);
        withStatement((statement, nativeStatement) -> {
            when(nativeStatement.executeUpdate(anyString())).thenThrow(server);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE t SET a = 1"))
                    .isInstanceOfSatisfying(SQLTransactionRollbackException.class, timeout -> {
                        assertThat(timeout.getSQLState()).isEqualTo("40001");
                        assertThat(timeout.getErrorCode()).isEqualTo(1205);
                        assertThat(timeout.getMessage()).isEqualTo(server.getMessage());
                        assertThat(timeout.getCause()).isSameAs(server);
                        assertThat(new org.springframework.jdbc.support.SQLExceptionSubclassTranslator().translate("task", null, timeout))
                                .isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
                    });
        });
    }

    @Test
    void shouldPassOtherFailuresThrough_unchanged() throws Exception {
        SQLException lockTableFull = new SQLException("The total number of locks exceeds the lock table size", "HY000", 1206);
        SQLException deadlock = new SQLTransactionRollbackException("Deadlock found when trying to get lock", "40001", 1213);
        withStatement((statement, nativeStatement) -> {
            when(nativeStatement.executeUpdate("a")).thenThrow(lockTableFull);
            when(nativeStatement.executeUpdate("b")).thenThrow(deadlock);
            assertThatThrownBy(() -> statement.executeUpdate("a")).isSameAs(lockTableFull);
            assertThatThrownBy(() -> statement.executeUpdate("b")).isSameAs(deadlock);
        });
    }

    @Test
    void shouldKeepABatchFailure_withItsUpdateCounts() throws Exception {
        BatchUpdateException batch = new BatchUpdateException("Lock wait timeout exceeded", "HY000", 1205, new int[]{1, -3});
        withStatement((statement, nativeStatement) -> {
            when(nativeStatement.executeBatch()).thenThrow(batch);
            assertThatThrownBy(statement::executeBatch).isSameAs(batch);
        });
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest='MariaDbCompatibilityDriverNumberUnitTest,MariaDbCompatibilityDriverLockTimeoutUnitTest'`
Expected: the SMALLINT cases see `Short`; the first lock-wait case sees a plain `SQLException`.

- [ ] **Step 4: Implement**

- `legacyNumberValue`: when `requested == Object.class` and `sqlType == Types.SMALLINT`, return `rows.getInt(column)` boxed, or `null` with `convertedWasNull` set when `rows.wasNull()`. Typed reads (`getObject(column, Short.class)`) are not touched.
- `columnClassName`: `java.lang.Integer` for a `Types.SMALLINT` column.
- `invokeNative`: `if (cause instanceof SQLException sql) throw lockWaitTimeout(sql);`. `lockWaitTimeout` returns its argument unless `getErrorCode() == 1205` and it is neither a `SQLTransactionRollbackException` nor a `BatchUpdateException`; then it returns `new SQLTransactionRollbackException(failure.getMessage(), "40001", 1205, failure)`. A comment names MySQL Connector/J's mapping and why the batch exception is kept.

- [ ] **Step 5: Run them and see them pass**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest'`
Expected: PASS.

- [ ] **Step 6: Add the physical cases**

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldReturnIntegerObjects_forSmallIntColumns(boolean legacy) throws Exception {
    for (String options : java.util.List.of("jdbcCompliantTruncation=false", "jdbcCompliantTruncation=false&useServerPrepStmts=true")) {
        try (var source = source(legacy, options); Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS small_probe (id INT PRIMARY KEY, s SMALLINT, u SMALLINT UNSIGNED)");
            statement.executeUpdate("REPLACE INTO small_probe VALUES (1, -32768, 65535), (2, NULL, NULL)");
            try (java.sql.PreparedStatement query = connection.prepareStatement("SELECT s, u FROM small_probe ORDER BY id");
                 ResultSet rows = query.executeQuery()) {
                assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo("java.lang.Integer");
                assertThat(rows.getMetaData().getColumnClassName(2)).isEqualTo("java.lang.Integer");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isInstanceOf(Integer.class).isEqualTo(-32768);
                assertThat(rows.getObject("u")).isInstanceOf(Integer.class).isEqualTo(65535);
                assertThat(rows.getObject(1, Short.class)).isEqualTo((short) -32768);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isNull();
                assertThat(rows.wasNull()).isTrue();
            }
        }
    }
}

@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldReportLockWaitTimeoutsAsTransactionRollbacks_forWaitingAndNowaitLocks(boolean legacy) throws Exception {
    try (var source = source(legacy, "jdbcCompliantTruncation=false");
         Connection holder = source.getConnection(); Connection waiter = source.getConnection();
         Statement holding = holder.createStatement(); Statement waiting = waiter.createStatement()) {
        holding.executeUpdate("CREATE TABLE lock_probe (id INT PRIMARY KEY, v INT) ENGINE=InnoDB");
        holding.executeUpdate("INSERT INTO lock_probe VALUES (1, 1)");
        holder.setAutoCommit(false);
        try (ResultSet locked = holding.executeQuery("SELECT v FROM lock_probe WHERE id = 1 FOR UPDATE")) {
            assertThat(locked.next()).isTrue();
        }
        waiting.execute("SET SESSION innodb_lock_wait_timeout = 1");
        for (String blocked : java.util.List.of("UPDATE lock_probe SET v = 2 WHERE id = 1",
                "SELECT v FROM lock_probe WHERE id = 1 FOR UPDATE NOWAIT")) {
            assertThatThrownBy(() -> waiting.execute(blocked)).as(blocked)
                    .isInstanceOfSatisfying(java.sql.SQLTransactionRollbackException.class, timeout -> {
                        assertThat(timeout.getSQLState()).isEqualTo("40001");
                        assertThat(timeout.getErrorCode()).isEqualTo(1205);
                    });
        }
        var template = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(waiter, true));
        assertThatThrownBy(() -> template.queryForList("SELECT v FROM lock_probe WHERE id = 1 FOR UPDATE NOWAIT"))
                .isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
        holder.rollback();
    }
}
```

Run the physical command. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverNumberUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverLockTimeoutUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java
git commit -m "fix(db): return Integer for SMALLINT objects and report lock-wait timeouts as rollbacks"
```

### Task 6: Name the refused JDBC option (#5770)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java` (`settings` :209, `put` :471-472, `validateTlsSettings` :546 and :551-553, `putCollation` :621, `invalid` :814)
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java:341-348`
- Test: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibilityUnitTest.java`, `OscarTrackingBasicDataSourceUnitTest.java`

**Interfaces:**
- Produces: `private static SQLException invalidOption(String option, String requirement)` returning `new SQLException("Invalid database connection configuration: option " + option + "; " + requirement, "08001")`. It is called only with the fixed names below, never with text taken from the URL. Draft PR #6133 has the same five calls with the same text, so it will merge later without a conflict of meaning.

| Refusal | `option` | `requirement` |
|---|---|---|
| `characterEncoding` other than UTF-8, UTF8, utf8mb4 | `characterEncoding` | `use UTF-8, UTF8 or utf8mb4` |
| `connectionCollation` that is not utf8mb4 | `connectionCollation` | `use a utf8mb4 collation` |
| `dnsSrv=true` | `dnsSrv` | `DNS SRV is unsupported; configure an explicit host` |
| `fipsCompliantJsse=true` | `fipsCompliantJsse` | `explicit FIPS JSSE selection is unsupported` |
| a JSSE provider option | that entry of `UNSUPPORTED_TLS_PROVIDERS` | `explicit JSSE provider selection is unsupported` |

- [ ] **Step 1: Write the failing tests**

```java
@ParameterizedTest
@CsvSource(delimiter = '|', value = {
        "characterEncoding=latin1|Invalid database connection configuration: option characterEncoding; use UTF-8, UTF8 or utf8mb4",
        "connectionCollation=utf8_general_ci|Invalid database connection configuration: option connectionCollation; use a utf8mb4 collation",
        "dnsSrv=true|Invalid database connection configuration: option dnsSrv; DNS SRV is unsupported; configure an explicit host",
        "fipsCompliantJsse=true|Invalid database connection configuration: option fipsCompliantJsse; explicit FIPS JSSE selection is unsupported",
        "keyStoreProvider=BCFIPS|Invalid database connection configuration: option keyStoreProvider; explicit JSSE provider selection is unsupported"})
void shouldNameTheRefusedOption_withoutItsValue(String option, String message) {
    assertThatThrownBy(() -> MariaDbJdbcCompatibility.settings("jdbc:mysql://db/carlos?" + option, new Properties()))
            .isInstanceOf(SQLException.class).hasMessage(message).hasNoCause()
            .extracting(failure -> ((SQLException) failure).getSQLState()).isEqualTo("08001");
}
```

and in `OscarTrackingBasicDataSourceUnitTest`:

```java
@Test
void shouldKeepTheOptionName_whenTheUrlIsRefusedAtConfiguration() {
    var source = new OscarTrackingBasicDataSource();
    assertThatThrownBy(() -> source.setUrl("jdbc:mysql://db/carlos?characterEncoding=latin1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid database connection configuration: option characterEncoding; use UTF-8, UTF8 or utf8mb4");
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest='MariaDbJdbcCompatibilityUnitTest,OscarTrackingBasicDataSourceUnitTest'`
Expected: FAIL; the messages are the bare text.

- [ ] **Step 3: Implement**

Add `invalidOption` beside `invalid()` and use it at the five refusals in the table. Every other `invalid()` stays (malformed URLs, hosts, reserved names, conflicting spellings, unknown values of other options): those can involve text the operator typed. In `OscarTrackingBasicDataSource.hasExplicitTimezone`, rethrow `new IllegalArgumentException(invalid.getMessage())` instead of the fixed text. `FlywayRunner` is not changed: it keeps its one fixed line for any refused URL (spec section 9).

- [ ] **Step 4: Update the four existing assertions of the old message**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,JdbcMetadataDefaultUnitTest'`
Expected: the new cases pass and exactly these existing methods of `MariaDbJdbcCompatibilityUnitTest` fail (11 cases). Give each its new message from the table:

| Method | Assertion to change |
|---|---|
| `shouldRejectUnsupportedConfiguration_withoutEchoingCredentials` | the first one (`characterEncoding=latin1`); the `password=%invalid` one keeps the bare message |
| `shouldDistinguishLegacyPreferredTls_fromExplicitVerifiedModes` | the last one (`dnsSrv=true`) |
| `shouldRejectUnsupportedCollation_withoutEchoingOptionValues` | both, for every value: `option connectionCollation; use a utf8mb4 collation` |
| `shouldRejectUnsupportedSecurityProviders_withoutIgnoringTheRequestedPolicy` | per parameter: the FIPS text for `fipsCompliantJsse`, the provider text with the option's own name for the other four |

Any other failure is a defect in Step 3. Rerun; expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java \
  src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibilityUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSourceUnitTest.java
git commit -m "fix(db): name the JDBC option a connection configuration is refused for"
```

### Task 7: `AuditLogManager` reads the schema name safely (#5863)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java:67` and `purgeAuditLog` (:73)
- Create: `src/test/java/io/github/carlos_emr/carlos/managers/AuditLogManagerUnitTest.java`

**Interfaces:**
- Produces: `static String schemaName(String configuredName)` (package-private) in `AuditLogManager`.

- [ ] **Step 1: Write the failing test**

```java
@Tag("unit")
class AuditLogManagerUnitTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "carlos|carlos", "carlos?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true|carlos",
            "carlos?|carlos", " carlos ?x=1|carlos", "''|''", "?x=1|''", "NULL|''"})
    void shouldReadTheSchemaName_forEveryShapeOfDbName(String configured, String expected) {
        assertThat(AuditLogManager.schemaName(configured)).isEqualTo(expected);
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=AuditLogManagerUnitTest`
Expected: compilation failure, `schemaName` not found.

- [ ] **Step 3: Implement**

`schemaName` returns `""` for null, otherwise the trimmed text before the first `?` (the split `QueryByExampleSqlValidator.applicationSchema` uses). The field becomes `String dbName = schemaName(CarlosProperties.getInstance().getProperty("db_name"));`. `purgeAuditLog` throws `new Exception("No database name has been defined. Please set db_name in the properties file")`, after its `mysqldump` check and in the same style, when `dbName` is empty. Nothing else in the class changes; the purge rewrite belongs to draft PR #6133.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest=AuditLogManagerUnitTest`
Expected: PASS, 7 cases.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java \
  src/test/java/io/github/carlos_emr/carlos/managers/AuditLogManagerUnitTest.java
git commit -m "fix(audit): start when db_name carries no JDBC options"
```

### Task 8: The appointment export recognises MariaDB (N7)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoImpl.java:593-599`
- Create: `src/test/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoStreamingUnitTest.java`

**Interfaces:**
- Produces: `static int streamingFetchSize(String productName)` (package-private) in `OscarAppointmentDaoImpl`.

- [ ] **Step 1: Write the failing test**

```java
@Tag("unit")
class OscarAppointmentDaoStreamingUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "MariaDB"})
    void shouldRequestTheStreamingSentinel_forMySqlAndMariaDb(String product) {
        assertThat(OscarAppointmentDaoImpl.streamingFetchSize(product)).isEqualTo(Integer.MIN_VALUE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"H2", "PostgreSQL"})
    void shouldUseABatchHint_forOtherProducts(String product) {
        assertThat(OscarAppointmentDaoImpl.streamingFetchSize(product)).isEqualTo(500);
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=OscarAppointmentDaoStreamingUnitTest`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`streamPatientAppointments` passes the product name from `connection.getMetaData().getDatabaseProductName()` to `streamingFetchSize` and gives the result to `query.setFetchSize`. Rewrite the comment: the adapter reports `MariaDB` and maps the sentinel to MariaDB's row-by-row streaming. This changes the export on MariaDB from 500 rows per read to one row per read, as `LegacyJdbcQuery` already reads reports; the rows are the same.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest='OscarAppointmentDaoStreamingUnitTest,PatientListByApptExportUnitTest'`
Expected: PASS. The MariaDB lane of Task 13 runs the export's DAO test on the physical server.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoImpl.java \
  src/test/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoStreamingUnitTest.java
git commit -m "fix(appointment): ask for a streamed export on MariaDB by name"
```

### Task 9: Report and dashboard SQL taken from request or saved text (security, spec section 11)

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/report/data/RptDemographicColumnNames.java`, `RptDemographicQuery2Builder.java`, `RptDemographicQueryBuilder.java`, `RptDemographicQuery2Saver.java`, `src/main/java/io/github/carlos_emr/carlos/prev/reports/ReportBuilder.java`
- Create: `src/main/java/io/github/carlos_emr/carlos/report/data/DemographicAgeMonths.java`, `ReportFilterResolver.java`
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/dao/ReportFilterDao.java`, `ReportFilterDaoImpl.java`, `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptFormQuery.java`, `RptDownloadCSVServlet.java`
- Modify: `src/main/java/io/github/carlos_emr/carlos/managers/DashboardManagerImpl.java`, `src/main/java/io/github/carlos_emr/carlos/dashboard/handler/IndicatorTemplateXML.java`
- Create: `src/test/java/io/github/carlos_emr/carlos/report/data/DemographicAgeMonthsUnitTest.java`, `ReportFilterResolverUnitTest.java`, `RptDemographicQueryColumnsUnitTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/report/pageUtil/RptDownloadCSVServletTest.java`, `RptDownloadCSVExtensionBudgetUnitTest.java`, `src/test/java/io/github/carlos_emr/carlos/managers/DashboardProviderContextUnitTest.java`, `src/test/java/io/github/carlos_emr/carlos/dashboard/handler/IndicatorTemplateXMLUnitTest.java`, `src/test/java/io/github/carlos_emr/carlos/commn/dao/ReportFilterDaoIntegrationTest.java`, `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`, `scripts/report-typed-queries-playwright-checks.js`

**Interfaces:**
- Produces:

```java
// RptDemographicColumnNames: exactly the keys of its column-title table (the 29 columns the report page offers)
public boolean isSelectable(String column);
// DemographicAgeMonths (static, final, private constructor): "[0-9]{1,4}m" -> months, else
// IllegalArgumentException("An age must be a whole number of years, or of months followed by m")
public static int parse(String text);
// ReportFilterDao / ReportFilterDaoImpl: report and position are unique together (UNIQUE KEY order_no)
ReportFilter findByReportIdAndOrderNo(int reportId, int orderNo);   // either status; null when absent
// ReportFilterResolver
public ReportFilterResolver(ReportFilterDao dao);
public List<ReportFilter> selected(String reportId, HttpServletRequest request);
// IndicatorTemplateXML
static String providerNumberForSql(String providerNo);   // "[A-Za-z0-9_-]{0,6}", else IllegalArgumentException
```

- [ ] **Step 1: Write the failing tests for the demographic and prevention reports**

```java
@Tag("unit")
class DemographicAgeMonthsUnitTest {
    @ParameterizedTest
    @CsvSource({"0m,0", "6m,6", "18m,18", "06m,6", "9999m,9999"})
    void shouldReadTheNumberOfMonths_forAgesWrittenInMonths(String text, int months) {
        assertThat(DemographicAgeMonths.parse(text)).isEqualTo(months);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"m", "6", "6M", " 6m", "6m ", "-6m", "6.5m", "10000m", "6 month", "6m)"})
    void shouldRefuseText_thatIsNotAWholeNumberOfMonths(String text) {
        assertThatThrownBy(() -> DemographicAgeMonths.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An age must be a whole number of years, or of months followed by m");
    }
}
```

`RptDemographicQueryColumnsUnitTest` extends `CarlosUnitTestBase` and registers mocks of `FormsDao` (answering `runParameterizedNativeQuery(anyString(), anyMap())` with an empty list) and `DemographicQueryFavouritesDao`. Its form helper sets `select` and `ageStyle "1"`.

```java
@Test
void shouldRefuseAColumnOutsideTheReportsList_beforeAnyQuery() {
    assertThatThrownBy(() -> new RptDemographicQuery2Builder().buildQuery(mock(LoggedInInfo.class),
            form("last_name", "last_name, (select 1)")))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown demographic report column");
    assertThatThrownBy(() -> new RptDemographicQueryBuilder().buildQuery(mock(LoggedInInfo.class),
            form("last_name", "LAST_NAME")))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown demographic report column");
    verifyNoInteractions(forms);
}

@Test
void shouldRefuseToSaveAQuery_withAColumnOutsideTheReportsList() {
    assertThatThrownBy(() -> new RptDemographicQuery2Saver().saveQuery(form("hin", "hin from security")))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown demographic report column");
    verifyNoInteractions(favourites);
}

@Test
void shouldAcceptEveryColumnTheReportPageOffers() throws Exception {
    String[] offered = {"address", "chart_no", "city", "date_joined", "date_of_birth", "demographic_no", "eff_date",
            "email", "end_date", "family_doctor", "first_name", "hc_renew_date", "hc_type", "hin", "last_name",
            "month_of_birth", "newsletter", "patient_status", "pcn_indicator", "phone", "phone2", "postal",
            "provider_name", "provider_no", "province", "roster_status", "sex", "ver", "year_of_birth"};
    assertThat(new RptDemographicQuery2Builder().buildQuery(mock(LoggedInInfo.class), form(offered))).isEmpty();
    assertThat(new RptDemographicQueryBuilder().buildQuery(mock(LoggedInInfo.class), form(offered))).isEmpty();
}

@SuppressWarnings("unchecked")
@Test
void shouldBindAnAgeInMonths_insteadOfWritingItIntoTheQuery() {
    var form = form("demographic_no");
    form.setAge("4");
    form.setStartYear("18m");
    form.setEndYear("5");
    new RptDemographicQuery2Builder().buildQuery(mock(LoggedInInfo.class), form);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
    verify(forms).runParameterizedNativeQuery(sql.capture(), params.capture());
    assertThat(sql.getValue()).contains("interval :startMonths4 month").doesNotContain("18");
    assertThat(params.getValue()).containsEntry("startMonths4", 18);
}

@Test
void shouldRefuseAnAgeThatIsNeitherYearsNorMonths_beforeAnyQuery() {
    var form = form("demographic_no");
    form.setAge("4");
    form.setStartYear("6 month");
    form.setEndYear("5");
    assertThatThrownBy(() -> new RptDemographicQuery2Builder().buildQuery(mock(LoggedInInfo.class), form))
            .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(forms);
}
```

The list in `shouldAcceptEveryColumnTheReportPageOffers` is the `select` checkbox values of `WEB-INF/jsp/oscarReport/ReportDemographicReport.jsp`; check it against that page before writing it.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest='DemographicAgeMonthsUnitTest,RptDemographicQueryColumnsUnitTest'`
Expected: compilation failure, `DemographicAgeMonths` and `isSelectable` not found.

- [ ] **Step 3: Implement the column list and the bound age**

- `RptDemographicColumnNames.isSelectable(String)` as in Interfaces (exact, case-sensitive match).
- In `RptDemographicQuery2Builder.buildQuery` and `RptDemographicQueryBuilder.buildQuery`, directly after `RptDemographicColumnNames demoCols = new RptDemographicColumnNames();`, refuse the request with `new IllegalArgumentException("Unknown demographic report column")` when any `select` entry is not selectable. This runs before the SQL is built; the builder's existing `try … catch (Exception)` around the query, which turns a failed query into an empty result, does not reach it.
- `RptDemographicQuery2Saver.saveQuery` makes the same check before anything is stored.
- In both builders and in `ReportBuilder` (age style 4, the months branch), replace `getInterval(startYear)` / `getInterval(endYear)` and the text spliced after `interval` with bound parameters: `params.put("startMonths4", DemographicAgeMonths.parse(startYear))` and `",interval :startMonths4 month"`, likewise `endMonths4`. Delete the three `getInterval` methods and their "cannot be parameterized" comments; MariaDB binds the quantity of an `INTERVAL` (Step 8 pins it).

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -B -ntp test -Dtest='DemographicAgeMonthsUnitTest,RptDemographicQueryColumnsUnitTest,RptDemographicReport2ActionUnitTest,PreventionReport2ActionUnitTest,ReportingServiceUnitTest'`
Expected: PASS (22 cases in the two new classes).

- [ ] **Step 5: Write the failing tests for configured-report filters and the CSV download**

```java
@Tag("unit")
class ReportFilterResolverUnitTest {
    private final ReportFilterDao dao = mock(ReportFilterDao.class);
    private final ReportFilterResolver resolver = new ReportFilterResolver(dao);

    private ReportFilter stored(int position, String value, String dateFormat) {
        ReportFilter filter = new ReportFilter();
        filter.setId(100 + position);   // AbstractModel.equals needs an id
        filter.setReportId(7);
        filter.setOrderNo(position);
        filter.setValue(value);
        filter.setDateFormat(dateFormat);
        when(dao.findByReportIdAndOrderNo(7, position)).thenReturn(filter);
        return filter;
    }

    @Test
    void shouldReturnTheStoredFilters_inPositionOrderAndIgnoringPostedSql() {
        ReportFilter second = stored(2, "demographic.sex='${sex}'", "");
        ReportFilter first = stored(1, "formBCAR.c_EDD>='${startDate1}'", "yyyy-MM-dd");
        var request = new MockHttpServletRequest();
        request.addParameter("filter_2", "on");
        request.addParameter("filter_1", "on");
        request.addParameter("value_1", "posted text that must not be used");
        request.addParameter("dateFormat_1", "dd/MM/yyyy");
        assertThat(resolver.selected("7", request)).containsExactly(first, second);
    }

    @Test
    void shouldReturnNothing_whenTheRequestSelectsNoFilter() {
        var request = new MockHttpServletRequest();
        request.addParameter("value_1", "posted text");
        assertThat(resolver.selected(null, request)).isEmpty();
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRefuseTheReport_whenASelectedFilterIsNotStored() {
        stored(1, "demographic.sex='${sex}'", "");
        var request = new MockHttpServletRequest();
        request.addParameter("filter_1", "on");
        request.addParameter("filter_9", "on");
        assertThatThrownBy(() -> resolver.selected("7", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The report filter is not one of this report's filters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x", "-1", "7 ", "1234567890"})
    void shouldRefuseTheReport_whenTheReportIdIsNotANumber(String reportId) {
        var request = new MockHttpServletRequest();
        request.addParameter("filter_1", "on");
        assertThatThrownBy(() -> resolver.selected(reportId, request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRefuseTheReport_whenAFilterPositionIsNotANumber() {
        var request = new MockHttpServletRequest();
        request.addParameter("filter_x", "on");
        assertThatThrownBy(() -> resolver.selected("7", request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dao);
    }
}
```

In `RptDownloadCSVServletTest` and `RptDownloadCSVExtensionBudgetUnitTest`:

- add these members, and build every servlet with `servlet()` instead of `new RptDownloadCSVServlet()`:

```java
private final SecurityInfoManager security = permitting();
private final ReportFilterDao filterDao = mock(ReportFilterDao.class);

private static SecurityInfoManager permitting() {
    SecurityInfoManager manager = mock(SecurityInfoManager.class);
    when(manager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
    return manager;
}

private RptDownloadCSVServlet servlet() {
    return new RptDownloadCSVServlet(security, filterDao);
}

/** Stores a filter of report 7 at a position, as the report configuration page would. */
private void storeFilter(int position, String expression) {
    ReportFilter filter = new ReportFilter();
    filter.setReportId(7);
    filter.setOrderNo(position);
    filter.setValue(expression);
    filter.setDateFormat("");
    when(filterDao.findByReportIdAndOrderNo(7, position)).thenReturn(filter);
}
```

- after every `request.getSession(true);`, put the user in the session: `LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());` (13 places in the first class, 1 in the second);
- where a test posts `value_N` and `dateFormat_N`, post `id=7` and call `storeFilter(N, <the same expression>)` instead; in `addFilter` this replaces its two `value_`/`dateFormat_` lines.

Then add:

```java
@Test
void shouldRefuseTheDownload_withoutAReportPrivilege() throws Exception {
    var request = baseRequest();
    request.getSession(true);
    LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
    request.setParameter("demoReport", "true");
    request.setParameter("last_name", "on");
    var response = new MockHttpServletResponse();
    when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);
    servlet().service(request, response);
    assertThat(response.getStatus()).isEqualTo(403);
    configuredQueryMock.verifyNoInteractions();
}

@Test
void shouldRefuseTheDownload_withoutALoggedInUser() throws Exception {
    var request = baseRequest();
    request.getSession(true);
    request.setParameter("demoReport", "true");
    var response = new MockHttpServletResponse();
    servlet().service(request, response);
    assertThat(response.getStatus()).isEqualTo(403);
    configuredQueryMock.verifyNoInteractions();
}

@Test
void shouldUseTheStoredFilter_andIgnoreSqlPostedWithTheRequest() throws Exception {
    MockHttpServletRequest request = baseRequest();
    request.addParameter("last_name", "on");
    addFilter(request, 1, "demographic.first_name='${name}'", "name", "FAKE");
    request.addParameter("value_1", "demographic.first_name<>'' or demographic.hin is not null");
    List<SqlCall> sqlCalls = invokeDemoReport(request);
    assertThat(sqlCalls).isNotEmpty().allSatisfy(call -> assertThat(call.sql()).doesNotContain("hin is not null"));
    assertThat(sqlCalls).anySatisfy(call -> assertThat(call.sql()).contains("demographic.first_name=?"));
}

@Test
void shouldRefuseTheReport_whenASelectedFilterIsNotStoredForTheReport() {
    MockHttpServletRequest request = baseRequest();
    request.addParameter("last_name", "on");
    request.addParameter("id", "7");
    request.addParameter("filter_4", "on");
    request.addParameter("value_4", "demographic.first_name='FAKE'");
    assertThatThrownBy(() -> invokeDemoReport(request)).isInstanceOf(IllegalArgumentException.class);
    configuredQueryMock.verifyNoInteractions();
}
```

In `ReportFilterDaoIntegrationTest`, `QueryOperations`:

```java
@Test
@Tag("query")
@DisplayName("should find the filter at a report position of either status")
void shouldFindTheFilterAtAReportPosition_ofEitherStatus() {
    var active = reportFilter(55493, 1, 1);
    var deleted = reportFilter(55493, 0, 2);
    reportFilter(55494, 1, 1);
    assertThat(reportFilterDao.findByReportIdAndOrderNo(55493, 1).getId()).isEqualTo(active.getId());
    assertThat(reportFilterDao.findByReportIdAndOrderNo(55493, 2).getId()).isEqualTo(deleted.getId());
    assertThat(reportFilterDao.findByReportIdAndOrderNo(55493, 3)).isNull();
    assertThat(reportFilterDao.findByReportIdAndOrderNo(55495, 1)).isNull();
}
```

- [ ] **Step 6: Run them and see them fail**

Run: `mvn -B -ntp test -Dtest='ReportFilterResolverUnitTest,RptDownloadCSVServletTest,RptDownloadCSVExtensionBudgetUnitTest,ReportFilterDaoIntegrationTest'`
Expected: compilation failure (`ReportFilterResolver`, `findByReportIdAndOrderNo`, the servlet's two-argument constructor).

- [ ] **Step 7: Implement the stored filters and the download's privilege check**

- `ReportFilterDaoImpl.findByReportIdAndOrderNo`: JPQL on `reportId` and `orderNo`, both bound, `getResultStream().findFirst().orElse(null)`.
- `ReportFilterResolver.selected`: for every request parameter named `filter_<position>` (position: 1 to 9 digits), the stored filter of the report at that position, of either status (the filter page also lists deleted reports), in position order. No `filter_` parameter: an empty list and no lookup. A report id or position that is not 1 to 9 digits, or a position the report has no filter at: `new IllegalArgumentException("The report filter is not one of this report's filters")`. The request's `value_N` and `dateFormat_N` are never read.
- `RptFormQuery`: a public no-argument constructor that calls `this(new ReportFilterResolver(SpringUtils.getBean(ReportFilterDao.class)))`, and a package-private constructor taking the resolver. `getValueParam(String reportId, HttpServletRequest request)` takes each selected filter's `getValue()` and `getDateFormat()`. Remove the now unused `java.util.Enumeration` import (Checkstyle).
- `RptDownloadCSVServlet`:
  - fields `SecurityInfoManager securityInfoManager` and `ReportFilterDao reportFilterDao`; the public no-argument constructor leaves them null and they are looked up through `SpringUtils` on first use, not in `init()` (the servlet loads at startup, possibly before the application context); a package-private constructor sets both.
  - `service`, right after its session check: `LoggedInInfo.getLoggedInInfoFromSession(request)` must be non-null and hold `_report` or `_admin.reporting` read (the same objects `reportResult.jsp` checks); otherwise `response.sendError(403)` and return, before any query.
  - `formReport` builds `new RptFormQuery(new ReportFilterResolver(dao))`.
  - `DemographicCsvReport` receives the resolver and takes its filters from `selected(request.getParameter("id"), request)`. A demographic CSV that ticks a filter without naming a report is refused, not run unfiltered.

- [ ] **Step 8: Run them and see them pass, and pin the interval on MariaDB**

Run: `mvn -B -ntp test -Dtest='ReportFilterResolverUnitTest,RptDownloadCSVServletTest,RptDownloadCSVExtensionBudgetUnitTest,RptFormQueryTest,RptReportCreatorUnitTest,ReportActionSecurityMigrationUnitTest,ReportFilterDaoIntegrationTest'`
Expected: PASS (the servlet class has 45 cases).

H2 has no `date_sub`, so the age-in-months query has never run in the H2 suite. Add to `JdbcConnectorMariaDbIntegrationTest`:

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldSubtractABoundNumberOfMonths_inTheReportAgeFilter(boolean legacy) throws Exception {
    for (String options : java.util.List.of("jdbcCompliantTruncation=false", "jdbcCompliantTruncation=false&useServerPrepStmts=true")) {
        try (var source = source(legacy, options); Connection connection = source.getConnection();
             java.sql.PreparedStatement query = connection.prepareStatement(
                     "SELECT date_sub(?, interval ? month), date_sub(?, interval ? month)")) {
            query.setDate(1, Date.valueOf("2026-11-19"));
            query.setInt(2, 18);
            query.setDate(3, Date.valueOf("2026-03-31"));
            query.setInt(4, 1);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("2025-05-19");
                assertThat(rows.getString(2)).isEqualTo("2026-02-28");
            }
        }
    }
}
```

Run the physical command and, with `CARLOS_TEST_MARIADB_DAO=true CARLOS_TEST_MARIADB_SCHEMA=generated`, `ReportFilterDaoIntegrationTest`. Expected: PASS.

- [ ] **Step 9: Restrict the dashboard provider number**

Tests first. In `DashboardProviderContextUnitTest.prepare()`, give the manager a `PMmodule.dao.ProviderDao` mock whose `providerExists(anyString())` is true (`ReflectionTestUtils.setField(manager, "providerDao", …)`), so the existing cases keep passing, and add:

```java
@Test void shouldRefuseAnotherProvider_whenNoSuchProviderExists() {
    var providers = mock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class);
    when(providers.providerExists("FAKE-provider-A")).thenReturn(false);
    ReflectionTestUtils.setField(manager, "providerDao", providers);
    var request = new MockHttpServletRequest(); request.setParameter("dashboardProviderNo", "FAKE-provider-A"); request(request);
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> manager.getRequestedProviderNo(actor));
    assertEquals("The requested provider does not exist", refused.getMessage());
}
```

In `IndicatorTemplateXMLUnitTest`:

```java
@ParameterizedTest
@ValueSource(strings = {"999998", "-1", "ab_12", "A1-B2", ""})
void shouldAcceptPlainProviderNumbers_forTemplateSubstitution(String providerNo) {
    assertThat(IndicatorTemplateXML.providerNumberForSql(providerNo)).isEqualTo(providerNo);
}

@ParameterizedTest
@NullSource
@ValueSource(strings = {"1234567", "1' or", "1 1", "(1)", "a.b", "1;", "%"})
void shouldRefuseProviderNumbersOfAnyOtherForm_beforeSubstitution(String providerNo) {
    assertThatThrownBy(() -> IndicatorTemplateXML.providerNumberForSql(providerNo))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A dashboard provider number has an unexpected form");
}
```

Then implement:

- `DashboardManagerImpl`: `@Autowired private io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao providerDao;` (the bean `DisplayDrilldown2Action` already uses; there is exactly one). In `getRequestedProviderNo`, after the `_dashboardChgUser` check, a provider other than the actor's own must exist: otherwise `new IllegalArgumentException("The requested provider does not exist")`.
- `IndicatorTemplateXML.setParameterAliasWithValue`: the requested provider (`providerNo != null`) and the `loggedinprovider` alias pass through `providerNumberForSql` before they are returned. The empty value is accepted: a template built without a logged-in user substitutes it, as the existing unit tests show.

Run: `mvn -B -ntp test -Dtest='DashboardProviderContextUnitTest,DashboardManagerEncryptionUnitTest,IndicatorTemplateXMLUnitTest,ShippedIndicatorTemplateSubstitutionUnitTest,DashboardBeanFactoryUnitTest,DrilldownQueryHandlerUnitTest,ExportQueryHandlerUnitTest,IndicatorQueryHandlerUnitTest,AbstractQueryHandlerSubstitutionUnitTest,ExcludeDemographicHandlerUnitTest,DashboardQueryHandlerScopeUnitTest,IndicatorTemplateHandlerUnitTest,IndicatorNativeQueryTypesUnitTest'`
Expected: PASS.

- [ ] **Step 10: Update the browser check that posts report filters**

`scripts/report-typed-queries-playwright-checks.js` posts `value_1` filter text to `/report/ViewReportResult` four times; after Step 7 that text is ignored. Before those steps, insert the filters into `reportFilter` with its `sql.execute` helper (a synthetic report id such as 55490, positions 1 and 2, `status` 1, `date_format` ''), post `id` and `filter_<position>` instead of `value_`/`dateFormat_`, and delete the rows when the check ends. Keep its assertions; the failing-query case stores its unknown column as the filter at position 2. Add one assertion: a request that also posts `value_1` with other text returns the same CSV. Run it with `npm run test:report-typed-queries-playwright` where a deployed CARLOS and its database are available; otherwise list it under "Not verified" in the pull request body.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/report src/main/java/io/github/carlos_emr/carlos/prev/reports/ReportBuilder.java \
  src/main/java/io/github/carlos_emr/carlos/commn/dao/ReportFilterDao.java src/main/java/io/github/carlos_emr/carlos/commn/dao/ReportFilterDaoImpl.java \
  src/main/java/io/github/carlos_emr/carlos/managers/DashboardManagerImpl.java src/main/java/io/github/carlos_emr/carlos/dashboard/handler/IndicatorTemplateXML.java \
  src/test/java scripts/report-typed-queries-playwright-checks.js
git commit -m "fix(report): take report columns, ages, filters and dashboard providers only from their checked sources"
```

### Task 10: Pin the documented differences and correct the documentation (#3053, spec section 4)

**Files:**
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`
- Modify: `docs/mariadb-connector-migration.md`, `docs/release-notes-2026.08.md` ("JDBC and packaged installation")
- Modify: `debian/carlos-emr-drugref.README.Debian:86-109`, `debian/carlos-emr.README.Debian:46-61`, `debian/assets/jdbc-defaults.properties:5-6`, `debian/assets/drugref-hibernate.properties:8-22`, `.devcontainer/docker-compose.yml:59-67` (comments only)
- Modify: `docs/cds-import-jdbc-timeouts.md`, `docs/test/report-template-budgets.md`, `CLAUDE.md`, `GEMINI.md`

- [ ] **Step 1: Add the physical pin for the deterministic differences**

One case; every line is a soft assertion, so a connector upgrade reports each changed row at once.

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldKeepTheDocumentedDifferencesFromConnectorJ_forDeterministicCases(boolean legacy) throws Exception {
    var softly = new org.assertj.core.api.SoftAssertions();
    try (var source = source(legacy, "jdbcCompliantTruncation=false");
         Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE diff_probe (id INT PRIMARY KEY AUTO_INCREMENT, big BIGINT, ubig BIGINT UNSIGNED,"
                + " n INT, txt VARCHAR(20), t TIME, body TEXT, bin BLOB, u INT UNIQUE)");
        statement.executeUpdate("INSERT INTO diff_probe VALUES (1, 5510719487, 18446744073709551615, 7, '12.5', '23:30:00', 'x', 'x', 1)");
        try (ResultSet row = statement.executeQuery("SELECT big, ubig, n, txt, t, body, bin FROM diff_probe")) {
            softly.assertThat(row.next()).isTrue();
            softly.assertThatThrownBy(() -> row.getInt(1)).as("getInt above the int range").isInstanceOf(java.sql.SQLDataException.class);
            softly.assertThatThrownBy(() -> row.getLong(2)).as("getLong above the long range").isInstanceOf(java.sql.SQLDataException.class);
            softly.assertThat(row.getInt(4)).as("getInt on decimal text").isEqualTo(12);
            softly.assertThatThrownBy(() -> row.getLong(4)).as("getLong on decimal text").isInstanceOf(SQLException.class);
            softly.assertThatThrownBy(() -> row.getBytes(3)).as("getBytes on INT").isInstanceOf(SQLException.class);
            softly.assertThatThrownBy(() -> row.getDate(5)).as("getDate on TIME").isInstanceOf(SQLException.class);
            softly.assertThatThrownBy(() -> row.getString("missing")).as("unknown label").isExactlyInstanceOf(SQLException.class);
            softly.assertThat(row.getMetaData().getColumnType(6)).as("TEXT type").isEqualTo(java.sql.Types.VARCHAR);
            softly.assertThat(row.getMetaData().getColumnType(7)).as("BLOB type").isEqualTo(java.sql.Types.VARBINARY);
            softly.assertThat(row.getMetaData().getColumnTypeName(3)).as("INT type name").isEqualTo("INTEGER");
        }
        statement.executeUpdate("INSERT INTO diff_probe (u) VALUES (2), (3)", Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            softly.assertThat(keys.getMetaData().getColumnLabel(1)).as("generated key label").isEqualTo("insert_id");
            softly.assertThat(keys.next()).as("first generated key").isTrue();
            softly.assertThat(keys.next()).as("one key for a two-row insert").isFalse();
        }
        try (java.sql.PreparedStatement batch = connection.prepareStatement("INSERT INTO diff_probe (u) VALUES (?)")) {
            for (int value : new int[]{10, 10, 11}) {
                batch.setInt(1, value);
                batch.addBatch();
            }
            softly.assertThatThrownBy(batch::executeBatch).as("batch with a failing row")
                    .isInstanceOfSatisfying(java.sql.BatchUpdateException.class,
                            failure -> assertThat(failure.getUpdateCounts()).containsExactly(-3, -3, -3));
        }
        try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM diff_probe WHERE u IN (10, 11)")) {
            count.next();
            softly.assertThat(count.getInt(1)).as("rows a failed batch leaves").isZero();
        }
        try (Statement streaming = connection.createStatement(); Statement second = connection.createStatement()) {
            streaming.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rows = streaming.executeQuery("SELECT id FROM diff_probe ORDER BY id")) {
                softly.assertThat(rows.next()).isTrue();
                try (ResultSet other = second.executeQuery("SELECT 1")) {
                    softly.assertThat(other.next()).as("second statement while streaming").isTrue();
                }
                int remaining = 0;
                while (rows.next()) remaining++;
                softly.assertThat(remaining).as("rows still delivered after the second statement").isEqualTo(2);
            }
        }
        try (java.sql.PreparedStatement unbound = connection.prepareStatement("SELECT ?")) {
            softly.assertThatThrownBy(unbound::executeQuery).as("missing parameter")
                    .isInstanceOfSatisfying(java.sql.SQLTransientConnectionException.class,
                            failure -> assertThat(failure.getSQLState()).isEqualTo("07004"));
        }
        try (java.sql.PreparedStatement extra = connection.prepareStatement("SELECT ?")) {
            extra.setInt(1, 7);
            softly.assertThatCode(() -> extra.setInt(2, 9)).as("extra parameter index").doesNotThrowAnyException();
            try (ResultSet row = extra.executeQuery()) {
                row.next();
                softly.assertThat(row.getInt(1)).as("value with an extra parameter bound").isEqualTo(7);
            }
        }
        try (Connection strict = source.getConnection(); Statement strictStatement = strict.createStatement()) {
            strictStatement.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'");
            softly.assertThatThrownBy(() -> strictStatement.executeUpdate("INSERT INTO diff_probe (n) VALUES ('abc')"))
                    .as("strict-mode bad integer").isInstanceOfSatisfying(java.sql.SQLSyntaxErrorException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(1366));
            strictStatement.execute("SET SESSION sql_mode=''");
        }
        for (Object refused : new Object[]{Calendar.getInstance(), 'c'}) {
            try (java.sql.PreparedStatement bound = connection.prepareStatement("SELECT ?")) {
                softly.assertThatThrownBy(() -> {
                    bound.setObject(1, refused);
                    bound.executeQuery().close();
                }).as("setObject " + refused.getClass().getSimpleName()).isInstanceOf(SQLException.class);
            }
        }
        softly.assertThatCode(connection::commit).as("commit in autocommit mode").doesNotThrowAnyException();
        try (ResultSet session = statement.executeQuery("SELECT @@SESSION.character_set_results")) {
            session.next();
            softly.assertThat(session.getString(1)).as("character_set_results").isEqualTo("utf8mb4");
        }
        softly.assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MariaDB");
    }
    softly.assertAll();
}
```

Run the physical command. Expected: 62 cases pass. This case uses the default text protocol; do not add `useServerPrepStmts=true` to it (an extra parameter corrupts the binary protocol's values, spec section 4). A row that fails is a finding: record what was observed in the pull request body and correct spec section 4 and the migration document rather than the assertion's meaning.

- [ ] **Step 2: Extend `docs/mariadb-connector-migration.md`**

Add these sections, in plain operator-facing prose, from the spec:

- "Statements `executeQuery` refuses": spec section 6 in three or four sentences, the exact message and SQLState, that MariaDB Connector/J 3.5.11 would otherwise execute them (CONJ-1306), what the guard does not stop, and that the statement text is never logged.
- "Session defaults": spec section 8, including how an operator overrides either default, and that saved report text which puts a space between a function name and its parenthesis needs the space removed.
- "Connection options that are refused": the five rows of Task 6 with the message format, and that `carlos-ctl db-migrate` reports any refused URL with one fixed line.
- "Known differences from MySQL Connector/J": the table of spec section 4, marking which rows the physical test pins.
- "Lock-wait timeouts" and "SMALLINT objects": one or two sentences each (spec N5, N6).
- Where it says `carlos-ctl` 1.3.1 writes the MySQL driver name, name the version in `debian/carlos-ctl.pin` instead.

- [ ] **Step 3: Update `docs/release-notes-2026.08.md`**

Under "JDBC and packaged installation" add one entry each for: the `executeQuery` refusal; the session defaults and the note about a space before a function's parenthesis in saved report text; named refused options (#5770 asks for these in the release notes); Query by Example on binary columns, and that accented text in binary columns now reads correctly in report cells where the previous driver showed it garbled; lock-wait timeouts answered as "retry"; `db_name` without options. Correct the `carlos-ctl` version there as in Step 2. Under "Reports, charts and local customizations", one entry each, in the neutral terms of Global Constraints, for: a configured report runs only the filters stored for it (a bookmarked or scripted request that carried its own filter text gets the stored filter, and a request naming a filter the report does not have is refused); the report CSV download requires the report privilege; demographic report and saved-query columns are limited to the report's own list; an age in months is a number followed by `m`; a dashboard provider chosen by someone allowed to switch providers must exist. Add that this release changes the CDS ruleset fingerprint, so imports and exports in progress are finished or cancelled before upgrading (`docs/cds-import-operator-guide.md`).

- [ ] **Step 4: Correct the packaging documentation**

After the edit these statements must be true and nothing may contradict them:

- `debian/carlos-emr-drugref.README.Debian`: DrugRef uses MariaDB Connector/J 3.5.11 through its own copy of the compatibility driver; the package names the dialect in `hibernate.properties`; there is no `useInformationSchema` transformation of `spring_config.xml` and no `db_use_information_schema` setting; `db_url` may carry query parameters; DrugRef adds `connectionTimeZone=UTC` only when the URL names neither `connectionTimeZone` nor `serverTimezone` (as `debian/assets/skel/drugref2.properties` says).
- `debian/carlos-emr.README.Debian` and `debian/assets/jdbc-defaults.properties`: `useInformationSchema`, and the package default `db_default_use_information_schema` that appends it, are still accepted and have no effect under MariaDB Connector/J; an existing operator file needs no change.
- `debian/assets/drugref-hibernate.properties` and `.devcontainer/docker-compose.yml`: the comments describe the MySQL Connector/J keyword query as the historical reason the dialect is named, not as current behaviour.

Run: `cd debian/assets && python3 -m unittest discover -s tests -t .`
Expected: `OK` (some packaging tests read these files).

- [ ] **Step 5: Correct the remaining documents**

- `docs/cds-import-jdbc-timeouts.md`: every "Connector/J" says "MariaDB Connector/J".
- `docs/test/report-template-budgets.md`, the line "Revalidated with … Connector/J 9.6.0": re-run the measurement procedure that document describes on this branch's build and replace the table and that line. If the procedure cannot be run where this plan is executed, do not edit the numbers; change the line to say the table was measured with MySQL Connector/J 9.6.0 and has not been re-measured on MariaDB Connector/J 3.5.11, and list it under "Not verified" in the pull request body.
- `CLAUDE.md` and `GEMINI.md`, version numbers and driver description only, no rule text: Spring 7.0.10, Spring Security 7.1.2, Struts 7.4.0, Hibernate 7.4.14.Final, Apache CXF 4.1.9, Drools 10.2.0 (DRL/MVEL compiler path, not "executable model"), OWASP Encoder 1.6.0, MariaDB Connector/J 3.5.11 behind `MariaDbCompatibilityDriver`. Confirm each against `pom.xml` before writing it.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java \
  docs debian/carlos-emr-drugref.README.Debian debian/carlos-emr.README.Debian debian/assets/jdbc-defaults.properties \
  debian/assets/drugref-hibernate.properties .devcontainer/docker-compose.yml CLAUDE.md GEMINI.md
git commit -m "docs: describe the MariaDB connector as shipped and pin its known differences"
```

### Task 11: DrugRef carries the same adapter and a reviewed pin (#5744, N8)

**Files (drugref2026, branch `claude/brave-ptolemy-yfxbw5` created from `origin/claude/jolly-albattani-aotrv2`, the head of its PR #19):**
- Modify: `src/main/java/io/github/carlos_emr/drugref2026/util/MariaDbCompatibilityDriver.java`, `MariaDbJdbcCompatibility.java`
- Create: `src/test/java/io/github/carlos_emr/drugref2026/util/MariaDbQueryStatementValidatorUnitTest.java`, `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`

**Files (carlos):**
- Create: `debian/check-drugref-adapter-sources.py`, `debian/assets/tests/test_drugref_adapter_sources.py`
- Modify: `debian/rules:146-151`, `debian/drugref.pin`

**Interfaces:**
- Consumes: the three adapter sources as they stand after Tasks 2, 4, 5 and 6.
- Produces: `python3 debian/check-drugref-adapter-sources.py <drugref-source-dir> <carlos-source-dir>`; exit 0 when the three sources match after the package name is replaced, exit 1 naming each class that differs (never its content).

- [ ] **Step 1: Write the failing check test (carlos)**

```python
class DrugrefAdapterSourcesTest(unittest.TestCase):
    """Builds two temporary source trees and runs debian/check-drugref-adapter-sources.py on them."""

    def test_identical_sources_pass(self): ...            # exit 0
    def test_package_name_is_the_only_allowed_difference(self): ...   # exit 0
    def test_a_changed_line_names_the_class(self): ...    # exit 1, stderr contains "MariaDbCompatibilityDriver", not the changed line
    def test_a_missing_class_fails(self): ...             # exit 1, stderr contains "MariaDbPreferredTlsPlugin"
```

Run: `cd debian/assets && python3 -m unittest tests.test_drugref_adapter_sources`
Expected: FAIL, the script does not exist.

- [ ] **Step 2: Write `debian/check-drugref-adapter-sources.py` and wire it in**

It compares the three classes named in Global Constraints byte for byte after replacing each tree's package name with a placeholder. In `debian/rules`, call it in the branch that builds DrugRef from source, after `src` is set and before the dependency-lock line: `python3 $(CURDIR)/debian/check-drugref-adapter-sources.py "$$src" $(CURDIR); \`. A supplied `DRUGREF_WAR` has no sources and skips it. `debian/check-drugref-jdbc.py` needs no change: the statement rule is a nested class of a class it already requires.

Run: `cd debian/assets && python3 -m unittest discover -s tests -t .`
Expected: `OK`.

- [ ] **Step 3: Mirror the adapter into drugref2026**

Copy the three sources from `carlos`, changing only the package line. This brings the DATE-write correction of #6275 that DrugRef's copy lacks (spec N8) as well as Tasks 2, 4, 5 and 6. Copy `MariaDbQueryStatementValidatorUnitTest` and the oracle fixture the same way. Then run DrugRef's tests: an assertion there of the bare option message for one of the five refusals of Task 6 changes to that refusal's new message, and an assertion that lists every property `settings()` returns gains `initSql`; any other failure is a defect in the mirror. The mirror adds no dependency, so `debian/drugref-dependencies-lock.json` in `carlos` does not change.

```bash
python3 ../carlos/debian/check-drugref-adapter-sources.py . ../carlos   # expected: exit 0
mvn -B -ntp test                                                       # expected: BUILD SUCCESS
git add src && git commit -m "fix(db): match the CARLOS MariaDB adapter: query guard, session defaults, DATE writes"
```

DrugRef's statements were read for this plan: every query-API statement is a `SELECT`, every write uses `execute` or `executeUpdate`, and none puts a space before a function's parenthesis.

- [ ] **Step 4: Pin the mirrored commit (carlos)**

Set `ref=` in `debian/drugref.pin` to the full SHA of the Step 3 commit (it must be pushed first, or the package build cannot fetch it; push only when the maintainer asks). Rewrite the comment block: what the revision adds, and this restored warning: "The pinned commit must be reachable from drugref2026 `master`. Merge the DrugRef pull request with a merge commit, or repin after a squash merge; do not ship a package pinned to a branch head."

Run: `cd debian/assets && python3 -m unittest tests.test_packaging`
Expected: `OK`.

- [ ] **Step 5: Hand the DrugRef merges to a maintainer, then repin**

This step is not the implementer's to complete. A maintainer merges drugref2026 PR #18, PR #19 and the Step 3 branch into `master` with merge commits. Then:

```bash
git -C ../drugref2026 fetch origin master
git -C ../drugref2026 merge-base --is-ancestor "$(sed -n 's/^ref=//p' debian/drugref.pin)" origin/master && echo reachable
```

Expected: `reachable`. If the merges were squashed, set `ref=` to the resulting `master` commit, rerun `check-drugref-adapter-sources.py` against it, and commit. Until this prints `reachable`, the pull request says "Refs #5744", not "Closes #5744".

- [ ] **Step 6: Commit (carlos)**

```bash
git add debian/check-drugref-adapter-sources.py debian/assets/tests/test_drugref_adapter_sources.py \
  debian/rules debian/drugref.pin
git commit -m "fix(deb): keep the DrugRef adapter copy identical and pin its mirrored revision"
```

### Task 12: Required-case counts and coverage floors

**Files:**
- Modify: `scripts/ci/verify-mariadb-dao-reports.py`, `scripts/ci/test_mariadb_report_evidence.py`, `scripts/ci/test_release_plan_5700.py`, `docs/testing/release-2026.08-verification.json`
- Create or modify: `scripts/coverage/floors/src/main/java/io/github/carlos_emr/carlos/**/<Source>.java.json`, their `scripts/coverage/evidence/` run document, `docs/testing/coverage-floor-policy.md`

**Interfaces:**
- Consumes: the fifteen cases added to `JdbcConnectorMariaDbIntegrationTest` and the one added to `MariaDbDaoHarnessIntegrationTest`:

| Method | Cases |
|---|---|
| `shouldRefuseWritesThroughQueryApis_andLeaveTheRowUnchanged(boolean)` | 2 |
| `shouldRenderBinaryBlobCells_inQueryByExampleResults(boolean)` | 2 |
| `shouldOpenSessionsWithConnectorJDefaults_forSqlModeAndWriteTimeout(boolean)` | 2 |
| `shouldKeepIgnoreSpace_whenTheServerDefaultHasIt` | 1 |
| `shouldReturnIntegerObjects_forSmallIntColumns(boolean)` | 2 |
| `shouldReportLockWaitTimeoutsAsTransactionRollbacks_forWaitingAndNowaitLocks(boolean)` | 2 |
| `shouldSubtractABoundNumberOfMonths_inTheReportAgeFilter(boolean)` | 2 |
| `shouldKeepTheDocumentedDifferencesFromConnectorJ_forDeterministicCases(boolean)` | 2 |
| `MariaDbDaoHarnessIntegrationTest.shouldRefuseANativeWrite_whenItIsRunAsAQuery` | 1 |

- [ ] **Step 1: Raise the counts**

- `scripts/ci/verify-mariadb-dao-reports.py`: in `CRITICAL`, `"MariaDbDaoHarnessIntegrationTest": 1` becomes `2` (it applies to the generated and the Flyway lane; the new case runs in both). In `REQUIRED["generated"]`, `"JdbcConnectorMariaDbIntegrationTest": 47` becomes `62`. `REQUIRED_CASES["JdbcConnectorMariaDbIntegrationTest"]` gains the fifteen names: `name(boolean)[1]` and `name(boolean)[2]` for each parameterized method, the bare name for the plain one.
- `scripts/ci/test_mariadb_report_evidence.py`: `JDBC_REQUIRED_CASES` gains the same fifteen names.
- `scripts/ci/test_release_plan_5700.py`, in `test_jdbc_parameterized_routes_require_both_native_invocations`: `23` becomes `30`, `47` becomes `62`, and the expected plain list becomes the existing name followed by `shouldKeepIgnoreSpace_whenTheServerDefaultHasIt`.
- `docs/testing/release-2026.08-verification.json`, three checks. `java-all`: in `routedSkips`, the `JdbcConnectorMariaDbIntegrationTest` entry gains the seven parameterized methods (each with its two `destinations`, in the shape of its neighbours) and then the plain one (`{"name": …, "container": ""}`), and the `MariaDbDaoHarnessIntegrationTest` entry gains the new case. `java-native-generated`: `minimumCases` rises by 17 (the 15 and 1 above, and the new `ReportFilterDaoIntegrationTest` case, which this lane also runs), `requiredFamilies` becomes 62 and 2 for the two classes. `java-all` runs the H2 cases of Task 9; its `minimumCases` is a floor and needs no change. `java-native-flyway`: `minimumCases` rises by 1, the harness family becomes 2.

- [ ] **Step 2: Run the gates that CI runs on every pull request**

```bash
python3 scripts/ci/run-python-release-tests.py --start scripts/ci --output /tmp/python.json
python3 scripts/ci/run-python-release-tests.py --start scripts/coverage --output /tmp/coverage-tests.json
python3 scripts/ci/verify-release-evidence.py validate-plan
```

Expected: `Python release tests: … readiness PASS` twice, then `Validated 40 workstreams and 100 checks`.

- [ ] **Step 3: Record coverage floor evidence, last**

Do this after every source change is final: a floor record is bound to the exact bytes of its source. On pull requests to the release branch CI runs the freshness check of Task 1 Step 4, and it fails until this step is done. In the dry run, with Task 9 included, it listed these nineteen sources. Records whose source changed: `MariaDbCompatibilityDriver`, `MariaDbJdbcCompatibility`, `OscarTrackingBasicDataSource`, `AuditLogManager`, `OscarAppointmentDaoImpl`, `ReportFilterDao`, `ReportFilterDaoImpl`, `IndicatorTemplateXML`, `DashboardManagerImpl`, `ReportBuilder`, `RptDownloadCSVServlet`. No record yet: `RptResultStruct`, `RptFormQuery`, `RptDemographicColumnNames`, `RptDemographicQuery2Builder`, `RptDemographicQueryBuilder`, `RptDemographicQuery2Saver`, `DemographicAgeMonths`, `ReportFilterResolver`. Run the freshness check yourself for the exact list on the final tree.

Follow `docs/testing/coverage-floor-policy.md`, "To update floors after a measured run", in the way that document records for PR #6113:

1. Measure with JaCoCo on the final sources. For the two adapter files use the focused union PR #6113 used: every H2 test class that names either adapter, plus `JdbcConnectorMariaDbIntegrationTest` on MariaDB 11.8.8, written to one execution file. For the other files use the lane that covers them (the full H2 `tests` job of CI, or its JaCoCo artifact for this branch's head).
2. Each metric takes the highest of the retained floor and the new measurement; never lower one. The retained floors are 94.00 / 92.18 / 85.04 (lines / instructions / branches) for the driver and 98.27 / 98.14 / 90.45 for the configuration class. The dry run measured 96.30 / 95.06 / 88.33 and 98.34 / 98.31 / 91.14 over the whole files with this plan's tests; the configuration class has little margin. If a metric on the changed lines falls below its floor, add the unit case that covers the missed branch; do not add a waiver.
3. Write the run document in the shape of the existing ones in `scripts/coverage/evidence/`, then import it and check freshness:

```bash
python3 scripts/coverage/manage-coverage-floors.py --import-from <run>/measured-floors.json \
  --source src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java   # one --source per file above
```

   Then run the freshness command of Task 1 Step 4. Expected: `"fresh": true` and no problems.
4. Add a paragraph to `docs/testing/coverage-floor-policy.md` in the form of the existing refresh paragraphs: the measured head, the test counts, the measured figures for the six files, and that no floor was lowered and no waiver added.

- [ ] **Step 4: Commit**

```bash
git add scripts/ci docs/testing scripts/coverage
git commit -m "test(ci): require the new MariaDB connector cases and refresh coverage floors"
```

### Task 13: Whole-branch verification and the pull request text

**Files:** none changed unless a check fails.

- [ ] **Step 1: Run the unit suite**

Run: `mvn -B -ntp test -Dgroups=unit`
Expected: `BUILD SUCCESS`. Search the output and the Surefire reports for `S1009` and for `executeQuery() cannot run`: a hit outside the guard's own tests is a statement the guard refused somewhere it should not, and blocks the pull request until explained.

- [ ] **Step 2: Run the four MariaDB lanes and the report verifier**

Against MariaDB 11.8.8, with `CARLOS_TEST_MARIADB_DAO=true` and the user and password variables set, run the commands of these steps of `.github/workflows/maven-project.yml` (each `-Dtest=` list is there; the first two are also recorded as `java-native-generated` and `java-native-flyway` in `docs/testing/release-2026.08-verification.json`):

| Lane | Environment | Extra arguments | Dry-run result |
|---|---|---|---|
| Generated schema | `CARLOS_TEST_MARIADB_SCHEMA=generated`, URL `jdbc:mariadb://…/` | `-Dcarlos.test.schema.expected=generated -DexcludedGroups=flyway-schema` | 3,328 passed |
| Flyway schema, Ontario | `CARLOS_TEST_MARIADB_SCHEMA=flyway` | `-Dcarlos.test.schema.expected=flyway -Dsurefire.reportNameSuffix=flyway` | 368 passed |
| Flyway schema, British Columbia | `CARLOS_TEST_MARIADB_SCHEMA=flyway CARLOS_TEST_MARIADB_PROVINCE=bc` | `-Dsurefire.reportNameSuffix=bc-flyway` | 266 passed |
| Packaged legacy URLs | `CARLOS_TEST_MARIADB_SCHEMA=flyway`, URL `jdbc:mysql://…/` | `-Dcarlos.test.schema.expected=flyway -Dtest.reportsDirectory=target/surefire-mariadb-legacy-url` | 29 passed |

Then `python3 scripts/ci/verify-mariadb-dao-reports.py` (it reads `target/surefire-reports`).
Expected: no failure, error or skip in any lane; the verifier exits 0 and reports 62 and 2 for the two classes; the `S1009` search of Step 1 finds nothing unexpected.

- [ ] **Step 3: Run the packaged Flyway runner through the adapter**

Build the WAR (`mvn -B -ntp -DskipTests package`), then, with `JAVA_TOOL_OPTIONS` and `_JAVA_OPTIONS` unset (the launcher's test compares its error output exactly):

```bash
sh debian/build-flyway-runner.sh target/<carlos>.war /tmp/runner
```

Expected: `PASS Flyway existing-object notes…`, `PASS Flyway MariaDB driver, legacy URL compatibility and TLS policy`, `built /tmp/runner/carlos-flyway-runner.jar`. Then migrate two empty schemas with that launcher on the class path `carlos-ctl` uses (the launcher jar, then the unpacked WAR's classes and libraries) and the packaged URL options, once with `classpath:db/migration/common,classpath:db/migration/on` and once with `…/bc`:

```bash
FLYWAY_URL='jdbc:mysql://127.0.0.1:3306/<empty schema>?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci' \
FLYWAY_USER=<user> FLYWAY_PASSWORD=<password> \
java -cp '/tmp/runner/carlos-flyway-runner.jar:<unpacked WAR>/WEB-INF/classes:<unpacked WAR>/WEB-INF/lib/*' \
  io.github.carlos_emr.carlos.deb.FlywayRunner migrate <locations>
```

Expected: exit 0 both times and every row of `flyway_schema_history` successful. The dry run did this with the launcher compiled from source against the prototype's classes, not with a built WAR: 91 migrations applied for Ontario and 90 for British Columbia. An exit with `executeQuery() cannot run a statement…` blocks the pull request.

- [ ] **Step 4: Run the packaging tests and the reference model**

```bash
cd debian/assets && python3 -m unittest discover -s tests -t . && cd ../..
python3 scripts/jdbc-connector-probe/guard_reference.py check src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv
```

Expected: `OK`, then `rows=212 recorded=181 deviations-from-connectorj=5 model-disagreements=0 unsafe=0`. Where the reference driver is available on a developer machine, re-run the probe (`scripts/jdbc-connector-probe/README.md`) against the adapter and confirm the `SESSION` section now shows `sql_mode` empty and `net_write_timeout` 600, and the `DMLQ` section shows every write refused.

- [ ] **Step 5: Write the pull request body (do not open it until asked)**

Follow the repository's pull request template. It must state:

- Closes #5922, #5968, #5969, #5770, #5863, #6088. Refs #3053 (the documentation work that was #5768). #5744: "Closes" only if Task 11 Step 5 printed `reachable`, otherwise "Refs" with what remains.
- That it contains pull requests #6113, #6238 and #6295, which can be closed in its favour, and takes the #5770 and #5863 corrections from draft #6133 in their narrow form.
- The eight findings N1 to N8 with one line each and the spec path.
- What users will notice: saved report text with a space before a function's parenthesis stops parsing; accented text from binary columns reads correctly in report cells; the appointment export reads row by row; a configured report runs only filters stored for it; the CSV download needs the report privilege; an age in months must be written as a number followed by `m`.
- Task 9 in neutral terms (Global Constraints): which inputs each report and dashboard path now takes only from its checked source. No issue number exists for it; say it was found during this review.
- What was verified and how (the commands of Steps 1 to 4 and their results), and what was not: no browser run; the report-budget table if it was not re-measured; anything a failing pin row changed.
- Left for maintainers: the files under `.github/` named in #3053; the drugref2026 merges; follow-ups in `carlos-ctl` and `carlos-podman` (spec section 9).
