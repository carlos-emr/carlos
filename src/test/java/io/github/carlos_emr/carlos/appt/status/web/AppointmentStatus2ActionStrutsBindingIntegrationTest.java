/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.status.web;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ActionProxyFactory;
import org.apache.struts2.dispatcher.Dispatcher;
import org.apache.struts2.dispatcher.HttpParameters;
import org.apache.struts2.inject.Container;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppointmentStatus2Action Struts binding")
@Tag("integration")
@Tag("appointment")
class AppointmentStatus2ActionStrutsBindingIntegrationTest extends CarlosWebTestBase {

    @Test
    void shouldBindRawRequestValues_throughStrutsParameterInterceptor() throws Exception {
        AppointmentStatus2Action bindingAction = bindRawParametersThroughStruts(Map.of(
                "id", "13",
                "active", "0",
                "apptDesc", "d".repeat(30),
                "apptColor", "#ABCDEF",
                "apptColorChanged", "true"));

        assertThat(bindingAction.getId()).isEqualTo(13);
        assertThat(bindingAction.getActive()).isZero();
        assertThat(bindingAction.getApptDesc()).hasSize(30);
        assertThat(bindingAction.getApptColor()).isEqualTo("#ABCDEF");
        assertThat(bindingAction.isApptColorChanged()).isTrue();
    }

    @Test
    void shouldRejectRawValues_whenStrutsCannotCoerceThem() throws Exception {
        AppointmentStatus2Action bindingAction = bindRawParametersThroughStruts(
                Map.of("id", "not-a-number", "active", "false"));

        assertThat(bindingAction.getId()).isNull();
        assertThat(bindingAction.getActive()).isNull();
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
}
