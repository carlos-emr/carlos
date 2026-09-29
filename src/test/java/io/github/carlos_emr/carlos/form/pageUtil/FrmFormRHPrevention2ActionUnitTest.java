// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.form.FrmRecord;
import io.github.carlos_emr.carlos.form.FrmRecordFactory;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.WriteNewMeasurements;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrmFormRHPrevention2ActionUnitTest extends CarlosUnitTestBase {
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
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
    @Test void shouldSaveOwnedWorkflow_withSessionProvider() throws Exception {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "17");
        request.setParameter("provider_no", "forged"); request.setParameter("state", "2");
        WorkFlow flow = mock(WorkFlow.class); FrmRecord record = mock(FrmRecord.class);
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>(java.util.List.of(row)));
        when(record.saveFormRecord(any())).thenReturn(32);

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
            assertThat(request.getAttribute("savedRhFormId")).isEqualTo("32");
            verify(states.constructed().get(0)).updateWorkFlowState(eq("17"), eq("2"), any());
            verify(flow, never()).addToWorkFlow(any(),any(),any());
        }
    }
    @Test void shouldLinkNewWorkflow_toSavedForm() throws Exception {
        MockHttpServletRequest request = request();
        WorkFlow flow = mock(WorkFlow.class); FrmRecord record = mock(FrmRecord.class);
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>());
        when(flow.addToWorkFlow(eq("999998"),eq("770001"),any())).thenReturn(18);
        when(record.saveFormRecord(any())).thenReturn(33);

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
    @Test void shouldRejectWorkflowOwnedByAnotherPatient_beforeAnyWrite() throws Exception {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "99");
        request.setParameter("state", "2");
        WorkFlow flow = mock(WorkFlow.class);
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>(java.util.List.of(row)));
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowFactory> workflows = mockConstruction(WorkFlowFactory.class,
               (mock, context) -> when(mock.getWorkFlow("RH")).thenReturn(flow));
             MockedConstruction<WorkFlowState> states = mockConstruction(WorkFlowState.class);
             MockedConstruction<FrmRecordFactory> records = mockConstruction(FrmRecordFactory.class)) {
            assertThat(new FrmFormRHPrevention2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getStatus()).isEqualTo(400);
            assertThat(states.constructed()).isEmpty();
            assertThat(records.constructed()).isEmpty();
            verify(flow, never()).addToWorkFlow(any(), any(), any());
        }
    }

    @Test void shouldReportSaveFailure_withoutReturningSuccessOrZeroRecordRoute() throws Exception {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "17");
        WorkFlow flow = mock(WorkFlow.class); FrmRecord record = mock(FrmRecord.class);
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        when(flow.getActiveWorkFlowList("770001")).thenReturn(new ArrayList<>(java.util.List.of(row)));
        when(record.saveFormRecord(any())).thenThrow(new java.sql.SQLException("synthetic save failure"));
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowFactory> workflows = mockConstruction(WorkFlowFactory.class,
               (mock, context) -> when(mock.getWorkFlow("RH")).thenReturn(flow));
             MockedConstruction<WorkFlowState> states = mockConstruction(WorkFlowState.class);
             MockedConstruction<FrmRecordFactory> records = mockConstruction(FrmRecordFactory.class,
               (mock, context) -> when(mock.factory("RhImmuneGlobulin")).thenReturn(record))) {
            assertThat(new FrmFormRHPrevention2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getStatus()).isEqualTo(500);
            verify(record, never()).createActionURL(any(), any(), any(), any());
            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }
    }

    @Test void shouldRejectGet_forBothRhMutations() {
        MockHttpServletRequest request = request(); request.setMethod("GET");
        try (MockedStatic<ServletActionContext> servlet = servlet(request)) {
            assertThat(new FrmFormRHPrevention2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getStatus()).isEqualTo(405);
            assertThat(new FrmFormAddRHWorkFlow2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getHeader("Allow")).isEqualTo("POST");
        }
    }

    @Test void shouldRejectOtherPatientWorkflowInAddAction_beforeMeasurements() {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "99");
        request.setParameter("state", "2");
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowState> workflows = mockConstruction(WorkFlowState.class,
               (mock, context) -> when(mock.getActiveWorkFlowList("RH", "770001"))
                 .thenReturn(new ArrayList<>(java.util.List.of(row))));
             MockedConstruction<WriteNewMeasurements> measurements = mockConstruction(WriteNewMeasurements.class)) {
            assertThat(new FrmFormAddRHWorkFlow2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getStatus()).isEqualTo(400);
            verify(workflows.constructed().get(0), never()).updateWorkFlowState(any(), any());
            assertThat(measurements.constructed()).isEmpty();
        }
    }

    @Test void shouldSaveOwnedAddAction_withPatientWorkflow() {
        MockHttpServletRequest request = request(); request.setParameter("workflowId", "17");
        request.setParameter("state", "2");
        Hashtable<String,String> row = new Hashtable<>(); row.put("ID", "17");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowState> workflows = mockConstruction(WorkFlowState.class,
               (mock, context) -> when(mock.getActiveWorkFlowList("RH", "770001"))
                 .thenReturn(new ArrayList<>(java.util.List.of(row))))) {
            assertThat(new FrmFormAddRHWorkFlow2Action().execute()).isEqualTo("success");
            verify(workflows.constructed().get(0)).updateWorkFlowState("17", "2");
        }
    }

    @Test void shouldSaveBloodMeasurements_withPatientAndSessionProvider() {
        MockHttpServletRequest request = request();
        request.setParameter("workflowId", "17");
        request.setParameter("state", "2");
        request.setParameter("motherABO", "AB");
        request.setParameter("motherRHtype", "NEG");
        request.setParameter("provider_no", "forged");
        Hashtable<String, String> row = new Hashtable<>();
        row.put("ID", "17");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowState> workflows = mockConstruction(WorkFlowState.class,
                 (mock, context) -> when(mock.getActiveWorkFlowList("RH", "770001"))
                     .thenReturn(new ArrayList<>(java.util.List.of(row))));
             MockedConstruction<WriteNewMeasurements> measurements = mockConstruction(WriteNewMeasurements.class)) {
            assertThat(new FrmFormAddRHWorkFlow2Action().execute()).isEqualTo("success");
            var writes = inOrder(workflows.constructed().get(0), measurements.constructed().get(0), transactionManager);
            writes.verify(workflows.constructed().get(0)).updateWorkFlowState("17", "2");
            writes.verify(measurements.constructed().get(0)).write(eq("BLDT"), eq("AB"), eq("770001"),
                    eq("999998"), any(java.util.Date.class), eq(""));
            writes.verify(measurements.constructed().get(0)).write(eq("RHT"), eq("NEG"), eq("770001"),
                    eq("999998"), any(java.util.Date.class), eq(""));
            writes.verify(transactionManager).commit(any());
            verify(transactionManager, never()).rollback(any());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRollBackWorkflowAndMeasurements_whenSecondMeasurementFails(boolean newWorkflow) {
        MockHttpServletRequest request = request();
        if (!newWorkflow) request.setParameter("workflowId", "17");
        request.setParameter("state", "2");
        request.setParameter("end_date", "2027-01-01");
        request.setParameter("motherABO", "AB");
        request.setParameter("motherRHtype", "NEG");
        Hashtable<String, String> row = new Hashtable<>();
        row.put("ID", "17");
        try (MockedStatic<ServletActionContext> servlet = servlet(request);
             MockedConstruction<WorkFlowState> workflows = mockConstruction(WorkFlowState.class, (mock, context) -> {
                 when(mock.getActiveWorkFlowList("RH", "770001"))
                     .thenReturn(new ArrayList<>(java.util.List.of(row)));
                 when(mock.addToWorkFlow(eq("RH"), eq("999998"), eq("770001"), any(), eq("1")))
                     .thenReturn(18);
             });
             MockedConstruction<WriteNewMeasurements> measurements = mockConstruction(WriteNewMeasurements.class,
                 (mock, context) -> doThrow(new IllegalStateException("synthetic measurement failure"))
                     .when(mock).write(eq("RHT"), anyString(), anyString(), anyString(), any(), anyString()))) {
            assertThat(new FrmFormAddRHWorkFlow2Action().execute()).isEqualTo("none");
            assertThat(ServletActionContext.getResponse().getStatus()).isEqualTo(500);
            var writes = inOrder(workflows.constructed().get(0), measurements.constructed().get(0), transactionManager);
            if (newWorkflow) {
                writes.verify(workflows.constructed().get(0)).addToWorkFlow(eq("RH"), eq("999998"),
                        eq("770001"), any(), eq("1"));
            } else {
                writes.verify(workflows.constructed().get(0)).updateWorkFlowState("17", "2");
            }
            writes.verify(measurements.constructed().get(0)).write(eq("BLDT"), eq("AB"), eq("770001"),
                    eq("999998"), any(), eq(""));
            writes.verify(measurements.constructed().get(0)).write(eq("RHT"), eq("NEG"), eq("770001"),
                    eq("999998"), any(), eq(""));
            writes.verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
            assertThat(request.getAttribute("demographic_no")).isNull();
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
        transactionManager = createAndRegisterMock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new org.springframework.transaction.support.SimpleTransactionStatus());
        createAndRegisterMock(MeasurementTypeDao.class);
        createAndRegisterMock(MeasurementDao.class);
        return request;
    }
    private MockedStatic<ServletActionContext> servlet(MockHttpServletRequest request) {
        MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        return servlet;
    }
}
