/**
 * Copyright (c) 2026 CARLOS Contributors.
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
package io.github.carlos_emr.carlos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the Cortico/Juno integration configuration placeholders in
 * {@code carlos.properties}.
 *
 * <p>The keys are placeholders for future adapter workflows: no CARLOS code
 * reads them yet. This test pins two invariants the placeholders depend on:
 * the keys are declared (so future adapter work has a documented home), and
 * every key defaults to empty (so no clinic-specific value or PHI is committed
 * into the default config and existing behavior stays unchanged).</p>
 *
 * @since 2026-06-25
 */
@DisplayName("Cortico integration config placeholders")
@Tag("unit")
@Tag("read")
class CorticoIntegrationConfigPlaceholdersTest extends CarlosUnitTestBase {

    /**
     * Classpath resource name for the runtime defaults properties.
     * Loaded via the test's ClassLoader rather than a filesystem path to
     * keep this test independent of the working directory and build layout.
     */
    private static final String CARLOS_PROPERTIES_RESOURCE = "carlos.properties";

    private static final List<String> CORTICO_PLACEHOLDER_KEYS = List.of(
            "integration.cortico.appointment.status.confirmed",
            "integration.cortico.appointment.status.cancelled",
            "integration.cortico.appointment.status.arrived",
            "integration.cortico.appointment.reminder.email_note_marker",
            "integration.cortico.appointment.reminder.sms_note_marker",
            "integration.cortico.default_location",
            "integration.cortico.default_provider",
            "integration.cortico.default_appointment_type",
            "integration.cortico.demographic.search.phn_field",
            "integration.cortico.document.default_type");

    @Test
    @DisplayName("should declare Cortico integration placeholder keys in carlos properties")
    void shouldDeclareCorticoPlaceholderKeys_inCarlosProperties() throws IOException {
        String content = readCarlosPropertiesContent();
        Properties properties = loadCarlosProperties();

        assertThat(content).contains("# CORTICO/JUNO INTEGRATION PLACEHOLDERS");
        assertThat(properties.stringPropertyNames()).containsAll(CORTICO_PLACEHOLDER_KEYS);
    }

    @Test
    @DisplayName("should default every Cortico placeholder to empty so no clinic value or PHI ships")
    void shouldDefaultEveryCorticoPlaceholderToEmpty_forSafeDefaults() throws IOException {
        Properties properties = loadCarlosProperties();

        assertThat(CORTICO_PLACEHOLDER_KEYS)
                .allSatisfy(key -> assertThat(properties.getProperty(key))
                        .as("placeholder %s must default to empty", key)
                        .isEmpty());
    }

    private String readCarlosPropertiesContent() throws IOException {
        try (InputStream inputStream = openCarlosProperties()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Properties loadCarlosProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(openCarlosProperties(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private InputStream openCarlosProperties() {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CARLOS_PROPERTIES_RESOURCE);
        if (inputStream == null) {
            throw new IllegalStateException("Missing classpath resource: " + CARLOS_PROPERTIES_RESOURCE);
        }
        return inputStream;
    }
}
