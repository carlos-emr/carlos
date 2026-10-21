// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.workflow;

import io.github.carlos_emr.carlos.commn.dao.WorkFlowDao;
import io.github.carlos_emr.carlos.commn.model.WorkFlow;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkFlowStateUnitTest extends CarlosUnitTestBase {
    private WorkFlowDao dao;
    private WorkFlowState state;
    @BeforeEach void setUp() {
        dao = createAndRegisterMock(WorkFlowDao.class);
        state = new WorkFlowState();
    }
    private WorkFlow fixture(Date completion) {
        WorkFlow flow = new WorkFlow();
        flow.setId(77); flow.setWorkflowType("RH"); flow.setProviderNo("999998");
        flow.setDemographicNo("770001"); flow.setCurrentState("2");
        flow.setCreateDateTime(new Date(1000)); flow.setCompletionDate(completion);
        return flow;
    }
    @Test void shouldPersistInitialWorkflow_withProviderPatientAndDueDate() {
        Date due = new Date(2000);
        doAnswer(call -> { ((WorkFlow) call.getArgument(0)).setId(77); return null; }).when(dao).persist(any());
        long before = System.currentTimeMillis();
        assertThat(state.addToWorkFlow("RH", "999998", "770001", due, "1")).isEqualTo(77);
        ArgumentCaptor<WorkFlow> saved = ArgumentCaptor.forClass(WorkFlow.class);
        verify(dao).persist(saved.capture());
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(saved.getValue().getDemographicNo()).isEqualTo("770001");
        assertThat(saved.getValue().getWorkflowType()).isEqualTo("RH");
        assertThat(saved.getValue().getCurrentState()).isEqualTo("1");
        assertThat(saved.getValue().getCompletionDate()).isEqualTo(due);
        assertThat(saved.getValue().getCreateDateTime().getTime()).isBetween(before, System.currentTimeMillis());
    }
    @Test void shouldPreserveDueDate_whenOnlyStateChanges() {
        WorkFlow flow = fixture(new Date(2000)); when(dao.find(77)).thenReturn(flow);
        state.updateWorkFlowState("77", "C");
        assertThat(flow.getCurrentState()).isEqualTo("C");
        assertThat(flow.getCompletionDate()).isEqualTo(new Date(2000)); verify(dao).merge(flow);
    }
    @Test void shouldUpdateDueDate_whenStateAndDateSubmitted() {
        WorkFlow flow = fixture(new Date(2000)); when(dao.find(77)).thenReturn(flow);
        state.updateWorkFlowState("77", "3", new Date(3000));
        assertThat(flow.getCurrentState()).isEqualTo("3");
        assertThat(flow.getCompletionDate()).isEqualTo(new Date(3000)); verify(dao).merge(flow);
    }
    @Test void shouldAvoidWrites_whenWorkflowDoesNotExist() {
        state.updateWorkFlowState("77", "C"); state.updateWorkFlowState("77", "C", new Date());
        verify(dao, never()).merge(any());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldMapAllListFields_withOptionalCompletionDate(boolean complete) {
        WorkFlow flow = fixture(complete ? new Date(2000) : null);
        when(dao.findByWorkflowType("RH")).thenReturn(List.of(flow));
        when(dao.findActiveByWorkflowType("RH")).thenReturn(List.of(flow));
        when(dao.findActiveByWorkflowTypeAndDemographicNo("RH", "770001")).thenReturn(List.of(flow));
        for (List<?> list : List.of(state.getWorkFlowList("RH"), state.getActiveWorkFlowList("RH"),
                state.getActiveWorkFlowList("RH", "770001"))) {
            assertThat(list).hasSize(1);
            Map<?, ?> row = (Map<?, ?>) list.get(0);
            assertThat(row.get("ID")).isEqualTo("77");
            assertThat(row.get("workflow_type")).isEqualTo("RH");
            assertThat(row.get("demographic_no")).isEqualTo("770001");
            assertThat(row.get("current_state")).isEqualTo("2");
            assertThat(row.get("create_date_time")).isEqualTo(flow.getCreateDateTime());
            assertThat(row.containsKey("completion_date")).isEqualTo(complete);
            assertThat(row.get("completion_date")).isEqualTo(flow.getCompletionDate());
        }
        verify(dao).findActiveByWorkflowTypeAndDemographicNo("RH", "770001");
    }
}
