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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

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
    @Test void shouldHashNativeNumbersDatesUnicodeAndBinary_withoutDriverCharacterCoercion() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE demographic (demographic_no INT PRIMARY KEY, n INT, amount DECIMAL(10,3), event_time DATETIME(6), content MEDIUMTEXT CHARACTER SET utf8mb4, bytes MEDIUMBLOB, absent VARCHAR(10))");
            try (var insert = connection.prepareStatement("INSERT INTO demographic VALUES (1,42,12.340,'2026-01-02 03:04:05.123456',?,?,NULL)")) {
                insert.setString(1, "Complete Unicode 😀\n".repeat(10000)); insert.setBytes(2, new byte[]{0, 1, (byte) 255}); insert.executeUpdate();
            }
            String original = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(original).matches("[0-9a-f]{64}");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).isEqualTo(original);
            statement.executeUpdate("UPDATE demographic SET absent='' WHERE demographic_no=1");
            String empty = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(empty).isNotEqualTo(original);
            statement.executeUpdate("UPDATE demographic SET event_time='2026-01-02 03:04:05.123457' WHERE demographic_no=1");
            String fraction = CdsTargetReceiptService.rowDigest(connection, "demographic", "1");
            assertThat(fraction).isNotEqualTo(empty);
            statement.executeUpdate("UPDATE demographic SET bytes=X'0001FE' WHERE demographic_no=1");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).isNotEqualTo(fraction);
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "2")).isNull();
            assertThatThrownBy(() -> CdsTargetReceiptService.rowDigest(connection, "unknown_table", "1"))
                    .isInstanceOf(CdsImportException.class).hasMessageContaining("TARGET_REFERENCE_INVALID");
        }
    }

    @Test void shouldExcludeOnlyOperationalFileVerificationEpoch_whenImmutableIdentityStaysEqual() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE cds_import_file (id VARCHAR(36) PRIMARY KEY, digest CHAR(64), byte_length BIGINT, verified_epoch BIGINT)");
            statement.executeUpdate("INSERT INTO cds_import_file VALUES ('fixture',REPEAT('a',64),123,1)");
            String original = CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture");
            statement.executeUpdate("UPDATE cds_import_file SET verified_epoch=2 WHERE id='fixture'");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture")).isEqualTo(original);
            statement.executeUpdate("UPDATE cds_import_file SET byte_length=124 WHERE id='fixture'");
            assertThat(CdsTargetReceiptService.rowDigest(connection, "cds_import_file", "fixture")).isNotEqualTo(original);
        }
    }

    private static Connection connection() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(selector != null && !selector.isBlank(), "Explicit protected synthetic MariaDB tier required");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of(selector))) { properties.load(input); }
        String url = properties.getProperty("jdbcUrl", "").replace("jdbc:mariadb:", "jdbc:mysql:");
        if (!url.matches("jdbc:mysql://[^/]+/(?:carlos_cds_test|cds_full_test)(?:\\?.*)?")) throw new IllegalArgumentException("Disposable CDS database required");
        Connection connection = DriverManager.getConnection(url, properties.getProperty("jdbcUser"), properties.getProperty("jdbcPassword"));
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !java.util.Set.of("carlos_cds_test", "cds_full_test").contains(result.getString(1))) {
                connection.close(); throw new IllegalArgumentException("Disposable CDS database required");
            }
        }
        return connection;
    }
}
