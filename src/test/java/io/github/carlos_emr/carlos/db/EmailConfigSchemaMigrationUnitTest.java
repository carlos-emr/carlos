/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;
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
                        "db/migration/common/V1.0.23.1__widen_email_config.sql").getInputStream()) {
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
    @Test
    void shouldAllowLaterDevelopMigrationsAfterReleaseWidening(@TempDir Path migrations) throws Exception {
        Files.writeString(migrations.resolve("V1__fixture.sql"),
                "CREATE TABLE emailConfig (id INTEGER PRIMARY KEY, configDetails VARCHAR(1000));");
        String widening;
        try (var input = new ClassPathResource(
                "db/migration/common/V1.0.23.1__widen_email_config.sql").getInputStream()) {
            widening = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Files.writeString(migrations.resolve("V1.0.23.1__widen_email_config.sql"), widening);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:email_upgrade_order;MODE=MySQL")) {
            Flyway flyway = Flyway.configure().dataSource("jdbc:h2:mem:email_upgrade_order;MODE=MySQL", "", "")
                    .locations("filesystem:" + migrations).ignoreMigrationPatterns(new String[0]).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            // Model versions reserved on develop, without importing its unrelated features.
            Files.writeString(migrations.resolve("V1.0.24__later_feature.sql"), "CREATE TABLE feature24 (id INT);");
            Files.writeString(migrations.resolve("V1.0.25__later_feature.sql"), "CREATE TABLE feature25 (id INT);");
            Files.writeString(migrations.resolve("V1.0.26__later_widening.sql"), widening);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
            flyway.validate();
        }
    }

}
