/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Executes the shipped client SQL body on a separately owned physical database. */
@Tag("integration")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class SoapTokenHousekeepingDaoIntegrationTest {
    private static final long NOW_SECONDS = 1_700_000_000L;
    private static final long CUTOFF = NOW_SECONDS * 1000 - 86_400_000;
    private IsolatedJpaTestDatabase database;
    private Connection connection;
    private String cleanup;
    private int sequence;

    @BeforeAll void openDatabase() throws Exception {
        database = new IsolatedJpaTestDatabase("soap_token_cleanup");
        connection = database.dataSource().getConnection();
        if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
            execute("CREATE TABLE SecurityToken(id INT AUTO_INCREMENT PRIMARY KEY, token VARCHAR(100), data VARCHAR(255), expiry DATETIME) ENGINE=InnoDB");
        }
        String script = Files.readString(Path.of("scripts/sql/purge-expired-soap-tokens.sql"));
        assertThat(script).contains("\nDELIMITER //\n", "\nEND//\nDELIMITER ;\n");
        // DELIMITER belongs to the interactive client; JDBC sends the complete unchanged block at once.
        cleanup = script.replace("DELIMITER //\n", "").replace("END//\nDELIMITER ;", "END");
    }
    @BeforeEach void resetRowsAndClock() throws Exception {
        connection.setAutoCommit(true); execute("DELETE FROM SecurityToken");
        execute("SET timestamp=" + NOW_SECONDS); execute("SET time_zone='+00:00'");
        sequence = 0;
    }
    @AfterEach void restoreSession() throws Exception {
        if (!connection.getAutoCommit()) { connection.rollback(); connection.setAutoCommit(true); }
        execute("DROP TRIGGER IF EXISTS reject_cleanup_delete"); execute("SET timestamp=DEFAULT");
    }
    @AfterAll void closeDatabase() throws Exception {
        if (connection != null) connection.close(); if (database != null) database.close();
    }
    private void execute(String sql) throws Exception { try (var query = connection.createStatement()) { query.execute(sql); } }
    private long count() throws Exception {
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM SecurityToken")) {
            rows.next(); return rows.getLong(1);
        }
    }
    private String token() { return "soap1:" + String.format(java.util.Locale.ROOT, "%064x", ++sequence); }
    private void insert(String token, String metadata, String legacyExpiry) throws Exception {
        try (var query = connection.prepareStatement("INSERT INTO SecurityToken(token,data,expiry) VALUES (?,?,?)")) {
            query.setString(1, token); query.setString(2, metadata); query.setString(3, legacyExpiry); query.executeUpdate();
        }
    }
    private static String metadata(long expires) { return "soap1:41:" + (expires - 60_000) + ":" + expires + ":" + "1".repeat(64); }
    private void expired() throws Exception { insert(token(), metadata(CUTOFF), "2099-01-01 00:00:00"); }
    private int clean() throws Exception {
        try (var query = connection.createStatement()) {
            boolean result = query.execute(cleanup);
            while (!result && query.getUpdateCount() != -1) result = query.getMoreResults();
            assertThat(result).isTrue();
            try (var rows = query.getResultSet()) { assertThat(rows.next()).isTrue(); int count = rows.getInt(1); assertThat(rows.next()).isFalse(); return count; }
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"+00:00", "-08:00", "+13:00"})
    void shouldUseEpochExpiryAcrossDatabaseZones_withoutDeletingRecentOrOtherPurposeTokens(String zone) throws Exception {
        execute("SET time_zone='" + zone + "'"); // Fixed test-owned offsets, never request input.
        expired();
        insert(token(), metadata(CUTOFF + 1), "2000-01-01 00:00:00");
        insert(token(), metadata(NOW_SECONDS * 1000 + 60_000), "2000-01-01 00:00:00");
        insert("other-purpose", "synthetic non-SOAP data", "2000-01-01 00:00:00");
        assertThat(clean()).isEqualTo(1); assertThat(count()).isEqualTo(3);
        assertThat(clean()).isZero(); assertThat(count()).isEqualTo(3);
    }
    @Test void shouldBoundDeletionBatches_andReportActualIdempotentCounts() throws Exception {
        for (int index = 0; index < 501; index++) expired();
        assertThat(clean()).isEqualTo(500); assertThat(count()).isEqualTo(1);
        assertThat(clean()).isEqualTo(1); assertThat(count()).isZero(); assertThat(clean()).isZero();
    }
    @Test void shouldRefuseCorruptSoapMetadata_withoutPartiallyDeletingValidRows() throws Exception {
        for (String invalid : List.of("soap1:41:garbage", metadata(CUTOFF) + "\n",
                "soap1:2147483648:100:200:" + "1".repeat(64),
                "soap1:41:100:9223372036854775808:" + "1".repeat(64),
                "soap1:41:200:100:" + "1".repeat(64),
                "soap1:41:0:86400001:" + "1".repeat(64))) {
            execute("DELETE FROM SecurityToken"); expired(); insert(token(), invalid, "2000-01-01 00:00:00");
            assertThatThrownBy(this::clean).isInstanceOf(SQLException.class).hasMessageContaining("cleanup rolled back");
            assertThat(count()).isEqualTo(2);
        }
        execute("DELETE FROM SecurityToken"); expired();
        insert("SOAP1:" + "a".repeat(64), metadata(CUTOFF), "2000-01-01 00:00:00");
        assertThatThrownBy(this::clean).isInstanceOf(SQLException.class).hasMessageContaining("cleanup rolled back");
        assertThat(count()).isEqualTo(2);
    }
    @Test void shouldRollbackEarlierDeletes_whenALaterDeleteFails() throws Exception {
        expired(); expired();
        execute("CREATE TRIGGER reject_cleanup_delete BEFORE DELETE ON SecurityToken FOR EACH ROW "
                + "BEGIN IF OLD.token='soap1:" + String.format(java.util.Locale.ROOT, "%064x", sequence)
                + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic cleanup deletion failure'; END IF; END");
        assertThatThrownBy(this::clean).isInstanceOf(SQLException.class).hasMessageContaining("Synthetic cleanup deletion failure");
        assertThat(count()).isEqualTo(2);
    }
    @Test void shouldRefuseNonTransactionalStorage_beforeAnyDeletion() throws Exception {
        expired(); execute("ALTER TABLE SecurityToken ENGINE=MyISAM");
        try {
            assertThatThrownBy(this::clean).isInstanceOf(SQLException.class).hasMessageContaining("requires an InnoDB table");
            assertThat(count()).isEqualTo(1);
        } finally { execute("ALTER TABLE SecurityToken ENGINE=InnoDB"); }
    }
    @Test void shouldRefuseAnExistingTransaction_withoutCommittingOrRollingBackCallerWork() throws Exception {
        connection.setAutoCommit(false); insert("other-purpose", "synthetic uncommitted row", "2000-01-01 00:00:00");
        assertThatThrownBy(this::clean).isInstanceOf(SQLException.class).hasMessageContaining("dedicated session");
        assertThat(count()).isEqualTo(1);
        try (var observer = database.dataSource().getConnection(); var query = observer.createStatement();
             var rows = query.executeQuery("SELECT COUNT(*) FROM SecurityToken")) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
        connection.commit();
        try (var observer = database.dataSource().getConnection(); var query = observer.createStatement();
             var rows = query.executeQuery("SELECT COUNT(*) FROM SecurityToken")) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(1); }
    }
}
