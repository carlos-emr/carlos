/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

/**
 * Shows that the reporting account, not the application validator, is what keeps prohibited data
 * out of Query by Example (issue #3372, "prohibited data remains inaccessible if application-level
 * table classification is incomplete").
 *
 * <p>Each test owns a uniquely named in-memory H2 database. The administrator connection creates a
 * few CARLOS-shaped tables seeded with marker secrets, creates a {@code carlos_report} user, and
 * grants it {@code SELECT} exactly as the shipped grant list does: on each table that exists here
 * and is {@code reportable} in {@code db/reporting/carlos_report_grants.sql}. Query by Example then
 * runs through {@link RptByExampleData} on that user's connection. A table with hidden columns is
 * left ungranted here, because H2 has no column privileges; the MariaDB column grants are covered by
 * the live checks in {@code docs/query-by-example-least-privilege.md}.</p>
 */
@Tag("integration")
@Tag("report")
@Tag("security")
@DisplayName("Query by Example against a least-privileged reporting account (isolated H2)")
class RptByExampleDataReportingAccountIntegrationTest {
    private static final String REPORT_USER = "carlos_report";
    private static final String REPORT_PASSWORD = "reporting-" + UUID.randomUUID();
    private static final String PATIENT_MARKER = "FAKE-QBE-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String PASSWORD_HASH = "pbkdf2-hash-" + UUID.randomUUID();
    private static final String SESSION_ID = "JSESSION-" + UUID.randomUUID();
    private static final String FUTURE_SECRET = "future-secret-" + UUID.randomUUID();

    private String url;
    private Connection administrator;
    private Properties properties;
    private List<String> granted;

    @BeforeEach
    void createDatabaseAndReportingAccount() throws Exception {
        url = "jdbc:h2:mem:qbe_reporting_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        // Closing this last connection destroys the database, including when a test fails.
        administrator = DriverManager.getConnection(url, "sa", "");
        try (Statement sql = administrator.createStatement()) {
            sql.execute("CREATE TABLE demographic (demographic_no INT PRIMARY KEY, last_name VARCHAR(30), first_name VARCHAR(30))");
            sql.execute("CREATE TABLE security (security_no INT PRIMARY KEY, user_name VARCHAR(30), password VARCHAR(255))");
            sql.execute("CREATE TABLE PageMonitor (id INT PRIMARY KEY, pageName VARCHAR(30), session VARCHAR(80))");
            sql.execute("CREATE TABLE casemgmt_note (note_id INT PRIMARY KEY, note VARCHAR(255), password VARCHAR(255))");
            // A table a future migration might add, classified nowhere: not in the grant list and not
            // in the application validator's restricted list.
            sql.execute("CREATE TABLE qbe_future_credentials (id INT PRIMARY KEY, api_secret VARCHAR(80))");

            sql.execute("INSERT INTO demographic VALUES (1, '" + PATIENT_MARKER + "', 'FAKE-Alex')");
            sql.execute("INSERT INTO security VALUES (1, 'carlosdoc', '" + PASSWORD_HASH + "')");
            sql.execute("INSERT INTO PageMonitor VALUES (1, 'eChart', '" + SESSION_ID + "')");
            sql.execute("INSERT INTO casemgmt_note VALUES (1, 'FAKE note', 'note-lock-password')");
            sql.execute("INSERT INTO qbe_future_credentials VALUES (1, '" + FUTURE_SECRET + "')");

            sql.execute("CREATE USER " + REPORT_USER + " PASSWORD '" + REPORT_PASSWORD + "'");
            granted = grantLikeTheShippedGrantList(sql);
        }
        properties = new Properties();
        properties.setProperty("db_name", "carlos?zeroDateTimeBehavior=round");
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        try {
            LegacyJdbcQuery.releaseThreadResources();
        } finally {
            if (administrator != null) {
                administrator.close();
            }
        }
    }

