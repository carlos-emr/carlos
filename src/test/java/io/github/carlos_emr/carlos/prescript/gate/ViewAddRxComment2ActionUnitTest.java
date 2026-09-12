/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ViewAddRxComment2Action")
@Tag("unit")
@Tag("security")
class ViewAddRxComment2ActionUnitTest extends CarlosUnitTestBase {

    private static final int SCRIPT_NO = 123;
    private static final int DEMOGRAPHIC_NO = 42;
    private static final String PROVIDER_NO = "999998";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private PrescriptionDao prescriptionDao;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/ViewAddRxComment");
        request.addParameter("scriptNo", String.valueOf(SCRIPT_NO));
        request.addParameter("comment", "Take with food");
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        prescriptionDao = mock(PrescriptionDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PrescriptionDao.class, prescriptionDao);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should reject a non-POST request before looking up a prescription")
    void shouldRejectNonPost_beforeLookup() throws Exception {
        request.setMethod("GET");

        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verifyNoInteractions(prescriptionDao);
    }

    @Test
    @DisplayName("should deny an unauthenticated GET before returning method information")
    void shouldRejectMissingSession_beforeMethodCheck() {
        request.setMethod("GET");
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(securityInfoManager, prescriptionDao);
    }

    @Test
    @DisplayName("should deny a GET without coarse Rx write before returning method information")
    void shouldRejectMissingPrivilege_beforeMethodCheck() {
        request.setMethod("GET");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(prescriptionDao);
    }

    @Test
    @DisplayName("should reject a malformed script id after coarse authorization but before lookup")
    void shouldRejectMalformedId_afterCoarseAuthorizationBeforeLookup() throws Exception {
        request.setParameter("scriptNo", "not-an-id");

        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, null);
        verifyNoInteractions(prescriptionDao);
    }

    @Test
    @DisplayName("should check the coarse Rx role before parsing malformed parameters")
    void shouldRejectMissingCoarsePrivilege_beforeMalformedParameterHandling() {
        request.setParameter("scriptNo", "not-an-id");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(prescriptionDao);
    }

    @Test
    @DisplayName("should reject a missing session before parsing malformed parameters")
    void shouldRejectMissingSession_beforeMalformedParameterHandling() {
        request.setParameter("scriptNo", "not-an-id");
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verifyNoInteractions(securityInfoManager, prescriptionDao);
    }

    @Test
    @DisplayName("should return not found when an authorized caller names no prescription")
    void shouldReturnNotFound_whenPrescriptionMissing() throws Exception {
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(null);

        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(prescriptionDao, never()).updatePrescriptionsByScriptNo(any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject an update to another prescriber's signed document")
    void shouldRejectComment_whenCallerDoesNotOwnPrescription() {
        Prescription prescription = prescription(PROVIDER_NO + "-other");
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(prescription);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verify(prescriptionDao, never()).updatePrescriptionsByScriptNo(any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject the owner without patient-specific Rx write")
    void shouldRejectComment_whenPatientWriteMissing() {
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(prescription(PROVIDER_NO));
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), eq(String.valueOf(DEMOGRAPHIC_NO)))).thenReturn(false);

        assertThatThrownBy(() -> new ViewAddRxComment2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verify(prescriptionDao, never()).updatePrescriptionsByScriptNo(any(Integer.class), any());
    }

    @Test
    @DisplayName("should update exactly the caller's prescription and return no content")
    void shouldUpdateOwnedPrescription_whenAuthorized() throws Exception {
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(prescription(PROVIDER_NO));
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), eq(String.valueOf(DEMOGRAPHIC_NO)))).thenReturn(true);
        when(prescriptionDao.updatePrescriptionsByScriptNo(SCRIPT_NO, "Take with food")).thenReturn(1);

        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        verify(prescriptionDao).updatePrescriptionsByScriptNo(SCRIPT_NO, "Take with food");
    }

    @Test
    @DisplayName("should return conflict when the authorized prescription disappears before update")
    void shouldReturnConflict_whenAuthorizedUpdateAffectsNoRows() throws Exception {
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(prescription(PROVIDER_NO));
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"),
                eq(SecurityInfoManager.WRITE), eq(String.valueOf(DEMOGRAPHIC_NO)))).thenReturn(true);
        when(prescriptionDao.updatePrescriptionsByScriptNo(SCRIPT_NO, "Take with food")).thenReturn(0);

        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        verify(prescriptionDao).updatePrescriptionsByScriptNo(SCRIPT_NO, "Take with food");
    }

    private static Prescription prescription(String providerNo) {
        Prescription prescription = new Prescription();
        prescription.setProviderNo(providerNo);
        prescription.setDemographicId(DEMOGRAPHIC_NO);
        return prescription;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "NULL", "", "  "})
    @DisplayName("should preserve literal clinical note text including explicit clearing")
    void shouldPreserveLiteralComment_whenAuthorized(String comment) throws Exception {
        request.setParameter("comment", comment);
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(prescription(PROVIDER_NO));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE,
                String.valueOf(DEMOGRAPHIC_NO))).thenReturn(true);
        when(prescriptionDao.updatePrescriptionsByScriptNo(SCRIPT_NO, comment)).thenReturn(1);
        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
        verify(prescriptionDao).updatePrescriptionsByScriptNo(SCRIPT_NO, comment);
    }

    @Test
    @DisplayName("should reject an absent comment without modifying the prescription")
    void shouldRejectAbsentComment_beforeLookup() throws Exception {
        request.removeParameter("comment");
        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(prescriptionDao);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("should accept a zero-change retry only when the database still contains the exact comment")
    void shouldCheckCurrentComments_whenDriverReportsZeroChangedRows(boolean exact) throws Exception {
        Prescription stale = prescription(PROVIDER_NO);
        stale.setComments("Take with food");
        when(prescriptionDao.find(SCRIPT_NO)).thenReturn(stale);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE,
                String.valueOf(DEMOGRAPHIC_NO))).thenReturn(true);
        when(prescriptionDao.updatePrescriptionsByScriptNo(SCRIPT_NO, "Take with food")).thenReturn(0);
        when(prescriptionDao.hasExactComments(SCRIPT_NO, "Take with food")).thenReturn(exact);
        assertThat(new ViewAddRxComment2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(exact ? 204 : 409);
        verify(prescriptionDao).hasExactComments(SCRIPT_NO, "Take with food");
    }
}
