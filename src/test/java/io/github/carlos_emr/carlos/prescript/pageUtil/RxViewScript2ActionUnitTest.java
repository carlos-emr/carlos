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

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxViewScript2Action}: which stash (live or reprinted) the view is built
 * for, when a stash counts as already persisted, and when the stamp is applied. The real
 * {@code saveScript} path needs patient/provider data and is covered by the Playwright check; here
 * every scenario either skips the save or is asserted on the persistence decision directly.
 *
 * @since 2026-09-02
 */
@DisplayName("RxViewScript2Action persistence and stamp decisions")
@Tag("unit")
@Tag("prescript")
class RxViewScript2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final int DEMOGRAPHIC_NO = 42;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private PrescriptionSignatureStampService stampService;
    private PrescriptionDao prescriptionDao;
    private LoggedInInfo loggedInInfo;
    private RxSessionBean liveBean;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/viewScript");
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        stampService = mock(PrescriptionSignatureStampService.class);
        prescriptionDao = mock(PrescriptionDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PrescriptionSignatureStampService.class, stampService);
        registerMock(PrescriptionDao.class, prescriptionDao);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        liveBean = new RxSessionBean();
        liveBean.setProviderNo(PROVIDER_NO);
        liveBean.setDemographicNo(DEMOGRAPHIC_NO);
        RxSessionBeanResolver.register(request.getSession(), liveBean);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
        loggedInInfoMock.close();
    }

    @Test
    @DisplayName("preview keeps the persisted script selected before a concurrent last-card close")
    void shouldResolvePreviewAtomically_whenLastCardCloses() throws Exception {
        try (ConcurrentRxStashClose concurrentBean = new ConcurrentRxStashClose(1)) {
            concurrentBean.setDemographicNo(DEMOGRAPHIC_NO);
            concurrentBean.setProviderNo(PROVIDER_NO);
            RxPrescriptionData.Prescription saved = savedItem(11, "77");
            saved.setRandomId(1);
            concurrentBean.getStashList().add(saved);
            RxSessionBeanResolver.register(request.getSession(), concurrentBean);
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            concurrentBean.armSizeCheck();

            assertThat(new RxViewScript2Action().execute()).isEqualTo("viewScript");
            concurrentBean.awaitCompletion();

            assertThat(request.getAttribute("scriptId")).isEqualTo("77");
            assertThat(concurrentBean.getStashSize()).isZero();
            verifyNoInteractions(stampService);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @DisplayName("explicit saved preview keeps its prescription after another reprint or workspace clear")
    void shouldPinRequestedSavedScript_whenReprintWorkspaceChanges(boolean newerReprint) throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("scriptId", "000123");
        liveBean.getStashList().add(rePrescribedItem("789"));
        RxSessionBean reprintA = new RxSessionBean();
        reprintA.setDemographicNo(DEMOGRAPHIC_NO);
        reprintA.getStashList().add(savedItem(11, "123"));
        RxReprintWorkspace.store(request.getSession(), reprintA, "first comment");
        RxReprintWorkspace.Entry latest = null;
        if (newerReprint) {
            RxSessionBean reprintB = new RxSessionBean();
            reprintB.setDemographicNo(DEMOGRAPHIC_NO);
            reprintB.getStashList().add(savedItem(22, "456"));
            latest = RxReprintWorkspace.store(request.getSession(), reprintB, "second comment");
        } else {
            RxReprintWorkspace.clear(request.getSession(), DEMOGRAPHIC_NO);
        }
        var header = new io.github.carlos_emr.carlos.commn.model.Prescription();
        header.setDemographicId(DEMOGRAPHIC_NO);
        header.setProviderNo(PROVIDER_NO);
        header.setComments("first comment");
        when(prescriptionDao.find(123)).thenReturn(header);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), anyInt())).thenReturn(false);
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class, (mock, context) ->
                when(mock.getPrescriptionsByScriptNo(123, DEMOGRAPHIC_NO))
                        .thenReturn(java.util.List.of(savedItem(11, "123"), savedItem(12, "123"))))) {
            assertThat(newAction().execute()).isEqualTo("viewScript");
            assertThat(data.constructed()).hasSize(1);
        }
        var snapshot = (RxPreviewSnapshot) request.getAttribute(RxPreviewSnapshot.REQUEST_ATTRIBUTE);
        assertThat(snapshot.scriptId()).isEqualTo("123");
        assertThat(snapshot.bean().getStashSize()).isEqualTo(2);
        assertThat(request.getAttribute("scriptId")).isEqualTo("123");
        var pinned = RxReprintWorkspace.findForRequest(request, request.getSession(), DEMOGRAPHIC_NO);
        assertThat(pinned.bean()).isSameAs(snapshot.bean());
        assertThat(pinned.comment()).isEqualTo("first comment");
        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO)).isSameAs(latest);
        assertThat(liveBean.getStashItem(0).getDrugId()).isZero();
        assertThat(liveBean.getStashItem(0).getScript_no()).isEqualTo("789");
        verifyNoInteractions(stampService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"abc", "-1", "0", "2147483648", "12345678901"})
    @DisplayName("invalid explicit saved targets cannot fall through to saving the live draft")
    void shouldRefuseInvalidSavedTarget_whenExplicitScriptIsMalformed(String scriptId) throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("scriptId", scriptId);
        liveBean.getStashList().add(rePrescribedItem("789"));
        assertThat(newAction().execute()).isEqualTo(RxViewScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute(RxPreviewSnapshot.REQUEST_ATTRIBUTE)).isNull();
        assertThat(liveBean.getStashItem(0).getDrugId()).isZero();
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @DisplayName("missing and foreign explicit saved targets cannot reuse the current reprint")
    void shouldRefuseSavedTarget_whenMissingOrOwnedByAnotherPatient(boolean foreign) throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("scriptId", "123");
        RxSessionBean current = new RxSessionBean();
        current.setDemographicNo(DEMOGRAPHIC_NO);
        current.getStashList().add(savedItem(22, "456"));
        var existing = RxReprintWorkspace.store(request.getSession(), current, "current comment");
        if (foreign) {
            var header = new io.github.carlos_emr.carlos.commn.model.Prescription();
            header.setDemographicId(DEMOGRAPHIC_NO + 1);
            when(prescriptionDao.find(123)).thenReturn(header);
        }
        try (var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class)) {
            assertThat(newAction().execute()).isEqualTo(RxViewScript2Action.NONE);
            assertThat(data.constructed()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("scriptId")).isNull();
        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO)).isSameAs(existing);
        verifyNoInteractions(stampService);
    }

    @Test
    @DisplayName("legacy Save And Print literal null still stamps its saved live prescription")
    void shouldUseLiveSavedScript_whenLegacySaveAndPrintSendsNull() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("scriptId", "null");
        liveBean.getStashList().add(savedItem(22, "456"));
        assertThat(newAction().execute()).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("456");
        verify(stampService).applyStampToScript(loggedInInfo, liveBean, "456");
        verifyNoInteractions(prescriptionDao);
    }

    /** A stash item as {@code Prescription.Save} leaves it: a drugs row id and its script number. */
    private static RxPrescriptionData.Prescription savedItem(int drugId, String scriptNo) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(drugId, PROVIDER_NO, DEMOGRAPHIC_NO);
        rx.setScript_no(scriptNo);
        return rx;
    }

    /** A re-prescribed item as {@code newPrescription(.., oldRx)} builds it: no drugs row, old script number. */
    private static RxPrescriptionData.Prescription rePrescribedItem(String oldScriptNo) {
        return savedItem(0, oldScriptNo);
    }

    private RxViewScript2Action newAction() {
        return new RxViewScript2Action(stampService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"GET,true", "HEAD,true", "GET,false", "HEAD,false", "PUT,true", "PUT,false",
            "post,true", "post,false", "PoSt,true", "PoSt,false", "PO\u017fT,true", "PO\u017fT,false"})
    @DisplayName("should never save or stamp prescription state on preview navigation or non-POST requests")
    void shouldKeepPreviewReadOnly_whenRequestIsNotPost(String method, boolean saved) throws Exception {
        request.setMethod(method);
        liveBean.getStashList().add(saved ? savedItem(5, "789") : rePrescribedItem("123"));
        String result = newAction().execute();
        if (saved) {
            assertThat(result).isEqualTo("viewScript");
            assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        } else {
            assertThat(result).isEqualTo(RxViewScript2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(request.getAttribute("scriptId")).isNull();
            assertThat(liveBean.getStashItem(0).getDrugId()).isZero();
        }
        verifyNoInteractions(stampService, prescriptionDao);
        org.mockito.Mockito.verify(securityInfoManager, org.mockito.Mockito.never())
                .hasPrivilege(any(), eq("_rx"), eq("w"), isNull());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "saved={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @DisplayName("should refuse a save/stamp POST with 409 when it names no patient, even with Rx open for the fallback patient")
    void shouldRefuseSaveAndStamp_whenPostNamesNoPatient(boolean saved) throws Exception {
        request.setMethod("POST");
        liveBean.getStashList().add(saved ? savedItem(5, "789") : rePrescribedItem("123"));

        String result = newAction().execute();

        assertThat(result).isEqualTo(RxViewScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("scriptId")).isNull();
        assertThat(liveBean.getStashItem(0).getDrugId()).isEqualTo(saved ? 5 : 0);
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should refuse a save/stamp POST with 409 when it names a different patient")
    void shouldRefuseSaveAndStamp_whenPostNamesOtherPatient() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO + 1));
        liveBean.getStashList().add(savedItem(5, "789"));

        String result = newAction().execute();

        // The other patient has no Rx open, so nothing resolves at all.
        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should reject a caller without _rx read before touching the stash")
    void shouldThrow_whenCallerLacksRxRead() {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        RxViewScript2Action action = newAction();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_rx)");
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should redirect to the error page when there is no Rx session")
    void shouldRedirect_whenRxSessionMissing() throws Exception {
        request.getSession().removeAttribute(RxSessionBeanResolver.BEANS_ATTRIBUTE);

        assertThat(newAction().execute()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should render an empty prescription without persisting or stamping for a read-only caller")
    void shouldRenderEmptyStash_withoutCreatingPrescription() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(false);

        assertThat(newAction().execute()).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isNull();
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(prescriptionDao, stampService);
        verify(securityInfoManager, org.mockito.Mockito.never()).hasPrivilege(any(), eq("_rx"), eq("w"), isNull());
    }

    @Test
    @DisplayName("should reuse a fully persisted stash and stamp that script without saving again")
    void shouldReusePersistedScript_whenEveryStashItemIsSaved() throws Exception {
        request.setMethod("POST");
        // The save/print POST names its window's patient (#3875).
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(savedItem(6, "789"));
        when(stampService.applyStampToScript(loggedInInfo, liveBean, "789")).thenReturn(77);

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isEqualTo(Boolean.TRUE);
        verify(stampService).applyStampToScript(loggedInInfo, liveBean, "789");
        verifyNoInteractions(prescriptionDao); // no second saveScript
    }

    @Test
    @DisplayName("should refuse to persist an unsaved stash for a caller with only _rx read")
    void shouldThrow_whenUnsavedStashAndCallerLacksRxWrite() {
        request.setMethod("POST");
        // The save/print POST names its window's patient (#3875).
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(false);
        liveBean.getStashList().add(rePrescribedItem("123")); // drugId 0: not yet persisted

        RxViewScript2Action action = newAction();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_rx)");
        verifyNoInteractions(stampService, prescriptionDao);
        assertThat(request.getAttribute("scriptId")).isNull();
    }

    @Test
    @DisplayName("should show but not stamp a saved script when the caller may read but not write this patient")
    void shouldSkipStamp_whenPatientLevelWriteDenied() throws Exception {
        // Global _rx write is held; patient-level write for this chart is not (#3908).
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), eq(DEMOGRAPHIC_NO))).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "POST"})
    @DisplayName("should refuse the script view for a patient whose record the caller may not open")
    void shouldRefuseView_whenPatientRecordAccessDenied(String httpMethod) {
        // The view reads the patient's prescriptions: patient-level _rx read and record access (#3908).
        request.setMethod(httpMethod);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        RxViewScript2Action action = newAction();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verifyNoInteractions(stampService, prescriptionDao);
        assertThat(request.getAttribute("scriptId")).isNull();
    }

    @Test
    @DisplayName("should not stamp when the caller has only _rx read")
    void shouldSkipStamp_whenCallerLacksRxWrite() throws Exception {
        request.setMethod("POST");
        // The save/print POST names its window's patient (#3875).
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(stampService);
    }

    @Test
    @DisplayName("should neither save nor stamp the live stash while the session is in reprint mode")
    void shouldSkipSaveAndStamp_whenSessionIsInReprintMode() throws Exception {
        // reprint2 leaves the reprinted script in this patient's reprint workspace; the live stash
        // still holds an UNSAVED re-prescription carrying the historical script number 123.
        liveBean.getStashList().add(rePrescribedItem("123"));
        RxSessionBean reprinted = new RxSessionBean();
        reprinted.setProviderNo(PROVIDER_NO);
        reprinted.setDemographicNo(DEMOGRAPHIC_NO);
        reprinted.getStashList().add(savedItem(9, "456"));
        RxReprintWorkspace.store(request.getSession(), reprinted, "");

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        // The view is built for the REPRINTED script, and script 123 is left exactly as it was.
        assertThat(request.getAttribute("scriptId")).isEqualTo("456");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(stampService, prescriptionDao);
        assertThat(liveBean.getStashItem(0).getDrugId()).isZero();
    }

    @Test
    @DisplayName("should render this patient's own script when only another patient is being reprinted")
    void shouldIgnoreReprint_whenItBelongsToAnotherPatient() throws Exception {
        // Two Rx windows: a reprint loaded in patient 43's window used to switch every window of the
        // session into reprint mode and render 43's script here (#3908).
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        liveBean.getStashList().add(savedItem(5, "789"));
        RxSessionBean otherPatientsReprint = new RxSessionBean();
        otherPatientsReprint.setProviderNo(PROVIDER_NO);
        otherPatientsReprint.setDemographicNo(DEMOGRAPHIC_NO + 1);
        otherPatientsReprint.getStashList().add(savedItem(9, "456"));
        RxReprintWorkspace.store(request.getSession(), otherPatientsReprint, "other patient's comment");

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO + 1)).isNotNull();
    }

    @Test
    @DisplayName("should keep each patient's reprint separate and clear only the named patient")
    void shouldScopeReprintState_perPatient() {
        RxSessionBean first = new RxSessionBean();
        first.setDemographicNo(DEMOGRAPHIC_NO);
        RxSessionBean second = new RxSessionBean();
        second.setDemographicNo(DEMOGRAPHIC_NO + 1);

        RxReprintWorkspace.store(request.getSession(), first, "first");
        RxReprintWorkspace.store(request.getSession(), second, null);

        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO).bean()).isSameAs(first);
        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO).comment()).isEqualTo("first");
        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO + 1).comment()).isEmpty();

        RxReprintWorkspace.clear(request.getSession(), DEMOGRAPHIC_NO);

        assertThat(RxReprintWorkspace.isReprinting(request.getSession(), DEMOGRAPHIC_NO)).isFalse();
        assertThat(RxReprintWorkspace.isReprinting(request.getSession(), DEMOGRAPHIC_NO + 1)).isTrue();
        assertThat(RxReprintWorkspace.isReprinting(null, DEMOGRAPHIC_NO + 1)).isFalse();
        assertThat(RxReprintWorkspace.isReprinting(request.getSession(), null)).isFalse();
    }

    @Test
    @DisplayName("should not mistake an unsaved re-prescribed stash for its original script")
    void shouldNotTreatStashAsPersisted_whenItemsCarryOldScriptNoButNoDrugRow() {
        // Every item shares a positive script_no (copied from the re-prescribed script), yet none
        // has a drugs row: this stash must be SAVED, never reused as script 123.
        liveBean.getStashList().add(rePrescribedItem("123"));
        liveBean.getStashList().add(rePrescribedItem("123"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should not treat a stash as persisted when any item is unsaved")
    void shouldNotTreatStashAsPersisted_whenOneItemLacksDrugRow() {
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(rePrescribedItem("789"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should not treat a stash split across scripts as persisted")
    void shouldNotTreatStashAsPersisted_whenItemsSpanScripts() {
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(savedItem(6, "790"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should treat a stash as persisted only for a positive int script number")
    void shouldNotTreatStashAsPersisted_whenScriptNoIsNotPositiveInt() {
        for (String bad : new String[] {null, "", "0", "-1", "abc", "99999999999", "4294967296"}) {
            RxSessionBean bean = new RxSessionBean();
            bean.getStashList().add(savedItem(5, bad));
            assertThat(RxViewScript2Action.persistedScriptId(bean)).as("script_no %s", bad).isNull();
        }
        RxSessionBean tenDigits = new RxSessionBean();
        tenDigits.getStashList().add(savedItem(5, "2000000000"));
        assertThat(RxViewScript2Action.persistedScriptId(tenDigits)).isEqualTo("2000000000");
    }

    @Test
    @DisplayName("should return null for an empty stash")
    void shouldNotTreatStashAsPersisted_whenEmpty() {
        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
        // and a stash item whose fields were never set is unsaved too
        RxPrescriptionData.Prescription blank = new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO);
        ReflectionTestUtils.setField(blank, "script_no", null);
        liveBean.getStashList().add(blank);
        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }
}
