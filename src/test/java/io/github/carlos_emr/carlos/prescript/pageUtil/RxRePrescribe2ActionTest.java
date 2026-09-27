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

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
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
    private DigitalSignatureManager mockDigitalSignatureManager;

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
        replaceSpringUtilsBean(DigitalSignatureManager.class, mockDigitalSignatureManager);
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
        targetPrescription.setProviderNo("999998");
        when(mockPrescriptionManager.getPrescription(any(), eq(SCRIPT_ID))).thenReturn(targetPrescription);
        DigitalSignature signature = new DigitalSignature();
        signature.setProviderNo("999998");
        signature.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        signature.setModuleType(ModuleType.PRESCRIPTION);
        when(mockDigitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID)).thenReturn(signature);
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

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("X-Carlos-Signature-Write")).isEqualTo("written");
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
        target.setProviderNo("999998");
        when(mockPrescriptionManager.getPrescription(any(), eq(tenDigitScript))).thenReturn(target);
        when(mockPrescriptionManager.setPrescriptionSignature(any(), eq(tenDigitScript), any())).thenReturn(true);
        request.setParameter("scriptId", String.valueOf(tenDigitScript));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("X-Carlos-Signature-Write")).isEqualTo("written");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, tenDigitScript, SIGNATURE_ID);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing", "wrong-module", "wrong-patient", "null-module", "null-patient"})
    @DisplayName("should independently reject missing or incorrectly bound signature metadata")
    void shouldRejectInvalidSignatureMetadata_withoutWriting(String scenario) throws Exception {
        DigitalSignature signature = new DigitalSignature();
        signature.setProviderNo("999998");
        signature.setDemographicId("null-patient".equals(scenario) ? null
                : "wrong-patient".equals(scenario) ? SIGNATURE_DEMOGRAPHIC_NO + 1 : SIGNATURE_DEMOGRAPHIC_NO);
        signature.setModuleType("null-module".equals(scenario) ? null
                : "wrong-module".equals(scenario) ? ModuleType.CONSULTATION
                        : ModuleType.PRESCRIPTION);
        when(mockDigitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID))
                .thenReturn("missing".equals(scenario) ? null : signature);
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        assertThat(action.saveDigitalSignature()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("X-Carlos-Signature-Write")).isNull();
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
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
    @DisplayName("should accept the maximum signed-int digital signature id")
    void shouldAcceptDigitalSignatureId_atSignedIntMaximum() throws Exception {
        int tenDigitSignatureId = Integer.MAX_VALUE;
        DigitalSignature signature = new DigitalSignature();
        signature.setProviderNo("999998");
        signature.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        signature.setModuleType(ModuleType.PRESCRIPTION);
        when(mockDigitalSignatureManager.getDigitalSignatureMetadata(tenDigitSignatureId)).thenReturn(signature);
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(tenDigitSignatureId));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("X-Carlos-Signature-Write")).isEqualTo("written");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockPrescriptionManager)
                .setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, tenDigitSignatureId);
    }

    @Test
    @DisplayName("should reject the first digital signature id above the signed-int range")
    void shouldRejectDigitalSignatureId_aboveSignedIntMaximum() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", "2147483648");

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockDigitalSignatureManager, never()).getDigitalSignatureMetadata(any(Integer.class));
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

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("X-Carlos-Signature-Write")).isEqualTo("written");
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
    @DisplayName("should reject a signature captured by another provider")
    void shouldRejectSignature_whenItBelongsToAnotherProvider() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        DigitalSignature foreignSignature = new DigitalSignature();
        foreignSignature.setProviderNo("888888");
        foreignSignature.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        foreignSignature.setModuleType(ModuleType.PRESCRIPTION);
        when(mockDigitalSignatureManager.getDigitalSignatureMetadata(SIGNATURE_ID))
                .thenReturn(foreignSignature);

        assertThat(action.saveDigitalSignature()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject signature replay by a covering provider")
    void shouldRejectSignatureReplay_whenCallerIsNotPrescriber() {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));
        io.github.carlos_emr.carlos.commn.model.Prescription target =
                new io.github.carlos_emr.carlos.commn.model.Prescription();
        target.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        target.setProviderNo("111111");
        when(mockPrescriptionManager.getPrescription(any(), eq(SCRIPT_ID))).thenReturn(target);

        assertThatThrownBy(() -> action.saveDigitalSignature())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("prescriber");

        verify(mockDigitalSignatureManager, never()).getDigitalSignatureMetadata(any(Integer.class));
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject clearing another prescriber's signature")
    void shouldRejectSignatureClear_whenCallerIsNotPrescriber() {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        io.github.carlos_emr.carlos.commn.model.Prescription target =
                new io.github.carlos_emr.carlos.commn.model.Prescription();
        target.setDemographicId(SIGNATURE_DEMOGRAPHIC_NO);
        target.setProviderNo("111111");
        when(mockPrescriptionManager.getPrescription(any(), eq(SCRIPT_ID))).thenReturn(target);

        assertThatThrownBy(() -> action.saveDigitalSignature())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("prescriber");

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
    @Test
    @DisplayName("should reject a missing prescription session instead of returning success")
    void shouldRejectMissingSession_whenStagingHistoryPrescription() throws Exception {
        request.getSession().removeAttribute("RxSessionBean");
        action.saveReRxDrugIdToStash();
        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("should enforce write permission before staging a history prescription")
    void shouldRejectReadOnlyUser_whenStagingHistoryPrescription() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> action.saveReRxDrugIdToStash()).isInstanceOf(RuntimeException.class).hasMessageContaining("_rx");
    }

    @Test
    @DisplayName("should return a client error for a malformed history drug identifier")
    void shouldRejectInvalidDrugId_whenStagingHistoryPrescription() throws Exception {
        request.setParameter("drugId", "invalid");
        action.saveReRxDrugIdToStash();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should return failure when the history prescription cannot be loaded")
    void shouldReportFailure_whenLoadingHistoryPrescriptionFails() throws Exception {
        request.setParameter("drugId", "27");
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class,
                (mock, context) -> when(mock.getPrescription(27)).thenThrow(new IllegalStateException("Synthetic database failure")))) {
            action.saveReRxDrugIdToStash();
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).doesNotContain("Synthetic database failure");
        }
    }

    @Test
    @DisplayName("should reject a history prescription belonging to another patient")
    void shouldRejectOtherPatient_whenStagingHistoryPrescription() throws Exception {
        request.setParameter("drugId", "27");
        var old = org.mockito.Mockito.mock(Prescription.class);
        when(old.getDemographicNo()).thenReturn(2);
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class,
                (mock, context) -> when(mock.getPrescription(27)).thenReturn(old))) {
            action.saveReRxDrugIdToStash();
            assertThat(response.getStatus()).isEqualTo(404);
            verify(data.constructed().getFirst(), never()).newPrescription(anyString(), any(Integer.class), any(Prescription.class));
        }
    }

    @Test
    @DisplayName("should enforce patient-scoped permission before copying a history prescription")
    void shouldRejectPatientWithoutWriteAccess_whenStagingHistoryPrescription() throws Exception {
        request.setParameter("drugId", "27");
        var old = org.mockito.Mockito.mock(Prescription.class);
        when(old.getDemographicNo()).thenReturn(1);
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class,
                (mock, context) -> when(mock.getPrescription(27)).thenReturn(old))) {
            action.saveReRxDrugIdToStash();
            assertThat(response.getStatus()).isEqualTo(403);
            verify(data.constructed().getFirst(), never()).newPrescription(anyString(), any(Integer.class), any(Prescription.class));
        }
    }

    @Test
    @DisplayName("should stage an authorized history prescription and return success")
    void shouldStagePrescription_whenHistoryPatientAndWriteAccessMatch() throws Exception {
        request.setParameter("drugId", "27");
        var bean = org.mockito.Mockito.mock(RxSessionBean.class);
        when(bean.getDemographicNo()).thenReturn(1);
        when(bean.getProviderNo()).thenReturn("999998");
        request.getSession().setAttribute("RxSessionBean", bean);
        var old = org.mockito.Mockito.mock(Prescription.class);
        var staged = org.mockito.Mockito.mock(Prescription.class);
        when(old.getDemographicNo()).thenReturn(1);
        when(staged.getQuantity()).thenReturn("30");
        when(bean.addStashItem(mockLoggedInInfo, staged)).thenReturn(7);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_rx", "w", "1")).thenReturn(true);
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class,
                (mock, context) -> {
                    when(mock.getPrescription(27)).thenReturn(old);
                    when(mock.newPrescription("999998", 1, old)).thenReturn(staged);
                });
             var util = mockStatic(RxUtil.class)) {
            util.when(() -> RxUtil.isStringToNumber("30")).thenReturn(true);
            action.saveReRxDrugIdToStash();
            assertThat(response.getStatus()).isEqualTo(200);
            verify(bean).addStashItem(mockLoggedInInfo, staged);
            verify(bean).setStashIndex(7);
        }
    }

}