    /** Grants SELECT on each table present here that the shipped grant list calls fully reportable. */
    private List<String> grantLikeTheShippedGrantList(Statement sql) throws Exception {
        Map<String, ReportingGrantList.Entry> grantList = ReportingGrantList.read();
        List<String> grants = new ArrayList<>();
        for (String table : List.of("demographic", "security", "PageMonitor", "casemgmt_note", "qbe_future_credentials")) {
            ReportingGrantList.Entry entry = grantList.get(table);
            if (entry != null && entry.reportable() && entry.excludedColumns().isEmpty()) {
                sql.execute("GRANT SELECT ON " + table.toLowerCase(Locale.ROOT) + " TO " + REPORT_USER);
                grants.add(table);
            }
        }
        return grants;
    }

    private Connection reportingConnection(Properties ignored) throws SQLException {
        return DriverManager.getConnection(url, REPORT_USER, REPORT_PASSWORD);
    }

    /** Query by Example exactly as production wires it, except for the connection's origin. */
    private RptByExampleData productionValidator() {
        return new RptByExampleData(this::reportingConnection);
    }

    /** Query by Example whose application-level classification is gone: no restricted-table list. */
    private RptByExampleData withoutApplicationClassification() {
        return new RptByExampleData(this::reportingConnection,
                (sql, ignored) -> LegacyJdbcQuery.trustedSelectSql(sql));
    }

    @Test
    @DisplayName("grants the reporting account only the fully reportable tables of the shipped list")
    void shouldGrantOnlyReportableTables_whenProvisionedFromShippedList() {
        assertThat(granted).containsExactly("demographic");
    }

    @Test
    @DisplayName("reads a reportable table through the reporting account")
    void shouldReturnRows_whenQueryReadsReportableTable() throws SQLException {
        RptByExampleData.QueryResult result = productionValidator().execute(
                "SELECT demographic_no, last_name FROM demographic", properties, "999998");

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.html()).contains(PATIENT_MARKER);
    }

    @Test
    @DisplayName("refuses a table nobody classified even though the application validator allows it")
    void shouldRefuseUnclassifiedTable_whenApplicationValidatorAllowsIt() {
        assertThat(QueryByExampleSqlValidator.SECURITY_SENSITIVE_TABLES).doesNotContain("qbe_future_credentials");

        assertThatThrownBy(() -> productionValidator().execute(
                "SELECT * FROM qbe_future_credentials", properties, "999998"))
                .isInstanceOf(QueryByExampleAccessDeniedException.class)
                .satisfies(thrown -> assertThat(thrown.toString()).doesNotContain(FUTURE_SECRET));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT password FROM security",
            "SELECT session FROM PageMonitor",
            "SELECT d.last_name FROM demographic d WHERE EXISTS (SELECT 1 FROM security s WHERE s.password IS NOT NULL)",
            "SELECT d.last_name, (SELECT MAX(session) FROM PageMonitor) AS s FROM demographic d",
            "SELECT note, password FROM casemgmt_note"})
    @DisplayName("refuses restricted data when the application's restricted-table list is missing")
    void shouldRefuseRestrictedData_whenApplicationClassificationIsMissing(String query) {
        assertThatThrownBy(() -> withoutApplicationClassification().execute(query, properties, "999998"))
                .isInstanceOf(QueryByExampleAccessDeniedException.class)
                .satisfies(thrown -> assertThat(thrown.toString())
                        .doesNotContain(PASSWORD_HASH)
                        .doesNotContain(SESSION_ID));
    }

    @Test
    @DisplayName("refuses writes on the reporting account's own connection")
    void shouldRefuseWrites_whenReportingAccountIsHandedAStatement() throws SQLException {
        try (Connection reporting = reportingConnection(properties); Statement sql = reporting.createStatement()) {
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM demographic"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(thrown -> assertThat(QueryByExampleAccessDeniedException.isAccessDenied((SQLException) thrown))
                            .isTrue());
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE security SET password = 'x'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("CREATE TABLE qbe_report_scratch (id INT)"))
                    .isInstanceOf(SQLException.class);
        }
        try (Statement sql = administrator.createStatement();
             var rows = sql.executeQuery("SELECT COUNT(*) FROM demographic")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }
}
