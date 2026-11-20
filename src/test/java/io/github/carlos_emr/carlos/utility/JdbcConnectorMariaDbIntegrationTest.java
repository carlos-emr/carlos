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
package io.github.carlos_emr.carlos.utility;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises migration-sensitive JDBC behavior on an owned physical MariaDB schema. */
@org.junit.jupiter.api.parallel.Isolated
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@Tag("integration")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_URL", matches = "jdbc:mariadb:.*")
class JdbcConnectorMariaDbIntegrationTest {
    private final String database = "carlos_test_jdbc_" + UUID.randomUUID().toString().replace("-", "");
    private final Properties credentials = new Properties();
    private String server;
    private boolean created;

    @BeforeEach
    void createOwnedSchema() throws SQLException {
        server = System.getenv("CARLOS_TEST_MARIADB_URL");
        assertThat(server).matches("jdbc:mariadb://[^/@?#;\\s]+/");
        credentials.setProperty("user", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        try (Connection connection = DriverManager.getConnection(server, credentials);
             Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
            assertThat(connection.getMetaData().getDriverName()).contains("MariaDB");
            statement.executeUpdate("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            created = true;
        }
    }

    @AfterEach
    void dropOwnedSchema() throws SQLException {
        if (created) {
            try (Connection connection = DriverManager.getConnection(server, credentials);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE " + database);
            }
        }
    }

    private OscarTrackingBasicDataSource source(boolean legacy, String options) {
        var source = new OscarTrackingBasicDataSource();
        source.setDriverClassName(legacy ? "com.mysql.cj.jdbc.Driver" : "org.mariadb.jdbc.Driver");
        String configured = server + database + "?connectionCollation=utf8mb4_general_ci&" + options;
        source.setUrl(legacy ? configured.replace("jdbc:mariadb:", "jdbc:mysql:") : configured);
        source.setUsername(credentials.getProperty("user"));
        source.setPassword(credentials.getProperty("password"));
        source.setConnectionInitSqls(java.util.List.of("SET SESSION sql_mode=''"));
        return source;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveGeneratedKeysUnicodeBinaryAndBooleanMetadata_forBothConfigurations(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jdbc_probe(id INT AUTO_INCREMENT PRIMARY KEY, text_value VARCHAR(80), "
                    + "binary_value VARBINARY(16), checked_value TINYINT(1), bit_value BIT(1))");
            long id;
            try (var insert = connection.prepareStatement("INSERT INTO jdbc_probe(text_value,binary_value,checked_value,bit_value) "
                    + "VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "Synthetic O'Brien \uD83D\uDE80 \u00E9");
                insert.setBytes(2, new byte[]{0, 1, -1, 0});
                insert.setBoolean(3, true);
                insert.setBoolean(4, false);
                assertThat(insert.executeUpdate()).isEqualTo(1);
                try (var keys = insert.getGeneratedKeys()) {
                    assertThat(keys.next()).isTrue();
                    id = keys.getLong(1);
                    assertThat(id).isPositive();
                    assertThat(keys.next()).isFalse();
                }
            }
            try (var rows = statement.executeQuery("SELECT * FROM jdbc_probe WHERE id=" + id)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("text_value")).isEqualTo("Synthetic O'Brien \uD83D\uDE80 \u00E9");
                assertThat(rows.getBytes("binary_value")).containsExactly(0, 1, -1, 0);
                assertThat(rows.getBoolean("checked_value")).isTrue();
                assertThat(rows.getBoolean("bit_value")).isFalse();
                assertThat(isCheckbox(rows, "checked_value")).isTrue();
                assertThat(isCheckbox(rows, "bit_value")).isTrue();
                assertThat(rows.getMetaData().getColumnLabel(2)).isEqualTo("text_value");
            }
            try (var tables = connection.getMetaData().getTables(database, null, "jdbc_probe", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
                assertThat(tables.getString("TABLE_NAME")).isEqualTo("jdbc_probe");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveLegacyFormInsertionAndUpdate_withUpdatableResultSets(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            statement.execute("CREATE TABLE jdbc_form(id INT AUTO_INCREMENT PRIMARY KEY, checked_value TINYINT(1), form_date DATE)");
            try (var rows = statement.executeQuery("SELECT * FROM jdbc_form")) {
                rows.moveToInsertRow();
                rows.updateInt("id", 0);
                rows.updateBoolean("checked_value", true);
                rows.updateDate("form_date", Date.valueOf("2026-11-05"));
                rows.insertRow();
                rows.moveToCurrentRow();
            }
            try (var rows = statement.executeQuery("SELECT * FROM jdbc_form")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("id")).isPositive();
                assertThat(rows.getBoolean("checked_value")).isTrue();
                assertThat(rows.getDate("form_date").toLocalDate()).isEqualTo(LocalDate.of(2026, Month.NOVEMBER, 5));
                rows.updateBoolean("checked_value", false);
                rows.updateRow();
            }
            try (var rows = statement.executeQuery("SELECT checked_value FROM jdbc_form")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveTransactionRollbackAndBatchCounts_withExplicitTransaction(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jdbc_transaction(id INT PRIMARY KEY, value_text VARCHAR(30)) ENGINE=InnoDB");
            connection.setAutoCommit(false);
            try (var batch = connection.prepareStatement("INSERT INTO jdbc_transaction VALUES (?,?)")) {
                for (int id = 1; id <= 2; id++) {
                    batch.setInt(1, id);
                    batch.setString(2, "Synthetic " + id);
                    batch.addBatch();
                }
                assertThat(batch.executeBatch()).containsExactly(1, 1);
            }
            connection.rollback();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM jdbc_transaction")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveLegacyStreamingRequests_forStatementsAndPreparedStatements(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.setFetchSize(Integer.MIN_VALUE);
            try (var rows = statement.executeQuery("SELECT 1 AS id UNION ALL SELECT 2")) {
                assertThat(rows.isWrapperFor(org.mariadb.jdbc.client.result.StreamingResult.class)).isTrue();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.next()).isFalse();
            }
            try (var prepared = connection.prepareStatement("SELECT ? AS id UNION ALL SELECT ?")) {
                prepared.setFetchSize(Integer.MIN_VALUE);
                prepared.setInt(1, 3);
                prepared.setInt(2, 4);
                try (var rows = prepared.executeQuery()) {
                    assertThat(rows.isWrapperFor(org.mariadb.jdbc.client.result.StreamingResult.class)).isTrue();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(3);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(4);
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveAliasMetadata_whenPolicyIsExplicit(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jdbc_alias(original_value INT)");
            statement.execute("INSERT INTO jdbc_alias VALUES (1)");
        }
        for (boolean aliases : new boolean[]{false, true}) {
            try (var source = source(legacy, "useOldAliasMetadataBehavior=" + aliases);
                 var connection = source.getConnection(); var statement = connection.createStatement()) {
                for (boolean streaming : new boolean[]{false, true}) {
                    statement.setFetchSize(streaming ? Integer.MIN_VALUE : 0);
                    try (var rows = statement.executeQuery("SELECT original_value AS display_value FROM jdbc_alias AS display_table")) {
                        assertThat(rows.next()).isTrue();
                        var metadata = rows.getMetaData();
                        assertThat(metadata.getColumnLabel(1)).isEqualTo("display_value");
                        assertThat(metadata.getColumnName(1)).isEqualTo(aliases ? "display_value" : "original_value");
                        assertThat(metadata.getTableName(1)).isEqualTo(aliases ? "display_table" : "jdbc_alias");
                        assertThatThrownBy(() -> metadata.getTableName(0)).isInstanceOf(SQLException.class);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ResourceLock("java.util.TimeZone")
    void shouldPreserveDateWrites_whenJvmAndConnectionZonesDiffer(boolean legacy) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String jvm : java.util.List.of("UTC", "America/Toronto", "Asia/Tokyo")) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvm));
                for (String zone : java.util.List.of("UTC", "America/Toronto", "Asia/Tokyo")) {
                    for (boolean prepared : java.util.List.of(false, true)) {
                        try (var source = source(legacy, "connectionTimeZone=" + zone + "&useServerPrepStmts=" + prepared);
                             var connection = source.getConnection(); var statement = connection.createStatement()) {
                            statement.execute("CREATE TEMPORARY TABLE jdbc_date_writes(kind VARCHAR(40) PRIMARY KEY, value DATE)");
                            try (var insert = connection.prepareStatement("INSERT INTO jdbc_date_writes VALUES (?,?)")) {
                                for (String kind : java.util.List.of("default", "null-calendar", "explicit-calendar",
                                        "object", "typed-object", "sqltype-object")) {
                                    insert.setString(1, kind);
                                    Date date = Date.valueOf("2026-11-07");
                                    switch (kind) {
                                        case "default" -> insert.setDate(2, date);
                                        case "null-calendar" -> insert.setDate(2, date, null);
                                        case "explicit-calendar" -> insert.setDate(2, date,
                                                Calendar.getInstance(TimeZone.getTimeZone("UTC"), java.util.Locale.US));
                                        case "object" -> insert.setObject(2, date);
                                        case "typed-object" -> insert.setObject(2, date, java.sql.Types.DATE);
                                        case "sqltype-object" -> insert.setObject(2, date, java.sql.JDBCType.DATE);
                                        default -> throw new AssertionError(kind);
                                    }
                                    assertThat(insert.executeUpdate()).isEqualTo(1);
                                }
                            }
                            statement.executeUpdate("INSERT INTO jdbc_date_writes VALUES ('updated-date',NULL),('updated-object',NULL),"
                                    + "('updated-indexed-date',NULL),('updated-indexed-object',NULL),('updated-typed-object',NULL),('updated-scaled-object',NULL)");
                            try (var update = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                                 var row = update.executeQuery("SELECT kind,value FROM jdbc_date_writes WHERE kind LIKE 'updated-%' ORDER BY kind")) {
                                while (row.next()) {
                                    Date date = Date.valueOf("2026-11-07");
                                    switch (row.getString(1)) {
                                        case "updated-date" -> row.updateDate("value", date);
                                        case "updated-object" -> row.updateObject("value", date);
                                        case "updated-indexed-date" -> row.updateDate(2, date);
                                        case "updated-indexed-object" -> row.updateObject(2, date);
                                        case "updated-typed-object" -> row.updateObject("value", date, java.sql.JDBCType.DATE);
                                        case "updated-scaled-object" -> row.updateObject(2, date, 0);
                                        default -> throw new AssertionError("Unexpected owned date row");
                                    }
                                    row.updateRow();
                                }
                            }
                            try (var rows = statement.executeQuery("SELECT kind,CAST(value AS CHAR) FROM jdbc_date_writes ORDER BY kind")) {
                                int count = 0;
                                var assertions = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
                                while (rows.next()) {
                                    String kind = rows.getString(1);
                                    String expected = "2026-11-07";
                                    if (kind.equals("explicit-calendar")) {
                                        var utc = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                                        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
                                        expected = utc.format(Date.valueOf("2026-11-07"));
                                    }
                                    String actual = rows.getString(2), wanted = expected;
                                    assertions.add(() -> assertThat(actual).as("%s JVM=%s connection=%s serverPrepared=%s", kind, jvm, zone, prepared)
                                            .isEqualTo(wanted));
                                    count++;
                                }
                                assertThat(count).isEqualTo(12);
                                org.junit.jupiter.api.Assertions.assertAll(assertions);
                            }
                        }
                    }
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ResourceLock("java.util.TimeZone")
    void shouldUseCurrentJvmZoneForDateWrites_afterConnectionWasOpened(boolean legacy) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (boolean serverPrepared : java.util.List.of(false, true)) {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                try (var source = source(legacy, "connectionTimeZone=America/Toronto&useServerPrepStmts=" + serverPrepared);
                     var connection = source.getConnection(); var statement = connection.createStatement()) {
                    statement.execute("CREATE TEMPORARY TABLE jdbc_current_zone(id INT PRIMARY KEY,value DATE)");
                    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
                    Date date = Date.valueOf("2026-11-07");
                    try (var insert = connection.prepareStatement("INSERT INTO jdbc_current_zone VALUES (?,?)")) {
                        for (int id = 1; id <= 4; id++) {
                            insert.setInt(1, id);
                            switch (id) {
                                case 1 -> insert.setDate(2, date);
                                case 2 -> insert.setDate(2, date, null);
                                case 3 -> insert.setObject(2, date, java.sql.JDBCType.DATE);
                                case 4 -> insert.setDate(2, null);
                                default -> throw new AssertionError(id);
                            }
                            assertThat(insert.executeUpdate()).isEqualTo(1);
                        }
                    }
                    try (var rows = statement.executeQuery("SELECT COUNT(value),MIN(CAST(value AS CHAR)),MAX(CAST(value AS CHAR)) FROM jdbc_current_zone")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(3);
                        assertThat(rows.getString(2)).isEqualTo("2026-11-07");
                        assertThat(rows.getString(3)).isEqualTo("2026-11-07");
                    }
                    // Prepared updatable results must preserve both DATE and NULL writes,
                    // including the server-prepared execution path.
                    try (var update = connection.prepareStatement("SELECT id,value FROM jdbc_current_zone ORDER BY id",
                            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                         var rows = update.executeQuery()) {
                        while (rows.next()) {
                            if (rows.getInt(1) % 2 == 0) rows.updateDate("value", date);
                            else rows.updateDate(2, date);
                            rows.updateRow();
                        }
                        try (var stored = statement.executeQuery("SELECT COUNT(value),MIN(CAST(value AS CHAR)),MAX(CAST(value AS CHAR)) FROM jdbc_current_zone")) {
                            assertThat(stored.next()).isTrue();
                            assertThat(stored.getInt(1)).isEqualTo(4);
                            assertThat(stored.getString(2)).isEqualTo("2026-11-07");
                            assertThat(stored.getString(3)).isEqualTo("2026-11-07");
                        }
                        rows.beforeFirst();
                        while (rows.next()) {
                            if (rows.getInt(1) % 2 == 0) rows.updateDate("value", null);
                            else rows.updateDate(2, null);
                            rows.updateRow();
                        }
                    }
                    try (var rows = statement.executeQuery("SELECT COUNT(*),COUNT(value) FROM jdbc_current_zone")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(1)).isEqualTo(4);
                        assertThat(rows.getInt(2)).isZero();
                    }
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveNullDateWrites_withEveryCalendarOverload(boolean legacy) throws Exception {
        for (boolean prepared : java.util.List.of(false, true)) {
            try (var source = source(legacy, "connectionTimeZone=America/Toronto&useServerPrepStmts=" + prepared);
                 var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE jdbc_null_dates(value DATE)");
                try (var insert = connection.prepareStatement("INSERT INTO jdbc_null_dates VALUES (?)")) {
                    insert.setDate(1, null);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    insert.setDate(1, null, null);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    insert.setDate(1, null, Calendar.getInstance(TimeZone.getTimeZone("UTC"), java.util.Locale.US));
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*),COUNT(value) FROM jdbc_null_dates")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(3);
                    assertThat(rows.getInt(2)).isZero();
                }
                statement.execute("CREATE TEMPORARY TABLE jdbc_null_updates(id INT PRIMARY KEY,value DATE)");
                statement.executeUpdate("INSERT INTO jdbc_null_updates VALUES(1,'2026-11-07'),(2,'2026-11-07')");
                try (var update = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                     var rows = update.executeQuery("SELECT id,value FROM jdbc_null_updates ORDER BY id")) {
                    while (rows.next()) {
                        if (rows.getInt(1) == 1) rows.updateDate("value", null);
                        else rows.updateDate(2, null);
                        rows.updateRow();
                    }
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*),COUNT(value) FROM jdbc_null_updates")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                    assertThat(rows.getInt(2)).isZero();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ResourceLock("java.util.TimeZone")
    void shouldPreserveNamedDateWrites_withAndWithoutCalendar(boolean legacy) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (var source = source(legacy, "connectionTimeZone=America/Toronto");
             var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE jdbc_named_dates(value DATE)");
            statement.execute("CREATE PROCEDURE jdbc_date_parameter(IN input_date DATE) INSERT INTO jdbc_named_dates VALUES(input_date)");
            try (var call = connection.prepareCall("{CALL jdbc_date_parameter(?)}")) {
                call.setDate("input_date", Date.valueOf("2026-11-07"));
                call.execute();
                call.setDate("input_date", Date.valueOf("2026-11-07"), null);
                call.execute();
                call.setDate("input_date", Date.valueOf("2026-11-07"), Calendar.getInstance(TimeZone.getTimeZone("UTC"), java.util.Locale.US));
                call.execute();
                call.setDate("input_date", null);
                call.execute();
                call.setDate("input_date", null, null);
                call.execute();
                call.setObject("input_date", Date.valueOf("2026-11-07"));
                call.execute();
                call.setObject("input_date", Date.valueOf("2026-11-07"), java.sql.Types.DATE);
                call.execute();
                call.setObject("input_date", null, java.sql.Types.DATE);
                call.execute();
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*),COUNT(value),MIN(CAST(value AS CHAR)),MAX(CAST(value AS CHAR)) FROM jdbc_named_dates")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(8);
                assertThat(rows.getInt(2)).isEqualTo(5);
                assertThat(rows.getString(3)).isEqualTo("2026-11-07");
                assertThat(rows.getString(4)).isEqualTo("2026-11-07");
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveDateAndTimestampValues_withExplicitTimezone(boolean legacy) throws Exception {
        Instant instant = Instant.parse("2026-01-14T18:30:45.123456Z");
        try (var source = source(legacy, "serverTimezone=UTC&forceConnectionTimeZoneToSession=true");
             var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jdbc_dates(date_value DATE, instant_value TIMESTAMP(6))");
            try (var insert = connection.prepareStatement("INSERT INTO jdbc_dates VALUES (?,?)")) {
                insert.setDate(1, Date.valueOf("1950-02-28"));
                insert.setTimestamp(2, Timestamp.from(instant));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT * FROM jdbc_dates")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDate(1).toLocalDate()).isEqualTo(LocalDate.of(1950, Month.FEBRUARY, 28));
                assertThat(rows.getTimestamp(2).toInstant()).isEqualTo(instant);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRoundZeroDateValues_whenPolicyIsExplicit(boolean legacy) throws Exception {
        createZeroDateFixture(legacy);
        try (var source = source(legacy, "zeroDateTimeBehavior=round"); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT * FROM jdbc_zero_dates")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDate(1)).hasToString("0001-01-01");
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getTimestamp(2)).hasToString("0001-01-01 00:00:00.0");
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getObject(2)).isEqualTo(LocalDateTime.of(1, Month.JANUARY, 1, 0, 0));
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getObject(1, LocalDate.class)).isEqualTo(LocalDate.of(1, Month.JANUARY, 1));
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getTime(1).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getTime(2).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getString(1)).isEqualTo("0000-00-00");
                assertThat(rows.getObject(2, String.class)).isEqualTo("0000-00-00 00:00:00");
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getNString(2)).isEqualTo("0000-00-00 00:00:00");
                assertThat(rows.wasNull()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveZeroDateRefusalAndNullPolicies_whenConfiguredExplicitly(boolean legacy) throws Exception {
        createZeroDateFixture(legacy);
        try (var source = source(legacy, "zeroDateTimeBehavior=CONVERT_TO_NULL"); var connection = source.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM jdbc_zero_dates")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getDate(1)).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.getTimestamp(2)).isNull();
            assertThat(rows.wasNull()).isTrue();
        }
        try (var source = source(legacy, "zeroDateTimeBehavior=EXCEPTION"); var connection = source.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM jdbc_zero_dates")) {
            assertThat(rows.next()).isTrue();
            assertThatThrownBy(() -> rows.getDate(1)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> rows.getTimestamp(2)).isInstanceOf(SQLException.class);
        }
        for (String policy : new String[]{"ROUND", "CONVERT_TO_NULL"}) {
            try (var source = source(legacy, "zeroDateTimeBehavior=" + policy);
                 var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT CAST('0000-00-00 00:00:00.000000' AS DATETIME(6)), "
                         + "CAST('0000-00-00 00:00:00.000001' AS DATETIME(6))")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("0000-00-00 00:00:00");
                if (policy.equals("ROUND")) assertThat(rows.getObject(1)).isEqualTo(LocalDateTime.of(1, Month.JANUARY, 1, 0, 0));
                else assertThat(rows.getObject(1)).isNull();
                assertThat(rows.getString(2)).isEqualTo("0000-00-00 00:00:00.000001");
                assertThatThrownBy(() -> rows.getTimestamp(2)).isInstanceOf(SQLException.class);
            }
        }
    }

    private void createZeroDateFixture(boolean legacy) throws SQLException {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE jdbc_zero_dates(date_value DATE, timestamp_value DATETIME)");
            statement.execute("INSERT INTO jdbc_zero_dates VALUES ('0000-00-00','0000-00-00 00:00:00')");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainDatetimeObjectsAndTypedGetterContracts_forTemporalColumns(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT CAST('2026-11-05 12:34:56.123456' AS DATETIME(6)) AS moment, CAST(NULL AS DATETIME) AS absent")) {
            assertThat(rows.next()).isTrue();
            LocalDateTime expected = LocalDateTime.of(2026, Month.NOVEMBER, 5, 12, 34, 56, 123456000);
            assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo(LocalDateTime.class.getName());
            assertThat(rows.getObject(1)).isEqualTo(expected);
            assertThat(rows.getObject("moment", Map.of("DATETIME", String.class))).isEqualTo(expected);
            assertThat(rows.getObject(1, LocalDateTime.class)).isEqualTo(expected);
            assertThat(rows.getObject(2)).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThatThrownBy(()->rows.getObject(1, (Class<?>) null)).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->rows.getObject(1, Object.class)).isInstanceOf(SQLException.class);
        }
        try (var source = source(legacy, "treatMysqlDatetimeAsTimestamp=true"); var connection = source.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT CAST('2026-11-05 12:34:56' AS DATETIME)")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1)).isInstanceOf(Timestamp.class);
            assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo(Timestamp.class.getName());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRefuseInvalidPartialDates_withoutDiscardingTheirTimeComponent(boolean legacy) throws Exception {
        try (var source = source(legacy, "zeroDateTimeBehavior=round"); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            for (String partial : new String[]{"2026-00-00", "2026-02-00", "0000-01-01"}) {
                try (var rows = statement.executeQuery("SELECT CAST('" + partial + "' AS DATE)")) {
                    assertThat(rows.next()).isTrue();
                    assertThatThrownBy(()->rows.getDate(1)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->rows.getTimestamp(1)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->rows.getObject(1)).isInstanceOf(SQLException.class);
                    assertThat(rows.getTime(1).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
                    assertThat(rows.wasNull()).isFalse();
                }
            }
            try (var rows = statement.executeQuery("SELECT CAST('0000-00-00 12:34:56' AS DATETIME)")) {
                assertThat(rows.next()).isTrue();
                assertThatThrownBy(()->rows.getDate(1)).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->rows.getTimestamp(1)).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->rows.getObject(1)).isInstanceOf(SQLException.class);
                assertThat(rows.getTime(1).toLocalTime()).isEqualTo(LocalTime.of(12, 34, 56));
                assertThat(rows.wasNull()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @ResourceLock("java.util.TimeZone")
    void shouldPreserveWallClockAndInstantConversions_whenJvmAndConnectionZonesDiffer(boolean legacy) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
        try (var source = source(legacy, "server%54imezone=%55TC&zeroDateTimeBehavior=round");
             var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT CAST('2026-11-05 12:34:56' AS DATETIME) AS moment, "
                     + "CAST('0000-00-00 00:00:00' AS DATETIME) AS zero_value")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("2026-11-05 12:34:56");
            assertThat(rows.getObject(1, String.class)).isEqualTo("2026-11-05 12:34:56");
            try (var reader = rows.getCharacterStream(1)) {
                assertThat(new java.io.BufferedReader(reader).readLine()).isEqualTo("2026-11-05 12:34:56");
            }
            assertThat(rows.getObject(1)).isEqualTo(LocalDateTime.of(2026, Month.NOVEMBER, 5, 12, 34, 56));
            assertThat(rows.getObject(1, LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026, Month.NOVEMBER, 5, 12, 34, 56));
            assertThat(rows.getTimestamp(1).toInstant()).isEqualTo(Instant.parse("2026-11-05T12:34:56Z"));
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), java.util.Locale.US);
            utc.clear(); utc.set(1, Calendar.JANUARY, 1);
            assertThat(rows.getTimestamp(2).getTime()).isEqualTo(utc.getTimeInMillis());
            Calendar toronto = Calendar.getInstance(TimeZone.getTimeZone("America/Toronto"), java.util.Locale.US);
            toronto.clear(); toronto.set(1, Calendar.JANUARY, 1);
            assertThat(rows.getTimestamp(2, toronto).getTime()).isEqualTo(toronto.getTimeInMillis());
            assertThat(rows.getObject(2)).isEqualTo(LocalDateTime.of(1, Month.JANUARY, 1, 0, 0));
            assertThat(rows.wasNull()).isFalse();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldConnectAndApplyConnectorJYesNo_forLegacyBooleanOptions() throws Exception {
        // #5969: these Connector/J spellings failed every MariaDB connection before translation.
        try (var source = source(true, "allowPublicKeyRetrieval=yes&useServerPrepStmts=YES&cachePrepStmts=No"
                + "&tcpKeepAlive=Yes&allowMultiQueries=yes&useAffectedRows=no&yearIsDateType=NO");
             var connection = source.getConnection(); var statement = connection.createStatement()) {
            var configuration = connection.unwrap(org.mariadb.jdbc.Connection.class).getContext().getConf();
            assertThat(configuration.allowPublicKeyRetrieval()).isTrue();
            assertThat(configuration.useServerPrepStmts()).isTrue();
            assertThat(configuration.cachePrepStmts()).isFalse();
            assertThat(configuration.tcpKeepAlive()).isTrue();
            assertThat(configuration.allowMultiQueries()).isTrue();
            assertThat(configuration.useAffectedRows()).isFalse();
            assertThat(configuration.yearIsDateType()).isFalse();
            statement.execute("CREATE TABLE yes_no_probe(id INT PRIMARY KEY, label VARCHAR(10))");
            // allowMultiQueries=yes: one statement string carries two inserts.
            statement.execute("INSERT INTO yes_no_probe VALUES (1, 'same'); INSERT INTO yes_no_probe VALUES (2, 'same')");
            // useAffectedRows=no keeps Connector/J's found-rows count for a row that does not change.
            assertThat(statement.executeUpdate("UPDATE yes_no_probe SET label='same' WHERE id=1")).isEqualTo(1);
            try (var count = connection.prepareStatement("SELECT COUNT(*) FROM yes_no_probe WHERE label=?")) {
                count.setString(1, "same");
                try (var rows = count.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveConnectorJValues_forFloatingAndBitColumns(boolean legacy) throws Exception {
        // #5968: expected values were read from the same rows through MySQL Connector/J 9.6.0. Reports,
        // Query by Example and legacy form loaders print these columns through getString.
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE number_probe(id INT PRIMARY KEY, d DOUBLE, f FLOAT, dd DOUBLE(10,2), "
                    + "b1 BIT(1), b8 BIT(8), b64 BIT(64), t1 TINYINT(1))");
            statement.execute("INSERT INTO number_probe VALUES (1, 100, 1.1, 1.5, b'1', 5, 3, 1), "
                    + "(2, 1.0E7, 16777217, 0, b'0', 0, b'1" + "0".repeat(63) + "', -1), "
                    + "(3, NULL, NULL, NULL, NULL, NULL, NULL, NULL)");
            statement.execute("INSERT INTO number_probe(id, t1) VALUES (4, -128), (5, -2), (6, 0), (7, 127)");
            try (var rows = statement.executeQuery("SELECT * FROM number_probe WHERE id <= 3 ORDER BY id")) {
                assertConnectorJColumnClasses(rows);
                assertThat(rows.next()).isTrue();
                assertConnectorJFirstNumberRow(rows);
                assertThat(rows.next()).isTrue();
                assertConnectorJSecondNumberRow(rows);
                assertThat(rows.next()).isTrue();
                assertConnectorJNullNumberRow(rows);
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT t1 FROM number_probe ORDER BY id")) {
                assertConnectorJTinyIntTruthValues(rows);
            }
        }
        // The binary protocol decodes FLOAT as a float that Connector/J widened before printing.
        try (var source = source(legacy, "useServerPrepStmts=true"); var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT * FROM number_probe WHERE id = ?")) {
            query.setInt(1, 1);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertConnectorJServerPreparedNumberRow(rows);
            }
            query.setInt(1, 3);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertConnectorJNullNumberRow(rows);
            }
            try (var flags = connection.prepareStatement("SELECT t1 FROM number_probe WHERE id >= ? ORDER BY id")) {
                flags.setInt(1, 1);
                try (var rows = flags.executeQuery()) {
                    assertConnectorJTinyIntTruthValues(rows);
                }
            }
        }
        assertConnectorJTinyIntMetadataModes(legacy);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveZeroFilledTextAndUnsignedTinyIntegers_inBothPreparedProtocols(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE numeric_flags(id INT PRIMARY KEY, padded FLOAT(8,2) ZEROFILL, "
                    + "ordinary FLOAT(8,2) UNSIGNED, tiny TINYINT(1) UNSIGNED)");
            statement.execute("INSERT INTO numeric_flags VALUES (1, 1.25, 1.25, 2), (2, 1.25, 1.25, 255), "
                    + "(3, NULL, NULL, NULL)");
        }
        for (boolean prepared : new boolean[]{false, true}) {
            for (boolean transformed : new boolean[]{false, true}) {
                String options = "tinyInt1isBit=true&transformedBitIsBoolean=" + transformed
                        + "&useServerPrepStmts=" + prepared;
                assertZeroFillAndUnsignedTinyIntRows(legacy, options);
            }
        }
    }

    private void assertZeroFillAndUnsignedTinyIntRows(boolean legacy, String options) throws SQLException {
        try (var source = source(legacy, options); var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT padded, ordinary, tiny FROM numeric_flags "
                     + "WHERE id >= ? ORDER BY id")) {
            query.setInt(1, 1);
            try (var rows = query.executeQuery()) {
                var metadata = rows.getMetaData();
                assertThat(metadata.getColumnClassName(3)).isEqualTo(Integer.class.getName());
                assertThat(metadata.getColumnType(3)).isEqualTo(java.sql.Types.TINYINT);
                assertThat(metadata.getColumnTypeName(3)).isEqualTo("TINYINT UNSIGNED");
                for (Integer tiny : new Integer[]{2, 255, null}) {
                    assertThat(rows.next()).isTrue();
                    String padded = tiny == null ? null : "00001.25";
                    assertThat(rows.getString("padded")).isEqualTo(padded);
                    assertThat(rows.wasNull()).isEqualTo(tiny == null);
                    assertThat(rows.getObject(1, String.class)).isEqualTo(padded);
                    assertThat(rows.getString("ordinary")).isEqualTo(tiny == null ? null : "1.25");
                    assertThat(rows.getObject(3)).isEqualTo(tiny);
                    assertThat(rows.wasNull()).isEqualTo(tiny == null);
                    assertThat(rows.getObject("tiny")).isEqualTo(tiny);
                    assertThat(rows.getObject(3, Map.of())).isEqualTo(tiny);
                }
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCheckFloatRange_inBothPreparedProtocols(boolean legacy) throws Exception {
        double boundary = Float.MAX_VALUE;
        var values = new Double[]{1e100, -1e100, Math.nextUp(boundary), Math.nextDown(-boundary),
                boundary, -boundary, null};
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE float_probe(id INT PRIMARY KEY, amount DOUBLE)");
            try (var insert = connection.prepareStatement("INSERT INTO float_probe VALUES (?, ?)")) {
                for (int index = 0; index < values.length; index++) {
                    insert.setInt(1, index);
                    insert.setObject(2, values[index]);
                    insert.executeUpdate();
                }
            }
        }
        for (boolean prepared : new boolean[]{false, true}) {
            for (boolean strict : new boolean[]{false, true}) {
                String options = "useServerPrepStmts=" + prepared + "&jdbcCompliantTruncation=" + strict;
                assertFloatRangeRows(legacy, options, strict, values, boundary);
            }
        }
    }

    private void assertFloatRangeRows(boolean legacy, String options, boolean strict, Double[] values, double boundary) throws SQLException {
        try (var source = source(legacy, options); var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT amount FROM float_probe WHERE id >= ? ORDER BY id")) {
            query.setInt(1, 0);
            try (var rows = query.executeQuery()) {
                for (Double value : values) {
                    assertThat(rows.next()).isTrue();
                    if (strict && value != null && Math.abs(value) > boundary) {
                        assertThatThrownBy(() -> rows.getFloat(1)).isInstanceOfSatisfying(
                                java.sql.SQLDataException.class, error -> assertThat(error.getSQLState()).isEqualTo("22003"));
                        assertThatThrownBy(() -> rows.getObject("amount", Float.class)).isInstanceOfSatisfying(
                                java.sql.SQLDataException.class, error -> assertThat(error.getSQLState()).isEqualTo("22003"));
                    } else {
                        assertThat(rows.getFloat(1)).isEqualTo(value == null ? 0.0f : value.floatValue());
                        assertThat(rows.wasNull()).isEqualTo(value == null);
                        assertThat(rows.getObject("amount", Float.class)).isEqualTo(value == null ? null : value.floatValue());
                    }
                }
                assertThat(rows.next()).isFalse();
            }
        }
    }

    /**
     * Both metadata switches retain the same values and object classes as Connector/J, for TINYINT(1) and for a
     * real BIT(1), which MariaDB alone reports as BOOLEAN when transformedBitIsBoolean is set.
     */
    private void assertConnectorJTinyIntMetadataModes(boolean legacy) throws Exception {
        for (boolean tinyBit : new boolean[]{false, true}) {
            for (boolean transformed : new boolean[]{false, true}) {
                for (boolean prepared : new boolean[]{false, true}) {
                    String options = "tinyInt1isBit=" + tinyBit + "&transformedBitIsBoolean=" + transformed
                            + "&useServerPrepStmts=" + prepared;
                    assertConnectorJTinyIntMetadataMode(legacy, tinyBit, options);
                }
            }
        }
    }

    private void assertConnectorJTinyIntMetadataMode(boolean legacy, boolean tinyBit, String options) throws SQLException {
        try (var source = source(legacy, options); var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT t1 FROM number_probe WHERE id >= ? ORDER BY id");
             var bits = connection.prepareStatement("SELECT b1 FROM number_probe WHERE id <= ? ORDER BY id")) {
            query.setInt(1, 1);
            try (var rows = query.executeQuery()) {
                assertThat(rows.getMetaData().isSigned(1))
                        .as("signed TINYINT(1) metadata with %s", options)
                        .isEqualTo(!tinyBit);
                if (tinyBit) assertConnectorJTinyIntTruthValues(rows);
                else assertConnectorJTinyIntIntegerValues(rows);
            }
            bits.setInt(1, 3);
            try (var rows = bits.executeQuery()) {
                assertConnectorJRealBitOneValues(rows);
            }
        }
    }

    /** A real BIT(1) keeps Connector/J's BIT metadata, "1"/"0" text and numeric reads in every metadata mode. */
    private static void assertConnectorJRealBitOneValues(ResultSet rows) throws SQLException {
        assertThat(rows.getMetaData().getColumnType(1)).isEqualTo(java.sql.Types.BIT);
        assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo(Boolean.class.getName());
        for (Integer expected : new Integer[]{1, 0, null}) {
            assertThat(rows.next()).isTrue();
            String text = expected == null ? null : expected.toString();
            Double number = expected == null ? null : expected.doubleValue();
            assertThat(rows.getString(1)).isEqualTo(text);
            assertThat(rows.wasNull()).isEqualTo(expected == null);
            assertThat(rows.getObject(1, String.class)).isEqualTo(text);
            assertThat(rows.getObject(1)).isEqualTo(expected == null ? null : expected == 1);
            assertThat(rows.getDouble(1)).isEqualTo(expected == null ? 0.0d : number);
            assertThat(rows.wasNull()).isEqualTo(expected == null);
            assertThat(rows.getFloat(1)).isEqualTo(expected == null ? 0.0f : number.floatValue());
            assertThat(rows.getObject(1, Double.class)).isEqualTo(number);
            assertThat(rows.getObject(1, double.class)).isEqualTo(number);
            assertThat(rows.getObject(1, Float.class)).isEqualTo(number == null ? null : number.floatValue());
            assertThat(rows.getBigDecimal(1)).isEqualTo(expected == null ? null : java.math.BigDecimal.valueOf(expected));
        }
        assertThat(rows.next()).isFalse();
    }

    private static void assertConnectorJTinyIntIntegerValues(ResultSet rows) throws SQLException {
        for (Integer expected : new Integer[]{1, -1, null, -128, -2, 0, 127}) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1)).isEqualTo(expected);
            assertThat(rows.wasNull()).isEqualTo(expected == null);
            assertThat(rows.getObject("t1")).isEqualTo(expected);
            assertThat(rows.getObject(1, Map.of())).isEqualTo(expected);
            assertThat(rows.getString(1)).isEqualTo(expected == null ? null : expected.toString());
        }
        assertThat(rows.next()).isFalse();
    }

    /** TINYINT(1) uses Connector/J's positive-or-negative-one truth rule, including untyped object reads. */
    private static void assertConnectorJTinyIntTruthValues(ResultSet rows) throws SQLException {
        for (Boolean expected : new Boolean[]{true, true, null, false, false, false, true}) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1)).isEqualTo(expected);
            assertThat(rows.wasNull()).isEqualTo(expected == null);
            assertThat(rows.getObject("t1")).isEqualTo(expected);
            assertThat(rows.getObject(1, Map.of())).isEqualTo(expected);
            assertThat(rows.getObject("t1", Boolean.class)).isEqualTo(expected);
            assertThat(rows.getBoolean(1)).isEqualTo(Boolean.TRUE.equals(expected));
            assertThat(rows.wasNull()).isEqualTo(expected == null);
        }
        assertThat(rows.next()).isFalse();
    }

    /** Connector/J reported java.lang.Boolean for every BIT width. */
    private static void assertConnectorJColumnClasses(ResultSet rows) throws SQLException {
        var metadata = rows.getMetaData();
        assertThat(metadata.getColumnClassName(rows.findColumn("b1"))).isEqualTo(Boolean.class.getName());
        assertThat(metadata.getColumnClassName(rows.findColumn("b8"))).isEqualTo(Boolean.class.getName());
        assertThat(metadata.getColumnClassName(rows.findColumn("d"))).isEqualTo(Double.class.getName());
    }

    private static void assertConnectorJFirstNumberRow(ResultSet rows) throws SQLException {
        assertConnectorJPrimitiveNumberObjects(rows);
        assertThat(rows.getString("d")).isEqualTo("100.0");
        assertThat(rows.getObject("d", String.class)).isEqualTo("100.0");
        assertThat(rows.getNString("d")).isEqualTo("100.0");
        assertThat(rows.getString("f")).isEqualTo("1.1");
        assertThat(rows.getString("dd")).isEqualTo("1.5");
        assertThat(rows.getBigDecimal("d")).hasToString("100.0");
        assertThat(rows.getObject("dd", java.math.BigDecimal.class)).hasToString("1.5");
        assertThat(rows.getString("b1")).isEqualTo("1");
        assertThat(rows.getObject("b1")).isEqualTo(Boolean.TRUE);
        assertThat(rows.getString("b8")).isEqualTo("5");
        assertThat(rows.getObject("b8")).isEqualTo(new byte[]{5});
        assertThat(rows.getDouble("b8")).isEqualTo(5.0d);
        assertThat(rows.getObject("b8", Double.class)).isEqualTo(5.0d);
        assertThat(rows.getFloat("b8")).isEqualTo(5.0f);
        assertThat(rows.getString("t1")).isEqualTo("1");
        assertThatThrownBy(() -> rows.getCharacterStream("d")).isInstanceOf(SQLException.class);
    }

    private static void assertConnectorJPrimitiveNumberObjects(ResultSet rows) throws SQLException {
        assertThat(rows.getObject("b8", boolean.class)).isTrue();
        assertThat(rows.getObject("b8", byte.class)).isEqualTo((byte) 5);
        assertThat(rows.getObject("b8", short.class)).isEqualTo((short) 5);
        assertThat(rows.getObject("b8", int.class)).isEqualTo(5);
        assertThat(rows.getObject("b8", long.class)).isEqualTo(5L);
        assertThat(rows.getObject("b8", float.class)).isEqualTo(5.0f);
        assertThat(rows.getObject("b8", double.class)).isEqualTo(5.0d);
    }

    private static void assertConnectorJSecondNumberRow(ResultSet rows) throws SQLException {
        assertThat(rows.getString("d")).isEqualTo("1.0E7");
        assertThat(rows.getBigDecimal("d")).hasToString("1.0E+7");
        assertThat(rows.getString("f")).isEqualTo("1.67772E7");
        assertThat(rows.getString("dd")).isEqualTo("0.0");
        assertThat(rows.getString("b1")).isEqualTo("0");
        assertThat(rows.getObject("b1")).isEqualTo(Boolean.FALSE);
        assertThat(rows.getString("b8")).isEqualTo("0");
        // Connector/J printed a BIT's signed long but converted it to numbers as unsigned.
        assertThat(rows.getString("b64")).isEqualTo("-9223372036854775808");
        assertThat(rows.getLong("b64")).isEqualTo(Long.MIN_VALUE);
        assertThat(rows.getDouble("b64")).isEqualTo(9.223372036854776E18d);
        assertThat(rows.getBigDecimal("b64")).hasToString("9223372036854775808");
        // TINYINT(1) is reported as BIT(1) too and keeps its sign.
        assertThat(rows.getString("t1")).isEqualTo("-1");
        assertThat(rows.getDouble("t1")).isEqualTo(-1.0d);
        assertThat(rows.getObject("t1")).isEqualTo(Boolean.TRUE);
    }

    private static void assertConnectorJNullNumberRow(ResultSet rows) throws SQLException {
        for (String column : new String[]{"d", "f", "dd", "b1", "b8", "b64", "t1"}) {
            assertThat(rows.getString(column)).as(column).isNull();
            assertThat(rows.wasNull()).as(column).isTrue();
            for (Class<?> type : new Class<?>[]{boolean.class, byte.class, short.class, int.class,
                    long.class, float.class, double.class}) {
                assertThat(rows.getObject(column, type)).as(column + ": " + type).isNull();
                assertThat(rows.wasNull()).isTrue();
                assertThat(rows.getObject(rows.findColumn(column), type)).isNull();
                assertThat(rows.wasNull()).isTrue();
            }
            assertThat(rows.getObject(column)).as(column).isNull();
            assertThat(rows.wasNull()).as(column).isTrue();
        }
        assertThat(rows.getBigDecimal("d")).isNull();
        assertThat(rows.getDouble("b8")).isZero();
        assertThat(rows.wasNull()).isTrue();
        assertThat(rows.getObject("b8", Double.class)).isNull();
    }

    private static void assertConnectorJServerPreparedNumberRow(ResultSet rows) throws SQLException {
        assertThat(rows.getString("d")).isEqualTo("100.0");
        assertThat(rows.getString("f")).isEqualTo("1.100000023841858");
        assertThat(rows.getString("b1")).isEqualTo("1");
        assertThat(rows.getObject("b1")).isEqualTo(Boolean.TRUE);
        assertThat(rows.getString("b8")).isEqualTo("5");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnRequestedNativeTypes_whenUnwrappingJdbcObjects(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1")) {
            var metadata = rows.getMetaData();
            assertThat(metadata.isWrapperFor(java.sql.ResultSetMetaData.class)).isTrue();
            assertThat(metadata.unwrap(java.sql.ResultSetMetaData.class)).isSameAs(metadata);
            assertThat(statement.unwrap(org.mariadb.jdbc.Statement.class)).isInstanceOf(org.mariadb.jdbc.Statement.class);
            assertThat(rows.unwrap(org.mariadb.jdbc.client.result.Result.class)).isInstanceOf(org.mariadb.jdbc.client.result.Result.class);
            assertThat(rows.getMetaData().unwrap(org.mariadb.jdbc.client.result.ResultSetMetaData.class))
                    .isInstanceOf(org.mariadb.jdbc.client.result.ResultSetMetaData.class);
            assertThat(connection.getMetaData().unwrap(org.mariadb.jdbc.DatabaseMetaData.class))
                    .isInstanceOf(org.mariadb.jdbc.DatabaseMetaData.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreservePartialDateText_withBinaryServerPreparedStatements(boolean legacy) throws Exception {
        try (var source = source(legacy, "useServerPrepStmts=true&zeroDateTimeBehavior=round");
             var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT CAST(? AS DATE) AS date_value, 42 AS sentinel")) {
            for (String value : new String[]{"2026-00-00", "2026-02-00", "0000-00-00", "0000-01-01"}) {
                query.setString(1, value);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(value);
                    assertThat(rows.wasNull()).isFalse();
                    assertThat(rows.getObject(1, String.class)).isEqualTo(value);
                    assertThat(rows.getInt(2)).isEqualTo(42);
                    assertThat(rows.getString(1)).isEqualTo(value);
                    if (!value.equals("0000-00-00")) {
                        assertThatThrownBy(()->rows.getDate(1)).isInstanceOf(SQLException.class);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveEmptyStringNumericCoercion_withExplicitOptOut(boolean legacy) throws Exception {
        for (String options : new String[]{"", "emptyStringsConvertToZero=true", "emptyStringsConvertToZero=false"}) {
            boolean enabled = !options.endsWith("=false");
            try (var source = source(legacy, options); var connection = source.getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT '' AS empty_value, CAST('' AS BINARY) AS binary_value, NULL AS absent")) {
                assertThat(rows.next()).isTrue();
                for (String getter : new String[]{"getByte", "getShort", "getInt", "getLong", "getFloat", "getDouble", "getBoolean", "getBigDecimal"}) {
                    var method = ResultSet.class.getMethod(getter, int.class);
                    if (enabled) {
                        Object value = method.invoke(rows, 1);
                        if (value instanceof Boolean flag) assertThat(flag).isFalse();
                        else assertThat(((Number) value).doubleValue()).isZero();
                        assertThat(rows.wasNull()).isFalse();
                    } else {
                        assertThatThrownBy(()->method.invoke(rows, 1)).hasCauseInstanceOf(SQLException.class);
                    }
                }
                for (Class<?> type : new Class<?>[]{Byte.class, Short.class, Integer.class, Long.class, Float.class,
                        Double.class, Boolean.class, java.math.BigDecimal.class, int.class, boolean.class}) {
                    if (enabled) {
                        Object value = rows.getObject("empty_value", type);
                        if (value instanceof Boolean flag) assertThat(flag).isFalse();
                        else assertThat(((Number) value).doubleValue()).isZero();
                        assertThat(rows.wasNull()).isFalse();
                    } else {
                        assertThatThrownBy(()->rows.getObject("empty_value", type)).isInstanceOf(SQLException.class);
                    }
                }
                if (enabled) assertThat(rows.getInt("binary_value")).isZero();
                else assertThatThrownBy(()->rows.getInt("binary_value")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> rows.getObject("empty_value", java.math.BigInteger.class))
                        .isInstanceOf(SQLException.class);
                assertThat(rows.getObject("empty_value")).isEqualTo("");
                assertThat(rows.getString("empty_value")).isEmpty();
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getInt("absent")).isZero();
                assertThat(rows.wasNull()).isTrue();
                assertThat(rows.getObject("absent", Integer.class)).isNull();
                assertThat(rows.wasNull()).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveByteTruncationBehavior_withExplicitPolicy(boolean legacy) throws Exception {
        for (boolean truncation : new boolean[]{true, false}) {
            try (var source = source(legacy, "jdbcCompliantTruncation=" + truncation);
                 var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT '1' AS one_byte, '12' AS two_bytes, X'FF' AS binary_byte")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getByte("one_byte")).isEqualTo((byte) 49);
                assertThat(rows.getObject("one_byte", Byte.class)).isEqualTo((byte) 49);
                assertThat(rows.wasNull()).isFalse();
                if (truncation) {
                    assertThatThrownBy(() -> rows.getByte("two_bytes")).isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> rows.getObject("two_bytes", Byte.class)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> rows.getByte("binary_byte")).isInstanceOf(SQLException.class);
                } else {
                    assertThat(rows.getByte("two_bytes")).isEqualTo((byte) 49);
                    assertThat(rows.getObject("two_bytes", Byte.class)).isEqualTo((byte) 49);
                    assertThat(rows.getByte("binary_byte")).isEqualTo((byte) -61);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveBooleanConversions_forTextAndNumericValues(boolean legacy) throws Exception {
        for (boolean binary : new boolean[]{false, true}) {
            try (var source = source(legacy, "useServerPrepStmts=" + binary);
                 var connection = source.getConnection()) {
                for (boolean expected : new boolean[]{false, true}) {
                    String[] expressions = expected
                            ? new String[]{"'yes'", "'Yeſ'", "'TRUE'", "'Y'", "'T'", "'1'", "'-1'", "'0.1'", "'1e-2'", "1", "-1", "0.1"}
                            : new String[]{"'false'", "'falſe'", "'FALSE'", "'no'", "'N'", "'F'", "'0'", "'-2'", "'-0.1'", "0", "-2",
                                    "CAST(-2 AS DECIMAL(5,2))", "CAST('false' AS BINARY)"};
                    for (String expression : expressions) {
                        try (var query = connection.prepareStatement("SELECT " + expression + " AS flag");
                             var rows = query.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getBoolean(1)).as(expression).isEqualTo(expected);
                            assertThat(rows.getObject("flag", Boolean.class)).as(expression).isEqualTo(expected);
                            assertThat(rows.wasNull()).isFalse();
                        }
                    }
                }
                for (String expression : new String[]{"'off'", "'arbitrary'", "' 1 '", "b'0'", "b'1'"}) {
                    try (var query = connection.prepareStatement("SELECT " + expression + " AS flag, 42 AS sentinel");
                         var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThatThrownBy(() -> rows.getBoolean("flag")).as(expression).isInstanceOf(SQLException.class);
                        assertThatThrownBy(() -> rows.getObject(1, Boolean.class)).as(expression).isInstanceOf(SQLException.class);
                        assertThat(rows.getInt("sentinel")).isEqualTo(42);
                    }
                }
            }
        }
    }

    private static boolean isCheckbox(ResultSet rows, String name) throws SQLException {
        return io.github.carlos_emr.carlos.form.FrmRecordHelp.isBooleanColumnType(
                rows.getMetaData().getColumnTypeName(rows.findColumn(name)));
    }

    /**
     * A trigger's SIGNAL and an unset parameter are statement failures, not lost connections:
     * Connector/J reports both as SQLTransientConnectionException, the compatibility driver
     * reports them as the plain SQLException MySQL Connector/J gave, and the pooled connection
     * stays usable (#6333).
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReportStatementFailuresAsPlainSqlExceptions_forBothConfigurations(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement()) {
            // A standalone SIGNAL raises exactly what a fault-injection trigger raises, without the
            // SUPER privilege that CREATE TRIGGER needs while binary logging is on.
            assertThatThrownBy(() -> statement.execute("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FAKE-PW owned signal probe'"))
                    .isExactlyInstanceOf(SQLException.class)
                    .hasMessageContaining("FAKE-PW owned signal probe")
                    .satisfies(failure -> {
                        assertThat(((SQLException) failure).getSQLState()).isEqualTo("45000");
                        assertThat(((SQLException) failure).getErrorCode()).isEqualTo(1644);
                    });
            try (var unset = connection.prepareStatement("SELECT ? FROM DUAL")) {
                assertThatThrownBy(unset::executeQuery).isExactlyInstanceOf(SQLException.class)
                        .satisfies(failure -> assertThat(((SQLException) failure).getSQLState()).isEqualTo("07004"));
            }
            try (var rows = statement.executeQuery("SELECT 1")) {
                assertThat(rows.next()).isTrue();
            }
        }
    }
}
