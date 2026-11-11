/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.managers.AuditLogManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class AuditLogPurgeActionUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AuditLogManager manager = mock(AuditLogManager.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void configureRequest() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        registerMock(AuditLogManager.class, manager);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), eq("_admin.auditLogPurge"), eq("w"), isNull())).thenReturn(true);
    }
    @AfterEach void closeServlet() { servlet.close(); }

    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD"})
    void shouldRenderFormAndRejectMutation_whenUsingReadMethods(String method) throws Exception {
        request.setMethod(method);
        assertThat(new AuditLogPurge2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        request.setParameter("dateBegin", "2000-01-01");
        assertThat(new AuditLogPurge2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(manager);
    }

    @ParameterizedTest @ValueSource(strings = {"2000-02-30", "2000-01-01x", "0000-01-01", "", "<script>"})
    void shouldPerformNoPurge_whenPostedDateIsInvalid(String date) throws Exception {
        request.setMethod("POST"); request.setParameter("dateBegin", date);
        assertThat(new AuditLogPurge2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(manager);
        assertThat(request.getAttribute("msg").toString()).doesNotContain("<script>");
    }

    @Test
    void shouldReportSafeFailure_whenPurgeThrowsWithoutCause() throws Exception {
        request.setMethod("POST"); request.setParameter("dateBegin", "2000-01-01");
        when(manager.purgeAuditLog(any(), any())).thenThrow(new java.io.IOException("FAKE-secret"));
        assertThat(new AuditLogPurge2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("msg").toString()).contains("did not finish", "backups were retained").doesNotContain("FAKE-secret");
    }

    @Test
    void shouldEnforcePrivilege_whenOpeningOrSubmittingForm() {
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(false);
        request.setMethod("GET");
        assertThatThrownBy(() -> new AuditLogPurge2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldReportConfirmedCount_whenAuthorizedPostSucceeds() throws Exception {
        request.setMethod("POST"); request.setParameter("dateBegin", "2000-01-01");
        when(manager.purgeAuditLog(any(), any())).thenReturn(2);
        assertThat(new AuditLogPurge2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("msg")).isEqualTo("Success. Removed 2 records from the audit log.");
        verify(manager).purgeAuditLog(any(), any());
    }
}
