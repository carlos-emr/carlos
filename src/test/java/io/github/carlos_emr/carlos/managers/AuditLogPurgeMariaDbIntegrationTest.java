/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Real dump/restore and JDBC transactions; only synthetic records in an owned schema. */
@org.junit.jupiter.api.parallel.Isolated
@Tag("integration") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_URL", matches = "jdbc:mariadb:.*")
class AuditLogPurgeMariaDbIntegrationTest {
    @TempDir Path temporary;
    private final String database = "carlos_test_audit_" + UUID.randomUUID().toString().replace("-", "");
    private BasicDataSource source;
    private Properties settings;
    private final Date through = java.sql.Date.valueOf(LocalDate.of(2000, 1, 1));

    @BeforeEach
    void createOwnedSchema() throws Exception {
        String server = System.getenv("CARLOS_TEST_MARIADB_URL");
        assertThat(server).matches("jdbc:mariadb://[^/@?#;\\s]+/");
        source = new io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource();
        source.setDriverClassName("org.mariadb.jdbc.Driver");
        source.setUrl(server + database + "?sslMode=disable");
        source.setUsername(System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        source.setPassword(System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        try (Connection connection = DriverManager.getConnection(server, source.getUsername(), source.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        }
        execute("CREATE TABLE log(id BIGINT AUTO_INCREMENT PRIMARY KEY, dateTime DATETIME(6) NOT NULL, data TEXT) ENGINE=InnoDB");
        execute("INSERT INTO log(dateTime,data) VALUES ('2000-01-01 00:00:00','FAKE O\\'Brien é'),"
                + "('2000-01-01 23:59:59.999999','FAKE last second'),('2000-01-02 00:00:00','FAKE next day')");
        settings = new Properties();
        settings.setProperty("log.purge.minDays", "0");
        settings.setProperty("log.purge.mysqldump", System.getenv().getOrDefault("CARLOS_TEST_DUMP", "/usr/bin/mariadb-dump"));
        settings.setProperty("log.purge.outputdir", temporary.toString());
    }

    @AfterEach
    void dropOwnedSchema() throws Exception {
        if (source != null) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + database);
            } finally { source.close(); }
        }
    }

    @Test
    void shouldRestoreEveryDeletedValue_whenPurgingThroughTheLastInstantOfTheDay() throws Exception {
        AtomicInteger audits = new AtomicInteger();
        assertThat(new AuditLogPurgeService(source).purge(settings, through, audits::incrementAndGet)).isEqualTo(2);
        assertThat(ids()).containsExactly(3L);
        assertThat(audits).hasValue(1);
        List<Path> files;
        try (var walk = Files.walk(temporary)) { files = walk.filter(Files::isRegularFile).toList(); }
        assertThat(files).noneMatch(p -> p.getFileName().toString().equals("connection.cnf"));
        Path backup = files.stream().filter(p -> p.toString().endsWith(".sql")).findFirst().orElseThrow();
        assertThat(Files.getPosixFilePermissions(backup)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.readString(backup)).contains("FAKE last second").doesNotContain("FAKE next day");
        // Restore into the same owned table: the preserved next-day row must coexist.
        Process restore = new ProcessBuilder("/usr/bin/mariadb", "--no-defaults", "--protocol=TCP",
                "--host=" + host(), "--port=" + port(), "--user=" + source.getUsername(), database)
                .redirectInput(backup.toFile()).redirectError(temporary.resolve("restore-errors").toFile())
                .start();
        // Native CI uses an empty-password isolated root account. No secret in argv.
        assertThat(restore.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(restore.exitValue()).isZero();
        assertThat(ids()).containsExactly(1L, 2L, 3L);
        try (var connection = source.getConnection(); var query = connection.createStatement();
             var result = query.executeQuery("SELECT data FROM log WHERE id=1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("FAKE O'Brien é");
        }
    }

    @Test
    void shouldRetainRowsAndReleaseLocks_whenDumpFailsTimesOutOrOmitsRows() throws Exception {
        Path fake = temporary.resolve("dump-client");
        String version = "if [ \"$2\" = --version ]; then echo 'MariaDB synthetic fault client'; exit 0; fi\n";
        for (String fault : List.of("exit 7\n", "exit 0\n", "exec sleep 10\n")) {
            Files.writeString(fake, "#!/bin/sh\n" + version + fault);
            Files.setPosixFilePermissions(fake, PosixFilePermissions.fromString("rwx------"));
            settings.setProperty("log.purge.mysqldump", fake.toString());
            assertThatThrownBy(() -> new AuditLogPurgeService(source, Duration.ofSeconds(1)).purge(settings, through,
                    () -> fail("No audit/delete before a verified dump"))).isInstanceOf(java.io.IOException.class);
            assertThat(ids()).containsExactly(1L, 2L, 3L);
            try (var walk = Files.walk(temporary)) {
                assertThat(walk.noneMatch(p -> p.getFileName().toString().equals("connection.cnf"))).isTrue();
            }
        }
        settings.setProperty("log.purge.mysqldump", System.getenv().getOrDefault("CARLOS_TEST_DUMP", "/usr/bin/mariadb-dump"));
        assertThat(new AuditLogPurgeService(source).purge(settings, through, () -> {})).isEqualTo(2);
    }

    @Test
    void shouldRetainBackedUpRows_whenAuditRecordingFailsBeforeDelete() throws Exception {
        assertThatThrownBy(() -> new AuditLogPurgeService(source).purge(settings, through,
                () -> { throw new IllegalStateException("synthetic audit refusal"); })).isInstanceOf(java.io.IOException.class);
        assertThat(ids()).containsExactly(1L, 2L, 3L);
        assertThat(new AuditLogPurgeService(source).purge(settings, through, () -> {})).isEqualTo(2);
    }

    @Test
    void shouldSerializePurgesAndPreserveLateHistoricalRows_whenAnotherWriterRuns() throws Exception {
        var writer = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> inserted = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            assertThat(new AuditLogPurgeService(source).purge(settings, through, () -> {
                assertThatThrownBy(() -> new AuditLogPurgeService(source).purge(settings, through, () -> {}))
                        .isInstanceOf(java.io.IOException.class);
                inserted.set(writer.submit(() -> {
                    try { execute("INSERT INTO log(dateTime,data) VALUES ('1999-01-01','FAKE late insert')"); }
                    catch (Exception failure) { throw new RuntimeException(failure); }
                }));
            })).isEqualTo(2);
            inserted.get().get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(ids()).containsExactly(3L, 4L);
        } finally { writer.shutdownNow(); }
    }

    private void execute(String sql) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }

