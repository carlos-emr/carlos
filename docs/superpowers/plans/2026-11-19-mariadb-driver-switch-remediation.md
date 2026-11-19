<!-- SPDX-License-Identifier: GPL-2.0-or-later -->

# MariaDB Driver Switch Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One pull request on `release/2026.08` that closes the seven open driver-switch issues (#5922, #5968, #5969, #5770, #5863, #5744, #6088), does the driver part of the documentation work now tracked in #3053 (formerly #5768), and corrects the eight further differences (N1 to N8) found by the differential review.

**Architecture:** The branch starts from the release head and merges the three open pull requests that already fix four of the issues. Everything else is small: three additions to the JDBC adapter (a statement guard for `executeQuery`, session defaults, two value and exception parities), four call-site corrections, documentation, and the same adapter mirrored into DrugRef with a build check that keeps the two copies identical. Every new physical-MariaDB case goes into a test class the MariaDB CI lane already runs, so no workflow file changes.

**Tech Stack:** Java 25, MariaDB Connector/J 3.5.11 behind `MariaDbCompatibilityDriver`, MariaDB 11.8.8, Spring 7.0.10, Hibernate 7.4.14, JUnit 5 / AssertJ / Mockito, Maven; Python 3 for CI bookkeeping and packaging tests.

**Spec:** `docs/superpowers/specs/2026-11-19-mariadb-driver-switch-findings.md` (read it first; section numbers below refer to it)

## Global Constraints

