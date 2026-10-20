/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifies method refusal before forwarding the repair request to its mutating JSP. */
@Tag("unit")
@Tag("security")
class FixRolesOnNotes2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        user = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void closeServlet() {
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "OPTIONS", "post", "PoSt"})
    void shouldRefuseRepair_whenMethodIsNotPost(String method) {
        request.setMethod(method);
        request.setParameter("action", "run");
        assertThat(new FixRolesOnNotes2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(security).hasPrivilege(user, "_admin", "w", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRenderForm_whenNoRepairRequested(String method) {
        request.setMethod(method);
        assertThat(new FixRolesOnNotes2Action().execute()).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldForwardAuthorizedPost_toRepairView() {
        request.setMethod("POST");
        request.setParameter("action", "run");
        assertThat(new FixRolesOnNotes2Action().execute()).isEqualTo("success");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void shouldKeepAdminWriteRequirement_forRepair(String method) {
        request.setMethod(method);
        request.setParameter("action", "run");
        when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> new FixRolesOnNotes2Action().execute()).isInstanceOf(SecurityException.class);
    }
}
