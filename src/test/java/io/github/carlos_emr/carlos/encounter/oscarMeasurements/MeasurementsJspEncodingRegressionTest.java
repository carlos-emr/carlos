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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Measurements.jsp output encoding regressions")
@Tag("unit")
@Tag("clinical")
class MeasurementsJspEncodingRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 8;
    private static final Path JSP = resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "encounter", "oscarMeasurements", "Measurements.jsp"));

    @Test
    @DisplayName("should canonicalize the demographic identifier before rendering patient data")
    void shouldCanonicalizeDemographicIdentifier_beforeRenderingPatientData() throws Exception {
        String jsp = readJsp();

        assertThat(jsp)
                .contains("Integer demographicNo = ConversionUtils.fromIntString(demographicNoSource);")
                .contains("String demo = demographicNo > 0 ? demographicNo.toString() : \"\";")
                .contains("getNameAgeString(")
                .doesNotContain("String demo = request.getParameter(\"demographicNo\")")
                .doesNotContain("<oscar:nameage");
    }

    @Test
    @DisplayName("should encode patient and group labels for their output contexts")
    void shouldEncodePatientAndGroupLabels_forTheirOutputContexts() throws Exception {
        String jsp = readJsp();

        assertThat(jsp)
                .contains("${carlos:forJavaScript(patientNameAge)}")
                .contains("${carlos:forHtmlContent(patientNameAge)}")
                .contains("${carlos:forHtmlContent(groupName)}")
                .contains("<base href=\"${carlos:forHtmlAttribute(pageContext.request.contextPath)}/\">")
                .doesNotContain("request.getServerName()")
                .doesNotContain("<h4>${groupName}</h4>");
    }

    @Test
    @DisplayName("should encode measurement metadata wherever it enters generated markup")
    void shouldEncodeMeasurementMetadata_whenRenderingGeneratedMarkup() throws Exception {
        String jsp = readJsp();

        assertThat(jsp)
                .contains("${carlos:forHtmlAttribute(measurementType.typeDesc)}")
                .contains("${carlos:forHtmlContent(measurementType.typeDisplayName)}")
                .contains("${carlos:forHtmlAttribute(mInstrc.measuringInstrc)}")
                .contains("${carlos:forHtmlContent(mInstrc.measuringInstrc)}")
                .contains("${carlos:forJavaScriptAttribute(carlos:forUriComponent(measurementType.type))}")
                .doesNotContain("value=\"${measurementType.type}\"")
                .doesNotContain("&nbsp;${measurementType.lastComments}");
    }

    private static String readJsp() throws Exception {
        return Files.readString(JSP, StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
