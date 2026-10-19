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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the eChart identity banner ({@code newEncounterHeader.jsp}) to the patient its own chart
 * request names (issue #4556).
 *
 * <p>The banner used to read the login-wide {@code EctSessionBean}, which IncomingEncounter
 * rewrites for whichever chart was entered last in any window. Refreshing patient A's chart after
 * patient B's was opened therefore rendered B's name, DOB and health number above A's notes. The
 * live reproduction and its fix are covered by {@code echart-two-patient-isolation}; this test
 * keeps the JSP from falling back to the shared bean and exercises the patient resolution it
 * relies on against a session that still holds the other chart.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("eChart identity banner patient isolation")
@Tag("unit")
@Tag("casemgmt")
class EncounterHeaderPatientIsolationUnitTest {

    private static final Path HEADER_JSP = Path.of(System.getProperty("basedir", ""))
            .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterHeader.jsp");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

    /** The JSP's executable source: JSP and full-line Java comments explain intent, not behaviour. */
    private static String headerSource() throws IOException {
        String jsp = Files.readString(HEADER_JSP, StandardCharsets.UTF_8);
        return JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
    }

    @Test
    @DisplayName("should resolve the banner patient from the chart request that includes it")
    void shouldResolveBannerPatient_fromTheExplicitChartRequest() throws IOException {
        String source = headerSource();

        assertThat(source)
                .contains("demoNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("demographicManager.getDemographicWithExt(loggedInInfo, Integer.valueOf(demoNo))")
                .contains("pageContext.setAttribute(\"popupDemographicNo\", demoNo);");
    }

    @Test
    @DisplayName("should not read the login-wide encounter bean anywhere in the banner")
    void shouldNotReadLoginWideEncounterBean_forTheBanner() throws IOException {
        String source = headerSource();

        assertThat(source)
                .doesNotContain("\"EctSessionBean\"")
                .doesNotContain("EctSessionBean ")
                .doesNotContain("bean.");
    }

    @Test
    @DisplayName("should take the calculator link's sex from the requested patient's record")
    void shouldDerivePatientSex_fromTheRequestedDemographic() throws IOException {
        String source = headerSource();

        // An unknown or unreadable patient must stop the banner instead of dereferencing null.
        assertThat(source)
                .contains("String popupPatientSex = demographic.getSex() == null ? \"\" : demographic.getSex();")
                .containsPattern("if \\(demographic == null\\) \\{\\s*response\\.sendRedirect\\(");
    }

    @Test
    @DisplayName("should name the requested chart while the session still holds another window's chart")
    void shouldReturnRequestedPatient_whenSessionBeanBelongsToAnotherChart() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/CaseManagementEntry");
        request.setSession(new MockHttpSession());
        EctSessionBean otherWindow = new EctSessionBean();
        otherWindow.demographicNo = "202";
        request.getSession().setAttribute("EctSessionBean", otherWindow);
        EncounterPatientContext.rememberEncounter(request, otherWindow);
        request.setParameter("method", "setUpMainEncounter");
        request.setParameter("demographicNo", "101");

        assertThat(EncounterPatientContext.requireDemographicNo(request)).isEqualTo("101");
    }

    @Test
    @DisplayName("should refuse a banner request that names no patient instead of borrowing the session's")
    void shouldRejectBannerRequest_whenNoPatientIsNamed() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/casemgmt/ViewNewEncounterHeader");
        request.setSession(new MockHttpSession());
        EctSessionBean otherWindow = new EctSessionBean();
        otherWindow.demographicNo = "202";
        request.getSession().setAttribute("EctSessionBean", otherWindow);

        assertThatThrownBy(() -> EncounterPatientContext.requireDemographicNo(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
