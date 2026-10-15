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
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Guards the Disease Registry add screen's ICD-9 decimal-point hint (#3759). */
@DisplayName("Disease Registry add screen JSP regression tests")
@Tag("unit")
class DxResearchJspRegressionTest {

    private static final Path DX_RESEARCH_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/oscarResearch/oscarDxResearch/dxResearch.jsp");
    private static final String GUARD = "<c:if test=\"${icd9Configured}\">";
    private static final String DESCRIBED_BY = "aria-describedby=\"icd9NoDecimalHint\"";

    @Test
    @DisplayName("should show the ICD-9 decimal-point hint only where ICD-9 is a configured coding system")
    void shouldShowIcd9Hint_onlyWhenIcd9IsConfigured() throws IOException {
        String jsp = Files.readString(DX_RESEARCH_JSP, StandardCharsets.UTF_8);

        // The flag comes from the configured coding systems, the same list the coding-system select shows.
        assertThat(jsp)
                .contains("<c:forEach var=\"configuredSystem\" items=\"${codingSystem.codingSystems}\">")
                .contains("<c:if test=\"${configuredSystem.trim() eq 'icd9'}\"><c:set var=\"icd9Configured\" value=\"${true}\"/></c:if>");

        int hint = jsp.indexOf("id=\"icd9NoDecimalHint\"");
        int guard = jsp.lastIndexOf(GUARD + "\n", hint);
        assertThat(hint).as("the hint is on the page").isNotNegative();
        assertThat(guard).as("the hint row sits inside the ICD-9 guard").isNotNegative();
        assertThat(jsp.indexOf("</c:if>", guard)).as("the guard closes after the hint").isGreaterThan(hint);

        // Every code box points at the hint only when the hint is rendered.
        long describedBy = Pattern.compile(Pattern.quote(DESCRIBED_BY)).matcher(jsp).results().count();
        long guardedDescribedBy = Pattern.compile(Pattern.quote(GUARD + DESCRIBED_BY + "</c:if>")).matcher(jsp).results().count();
        assertThat(describedBy).isEqualTo(5);
        assertThat(guardedDescribedBy).isEqualTo(describedBy);
    }
}
