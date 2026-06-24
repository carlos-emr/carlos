/*
 * This file is part of the CARLOS project.
 *
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Project repository: https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression coverage for the CSRFGuard properties file.
 *
 * @since 2026-05-31
 */
@Tag("unit")
@Tag("security")
@DisplayName("CSRFGuard configuration")
class CsrfGuardConfigurationRegressionTest {

    private static final Path CSRF_GUARD_PROPERTIES =
            Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties");
    private static final String PRNG_PROPERTY = "org.owasp.csrfguard.PRNG";
    private static final String PRNG_PROVIDER_PROPERTY = "org.owasp.csrfguard.PRNG.Provider";

    @Test
    @DisplayName("should use DRBG without provider constraint")
    void shouldUseDrbgWithoutProviderConstraint_whenCsrfGuardConfigLoaded() throws IOException {
        Properties properties = loadCsrfGuardProperties();

        assertThat(properties.getProperty(PRNG_PROPERTY)).isEqualTo("DRBG");
        assertThat(properties).doesNotContainKey(PRNG_PROVIDER_PROPERTY);
    }

    @Test
    @DisplayName("should resolve configured PRNG without provider constraint")
    void shouldResolveConfiguredPrng_withoutProviderConstraint() throws IOException {
        Properties properties = loadCsrfGuardProperties();

        assertThatCode(() -> SecureRandom.getInstance(properties.getProperty(PRNG_PROPERTY)))
                .doesNotThrowAnyException();
    }

    private static Properties loadCsrfGuardProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CSRF_GUARD_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
