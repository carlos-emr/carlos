/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.commn.dao.DashboardDao;
import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard administration service permissions and metadata")
class DashboardManagerAdministrationUnitTest {
    @Mock private SecurityInfoManager security;
    @Mock private DashboardDao dao;
    @Mock private LoggedInInfo info;
    @InjectMocks private DashboardManagerImpl manager;

    @Test
    @DisplayName("should refuse saves and status changes for a read-only user before accessing the DAO")
    void shouldRefuseWrites_whenReadOnly() {
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, new Dashboard())).isFalse();
            assertThat(manager.toggleDashboardActive(info, 17, false)).isFalse();
            verify(security, times(2)).hasPrivilege(info, "_dashboardManager", "w", null);
            verifyNoInteractions(dao);
            log.verify(() -> LogAction.addLog(info, "DashboardManager.addDashboard", null, null, null,
                    "User missing _dashboardManager role with write access"));
            log.verify(() -> LogAction.addLog(info, "DashboardManager.toggleDashboardActive", null, null, null,
                    "User missing _dashboardManager role with write access"));
        }
    }

    @Test
    @DisplayName("should preserve creator, lock, and current active state when editing metadata")
    void shouldPreserveMetadata_whenEditing() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        when(dao.updateMetadata(eq(17), eq("Renamed"), eq("Updated description"), any())).thenReturn(true);
        Dashboard submitted = new Dashboard();
        submitted.setId(17);
        submitted.setName("Renamed");
        submitted.setDescription("Updated description");
        submitted.setActive(true);
        submitted.setCreator("untrusted");
        submitted.setLocked(true);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, submitted)).isTrue();
            log.verify(() -> LogAction.addLog(info, "DashboardManager.addDashboard", "Dashboard", "17", null,
                    "Dashboard updated"));
        }
        verify(dao).updateMetadata(eq(17), eq("Renamed"), eq("Updated description"), any());
        verifyNoMoreInteractions(dao);
    }

    @Test
    @DisplayName("should return failure and emit no success audit when the guarded database update refuses a change")
    void shouldRefuseChanges_whenUpdateRefused() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        Dashboard stored = new Dashboard();
        stored.setId(17);
        stored.setLocked(true);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, stored)).isFalse();
            assertThat(manager.toggleDashboardActive(info, 17, false)).isFalse();
            log.verifyNoInteractions();
        }
        verify(dao, never()).merge(any());
        verify(dao, never()).persist(any());
    }

    @Test
    @DisplayName("should stamp creator and time and create new dashboards active")
    void shouldStampMetadata_whenCreating() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        Dashboard dashboard = new Dashboard();
        dashboard.setName("New dashboard");
        doAnswer(invocation -> { dashboard.setId(18); return null; }).when(dao).persist(dashboard);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, dashboard)).isTrue();
            log.verify(() -> LogAction.addLog(info, "DashboardManager.addDashboard", "Dashboard", "18", null,
                    "Dashboard created"));
        }
        assertThat(dashboard.isActive()).isTrue();
        assertThat(dashboard.getCreator()).isEqualTo("999998");
        assertThat(dashboard.getEdited()).isNotNull();
    }

    @Test
    @DisplayName("should audit the dashboard identifier and accepted active state")
    void shouldAuditStatus_whenUpdateAccepted() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        when(dao.updateActive(eq(17), eq(false), any())).thenReturn(true);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.toggleDashboardActive(info, 17, false)).isTrue();
            log.verify(() -> LogAction.addLog(info, "DashboardManager.toggleDashboardActive", "Dashboard", "17", null,
                    "Dashboard active state set to false"));
        }
        verify(dao).updateActive(eq(17), eq(false), any());
        verifyNoMoreInteractions(dao);
    }
}
