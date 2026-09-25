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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RxWriteScript2Action}.
 *
 * @since 2026-05-31
 */
@DisplayName("RxWriteScript2Action Tests")
@Tag("integration")
@Tag("prescription")
class RxWriteScript2ActionIntegrationTest extends CarlosWebTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private DrugDao mockDrugDao;

    @Mock
    private RxManager mockRxManager;

    private RxWriteScript2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(DrugDao.class, mockDrugDao);
        replaceSpringUtilsBean(RxManager.class, mockRxManager);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
        // MockHttpServletRequest defaults to no method; the mutating entry points require POST.
        mockRequest.setMethod("POST");
        action = new RxWriteScript2Action();
    }

    @Test
    @DisplayName("should reject long term status update when drug belongs to another demographic")
    void shouldRejectLongTermStatusUpdate_whenDrugBelongsToAnotherDemographic() throws Exception {
        int requestedDemographicNo = 1001;
        int drugOwnerDemographicNo = 2002;
        int drugId = 3003;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(requestedDemographicNo);
        RxSessionBeanResolver.register(getMockSession(), bean);
        // Name the bean's own patient: the long-term toggle requires it (#3875), so the drug
        // ownership check below is what refuses this request.
        addRequestParameter("demographicNo", String.valueOf(requestedDemographicNo));
        addRequestParameter("ltDrugId", String.valueOf(drugId));
        addRequestParameter("isLongTerm", "true");

        Drug drug = new Drug();
        drug.setId(drugId);
        drug.setProviderNo("999998");
        drug.setDemographicId(drugOwnerDemographicNo);
        drug.setSpecial("Take one tablet daily");
        drug.setScriptNo(4004);
        when(mockDrugDao.find(drugId)).thenReturn(drug);

        String result = executeActionMethod(action, "updateLongTermStatus");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(mockDrugDao, never()).persist(any(Drug.class));
        verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), any(String.class));
    }

    @Test
    @DisplayName("should update long term status when drug belongs to current demographic")
    void shouldUpdateLongTermStatus_whenDrugBelongsToCurrentDemographic() throws Exception {
        int demographicNo = 1001;
        int drugId = 3003;
        int scriptNo = 4004;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        RxSessionBeanResolver.register(getMockSession(), bean);
        // The long-term toggle only acts for the patient the request names (#3875).
        addRequestParameter("demographicNo", String.valueOf(demographicNo));
        addRequestParameter("ltDrugId", String.valueOf(drugId));
        addRequestParameter("isLongTerm", "true");

        Drug drug = new Drug();
        drug.setId(drugId);
        drug.setProviderNo("999998");
        drug.setDemographicId(demographicNo);
        drug.setSpecial("Take one tablet daily");
        drug.setScriptNo(scriptNo);
        when(mockDrugDao.find(drugId)).thenReturn(drug);
        when(mockDrugDao.getMaxPosition(demographicNo)).thenReturn(0);
        doAnswer(invocation -> {
            Drug savedDrug = invocation.getArgument(0);
            savedDrug.setId(5005);
            return null;
        }).when(mockDrugDao).persist(any(Drug.class));
        when(mockRxManager.archiveDrug(
                any(),
                eq(drugId),
                eq(demographicNo),
                eq(Drug.ARCHIVED_REASON_LT_ENABLED))).thenReturn(true);

        String result = executeActionMethod(action, "updateLongTermStatus");

        // JSON is written directly, so Struts processing ends with NONE rather than a bare null.
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        JsonNode responseBody = OBJECT_MAPPER.readTree(getMockResponse().getContentAsString());
        assertThat(responseBody.get("success").asBoolean()).isTrue();

        ArgumentCaptor<Drug> savedDrug = ArgumentCaptor.forClass(Drug.class);
        verify(mockDrugDao).persist(savedDrug.capture());
        assertThat(savedDrug.getValue().getDemographicId()).isEqualTo(demographicNo);
        assertThat(savedDrug.getValue().isLongTerm()).isTrue();
        assertThat(savedDrug.getValue().getShortTerm()).isFalse();
        verify(mockRxManager).archiveDrug(
                any(),
                eq(drugId),
                eq(demographicNo),
                eq(Drug.ARCHIVED_REASON_LT_ENABLED));
    }

    // Re-prescribe staging and archival (cross-patient guards)
    @Test
    @DisplayName("should reject re-Rx staging when drug belongs to another demographic")
    void shouldRejectReRxStaging_whenDrugBelongsToAnotherDemographic() throws Exception {
        int sessionDemographicNo = 1001;
        int drugOwnerDemographicNo = 2002;
        int drugId = 3003;

        RxSessionBean bean = stageReRxRequest(sessionDemographicNo, "addToReRxDrugIdList", String.valueOf(drugId));
        when(mockDrugDao.find(drugId)).thenReturn(drugOwnedBy(drugId, drugOwnerDemographicNo));

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
    }

    @Test
    @DisplayName("should stage re-Rx drug when drug belongs to current demographic")
    void shouldStageReRxDrug_whenDrugBelongsToCurrentDemographic() throws Exception {
        int demographicNo = 1001;
        int drugId = 3003;

        RxSessionBean bean = stageReRxRequest(demographicNo, "addToReRxDrugIdList", String.valueOf(drugId));
        when(mockDrugDao.find(drugId)).thenReturn(drugOwnedBy(drugId, demographicNo));

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isNull();
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(bean.getReRxDrugIdList()).containsExactly(String.valueOf(drugId));
    }

    @Test
    @DisplayName("should keep the cursor on the same card when a ReRx card before it is removed")
    void shouldKeepCursorOnSameCard_whenReRxCardBeforeCursorRemoved() throws Exception {
        // removeFromReRxDrugIdList removed the card with an iterator, bypassing removeStashItem's
        // cursor adjustment, so the cursor then pointed one card too far (#3908).
        RxSessionBean bean = stageReRxRequest(1001, "removeFromReRxDrugIdList", "3003");
        bean.addReRxDrugIdList("3003");
        RxPrescriptionData.Prescription reRxCard = new RxPrescriptionData.Prescription(0, "999998", 1001);
        reRxCard.setDrugReferenceId(3003);
        RxPrescriptionData.Prescription second = new RxPrescriptionData.Prescription(0, "999998", 1001);
        RxPrescriptionData.Prescription edited = new RxPrescriptionData.Prescription(0, "999998", 1001);
        bean.getStashList().add(reRxCard);
        bean.getStashList().add(second);
        bean.getStashList().add(edited);
        bean.setStashIndex(2);

        executeActionMethod(action, "updateReRxDrug");

        assertThat(bean.getStashList()).containsExactly(second, edited);
        assertThat(bean.getStashIndex()).isEqualTo(1);
        assertThat(bean.getCurrentStashItem()).isSameAs(edited);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
    }

    @Test
    @DisplayName("should reject re-Rx staging when drug id is malformed")
    void shouldRejectReRxStaging_whenDrugIdMalformed() throws Exception {
        RxSessionBean bean = stageReRxRequest(1001, "addToReRxDrugIdList", "not-a-number");

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        verify(mockDrugDao, never()).find(anyInt());
    }

    @Test
    @DisplayName("should reject re-Rx staging when drug does not exist")
    void shouldRejectReRxStaging_whenDrugNotFound() throws Exception {
        int drugId = 3003;

        RxSessionBean bean = stageReRxRequest(1001, "addToReRxDrugIdList", String.valueOf(drugId));
        when(mockDrugDao.find(drugId)).thenReturn(null);

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
    }

    @Test
    @DisplayName("should not archive drug when staged drug belongs to another demographic")
    void shouldNotArchiveDrug_whenStagedDrugBelongsToAnotherDemographic() {
        int demographicNo = 1001;
        int ownedDrugId = 3003;
        int foreignDrugId = 4004;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.getReRxDrugIdList().add(String.valueOf(foreignDrugId));
        bean.getReRxDrugIdList().add(String.valueOf(ownedDrugId));

        // archiveDrug() rejects the drug owned by another patient and accepts the current one.
        when(mockRxManager.archiveDrug(any(), eq(foreignDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(false);
        when(mockRxManager.archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(true);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(foreignDrugId, ownedDrugId),
                    "127.0.0.1", "audit");

            verifyNotAudited(logAction, foreignDrugId);
            verifyAudited(logAction, ownedDrugId);
        }

        // A cross-patient rejection must not stop the remaining staged drugs being archived.
        verify(mockRxManager).archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @Test
    @DisplayName("should archive staged drugs when all belong to current demographic")
    void shouldArchiveStagedDrugs_whenAllBelongToCurrentDemographic() {
        int demographicNo = 1001;
        int firstDrugId = 3003;
        int secondDrugId = 4004;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.getReRxDrugIdList().add(String.valueOf(firstDrugId));
        bean.getReRxDrugIdList().add(String.valueOf(secondDrugId));

        when(mockRxManager.archiveDrug(any(), anyInt(), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(true);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(firstDrugId, secondDrugId),
                    "127.0.0.1", "audit");

            verifyAudited(logAction, firstDrugId);
            verifyAudited(logAction, secondDrugId);
        }

        verify(mockRxManager).archiveDrug(any(), eq(firstDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
        verify(mockRxManager).archiveDrug(any(), eq(secondDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
        // Archival goes through the manager, never a direct row mutation.
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @Test
    @DisplayName("should skip archival when staged drug id is malformed")
    void shouldSkipArchival_whenStagedDrugIdMalformed() {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1001);
        bean.getReRxDrugIdList().add("not-a-number");

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(), "127.0.0.1", "audit");

            // A skipped drug is never archived, so it must not appear in the audit trail either.
            logAction.verifyNoInteractions();
        }

        verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), any(String.class));
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @Test
    @DisplayName("should reject re-Rx update when action parameter is missing")
    void shouldRejectReRxUpdate_whenActionMissing() throws Exception {
        RxSessionBean bean = stageReRxSession(1001);
        addRequestParameter("demographicNo", "1001");
        addRequestParameter("reRxDrugId", "3003");

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        // Rejection happens before any lookup, so the drug is never read.
        verify(mockDrugDao, never()).find(anyInt());
    }

    @Test
    @DisplayName("should reject re-Rx update when action is unrecognized")
    void shouldRejectReRxUpdate_whenActionUnrecognized() throws Exception {
        RxSessionBean bean = stageReRxRequest(1001, "bogusAction", "3003");

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        verify(mockDrugDao, never()).find(anyInt());
    }

    @Test
    @DisplayName("should reject re-Rx update when request method is GET")
    void shouldRejectReRxUpdate_whenMethodIsGet() throws Exception {
        RxSessionBean bean = stageReRxSession(1001);
        addRequestParameter("action", "addToReRxDrugIdList");
        addRequestParameter("reRxDrugId", "3003");
        mockRequest.setMethod("GET");

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        // CSRFGuard does not cover GET, so the rejection has to land before anything is staged.
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        verify(mockDrugDao, never()).find(anyInt());
    }

    @Test
    @DisplayName("should reject re-Rx update when request method is HEAD")
    void shouldRejectReRxUpdate_whenMethodIsHead() throws Exception {
        RxSessionBean bean = stageReRxSession(1001);
        addRequestParameter("action", "addToReRxDrugIdList");
        addRequestParameter("reRxDrugId", "3003");
        mockRequest.setMethod("HEAD");

        String result = executeActionMethod(action, "updateReRxDrug");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(bean.getReRxDrugIdList()).isEmpty();
        verify(mockDrugDao, never()).find(anyInt());
    }

    @Test
    @DisplayName("should not stage a re-Rx drug on the fallback patient when the request names none")
    void shouldRefuseReRxStaging_whenRequestNamesNoPatient() throws Exception {
        int drugId = 3003;
        RxSessionBean beanA = stageReRxSession(1001);
        // Patient B's Rx page was opened last, so B is the no-patient fallback.
        RxSessionBean beanB = stageReRxSession(2002);
        addRequestParameter("action", "addToReRxDrugIdList");
        addRequestParameter("reRxDrugId", String.valueOf(drugId));
        when(mockDrugDao.find(drugId)).thenReturn(drugOwnedBy(drugId, 2002));

        executeActionMethod(action, "updateReRxDrug");

        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(getMockResponse().getRedirectedUrl()).isNull();
        assertThat(beanA.getReRxDrugIdList()).isEmpty();
        assertThat(beanB.getReRxDrugIdList()).isEmpty();
    }

    @Test
    @DisplayName("should not restage drug when it is already staged")
    void shouldNotRestage_whenDrugAlreadyStaged() throws Exception {
        int demographicNo = 1001;
        int drugId = 3003;

        RxSessionBean bean = stageReRxRequest(demographicNo, "addToReRxDrugIdList", String.valueOf(drugId));
        bean.getReRxDrugIdList().add(String.valueOf(drugId));

        String result = executeActionMethod(action, "updateReRxDrug");

        // A double-click must not 403: the name is valid, only the list state makes it a no-op.
        assertThat(result).isNull();
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(bean.getReRxDrugIdList()).containsExactly(String.valueOf(drugId));
    }

    @Test
    @DisplayName("should skip archival when staged drug id is null")
    void shouldSkipArchival_whenStagedDrugIdNull() {
        int demographicNo = 1001;
        int ownedDrugId = 3003;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.getReRxDrugIdList().add(null);
        bean.getReRxDrugIdList().add(String.valueOf(ownedDrugId));

        when(mockRxManager.archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(true);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(ownedDrugId), "127.0.0.1", "audit");

            verifyAudited(logAction, ownedDrugId);
        }

        // The null entry must be stepped over: throwing here would strand every later drug.
        verify(mockRxManager).archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    // #3869: an empty save, or a ticked-but-unstaged ReRx, must never archive the source drug.
    @Test
    @DisplayName("should not archive a staged re-Rx drug that was not re-prescribed in this save")
    void shouldNotArchiveReRxDrug_whenNotRePrescribedInThisSave() {
        int demographicNo = 1001;
        int tickedOnlyDrugId = 3003;
        int savedDrugId = 4004;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.getReRxDrugIdList().add(String.valueOf(tickedOnlyDrugId));
        bean.getReRxDrugIdList().add(String.valueOf(savedDrugId));

        when(mockRxManager.archiveDrug(any(), anyInt(), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(true);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(savedDrugId), "127.0.0.1", "audit");

            verifyNotAudited(logAction, tickedOnlyDrugId);
            verifyAudited(logAction, savedDrugId);
        }

        verify(mockRxManager, never()).archiveDrug(any(), eq(tickedOnlyDrugId), anyInt(), any(String.class));
        verify(mockRxManager).archiveDrug(any(), eq(savedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
    }

    @Test
    @DisplayName("should keep archiving later drugs when archiveDrug throws for one of them")
    void shouldContinueArchival_whenArchiveDrugThrows() {
        int demographicNo = 1001;
        int deniedDrugId = 3003;
        int ownedDrugId = 4004;

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.getReRxDrugIdList().add(String.valueOf(deniedDrugId));
        bean.getReRxDrugIdList().add(String.valueOf(ownedDrugId));

        when(mockRxManager.archiveDrug(any(), eq(deniedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenThrow(new RuntimeException("denied"));
        when(mockRxManager.archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED)))
                .thenReturn(true);

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.archiveReRxDrugs(mockLoggedInInfo, bean, Set.of(deniedDrugId, ownedDrugId),
                    "127.0.0.1", "audit");

            verifyNotAudited(logAction, deniedDrugId);
            verifyAudited(logAction, ownedDrugId);
        }
    }

    @Test
    @DisplayName("should keep the pending reprint when the submission names no staged card")
    void shouldKeepReprint_whenSaveIsEmpty() throws Exception {
        // An empty or stale save is refused as a no-op; it must not end the reprint the prescriber
        // still has open for this patient (Copilot review on #3908).
        RxSessionBean bean = stageReRxSession(1001);
        RxSessionBean reprint = new RxSessionBean();
        reprint.setDemographicNo(1001);
        RxReprintWorkspace.store(getMockSession(), reprint, "reprint comment");
        addRequestParameter("demographicNo", "1001");

        String result = executeActionMethod(action, "updateSaveAllDrugs");

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(RxReprintWorkspace.find(getMockSession(), 1001)).isNotNull();
        assertThat(bean.getStashSize()).isZero();
    }

    @Test
    @DisplayName("should neither save nor archive when the stash is empty")
    void shouldSkipSaveAndArchival_whenStashEmpty() {
        RxSessionBean bean = stageReRxSession(1001);
        bean.getReRxDrugIdList().add("3003");
        addRequestParameter("demographicNo", "1001");

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            action.saveDrug(getMockRequest());

            logAction.verifyNoInteractions();
        }

        verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), any(String.class));
        verify(mockDrugDao, never()).merge(any(Drug.class));
        assertThat(getMockRequest().getAttribute("scriptId")).isNull();
        // The ReRx selection survives so the prescriber can still stage it.
        assertThat(bean.getReRxDrugIdList()).containsExactly("3003");
    }

    @Test
    @DisplayName("should archive the re-prescribed sources once and clear the ReRx list when the stash is persisted")
    void shouldArchiveSourcesAndClearReRxList_whenStashPersisted() {
        // persistStash is the one persistence every save path uses (updateSaveAllDrugs,
        // updateAndPrint, the write-script fallback), so the ReRx invariant holds on all of them,
        // and a second save from the same window cannot archive the sources again (#3908).
        RxSessionBean bean = stageReRxSession(1001);
        bean.setProviderNo("999998");
        bean.getReRxDrugIdList().add("3003");
        bean.getReRxDrugIdList().add("4004"); // ticked but never staged: stays active
        RxPrescriptionData.Prescription replacement = new RxPrescriptionData.Prescription(0, "999998", 1001);
        replacement.setDrugReferenceId(3003);
        replacement.setBrandName("SOURCE DRUG");
        bean.getStashList().add(replacement);
        doAnswer(invocation -> {
            Drug savedDrug = invocation.getArgument(0);
            savedDrug.setId(5005);
            return null;
        }).when(mockDrugDao).persist(any(Drug.class));
        when(mockRxManager.archiveDrug(any(), eq(3003), eq(1001), eq(Drug.REPRESCRIBED))).thenReturn(true);

        String scriptId;
        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class);
             var _ =
                     org.mockito.Mockito.mockConstruction(io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.class,
                             (mock, context) -> when(mock.saveScript(any(), any())).thenReturn("77"))) {
            scriptId = action.persistStash(mockLoggedInInfo, bean);
            verifyAudited(logAction, 3003);
        }

        assertThat(scriptId).isEqualTo("77");
        assertThat(replacement.getScript_no()).isEqualTo("77");
        verify(mockRxManager).archiveDrug(any(), eq(3003), eq(1001), eq(Drug.REPRESCRIBED));
        verify(mockRxManager, never()).archiveDrug(any(), eq(4004), anyInt(), any(String.class));
        assertThat(bean.getReRxDrugIdList()).isEmpty();
    }

    // #3875: a save must name the prescribing window's patient; the no-patient fallback is read-only.
    @Test
    @DisplayName("should refuse a save that names no patient even though a fallback bean exists")
    void shouldRefuseSave_whenRequestNamesNoPatient() throws Exception {
        RxSessionBean beanA = stageReRxSession(1001);
        beanA.getReRxDrugIdList().add("3003");
        RxSessionBean beanB = stageReRxSession(2002);
        beanB.getReRxDrugIdList().add("4004");

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            String result = executeActionMethod(action, "updateSaveAllDrugs");

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            logAction.verifyNoInteractions();
        }
        verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), any(String.class));
        assertThat(beanB.getReRxDrugIdList()).containsExactly("4004");
    }

    @Test
    @DisplayName("should refuse a save for a patient whose Rx window is not open in this session")
    void shouldRefuseSave_whenNamedPatientHasNoRxBean() throws Exception {
        stageReRxSession(1001).getReRxDrugIdList().add("3003");
        addRequestParameter("demographicNo", "2002");

        try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
            String result = executeActionMethod(action, "updateSaveAllDrugs");

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            logAction.verifyNoInteractions();
        }
        verify(mockRxManager, never()).archiveDrug(any(), anyInt(), anyInt(), any(String.class));
    }

    /**
     * Asserts an archived drug left the full audit trail: re-prescribed by the provider, then
     * discontinued by the system.
     */
    private void verifyAudited(MockedStatic<LogAction> logAction, int drugId) {
        logAction.verify(() -> LogAction.addLog(any(), eq(LogConst.REPRESCRIBE), any(),
                eq("drugid=" + drugId), any(), any(), any()));
        logAction.verify(() -> LogAction.addLog(any(), eq(LogConst.DISCONTINUE), any(),
                eq("drugid=" + drugId), any(), any(), any()));
    }

    /** Asserts a drug that was not archived left no audit trail of either kind. */
    private void verifyNotAudited(MockedStatic<LogAction> logAction, int drugId) {
        logAction.verify(() -> LogAction.addLog(any(), eq(LogConst.REPRESCRIBE), any(),
                eq("drugid=" + drugId), any(), any(), any()), never());
        logAction.verify(() -> LogAction.addLog(any(), eq(LogConst.DISCONTINUE), any(),
                eq("drugid=" + drugId), any(), any(), any()), never());
    }

    /**
     * Puts an Rx session for {@code demographicNo} in place, without any request parameters.
     *
     * @return the session bean, so tests can assert on what did or did not get staged
     */
    private RxSessionBean stageReRxSession(int demographicNo) {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        RxSessionBeanResolver.register(getMockSession(), bean);
        return bean;
    }

    /**
     * Puts an Rx session for {@code demographicNo} in place and wires up the request parameters
     * the re-Rx staging endpoint reads.
     *
     * @return the session bean, so tests can assert on what did or did not get staged
     */
    private RxSessionBean stageReRxRequest(int demographicNo, String reRxAction, String drugId) {
        RxSessionBean bean = stageReRxSession(demographicNo);
        // Staging only acts on the patient the request names (#3875).
        addRequestParameter("demographicNo", String.valueOf(demographicNo));
        addRequestParameter("action", reRxAction);
        addRequestParameter("reRxDrugId", drugId);
        return bean;
    }

    /** Builds a persisted-looking drug row owned by {@code demographicNo}. */
    private Drug drugOwnedBy(int drugId, int demographicNo) {
        Drug drug = new Drug();
        drug.setId(drugId);
        drug.setProviderNo("999998");
        drug.setDemographicId(demographicNo);
        drug.setSpecial("Take one tablet daily");
        drug.setScriptNo(4004);
        return drug;
    }
}
