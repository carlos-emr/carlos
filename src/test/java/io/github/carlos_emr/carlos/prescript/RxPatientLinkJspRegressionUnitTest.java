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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-patient Rx state (#3875): links into Rx pages must name their patient, because a request that
 * names none falls back to the most recently opened Rx patient. The static-script page lists a
 * patient's saved drugs and offers to re-prescribe them, so it must never use that fallback.
 *
 * @since 2026-09-24
 */
@DisplayName("Rx patient-link JSP regressions")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxPatientLinkJspRegressionUnitTest {

    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Pattern STATIC_SCRIPT_LINK = Pattern.compile("/rx/ViewStaticScript2\\?[^\"']*");

    private static String read(String relative) throws IOException {
        return Files.readString(JSP_ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should name the patient on every static-script link")
    void shouldNamePatient_onEveryStaticScriptLink() throws IOException {
        for (String jsp : new String[] {"casemgmt/prescriptions.jsp", "rx/PrintDrugProfile2.jsp", "rx/ListDrugs.jsp",
                "casemgmt/ChartNotesAjax.jsp"}) {
            Matcher links = STATIC_SCRIPT_LINK.matcher(read(jsp));
            int found = 0;
            while (links.find()) {
                found++;
                assertThat(links.group()).as(jsp).contains("demographicNo=");
            }
            assertThat(found).as(jsp).isPositive();
        }
    }

    @Test
    @DisplayName("should keep the drug-profile Show All / Show Current toggles on the page's patient")
    void shouldNamePatient_onDrugProfileToggleLinks() throws IOException {
        int found = 0;
        for (String line : read("rx/PrintDrugProfile2.jsp").split("\\R")) {
            if (!line.contains(">Show All</a>") && !line.contains(">Show Current</a>")) {
                continue;
            }
            found++;
            assertThat(line).contains("/rx/ViewPrintDrugProfile2?").contains(
                    "demographicNo=<carlos:encode value='<%= profileDemographicNo %>' context=\"uriComponent\"/>");
        }
        // Show All and Show Current, above and below the drug list.
        assertThat(found).isEqualTo(4);
    }

    @Test
    @DisplayName("should post the add-allergy form for its patient and redirect back to that patient")
    void shouldNamePatient_onAddAllergyFormAndRedirect() throws IOException {
        String jsp = read("rx/AddReaction2.jsp");
        assertThat(jsp).contains("name=\"formDemographicNo\"");
        assertThat(jsp).contains("<input type=\"hidden\" name=\"demographicNo\"");

        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-prescription.xml"),
                StandardCharsets.UTF_8);
        // The shared "Patient" session attribute is gone (#3875); the action exposes the patient.
        assertThat(struts).doesNotContain("#session.Patient");
        assertThat(struts).contains("/rx/showAllergy?demographicNo=${demographicNo}");
    }

    @Test
    @DisplayName("should open the static-script page only for an explicitly named patient")
    void shouldRefuseFallbackPatient_onStaticScriptPage() throws IOException {
        String jsp = read("rx/StaticScript2.jsp");

        assertThat(jsp).contains("RxSessionBeanResolver.requestedDemographicNo(request)");
        assertThat(jsp).doesNotContain("RxSessionBeanResolver.resolve(request)");
        // Its re-prescribe calls stage for the page's patient only.
        assertThat(jsp).contains("\"&parameterValue=updateReRxDrug&demographicNo=\" + staticScriptDemographicNo");
        assertThat(jsp).contains("\"&demographicNo=\" + staticScriptDemographicNo");
        assertThat(jsp).contains("/rx/searchDrug?demographicNo=\" + staticScriptDemographicNo");
    }

    @Test
    @DisplayName("should keep favourite and add-favourite navigations on the window's patient")
    void shouldNamePatient_onStagingPageNavigations() throws IOException {
        for (String jsp : new String[] {"rx/SideLinksEditFavorites2.jsp", "rx/SideLinksNoEditFavorites.jsp",
                "rx/SideLinksNoEditFavorites2.jsp"}) {
            assertThat(read(jsp)).as(jsp)
                    .contains("/rx/searchDrug?demographicNo=<%= bean2.getDemographicNo() %>&usefav=true");
        }
        assertThat(read("rx/SearchDrug3.jsp"))
                .contains("window.location.href = RxPatientContext.withPatient(ctx + \"/rx/searchDrug\");")
                .contains("onFailure: reportRefusedSave");
    }
}
