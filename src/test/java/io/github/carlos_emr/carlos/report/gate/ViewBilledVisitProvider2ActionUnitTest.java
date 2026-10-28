/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Verifies the gate refuses to forward unauthorized mutations to the writing JSP. */
@Tag("unit")
class ViewBilledVisitProvider2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"GET, buttonUpdate", "HEAD, buttonUpdate", "GET, submit", "HEAD, submit"})
    void shouldRefuseRoleChangesBeforeJspForward_whenMethodIsReadOnly(String method, String parameter) {
        request.setMethod(method);
        request.setParameter(parameter, "localized button label");
        assertThat(new ViewBilledVisitProvider2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"buttonUpdate", "submit"})
    void shouldRefuseRoleChanges_whenReportReaderHasNoAdministrativeWritePrivilege(String parameter) {
        request.setMethod("POST");
        request.setParameter(parameter, "localized button label");
        assertThatThrownBy(() -> new ViewBilledVisitProvider2Action().execute())
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest
    @CsvSource({"_admin, buttonUpdate", "_admin.userAdmin, buttonUpdate", "_admin, submit", "_admin.userAdmin, submit"})
    void shouldAllowRoleChanges_whenPostHasAdministrativeWritePrivilege(String privilege, String parameter) {
        request.setMethod("POST");
        request.setParameter(parameter, "localized button label");
        when(security.hasPrivilege(login, privilege, "w", null)).thenReturn(true);
        assertThat(new ViewBilledVisitProvider2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST"})
    void shouldPreserveViews_whenReportReaderDoesNotSubmitAMutation(String method) {
        request.setMethod(method);
        request.setParameter("buttonUpdate", "");
        request.setParameter("submit", "");
        assertThat(new ViewBilledVisitProvider2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(security, never()).hasPrivilege(eq(login), anyString(), eq("w"), isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "OPTIONS"})
    void shouldPreserveAllowedViewMethods_whenUnsupportedVerbIsUsed(String method) {
        request.setMethod(method);
        assertThat(new ViewBilledVisitProvider2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    void shouldRefuseView_whenReportPrivilegeIsMissing() {
        request.setMethod("GET");
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(false);
        assertThatThrownBy(() -> new ViewBilledVisitProvider2Action().execute())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseView_whenSessionIsMissing() {
        request.setMethod("GET");
        request.getSession().invalidate();
        assertThatThrownBy(() -> new ViewBilledVisitProvider2Action().execute())
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(security);
    }
}
