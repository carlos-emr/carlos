/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Migration regression; scripts/email-config-schema-checks.js verifies MariaDB. */
@Tag("unit")
class EmailConfigSchemaMigrationUnitTest {
    @Test
    void shouldWidenLegacyColumnWithoutLosingExistingOrNullValues() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_schema;MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE emailConfig (id INTEGER PRIMARY KEY, configDetails VARCHAR(1000))");
            statement.execute("INSERT INTO emailConfig VALUES (1, 'existing'), (2, NULL), (3, NULL)");
            String json = "{\"password\":\"" + "synthetic-é".repeat(1500) + "\"}";
            try (var update = connection.prepareStatement("UPDATE emailConfig SET configDetails=? WHERE id=3")) {
                update.setString(1, json);
                assertThatThrownBy(update::executeUpdate).isInstanceOf(SQLException.class);
                String migration;
                try (var input = new ClassPathResource(
                        "db/migration/common/V1.0.26__widen_email_config_for_encrypted_credentials.sql").getInputStream()) {
                    migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                statement.execute(migration);
                statement.execute(migration);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (var rows = statement.executeQuery("SELECT configDetails FROM emailConfig ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("existing");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(json);
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
