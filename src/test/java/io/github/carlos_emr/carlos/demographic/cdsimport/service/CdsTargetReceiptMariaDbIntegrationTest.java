/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Actual Connector/J row proofs over connection-local temporary native-table shapes. */
@Tag("integration")
class CdsTargetReceiptMariaDbIntegrationTest {
    @Test
    @org.junit.jupiter.api.Timeout(15)
    void shouldCancelASlowBinaryRead_whenTimeoutIsSetAfterItsMetadataPrepare() throws Exception {
        try (Connection connection = connection()) {
            connection.setNetworkTimeout(Runnable::run, 8000);
            try (var statement =
                    io.github.carlos_emr.carlos.demographic.cds.jdbc.CdsBinaryReads
                            .prepareDriver(connection, "SELECT SLEEP(?)")) {
                // The production admission guard correctly forbids SLEEP. This isolated
                // driver-capability probe uses the same adapter on an owned connection.
                // Its binary adapter already prepares parameter metadata. Connector/J must
                // invalidate that preparation when the owning reader supplies its deadline.
                assertThat(statement.getParameterMetaData().getParameterCount()).isEqualTo(1);
                statement.setInt(1, 10);
                statement.setQueryTimeout(1);
                long started = System.nanoTime();
                assertThatThrownBy(statement::executeQuery)
                        .isInstanceOf(java.sql.SQLTimeoutException.class);
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
                        .isLessThan(java.time.Duration.ofSeconds(8));
            }
        }
    }

