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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.ValidationFailure;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionResult;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthTrackerUpdate2Action}.
 *
 * <p>This is the Health Tracker's only write endpoint, so the tests pin the two
 * things that must hold before anything is stored — POST-only and
 * {@code _measurement w} — plus the split between the redirect the clean path
 * takes and the forward the validation-error path needs.
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class HealthTrackerUpdate2ActionUnitTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private MockedStatic<MeasurementTemplateFlowSheetConfig> flowSheetConfigStatic;
    private MeasurementTemplateFlowSheetConfig flowSheetConfig;
    private MockedConstruction<EctProgram> ectProgram;

    private SecurityInfoManager securityInfoManager;
    private FlowSheetCustomizationDao flowSheetCustomizationDao;
    private HealthTrackerSubmissionService submissionService;
    private HealthTrackerUpdate2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));

        // The flowsheet registry is a Spring-populated singleton; stubbing the
        // accessor keeps this a unit test and lets each case choose whether the
        // requested template resolves.
        flowSheetConfig = mock(MeasurementTemplateFlowSheetConfig.class);
        flowSheetConfigStatic = mockStatic(MeasurementTemplateFlowSheetConfig.class);
        flowSheetConfigStatic.when(MeasurementTemplateFlowSheetConfig::getInstance).thenReturn(flowSheetConfig);

        // EctProgram resolves the CAISI program from the servlet context's Spring
        // context, which a unit test has no way to supply; stub the lookup so the
        // note's program_no is a fixed value instead of an NPE.
        ectProgram = mockConstruction(EctProgram.class,
                (mockProgram, context) -> when(mockProgram.getProgram(nullable(String.class))).thenReturn("10"));

        securityInfoManager = mock(SecurityInfoManager.class);
        flowSheetCustomizationDao = mock(FlowSheetCustomizationDao.class);
        submissionService = mock(HealthTrackerSubmissionService.class);

        action = new HealthTrackerUpdate2Action(securityInfoManager, flowSheetCustomizationDao, submissionService);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
        loggedInInfo.close();
        flowSheetConfigStatic.close();
        ectProgram.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should reject unsafe methods with 405 before any save work happens")
    void shouldReject_whenMethodIsNotPost(String method) throws Exception {
        request.setMethod(method);
        request.addParameter("demographic_no", "111");
        request.addParameter("template", "tracker");

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, flowSheetCustomizationDao, submissionService);
    }

    @Test
    @DisplayName("should throw when the provider lacks measurement write access")
    void shouldThrowSecurityException_whenMeasurementWriteMissing() {
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        request.addParameter("template", "tracker");
        grantPrivilege(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_measurement)");

        verifyNoInteractions(flowSheetCustomizationDao, submissionService);
    }

    @Test
    @DisplayName("should throw when the provider may not open that patient's record")
    void shouldThrowSecurityException_whenPatientRecordNotAccessible() {
        // Chart-wide _measurement w is held; this patient's chart is locked to the
        // role. The demographic number is submitted, so without this check a
        // hand-built POST could write measurements and a signed note into it.
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        request.addParameter("template", "tracker");
        grantPrivilege(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_measurement)");

        verifyNoInteractions(flowSheetCustomizationDao, submissionService);
    }

    @Test
    @DisplayName("should throw when measurement write is denied for that patient")
    void shouldThrowSecurityException_whenPatientScopedWriteMissing() {
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        request.addParameter("template", "tracker");
        grantPrivilege(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), eq("111")))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_measurement)");

        verifyNoInteractions(flowSheetCustomizationDao, submissionService);
    }

    @Test
    @DisplayName("should answer 400 when demographic_no is not a positive number")
    void shouldReturnBadRequest_whenDemographicNoNotPositive() throws Exception {
        request.setMethod("POST");
        request.addParameter("demographic_no", "0");
        request.addParameter("template", "tracker");
        grantPrivilege(true);

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(flowSheetCustomizationDao, submissionService);
    }

    @Test
    @DisplayName("should answer 400 when demographic_no is not a number")
    void shouldReturnBadRequest_whenDemographicNoNotNumeric() throws Exception {
        request.setMethod("POST");
        request.addParameter("demographic_no", "not-a-number");
        request.addParameter("template", "tracker");
        grantPrivilege(true);

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("should answer 404 when the request names no template")
    void shouldReturnNotFound_whenTemplateMissing() throws Exception {
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        grantPrivilege(true);

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(submissionService);
    }

    @Test
    @DisplayName("should forward back to the page when a value was rejected")
    void shouldReturnFailure_whenSubmissionRejectsValues() throws Exception {
        givenTrackerFlowsheet();
        request.addParameter("template", "tracker");
        when(submissionService.submit(any(), any(), anyInt(), nullable(String.class), anyInt(),
                nullable(String.class), anyString()))
                .thenReturn(new HealthTrackerSubmissionResult(
                        1,
                        List.of("Weight (kg): 9999"),
                        List.of(new ValidationFailure("errors.range", List.of("Weight (kg)", "0.0", "500.0"))),
                        ""));

        String result = action.execute();

        assertThat(result).isEqualTo("failure");
        // The reason line falls back to the bare message key here: a unit test has
        // no Struts TextProvider bound, which is exactly the degradation the
        // action's describe() guards.
        assertThat(String.valueOf(request.getAttribute("testOutput")))
                .contains("Weight (kg): 9999")
                .contains("errors.range");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should redirect back to the tracker when everything saved")
    void shouldRedirectToTracker_whenSubmissionClean() throws Exception {
        givenTrackerFlowsheet();
        request.addParameter("template", "tracker");
        request.addParameter("ycoord", "480");
        givenCleanSubmission();

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/encounter/oscarMeasurements/ViewHealthTracker"
                        + "?demographic_no=111&template=tracker&ycoord=480");
    }

    @Test
    @DisplayName("should leave a non-numeric scroll offset out of the redirect")
    void shouldOmitScrollOffset_whenYcoordNotNumeric() throws Exception {
        givenTrackerFlowsheet();
        request.addParameter("template", "tracker");
        request.addParameter("ycoord", "javascript:alert(1)");
        givenCleanSubmission();

        action.execute();

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/encounter/oscarMeasurements/ViewHealthTracker"
                        + "?demographic_no=111&template=tracker");
    }

    private void givenCleanSubmission() {
        when(submissionService.submit(any(), any(), anyInt(), nullable(String.class), anyInt(),
                nullable(String.class), anyString()))
                .thenReturn(new HealthTrackerSubmissionResult(2, List.of(), List.of(), ""));
    }

    /**
     * Sets up a POST whose template resolves, which is what lets the action get
     * as far as the submission service.
     */
    private void givenTrackerFlowsheet() {
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        grantPrivilege(true);
        when(flowSheetCustomizationDao.getFlowSheetCustomizations(anyString(), nullable(String.class), anyInt()))
                .thenReturn(List.of());
        when(flowSheetConfig.getFlowSheet(anyString(), any(List.class)))
                .thenReturn(mock(MeasurementFlowSheet.class));
    }

    @Test
    @DisplayName("should answer 404 when the named flowsheet does not exist")
    void shouldReturnNotFound_whenFlowsheetUnknown() throws Exception {
        request.setMethod("POST");
        request.addParameter("demographic_no", "111");
        request.addParameter("template", "no-such-flowsheet");
        grantPrivilege(true);
        when(flowSheetCustomizationDao.getFlowSheetCustomizations(anyString(), nullable(String.class), anyInt()))
                .thenReturn(List.of());
        when(flowSheetConfig.getFlowSheet(anyString(), any(List.class))).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(submissionService);
    }

    /**
     * Grants (or denies) both halves of the gate: the chart-wide privilege and the
     * patient-scoped one the action applies once it knows which patient is named.
     */
    private void grantPrivilege(boolean granted) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(),
                nullable(String.class))).thenReturn(granted);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt()))
                .thenReturn(granted);
    }
}
