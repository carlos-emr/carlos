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
            manager.toggleDashboardActive(info, 17, false);
            verify(security, times(2)).hasPrivilege(info, "_dashboardManager", "w", null);
            verifyNoInteractions(dao);
        }
    }

    @Test
    @DisplayName("should preserve creator, lock, and current active state when editing metadata")
    void shouldPreserveMetadata_whenEditing() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        Dashboard stored = new Dashboard();
        stored.setId(17);
        stored.setActive(false);
        stored.setCreator("999998");
        when(dao.find(17)).thenReturn(stored);
        Dashboard submitted = new Dashboard();
        submitted.setId(17);
        submitted.setName("Renamed");
        submitted.setDescription("Updated description");
        submitted.setActive(true);
        submitted.setCreator("untrusted");
        submitted.setLocked(true);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, submitted)).isTrue();
        }
        verify(dao).merge(stored);
        assertThat(stored.getName()).isEqualTo("Renamed");
        assertThat(stored.getDescription()).isEqualTo("Updated description");
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getCreator()).isEqualTo("999998");
        assertThat(stored.isLocked()).isFalse();
        assertThat(stored.getEdited()).isNotNull();
    }

    @Test
    @DisplayName("should refuse changes to a locked dashboard even when called outside the web action")
    void shouldRefuseChanges_whenLocked() {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        Dashboard stored = new Dashboard();
        stored.setId(17);
        stored.setLocked(true);
        when(dao.find(17)).thenReturn(stored);
        try (var log = mockStatic(LogAction.class)) {
            assertThat(manager.addDashboard(info, stored)).isFalse();
            manager.toggleDashboardActive(info, 17, false);
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
        }
        assertThat(dashboard.isActive()).isTrue();
        assertThat(dashboard.getCreator()).isEqualTo("999998");
        assertThat(dashboard.getEdited()).isNotNull();
    }
}
