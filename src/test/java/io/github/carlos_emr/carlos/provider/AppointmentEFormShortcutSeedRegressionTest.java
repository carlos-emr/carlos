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
package io.github.carlos_emr.carlos.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the development provider preferences against stale appointment eForm shortcuts.
 */
@DisplayName("appointment eForm shortcut seed regressions")
@Tag("unit")
@Tag("provider")
class AppointmentEFormShortcutSeedRegressionTest {

    private static final Path DEVELOPMENT_SEED =
            Path.of(".devcontainer", "db", "scripts", "development.sql");

    @Test
    @DisplayName("should omit stale Ocean shortcuts when development data is seeded")
    void shouldOmitStaleOceanShortcuts_whenDevelopmentDataSeeded() throws IOException {
        String seedSql = Files.readString(DEVELOPMENT_SEED, StandardCharsets.UTF_8);

        assertThat(seedSql)
                .doesNotContain("('5',46,'Ocean')")
                .doesNotContain("('999998',52,'Ocean')");
    }
}
