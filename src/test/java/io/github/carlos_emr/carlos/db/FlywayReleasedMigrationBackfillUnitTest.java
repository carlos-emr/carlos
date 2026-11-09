/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

@Tag("unit")
class FlywayReleasedMigrationBackfillUnitTest {
    private static final String FROZEN = "V1.0.59.8__widen_messagetbl_pdfattachment.sql";
    @TempDir Path migrations;

    private FluentConfiguration base(String highWater) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:backfill" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Files.writeString(
                migrations.resolve("V1__base.sql"),
                "CREATE TABLE retained(id INT PRIMARY KEY); INSERT INTO retained VALUES(7);\n");
        Files.writeString(
                migrations.resolve("V" + highWater + "__develop.sql"),
                "CREATE TABLE develop_marker(id INT);\n");
        var configuration =
                Flyway.configure()
                        .dataSource(source)
                        .locations("filesystem:" + migrations)
                        .ignoreMigrationPatterns(new String[0])
                        .failOnMissingLocations(true);
        configuration.load().migrate();
        return configuration;
    }

    private void released() throws Exception {
        Files.copy(Path.of("database/mysql/migration/common", FROZEN), migrations.resolve(FROZEN));
    }

    @Test
    void shouldRecognizeTheExactFrozenScript_withoutStampingItsHistory() throws Exception {
        var configuration = base("1.0.81");
        released();
        var eligible = FlywayReleasedMigrationBackfill.load(configuration);
        assertThat(eligible.getConfiguration().isOutOfOrder()).isTrue();
        assertThat(eligible.info().pending()).hasSize(1);
        assertThat(eligible.info().applied()).hasSize(2);
    }

    @Test
    void shouldRefuseAnEditedFrozenScript_beforeMigration() throws Exception {
        var configuration = base("1.0.81");
        released();
        Files.writeString(
                migrations.resolve(FROZEN),
                Files.readString(migrations.resolve(FROZEN)) + "-- changed\n");
        assertThatThrownBy(() -> FlywayReleasedMigrationBackfill.load(configuration))
                .hasMessage("FLYWAY_RELEASED_BACKFILL_SCRIPT_UNREVIEWED");
    }

    @Test
    void shouldRefuseAnUnreviewedHistoricalArrival() throws Exception {
        var configuration = base("1.0.81");
        Files.writeString(migrations.resolve("V1.0.80.99__unknown.sql"), "DROP TABLE retained;\n");
        assertThatThrownBy(() -> FlywayReleasedMigrationBackfill.load(configuration))
                .hasMessage("FLYWAY_RELEASED_BACKFILL_SCRIPT_UNREVIEWED");
    }

    @Test
    void shouldRefuseAnUnreviewedDevelopHistory() throws Exception {
        var configuration = base("1.0.80");
        released();
        assertThatThrownBy(() -> FlywayReleasedMigrationBackfill.load(configuration))
                .hasMessage("FLYWAY_RELEASED_BACKFILL_HISTORY_UNREVIEWED");
    }

    @Test
    void shouldRetainAppliedChecksumValidation_whenAnEligibleBackfillExists() throws Exception {
        var configuration = base("1.0.81");
        released();
        Files.writeString(migrations.resolve("V1__base.sql"), "DROP TABLE retained;\n");
        assertThatThrownBy(() -> FlywayReleasedMigrationBackfill.load(configuration).migrate())
                .hasMessageContaining("Validate failed");
    }
}
