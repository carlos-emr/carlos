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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsUploadSubmissionService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.ServletInputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CdsWebContractUnitTest extends CarlosUnitTestBase {
    private final io.github.carlos_emr.carlos.managers.SecurityInfoManager securityInfoManager = allowedPrivileges();
    private static io.github.carlos_emr.carlos.managers.SecurityInfoManager allowedPrivileges() {
        var privileges = mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        when(privileges.hasPrivilege(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(true);
        return privileges;
    }

    static Stream<Arguments> unsupportedVersions() {
        return Stream.of(
                Arguments.of((Object) null), Arguments.of((Object) new String[0]),
                Arguments.of((Object) new String[]{"1"}), Arguments.of((Object) new String[]{"3"}),
                Arguments.of((Object) new String[]{"02"}), Arguments.of((Object) new String[]{" 2"}),
                Arguments.of((Object) new String[]{"2", "2"}), Arguments.of((Object) new String[]{"2", "1"}));
    }

    @AfterEach void clearContext() { ActionContext.clear(); }

    @ParameterizedTest @MethodSource("unsupportedVersions")
    void shouldDenyChunkBodiesQuotaAcceptAndRunControl_forIncompatibleClients(String[] versions) throws Exception {
        var access = mock(CdsImportAccessService.class);
        var uploads = mock(CdsUploadService.class);
        var imports = mock(CdsImportService.class);
        var submissions = mock(CdsUploadSubmissionService.class);
        LoggedInInfo actor = mock(LoggedInInfo.class);
        Security security = new Security(); security.setSecurityNo(42);
        when(actor.getLoggedInSecurity()).thenReturn(security);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        for (String operation : new String[]{"begin", "chunk", "finish", "abandon", "submit", "resume", "cancel", "review"}) {
            var request = new MockHttpServletRequest() {
                @Override public ServletInputStream getInputStream() {
                    throw new AssertionError("incompatible client body must not be opened");
                }
            };
            request.setMethod("POST"); request.setContentType("application/octet-stream");
            request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", actor);
            if (versions != null) request.addParameter(CdsWeb.API_VERSION_PARAMETER, versions);
            var response = new MockHttpServletResponse();
            ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
            if ("submit".equals(operation)) {
                new SubmitCdsImport2Action(access, submissions, securityInfoManager).execute();
            } else if (Set.of("resume", "cancel", "review").contains(operation)) {
                var action = new CdsImportControl2Action(access, imports, securityInfoManager); action.setOperation(operation); action.execute();
            } else {
                var action = new CdsUpload2Action(access, uploads, securityInfoManager); action.setOperation(operation); action.execute();
            }
            assertThat(response.getStatus()).as(operation).isEqualTo(409);
            assertThat(response.getHeader(CdsWeb.API_VERSION_HEADER)).isEqualTo("2");
            assertThat(response.getContentAsString()).contains("IMPORT_CLIENT_REFRESH_REQUIRED", "\"apiVersion\":2");
            verifyNoInteractions(uploads, imports, submissions);
        }
    }

    @Test void shouldKeepSupportedJsonParametersStrict_withoutOverridingResponseContract() throws Exception {
        var request = new MockHttpServletRequest(); request.setMethod("POST");
        request.addParameter("apiVersion", "2"); request.addParameter("operation", "begin");
        CdsWeb.parameters(request, Set.of("operation"));
        request.addParameter("path", "/private/source");
        assertThatThrownBy(() -> CdsWeb.parameters(request, Set.of("operation")))
                .hasMessage("IMPORT_PARAMETERS_INVALID");
        var response = new MockHttpServletResponse();
        CdsWeb.json(response, 200, Map.of("ok", true, "apiVersion", 1));
        assertThat(response.getHeader(CdsWeb.API_VERSION_HEADER)).isEqualTo("2");
        assertThat(response.getContentAsString()).contains("\"ok\":true", "\"apiVersion\":2")
                .doesNotContain("\"apiVersion\":1");
    }

    @Test void shouldKeepVersionlessLinksForExplicitReadOnlyCallersOnly_withoutAdmittingPost() {
        var request = new MockHttpServletRequest(); request.setMethod("GET"); request.addParameter("type", "event");
        CdsWeb.readOnlyParameters(request, Set.of("type"));
        assertThatThrownBy(() -> CdsWeb.parameters(request, Set.of("type")))
                .hasMessage("IMPORT_CLIENT_REFRESH_REQUIRED");
        request.setMethod("POST"); request.addParameter("apiVersion", "2");
        assertThatThrownBy(() -> CdsWeb.readOnlyParameters(request, Set.of("type")))
                .hasMessage("IMPORT_PARAMETERS_INVALID");
    }

    @Test void shouldPreserveRequestContractUnderFrameworkBinding_withoutActionPropertyOrWorkflowError() throws Exception {
        var request = new MockHttpServletRequest(); request.setMethod("POST");
        request.addParameter("apiVersion", "2"); request.addParameter("operation", "begin");
        var response = new MockHttpServletResponse();
        var uploads = mock(CdsUploadService.class);
        var action = org.mockito.Mockito.spy(new CdsUpload2Action(mock(CdsImportAccessService.class), uploads, securityInfoManager));
        var previous = ActionContext.getContext();
        var previousDispatcher = org.apache.struts2.dispatcher.Dispatcher.getInstance();
        var dispatcher = new org.apache.struts2.dispatcher.Dispatcher(request.getServletContext(),
                Map.of("config", "struts-default.xml", "struts.devMode", "false"));
        dispatcher.init();
        try {
            var container = dispatcher.getContainer();
            container.getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).allowClassHierarchy(action.getClass());
            var stack = container.getInstance(org.apache.struts2.util.ValueStackFactory.class).createValueStack();
            stack.push(action);
            var context = stack.getActionContext().withContainer(container).withValueStack(stack)
                    .withServletRequest(request).withServletResponse(response)
                    .withParameters(org.apache.struts2.dispatcher.HttpParameters.create(request.getParameterMap()).build()).bind();
            var invocation = mock(org.apache.struts2.ActionInvocation.class);
            var proxy = mock(org.apache.struts2.ActionProxy.class);
            when(proxy.getMethod()).thenReturn("execute");
            when(invocation.getProxy()).thenReturn(proxy); when(invocation.getAction()).thenReturn(action);
            when(invocation.getInvocationContext()).thenReturn(context); when(invocation.getStack()).thenReturn(stack);
            when(invocation.invoke()).thenReturn("none");
            var interceptor = new org.apache.struts2.interceptor.parameter.ParametersInterceptor();
            container.inject(interceptor); interceptor.setRequireAnnotations("true"); interceptor.init();
            try { assertThat(interceptor.intercept(invocation)).isEqualTo("none"); }
            finally { interceptor.destroy(); }
            org.mockito.Mockito.verify(action).setOperation("begin");
            assertThat(action.hasErrors()).isFalse();
            CdsWeb.parameters(request, Set.of("operation"));
            verifyNoInteractions(uploads);
        } finally {
            dispatcher.getContainer().getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).clearAllowlist();
            dispatcher.cleanup();
            if (previousDispatcher == null) org.apache.struts2.dispatcher.Dispatcher.clearInstance();
            else org.apache.struts2.dispatcher.Dispatcher.setInstance(previousDispatcher);
            if (previous == null) ActionContext.clear(); else previous.bind();
        }
    }
}
