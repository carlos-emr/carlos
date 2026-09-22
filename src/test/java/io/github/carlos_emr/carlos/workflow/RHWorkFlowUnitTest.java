// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.workflow;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RHWorkFlowUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"1,No Appt made", "2,Appt Booked", "3,Injection 28", "4,Requires Another Injection", "5,Missed Appt", "C,Closed", "unknown,None"})
    void shouldDescribeState_whenWorkflowRendered(String state, String label) {
        assertThat(new RHWorkFlow().getState(state)).isEqualTo(label);
    }
    @Test void shouldCreateInitialRhWorkflow_withOriginalPatientAndDueDate() {
        Date due = new Date(2000);
        try (MockedConstruction<WorkFlowState> construction = mockConstruction(WorkFlowState.class,
                (mock, context) -> when(mock.addToWorkFlow("RH", "999998", "770001", due, "1")).thenReturn(77))) {
            assertThat(new RHWorkFlow().addToWorkFlow("999998", "770001", due)).isEqualTo(77);
            verify(construction.constructed().get(0)).addToWorkFlow("RH", "999998", "770001", due, "1");
        }
    }
    @Test void shouldPreservePatientFilter_whenRetrievingActiveWorkflows() {
        List<Map<String, Object>> expected = new ArrayList<>();
        try (MockedConstruction<WorkFlowState> construction = mockConstruction(WorkFlowState.class,
                (mock, context) -> when(mock.getActiveWorkFlowList("RH", "770001")).thenReturn(expected))) {
            assertThat(new RHWorkFlow().getActiveWorkFlowList("770001")).isSameAs(expected);
            verify(construction.constructed().get(0)).getActiveWorkFlowList("RH", "770001");
        }
    }
    @Test void shouldReturnRuleEvaluation_whenDecisionSupportSucceeds() throws Exception {
        WorkFlowDS engine = mock(WorkFlowDS.class);
        when(engine.getMessages(any())).thenAnswer(call -> {
            WorkFlowInfo info = call.getArgument(0);
            info.setColour("yellow");
            return info;
        });
        Hashtable<String, Object> input = new Hashtable<>();
        input.put("current_state", "2");
        input.put("ID", "77");
        WorkFlowInfo result = new RHWorkFlow().executeRules(engine, input);
        assertThat(result.getID()).isEqualTo("77");
        assertThat(result.getCurrentState()).isEqualTo("2");
        assertThat(result.getColour()).isEqualTo("yellow");
        verify(engine).getMessages(any(WorkFlowInfo.class));
    }
}
