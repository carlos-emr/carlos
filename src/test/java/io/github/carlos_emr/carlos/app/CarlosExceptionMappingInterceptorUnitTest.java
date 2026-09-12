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
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.config.entities.ActionConfig;
import org.apache.struts2.config.entities.ExceptionMappingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The contract that replaced struts-default's silent exception mapping: every mapped failure is
 * logged once with an incident id, the same id reaches the result page, the status is real, and
 * nothing about the request beyond its path reaches the log line.
 */
@DisplayName("CarlosExceptionMappingInterceptor")
@Tag("unit")
class CarlosExceptionMappingInterceptorUnitTest {

    private static final String QUERY_VALUE = "hin=9876543210";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ActionInvocation invocation;
    private CarlosExceptionMappingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/carlos/encounter/RequestConsultation;jsessionid=abc");
        request.setQueryString(QUERY_VALUE);
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        ActionConfig config = new ActionConfig.Builder("encounter", "encounter/RequestConsultation", "Some2Action")
                .addExceptionMapping(new ExceptionMappingConfig.Builder("security", SecurityException.class.getName(), "securityError").build())
                .addExceptionMapping(new ExceptionMappingConfig.Builder("any", Exception.class.getName(), "error").build())
                .build();
        ActionProxy proxy = mock(ActionProxy.class);
        when(proxy.getConfig()).thenReturn(config);
        when(proxy.getActionName()).thenReturn("encounter/RequestConsultation");
        invocation = mock(ActionInvocation.class);
        when(invocation.getProxy()).thenReturn(proxy);

        interceptor = new CarlosExceptionMappingInterceptor();
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("maps an unexpected exception to the error result with a 500, an incident id, and one ERROR line")
    void shouldMapToErrorResult_withIncidentIdAndStatus500() throws Exception {
        when(invocation.invoke()).thenThrow(new IllegalArgumentException("Identifier may not be null"));

        String result;
        List<String> messages;
        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            result = interceptor.intercept(invocation);
            messages = logCapture.messages();
        }

        assertThat(result).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(500);
        Object incidentId = request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE);
        assertThat(incidentId).isInstanceOf(String.class);
        assertThat((String) incidentId).matches("[0-9a-f-]{36}");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .contains((String) incidentId)
                .contains("IllegalArgumentException")
                .contains("encounter/RequestConsultation")
                .contains("POST")
                .contains("/carlos/encounter/RequestConsultation")
                .contains("provider=999998");
    }

    @Test
    @DisplayName("keeps the query string, the session id and the exception message out of the log line")
    void shouldKeepRequestDetail_outOfLogLine() throws Exception {
        when(invocation.invoke()).thenThrow(new IllegalStateException("value was " + QUERY_VALUE));

        List<String> messages;
        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            interceptor.intercept(invocation);
            messages = logCapture.messages();
        }

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .doesNotContain(QUERY_VALUE)
                .doesNotContain("jsessionid")
                .doesNotContain("value was");
    }

    @Test
    @DisplayName("maps a SecurityException to the security result with a 403 and a WARN line carrying its message")
    void shouldMapSecurityException_toSecurityErrorWith403() throws Exception {
        when(invocation.invoke()).thenThrow(new SecurityException("missing required sec object (_con)"));

        String result;
        List<String> messages;
        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            result = interceptor.intercept(invocation);
            messages = logCapture.messages();
        }

        assertThat(result).isEqualTo("securityError");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNotNull();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .startsWith("Authorization refused")
                .contains("missing required sec object (_con)")
                .contains("provider=999998");
    }

    /**
     * struts-admin.xml maps Spring Security's AccessDeniedException to securityError as well; the
     * refusal classification must follow that configured mapping, not the exception type alone.
     */
    @Test
    @DisplayName("treats any exception the package maps to securityError as a refusal")
    void shouldTreatMappedSecurityResult_asRefusal() throws Exception {
        ActionConfig config = new ActionConfig.Builder("admin", "admin/x", "Some2Action")
                .addExceptionMapping(new ExceptionMappingConfig.Builder("denied", IllegalStateException.class.getName(), "securityError").build())
                .addExceptionMapping(new ExceptionMappingConfig.Builder("any", Exception.class.getName(), "error").build())
                .build();
        ActionProxy proxy = mock(ActionProxy.class);
        when(proxy.getConfig()).thenReturn(config);
        when(proxy.getActionName()).thenReturn("admin/x");
        when(invocation.getProxy()).thenReturn(proxy);
        when(invocation.invoke()).thenThrow(new IllegalStateException("Access is denied"));

        String result;
        List<String> messages;
        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            result = interceptor.intercept(invocation);
            messages = logCapture.messages();
        }

        assertThat(result).isEqualTo("securityError");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).startsWith("Authorization refused").contains("Access is denied");
    }

    @Test
    @DisplayName("leaves a committed response's status alone but still records the incident")
    void shouldNotTouchStatus_whenResponseIsCommitted() throws Exception {
        response.setStatus(200);
        response.setCommitted(true);
        when(invocation.invoke()).thenThrow(new IllegalStateException("after the first byte"));

        String result;
        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            result = interceptor.intercept(invocation);
            assertThat(logCapture.messages()).hasSize(1);
        }

        assertThat(result).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNotNull();
    }

    @Test
    @DisplayName("rethrows an exception no mapping covers, unchanged and unlogged here")
    void shouldRethrow_whenNoMappingMatches() throws Exception {
        ActionConfig unmapped = new ActionConfig.Builder("encounter", "x", "Some2Action").build();
        ActionProxy proxy = mock(ActionProxy.class);
        when(proxy.getConfig()).thenReturn(unmapped);
        when(proxy.getActionName()).thenReturn("x");
        when(invocation.getProxy()).thenReturn(proxy);
        IllegalStateException failure = new IllegalStateException("unmapped");
        when(invocation.invoke()).thenThrow(failure);

        try (LogCapture logCapture = LogCapture.forLogger(CarlosExceptionMappingInterceptor.class)) {
            assertThatThrownBy(() -> interceptor.intercept(invocation)).isSameAs(failure);
            assertThat(logCapture.messages()).isEmpty();
        }
        assertThat(request.getAttribute(CarlosExceptionMappingInterceptor.INCIDENT_ID_ATTRIBUTE)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
