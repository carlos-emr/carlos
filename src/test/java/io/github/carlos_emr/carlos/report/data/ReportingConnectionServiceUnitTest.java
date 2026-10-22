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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

/**
 * Unit tests for {@link ReportingConnectionService}: the settings it requires before it will
 * connect, how it classifies login failures, and the {@code SHOW GRANTS} audit that refuses a
 * reporting account holding more than {@code SELECT} on reportable tables (issue #3372).
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("Reporting database connection service")
class ReportingConnectionServiceUnitTest {
    private static final String USAGE = "GRANT USAGE ON *.* TO `carlos_report`@`localhost` IDENTIFIED BY PASSWORD "
            + "'*791C51FBA9525165285F44C57F2B3F5E11DDE6AC' WITH MAX_USER_CONNECTIONS 6 MAX_STATEMENT_TIME 30.000000";
    private static final String SELECT_DEMOGRAPHIC = "GRANT SELECT ON `carlos`.`demographic` TO `carlos_report`@`localhost`";

    private Properties properties;
    private Connection connection;
    private Statement statement;
    private AtomicReference<String> connectedUrl;
    private AtomicReference<Properties> connectedCredentials;
    private ReportingConnectionService service;

    @BeforeEach
    void setUp() throws SQLException {
        properties = new Properties();
        properties.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("db_uri", "jdbc:mysql://127.0.0.1:3306/");
        properties.setProperty("db_name", "carlos?zeroDateTimeBehavior=round&jdbcCompliantTruncation=false");
        properties.setProperty("db_username", "carlos");
        properties.setProperty(ReportingConnectionService.USERNAME_PROPERTY, "carlos_report");
        properties.setProperty(ReportingConnectionService.PASSWORD_PROPERTY, "s3cret-report");

        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        connectedUrl = new AtomicReference<>();
        connectedCredentials = new AtomicReference<>();
        service = new ReportingConnectionService((url, credentials) -> {
            connectedUrl.set(url);
            connectedCredentials.set(credentials);
            return connection;
        });
    }

    private void grants(String... lines) throws SQLException {
        ResultSet currentUser = mock(ResultSet.class);
        when(currentUser.next()).thenReturn(true, false);
        when(currentUser.getString(1)).thenReturn("carlos_report@localhost");
        when(statement.executeQuery("SELECT CURRENT_USER()")).thenReturn(currentUser);

        ResultSet grantRows = mock(ResultSet.class);
        List<Boolean> more = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            more.add(true);
        }
        more.add(false);
        when(grantRows.next()).thenReturn(more.get(0), more.subList(1, more.size()).toArray(new Boolean[0]));
        if (lines.length > 0) {
            when(grantRows.getString(1)).thenReturn(lines[0], Arrays.copyOfRange(lines, 1, lines.length));
        }
        when(statement.executeQuery("SHOW GRANTS")).thenReturn(grantRows);
    }

    @Nested
    @DisplayName("settings")
    class Settings {
        @Test
        @DisplayName("connects to the application database URL with the reporting credentials")
        void shouldConnectWithReportingCredentials_toApplicationDatabaseUrl() throws SQLException {
            grants(USAGE, SELECT_DEMOGRAPHIC);

            Connection opened = service.openConnection(properties);

            assertThat(opened).isSameAs(connection);
            assertThat(connectedUrl.get())
                    .isEqualTo("jdbc:mysql://127.0.0.1:3306/carlos?zeroDateTimeBehavior=round&jdbcCompliantTruncation=false");
            assertThat(connectedCredentials.get().getProperty("user")).isEqualTo("carlos_report");
            assertThat(connectedCredentials.get().getProperty("password")).isEqualTo("s3cret-report");
            verify(connection, never()).close();
        }

        @ParameterizedTest
        @ValueSource(strings = {ReportingConnectionService.USERNAME_PROPERTY, ReportingConnectionService.PASSWORD_PROPERTY,
                "db_uri", "db_name"})
        @DisplayName("refuses without connecting when a required setting is missing")
        void shouldRefuseWithoutConnecting_whenRequiredSettingIsBlank(String property) {
            properties.setProperty(property, "  ");

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class);
            assertThat(connectedUrl.get()).isNull();
        }

        @Test
        @DisplayName("reports whether the reporting account is configured without touching the database")
        void shouldReportConfigured_forCompleteSettings() {
            assertThat(ReportingConnectionService.isConfigured(properties)).isTrue();

            properties.remove(ReportingConnectionService.PASSWORD_PROPERTY);

            assertThat(ReportingConnectionService.isConfigured(properties)).isFalse();
            assertThat(connectedUrl.get()).isNull();
        }

        @Test
        @DisplayName("refuses to run reports as the application account")
        void shouldRefuse_whenReportingAccountIsTheApplicationAccount() {
            properties.setProperty(ReportingConnectionService.USERNAME_PROPERTY, "CARLOS");

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class)
                    .hasMessageContaining("application account");
            assertThat(connectedUrl.get()).isNull();
        }
    }

    @Nested
    @DisplayName("login")
    class Login {
        @ParameterizedTest
        @ValueSource(ints = {1044, 1045})
        @DisplayName("reports the account unavailable when MariaDB refuses the login or the schema")
        void shouldReportUnavailable_whenDatabaseRefusesLogin(int errorCode) {
            SQLException refused = new SQLException("Access denied", "28000", errorCode);
            service = new ReportingConnectionService((url, credentials) -> {
                throw refused;
            });

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class)
                    .hasCause(refused);
        }

        @Test
        @DisplayName("propagates a connection failure that is not a login refusal")
        void shouldPropagate_whenConnectFailsForAnotherReason() {
            SQLException down = new SQLNonTransientConnectionException("Communications link failure", "08S01", 0);
            service = new ReportingConnectionService((url, credentials) -> {
                throw down;
            });

            assertThatThrownBy(() -> service.openConnection(properties)).isSameAs(down);
        }

        @Test
        @DisplayName("reports the account unavailable when the driver does not accept the URL")
        void shouldReportUnavailable_whenDriverReturnsNoConnection() {
            service = new ReportingConnectionService((url, credentials) -> null);

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("grant audit")
    class GrantAudit {
        @Test
        @DisplayName("accepts usage plus table and column SELECT grants on reportable tables")
        void shouldReturnConnection_whenGrantsAreReportableSelectsOnly() throws SQLException {
            grants(USAGE, SELECT_DEMOGRAPHIC,
                    "GRANT SELECT (`note_id`, `note`) ON `carlos`.`casemgmt_note` TO `carlos_report`@`localhost`");

            assertThat(service.openConnection(properties)).isSameAs(connection);
            verify(connection, never()).close();
        }

        @Test
        @DisplayName("accepts the single-quoted grantee older MariaDB releases print")
        void shouldAcceptLegacyQuotedGrantee_whenServerPrintsSingleQuotes() throws SQLException {
            grants("GRANT USAGE ON *.* TO 'carlos_report'@'localhost'",
                    "GRANT SELECT ON `carlos`.`demographic` TO 'carlos_report'@'localhost'");

            assertThat(service.openConnection(properties)).isSameAs(connection);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "GRANT SELECT, INSERT ON `carlos`.`demographic` TO `carlos_report`@`localhost`",
                "GRANT INSERT ON `carlos`.`demographic` TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `carlos`.* TO `carlos_report`@`localhost`",
                "GRANT SELECT ON *.* TO `carlos_report`@`localhost`",
                "GRANT ALL PRIVILEGES ON `carlos`.* TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `drugref2`.`drugs` TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `carlos`.`security` TO `carlos_report`@`localhost`",
                "GRANT SELECT (`user_name`) ON `carlos`.`security` TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `carlos`.`PageMonitor` TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `carlos`.`demographic` TO `carlos_report`@`localhost` WITH GRANT OPTION",
                "GRANT USAGE ON *.* TO `carlos_report`@`localhost` WITH GRANT OPTION",
                "GRANT `reporting_writer` TO `carlos_report`@`localhost`",
                "SET DEFAULT ROLE `reporting_writer` FOR `carlos_report`@`localhost`",
                "GRANT INSERT ON `carlos`.`demographic` TO `reporting_writer`",
                "GRANT SELECT ON `carlos`.`demographic` TO PUBLIC",
                "GRANT PROXY ON ''@'%' TO `carlos_report`@`localhost`",
                "GRANT EXECUTE ON FUNCTION `carlos`.`f` TO `carlos_report`@`localhost`",
                "GRANT SELECT ON `carlos`.`demographic` TO `carlos_report`@`%`"})
        @DisplayName("refuses and closes the connection for any grant beyond reportable SELECT")
        void shouldRefuseAndClose_whenGrantExceedsReportableSelect(String grant) throws SQLException {
            grants(USAGE, SELECT_DEMOGRAPHIC, grant);

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class);
            verify(connection).close();
        }

        @Test
        @DisplayName("never logs the grant text, which carries the account's password hash")
        void shouldNotLogPasswordHash_whenRefusingGrant() throws SQLException {
            grants(USAGE, "GRANT SELECT ON `carlos`.`security` TO `carlos_report`@`localhost`");

            try (LogCapture logs = LogCapture.forLogger(ReportingConnectionService.class)) {
                assertThatThrownBy(() -> service.openConnection(properties))
                        .isInstanceOf(ReportingAccountUnavailableException.class)
                        .satisfies(thrown -> assertThat(thrown.getMessage()).doesNotContain("*791C"));
                assertThat(logs.messages()).isNotEmpty();
                assertThat(String.join("\n", logs.messages()))
                        .doesNotContain("*791C")
                        .doesNotContain("IDENTIFIED")
                        .contains("security");
            }
        }

        @Test
        @DisplayName("fails closed when the grants cannot be read")
        void shouldReportUnavailable_whenGrantsCannotBeRead() throws SQLException {
            when(statement.executeQuery(anyString())).thenThrow(new SQLException("SHOW GRANTS failed"));

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class);
            verify(connection).close();
        }

        @Test
        @DisplayName("keeps the refusal when closing the refused connection also fails")
        void shouldSuppressCloseFailure_whenRefusingConnection() throws SQLException {
            grants(USAGE, "GRANT SELECT ON *.* TO `carlos_report`@`localhost`");
            SQLException closeFailure = new SQLException("close failed");
            org.mockito.Mockito.doThrow(closeFailure).when(connection).close();

            assertThatThrownBy(() -> service.openConnection(properties))
                    .isInstanceOf(ReportingAccountUnavailableException.class)
                    .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(closeFailure));
        }
    }

    @Nested
    @DisplayName("grant line classification")
    class GrantClassification {
        @Test
        @DisplayName("accepts a backtick-escaped table name")
        void shouldAcceptEscapedIdentifier_forReportableTable() {
            assertThat(ReportingConnectionService.refusalReason(
                    "GRANT SELECT ON `carlos`.`odd``name` TO `carlos_report`@`localhost`",
                    "carlos_report", "localhost", "carlos")).isEmpty();
        }

        @Test
        @DisplayName("names the restricted table it refuses")
        void shouldNameRestrictedTable_whenRefusing() {
            assertThat(ReportingConnectionService.refusalReason(
                    "GRANT SELECT ON `carlos`.`ServiceClient` TO `carlos_report`@`localhost`",
                    "carlos_report", "localhost", "carlos"))
                    .hasValueSatisfying(reason -> assertThat(reason).contains("ServiceClient"));
        }

        @Test
        @DisplayName("does not mistake a schema with a similar name for the application schema")
        void shouldRefuse_whenSchemaOnlyResemblesApplicationSchema() {
            assertThat(ReportingConnectionService.refusalReason(
                    "GRANT SELECT ON `carlos_test`.`demographic` TO `carlos_report`@`localhost`",
                    "carlos_report", "localhost", "carlos")).isPresent();
        }
    }

    @Test
    @DisplayName("does not connect when the reporting account is the root account")
    void shouldRefuseRootAccount_withoutConnecting() {
        properties.setProperty(ReportingConnectionService.USERNAME_PROPERTY, "root");

        assertThatThrownBy(() -> service.openConnection(properties))
                .isInstanceOf(ReportingAccountUnavailableException.class);
        assertThat(connectedUrl.get()).isNull();
    }

    @Test
    @DisplayName("reports the account unavailable when no registered JDBC driver accepts the URL")
    void shouldReportUnavailable_whenNoRegisteredDriverAcceptsUrl() {
        properties.setProperty("db_uri", "jdbc:carlos-unregistered://127.0.0.1:3306/");
        ReportingConnectionService realConnector = new ReportingConnectionService();

        assertThatThrownBy(() -> realConnector.openConnection(properties))
                .isInstanceOf(ReportingAccountUnavailableException.class);
    }
}
