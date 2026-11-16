/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReport;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReportFactory;
import io.github.carlos_emr.carlos.report.data.RptDemographicQueryBuilder;
import io.github.carlos_emr.carlos.report.data.RptDemographicQueryLoader;
import io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Form;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Ontario prevention report action")
class PreventionReport2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/prevention/PreventionReport");
        MockHttpServletResponse response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void closeServletActionContextMock() {
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should render the report form when opened without selections")
    void shouldRenderForm_whenOpenedWithoutSelections() {
        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when placeholder selections are submitted")
    void shouldRenderForm_whenPlaceholderSelectionsSubmitted() {
        request.addParameter("patientSet", "-1");
        request.addParameter("prevention", "-1");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when the saved-query id is malformed")
    void shouldRenderForm_whenPatientSetIdMalformed() {
        request.addParameter("patientSet", "not-a-query-id");
        request.addParameter("prevention", "Flu");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should render the report form when the report type is unknown")
    void shouldRenderForm_whenReportTypeUnknown() {
        request.addParameter("patientSet", "1");
        request.addParameter("prevention", "not-a-report");

        assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should pass the count of unreadable patients to the page with the report")
    void shouldPublishUnreadableCount_whenTheReportCouldNotReadSomePatients() {
        request.addParameter("patientSet", "1");
        request.addParameter("prevention", "Flu");
        request.addParameter("asofDate", "2026-11-01");
        java.util.ArrayList<PreventionReportDisplay> rows = new java.util.ArrayList<>();
        rows.add(PreventionReportDisplay.unreadable(102));
        java.util.Hashtable<String, Object> ran = new java.util.Hashtable<>();
        ran.put("returnReport", rows);
        ran.put(PreventionReport.UNREADABLE_COUNT, "1");
        ran.put("up2date", "0");
        ran.put("percent", "0");
        ran.put("inEligible", "0");
        PreventionReport report = (user, patients, asOf) -> ran;

        try (var loaders = org.mockito.Mockito.mockConstruction(RptDemographicQueryLoader.class,
                     (loader, context) -> when(loader.queryLoader(org.mockito.ArgumentMatchers.any()))
                             .thenAnswer(call -> call.getArgument(0)));
             var builders = org.mockito.Mockito.mockConstruction(RptDemographicQueryBuilder.class,
                     (builder, context) -> when(builder.buildQuery(org.mockito.ArgumentMatchers.eq(loggedInInfo),
                             org.mockito.ArgumentMatchers.any(RptDemographicReport2Form.class)))
                             .thenReturn(new java.util.ArrayList<>()));
             var demographics = org.mockito.Mockito.mockConstruction(
                     io.github.carlos_emr.carlos.demographic.data.DemographicData.class);
             MockedStatic<PreventionReportFactory> factory = mockStatic(PreventionReportFactory.class)) {
            factory.when(() -> PreventionReportFactory.getPreventionReport("Flu")).thenReturn(report);

            assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        // The page reads this to say, above the totals, that the report is incomplete.
        assertThat(request.getAttribute("unreadablePatients")).isEqualTo("1");
        assertThat(request.getAttribute("returnReport")).isSameAs(rows);
    }

    @Test
    @DisplayName("should count a row whose patient record is not found and give the page the records it found")
    void shouldCountARowWithoutAPatientRecord_whenTheDemographicIsNotFound() {
        request.addParameter("patientSet", "1");
        request.addParameter("prevention", "Flu");
        request.addParameter("asofDate", "2026-11-01");
        java.util.ArrayList<PreventionReportDisplay> rows = new java.util.ArrayList<>();
        PreventionReportDisplay found = new PreventionReportDisplay();
        found.demographicNo = 101;
        found.state = "Up to date";
        PreventionReportDisplay missing = new PreventionReportDisplay();
        missing.demographicNo = 102;
        missing.state = "No Info";
        // Already counted by the report: must not be counted a second time for its missing record.
        PreventionReportDisplay unreadable = PreventionReportDisplay.unreadable(103);
        rows.add(found);
        rows.add(missing);
        rows.add(unreadable);
        java.util.Hashtable<String, Object> ran = new java.util.Hashtable<>();
        ran.put("returnReport", rows);
        ran.put(PreventionReport.UNREADABLE_COUNT, "1");
        PreventionReport report = (user, patients, asOf) -> ran;
        io.github.carlos_emr.carlos.commn.model.Demographic patient = new io.github.carlos_emr.carlos.commn.model.Demographic();

        try (var loaders = org.mockito.Mockito.mockConstruction(RptDemographicQueryLoader.class,
                     (loader, context) -> when(loader.queryLoader(org.mockito.ArgumentMatchers.any()))
                             .thenAnswer(call -> call.getArgument(0)));
             var builders = org.mockito.Mockito.mockConstruction(RptDemographicQueryBuilder.class,
                     (builder, context) -> when(builder.buildQuery(org.mockito.ArgumentMatchers.eq(loggedInInfo),
                             org.mockito.ArgumentMatchers.any(RptDemographicReport2Form.class)))
                             .thenReturn(new java.util.ArrayList<>()));
             var demographics = org.mockito.Mockito.mockConstruction(
                     io.github.carlos_emr.carlos.demographic.data.DemographicData.class,
                     (data, context) -> when(data.getDemographic(loggedInInfo, "101")).thenReturn(patient));
             MockedStatic<PreventionReportFactory> factory = mockStatic(PreventionReportFactory.class)) {
            factory.when(() -> PreventionReportFactory.getPreventionReport("Flu")).thenReturn(report);

            assertThat(new PreventionReport2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        // One from the report (103) and one whose patient record was not found (102).
        assertThat(request.getAttribute("unreadablePatients")).isEqualTo("2");
        // No row is dropped: the page lists 102 as "could not be read" for want of its record.
        assertThat(request.getAttribute("returnReport")).isSameAs(rows);
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, Object> records = (java.util.Map<Integer, Object>) request.getAttribute("reportDemographics");
        assertThat(records).containsOnlyKeys(101).containsEntry(101, patient);
    }

    @Test
    @DisplayName("should preserve the report privilege requirement")
    void shouldRejectRequest_whenReportPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null))
                .thenReturn(false);

        PreventionReport2Action action = new PreventionReport2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_report");
    }
}
