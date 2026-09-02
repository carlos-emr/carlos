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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RxRePrescribe2Action prescription signature tests")
@Tag("integration")
@Tag("prescript")
class RxRePrescribe2ActionTest extends CarlosWebTestBase {

    /** The patient the fixture prescription belongs to; the patient-scoped _rx check targets this. */
    private static final int SIGNATURE_DEMOGRAPHIC_NO = 4242;
    private static final int SCRIPT_ID = 1234;
    private static final int SIGNATURE_ID = 5678;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private PrescriptionManager mockPrescriptionManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxRePrescribe2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRemoteAddr("127.0.0.1");

        RxSessionBean rxSessionBean = new RxSessionBean();
        rxSessionBean.setDemographicNo(1);
        rxSessionBean.setProviderNo("999998");
        request.getSession().setAttribute("RxSessionBean", rxSessionBean);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(PrescriptionManager.class, mockPrescriptionManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // By default the prescription row exists and the link persists.
        when(mockPrescriptionManager.setPrescriptionSignature(any(), any(Integer.class), any())).thenReturn(true);
        // The signature update resolves the target prescription and re-checks _rx write against the
        // patient that prescription actually belongs to, so both must be stubbed for the happy path.
        io.github.carlos_emr.carlos.commn.model.Prescription targetPrescription =
                new io.github.carlos_emr.carlos.commn.model.Prescription();
        targetPrescription.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        when(mockPrescriptionManager.getPrescription(any(), eq(SCRIPT_ID))).thenReturn(targetPrescription);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"),
                eq(String.valueOf(SIGNATURE_DEMOGRAPHIC_NO)))).thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        action = new RxRePrescribe2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should reject non-POST methods before mutating prescription signatures")
    void shouldRejectNonPost_beforeMutatingPrescriptionSignatures() throws Exception {
        request.setMethod("PUT");
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull());
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should associate a saved digital signature with a prescription")
    void shouldAssociateSavedDigitalSignature_withPrescription() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "w", null);
        verify(mockSecurityInfoManager)
                .hasPrivilege(mockLoggedInInfo, "_rx", "w", String.valueOf(SIGNATURE_DEMOGRAPHIC_NO));
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, SIGNATURE_ID);
    }

    @Test
    @DisplayName("should audit the persisted prescription's patient, not the open chart's")
    void shouldAuditPersistedPatient_whenSessionBeanHoldsAnotherChart() throws Exception {
        // scriptId is request-supplied and authorized against the row it resolves to, so the signed
        // prescription can belong to a different patient than the chart the session has open (the
        // fixture's bean holds demographic 1; the target row is SIGNATURE_DEMOGRAPHIC_NO). Auditing
        // the bean would file the signature event under whichever chart happened to be open.
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        try (MockedStatic<LogAction> logActionMock = mockStatic(LogAction.class)) {
            action.saveDigitalSignature();

            logActionMock.verify(() -> LogAction.addLog(eq("999998"), eq(LogConst.REPRINT),
                    eq(LogConst.CON_PRESCRIPTION), eq(String.valueOf(SCRIPT_ID)), anyString(),
                    eq(String.valueOf(SIGNATURE_DEMOGRAPHIC_NO))));
        }
    }

    @Test
    @DisplayName("should accept a 10-digit script id the page is able to emit")
    void shouldAcceptScriptId_withTenDigits() throws Exception {
        // ViewScript2's firstValidScriptId emits any 1-10 digit id that parses to a positive int, so
        // a 9-digit cap here would reject a legitimate high script number and silently leave the
        // drawn signature unlinked while the page reported success.
        int tenDigitScript = 1234567890;
        io.github.carlos_emr.carlos.commn.model.Prescription target =
                new io.github.carlos_emr.carlos.commn.model.Prescription();
        target.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        when(mockPrescriptionManager.getPrescription(any(), eq(tenDigitScript))).thenReturn(target);
        when(mockPrescriptionManager.setPrescriptionSignature(any(), eq(tenDigitScript), any())).thenReturn(true);
        request.setParameter("scriptId", String.valueOf(tenDigitScript));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, tenDigitScript, SIGNATURE_ID);
    }

    @Test
    @DisplayName("should reject a 10-digit script id that overflows an int")
    void shouldRejectScriptId_whenTenDigitsOverflowInt() throws Exception {
        // 9999999999 matches the widened digit pattern but does not fit an int; it must be a 400
        // like any other malformed id, never a NumberFormatException escaping as a 500.
        request.setParameter("scriptId", "9999999999");
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should report not found when the prescription row does not exist")
    void shouldReturnNotFound_whenPrescriptionMissing() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        when(mockPrescriptionManager.setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, SIGNATURE_ID)).thenReturn(false);

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should clear the prescription signature when signature id is absent")
    void shouldClearPrescriptionSignature_whenSignatureIdIsAbsent() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, null);
    }

    @Test
    @DisplayName("should refuse to touch a prescription belonging to a patient the caller cannot write")
    void shouldRefuseSignatureUpdate_whenPrescriptionBelongsToAnotherPatient() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        // Global _rx write is held (stubbed in setUp) but the right for THIS prescription's patient
        // is not: script ids are small sequential integers, so without the patient-scoped re-check a
        // caller could walk them and sign any patient's prescription.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"),
                eq(String.valueOf(SIGNATURE_DEMOGRAPHIC_NO)))).thenReturn(false);

        assertThatThrownBy(() -> action.saveDigitalSignature())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_rx");

        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should report not found when the script id resolves to no prescription")
    void shouldReturnNotFound_whenScriptIdResolvesToNothing() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        when(mockPrescriptionManager.getPrescription(any(), eq(SCRIPT_ID))).thenReturn(null);

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject malformed digital signature ids")
    void shouldRejectMalformedDigitalSignature_whenIdIsMalformed() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", "7<script>");

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject malformed prescription script ids")
    void shouldRejectMalformedScriptId_whenIdIsMalformed() throws Exception {
        request.setParameter("scriptId", "../123");
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should redirect when prescription session is missing")
    void shouldRedirect_whenPrescriptionSessionIsMissing() throws Exception {
        request.getSession().removeAttribute("RxSessionBean");
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }
}
