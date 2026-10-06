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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("fast")
@DisplayName("prevention/index.jsp patient lookups")
class PreventionIndexJspLookupsUnitTest {

    private static final Path INDEX_JSP = Path.of("src/main/webapp/WEB-INF/jsp/prevention/index.jsp");

    @Test
    @DisplayName("should read every prevention type through the page's single patient lookup")
    void shouldUsePageData_forEveryPreventionType() throws Exception {
        String jsp = Files.readString(INDEX_JSP);

        assertThat(jsp)
                .contains("new PreventionPageData(loggedInInfo, demographic_no)")
                .doesNotContain("PreventionData.getPreventionData(")
                .doesNotContain("PreventionData.getDemographicDateOfBirth(")
                .doesNotContain("pdc.display(loggedInInfo")
                .doesNotContain("demoData.getDemographic(");
    }
}
