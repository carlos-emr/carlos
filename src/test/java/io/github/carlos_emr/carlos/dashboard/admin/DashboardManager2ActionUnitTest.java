/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.dashboard.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@DisplayName("Indicator Dashboard Manager administration")
class DashboardManager2ActionUnitTest {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DashboardManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo info;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private Dashboard existing;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.setMethod("GET");
        response = new MockHttpServletResponse();
        manager = mock(DashboardManager.class);
        security = mock(SecurityInfoManager.class);
        info = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        when(security.hasPrivilege(info, "_dashboardManager", "r", null)).thenReturn(true);
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        existing = new Dashboard();
        existing.setId(17);
        existing.setName("Original");
        existing.setCreator("999998");
        existing.setActive(false);
        when(manager.getDashboards(info)).thenReturn(List.of(existing));
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
    }

    private String execute() throws Exception {
        return new DashboardManager2Action(manager, security).execute();
    }

    @Test
    @DisplayName("should refuse access when the manager denies a read after the action permission check")
    void shouldRefuseRead_whenManagerDeniesAccess() throws Exception {
        when(manager.getDashboards(info)).thenReturn(null);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "existing"})
    @DisplayName("should keep submitted fields visible when a save fails or conflicts")
    void shouldPreserveForm_whenSaveRefused(String kind) throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("name", "Submitted");
        if ("existing".equals(kind)) request.setParameter("dashboardId", "17");
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo("existing".equals(kind) ? 409 : 500);
        assertThat(request.getAttribute("dashboardName")).isEqualTo("Submitted");
        assertThat(request.getAttribute("dashboardManagerError")).isEqualTo("dashboard.dashboardmanager.saveFailed");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should list active and inactive dashboards for a reader")
    void shouldListDashboards_whenReader(String method) throws Exception {
        request.setMethod(method);
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(false);
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("dashboards")).isEqualTo(List.of(existing));
        assertThat(request.getAttribute("dashboardCanWrite")).isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should refuse a mutation without POST before accessing dashboards")
    void shouldRefuseMutation_whenNotPost(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("operation", "save");
        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("should deny readers write access even when the submitted dashboard exists")
    void shouldDenySave_whenReadOnly() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(false);
        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("should deny dashboard enumeration without read access")
    void shouldDenyView_whenUnauthorized() throws Exception {
        when(security.hasPrivilege(info, "_dashboardManager", "r", null)).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("should create a dashboard and redirect so refresh does not repeat the save")
    void shouldSaveNewDashboard_whenValid() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("name", "  New <dashboard> & name  ");
        request.setParameter("description", " Description ");
        when(manager.addDashboard(eq(info), any())).thenReturn(true);
        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        ArgumentCaptor<Dashboard> captured = ArgumentCaptor.forClass(Dashboard.class);
        verify(manager).addDashboard(eq(info), captured.capture());
        assertThat(captured.getValue().getId()).isNull();
        assertThat(captured.getValue().getName()).isEqualTo("New <dashboard> & name");
        assertThat(captured.getValue().getDescription()).isEqualTo("Description");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/web/dashboard/admin/DashboardManager");
    }

    @Test
    @DisplayName("should pass only editable fields and leave the loaded dashboard untouched")
    void shouldEditMetadataOnly_whenExisting() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("dashboardId", "17");
        request.setParameter("name", "Renamed");
        request.setParameter("active", "true");
        request.setParameter("creator", "attacker");
        request.setParameter("locked", "true");
        when(manager.addDashboard(eq(info), any())).thenReturn(true);
        execute();
        ArgumentCaptor<Dashboard> captured = ArgumentCaptor.forClass(Dashboard.class);
        verify(manager).addDashboard(eq(info), captured.capture());
        assertThat(captured.getValue()).isNotSameAs(existing);
        assertThat(captured.getValue().getId()).isEqualTo(17);
        assertThat(captured.getValue().getCreator()).isNull();
        assertThat(captured.getValue().isActive()).isFalse();
        assertThat(captured.getValue().isLocked()).isFalse();
        assertThat(existing.getName()).isEqualTo("Original");
        assertThat(existing.isActive()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "oversize"})
    @DisplayName("should preserve the form and save nothing when the name is invalid")
    void shouldRejectName_whenInvalid(String name) throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("name", "oversize".equals(name) ? "x".repeat(256) : name);
        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("dashboardManagerError")).isNotNull();
        verify(manager, never()).addDashboard(any(), any());
    }

    @Test
    @DisplayName("should reject descriptions wider than the database column")
    void shouldRejectDescription_whenOversize() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("name", "Valid");
        request.setParameter("description", "x".repeat(256));
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).addDashboard(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "no-id", "2147483648"})
    @DisplayName("should reject malformed dashboard identifiers")
    void shouldRejectId_whenMalformed(String id) throws Exception {
        request.setParameter("dashboardId", id);
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should not turn a nonexistent edit into a new dashboard")
    void shouldRejectEdit_whenMissing() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "save");
        request.setParameter("dashboardId", "18");
        execute();
        assertThat(response.getStatus()).isEqualTo(404);
        verify(manager, never()).addDashboard(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "status"})
    @DisplayName("should refuse to edit or change status on a locked dashboard")
    void shouldRejectChange_whenLocked(String operation) throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", operation);
        request.setParameter("dashboardId", "17");
        existing.setLocked(true);
        execute();
        assertThat(response.getStatus()).isEqualTo(409);
        verify(manager, never()).addDashboard(any(), any());
        verify(manager, never()).toggleDashboardActive(any(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    @DisplayName("should explicitly set active status rather than flip it on repeated submission")
    void shouldSetStatus_whenValid(String active) throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "status");
        request.setParameter("dashboardId", "17");
        request.setParameter("active", active);
        when(manager.toggleDashboardActive(info, 17, Boolean.valueOf(active))).thenReturn(true);
        execute();
        verify(manager).toggleDashboardActive(info, 17, Boolean.valueOf(active));
        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    @DisplayName("should report a status conflict when the dashboard was locked or removed after loading")
    void shouldReportConflict_whenStatusUpdateRefused() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "status");
        request.setParameter("dashboardId", "17");
        request.setParameter("active", "false");
        execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should refuse an unknown operation without writing")
    void shouldRejectOperation_whenUnknown() throws Exception {
        request.setMethod("POST");
        request.setParameter("operation", "delete");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).addDashboard(any(), any());
        verify(manager, never()).toggleDashboardActive(any(), anyInt(), any());
    }
}
