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
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.PrescriptionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
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
class RxRePrescribe2ActionUnitTest extends CarlosWebTestBase {

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
        RxSessionBeanResolver.register(request.getSession(), rxSessionBean);

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
    @DisplayName("should refuse to stage a re-prescription for a request that names no patient")
    void shouldRefuseReRxStaging_whenRequestNamesNoPatient() throws Exception {
        request.setParameter("drugId", "5");

        String result = action.represcribe2();

        // Falling back to the most recently opened Rx patient would stage the drug in another
        // chart's stash (#3875).
        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        assertThat(RxSessionBeanResolver.find(request.getSession(), 1).getStashSize()).isZero();
    }

    @Test
    @DisplayName("should check _rx write and require a named patient before staging a saved drug")
    void shouldRefuseSaveReRxToStash_whenRequestNamesNoPatient() throws Exception {
        request.setParameter("drugId", "5");

        String result = action.saveReRxDrugIdToStash();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull());
    }

    @Test
    @DisplayName("should keep the staged source drug ids so the save archives the source medication")
    void shouldArchiveSourceDrug_whenReprescribedFromDrugIdsParameter() throws Exception {
        // drugIds path of represcribeMultiple: the request list is the source of truth. The
        // session ReRx list must end up holding exactly the staged, owned source ids, because
        // saveDrug() archives a re-prescribed source only when its id is in that list. Clearing it
        // (the previous behaviour) saved the replacement and left the source active.
        request.setParameter("demographicNo", "1");
        request.setParameter("drugIds", "5,6");
        RxSessionBean bean = RxSessionBeanResolver.find(request.getSession(), 1);
        bean.addReRxDrugIdList("99");

        RxPrescriptionData.Prescription ownSource = new RxPrescriptionData.Prescription(5, "999998", 1);
        ownSource.setBrandName("SOURCE DRUG");
        RxPrescriptionData.Prescription otherPatientsDrug = new RxPrescriptionData.Prescription(6, "999998", 2);
        try (org.mockito.MockedConstruction<RxPrescriptionData> rxData = org.mockito.Mockito.mockConstruction(
                RxPrescriptionData.class, (mock, context) -> {
                    when(mock.getPrescription(5)).thenReturn(ownSource);
                    when(mock.getPrescription(6)).thenReturn(otherPatientsDrug);
                    // Staging preloads interactions from the patient's current ATC codes.
                    when(mock.getCurrentATCCodesByPatient(org.mockito.ArgumentMatchers.anyInt()))
                            .thenReturn(new java.util.Vector<>());
                    when(mock.newPrescription(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                            any(RxPrescriptionData.Prescription.class))).thenAnswer(invocation -> {
                        RxPrescriptionData.Prescription source = invocation.getArgument(2);
                        RxPrescriptionData.Prescription staged =
                                new RxPrescriptionData.Prescription(0, invocation.getArgument(0), invocation.getArgument(1));
                        staged.setBrandName(source.getBrandName());
                        staged.setDrugReferenceId(source.getDrugId());
                        return staged;
                    });
                })) {
            assertThat(action.represcribeMultiple()).isEqualTo("represcribe");
        }

        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashItem(0).getDrugReferenceId()).isEqualTo(5);
        assertThat(bean.getReRxDrugIdList()).containsExactly("5");

        // The save then archives that source, as saveDrug() does with the saved replacements.
        io.github.carlos_emr.carlos.managers.RxManager rxManager =
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.RxManager.class);
        replaceSpringUtilsBean(io.github.carlos_emr.carlos.managers.RxManager.class, rxManager);
        replaceSpringUtilsBean(io.github.carlos_emr.carlos.managers.DemographicManager.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class));
        when(rxManager.archiveDrug(mockLoggedInInfo, 5, 1, io.github.carlos_emr.carlos.commn.model.Drug.REPRESCRIBED))
                .thenReturn(true);
        new RxWriteScript2Action(org.mockito.Mockito.mock(
                io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService.class))
                .archiveReRxDrugs(mockLoggedInInfo, bean, java.util.Set.of(5), "127.0.0.1", "audit");

        verify(rxManager).archiveDrug(mockLoggedInInfo, 5, 1, io.github.carlos_emr.carlos.commn.model.Drug.REPRESCRIBED);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"saveReRxDrugIdToStash", "represcribe2", "represcribe"})
    @DisplayName("should record the ReRx source in the same request that stages its copy")
    void shouldRecordReRxSource_whenStagingSingleReprescription(String method) throws Exception {
        // StaticScript2 used to send addToReRxDrugIdList as a separate, un-awaited request; losing
        // that race left the source unarchived after the replacement was saved (#3908).
        request.setParameter("demographicNo", "1");
        request.setParameter("drugId", "5");
        RxSessionBean bean = RxSessionBeanResolver.find(request.getSession(), 1);
        RxPrescriptionData.Prescription ownSource = new RxPrescriptionData.Prescription(5, "999998", 1);
        ownSource.setBrandName("SOURCE DRUG");
        try (org.mockito.MockedConstruction<RxPrescriptionData> rxData = stagingData(ownSource)) {
            if ("represcribe2".equals(method)) {
                action.represcribe2();
            } else if ("represcribe".equals(method)) {
                // The legacy rx/rePrescribe form path stages the ids posted in drugList.
                action.setDrugList("5");
                action.represcribe();
            } else {
                action.saveReRxDrugIdToStash();
            }
        }

        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashItem(0).getDrugReferenceId()).isEqualTo(5);
        assertThat(bean.getReRxDrugIdList()).containsExactly("5");
    }

    @Test
    @DisplayName("should neither stage nor record another patient's drug as a ReRx source")
    void shouldNotRecordReRxSource_whenSourceBelongsToAnotherPatient() throws Exception {
        request.setParameter("demographicNo", "1");
        request.setParameter("drugId", "6");
        RxSessionBean bean = RxSessionBeanResolver.find(request.getSession(), 1);
        RxPrescriptionData.Prescription otherPatientsDrug = new RxPrescriptionData.Prescription(6, "999998", 2);
        try (org.mockito.MockedConstruction<RxPrescriptionData> rxData = stagingData(otherPatientsDrug)) {
            assertThat(action.saveReRxDrugIdToStash()).isEqualTo(ActionSupport.NONE);
        }

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getStashSize()).isZero();
        assertThat(bean.getReRxDrugIdList()).isEmpty();
    }

    private static org.mockito.MockedConstruction<RxPrescriptionData> stagingData(RxPrescriptionData.Prescription source) {
        return org.mockito.Mockito.mockConstruction(RxPrescriptionData.class, (mock, context) -> {
            when(mock.getPrescription(source.getDrugId())).thenReturn(source);
            when(mock.getCurrentATCCodesByPatient(org.mockito.ArgumentMatchers.anyInt())).thenReturn(new java.util.Vector<>());
            when(mock.newPrescription(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                    any(RxPrescriptionData.Prescription.class))).thenAnswer(invocation -> {
                RxPrescriptionData.Prescription from = invocation.getArgument(2);
                RxPrescriptionData.Prescription staged =
                        new RxPrescriptionData.Prescription(0, invocation.getArgument(0), invocation.getArgument(1));
                staged.setBrandName(from.getBrandName());
                staged.setDrugReferenceId(from.getDrugId());
                return staged;
            });
        });
    }

    @Test
    @DisplayName("should only treat a source drug of the Rx window's own patient as re-prescribable")
    void shouldMatchSourceDrugOwner_toBeanPatient() {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);

        assertThat(RxRePrescribe2Action.isOwnedByBeanPatient(
                new RxPrescriptionData.Prescription(5, "999998", 1), bean)).isTrue();
        assertThat(RxRePrescribe2Action.isOwnedByBeanPatient(
                new RxPrescriptionData.Prescription(5, "999998", 2), bean)).isFalse();
        assertThat(RxRePrescribe2Action.isOwnedByBeanPatient(null, bean)).isFalse();
        assertThat(RxRePrescribe2Action.isOwnedByBeanPatient(
                new RxPrescriptionData.Prescription(5, "999998", 1), null)).isFalse();
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
        request.getSession().removeAttribute(RxSessionBeanResolver.BEANS_ATTRIBUTE);
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }
}
