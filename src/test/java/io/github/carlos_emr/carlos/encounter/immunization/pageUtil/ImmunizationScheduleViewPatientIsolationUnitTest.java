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
package io.github.carlos_emr.carlos.encounter.immunization.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the legacy immunization schedule pages to the patient their own request names (issue #4673).
 *
 * <p>{@code Schedule.jsp} and {@code ScheduleConfig.jsp} fell back to the login-wide
 * {@code EctSessionBean} when the request named no patient, so a schedule opened without one, or
 * redisplayed after a delete or restore that posted its patient as {@code demoNo}, showed (and
 * offered to change) whichever chart was entered last in any window. {@code ScheduleEdit.jsp}
 * defaulted the "given by" provider to that bean's provider. The actions are covered by
 * {@link ImmunizationSchedulePatientIsolationUnitTest}; the live check is the
 * {@code echart-two-patient-isolation} immunization step.</p>
 *
 * @since 2026-10-16
 */
@DisplayName("Immunization schedule pages patient isolation")
@Tag("unit")
@Tag("prevention")
class ImmunizationScheduleViewPatientIsolationUnitTest extends CarlosUnitTestBase {

    private static final Path PAGES = Path.of(System.getProperty("basedir", ""))
            .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp/encounter/immunization");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

    /** Executable source: JSP and full-line Java comments explain intent, not behaviour. */
    private static String source(String page) throws IOException {
        String jsp = Files.readString(PAGES.resolve(page), StandardCharsets.UTF_8);
        return JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Schedule.jsp", "ScheduleConfig.jsp", "ScheduleEdit.jsp"})
    @DisplayName("should never read the login-wide encounter bean")
    void shouldNotReadSessionBean_forAnyImmunizationPage(String page) throws IOException {
        assertThat(source(page))
                .doesNotContain("getAttribute(\"EctSessionBean\")")
                .doesNotContain("EctSessionBean")
                .doesNotContain("bean.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Schedule.jsp", "ScheduleConfig.jsp"})
    @DisplayName("should show only the patient the request names and refuse a request without one")
    void shouldRequireTheRequestPatient_forSchedulePages(String page) throws IOException {
        String source = source(page);

        assertThat(source)
                .contains("demoNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("response.sendError(HttpServletResponse.SC_BAD_REQUEST")
                .contains("isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(demoNo))")
                .contains("response.sendRedirect(request.getContextPath() + \"/securityError?type=_demographic\");");
        // The patient is resolved, and access to its record checked, before the record or the
        // schedule is read: the pages apply the record check their own write actions apply.
        assertThat(source.indexOf("EncounterPatientContext.requireDemographicNo(request)"))
                .isLessThan(source.indexOf("isAllowedAccessToPatientRecord("));
        assertThat(source.indexOf("isAllowedAccessToPatientRecord("))
                .isLessThan(source.indexOf("getDemographic("));
    }

    @Test
    @DisplayName("should post the patient of a delete or restore under the name the redisplayed schedule reads")
    void shouldPostDemographicNo_fromScheduleMutationForm() throws IOException {
        String source = source("Schedule.jsp");
        int start = source.indexOf("<form id=\"scheduleMutationForm\"");
        int end = source.indexOf("</form>", start);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);

        assertThat(source.substring(start, end))
                .contains("<input type=\"hidden\" name=\"demographic_no\" value=\"<carlos:encode value='<%= demoNo %>' context=\"htmlAttribute\"/>\"/>")
                .doesNotContain("name=\"demoNo\"");
    }

    @Test
    @DisplayName("should name the schedule's own patient in the window that records a dose")
    void shouldHeadDoseWindow_withTheSchedulesPatient() throws IOException {
        assertThat(source("Schedule.jsp"))
                .contains("+ \"&demographic_no=<%= SafeEncode.forJavaScriptBlock(SafeEncode.forUriComponent(demoNo)) %>\"");
        // A template preview (ImmunizationSetDisplay.jsp) names no patient and shows none.
        assertThat(source("ScheduleEdit.jsp"))
                .contains("if (request.getParameter(\"demographic_no\") != null || request.getParameter(\"demographicNo\") != null) {")
                .contains("headerPatientNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(headerPatientNo))")
                .contains("headerPatient = new DemographicData().getDemographic(loggedInInfo, headerPatientNo);")
                .contains("<carlos:encode value='<%= headerPatient.getLastName() %>' context=\"html\"/>");
    }

    @Test
    @DisplayName("should default the provider who gave a dose to the logged-in provider")
    void shouldDefaultGivenByProvider_toLoggedInProvider() throws IOException {
        assertThat(source("ScheduleEdit.jsp"))
                .contains("LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);")
                .contains("String loggedInProviderNo = loggedInInfo.getLoggedInProviderNo();")
                .contains("frm.provider.value = \"<carlos:encode value='<%= loggedInProviderNo %>' context=\"javaScriptBlock\"/>\";");
    }
}
