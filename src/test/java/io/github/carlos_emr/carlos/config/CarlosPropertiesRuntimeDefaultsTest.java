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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

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
class CarlosPropertiesRuntimeDefaultsTest {

    private static final Path CARLOS_PROPERTIES =
            Path.of("src", "main", "resources", "carlos.properties");

    private static final List<String> SMOKE_TEST_DEFAULT_KEYS = List.of(
            "DOCUMENT_CACHE_DIR",
            "TMP_DIR",
            "OMD_hrm",
            "OMD_downloads",
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

    @Test
    @DisplayName("should declare smoke-test runtime defaults in carlos properties")
    void shouldDeclareSmokeTestRuntimeDefaults_inCarlosProperties() throws IOException {
        String content = Files.readString(CARLOS_PROPERTIES, StandardCharsets.UTF_8);
        Properties properties = loadCarlosProperties();

        assertThat(content).contains("# Smoke-test runtime defaults");
        assertThat(properties.stringPropertyNames()).containsAll(SMOKE_TEST_DEFAULT_KEYS);
        assertThat(properties.getProperty("DOCUMENT_CACHE_DIR"))
                .isEqualTo("/var/lib/OscarDocument/carlos/document_cache/");
        assertThat(properties.getProperty("TMP_DIR"))
                .isEqualTo("/var/lib/OscarDocument/carlos/export/");
        assertThat(properties.getProperty("OMD_hrm"))
                .isEqualTo("/var/lib/OscarDocument/carlos/hrm/");
        assertThat(properties.getProperty("OMD_downloads"))
                .isEqualTo("/var/lib/OscarDocument/carlos/hrm/sftp_downloads/");
        assertThat(properties.getProperty("log.purge.outputdir"))
                .isEqualTo("/var/lib/OscarDocument/carlos/document/");
        assertThat(properties.getProperty("INACTIVITY_LIMIT_MINS")).isEqualTo("60");
        assertThat(properties.getProperty("caisi")).isEqualTo("off");
        assertThat(properties.getProperty("useProgramLocation")).isEqualTo("false");
        assertThat(properties.getProperty("APPT_SHOW_FULL_NAME")).isEqualTo("false");
        assertThat(properties.getProperty("display_timeline")).isEqualTo("true");
        assertThat(properties.getProperty("NOT_FOR_CAISI")).isEqualTo("no");
        assertThat(properties.getProperty("WORKFLOW")).isEqualTo("no");
        assertThat(properties.getProperty("view.appointmentdaysheetbutton")).isEqualTo("off");
    }

    private Properties loadCarlosProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CARLOS_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