- Target branch `release/2026.08`. Work branch `claude/brave-ptolemy-yfxbw5`, based on `origin/release/2026.08`, never on `main` (`main` has three commits the release branch lacks). The branch already exists on the remote: the release head of 2026-11-19 (`8872cdd3`) plus one commit that holds this plan, the spec, the probe and the oracle fixture. Bring it up to date by merging; never rebase or force-push it.
- Do not edit anything under `.github/` or `database/`. Consequences: new physical cases go only into `JdbcConnectorMariaDbIntegrationTest` and `MariaDbDaoHarnessIntegrationTest` (the MariaDB lane's class list is duplicated in `.github/workflows/maven-project.yml`); the one workflow line PR #6295 changes arrives by merging that PR, not by editing; the assistant files under `.github/` named in #3053 are left for a maintainer and listed in the pull request body.
- MariaDB Connector/J stays at 3.5.11. MySQL Connector/J must not appear in `pom.xml`, a lock file or a package. The guard is written from spec section 6 and the recorded oracle; do not copy code from MySQL Connector/J.
- No statement text, URL, credential or row value in any exception message or log line this work adds.
- The four adapter sources (`MariaDbCompatibilityDriver`, `MariaDbJdbcCompatibility`, `MariaDbPreferredTlsPlugin`, `MariaDbQueryStatementValidator`) must be identical in `carlos` and `drugref2026` apart from the package name (`io.github.carlos_emr.carlos.utility` / `io.github.carlos_emr.drugref2026.util`).
- Exact values: refusal SQLState `S1009`, message `executeQuery() cannot run a statement that does not produce a result set`; session statement and its rules as in spec section 8 (`net_write_timeout=600`); lock-wait mapping error `1205` to SQLState `40001`; option message `Invalid database connection configuration: option <name>; <requirement>` with SQLState `08001`.
- Tests: JUnit 5, AssertJ, names `should<Action>_<context>` with exactly one underscore, `@Tag("unit")` or `@Tag("integration")`. New files carry the CARLOS copyright header (`docs/copyright-header-carlos.md`), no `@author`. New classes follow `docs/architecture/layer-names.md` (no `*Helper`, `*Utils`, `*Manager`).
- Conventional Commits. End every commit message with the attribution trailers the session supplies. Commit after each task; push only when the maintainer asks; do not open the pull request until asked.

## Review Focus

1. A read the guard refuses: a saved report or a code path whose statement starts with a comment, a parenthesis, `WITH`, lower case, white space, `SHOW`, `EXPLAIN` or `{call …}`, or mentions `INTO` only inside a quoted value or a quoted name. It must run exactly as before. Pinned in Task 2 (every `SENT` row of the oracle, and `shouldSendTheReadShapesCarlosUses_withoutRefusal`).
2. An operator's own `sessionVariables` or `initSql` in the JDBC URL, in any letter case. Their settings must still apply and must win over the adapter's defaults. Pinned in Task 4.
3. `db_name` with no `?`, with a `?` and nothing after it, blank, or missing. The application must start; only the audit purge may refuse, with a clear message. Pinned in Task 7.
4. A Query by Example result whose binary cell holds bytes that are not text (an image in `client_image`). The page must render within its existing budget instead of failing. Pinned in Task 3.
5. A server whose own default `sql_mode` contains `IGNORE_SPACE` or `ANSI`. The session must keep it, as it did under MySQL Connector/J. Pinned in Task 4.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidator.java` (new) | Decides whether `executeQuery` may send a statement (spec section 6). | 2 |
| `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java` | Calls the validator; SMALLINT objects; error 1205. | 2, 5 |
| `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java` | Session defaults; names a refused option. | 4, 6 |
| `src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java` | Stops replacing the option message. | 6 |
| `src/main/java/io/github/carlos_emr/carlos/report/data/RptResultStruct.java` | Reads binary cells as text. | 3 |
| `src/main/java/io/github/carlos_emr/carlos/managers/AuditLogManager.java` | Reads the schema name safely. | 7 |
| `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoImpl.java` | Recognises both product names. | 8 |
| `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java` | All new physical JDBC cases (13). | 2, 3, 4, 5, 9 |
| `src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java` | One physical JPA case. | 2 |
| `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`, `scripts/jdbc-connector-probe/` (already on the branch) | Oracle fixture; the probe that produced it. | 2 |
| `docs/mariadb-connector-migration.md`, `docs/release-notes-2026.08.md`, packaging READMEs, `CLAUDE.md`, `GEMINI.md` | Documentation. | 9 |
| `debian/check-drugref-adapter-sources.py` (new), `debian/check-drugref-jdbc.py`, `debian/rules`, `debian/drugref.pin` | DrugRef copy check and pin. | 10 |
| `scripts/ci/verify-mariadb-dao-reports.py`, `scripts/ci/test_mariadb_report_evidence.py`, `scripts/ci/test_release_plan_5700.py`, `docs/testing/release-2026.08-verification.json`, `scripts/coverage/floors/**` | Required-case counts and coverage floors. | 11 |

Commands used throughout:

- Unit tests: `mvn -B -ntp test -Dtest='<Class>'`
- Physical cases: `CARLOS_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:3306/ CARLOS_TEST_MARIADB_USER=root CARLOS_TEST_MARIADB_PASSWORD=<password> mvn -B -ntp test -Dtest=JdbcConnectorMariaDbIntegrationTest -Dgroups=mariadb-native` against a disposable MariaDB 11.8.8 (the devcontainer database is suitable; each test creates and drops its own schema).

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
mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,ReportBinaryReadersUnitTest,RptResultStructUnitTest'
```

Expected: `OK` twice, then `BUILD SUCCESS`. The merges are this task's commits; there is nothing else to commit.

### Task 2: Refuse statements that produce no result set in `executeQuery` (N1)

**Files:**
- Create: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidator.java`
- Create: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidatorUnitTest.java`
- Create: `src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverQueryGuardUnitTest.java`
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java` (`wrap` :116, `JdbcHandler` :122-135, `invoke` :138-178, `wrapResult` :500-510)
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java`
- Uses (already on the branch): `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`

**Interfaces:**
- Produces (package-private; DrugRef gets its own copy in Task 10):

```java
final class MariaDbQueryStatementValidator {
    static final String REFUSAL_STATE = "S1009";
    static final String REFUSAL_MESSAGE = "executeQuery() cannot run a statement that does not produce a result set";
    static boolean producesResultSet(String sql);   // spec section 6; null is refused
    static SQLException refusal();                   // new SQLException(REFUSAL_MESSAGE, REFUSAL_STATE)
}
```

- [ ] **Step 1: Write the failing validator test**

The fixture has 207 data rows: tab-separated `connectorj`, `carlos`, `changes`, `statement`; `#` lines and the header are skipped; `\n` and `\t` in the statement are escaped.

```java
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
        assertThat(MariaDbQueryStatementValidator.producesResultSet(row.statement()))
                .as(row.statement()).isEqualTo(row.carlos().equals("SENT"));
    }

    @Test
    void shouldRefuseEveryRecordedWrite_whenConnectorJRefusedIt() throws IOException {
        assertThat(oracle()).hasSize(207);
        var writes = oracle().stream().filter(row -> row.changes() && row.connectorJ().equals("REFUSED")).toList();
        assertThat(writes).hasSize(25).allSatisfy(row ->
                assertThat(MariaDbQueryStatementValidator.producesResultSet(row.statement())).as(row.statement()).isFalse());
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
        assertThat(MariaDbQueryStatementValidator.producesResultSet(sql)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "/* only */", "-- only", "# only"})
    void shouldRefuseText_withNoStatementInIt(String sql) {
        assertThat(MariaDbQueryStatementValidator.producesResultSet(sql)).isFalse();
    }

    @Test
    void shouldCarryNoStatementText_inTheRefusal() {
        SQLException refusal = MariaDbQueryStatementValidator.refusal();
        assertThat(refusal).hasMessage("executeQuery() cannot run a statement that does not produce a result set");
        assertThat(refusal.getSQLState()).isEqualTo("S1009");
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=MariaDbQueryStatementValidatorUnitTest`
Expected: compilation failure, `MariaDbQueryStatementValidator` not found.

- [ ] **Step 3: Implement `MariaDbQueryStatementValidator`**

One left-to-right scan of the characters, no regular expressions over the whole text, following spec section 6 exactly. `scripts/jdbc-connector-probe/guard_reference.py` is a working model of the same rule in Python (it is ours; reading it is fine). Class Javadoc: what the guard restores, that it mirrors a driver refusal rather than replacing any application validator, the pointer to the oracle, and that the statement text must never reach a message or a log.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest=MariaDbQueryStatementValidatorUnitTest`
Expected: PASS (207 oracle cases and the others).

- [ ] **Step 5: Write the failing adapter test**

`MariaDbCompatibilityDriverQueryGuardUnitTest` (`@Tag("unit")`) opens the adapter over a mocked MariaDB driver exactly as `MariaDbCompatibilityDriverNumberUnitTest.withNativeRows` does (`mockConstruction(org.mariadb.jdbc.Driver.class, …)`, a deep-stub `org.mariadb.jdbc.Connection` that unwraps to itself, `connect("jdbc:mysql://127.0.0.1:3306/probe", new Properties())`). Cases:

```java
@Test
void shouldRefuseBeforeSending_whenStatementExecuteQueryIsGivenAWrite() throws Exception {
    // physical.createStatement() returns nativeStatement, a mock of org.mariadb.jdbc.Statement
    assertThatThrownBy(() -> statement.executeQuery("UPDATE demographic SET last_name = 'x'"))
            .isInstanceOfSatisfying(SQLException.class, refusal -> {
                assertThat(refusal.getSQLState()).isEqualTo("S1009");
                assertThat(refusal).hasMessage("executeQuery() cannot run a statement that does not produce a result set");
            });
    verify(nativeStatement, never()).executeQuery(anyString());
}

@Test
void shouldRefuseBeforeSending_whenAPreparedWriteIsRunAsAQuery() throws Exception {
    // physical.prepareStatement("DELETE FROM log WHERE id = ?") returns nativePrepared, a mock of PreparedStatement
    assertThatThrownBy(prepared::executeQuery)
            .isInstanceOfSatisfying(SQLException.class, refusal -> assertThat(refusal.getSQLState()).isEqualTo("S1009"));
    verify(nativePrepared, never()).executeQuery();
    prepared.executeUpdate();
    verify(nativePrepared).executeUpdate();
}
```

and three more in the same style:

- `shouldSendAPreparedRead_forEveryPrepareOverload`: `"SELECT 1"` through `prepareStatement(sql)`, `(sql, int)`, `(sql, int, int)`, `(sql, int, int, int)`, `(sql, int[])`, `(sql, String[])`, `prepareCall(sql)`, `(sql, int, int)`, `(sql, int, int, int)`; `executeQuery()` reaches the native statement each time.
- `shouldRefuseAPreparedWrite_forEveryPrepareOverload`: the same nine with `"INSERT INTO t VALUES (1)"`; none reaches the native `executeQuery()`.
- `shouldLeaveExecuteAndExecuteUpdateAlone_forWrites`: `statement.execute("UPDATE t SET a = 1")` and `statement.executeUpdate("UPDATE t SET a = 1")` reach the native statement.

- [ ] **Step 6: Run it and see it fail**

Run: `mvn -B -ntp test -Dtest=MariaDbCompatibilityDriverQueryGuardUnitTest`
Expected: FAIL; the refusal cases see no exception.

- [ ] **Step 7: Call the validator from the adapter**

In `MariaDbCompatibilityDriver`:

- `JdbcHandler` gains `private final boolean refusesExecuteQuery;`, set by a sixth constructor argument. `wrap(Object, Class<?>, Connection, Statement, ConversionPolicy)` keeps its signature and passes `false`; add an overload with a trailing `boolean refusesExecuteQuery`.
- `wrapResult(Object result, Object proxy)` becomes `wrapResult(Object result, Object proxy, String name, Object[] args)`. When `target` is a `Connection` and `name` is `prepareStatement` or `prepareCall`, the new statement proxy is created with `!MariaDbQueryStatementValidator.producesResultSet((String) args[0])`. The statement is classified once, here; the handler keeps the flag, not the text.
- In `invoke`, after the `setFetchSize` block: when `target` is a `Statement` and `name` is `executeQuery`, throw `MariaDbQueryStatementValidator.refusal()` if the call has a `String` argument that does not produce a result set, or has no argument and `refusesExecuteQuery` is set.

- [ ] **Step 8: Run the adapter unit tests**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest'`
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
        for (String write : List.of("UPDATE guard_probe SET v = 2", "/* c */ DELETE FROM guard_probe",
                "/*! INSERT INTO guard_probe VALUES (2, 2) */", "INSERT INTO guard_probe VALUES (3, 3) RETURNING id",
                "DROP TABLE guard_probe", "SET @guard_probe = 1", "SELECT v INTO @guard_probe FROM guard_probe")) {
            assertThatThrownBy(() -> statement.executeQuery(write)).as(write)
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("S1009"));
            try (PreparedStatement prepared = connection.prepareStatement(write)) {
                assertThatThrownBy(prepared::executeQuery).as(write)
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("S1009"));
            }
        }
        assertThatThrownBy(() -> new org.springframework.jdbc.core.JdbcTemplate(source)
                .queryForList("UPDATE guard_probe SET v = 9"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        for (String read : List.of("SELECT v FROM guard_probe", "(SELECT 1) UNION (SELECT 2)",
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
                .rootCause().isInstanceOf(SQLException.class)
                .hasMessage("executeQuery() cannot run a statement that does not produce a result set");
    } finally {
        manager.close();
    }
}
```

- [ ] **Step 11: Run both physical cases**

Run the physical command from the top of this plan, then the same with `-Dtest=MariaDbDaoHarnessIntegrationTest -Dgroups=integration` and `CARLOS_TEST_MARIADB_DAO=true CARLOS_TEST_MARIADB_SCHEMA=generated`.
Expected: PASS. If either refusal case had passed before Step 7, stop: the case is not testing the guard.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidator.java \
  src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbQueryStatementValidatorUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriverQueryGuardUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java \
  src/test/java/io/github/carlos_emr/carlos/commn/dao/MariaDbDaoHarnessIntegrationTest.java
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

Each stubs `rows.getMetaData()` with one column (`getColumnCount()` 1, `getColumnLabel(1)` `"query"`, `getColumnType(1)` the type under test) and `rows.next()` returning `true, false`.

```java
@ParameterizedTest
@ValueSource(ints = {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB})
void shouldRenderBinaryCellsAsUtf8Text_withoutTheCharacterCodec(int type) throws Exception {
    when(rows.getBinaryStream(1)).thenReturn(new ByteArrayInputStream("select <1> é".getBytes(StandardCharsets.UTF_8)));
    when(rows.getCharacterStream(1)).thenThrow(new SQLDataException("Data type BLOB cannot be decoded as Reader"));
    assertThat(RptResultStruct.getStructure(rows)).contains("<td>select &lt;1&gt; é</td>");
    verify(rows, never()).getCharacterStream(anyInt());
}

@Test
void shouldRenderAnEmptyCell_whenTheBinaryValueIsNull() throws Exception {
    when(metadata.getColumnType(1)).thenReturn(Types.BLOB);
    when(rows.getBinaryStream(1)).thenReturn(null);
    assertThat(RptResultStruct.getStructure(rows)).contains("<td></td>");
}

@Test
void shouldRenderWithinTheBudget_whenBinaryBytesAreNotText() throws Exception {
    when(metadata.getColumnType(1)).thenReturn(Types.BLOB);
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

Run: `mvn -B -ntp test -Dtest='RptResultStructUnitTest,RptByExampleDataUnitTest'`
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

`PACKAGED` is `"jdbc:mysql://db/carlos?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false"`; `DEFAULTS` is `"SET SESSION net_write_timeout=600, sql_mode=" + MariaDbJdbcCompatibility.SESSION_SQL_MODE`; `initSql(url)` is `MariaDbJdbcCompatibility.settings(url, new Properties()).properties().getProperty("initSql")`.

```java
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
Expected: the six new cases fail (`initSql` is null or `SESSION_SQL_MODE` does not compile).

- [ ] **Step 3: Implement**

`canonicalOptionKey` maps `initsql` to `initSql` and `sessionvariables` to `sessionVariables`. `settings()` calls `applySessionDefaults(properties)` directly after `properties.putIfAbsent("permitRedirect", FALSE)`. `applySessionDefaults` follows spec section 8: it looks for `net_write_timeout` and `sql_mode` (ASCII case-insensitive substring) in the operator's `sessionVariables` and `initSql`, builds `SET SESSION ` plus the parts not claimed, joined by `", "`, and stores it in `initSql` followed by `";"` and the operator's `initSql` when there is one. A comment says why `sessionVariables` cannot carry the expression.

- [ ] **Step 4: Run them and see them pass**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,JdbcMetadataDefaultUnitTest,CdsJdbcConnectionTimeoutsUnitTest'`
Expected: PASS. A test elsewhere that lists every property `settings()` returns needs `initSql` added to its expectation; nothing else may change.

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
    // as above, with nativeRows.getInt(1) returning 0 and nativeRows.wasNull() returning true
    withNativeRows(nativeRows, rows -> {
        assertThat(rows.getObject(1)).isNull();
        assertThat(rows.wasNull()).isTrue();
    });
}
```

- [ ] **Step 2: Write the failing lock-wait tests**

`MariaDbCompatibilityDriverLockTimeoutUnitTest` (`@Tag("unit")`), same mocked-driver harness as Task 2 Step 5. `server` is `new SQLException("(conn=7) Lock wait timeout exceeded; try restarting transaction", "HY000", 1205)`.

```java
@Test
void shouldReportALockWaitTimeoutAsARollback_whenTheServerSendsHy000() throws Exception {
    when(nativeStatement.executeUpdate(anyString())).thenThrow(server);
    assertThatThrownBy(() -> statement.executeUpdate("UPDATE t SET a = 1"))
            .isInstanceOfSatisfying(SQLTransactionRollbackException.class, timeout -> {
                assertThat(timeout.getSQLState()).isEqualTo("40001");
                assertThat(timeout.getErrorCode()).isEqualTo(1205);
                assertThat(timeout).hasMessage(server.getMessage()).hasCause(server);
                assertThat(new org.springframework.jdbc.support.SQLExceptionSubclassTranslator().translate("task", null, timeout))
                        .isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
            });
}

@Test
void shouldPassOtherFailuresThrough_unchanged() throws Exception {
    // new SQLException("Lock table full", "HY000", 1206) and
    // new SQLTransactionRollbackException("Deadlock", "40001", 1213): each is thrown as the same instance
}

@Test
void shouldKeepABatchFailure_withItsUpdateCounts() throws Exception {
    // new BatchUpdateException("Lock wait timeout exceeded", "HY000", 1205, new int[]{1, -3}) from executeBatch():
    // thrown as the same instance
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
    for (String options : List.of("jdbcCompliantTruncation=false", "jdbcCompliantTruncation=false&useServerPrepStmts=true")) {
        try (var source = source(legacy, options); Connection connection = source.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS small_probe (id INT PRIMARY KEY, s SMALLINT, u SMALLINT UNSIGNED)");
            statement.executeUpdate("REPLACE INTO small_probe VALUES (1, -32768, 65535), (2, NULL, NULL)");
            try (PreparedStatement query = connection.prepareStatement("SELECT s, u FROM small_probe ORDER BY id");
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
        for (String blocked : List.of("UPDATE lock_probe SET v = 2 WHERE id = 1",
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
- Test: `MariaDbJdbcCompatibilityUnitTest`, `MariaDbTlsOriginUnitTest`, `JdbcMetadataDefaultUnitTest`, `OscarTrackingBasicDataSourceUnitTest`

**Interfaces:**
- Produces: `private static SQLException invalidOption(String option, String requirement)` returning `new SQLException("Invalid database connection configuration: option " + option + "; " + requirement, "08001")`. It is called only with the fixed names below, never with text taken from the URL. The same five messages are in draft PR #6133, so that pull request merges without a conflict of meaning later.

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

Add `invalidOption` beside `invalid()` and use it at the five refusals in the table. Every other `invalid()` stays (malformed URLs, hosts, reserved names, conflicting spellings, unknown values of other options): those can involve text the operator typed. In `OscarTrackingBasicDataSource.hasExplicitTimezone`, rethrow `new IllegalArgumentException(invalid.getMessage())` instead of the fixed text.

- [ ] **Step 4: Run the affected classes and update their expectations**

Run: `mvn -B -ntp test -Dtest='MariaDb*UnitTest,OscarTrackingBasicDataSourceUnitTest,JdbcMetadataDefaultUnitTest'`
Expected: the new cases pass. Existing assertions of the bare message fail only where they exercise one of the five refusals; change each of those to the exact new message from the table. Any other failure is a defect in Step 3.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/MariaDbJdbcCompatibility.java \
  src/main/java/io/github/carlos_emr/carlos/utility/OscarTrackingBasicDataSource.java \
  src/test/java/io/github/carlos_emr/carlos/utility
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

`schemaName` returns `""` for null, otherwise the trimmed text before the first `?` (the split `QueryByExampleSqlValidator.applicationSchema` uses). The field becomes `String dbName = schemaName(CarlosProperties.getInstance().getProperty("db_name"));`. `purgeAuditLog` throws `new Exception("No database name has been defined. Please set db_name in the properties file")`, in the style of its `mysqldump` check, when `dbName` is empty. Nothing else in the class changes; the purge rewrite belongs to draft PR #6133.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest=AuditLogManagerUnitTest`
Expected: PASS.

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

`streamPatientAppointments` passes the product name from `connection.getMetaData().getDatabaseProductName()` to `streamingFetchSize` and gives the result to `query.setFetchSize`. Rewrite the comment: the adapter reports `MariaDB` and maps the sentinel to MariaDB's streaming fetch size.

- [ ] **Step 4: Run it and see it pass**

Run: `mvn -B -ntp test -Dtest='OscarAppointmentDaoStreamingUnitTest,OscarAppointmentDaoQueryIntegrationTest,PatientListByApptExportUnitTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoImpl.java \
  src/test/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDaoStreamingUnitTest.java
git commit -m "fix(appointment): ask for a streamed export on MariaDB by name"
```

### Task 9: Pin the documented differences and correct the documentation (#3053, spec section 4)

**Files:**
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java`
- Modify: `docs/mariadb-connector-migration.md`, `docs/release-notes-2026.08.md` ("JDBC and packaged installation", :417)
- Modify: `debian/carlos-emr-drugref.README.Debian:86-109`, `debian/carlos-emr.README.Debian:46-61`, `debian/assets/jdbc-defaults.properties:5-6`, `debian/assets/drugref-hibernate.properties:8-22`, `.devcontainer/docker-compose.yml:59-67` (comments only)
- Modify: `docs/cds-import-jdbc-timeouts.md`, `docs/test/report-template-budgets.md:172`, `CLAUDE.md`, `GEMINI.md`

- [ ] **Step 1: Add the physical pin for the deterministic differences**

One case; each line is a soft assertion so a connector upgrade reports every changed row at once. The fixture is `CREATE TABLE diff_probe (id INT PRIMARY KEY AUTO_INCREMENT, big BIGINT, ubig BIGINT UNSIGNED, n INT, txt VARCHAR(20), t TIME, body TEXT, bin BLOB, u INT UNIQUE)` with one row `(1, 5510719487, 18446744073709551615, 7, '12.5', '23:30:00', 'x', 'x', 1)`.

```java
@ParameterizedTest
@ValueSource(booleans = {false, true})
void shouldKeepTheDocumentedDifferencesFromConnectorJ_forDeterministicCases(boolean legacy) throws Exception {
    try (var source = source(legacy, "jdbcCompliantTruncation=false");
         Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
        // create and fill diff_probe as described above
        SoftAssertions.assertSoftly(softly -> {
            try (ResultSet row = statement.executeQuery("SELECT big, ubig, n, txt, t, body, bin FROM diff_probe")) {
                softly.assertThat(row.next()).isTrue();
                softly.assertThatThrownBy(() -> row.getInt(1)).as("getInt above the int range").isInstanceOf(SQLDataException.class);
                softly.assertThatThrownBy(() -> row.getLong(2)).as("getLong above the long range").isInstanceOf(SQLDataException.class);
                softly.assertThat(row.getInt(4)).as("getInt on decimal text").isEqualTo(12);
                softly.assertThatThrownBy(() -> row.getLong(4)).as("getLong on decimal text").isInstanceOf(SQLException.class);
                softly.assertThatThrownBy(() -> row.getBytes(3)).as("getBytes on INT").isInstanceOf(SQLException.class);
                softly.assertThatThrownBy(() -> row.getDate(5)).as("getDate on TIME").isInstanceOf(SQLException.class);
                softly.assertThatThrownBy(() -> row.getString("missing")).as("unknown label").isExactlyInstanceOf(SQLException.class);
                softly.assertThat(row.getMetaData().getColumnType(6)).as("TEXT type").isEqualTo(Types.VARCHAR);
                softly.assertThat(row.getMetaData().getColumnType(7)).as("BLOB type").isEqualTo(Types.VARBINARY);
                softly.assertThat(row.getMetaData().getColumnTypeName(3)).as("INT type name").isEqualTo("INTEGER");
            }
            // the remaining rows, each written the same way:
        });
    }
}
```

The remaining rows, with the outcome the probe recorded:

| Case | Do | Expect |
|---|---|---|
| Generated keys, two-row insert | `executeUpdate("INSERT INTO diff_probe (u) VALUES (2), (3)", Statement.RETURN_GENERATED_KEYS)`, read `getGeneratedKeys()` | exactly one row; `getMetaData().getColumnLabel(1)` is `insert_id` |
| Batch with a failing row | on `prepareStatement("INSERT INTO diff_probe (u) VALUES (?)")`, `addBatch` for `10`, `10`, `11`; `executeBatch()` | `BatchUpdateException` with `getUpdateCounts()` equal to `{-3, -3, -3}`; no row with `u` 10 or 11 afterwards |
| Second statement while streaming | `setFetchSize(Integer.MIN_VALUE)` on a statement running `SELECT id FROM diff_probe`, read one row, run `SELECT 1` on another statement of the same connection, read on | the second query returns; the first result still delivers every remaining row |
| Missing parameter | `prepareStatement("SELECT ?").executeQuery()` | `SQLTransientConnectionException`, SQLState `07004` |
| Strict-mode bad integer | on a second connection: `SET SESSION sql_mode='STRICT_TRANS_TABLES'`, then `INSERT INTO diff_probe (n) VALUES ('abc')` | `SQLSyntaxErrorException`, error code `1366` |
| Objects the driver will not bind | `setObject(1, Calendar.getInstance())`, then `setObject(1, 'c')`, on `prepareStatement("SELECT ?")` | each is refused with a `SQLException`, at the bind or at `executeQuery()` |
| `commit()` in autocommit mode | `connection.commit()` | no exception |
| Session and product | `SELECT @@SESSION.character_set_results`; `getMetaData().getDatabaseProductName()` | `utf8mb4`; `MariaDB` |

Run the physical command. Expected: PASS. A row that fails is a finding: record what was observed in the pull request body and correct spec section 4 and the migration document rather than the assertion's meaning.

- [ ] **Step 2: Rewrite `docs/mariadb-connector-migration.md` additions**

Add these sections, in plain operator-facing prose, from the spec:

- "Statements `executeQuery` refuses": spec section 6 in three or four sentences, the exact message and SQLState, that MariaDB Connector/J 3.5.11 would otherwise execute them (CONJ-1306), and that the statement text is never logged.
- "Session defaults": spec section 8, including how an operator overrides either default.
- "Connection options that are refused": the five rows of Task 6 with the message format.
- "Known differences from MySQL Connector/J": the table of spec section 4, marking which rows the physical test pins.
- "Lock-wait timeouts" and "SMALLINT objects": one sentence each (spec N5, N6).
- Correct "pinned carlos-ctl 1.3.1" (:29) to the tag in `debian/carlos-ctl.pin`.

- [ ] **Step 3: Update `docs/release-notes-2026.08.md`**

Under "JDBC and packaged installation" add one entry each for: the `executeQuery` refusal; the session defaults; named refused options (#5770 asks for these in the release notes); Query by Example on binary columns; lock-wait timeouts answered as "retry"; `db_name` without options. Correct the carlos-ctl version at :390 as in Step 2.

- [ ] **Step 4: Correct the packaging documentation**

After the edit these statements must be true and nothing may contradict them:

- `debian/carlos-emr-drugref.README.Debian`: DrugRef uses MariaDB Connector/J 3.5.11 through its own copy of the compatibility driver; the package names the dialect in `hibernate.properties`; there is no `useInformationSchema` transformation of `spring_config.xml` and no `db_use_information_schema` setting; `db_url` may carry query parameters; DrugRef adds `connectionTimeZone=UTC` only when the URL names neither `connectionTimeZone` nor `serverTimezone` (as `debian/assets/skel/drugref2.properties` says).
- `debian/carlos-emr.README.Debian` and `debian/assets/jdbc-defaults.properties`: `useInformationSchema`, and the package default `db_default_use_information_schema` that appends it, are still accepted and have no effect under MariaDB Connector/J; an existing operator file needs no change.
- `debian/assets/drugref-hibernate.properties` and `.devcontainer/docker-compose.yml`: the comments describe the MySQL Connector/J keyword query as the historical reason the dialect is named, not as current behaviour.

Run: `cd debian/assets && python3 -m unittest discover -s tests -t .`
Expected: `OK` (some packaging tests read these files).

- [ ] **Step 5: Correct the remaining documents**

- `docs/cds-import-jdbc-timeouts.md`: every "Connector/J" says "MariaDB Connector/J".
- `docs/test/report-template-budgets.md:172`: re-run the measurement procedure that document describes on this branch's build and replace the table and its "Revalidated with" line. If the procedure cannot be run where this plan is executed, do not edit the numbers; change the line to say the table was measured with MySQL Connector/J 9.6.0 and has not been re-measured on MariaDB Connector/J 3.5.11, and list it under "Not verified" in the pull request body.
- `CLAUDE.md` and `GEMINI.md`, version numbers and driver description only, no rule text: Spring 7.0.10, Spring Security 7.1.2, Struts 7.4.0, Hibernate 7.4.14.Final, Apache CXF 4.1.9, Drools 10.2.0 (DRL/MVEL compiler path, not "executable model"), OWASP Encoder 1.6.0, MariaDB Connector/J 3.5.11 behind `MariaDbCompatibilityDriver`. Confirm each against `pom.xml` before writing it.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/github/carlos_emr/carlos/utility/JdbcConnectorMariaDbIntegrationTest.java \
  docs debian/carlos-emr-drugref.README.Debian debian/carlos-emr.README.Debian debian/assets/jdbc-defaults.properties \
  debian/assets/drugref-hibernate.properties .devcontainer/docker-compose.yml CLAUDE.md GEMINI.md
git commit -m "docs: describe the MariaDB connector as shipped and pin its known differences"
```

### Task 10: DrugRef carries the same adapter and a reviewed pin (#5744, N8)

**Files (drugref2026, branch `claude/brave-ptolemy-yfxbw5` created from `origin/claude/jolly-albattani-aotrv2`, the head of its PR #19):**
- Modify: `src/main/java/io/github/carlos_emr/drugref2026/util/MariaDbCompatibilityDriver.java`, `MariaDbJdbcCompatibility.java`
- Create: `src/main/java/io/github/carlos_emr/drugref2026/util/MariaDbQueryStatementValidator.java`
- Create: `src/test/java/io/github/carlos_emr/drugref2026/util/MariaDbQueryStatementValidatorUnitTest.java`, `src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`

**Files (carlos):**
- Create: `debian/check-drugref-adapter-sources.py`, `debian/assets/tests/test_drugref_adapter_sources.py`
- Modify: `debian/check-drugref-jdbc.py:50-51`, `debian/rules:148-149`, `debian/drugref.pin`

**Interfaces:**
- Consumes: the four adapter sources as they stand after Tasks 2, 4, 5 and 6.
- Produces: `python3 debian/check-drugref-adapter-sources.py <drugref-source-dir> <carlos-source-dir>`; exit 0 when the four sources match after the package name is replaced, exit 1 naming each class that differs (never its content).

- [ ] **Step 1: Write the failing check test (carlos)**

```python
class DrugrefAdapterSourcesTest(unittest.TestCase):
    """Builds two temporary source trees and runs debian/check-drugref-adapter-sources.py on them."""

    def test_identical_sources_pass(self): ...            # exit 0
    def test_package_name_is_the_only_allowed_difference(self): ...   # exit 0
    def test_a_changed_line_names_the_class(self): ...    # exit 1, stderr contains "MariaDbCompatibilityDriver", not the changed line
    def test_a_missing_class_fails(self): ...             # exit 1, stderr contains "MariaDbQueryStatementValidator"
```

Run: `cd debian/assets && python3 -m unittest tests.test_drugref_adapter_sources`
Expected: FAIL, the script does not exist.

- [ ] **Step 2: Write `debian/check-drugref-adapter-sources.py` and wire it in**

It compares the four classes named in Global Constraints byte for byte after replacing each tree's package name with a placeholder. In `debian/rules`, call it after `fetch-drugref.sh` in the branch that builds DrugRef from source (:148-149); a supplied `DRUGREF_WAR` has no sources and skips it. Add `MariaDbQueryStatementValidator` to the classes `debian/check-drugref-jdbc.py` requires in the staged WAR.

Run: `cd debian/assets && python3 -m unittest discover -s tests -t .`
Expected: `OK`.

- [ ] **Step 3: Mirror the adapter into drugref2026**

Copy the four sources from `carlos`, changing only the package line and the package in imports of each other. This brings the DATE-write correction of #6275 that DrugRef's copy lacks (spec N8) as well as Tasks 2, 4, 5 and 6. Copy `MariaDbQueryStatementValidatorUnitTest` and the oracle fixture the same way. DrugRef's own adapter tests then fail where they assert the bare option message or list every property `settings()` returns; change those expectations exactly as in Task 4 Step 4 and Task 6 Step 4. The mirror adds no dependency, so `debian/drugref-dependencies-lock.json` in `carlos` does not change.

```bash
python3 ../carlos/debian/check-drugref-adapter-sources.py . ../carlos   # expected: exit 0
mvn -B -ntp test                                                       # expected: BUILD SUCCESS
git add src && git commit -m "fix(db): match the CARLOS MariaDB adapter: query guard, session defaults, DATE writes"
```

- [ ] **Step 4: Pin the mirrored commit and build the package check (carlos)**

Set `ref=` in `debian/drugref.pin` to the full SHA of the Step 3 commit (it must be pushed first; push only when the maintainer asks). Rewrite the comment block: what the revision adds, and this restored warning: "The pinned commit must be reachable from drugref2026 `master`. Merge the DrugRef pull request with a merge commit, or repin after a squash merge; do not ship a package pinned to a branch head."

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
  debian/check-drugref-jdbc.py debian/rules debian/drugref.pin
git commit -m "fix(deb): keep the DrugRef adapter copy identical and pin its mirrored revision"
```

### Task 11: Required-case counts and coverage floors

**Files:**
- Modify: `scripts/ci/verify-mariadb-dao-reports.py`, `scripts/ci/test_mariadb_report_evidence.py`, `scripts/ci/test_release_plan_5700.py`, `docs/testing/release-2026.08-verification.json`
- Create or modify: `scripts/coverage/floors/src/main/java/io/github/carlos_emr/carlos/**/<Source>.java.json` and their `scripts/coverage/evidence/` run documents

**Interfaces:**
- Consumes: the thirteen cases added to `JdbcConnectorMariaDbIntegrationTest` and the one added to `MariaDbDaoHarnessIntegrationTest`:

| Method | Cases |
|---|---|
| `shouldRefuseWritesThroughQueryApis_andLeaveTheRowUnchanged(boolean)` | 2 |
| `shouldRenderBinaryBlobCells_inQueryByExampleResults(boolean)` | 2 |
| `shouldOpenSessionsWithConnectorJDefaults_forSqlModeAndWriteTimeout(boolean)` | 2 |
| `shouldKeepIgnoreSpace_whenTheServerDefaultHasIt` | 1 |
| `shouldReturnIntegerObjects_forSmallIntColumns(boolean)` | 2 |
| `shouldReportLockWaitTimeoutsAsTransactionRollbacks_forWaitingAndNowaitLocks(boolean)` | 2 |
| `shouldKeepTheDocumentedDifferencesFromConnectorJ_forDeterministicCases(boolean)` | 2 |
| `MariaDbDaoHarnessIntegrationTest.shouldRefuseANativeWrite_whenItIsRunAsAQuery` | 1 |

- [ ] **Step 1: Raise the counts**

- `JdbcConnectorMariaDbIntegrationTest`: 47 becomes 60 everywhere it is a required count (`REQUIRED` in `verify-mariadb-dao-reports.py`; `requiredFamilies` in the verification JSON; the `47` in `test_release_plan_5700.py`). Parameterized methods: 23 becomes 29 (`test_release_plan_5700.py:114`). The plain-case list there gains `shouldKeepIgnoreSpace_whenTheServerDefaultHasIt`.
- `MariaDbDaoHarnessIntegrationTest`: 1 becomes 2 everywhere it is a required count.
- `REQUIRED_CASES["JdbcConnectorMariaDbIntegrationTest"]` and `JDBC_REQUIRED_CASES` gain the thirteen names (`name(boolean)[1]` and `[2]` for parameterized methods).
- In the verification JSON, the `java-all` `routedSkips` entries for both classes gain the new methods in the shape of their neighbours, and each `minimumCases` of a check that runs these classes rises by the cases added to it.

- [ ] **Step 2: Run the bookkeeping tests**

```bash
python3 -m unittest discover -s scripts/ci -p 'test_mariadb_report_evidence.py'
python3 -m unittest discover -s scripts/ci -p 'test_release_plan_5700.py'
```

Expected: `OK` twice.

- [ ] **Step 3: Refresh the coverage floors**

Changed production sources: `MariaDbCompatibilityDriver`, `MariaDbJdbcCompatibility`, `MariaDbQueryStatementValidator` (new), `OscarTrackingBasicDataSource`, `RptResultStruct`, `AuditLogManager`, `OscarAppointmentDaoImpl`. Follow `docs/testing/coverage-floor-policy.md`, "To update floors after a measured run": produce the measured run document from a passing run that includes the unit tests above and the physical lane, then

```bash
python3 scripts/coverage/manage-coverage-floors.py --import-from <run>/measured-floors.json \
  --source src/main/java/io/github/carlos_emr/carlos/utility/MariaDbCompatibilityDriver.java   # one --source per file above
python3 scripts/coverage/check-coverage-floors.py --freshness-only \
  --floors scripts/coverage/release-coverage-floors.json --base <the base that manifest pins> --output <run>/freshness.json
```

Expected: the freshness check passes. Keep every existing higher floor; no zero floor on a changed executable line; no waiver.

- [ ] **Step 4: Commit**

```bash
git add scripts/ci docs/testing/release-2026.08-verification.json scripts/coverage
git commit -m "test(ci): require the new MariaDB connector cases and refresh coverage floors"
```

### Task 12: Whole-branch verification and the pull request text

**Files:** none changed unless a check fails.

- [ ] **Step 1: Run the unit suite**

Run: `mvn -B -ntp test -Dgroups=unit`
Expected: `BUILD SUCCESS`. Search the output for `S1009` and for `executeQuery() cannot run`: any hit outside the guard's own tests is a statement the guard refused somewhere it should not, and blocks the pull request until explained.

- [ ] **Step 2: Run both MariaDB lanes**

Run the `java-native-generated` command recorded in `docs/testing/release-2026.08-verification.json` against MariaDB 11.8.8 with `CARLOS_TEST_MARIADB_URL`, `CARLOS_TEST_MARIADB_USER`, `CARLOS_TEST_MARIADB_PASSWORD`, `CARLOS_TEST_MARIADB_DAO=true`, `CARLOS_TEST_MARIADB_SCHEMA=generated`; then the Flyway-schema lane recorded beside it. Then `python3 scripts/ci/verify-mariadb-dao-reports.py` with the arguments that file's `main` documents, on each lane's reports.
Expected: every required family and case reported as passed; the same `S1009` search as Step 1 finds nothing unexpected.

- [ ] **Step 3: Run the packaging and probe checks**

```bash
cd debian/assets && python3 -m unittest discover -s tests -t . && cd ../..
python3 -m py_compile scripts/jdbc-connector-probe/*.py
```

Expected: `OK`, then no output. Where a MariaDB 11.8.8 and the reference driver are available, re-run the probe (`scripts/jdbc-connector-probe/README.md`) against the adapter and confirm the `SESSION` section now shows `sql_mode` empty and `net_write_timeout` 600, and the `DMLQ` section shows every write refused.

- [ ] **Step 4: Write the pull request body (do not open it until asked)**

Follow the repository's pull request template. It must state:

- Closes #5922, #5968, #5969, #5770, #5863, #6088. Refs #3053 (the documentation work that was #5768). #5744: "Closes" only if Task 10 Step 5 printed `reachable`, otherwise "Refs" with what remains.
- That it contains pull requests #6113, #6238 and #6295, which can be closed in its favour, and takes the #5770 and #5863 corrections from draft #6133 in their narrow form.
- The eight findings N1 to N8 with one line each and the spec path.
- What was verified and how (the commands of Steps 1 to 3 and their results), and what was not: no browser run; the report-budget table if it was not re-measured; anything a failing pin row changed.
- Left for maintainers: the files under `.github/` named in #3053; the drugref2026 merges; follow-ups in `carlos-ctl` and `carlos-podman` (spec section 9).
