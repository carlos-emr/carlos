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
package io.github.carlos_emr.carlos.flowsheet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Flowsheet editor JSP regressions")
@Tag("unit")
@Tag("fast")
@Tag("security")
class FlowSheetEditorJspRegressionTest {

    private static final Path EDIT_FLOWSHEET_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/encounter/oscarMeasurements/adminFlowsheet/EditFlowsheet.jsp");

    @Test
    @DisplayName("dynamic customization forms should copy the CSRF token before submission")
    void dynamicCustomizationFormsShouldCopyCsrfTokenBeforeSubmission() throws IOException {
        String jsp = Files.readString(EDIT_FLOWSHEET_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("input[name=\"CSRF-TOKEN\"]")
                .contains("csrfInput.name = 'CSRF-TOKEN'")
                .contains("csrfInput.value = csrfElement.value")
                .contains("appendCsrfToken(form);")
                .containsSubsequence("appendCsrfToken(form);", "form.submit();");
    }
}
