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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC bill form's diagnosis reference list (DxReference.jsp, loaded into every BC bill form) writes each earlier
 * diagnosis into an onClick handler and its description into a title attribute; both values are encoded for their
 * context.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC dx reference list encoding")
class DxReferenceJspEncodingRegressionTest {

    private static final Path DX_REFERENCE_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/DxReference.jsp");

    @Test
    @DisplayName("should encode the diagnosis code and description in the option's attributes")
    void shouldEncodeDiagnosisAttributes_inDxReferenceOptions() throws IOException {
        String jsp = Files.readString(DX_REFERENCE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("quickPickDiagnostic('${carlos:forJavaScriptAttribute(dx.dx)}')")
                .contains("title=\"${carlos:forHtmlAttribute(dx.desc)}\"")
                .doesNotContainPattern("\\$\\{\\s*dx\\.dx\\s*}")
                .doesNotContainPattern("\\$\\{\\s*dx\\.desc\\s*}");
    }
}
