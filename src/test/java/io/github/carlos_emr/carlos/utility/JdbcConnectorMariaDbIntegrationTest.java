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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnRequestedNativeTypes_whenUnwrappingJdbcObjects(boolean legacy) throws Exception {
        try (var source = source(legacy, ""); var connection = source.getConnection();
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1")) {
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
}
