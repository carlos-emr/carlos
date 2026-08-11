/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.measurements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the production and development default measurement-group seeds. */
@DisplayName("default measurement group seed regressions")
@Tag("unit")
@Tag("measurement")
class DefaultMeasurementGroupSeedRegressionTest {

    private static final Path MIGRATION = Path.of("database", "mysql", "migration", "common",
            "V1.0.7__seed_default_measurement_groups.sql");
    private static final Path DEVELOPMENT_SEED =
            Path.of(".devcontainer", "db", "scripts", "development.sql");

    private static final List<String> REQUIRED_MAPPINGS = List.of(
            "('Vitals', 'BP')",
            "('Vitals', 'Heart Rate')",
            "('Vitals', 'RR')",
            "('Vitals', 'Temp')",
            "('Vitals', 'Oxygen Saturation')",
            "('Anthropometrics', 'HT')",
            "('Anthropometrics', 'WT')",
            "('Anthropometrics', 'Body Mass Index')",
            "('Anthropometrics', 'Waist')",
            "('Anthropometrics', 'Head circumference')",
            "('Diabetes Review', 'A1C')",
            "('Diabetes Review', 'Blood Glucose')",
            "('Diabetes Review', 'FBS')",
            "('Diabetes Review', 'Alb creat ratio')",
            "('Diabetes Review', 'EGFR')",
            "('Diabetes Review', 'LDL')",
            "('Diabetes Review', 'HDL')",
            "('Diabetes Review', 'Triglycerides')",
            "('Diabetes Review', 'TC/HDL')",
            "('Respiratory Review', 'Oxygen Saturation')",
            "('Respiratory Review', 'RR')",
            "('Respiratory Review', 'PEFR value')",
            "('Respiratory Review', 'Forced Expiratory Volume 1 Second')",
            "('Respiratory Review', 'Spirometry')",
            "('Respiratory Review', 'Smoking Status')",
            "('Mental Health Scores', 'PHQ9 Score')",
            "('Mental Health Scores', 'GAD7 Anxiety Score')");

    @Test
    @DisplayName("should provide cross-province defaults in a common forward migration")
    void shouldProvideDefaults_whenFlywayMigrationRuns() throws IOException {
        assertThat(MIGRATION).exists();

        String migrationSql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(migrationSql).contains(REQUIRED_MAPPINGS.toArray(String[]::new));
        assertThat(migrationSql)
                .contains("INNER JOIN `measurementType`")
                .contains("mg.`id` IS NULL")
                .contains("INSERT INTO `measurementGroupStyle` (`groupName`, `cssID`)")
                .contains("mgs.`groupID` IS NULL")
                .contains("mgs.`groupName` = 'Test'");
    }

    @Test
    @DisplayName("should replace the empty Test placeholder in development data")
    void shouldProvideDefaults_whenDevelopmentDataSeeded() throws IOException {
        String developmentSql = Files.readString(DEVELOPMENT_SEED, StandardCharsets.UTF_8);

        assertThat(developmentSql).contains(REQUIRED_MAPPINGS.toArray(String[]::new));
        assertThat(developmentSql)
                .doesNotContain("INSERT INTO `measurementGroupStyle` VALUES (1,'Test',0)")
                .contains("('Vitals',0)", "('Mental Health Scores',0)");
    }
}
