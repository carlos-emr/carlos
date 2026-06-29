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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the demo consultation service-specialist seed update used by local UI tests.
 *
 * @since 2026-06-01
 */
@DisplayName("Consultation specialist demo seed regressions")
@Tag("unit")
@Tag("database")
class ConsultationSpecialistDemoSeedRegressionTest {

    private static final Path MIGRATION = Path.of("database", "mysql", "updates",
            "update-2026-06-01-varied-consult-specialist-demo-data.sql");
    private static final Path DEVELOPMENT_SEED = Path.of(".devcontainer", "db", "scripts",
            "development.sql");

    @Test
    @DisplayName("migration should resolve demo services by description")
    void shouldResolveServicesByDescription_whenMigrationRuns() throws IOException {
        String migrationSql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(migrationSql).contains(
                "SET @service_acarology := (",
                "WHERE serviceDesc = 'Acarology'",
                "SET @service_cardiology := (",
                "WHERE serviceDesc = 'Cardiology'",
                "SET @service_cetology := (",
                "WHERE serviceDesc = 'Cetology'",
                "SET @service_embryology := (",
                "WHERE serviceDesc = 'Embryology'",
                "SET @service_geology := (",
                "WHERE serviceDesc = 'Geology'",
                "SET @service_radiology := (",
                "WHERE serviceDesc = 'Radiology'")
                .doesNotContain(
                "serviceId IN (1, 2, 3, 4, 5, 6)",
                "SELECT 3 AS serviceId");
    }

    @Test
    @DisplayName("migration should match mock specialist names in development seed")
    void shouldMatchMockSpecialistNames_whenGuardingMigration() throws IOException {
        String migrationSql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String developmentSeedSql = Files.readString(DEVELOPMENT_SEED, StandardCharsets.UTF_8);

        assertThat(developmentSeedSql).contains(
                "(1,'John','C'",
                "(2,'Danny','L'",
                "(3,'Test','1'");
        assertThat(migrationSql).contains(
                "WHERE fName = 'John' AND lName = 'C'",
                "WHERE fName = 'Danny' AND lName = 'L'",
                "WHERE fName = 'Test' AND lName = '1'")
                .doesNotContain("WHERE fName = '1' AND lName = 'Test'");
    }

    @Test
    @DisplayName("migration should define distinct demo service specialist mappings")
    void shouldDefineDistinctMappings_whenMigrationRuns() throws IOException {
        String migrationSql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(migrationSql).contains(
                "SELECT @service_acarology AS serviceId, @spec_john_c AS specId",
                "SELECT @service_cardiology, @spec_danny_l",
                "SELECT @service_cetology, @spec_john_c",
                "SELECT @service_cetology, @spec_danny_l",
                "SELECT @service_embryology, @spec_test_1",
                "SELECT @service_geology, @spec_john_c",
                "SELECT @service_radiology, @spec_danny_l",
                "SELECT @service_radiology, @spec_test_1");
    }
}