    @Test
    void shouldRecordPurgeAuditWithoutBlocking_whenAllExistingRowsAreEligible() throws Exception {
        execute("DELETE FROM log WHERE id=3");
        assertThat(new AuditLogPurgeService(source).purge(settings, through, () -> {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout=2");
                statement.execute("INSERT INTO log(dateTime,data) VALUES (NOW(),'FAKE synchronous purge audit')");
            } catch (Exception failure) { throw new RuntimeException(failure); }
        })).isEqualTo(2);
        assertThat(ids()).containsExactly(4L);
    }

    @Test
    void shouldUseLiveCredentials_whenApplicationUserHasQuotedPassword() throws Exception {
        String user = database.substring(0, 30);
        String password = "FAKE-purge'quote\\slash#; spaces";
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var create = connection.prepareStatement("CREATE USER '" + user + "'@'%' IDENTIFIED BY ?")) {
                create.setString(1, password); create.execute();
            }
            statement.execute("GRANT ALL ON " + database + ".* TO '" + user + "'@'%'");
        }
        try (var application = new io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource()) {
            application.setDriverClassName("org.mariadb.jdbc.Driver");
            application.setUrl(source.getUrl()); application.setUsername(user); application.setPassword(password);
            assertThat(new AuditLogPurgeService(application).purge(settings, through, () -> {})).isEqualTo(2);
            assertThat(ids()).containsExactly(3L);
        } finally { execute("DROP USER '" + user + "'@'%'"); }
    }

    @Test
    void shouldRetainCompletedBackupsAndReportFailure_whenALaterCohortFails() throws Exception {
        try (var connection = source.getConnection(); var insert = connection.prepareStatement(
                "INSERT INTO log(dateTime,data) VALUES ('2000-01-01','FAKE bounded cohort')")) {
            for (int i = 0; i < 1050; i++) insert.addBatch();
            insert.executeBatch();
        }
        Path marker = temporary.resolve("dump-once");
        Path wrapper = temporary.resolve("fail-second-cohort");
        String real = settings.getProperty("log.purge.mysqldump");
        Files.writeString(wrapper, "#!/bin/sh\nif [ \"$2\" != --version ]; then\n"
                + "  test ! -e '" + marker + "' || exit 7\n  touch '" + marker + "'\nfi\nexec '" + real + "' \"$@\"\n");
        Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwx------"));
        settings.setProperty("log.purge.mysqldump", wrapper.toString());
        assertThatThrownBy(() -> new AuditLogPurgeService(source).purge(settings, through, () -> {}))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("did not finish");
        assertThat(ids()).hasSize(53).contains(3L);
        Path backup;
        try (var walk = Files.walk(temporary)) {
            backup = walk.filter(p -> p.toString().endsWith(".sql")).findFirst().orElseThrow();
        }
        assertThat(Files.readAllLines(backup).stream().filter(line -> line.startsWith("INSERT INTO")).count()).isEqualTo(1000);
        settings.setProperty("log.purge.mysqldump", real);
        assertThat(new AuditLogPurgeService(source).purge(settings, through, () -> {})).isEqualTo(52);
        assertThat(ids()).containsExactly(3L);
    }
    private List<Long> ids() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id FROM log ORDER BY id")) {
            List<Long> values = new java.util.ArrayList<>();
            while (rows.next()) values.add(rows.getLong(1));
            return values;
        }
    }
    private String host() { return java.net.URI.create(source.getUrl().substring(5)).getHost(); }
    private String port() { return Integer.toString(java.net.URI.create(source.getUrl().substring(5)).getPort() == -1
            ? 3306 : java.net.URI.create(source.getUrl().substring(5)).getPort()); }
}
