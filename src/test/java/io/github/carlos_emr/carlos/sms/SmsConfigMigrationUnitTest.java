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
package io.github.carlos_emr.carlos.sms;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the SMS settings migration verbatim against H2 in MySQL mode. The H2 test schema is built from
 * the entity mappings and never runs the Flyway files, so this is what checks the file itself.
 */
@Tag("unit")
@Tag("dao")
class SmsConfigMigrationUnitTest {
    private static final Path MIGRATION =
            Path.of("database", "mysql", "migration", "common", "V1.0.34__add_sms_config.sql");

    @Test
    @DisplayName("applied twice, creates sms_config with the columns the entity maps")
    void shouldCreateSettingsTable_whenAppliedTwice() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sms_config_migration;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            applyMigration(connection);
            applyMigration(connection);

            statement.execute("""
                    INSERT INTO sms_config (provider_type, enabled, scheduler_enabled, sender_number,
                                            webhook_secret, credentials, updated_at, updated_by)
                    VALUES ('STUB', 1, 0, '+14165551212', '{ENC}abc', '{}', CURRENT_TIMESTAMP, '999998')""");
            try (ResultSet row = statement.executeQuery(
                    "SELECT provider_type, enabled, scheduler_enabled, sender_number FROM sms_config")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("provider_type")).isEqualTo("STUB");
                assertThat(row.getBoolean("enabled")).isTrue();
                assertThat(row.getBoolean("scheduler_enabled")).isFalse();
                assertThat(row.getString("sender_number")).isEqualTo("+14165551212");
            }
        }
    }

    @Test
    @DisplayName("creates the table only; it seeds no row, so an install keeps its property settings until saved")
    void shouldSeedNoRow_soPropertiesStillApply() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:sms_config_empty;MODE=MySQL");
             Statement statement = connection.createStatement()) {
            applyMigration(connection);

            try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM sms_config")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isZero();
            }
        }
    }

    private static void applyMigration(Connection connection) throws Exception {
        try (Reader reader = Files.newBufferedReader(MIGRATION, StandardCharsets.UTF_8)) {
            RunScript.execute(connection, reader);
        }
    }
}
