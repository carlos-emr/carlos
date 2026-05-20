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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Removed JSP reference regression tests")
class RemovedJspReferenceRegressionTest {

    @Test
    @DisplayName("Appointment admin day should not link to removed PMmodule popup JSPs")
    void shouldNotContainRemovedPmmodulePopups_inAppointmentAdminDayJsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp"));

        assertThat(jsp)
                .doesNotContain("/PMmodule/createAnonymousClient.jsp")
                .doesNotContain("/PMmodule/createPEClient.jsp");
    }

    @Test
    @DisplayName("SearchDrug3 should not reference removed TreatmentMyD JSP")
    void shouldNotContainRemovedTreatmentMyDJsp_inSearchDrug3Jsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp"));

        assertThat(jsp).doesNotContain("/rx/TreatmentMyD.jsp");
    }

    @Test
    @DisplayName("WEB-INF JSPs should not reference removed DataTables assets")
    void shouldNotReferenceRemovedDataTablesAssets_inWebInfJsps() throws IOException {
        Path jspRoot = Path.of("src/main/webapp/WEB-INF/jsp");

        try (Stream<Path> paths = Files.walk(jspRoot)) {
            List<Path> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                    .filter(path -> containsAny(path, "/library/DataTables/datatables.min.js",
                            "/library/DataTables/DataTables-1.13.11/css/jquery.dataTables.min.css"))
                    .toList();

            assertThat(offenders)
                    .as("JSPs must reference shipped DataTables-1.13.11 assets, not removed compatibility paths")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("demographic search results should not include Oscar.js twice")
    void shouldNotDuplicateOscarJsInclude_inDemographicSearchResults() throws IOException {
        String jsp = Files.readString(
                Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicsearchresults.jsp"));

        assertThat(jsp)
                .as("global-head.jspf already includes Oscar.js")
                .contains("global-head.jspf")
                .doesNotContain("/share/javascript/Oscar.js");
    }

    @Test
    @DisplayName("Oscar.js force-window list should tolerate duplicate script includes")
    void shouldUseRedeclarableForceWindowList_inOscarJs() throws IOException {
        String oscarJs = Files.readString(Path.of("src/main/webapp/share/javascript/Oscar.js"));

        assertThat(oscarJs)
                .as("legacy JSPs can still include Oscar.js more than once on the same page")
                .contains("window.forceWindowPaths = window.forceWindowPaths || [")
                .doesNotContain("const forceWindowPaths = [")
                .doesNotContain("let forceWindowPaths = [");
    }

    @Test
    @DisplayName("Standalone admin JSPs should guard admin-chrome helper calls")
    void shouldGuardAdminChromeHelpers_inStandaloneAdminJsps() throws IOException {
        String myGroup = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/admindisplaymygroup.jsp"));
        String labForwarding = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/labforwardingrules.jsp"));

        assertThat(myGroup)
                .doesNotContain("if it is")
                .contains("typeof parent.parent.resizeIframe === 'function'");
        assertThat(labForwarding).contains("typeof registerFormSubmit === 'function'");
    }

    @Test
    @DisplayName("Lab forwarding JSP should load jQuery for standalone popup use")
    void shouldLoadJqueryBeforeInlineScript_inLabForwardingJsp() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/labforwardingrules.jsp"));

        assertThat(jsp.indexOf("/library/jquery/jquery-3.7.1.min.js"))
                .isPositive()
                .isLessThan(jsp.indexOf("$(\"#providers-selection\")"));
    }

    private static boolean containsAny(Path path, String... needles) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (content.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect " + path, e);
        }
    }
}
