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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessMeasurementsSubmission.jsp regressions")
@Tag("unit")
@Tag("clinical")
class ProcessMeasurementsSubmissionJspRegressionTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "encounter", "oscarMeasurements", "ProcessMeasurementsSubmission.jsp");

    @Test
    @DisplayName("should render action errors with JSTL iteration and encoded EL values")
    void shouldRenderActionErrors_withJstlAndEncodedValues() throws Exception {
        String jsp = Files.readString(JSP);

        assertThat(jsp)
                .contains("<%@ taglib prefix=\"c\" uri=\"jakarta.tags.core\" %>")
                .contains("<c:if test=\"${not empty actionErrors}\">")
                .contains("<c:forEach items=\"${actionErrors}\" var=\"error\">")
                .contains("<li><carlos:encode value=\"${error}\"/></li>")
                .doesNotContain("<%= error %>")
                .doesNotContain("for (String error : actionErrors)");
    }

    @Test
    @DisplayName("should only auto-close when there are no action errors")
    void shouldOnlyAutoClose_whenActionErrorsAreEmpty() throws Exception {
        String jsp = Files.readString(JSP);

        assertThat(jsp)
                .contains("<body<c:if test=\"${empty actionErrors}\"> onload=\"closeWin();\"</c:if>>")
                .doesNotContain("<body onload=\"closeWin();\">");
    }
}
