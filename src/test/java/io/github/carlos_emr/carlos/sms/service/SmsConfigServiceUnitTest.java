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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.event.SmsConfigChangedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
class SmsConfigServiceUnitTest {
    private final SmsConfigDao smsConfigDao = mock(SmsConfigDao.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private String originalKey;

    @BeforeEach
    void seedEncryptionKey() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @DisplayName("save creates the settings row the first time and normalizes the sender number")
    void shouldCreateSettings_whenNoneStored() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        SmsConfig saved = service().save(update(SmsProviderType.STUB, true, "416-555-1212", "hook-secret", false,
                Map.of()), "999998");

        verify(smsConfigDao).persist(saved);
        verify(smsConfigDao, never()).merge(any());
        assertThat(saved)
                .extracting(SmsConfig::getProviderType, SmsConfig::isEnabled, SmsConfig::getSenderNumber,
                        SmsConfig::getWebhookSecret, SmsConfig::getUpdatedBy)
                .containsExactly(SmsProviderType.STUB, true, "+14165551212", "hook-secret", "999998");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save keeps the stored webhook secret when the field is left blank")
    void shouldKeepWebhookSecret_whenFieldIsBlank() {
        SmsConfig stored = new SmsConfig();
        stored.setWebhookSecret("original-secret");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998");

        verify(smsConfigDao).merge(stored);
        assertThat(saved.getWebhookSecret()).isEqualTo("original-secret");
        assertThat(saved.getSenderNumber()).isNull();
    }

    @Test
    @DisplayName("save replaces the webhook secret when a new one is typed, and clears it when asked")
    void shouldReplaceOrClearWebhookSecret_whenRequested() {
        SmsConfig stored = new SmsConfig();
        stored.setWebhookSecret("original-secret");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThat(service().save(update(SmsProviderType.STUB, true, "", "new-secret", false, Map.of()), "999998")
                .getWebhookSecret()).isEqualTo("new-secret");
        assertThat(service().save(update(SmsProviderType.STUB, true, "", "", true, Map.of()), "999998")
                .hasWebhookSecret()).isFalse();
    }

    @Test
    @DisplayName("save stores only the credentials the provider declares, keeps blank ones, and drops the rest")
    void shouldStoreDeclaredCredentialsOnly_forProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setCredential("api_username", "old-user");
        stored.setCredential("api_password", "old-password");
        stored.setCredential("legacy_token", "stale");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.VOIPMS, true, "", "", false,
                Map.of("api_username", "new-user", "api_password", "", "injected", "x")), "999998");

        assertThat(saved.getCredential("api_username")).isEqualTo("new-user");
        assertThat(saved.getCredential("api_password")).isEqualTo("old-password");
        assertThat(saved.hasCredential("legacy_token")).isFalse();
        assertThat(saved.hasCredential("injected")).isFalse();
    }

    @Test
    @DisplayName("save announces the scheduler setting so the scheduler can start or stop without a restart")
    void shouldPublishSchedulerSetting_whenSaved() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        service().save(new SmsConfigUpdateDto(SmsProviderType.STUB, true, true, "", "", false, Map.of()), "999998");

        ArgumentCaptor<SmsConfigChangedEvent> event = ArgumentCaptor.forClass(SmsConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().schedulerEnabled()).isTrue();
    }

    @Test
    @DisplayName("sending stays on while nothing is stored, and follows the stored switch once saved")
    void shouldReportSendingEnabled_fromStoredSwitch() {
        SmsConfig disabled = new SmsConfig();
        disabled.setEnabled(false);
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty(), Optional.of(disabled));

        assertThat(service().sendingEnabled()).isTrue();
        assertThat(service().sendingEnabled()).isFalse();
    }

    @Test
    @DisplayName("stored provider and scheduler settings are empty until something is saved")
    void shouldReturnEmptySettings_whenNothingStored() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        assertThat(service().storedProvider()).isEmpty();
        assertThat(service().storedSchedulerEnabled()).isEmpty();
    }

    @Test
    @DisplayName("credential fields come from the provider client, and are empty for a provider with no client")
    void shouldListCredentialFields_fromProviderClient() {
        assertThat(service().credentialFields(SmsProviderType.VOIPMS)).containsExactly("api_username", "api_password");
        assertThat(service().credentialFields(SmsProviderType.STUB)).isEmpty();
        assertThat(service().credentialFields(SmsProviderType.CLOUDLI)).isEmpty();
    }

    private SmsConfigService service() {
        return new SmsConfigService(smsConfigDao, providerClients(), eventPublisher);
    }

    static SmsProviderClientResolver providerClients() {
        StubSmsProviderClient voipMs = new StubSmsProviderClient() {
            @Override
            public SmsProviderType providerType() {
                return SmsProviderType.VOIPMS;
            }

            @Override
            public List<String> credentialFields() {
                return List.of("api_username", "api_password");
            }
        };
        return new SmsProviderClientResolver(List.of(new StubSmsProviderClient(), voipMs));
    }

    private static SmsConfigUpdateDto update(SmsProviderType providerType, boolean enabled, String senderNumber,
                                             String webhookSecret, boolean clearWebhookSecret,
                                             Map<String, String> credentials) {
        return new SmsConfigUpdateDto(providerType, enabled, false, senderNumber, webhookSecret, clearWebhookSecret,
                credentials);
    }
}
