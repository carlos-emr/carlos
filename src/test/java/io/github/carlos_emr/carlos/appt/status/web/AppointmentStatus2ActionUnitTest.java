/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.status.web;

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ActionProxyFactory;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.dispatcher.HttpParameters;
import org.apache.struts2.dispatcher.Dispatcher;
import org.apache.struts2.inject.Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppointmentStatus2Action")
@Tag("unit")
@Tag("appointment")
class AppointmentStatus2ActionUnitTest extends CarlosWebTestBase {

    private AppointmentStatusMgr appointmentStatusMgr;
    private TestAppointmentStatus2Action action;

    @BeforeEach
    void setUpAction() {
        appointmentStatusMgr = mock(AppointmentStatusMgr.class);
        when(appointmentStatusMgr.getAllStatus()).thenReturn(List.of());

        AppointmentStatus existingStatus = new AppointmentStatus();
        existingStatus.setId(13);
        existingStatus.setStatus("N");
        existingStatus.setDescription("No Show");
        existingStatus.setColor("#cccccc");
        when(appointmentStatusMgr.getStatus(13)).thenReturn(existingStatus);

        action = new TestAppointmentStatus2Action(appointmentStatusMgr);
    }

    @Test
    void shouldLoadExistingStatusForEdit() throws Exception {
        addRequestParameter("dispatch", "modify");
        action.setId(13);

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getId()).isEqualTo(13);
        assertThat(action.getApptStatus()).isEqualTo("N");
        assertThat(action.getApptDesc()).isEqualTo("No Show");
        assertThat(action.getApptColor()).isEqualTo("#cccccc");
    }

    @Test
    void shouldShowValidationErrorWhenEditIdDoesNotExist() throws Exception {
        addRequestParameter("dispatch", "modify");
        action.setId(9999);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldPersistValidatedDescriptionAndColour() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("  Missed appointment  ");
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "Missed appointment", "#abcdef");
        assertThat(action.getActionMessages()).isNotEmpty();
    }

    @Test
    void shouldPersistDescriptionOnlyEdit() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("Missed appointment");
        action.setApptColor("#cccccc");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "Missed appointment", "#cccccc");
    }

    @Test
    void shouldPersistColourOnlyEdit() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc("No Show");
        action.setApptColor("#123456");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).modifyStatus(13, "No Show", "#123456");
    }

    @Test
    void shouldRejectInvalidUpdateValuesWithoutMutation() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setId(13);
        action.setApptDesc(" ");
        action.setApptColor("red");

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getActionErrors()).hasSize(2);
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    @Test
    void shouldRejectMissingUpdateIdWithoutMutation() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "update");
        action.setApptDesc("Valid description");
        action.setApptColor("#abcdef");

        assertThat(executeAction(action)).isEqualTo("edit");
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    @Test
    void shouldChangeStatusWhenPostValuesAreValid() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(0);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).changeStatus(13, 0);
    }

    @Test
    void shouldBindRawRequestValuesThroughTheStrutsParameterInterceptor() throws Exception {
        AppointmentStatus2Action bindingAction = bindRawParametersThroughStruts(Map.of(
                "id", "13",
                "active", "0",
                "apptDesc", "d".repeat(30),
                "apptColor", "#ABCDEF"));

        assertThat(bindingAction.getId()).isEqualTo(13);
        assertThat(bindingAction.getActive()).isZero();
        assertThat(bindingAction.getApptDesc()).hasSize(30);
        assertThat(bindingAction.getApptColor()).isEqualTo("#ABCDEF");
    }

    @Test
    void shouldRejectRawValuesThatCannotBeCoercedByStruts() throws Exception {
        AppointmentStatus2Action bindingAction = bindRawParametersThroughStruts(
                Map.of("id", "not-a-number", "active", "false"));

        assertThat(bindingAction.getId()).isNull();
        assertThat(bindingAction.getActive()).isNull();
    }

    @Test
    void shouldRejectInvalidActiveValue() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "changestatus");
        action.setId(13);
        action.setActive(2);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentStatusMgr, never()).changeStatus(13, 2);
    }

    @Test
    void shouldResetStatusesOnPost() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("dispatch", "reset");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentStatusMgr).reset();
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "changestatus", "reset"})
    void shouldRejectMutationDispatchesOverGet(String dispatch) throws Exception {
        mockRequest.setMethod("GET");
        addRequestParameter("dispatch", dispatch);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(appointmentStatusMgr, never()).reset();
        verify(appointmentStatusMgr, never()).changeStatus(anyInt(), anyInt());
        verify(appointmentStatusMgr, never()).modifyStatus(anyInt(), anyString(), anyString());
    }

    private AppointmentStatus2Action bindRawParametersThroughStruts(
            Map<String, String> parameters) throws Exception {
        parameters.forEach(this::addRequestParameter);
        mockRequest.getServletContext().setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        Dispatcher dispatcher = new Dispatcher(mockRequest.getServletContext(), Map.of(
                "config", "struts-default.xml,struts-plugin.xml,struts-appointment-status-test.xml"));
        dispatcher.init();
        try {
            Container container = dispatcher.getContainer();
            ActionContext bindingContext = ActionContext.of(
                            new HashMap<>(ActionContext.getContext().getContextMap()))
                    .withContainer(container)
                    .withServletContext(mockRequest.getServletContext())
                    .withServletRequest(mockRequest)
                    .withServletResponse(mockResponse)
                    .withSession(new HashMap<>())
                    .withParameters(HttpParameters.create(requestParameters).build());
            bindingContext.bind();
            ActionProxy proxy = container.getInstance(ActionProxyFactory.class).createActionProxy(
                    "/", "appointment-status-binding-test", null,
                    bindingContext.getContextMap(), false, true);

            proxy.execute();
            return (AppointmentStatus2Action) proxy.getAction();
        } finally {
            dispatcher.cleanup();
            Dispatcher.clearInstance();
            setUpActionContext();
        }
    }

    private static final class TestAppointmentStatus2Action extends AppointmentStatus2Action {
        private final AppointmentStatusMgr appointmentStatusMgr;

        private TestAppointmentStatus2Action(AppointmentStatusMgr appointmentStatusMgr) {
            this.appointmentStatusMgr = appointmentStatusMgr;
        }

        @Override
        public AppointmentStatusMgr getApptStatusMgr() {
            return appointmentStatusMgr;
        }

        @Override
        public String getText(String key) {
            return key;
        }
    }
}
