/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.gate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Patient locks on the cumulative lab-value table and graph. The JSPs' demographic lookup enforces
 * only {@code _demographic$N}; the gates must also refuse an {@code _eChart$N} lock (both reach
 * {@code isAllowedAccessToPatientRecord}) on the patient parameter each JSP actually reads, before
 * any lab value, medication or demographic is read.
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
class LabValuesPatientLockUnitTest extends CarlosUnitTestBase {

    /** Each gate with the patient parameter and JSP it forwards to. */
    enum Gate {
        VALUES("demo", "labValues.jsp"), GRAPH("demographic_no", "labValuesGraph.jsp");

        final String patientParameter;
        final String jsp;

        Gate(String patientParameter, String jsp) {
            this.patientParameter = patientParameter;
            this.jsp = jsp;
        }
    }

    private SecurityInfoManager security;
    private PatientLabRoutingDao labValues;
    private DemographicManager demographics;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        labValues = mock(PatientLabRoutingDao.class);
        demographics = mock(DemographicManager.class);
        login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(login), eq("_lab"), eq("r"), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        // The JSPs read values through PatientLabRoutingDao and the patient through DemographicManager.
        registerMock(PatientLabRoutingDao.class, labValues);
        registerMock(DemographicManager.class, demographics);

        request = new MockHttpServletRequest("GET", "/lab/CA/ON/values");
        request.setParameter("labType", "HL7");
        request.setParameter("testName", "Synthetic Test");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
    }

    @AfterEach
    void tearDown() {
        sessions.close();
        servlet.close();
    }

    private String execute(Gate gate) throws Exception {
        return switch (gate) {
            case VALUES -> new ViewLabValues2Action().execute();
            case GRAPH -> new ViewLabValuesGraph2Action().execute();
        };
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should forward to the values page when the requested patient is unlocked")
    void shouldForwardToValues_whenRequestedPatientIsUnlocked(Gate gate) throws Exception {
        request.setParameter(gate.patientParameter, "123");

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(security).isAllowedAccessToPatientRecord(login, 123);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should refuse a locked patient before any lab value, demographic or content is read")
    void shouldRefuseBeforeAnyValueRead_whenRequestedPatientIsLocked(Gate gate) {
        request.setParameter(gate.patientParameter, "123");
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(false);

        assertThatThrownBy(() -> execute(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessageNotContaining("123");

        verifyNoInteractions(labValues, demographics);
        assertThat(response.getContentAsByteArray()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should check the parameter its JSP reads, not a forged sibling parameter")
    void shouldCheckThePatientTheJspReads_whenOtherPatientParametersAreForged(Gate gate) {
        request.setParameter("demo", "999");
        request.setParameter("demographic_no", "999");
        request.setParameter("demographicNo", "999");
        request.setParameter(gate.patientParameter, "123");
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(false);

        assertThatThrownBy(() -> execute(gate)).isInstanceOf(SecurityException.class);

        verify(security, never()).isAllowedAccessToPatientRecord(login, 999);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should offer the lab graph's medication picker only to a user holding _rx on the patient")
    void shouldOfferMedicationPickerOnlyWithRxOnThePatient() throws Exception {
        request.setParameter("demographic_no", "123");
        when(security.hasPrivilege(login, "_rx", "r", "123")).thenReturn(false);

        // A lab-only user still gets the lab graph page, but the page is told to leave medications out.
        assertThat(new ViewLabValuesGraph2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED)).isEqualTo(false);

        when(security.hasPrivilege(login, "_rx", "r", "123")).thenReturn(true);
        assertThat(new ViewLabValuesGraph2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED)).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("should decide the medication picker for the canonical patient when demographic_no is zero-padded")
    void shouldDecideMedicationsForTheCanonicalPatient_whenDemographicNoIsZeroPadded(boolean mayReadRx) throws Exception {
        // The lab gate and the embedded graph both read 00123 as patient 123; the _rx decision must too.
        request.setParameter("demographic_no", "00123");
        when(security.hasPrivilege(login, "_rx", "r", "123")).thenReturn(mayReadRx);

        assertThat(new ViewLabValuesGraph2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED)).isEqualTo(mayReadRx);
        verify(security).hasPrivilege(login, "_rx", "r", "123");
        verify(security, never()).hasPrivilege(login, "_rx", "r", "00123");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should not offer medications for a prescription right held on another patient or no patient")
    void shouldNotOfferMedications_whenRxIsMissingOnThisPatientOrNoPatientIsNamed() throws Exception {
        when(security.hasPrivilege(login, "_rx", "r", "123")).thenReturn(false);
        when(security.hasPrivilege(login, "_rx", "r", "456")).thenReturn(true);
        request.setParameter("demographic_no", "123");
        new ViewLabValuesGraph2Action().execute();
        assertThat(request.getAttribute(ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED)).isEqualTo(false);

        MockHttpServletRequest none = new MockHttpServletRequest("GET", "/lab/CA/ON/values");
        servlet.when(ServletActionContext::getRequest).thenReturn(none);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(none)).thenReturn(login);
        new ViewLabValuesGraph2Action().execute();
        assertThat(none.getAttribute(ViewLabValuesGraph2Action.MEDICATIONS_ALLOWED)).isEqualTo(false);
    }

    static Stream<Arguments> noPatientValues() {
        return cases(new String[] {null, "", "null", "undefined", "0"});
    }

    @ParameterizedTest
    @MethodSource("noPatientValues")
    @DisplayName("should keep the legacy no-patient and unassigned forms behind the global _lab read")
    void shouldPreserveLegacyNoPatientForms_whenNoPatientIsNamed(Gate gate, String value) throws Exception {
        if (value != null) {
            request.setParameter(gate.patientParameter, value);
        }

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    static Stream<Arguments> malformedPatients() {
        return cases(new String[] {"-1", "00x", "1x", " 12", "2147483648", "999999999999999999999"});
    }

    @ParameterizedTest
    @MethodSource("malformedPatients")
    @DisplayName("should answer 400 without a patient check when the patient parameter is malformed")
    void shouldAnswerBadRequest_whenPatientParameterIsMalformed(Gate gate, String value) throws Exception {
        request.setParameter(gate.patientParameter, value);

        assertThat(execute(gate)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
        verifyNoInteractions(labValues, demographics);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should deny before patient parsing when global _lab read is missing")
    void shouldDenyBeforePatientParsing_whenGlobalLabReadIsMissing(Gate gate) {
        request.setParameter(gate.patientParameter, "bad");
        when(security.hasPrivilege(login, "_lab", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> execute(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");

        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should gate the same patient parameter its JSP reads")
    void shouldGateTheJspPatientParameter_forEachValuesPage(Gate gate) throws Exception {
        String jsp = Files.readString(Path.of(System.getProperty("user.dir"),
                "src/main/webapp/WEB-INF/jsp/lab/CA/ON", gate.jsp));

        assertThat(jsp).contains("String demographicNo = request.getParameter(\"" + gate.patientParameter + "\");");
    }

    private static Stream<Arguments> cases(String[] values) {
        List<Arguments> cases = new ArrayList<>();
        for (Gate gate : Gate.values()) {
            for (String value : values) {
                cases.add(Arguments.of(gate, value));
            }
        }
        return cases.stream();
    }
}