    @Test
    void shouldDetectChangedNoteDatePrecision_inTheActualNativeReceipt() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            String extensionId = null;
            try {
                long note;
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO casemgmt_note"
                                    + " (demographic_no,uuid,observation_date,update_date,provider_no,signing_provider_no,note,history,locked,archived)"
                                    + " VALUES ('974234',?,'2026-02-03"
                                    + " 00:00:00',NOW(),'','','Synthetic precision"
                                    + " receipt','',0,0)",
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, java.util.UUID.randomUUID().toString());
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        note = keys.getLong(1);
                    }
                }
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO casemgmt_note_ext (note_id,key_val,`value`) VALUES"
                                        + " (?,?,?)",
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, note);
                    insert.setString(2, "cdsDatePrecision");
                    insert.setString(3, "FULL_DATE");
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        extensionId = keys.getString(1);
                    }
                }
                var entityManager =
                        org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
                var session = org.mockito.Mockito.mock(org.hibernate.Session.class);
                org.mockito.Mockito.when(entityManager.unwrap(org.hibernate.Session.class))
                        .thenReturn(session);
                org.mockito.Mockito.when(
                                session.doReturningWork(org.mockito.ArgumentMatchers.any()))
                        .thenAnswer(
                                call ->
                                        ((org.hibernate.jdbc.ReturningWork<?>) call.getArgument(0))
                                                .execute(connection));
                var service = new CdsTargetReceiptService();
                org.springframework.test.util.ReflectionTestUtils.setField(
                        service, "entityManager", entityManager);
                var receipt = service.capture(java.util.Map.of("casemgmt_note_ext", extensionId));
                assertThat(service.verify(receipt)).isTrue();
                try (var update =
                        connection.prepareStatement(
                                "UPDATE casemgmt_note_ext SET `value`=? WHERE id=?")) {
                    update.setString(1, "YEAR");
                    update.setString(2, extensionId);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                assertThat(service.verify(receipt)).isFalse();
            } finally {
                connection.rollback();
            }
            if (extensionId != null)
                assertThat(
                                CdsTargetReceiptService.rowDigest(
                                        connection, "casemgmt_note_ext", extensionId))
                        .isNull();
        }
    }

    @Test
    void shouldCaptureColumnWitnessesAlongsideTheOriginalReceipt_andIdentifyTheChangedField()
            throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(
                    "CREATE TEMPORARY TABLE demographic (demographic_no INT PRIMARY KEY, content"
                            + " MEDIUMTEXT CHARACTER SET utf8mb4, absent VARCHAR(10), bytes"
                            + " MEDIUMBLOB)");
            try {
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO demographic VALUES (1,?,NULL,X'0001FF')")) {
                    insert.setString(1, "Private synthetic content café 😀");
                    insert.executeUpdate();
                }
                var entityManager =
                        org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
                var session = org.mockito.Mockito.mock(org.hibernate.Session.class);
                org.mockito.Mockito.when(entityManager.unwrap(org.hibernate.Session.class))
                        .thenReturn(session);
                org.mockito.Mockito.when(
                                session.doReturningWork(org.mockito.ArgumentMatchers.any()))
                        .thenAnswer(
                                call ->
                                        ((org.hibernate.jdbc.ReturningWork<?>) call.getArgument(0))
                                                .execute(connection));
                var service = new CdsTargetReceiptService();
                org.springframework.test.util.ReflectionTestUtils.setField(
                        service, "entityManager", entityManager);
                var original = service.capture(java.util.Map.of("demographic", "1"));
                var baseline =
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec
                                .decode(
                                        original.nativeBaseline(),
                                        original.targetReferences(),
                                        original.targetDigest())
                                .rows()
                                .getFirst();
                assertThat(baseline.digest())
                        .isEqualTo(
                                CdsTargetReceiptService.rowDigest(connection, "demographic", "1"));
                assertThat(original.nativeBaseline())
                        .doesNotContain("Private synthetic content", "café");
                assertThat(service.verify(original)).isTrue();
                statement.executeUpdate("UPDATE demographic SET absent='' WHERE demographic_no=1");
                var changed = service.capture(java.util.Map.of("demographic", "1"));
                var current =
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec
                                .decode(
                                        changed.nativeBaseline(),
                                        changed.targetReferences(),
                                        changed.targetDigest())
                                .rows()
                                .getFirst();
                assertThat(
                                io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsNativeRowComparison.compare(baseline, current)
                                        .changedColumns())
                        .containsExactly("absent");
                assertThat(service.verify(original)).isFalse();
                assertThat(service.verify(changed)).isTrue();
                assertThat(
                                service.verify(
                                        new io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                                .CdsImportReceiptDto(
                                                changed.targetReferences(),
                                                changed.targetDigest(),
                                                false,
                                                "{}")))
                        .isFalse();
            } finally {
                connection.rollback();
            }
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).isNull();
        }
    }

    @Test
    void shouldHashNativeNumbersDatesUnicodeAndBinary_withoutDriverCharacterCoercion()
            throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TEMPORARY TABLE demographic (demographic_no INT PRIMARY KEY, n INT,"
                            + " amount DECIMAL(10,3), event_time DATETIME(6), content MEDIUMTEXT"
                            + " CHARACTER SET utf8mb4, bytes MEDIUMBLOB, absent VARCHAR(10))");
            try (var insert =
                    connection.prepareStatement(
                            "INSERT INTO demographic VALUES (1,42,12.340,'2026-01-02"
                                    + " 03:04:05.123456',?,?,NULL)")) {
                insert.setString(1, "Complete Unicode 😀\n".repeat(10000));
                insert.setBytes(2, new byte[] {0, 1, (byte) 255});
                insert.executeUpdate();
            }
            String original = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(original).matches("[0-9a-f]{64}");
            assertThat(original).isEqualTo(legacyTemporaryDemographicDigest(connection));
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1"))
                    .isEqualTo(original);
            statement.executeUpdate("UPDATE demographic SET absent='' WHERE demographic_no=1");
            String empty = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(empty).isNotEqualTo(original);
            statement.executeUpdate(
                    "UPDATE demographic SET event_time='2026-01-02 03:04:05.123457' WHERE"
                            + " demographic_no=1");
            String fraction = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(fraction).isNotEqualTo(empty);
            statement.executeUpdate(
                    "UPDATE demographic SET bytes=X'0001FE' WHERE demographic_no=1");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1"))
                    .isNotEqualTo(fraction);
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "2")).isNull();
            assertThatThrownBy(
                            () ->
                                    CdsTargetReceiptService.rowDigest(
                                            connection, "unknown_table", "1"))
                    .isInstanceOf(CdsImportException.class)
                    .hasMessageContaining("TARGET_REFERENCE_INVALID");
        }
    }

    @Test
    void shouldExcludeOnlyOperationalFileVerificationEpoch_whenImmutableIdentityStaysEqual()
            throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TEMPORARY TABLE cds_import_file (id VARCHAR(36) PRIMARY KEY, digest"
                            + " CHAR(64), byte_length BIGINT, verified_epoch BIGINT)");
            statement.executeUpdate(
                    "INSERT INTO cds_import_file VALUES ('fixture',REPEAT('a',64),123,1)");
            String original =
                    CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture");
            statement.executeUpdate(
                    "UPDATE cds_import_file SET verified_epoch=2 WHERE id='fixture'");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture"))
                    .isEqualTo(original);
            statement.executeUpdate(
                    "UPDATE cds_import_file SET byte_length=124 WHERE id='fixture'");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture"))
                    .isNotEqualTo(original);
        }
    }

    @Test
    void shouldResolveEveryAllowlistedKey_againstTheActualMigratedNativeTables() throws Exception {
        try (Connection connection = connection()) {
            for (String table : CdsTargetReceiptService.targetTables()) {
                String id =
                        java.util.Set.of("ctl_document", "casemgmt_issue_notes").contains(table)
                                ? "2147483647_2147483647"
                                : "2147483647";
                assertThat(CdsTargetReceiptService.rowDigest(connection, table, id))
                        .as("Absent native key resolves without an invalid column: %s", table)
                        .isNull();
            }
        }
    }

    @Test
    void shouldHashAndDetectAdmissionChanges_usingTheActualNativePrimaryKey() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                String id;
                try (var insert =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " admission(client_id,program_id,provider_no,admission_date,admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)"
                                    + " VALUES(2147483646,2147483646,'T099',NOW(),0,0,'current',NOW())",
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        id = keys.getString(1);
                    }
                }
                String original = CdsTargetReceiptService.rowDigest(connection, "admission", id);
                assertThat(original).matches("[0-9a-f]{64}");
                assertThat(CdsTargetReceiptService.rowDigest(connection, "admission", id))
                        .isEqualTo(original);
                try (var update =
                        connection.prepareStatement(
                                "UPDATE admission SET admission_status='discharged' WHERE"
                                        + " am_id=?")) {
                    update.setString(1, id);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                assertThat(CdsTargetReceiptService.rowDigest(connection, "admission", id))
                        .isNotEqualTo(original);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectAnExactFloatChange_hiddenByTheOriginalSqlReceipt() throws Exception {
        try (Connection connection = connection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TEMPORARY TABLE demographic (demographic_no INT PRIMARY KEY, dose FLOAT"
                            + " NULL, other DOUBLE NULL)");
            statement.executeUpdate("INSERT INTO demographic VALUES (1,0.1,0.1)");
            var entityManager = org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
            var session = org.mockito.Mockito.mock(org.hibernate.Session.class);
            org.mockito.Mockito.when(entityManager.unwrap(org.hibernate.Session.class))
                    .thenReturn(session);
            org.mockito.Mockito.when(session.doReturningWork(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(
                            call ->
                                    ((org.hibernate.jdbc.ReturningWork<?>) call.getArgument(0))
                                            .execute(connection));
            var service = new CdsTargetReceiptService();
            org.springframework.test.util.ReflectionTestUtils.setField(
                    service, "entityManager", entityManager);
            var original = service.capture(java.util.Map.of("demographic", "1"));
            assertThat(service.verify(original)).isTrue();
            try (var update =
                    io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionGuard
                            .prepareBinaryRead(
                                    connection,
                                    "SELECT dose FROM demographic WHERE demographic_no=?")) {
                update.setInt(1, 1);
                try (var rows = update.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getFloat(1)).isEqualTo(0.1f);
                }
            }
            statement.executeUpdate(
                    "UPDATE demographic SET dose=0.10000000894069672 WHERE demographic_no=1");
            var changed = service.capture(java.util.Map.of("demographic", "1"));
            assertThat(changed.targetReferences()).isEqualTo(original.targetReferences());
            assertThat(changed.targetDigest()).isEqualTo(original.targetDigest());
            assertThat(service.verify(original)).isFalse();
            assertThat(service.verify(changed)).isTrue();
            var baseline =
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec
                            .decode(
                                    original.nativeBaseline(),
                                    original.targetReferences(),
                                    original.targetDigest())
                            .rows()
                            .getFirst();
            var current =
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec
                            .decode(
                                    changed.nativeBaseline(),
                                    changed.targetReferences(),
                                    changed.targetDigest())
                            .rows()
                            .getFirst();
            assertThat(
                            io.github.carlos_emr.carlos.demographic.cds.contract
                                    .CdsNativeRowComparison.compare(baseline, current)
                                    .changedColumns())
                    .containsExactly("dose");
        }
    }

    /**
     * Frozen pre-extraction protocol algorithm; temporary fixture only, with no shared row-codec
     * dependency.
     */
    private static String legacyTemporaryDemographicDigest(Connection connection) throws Exception {
        var hash = java.security.MessageDigest.getInstance("SHA-256");
        java.util.function.Consumer<String> add =
                value -> {
                    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    hash.update(
                            Integer.toString(bytes.length)
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    hash.update((byte) ':');
                    hash.update(bytes);
                };
        add.accept("carlos-native-row-v2");
        try (var definition = connection.prepareStatement("select * from demographic where 1=0");
                var empty = definition.executeQuery()) {
            var metadata = empty.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String column = metadata.getColumnName(i);
                add.accept(column);
                add.accept(metadata.getColumnTypeName(i));
                add.accept(Integer.toString(metadata.getPrecision(i)));
                add.accept(Integer.toString(metadata.getScale(i)));
                try (var projection =
                                connection.prepareStatement(
                                        "select `"
                                                + column
                                                + "` is null,octet_length(cast(`"
                                                + column
                                                + "` as binary)),sha2(cast(`"
                                                + column
                                                + "` as binary),256) from demographic where"
                                                + " demographic_no=1");
                        var row = projection.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    boolean absent = row.getBoolean(1);
                    hash.update(absent ? (byte) 0 : (byte) 1);
                    if (!absent) {
                        add.accept(Long.toString(row.getLong(2)));
                        add.accept(row.getString(3).toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }
        return java.util.HexFormat.of().formatHex(hash.digest());
    }

    private static Connection connection() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit protected synthetic MariaDB tier required");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) {
            properties.load(input);
        }
        String url = properties.getProperty("jdbcUrl", "");
        if (!url.matches("jdbc:mariadb://[^/]+/(?:carlos_cds_test|cds_full_test)(?:\\?.*)?"))
            throw new IllegalArgumentException("Disposable CDS database required");
        Connection connection =
                DriverManager.getConnection(
                        url,
                        properties.getProperty("jdbcUser"),
                        properties.getProperty("jdbcPassword"));
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()
                    || !java.util.Set.of("carlos_cds_test", "cds_full_test")
                            .contains(result.getString(1))) {
                connection.close();
                throw new IllegalArgumentException("Disposable CDS database required");
            }
        }
        return connection;
    }
}
