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
package io.github.carlos_emr.carlos.prescript;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default DrugRef configuration points to the XML-RPC servlet endpoint.
 *
 * @since 2026-05-28
 */
@DisplayName("DrugRef configuration regressions")
@Tag("unit")
@Tag("regression")
class DrugRefConfigurationRegressionTest extends CarlosUnitTestBase {
    private static final String CARLOS_PROPERTIES_RESOURCE = "carlos.properties";
    private static final String DRUGREF_SERVICE_PATH = "/drugref2/DrugrefService";
    private static final String DRUGREF_ENDPOINT = "http://localhost:8080" + DRUGREF_SERVICE_PATH;

    @Test
    @DisplayName("should include XML-RPC servlet path in default DrugRef URL")
    void shouldIncludeXmlRpcServletPath_inDefaultDrugRefUrl() throws IOException {
        String carlosProperties = readCarlosPropertiesContent();
        Properties properties = loadCarlosProperties();

        assertThat(properties.getProperty("drugref_url"))
                .as("default drugref_url must point at the XML-RPC servlet endpoint")
                .isEqualTo(DRUGREF_ENDPOINT);
        assertThat(carlosProperties)
                .as("operator template should show the required servlet path")
                .contains("yourDrugRefServerIP:portNumber" + DRUGREF_SERVICE_PATH);
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
