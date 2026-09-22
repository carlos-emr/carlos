// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.dao.OceanSettingDao;
import io.github.carlos_emr.carlos.commn.model.OceanSetting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import jakarta.ws.rs.client.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("endpoint")
@DisplayName("Ocean settings REST contract")
class OceanServiceEndpointTest extends CarlosRestTestBase {
    @Mock private OceanSettingDao dao;
    @Mock private SecurityInfoManager security;

    @Override
    protected Object getServiceBean() {
        return new OceanService(dao, security);
    }

    @Test
    void shouldReturnNull_whenNoSettingsHaveBeenSaved() {
        try (var response = request().path("/ocean/getSettings").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("settings").isNull()).isTrue();
        }
        verify(dao).getSettings();
        verifyNoInteractions(security);
    }

    @Test
    void shouldReturnOpaqueSettings_whenAuthenticatedProviderReads() {
        OceanSetting setting = new OceanSetting();
        setting.setSettings("synthetic-encrypted-blob");
        when(dao.getSettings()).thenReturn(setting);
        try (var response = request().path("/ocean/getSettings").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            var json = responseJson(response);
            assertThat(json.path("settings").asText()).isEqualTo("synthetic-encrypted-blob");
            assertThat(json.size()).isEqualTo(1);
        }
    }

    @Test
    void shouldDenyMutation_whenAdminWritePrivilegeIsMissing() {
        try (var response = request().path("/ocean/saveSettings").post(Entity.json("{\"settings\":\"changed\"}"))) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
        verify(security).hasPrivilege(mockLoggedInInfo, "_admin", "w", null);
        verifyNoInteractions(dao);
    }

    @Test
    void shouldSaveAndAuditActor_whenAdminHasWritePrivilege() {
        allowWrite();
        OceanSetting setting = new OceanSetting();
        setting.setSettings("new opaque value");
        when(dao.saveSettings("new opaque value", "1001")).thenReturn(setting);
        try (var response = request().path("/ocean/saveSettings").post(Entity.json("{\"settings\":\"new opaque value\"}"))) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("settings").asText()).isEqualTo("new opaque value");
        }
        verify(dao).saveSettings("new opaque value", "1001");
    }

    @Test
    void shouldClearSettings_whenAdminExplicitlySendsNull() {
        allowWrite();
        when(dao.saveSettings(null, "1001")).thenReturn(new OceanSetting());
        try (var response = request().path("/ocean/saveSettings").post(Entity.json("{\"settings\":null}"))) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).path("settings").isNull()).isTrue();
        }
        verify(dao).saveSettings(null, "1001");
    }

    @Test
    void shouldRejectMissingRequest_insteadOfClearingSettings() {
        allowWrite();
        try (var response = request().path("/ocean/saveSettings").post(Entity.json("null"))) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRejectGet_whenCallingSaveEndpoint() {
        try (var response = request().path("/ocean/saveSettings").get()) {
            assertThat(response.getStatus()).isEqualTo(405);
        }
        verifyNoInteractions(dao);
    }

    private void allowWrite() {
        when(security.hasPrivilege(mockLoggedInInfo, "_admin", "w", null)).thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("1001");
    }
}
