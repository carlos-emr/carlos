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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A measurement group's decision support is added and removed by
 * {@code encounter/oscarMeasurements/MeasurementGroupDecisionSupport}: the Add and Remove pages post to it, and the
 * complete page, which the read-only view gate also serves, only shows the result.
 *
 * @since 2026-10-20
 */
@DisplayName("Measurement group decision support pages")
@Tag("unit")
@Tag("clinical")
class MeasurementGroupDecisionSupportPagesRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 8;
    private static final Path DIR = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "encounter", "oscarMeasurements");
    private static final String ROUTE = "encounter/oscarMeasurements/MeasurementGroupDecisionSupport";

    @Test
    @DisplayName("should post the Add and Remove forms to the decision support action")
    void shouldPostAddAndRemoveForms_toTheDecisionSupportAction() throws Exception {
        for (String page : new String[] {"MeasurementGroupDSadd.jsp", "MeasurementGroupDSremove.jsp"}) {
            String jsp = Files.readString(resolveProjectPath(DIR.resolve(page)));
            assertThat(jsp).as(page)
                    .contains("<form action=\"<%= request.getContextPath() %>/" + ROUTE + "\" method=\"post\"")
                    // The group the page shows travels with the form, encoded, so another tab can't change the target.
                    .contains("<input type=\"hidden\" name=\"groupName\" value=\"<carlos:encode value='<%= groupName %>' "
                            + "context=\"htmlAttribute\"/>\">")
                    .doesNotContain("ViewMeasurementGroupDScomplete");
        }
    }

    @Test
    @DisplayName("should only show the result on the complete page")
    void shouldOnlyShowTheResult_onTheCompletePage() throws Exception {
        String jsp = Files.readString(resolveProjectPath(DIR.resolve("MeasurementGroupDScomplete.jsp")));

        assertThat(jsp)
                .contains("request.getAttribute(\"dsState\")")
                .doesNotContain("addMeasurementGroupDS(")
                .doesNotContain("removeMeasurementGroupDS(")
                .doesNotContain("MeasurementManager");
    }

    @Test
    @DisplayName("should map the decision support action to the complete page")
    void shouldMapTheAction_toTheCompletePage() throws Exception {
        String struts = Files.readString(resolveProjectPath(
                Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts-encounter.xml")));

        assertThat(struts).contains("<action name=\"" + ROUTE + "\" class=\"io.github.carlos_emr.carlos.encounter."
                + "oscarMeasurements.pageUtil.EctMeasurementGroupDecisionSupport2Action\">\n"
                + "            <result name=\"success\">/WEB-INF/jsp/encounter/oscarMeasurements/"
                + "MeasurementGroupDScomplete.jsp</result>");
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
