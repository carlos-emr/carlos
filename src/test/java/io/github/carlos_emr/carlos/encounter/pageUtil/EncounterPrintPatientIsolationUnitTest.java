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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The legacy encounter prints show the patient their own request names (issue #4673).
 *
 * <p>{@code encounterPrint.jsp} (the legacy editor's Print button) printed the login-wide
 * {@code EctSessionBean}, which follows whichever chart was entered last in any window, so A's
 * window printed B's name, family doctor, history and encounter. {@code echarthistoryprint.jsp}
 * (a revision from the split chart, the eChart history report and the messenger's encounter
 * attachment) loaded the requested revision into that shared bean, overwriting another window's
 * entry, and failed when the login had not entered any chart. Both now load a request-owned bean
 * through {@link EctSessionBean#setUpEncounterPage(LoggedInInfo, String, String)}, which shows a
 * revision only when it belongs to the printed patient.</p>
 *
 * @since 2026-10-16
 */
@DisplayName("Legacy encounter print patient isolation")
@Tag("unit")
@Tag("encounter")
class EncounterPrintPatientIsolationUnitTest extends CarlosUnitTestBase {
    private static final String A = "900001";
    private static final int B = 900002;

    private EChartDao charts;
    private DemographicManager demographics;
    private LoggedInInfo login;
    private SecurityInfoManager security;

    @BeforeEach
    void registerPatientRecords() {
        charts = createAndRegisterMock(EChartDao.class);
        demographics = createAndRegisterMock(DemographicManager.class);
        login = mock(LoggedInInfo.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.isAllowedAccessToPatientRecord(login, Integer.valueOf(A))).thenReturn(true);
        Demographic patient = new Demographic();
        patient.setDemographicNo(Integer.valueOf(A));
        patient.setLastName("FAKE-Alpha");
        patient.setFirstName("Ann");
        patient.setSex("F");
        patient.setProviderNo("111111");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        when(demographics.getDemographic(login, A)).thenReturn(patient);
    }

    @Nested
    @DisplayName("print bean")
    class PrintBean {

        @Test
        void shouldClearPriorTextAndRefuseBeforeClinicalReads_whenPrintedRecordIsExcluded() {
            when(security.isAllowedAccessToPatientRecord(login, Integer.valueOf(A))).thenReturn(false);
            EctSessionBean printed = new EctSessionBean();
            printed.encounter = "FAKE stale chart text";
            printed.oscarMsg = "FAKE stale message";

            assertThatThrownBy(() -> printed.setUpEncounterPage(login, "41", A))
                    .isInstanceOf(SecurityException.class).hasMessage("Patient record access denied");

            assertThat(printed.encounter).isEmpty();
            assertThat(printed.oscarMsg).isEmpty();
            verifyNoInteractions(charts, demographics);
        }

        @Test
        @DisplayName("should print the requested revision when it is this patient's")
        void shouldPrintRequestedRevision_whenItBelongsToThePatient() {
            when(charts.find((Object) 41)).thenReturn(revision(41, Integer.parseInt(A), "Alpha history"));

            EctSessionBean printed = new EctSessionBean();
            printed.setUpEncounterPage(login, "41", A);

            assertThat(printed.eChartId).isEqualTo("41");
            assertThat(printed.socialHistory).isEqualTo("Alpha history");
            assertThat(printed.encounter).isEqualTo("Alpha history encounter");
            assertThat(printed.patientLastName).isEqualTo("FAKE-Alpha");
            assertThat(printed.familyDoctorNo).isEqualTo("111111");
        }

        @Test
        @DisplayName("should not print another patient's revision under this patient's name")
        void shouldIgnoreRevision_whenItBelongsToAnotherPatient() {
            when(charts.find((Object) 42)).thenReturn(revision(42, B, "Beta history"));

            EctSessionBean printed = new EctSessionBean();
            printed.setUpEncounterPage(login, "42", A);

            assertThat(printed.eChartId).isEmpty();
            assertThat(printed.socialHistory).isEmpty();
            assertThat(printed.encounter).isEmpty();
            assertThat(printed.patientLastName).isEqualTo("FAKE-Alpha");
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", "not-a-revision", "43"})
        @DisplayName("should print only the patient's header when the revision is missing or unknown")
        void shouldPrintHeaderOnly_whenRevisionIsMissingOrUnknown(String echartId) {
            EctSessionBean printed = new EctSessionBean();

            assertThatCode(() -> printed.setUpEncounterPage(login, echartId, A)).doesNotThrowAnyException();
            assertThat(printed.encounter).isEmpty();
            assertThat(printed.patientLastName).isEqualTo("FAKE-Alpha");
        }

        @Test
        @DisplayName("should print a patient whose birth date is not recorded")
        void shouldPrintPatient_whenBirthDateIsMissing() {
            Demographic undated = new Demographic();
            undated.setDemographicNo(Integer.valueOf(A));
            undated.setLastName("FAKE-Alpha");
            when(demographics.getDemographic(login, A)).thenReturn(undated);

            EctSessionBean printed = new EctSessionBean();

            assertThatCode(() -> printed.setUpEncounterPage(login, null, A)).doesNotThrowAnyException();
            assertThat(printed.patientLastName).isEqualTo("FAKE-Alpha");
        }

        private EChart revision(int id, int patient, String history) {
            EChart chart = new EChart();
            chart.setId(id);
            chart.setDemographicNo(patient);
            chart.setSocialHistory(history);
            chart.setFamilyHistory("");
            chart.setMedicalHistory("");
            chart.setOngoingConcerns("");
            chart.setReminders("");
            chart.setEncounter(history + " encounter");
            chart.setSubject("");
            return chart;
        }
    }

    @Nested
    @DisplayName("print pages")
    class PrintPages {
        private static final Path ENCOUNTER = Path.of(System.getProperty("basedir", ""))
                .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp/encounter");
        private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
        private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

        private String source(String page) throws IOException {
            String jsp = Files.readString(ENCOUNTER.resolve(page), StandardCharsets.UTF_8);
            return JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"encounterPrint.jsp", "echarthistoryprint.jsp"})
        @DisplayName("should load a request-owned bean for the request's own, authorized patient")
        void shouldLoadRequestOwnedBean_forTheRequestPatient(String page) throws IOException {
            String source = source(page);

            assertThat(source)
                    .doesNotContain("getAttribute(\"EctSessionBean\")")
                    .doesNotContain("setAttribute(\"EctSessionBean\"")
                    .contains("printedPatient = EncounterPatientContext.requireDemographicNo(request);")
                    .contains("response.sendError(HttpServletResponse.SC_BAD_REQUEST")
                    .contains("securityInfoManager.hasPrivilege(loggedInInfo, \"_eChart\", \"r\", printedPatient)")
                    .contains("securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.valueOf(printedPatient))")
                    .contains("EctSessionBean bean = new EctSessionBean();")
                    .contains("bean.setUpEncounterPage(loggedInInfo, request.getParameter(\"echartid\"), printedPatient);");
            // Access is checked before the patient's record or chart is loaded.
            assertThat(source.indexOf("isAllowedAccessToPatientRecord("))
                    .isLessThan(source.indexOf("bean.setUpEncounterPage("));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"encounterPrint.jsp", "echarthistoryprint.jsp"})
        @DisplayName("should encode every printed patient and chart field")
        void shouldEncodePrintedFields_forEachPrintPage(String page) throws IOException {
            String source = source(page);

            // No raw expression output of a bean field; encoded tag values are written value='<%= ... %>'.
            assertThat(source).doesNotContainPattern("(?<!value=')<%=\\s*bean\\.");
            for (String field : new String[]{"patientLastName", "patientFirstName", "socialHistory", "familyHistory",
                    "medicalHistory", "ongoingConcerns", "reminders", "encounter"}) {
                assertThat(source).as(field).contains("<carlos:encode value='<%= bean." + field + " %>'");
            }
        }

        @Test
        @DisplayName("should print the revision the legacy editor window shows")
        void shouldPassWindowRevision_fromLegacyPrintButton() throws IOException {
            assertThat(source("includes/encounter-row-three.jspf"))
                    .contains("/encounter/ViewEncounterPrint?demographicNo=${carlos:forJavaScriptAttribute(carlos:forUriComponent(bean.demographicNo))}"
                            + "&amp;echartid=${carlos:forJavaScriptAttribute(carlos:forUriComponent(bean.EChartId))}");
        }

        @Test
        @DisplayName("should read the window's revision through the property EL resolves on the bean")
        void shouldUseResolvableRevisionProperty_inLegacyEditorPages() throws Exception {
            // getEChartId() is the bean property "EChartId": JavaBeans keeps a leading pair of
            // capitals, so ${bean.eChartId} would not resolve and the editor page would fail.
            Set<String> properties = Arrays.stream(Introspector.getBeanInfo(EctSessionBean.class).getPropertyDescriptors())
                    .map(PropertyDescriptor::getName).collect(Collectors.toSet());
            assertThat(properties).contains("EChartId").doesNotContain("eChartId");

            assertThat(source("Index2.jsp"))
                    .contains("<input type=\"hidden\" name=\"eChartId\" value=\"${carlos:forHtmlAttribute(bean.EChartId)}\">")
                    .doesNotContain("bean.eChartId");
            assertThat(source("includes/encounter-row-three.jspf")).doesNotContain("bean.eChartId");
        }

        @Test
        @DisplayName("should render the legacy editor's appointment encoded")
        void shouldEncodeAppointment_inLegacyEditorForm() throws IOException {
            assertThat(source("includes/encounter-row-three.jspf"))
                    .contains("<input type=\"hidden\" name=\"appointment_no\" value=\"<carlos:encode value='${bean.appointmentNo}' context=\"htmlAttribute\"/>\"/>")
                    .doesNotContain("value=\"${bean.appointmentNo}\"");
        }

        @Test
        @DisplayName("should post the legacy editor to its save route whatever the page's base address")
        void shouldPostEditorToSaveRoute_despiteBaseHref() throws IOException {
            // encounter-head.jspf sets <base href> to the context root, so the relative action
            // "SaveEncounter2" posted to /carlos/SaveEncounter2 (404) and no editor save reached it.
            assertThat(source("includes/encounter-head.jspf")).contains("<base href=");
            assertThat(source("Index2.jsp"))
                    .contains("<form name=\"encForm\" action=\"${pageContext.request.contextPath}/encounter/SaveEncounter2\" method=\"POST\">")
                    .doesNotContain("action=\"SaveEncounter2\"");
        }
    }
}
