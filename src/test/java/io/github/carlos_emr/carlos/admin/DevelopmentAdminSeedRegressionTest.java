/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License;
 * you can redistribute it and/or modify it under the terms of the GPL.
 */
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Development Administration seed regressions")
@Tag("unit")
@Tag("admin")
class DevelopmentAdminSeedRegressionTest {

    private static final Path ADMIN_SEED = Path.of(
            ".devcontainer", "db", "scripts", "admin_test_data.sql");

    @Test
    @DisplayName("should load admin fixtures after the eForm migrations")
    void shouldLoadAdminFixtures_afterEformMigrations() throws IOException {
        String dockerfile = Files.readString(
                Path.of(".devcontainer", "db", "Dockerfile"), StandardCharsets.UTF_8);
        String populate = Files.readString(
                Path.of(".devcontainer", "db", "scripts", "populate_db.sh"), StandardCharsets.UTF_8);

        assertThat(dockerfile)
                .contains("COPY ./.devcontainer/db/scripts/admin_test_data.sql /scripts/admin_test_data.sql");
        assertThat(populate)
                .contains("$SQL oscar < /scripts/admin_test_data.sql")
                .satisfies(script -> assertThat(script.indexOf("/scripts/admin_test_data.sql"))
                        .isGreaterThan(script.indexOf("update-2026-03-22-rtl-2026.3.0-modernize.sql")));
    }

    @Test
    @DisplayName("should cover the data-backed Administration screens that are empty in the base dump")
    void shouldCoverEmptyAdministrationScreens() throws IOException {
        String seed = Files.readString(ADMIN_SEED, StandardCharsets.UTF_8);

        assertThat(seed).contains(
                "'locktest'",
                "INSERT INTO cssStyles",
                "INSERT INTO incomingLabRules",
                "INSERT INTO incomingLabRulesType",
                "INSERT INTO eform_groups",
                "INSERT INTO eform_data",
                "INSERT INTO reportByExamplesFavorite",
                "INSERT INTO demographicQueryFavourites",
                "INSERT INTO appointmentType",
                "INSERT INTO PreventionsLotNrs");
    }

    @Test
    @DisplayName("should seed current and deleted patient-independent eForms")
    void shouldSeedPatientIndependentEforms_forBothAdministrationViews() throws IOException {
        String seed = Files.readString(ADMIN_SEED, StandardCharsets.UTF_8);

        assertThat(seed).contains(
                "'Local Test - Independent Checklist'",
                "'Local Test - Shared Operations Note'",
                "'Local Test - Monthly Safety Review'",
                "'Local Test - Quarterly Operations Review'",
                "'Local Test - Archived Safety Draft'",
                "patient_independent");
    }

    @Test
    @DisplayName("should keep local admin fixtures repeatable and visibly synthetic")
    void shouldKeepFixtures_repeatableAndSynthetic() throws IOException {
        String seed = Files.readString(ADMIN_SEED, StandardCharsets.UTF_8);

        assertThat(seed)
                .contains("WHERE NOT EXISTS")
                .contains("Local Test -")
                .contains("LOCAL-FLU-2026-A")
                .contains("LOCAL-COVID-2026-B")
                .contains("LOCAL-DTAP-ARCHIVED")
                .doesNotContain("INSERT INTO demographic ");
    }
}
