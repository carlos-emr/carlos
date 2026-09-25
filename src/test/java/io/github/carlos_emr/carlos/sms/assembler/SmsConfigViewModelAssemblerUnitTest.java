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
package io.github.carlos_emr.carlos.sms.assembler;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsDefaultProviderResolver;
import io.github.carlos_emr.carlos.sms.service.SmsProviderClientResolver;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.service.StubSmsProviderClient;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
class SmsConfigViewModelAssemblerUnitTest {
    private final SmsConfigService configService = mock(SmsConfigService.class);
    private final SmsQueueScheduler scheduler = mock(SmsQueueScheduler.class);
    private final SmsDefaultProviderResolver providerResolver = mock(SmsDefaultProviderResolver.class);
    private final SmsProviderClientResolver clients =
            new SmsProviderClientResolver(List.of(new StubSmsProviderClient()));
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
    @DisplayName("shows the saved settings, and only whether each secret is set, never its value")
    void shouldShowSavedSettings_withoutSecretValues() {
        SmsConfig stored = new SmsConfig();
        stored.setEnabled(true);
        stored.setSchedulerEnabled(true);
        stored.setSenderNumber("+14165551212");
        stored.setWebhookSecret("webhook-value-123");
        stored.setCredential("field_two", "value two");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of("field_two", "field_one"));
        when(scheduler.isRunning()).thenReturn(true);

        SmsConfigViewModel model = assembler().assemble("saved", List.of());

        assertThat(model)
                .extracting(SmsConfigViewModel::providerType, SmsConfigViewModel::enabled,
                        SmsConfigViewModel::schedulerEnabled, SmsConfigViewModel::schedulerRunning,
                        SmsConfigViewModel::senderNumber, SmsConfigViewModel::webhookSecretSet,
                        SmsConfigViewModel::stored, SmsConfigViewModel::resultKey)
                .containsExactly("STUB", true, true, true, "+14165551212", true, true, "sms.config.result.saved");
        assertThat(model.credentialFields()).containsExactly(
                new SmsConfigViewModel.CredentialField("field_two", true),
                new SmsConfigViewModel.CredentialField("field_one", false));
        assertThat(model.toString()).doesNotContain("webhook-value-123").doesNotContain("value two");
    }

    @Test
    @DisplayName("shows the property defaults while nothing is saved: sending on, provider from the property")
    void shouldShowPropertyDefaults_whenNothingSaved() {
        when(configService.current()).thenReturn(Optional.empty());

        SmsConfigViewModel model = assembler().assemble(null, List.of());

        assertThat(model)
                .extracting(SmsConfigViewModel::providerType, SmsConfigViewModel::enabled,
                        SmsConfigViewModel::stored, SmsConfigViewModel::webhookSecretSet,
                        SmsConfigViewModel::resultKey)
                .containsExactly("STUB", true, false, false, "");
        assertThat(model.providerOptions()).containsExactly("STUB");
    }

    @Test
    @DisplayName("turns only known result codes into messages, so the query string cannot inject text")
    void shouldMapKnownResultCodes_only() {
        when(configService.current()).thenReturn(Optional.empty());

        assertThat(assembler().assemble("testSent", List.of()).resultKey()).isEqualTo("sms.config.result.testSent");
        assertThat(assembler().assemble("<script>", List.of()).resultKey()).isEmpty();
    }

    @Test
    @DisplayName("passes validation errors through as message keys")
    void shouldCarryErrorKeys_forPage() {
        when(configService.current()).thenReturn(Optional.empty());

        SmsConfigViewModel model = assembler().assemble(null, List.of("sms.config.error.senderNumber"));

        assertThat(model.errorKeys()).containsExactly("sms.config.error.senderNumber");
    }

    private SmsConfigViewModelAssembler assembler() {
        when(providerResolver.configuredDefault()).thenReturn(SmsProviderType.STUB);
        return new SmsConfigViewModelAssembler(configService, clients, providerResolver, scheduler, () -> true);
    }
}
