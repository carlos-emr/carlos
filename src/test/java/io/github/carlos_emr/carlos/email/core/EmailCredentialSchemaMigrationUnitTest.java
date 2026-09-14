/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
@Tag("update")
class EmailCredentialSchemaMigrationUnitTest {
    @Test
    @DisplayName("should store expanded ciphertext after widening the legacy configuration column")
    void shouldStoreExpandedCredentials_whenWideningMigrationApplied() throws Exception {
        String originalKey = EncryptionKeyTestSupport.seedFreshKey();
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_config_width;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE emailConfig (id INTEGER PRIMARY KEY, configDetails VARCHAR(1000))");
            statement.execute("INSERT INTO emailConfig VALUES (1, NULL)");
            statement.execute("INSERT INTO emailConfig VALUES (2, NULL)");
            String password = "fixture-".repeat(112);
            String original = "{\"password\":\"" + password + "\"}";
            String encrypted = EmailConfigSecrets.encryptSecrets(original);
            assertThat(original.length()).isLessThanOrEqualTo(1000);
            assertThat(encrypted.length()).isGreaterThan(1000);

            try (var update = connection.prepareStatement("UPDATE emailConfig SET configDetails = ? WHERE id = 1")) {
                update.setString(1, original);
                assertThat(update.executeUpdate()).isEqualTo(1);
                update.setString(1, encrypted);
                assertThatThrownBy(update::executeUpdate).isInstanceOf(SQLException.class);

                String migration;
                try (var input = new ClassPathResource(
                        "db/migration/common/V1.0.26__widen_email_config_for_encrypted_credentials.sql")
                        .getInputStream()) {
                    migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                statement.execute(migration);
                statement.execute(migration); // The forward migration can safely be reapplied.
                try (var rows = statement.executeQuery("SELECT configDetails FROM emailConfig ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(original);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isNull();
                }
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT configDetails FROM emailConfig WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                String stored = rows.getString(1);
                assertThat(stored).isEqualTo(encrypted);
                String storedPassword = new ObjectMapper().readTree(stored).get("password").asText();
                assertThat(EmailConfigSecrets.decryptSecret(storedPassword)).isEqualTo(password);
            }
        } finally {
            EncryptionKeyTestSupport.restoreKey(originalKey);
        }
    }
}
