/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AuditLogPurgeServiceUnitTest {
    @TempDir Path temporary;

    @Test
    void shouldLogSafePreparationFailure_withoutOpeningDatabaseOrCreatingBackups() throws Exception {
        var source = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var settings = new java.util.Properties();
        settings.setProperty("log.purge.minDays", "FAKE-sensitive-retention");
        var messages = new java.util.ArrayList<String>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(AuditLogPurgeService.class);
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("audit-preparation-test", null,
                org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start(); logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> new AuditLogPurgeService(source).purge(settings, new java.util.Date(), () -> {}))
                    .isInstanceOf(java.io.IOException.class).hasMessageContaining("retention policy", "no rows were deleted");
        } finally { logger.removeAppender(appender); appender.stop(); }
        assertThat(messages).anyMatch(message -> message.contains("retention policy") && message.contains("no rows were deleted"))
                .noneMatch(message -> message.contains("FAKE-sensitive-retention"));
        org.mockito.Mockito.verifyNoInteractions(source);
        try (var files = Files.list(temporary)) { assertThat(files).isEmpty(); }
    }

    @Test
    void shouldRefuseMissingDate_beforeOpeningDatabaseOrPreparingClient() throws Exception {
        var source = mock(javax.sql.DataSource.class);
        assertThatThrownBy(() -> new AuditLogPurgeService(source).purge(new java.util.Properties(), null, () -> {}))
                .isInstanceOf(IOException.class).hasMessageContaining("retention policy", "no rows were deleted");
        verifyNoInteractions(source);
        try (var files = Files.list(temporary)) { assertThat(files).isEmpty(); }
    }

    @Test
    void shouldRefuseUnsafeRetention_whenDateOrSettingIsInvalid() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        for (String setting : List.of("-1", "invalid", "2147483648")) {
            assertThatThrownBy(() -> AuditLogPurgeService.validateRetention(setting, today.minusYears(20), today))
                    .isInstanceOf(java.io.IOException.class);
        }
        assertThatThrownBy(() -> AuditLogPurgeService.validateRetention("3650", today, today)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> AuditLogPurgeService.validateRetention("0", null, today)).isInstanceOf(java.io.IOException.class);
        assertThatCode(() -> AuditLogPurgeService.validateRetention("10", today.minusDays(10), today)).doesNotThrowAnyException();
    }

    @Test
    void shouldRequireExactDumpIds_whenRowsAreMissingDuplicatedOrUnexpected() throws Exception {
        Path sql = temporary.resolve("backup.sql");
        String first = "INSERT INTO `log` (`id`, `dateTime`, `data`) VALUES (1,'2000-01-01','FAKE');\n";
        String second = "INSERT INTO `log` (`id`, `dateTime`, `data`) VALUES (2,'2000-01-01','FAKE');\n";
        Files.writeString(sql, first + second);
        assertThatCode(() -> AuditLogPurgeService.verifyDump(sql, List.of(1L, 2L))).doesNotThrowAnyException();
        for (String content : List.of(first, first + first, first + second + first,
                "INSERT INTO `other` VALUES (1);\n", "-- no rows\n")) {
            Files.writeString(sql, content);
            assertThatThrownBy(() -> AuditLogPurgeService.verifyDump(sql, List.of(1L, 2L))).isInstanceOf(java.io.IOException.class);
        }
    }

    @Test
    void shouldUseActiveHostAndPrivateEscapedCredentials_whenPreparingClientOptions() throws Exception {
        Configuration live = Configuration.parse("jdbc:mariadb://original.invalid:3306/FAKE?sslMode=disable&user=FAKE-user&password=FAKE-secret");
        HostAddress actual = HostAddress.from("active.invalid", 4407);
        String options = AuditLogPurgeService.clientOptions(live, actual, true);
        assertThat(options).contains("host=\"active.invalid\"", "port=\"4407\"", "password=\"FAKE-secret\"", "ssl=\"0\"")
                .doesNotContain("original.invalid");
    }

    @Test
    void shouldPreserveDumpFailureAndInterruptFlag_whenForcedReapingAlsoFails() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(false);
        IOException primary = new IOException("Synthetic primary dump failure");
        boolean priorInterrupt = Thread.interrupted();
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(AuditLogPurgeService.class)) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> {
                try (var cleanup = new AuditLogPurgeService.DumpProcess(process)) { throw primary; }
            }).isSameAs(primary);
            assertThat(primary.getSuppressed()).hasSize(1);
            assertThat(primary.getSuppressed()[0]).isInstanceOf(IOException.class).hasMessageContaining("could not be reaped");
            assertThat(Thread.interrupted()).isTrue();
            verify(process).destroyForcibly();
            assertThat(logs.messages()).anyMatch(message -> message.contains("could not be reaped after forced termination"));
        } finally {
            Thread.interrupted();
            if (priorInterrupt) Thread.currentThread().interrupt();
        }
    }

    @Test
    void shouldReportInterruptedReaping_whenTerminationWaitIsInterrupted() throws Exception {
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(true);
        when(process.waitFor(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Synthetic interrupt"));
        boolean priorInterrupt = Thread.interrupted();
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(AuditLogPurgeService.class)) {
            assertThatThrownBy(() -> new AuditLogPurgeService.DumpProcess(process).close())
                    .isInstanceOf(IOException.class).hasMessageContaining("reaping was interrupted");
            assertThat(Thread.interrupted()).isTrue();
            verify(process).destroyForcibly();
            assertThat(logs.messages()).anyMatch(message -> message.contains("reaping was interrupted"));
        } finally {
            Thread.interrupted();
            if (priorInterrupt) Thread.currentThread().interrupt();
        }
    }

    @Test
    void shouldPreservePrimaryFailureAndAbortConnection_whenRollbackAndPoolInvalidationFail() throws Exception {
        var pool = mock(org.apache.commons.dbcp2.BasicDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("Synthetic rollback failure");
        IllegalStateException invalidation = new IllegalStateException("Synthetic invalidation failure");
        doThrow(rollback).when(connection).rollback();
        doThrow(invalidation).when(pool).invalidateConnection(connection);
        var cleanup = new AuditLogPurgeService(pool).new PurgeSession(connection, "serial", "witness");
        cleanup.transactional = true;
        IOException primary = new IOException("Synthetic primary purge failure");
        assertThatThrownBy(() -> {
            try (cleanup) { throw primary; }
        }).isSameAs(primary);
        assertThat(primary.getSuppressed()).containsExactly(rollback);
        assertThat(rollback.getSuppressed()).containsExactly(invalidation);
        verify(connection).abort(any());
    }

    @Test
    void shouldRetainEveryCleanupError_whenPoolInvalidationAndAbortAlsoFail() throws Exception {
        var pool = mock(org.apache.commons.dbcp2.BasicDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("Synthetic rollback failure");
        IllegalStateException invalidation = new IllegalStateException("Synthetic invalidation failure");
        SQLException abort = new SQLException("Synthetic abort failure");
        doThrow(rollback).when(connection).rollback();
        doThrow(invalidation).when(pool).invalidateConnection(connection);
        doThrow(abort).when(connection).abort(any());
        var cleanup = new AuditLogPurgeService(pool).new PurgeSession(connection, "serial", "witness");
        cleanup.transactional = true;
        assertThatThrownBy(cleanup::close).isSameAs(rollback);
        assertThat(rollback.getSuppressed()).containsExactly(invalidation, abort);
    }

    @Test
    void shouldInvalidatePooledConnection_whenCleanupFailsAfterAnOtherwiseSuccessfulOperation() throws Exception {
        var pool = mock(org.apache.commons.dbcp2.BasicDataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("Synthetic rollback failure");
        doThrow(rollback).when(connection).rollback();
        var cleanup = new AuditLogPurgeService(pool).new PurgeSession(connection, "serial", "witness");
        cleanup.transactional = true;
        assertThatThrownBy(cleanup::close).isSameAs(rollback);
        verify(pool).invalidateConnection(connection);
        verify(connection, never()).abort(any());
    }

    @Test
    void shouldAbortUnpooledConnection_whenCleanupFails() throws Exception {
        var source = mock(javax.sql.DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException rollback = new SQLException("Synthetic rollback failure");
        doThrow(rollback).when(connection).rollback();
        var cleanup = new AuditLogPurgeService(source).new PurgeSession(connection, "serial", "witness");
        cleanup.transactional = true;
        assertThatThrownBy(cleanup::close).isSameAs(rollback);
        verify(connection).abort(any());
    }

    @Test
    void shouldPreservePrimaryFailureAndReportRetainedCredentials_whenCredentialRemovalFails() throws Exception {
        Path blocked = Files.createDirectory(temporary.resolve("connection.cnf"));
        Files.writeString(blocked.resolve("retained"), "Synthetic removal fault");
        IOException primary = new IOException("Synthetic primary purge failure");
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(AuditLogPurgeService.class)) {
            assertThatThrownBy(() -> {
                try (var cleanup = new AuditLogPurgeService.PrivateCredentials(blocked)) { throw primary; }
            }).isSameAs(primary);
            assertThat(primary.getSuppressed()).hasSize(1);
            assertThat(primary.getSuppressed()[0]).isInstanceOf(IOException.class)
                    .hasMessageContaining("temporary credentials could not be removed");
            assertThat(logs.messages()).anyMatch(message -> message.contains("temporary credentials could not be removed")
                    && message.contains(blocked.toString()));
        }
        assertThat(blocked.resolve("retained")).hasContent("Synthetic removal fault");
    }
}
