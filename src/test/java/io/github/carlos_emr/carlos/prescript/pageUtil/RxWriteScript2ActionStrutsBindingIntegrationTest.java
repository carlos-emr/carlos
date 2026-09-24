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

import io.github.carlos_emr.carlos.managers.RxManager;
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
import static org.mockito.Mockito.mock;

/**
 * Drives {@link RxWriteScript2Action} through the real Struts interceptor stack with the request
 * shape the Rx staging page sends. The unit and integration tests call the action's methods
 * directly and so never bind parameters; the packaged install showed why that matters.
 *
 * <p>SearchDrug3's Save posts the drug form, which carries a hidden {@code demographicNo}, and
 * {@code rx-patient-context.js} used to add {@code demographicNo} to the URL as well. The action
 * then received two values. Struts bound them into the action's {@code int demographicNo}, failed
 * the conversion, and the workflow interceptor answered with the undefined {@code input} result
 * (HTTP 404), so every prescription save was refused. The patient is resolved from the request by
 * {@link RxSessionBeanResolver}, which accepts repeated equal values, so the action must not bind
 * {@code demographicNo} itself.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("RxWriteScript2Action Struts binding")
@Tag("integration")
@Tag("prescript")
class RxWriteScript2ActionStrutsBindingIntegrationTest extends CarlosWebTestBase {

    @Test
    @DisplayName("should reach the action when demographicNo arrives in both the URL and the form body")
    void shouldReachAction_whenDemographicNoIsRepeated() throws Exception {
        // parameterValue names no operation, so execute() returns before any Rx work: the test is
        // only about whether the interceptor stack lets the request through.
        BindingResult result = bindThroughStruts(Map.of(
                "parameterValue", new String[]{"noSuchOperation"},
                "demographicNo", new String[]{"1001", "1001"}));

        assertThat(result.failure()).isNull();
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.action().hasActionErrors()).isFalse();
        assertThat(result.code()).isNull();
    }

    @Test
    @DisplayName("should reach the action when demographicNo arrives once")
    void shouldReachAction_whenDemographicNoIsSingle() throws Exception {
        BindingResult result = bindThroughStruts(Map.of(
                "parameterValue", new String[]{"noSuchOperation"},
                "demographicNo", new String[]{"1001"}));

        assertThat(result.failure()).isNull();
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.code()).isNull();
    }

    private record BindingResult(RxWriteScript2Action action, String code, Exception failure) {
    }

    private BindingResult bindThroughStruts(Map<String, String[]> parameters) throws Exception {
        // Collaborators the action resolves in its constructor; none is used by this request.
        replaceSpringUtilsBean(RxManager.class, mock(RxManager.class));
        parameters.forEach((name, values) -> {
            requestParameters.put(name, values);
            mockRequest.setParameter(name, values);
        });
        mockRequest.setMethod("POST");
        mockRequest.getServletContext().setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        Dispatcher dispatcher = new Dispatcher(mockRequest.getServletContext(), Map.of(
                "config", "struts-default.xml,struts-plugin.xml,struts-rx-writescript-binding-test.xml"));
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
                    "/", "rx-writescript-binding-test", null,
                    bindingContext.getContextMap(), false, true);
            String code = null;
            Exception failure = null;
            try {
                code = proxy.execute();
            } catch (Exception e) {
                // An "input" result has no mapping, so a conversion error surfaces here.
                failure = e;
            }
            return new BindingResult((RxWriteScript2Action) proxy.getAction(), code, failure);
        } finally {
            dispatcher.cleanup();
            Dispatcher.clearInstance();
            setUpActionContext();
        }
    }
}
