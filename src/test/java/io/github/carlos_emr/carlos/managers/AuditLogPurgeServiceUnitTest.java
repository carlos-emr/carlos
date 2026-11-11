/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.HostAddress;
import static org.assertj.core.api.Assertions.*;

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
}
