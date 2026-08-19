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
        getMockSession().setAttribute("RxSessionBean", bean);
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
        getMockSession().setAttribute("RxSessionBean", bean);
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

        assertThat(result).isNull();
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
            action.archiveReRxDrugs(mockLoggedInInfo, bean, "127.0.0.1", "audit");

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
            action.archiveReRxDrugs(mockLoggedInInfo, bean, "127.0.0.1", "audit");

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
            action.archiveReRxDrugs(mockLoggedInInfo, bean, "127.0.0.1", "audit");

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
            action.archiveReRxDrugs(mockLoggedInInfo, bean, "127.0.0.1", "audit");

            verifyAudited(logAction, ownedDrugId);
        }

        // The null entry must be stepped over: throwing here would strand every later drug.
        verify(mockRxManager).archiveDrug(any(), eq(ownedDrugId), eq(demographicNo), eq(Drug.REPRESCRIBED));
        verify(mockDrugDao, never()).merge(any(Drug.class));
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
        getMockSession().setAttribute("RxSessionBean", bean);
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
