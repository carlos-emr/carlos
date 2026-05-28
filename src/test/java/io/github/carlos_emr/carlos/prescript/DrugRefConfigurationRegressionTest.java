/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default DrugRef configuration points to the XML-RPC servlet endpoint.
 *
 * @since 2026-05-26
 */
@DisplayName("DrugRef configuration regressions")
@Tag("unit")
@Tag("regression")
class DrugRefConfigurationRegressionTest extends CarlosUnitTestBase {
    private static final Path CARLOS_PROPERTIES = Path.of("src/main/resources/carlos.properties");
    private static final String DRUGREF_ENDPOINT = "http://localhost:8080/drugref2/DrugrefService";
    private static final String DRUGREF_TEMPLATE =
            "drugref_url = http://yourDrugRefServerIP:portNumber/drugref2/DrugrefService";

    @Test
    @DisplayName("should include DrugrefService path in default DrugRef URL")
    void shouldContainServletPath_inDefaultDrugRefUrl() throws IOException {
        String carlosProperties = Files.readString(CARLOS_PROPERTIES);
        Properties properties = new Properties();
        properties.load(new StringReader(carlosProperties));

        assertThat(properties.getProperty("drugref_url"))
                .as("default drugref_url must point at the XML-RPC servlet endpoint")
                .isEqualTo(DRUGREF_ENDPOINT);
        assertThat(carlosProperties)
                .as("operator template should show the required servlet path")
                .contains(DRUGREF_TEMPLATE);
    }
}
