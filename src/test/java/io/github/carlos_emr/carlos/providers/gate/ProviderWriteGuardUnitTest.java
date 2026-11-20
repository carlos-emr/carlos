/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.providers.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Includes cannot set an outer response's status; rejection must survive on the request. */
@Tag("unit")
class ProviderWriteGuardUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private SecurityInfoManager security;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectBeforePrivilegeLookup_whenMutationMethodIsNotPost(String method) {
        request.setMethod(method);
        assertThat(ProviderWriteGuard.requireAdminPost(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(request.getAttribute(ProviderWriteGuard.STATUS_ATTRIBUTE)).isEqualTo(405);
        verifyNoInteractions(security);
    }

    @Test
    void shouldPermitMutation_whenPostHasAdministrativeWritePrivilege() {
        request.setMethod("POST");
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        assertThat(ProviderWriteGuard.requireAdminPost(request, response)).isTrue();
        assertThat(request.getAttribute(ProviderWriteGuard.STATUS_ATTRIBUTE)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectMutation_whenPostOnlyHasAppointmentReadPrivilege() {
        request.setMethod("POST");
        when(security.hasPrivilege(login, "_appointment", "r", null)).thenReturn(true);
        assertThat(ProviderWriteGuard.requireAdminPost(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute(ProviderWriteGuard.STATUS_ATTRIBUTE)).isEqualTo(403);
    }

    @Test
    void shouldRejectMutation_whenSessionIsMissing() {
        request.setMethod("POST");
        request.getSession().invalidate();
        assertThat(ProviderWriteGuard.requireAdminPost(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(security);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void shouldRetainRejectionForOuterController_whenIncludeIgnoresStatusChanges(String method) {
        request.setMethod(method);
        HttpServletResponse included = new HttpServletResponseWrapper(response) {
            @Override public void setStatus(int status) { /* Servlet include semantics. */ }
            @Override public void setHeader(String name, String value) { /* Servlet include semantics. */ }
        };
        assertThat(ProviderWriteGuard.requireAdminPost(request, included)).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(ProviderWriteGuard.STATUS_ATTRIBUTE))
                .isEqualTo("GET".equals(method) ? 405 : 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRecordIncludedActionRejection_whenSaveMyGroupRejectsBeforeJsp(String method) throws Exception {
        request.setMethod(method);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new SaveMyGroup2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(request.getAttribute(ProviderWriteGuard.STATUS_ATTRIBUTE)).isEqualTo(405);
        }
    }
}
