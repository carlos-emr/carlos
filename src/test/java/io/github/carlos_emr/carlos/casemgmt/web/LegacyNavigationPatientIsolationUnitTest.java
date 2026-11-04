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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the legacy case-management navigation ({@code navigation.jsp}, the left column of
 * {@code encounterLayout.jsp} and the {@code casemgmt/ViewNavigation} fragment) to the patient its
 * own request names (issue #4673).
 *
 * <p>The page used to copy the login-wide {@code EctSessionBean} into a session-wide
 * {@code casemgmt_bean} whenever {@code Index2.jsp} had flagged a new chart entry, and built
 * every link (Master, Billing, Prescriptions, Consultations, Immunizations, forms, messages,
 * ticklers, labs) from that copy, so a navigation rendered for A after B's chart was entered
 * linked to B. Its Immunizations links named no patient at all and relied on the same bean.</p>
 *
 * @since 2026-10-16
 */
@DisplayName("Legacy case-management navigation patient isolation")
@Tag("unit")
@Tag("casemgmt")
class LegacyNavigationPatientIsolationUnitTest extends CarlosUnitTestBase {

    private static final Path WEBAPP = Path.of(System.getProperty("basedir", ""))
            .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /** Executable source: JSP, HTML and full-line Java comments explain intent, not behaviour. */
    private static String source(String page) throws IOException {
        String jsp = Files.readString(WEBAPP.resolve(page), StandardCharsets.UTF_8);
        String withoutJsp = JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
        return HTML_COMMENT.matcher(withoutJsp).replaceAll("");
    }

    @Test
    @DisplayName("should build the navigation from the request's own patient, never a session copy of another chart")
    void shouldBuildNavigation_fromTheRequestPatient() throws IOException {
        String source = source("casemgmt/navigation.jsp");

        assertThat(source)
                .doesNotContain("getAttribute(\"EctSessionBean\")")
                .doesNotContain("casemgmt_bean_flag")
                .doesNotContain("\"casemgmt_bean\"")
                .contains("chartPatientNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("EctSessionBean bean = EncounterPatientContext.entryOrLoggedIn(request, loggedInInfo, chartPatientNo);")
                .contains("Demographic chartPatient = demographicManager.getDemographic(loggedInInfo, chartPatientNo);");
        // The patient record (which checks _demographic read for this patient) is read before any link is built.
        assertThat(source.indexOf("demographicManager.getDemographic(loggedInInfo, chartPatientNo)"))
                .isLessThan(source.indexOf("Clinical Modules"));
    }

    @Test
    @DisplayName("should open the immunization schedule of this chart's patient")
    void shouldNameThePatient_inImmunizationLinks() throws IOException {
        String source = source("casemgmt/navigation.jsp");

        assertThat(source)
                .doesNotContain("/encounter/immunization/initSchedule')")
                .contains("/encounter/immunization/initSchedule?demographic_no=<carlos:encode value='<%= chartPatientNo %>' context=\"uriComponent\"/>");
    }

    @Test
    @DisplayName("should encode the patient's, user's and measurement groups' names wherever the navigation writes them")
    void shouldEncodeFreeTextNames_inNavigation() throws IOException {
        String source = source("casemgmt/navigation.jsp");

        assertThat(source)
                .doesNotContainPattern("<%=\\s*bean\\.patient(Last|First)Name")
                .doesNotContain("userName=<%=bean.userName%>")
                .doesNotContain("<option value=\"<%=tmp%>\"><%=tmp %>")
                .contains("<option value=\"<%= SafeEncode.forHtmlAttribute(tmp) %>\"><%= SafeEncode.forHtmlContent(tmp) %>")
                .contains("userName=<%= SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(bean.userName)) %>");
    }

    @Test
    @DisplayName("should give the page's own links the request's patient through its own bean")
    void shouldExposeRequestBean_toTheNavigationLinks() throws IOException {
        String source = source("casemgmt/navigation.jsp");

        // Its ${bean.demographicNo} and ${bean.providerNo} links (forms, labs) named no scoped bean,
        // so they carried no patient or provider at all.
        assertThat(source).contains("pageContext.setAttribute(\"bean\", bean);");
        assertThat(source.indexOf("pageContext.setAttribute(\"bean\", bean);"))
                .isLessThan(source.indexOf("${bean.demographicNo}"));
    }

    @Test
    @DisplayName("should list only this patient's messages, encoded, and only to a user who may read messages")
    void shouldListOwnPatientsMessages_inMessengerSelect() throws IOException {
        String source = source("casemgmt/navigation.jsp");

        // The loop named the session-wide casemgmt_msgBeans (whichever chart's view loaded last) as a
        // literal string, so every rendering failed there with a PropertyNotFoundException (a 500).
        assertThat(source)
                .doesNotContain("items=\"casemgmt_msgBeans\"")
                .doesNotContain("casemgmt_msgBeans}")
                .contains("securityInfoManager.hasPrivilege(loggedInInfo, \"_msg\", \"r\", null)")
                .contains("caseManagementManager.getMsgBeans(Integer.valueOf(chartPatientNo))")
                .contains("<c:forEach var=\"cmb\" items=\"${navigationMessages}\">")
                .contains("<option value=\"<carlos:encode value='${cmb.label}' context=\"htmlAttribute\"/>\"><carlos:encode value='${cmb.value}'/></option>");
    }

    @Test
    @DisplayName("should no longer flag chart entries for a session-wide navigation copy")
    void shouldNotFlagSessionNavigationCopy_whenEnteringChart() throws IOException {
        assertThat(source("encounter/Index2.jsp")).doesNotContain("casemgmt_bean_flag");
    }
}
