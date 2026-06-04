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
 * Guards the documented startup smoke-test defaults in {@code carlos.properties}.
 *
 * @since 2026-05-20
 */
@DisplayName("CARLOS runtime property defaults")
@Tag("unit")
@Tag("read")
class CarlosPropertiesRuntimeDefaultsTest extends CarlosUnitTestBase {

    /**
     * Classpath resource name for the runtime defaults properties.
     * Loaded via the test's ClassLoader rather than a filesystem path to
     * keep this test independent of the working directory and build layout.
     */
    private static final String CARLOS_PROPERTIES_RESOURCE = "carlos.properties";

    private static final List<String> SMOKE_TEST_DEFAULT_KEYS = List.of(
            "FAX_INCOMING_DIR",
            "log.purge.outputdir",
            "log.purge.daysfromnowtopurge",
            "PREVENTION_FILE",
            "WAF_TRUSTED_PROXY_IPS",
            "WAF_TRUSTED_PROXY_CIDRS",
            "INACTIVITY_LIMIT_MINS",
            "sec.token.manager",
            "caisi",
            "useProgramLocation",
            "APPT_SHOW_FULL_NAME",
            "display_timeline",
            "NOT_FOR_CAISI",
            "WORKFLOW",
            "view.appointmentdaysheetbutton");

    private static final List<String> STARTUP_DERIVED_DOCUMENT_PATH_KEYS = List.of(
            "DOCUMENT_CACHE_DIR",
            "TMP_DIR",
            "OMD_hrm",
            "OMD_downloads");

    @Test
    @DisplayName("should declare smoke-test runtime defaults in carlos properties")
    void shouldDeclareSmokeTestRuntimeDefaults_inCarlosProperties() throws IOException {
        String content = readCarlosPropertiesContent();
        Properties properties = loadCarlosProperties();

        assertThat(content).contains("# Smoke-test runtime defaults");
        assertThat(properties.stringPropertyNames()).containsAll(SMOKE_TEST_DEFAULT_KEYS);
        assertThat(properties.stringPropertyNames())
                .doesNotContainAnyElementsOf(STARTUP_DERIVED_DOCUMENT_PATH_KEYS);
        assertThat(properties.getProperty("log.purge.outputdir"))
                .isEqualTo("/var/lib/OscarDocument/carlos/document/");
        assertThat(properties.getProperty("FAX_INCOMING_DIR")).isEmpty();
        assertThat(properties.getProperty("log.purge.daysfromnowtopurge")).isEmpty();
        assertThat(properties.getProperty("PREVENTION_FILE")).isEmpty();
        assertThat(properties.getProperty("WAF_TRUSTED_PROXY_IPS")).isEmpty();
        assertThat(properties.getProperty("WAF_TRUSTED_PROXY_CIDRS")).isEmpty();
        assertThat(properties.getProperty("INACTIVITY_LIMIT_MINS")).isEqualTo("60");
        assertThat(properties.getProperty("sec.token.manager")).isEmpty();
        assertThat(properties.getProperty("caisi")).isEqualTo("off");
        assertThat(properties.getProperty("useProgramLocation")).isEqualTo("false");
        assertThat(properties.getProperty("APPT_SHOW_FULL_NAME")).isEqualTo("false");
        assertThat(properties.getProperty("display_timeline")).isEqualTo("true");
        assertThat(properties.getProperty("NOT_FOR_CAISI")).isEqualTo("no");
        assertThat(properties.getProperty("WORKFLOW")).isEqualTo("no");
        assertThat(properties.getProperty("view.appointmentdaysheetbutton")).isEqualTo("off");
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
