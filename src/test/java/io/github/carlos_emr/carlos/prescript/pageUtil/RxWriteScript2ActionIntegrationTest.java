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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
}
