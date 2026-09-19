// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.form.FrmRecord;
import io.github.carlos_emr.carlos.form.FrmRecordFactory;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.workflow.WorkFlow;
import io.github.carlos_emr.carlos.workflow.WorkFlowFactory;
import io.github.carlos_emr.carlos.workflow.WorkFlowState;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Properties;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrmFormRHPrevention2ActionUnitTest extends CarlosUnitTestBase {
    @Test void shouldRejectDeniedWrite_beforeCreatingWorkflowOrSavingForm() {
        createAndRegisterMock(SecurityInfoManager.class);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<WorkFlowFactory> workflows = mockConstruction(WorkFlowFactory.class);
             MockedConstruction<FrmRecordFactory> records = mockConstruction(FrmRecordFactory.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
            assertThatThrownBy(() -> new FrmFormRHPrevention2Action().execute()).isInstanceOf(SecurityException.class);
            assertThat(workflows.constructed()).isEmpty(); assertThat(records.constructed()).isEmpty();
        }
    }
    @Test void shouldSaveOwnedWorkflowAndSessionProvider() throws Exception {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "17");
        request.setParameter("provider_no", "forged"); request.setParameter("state", "2");
        WorkFlow flow = mock(WorkFlow.class); FrmRecord record = mock(FrmRecord.class);
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>(java.util.List.of(row)));
        when(record.saveFormRecord(any())).thenReturn(32);
        when(record.createActionURL("success", "save", "770001", "32")).thenReturn("success");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowFactory> workflows = mockConstruction(WorkFlowFactory.class,
               (mock, context) -> when(mock.getWorkFlow("RH")).thenReturn(flow));
             MockedConstruction<WorkFlowState> states = mockConstruction(WorkFlowState.class);
             MockedConstruction<FrmRecordFactory> records = mockConstruction(FrmRecordFactory.class,
               (mock, context) -> when(mock.factory("RhImmuneGlobulin")).thenReturn(record))) {
            assertThat(new FrmFormRHPrevention2Action().execute()).isEqualTo("success");
            ArgumentCaptor<Properties> saved = ArgumentCaptor.forClass(Properties.class);
            verify(record).saveFormRecord(saved.capture());
            assertThat(saved.getValue().getProperty("provider_no")).isEqualTo("999998");
            assertThat(saved.getValue().getProperty("demographic_no")).isEqualTo("770001");
            assertThat(saved.getValue().getProperty("workflowId")).isEqualTo("17");
            verify(states.constructed().get(0)).updateWorkFlowState(eq("17"), eq("2"), any());
            verify(flow, never()).addToWorkFlow(any(),any(),any());
        }
    }
    @Test void shouldLinkNewWorkflowToSavedForm() throws Exception {
        MockHttpServletRequest request = request();
        WorkFlow flow = mock(WorkFlow.class); FrmRecord record = mock(FrmRecord.class);
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>());
        when(flow.addToWorkFlow(eq("999998"),eq("770001"),any())).thenReturn(18);
        when(record.saveFormRecord(any())).thenReturn(33);
        when(record.createActionURL("success","save","770001","33")).thenReturn("success");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowFactory> workflows = mockConstruction(WorkFlowFactory.class,
               (mock, context) -> when(mock.getWorkFlow("RH")).thenReturn(flow));
             MockedConstruction<FrmRecordFactory> records = mockConstruction(FrmRecordFactory.class,
               (mock, context) -> when(mock.factory("RhImmuneGlobulin")).thenReturn(record))) {
            assertThat(new FrmFormRHPrevention2Action().execute()).isEqualTo("success");
            ArgumentCaptor<Properties> saved=ArgumentCaptor.forClass(Properties.class);
            verify(record).saveFormRecord(saved.capture());
            assertThat(saved.getValue().getProperty("workflowId")).isEqualTo("18");
            assertThat(request.getAttribute("demographic_no")).isEqualTo("770001");
        }
    }
    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST","/form/RHPrevention");
        request.setParameter("demographic_no","770001"); request.setParameter("edd","2027-01-01");
        request.setParameter("form_class","RhImmuneGlobulin");request.getSession().setAttribute("user","999998");
        LoggedInInfo info=mock(LoggedInInfo.class); when(info.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),info);
        SecurityInfoManager security=createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info,"_form","w",null)).thenReturn(true);
        return request;
    }
    private MockedStatic<ServletActionContext> servlet(MockHttpServletRequest request) {
        MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        return servlet;
    }
}
