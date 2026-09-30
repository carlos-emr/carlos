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
import io.github.carlos_emr.carlos.sms.service.SmsProviderClient;
import io.github.carlos_emr.carlos.sms.service.SmsProviderClientResolver;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.service.StubSmsProviderClient;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

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
        stored.setCredential(SmsProviderType.STUB, "field_two", "value two");
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
        assertThat(model.credentialsStored()).isTrue();
        assertThat(model.toString()).doesNotContain("webhook-value-123").doesNotContain("value two");
    }

    @Test
    @DisplayName("shows whether each field is stored for the shown provider only, not for another provider")
    void shouldShowStoredFlags_forShownProviderOnly() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        stored.setCredential(SmsProviderType.CLOUDLI, "field_two", "cloudli-value");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.VOIPMS)).thenReturn(List.of("field_one", "field_two"));

        SmsConfigViewModel model = assembler(installed(SmsProviderType.VOIPMS, SmsProviderType.CLOUDLI))
                .assemble(null, List.of());

        assertThat(model.providerType()).isEqualTo("VOIPMS");
        assertThat(model.credentialFields()).containsExactly(
                new SmsConfigViewModel.CredentialField("field_one", true),
                new SmsConfigViewModel.CredentialField("field_two", false));
        assertThat(model.credentialsStored()).isTrue();
    }

    @Test
    @DisplayName("offers no removal when only another provider has stored credentials")
    void shouldNotFlagStoredCredentials_whenOnlyAnotherProviderHasThem() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.CLOUDLI, "field_one", "cloudli-value");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.VOIPMS)).thenReturn(List.of("field_one"));

        SmsConfigViewModel model = assembler(installed(SmsProviderType.VOIPMS, SmsProviderType.CLOUDLI))
                .assemble(null, List.of());

        assertThat(model.credentialFields())
                .containsExactly(new SmsConfigViewModel.CredentialField("field_one", false));
        assertThat(model.credentialsStored()).isFalse();
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
    void shouldIgnoreResultCode_whenCodeIsUnknown() {
        when(configService.current()).thenReturn(Optional.empty());

        assertThat(assembler().assemble("testSent", List.of()).resultKey()).isEqualTo("sms.config.result.testSent");
        assertThat(assembler().assemble("<script>", List.of()).resultKey()).isEmpty();
    }

    @Test
    @DisplayName("after a save that ignored typed credentials, shows a fixed message and no credential")
    void shouldShowCredentialsNotSavedMessage_withoutAnyValue() throws Exception {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.CLOUDLI);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        when(configService.current()).thenReturn(Optional.of(stored));

        SmsConfigViewModel model = assembler().assemble("savedWithoutCredentials", List.of());

        assertThat(model.resultKey()).isEqualTo("sms.config.result.savedWithoutCredentials");
        assertThat(model.toString()).doesNotContain("voipms-value").doesNotContain("{ENC}");
        Properties english = new Properties();
        try (InputStream bundle = getClass().getResourceAsStream("/oscarResources_en.properties")) {
            english.load(bundle);
        }
        // Fixed text with no placeholder, so no value can be put into it.
        assertThat(english.getProperty(model.resultKey()))
                .contains("the credentials you typed were not saved")
                .doesNotContain("{");
    }

    @Test
    @DisplayName("passes validation errors through as message keys")
    void shouldCarryErrorKeys_forPage() {
        when(configService.current()).thenReturn(Optional.empty());

        SmsConfigViewModel model = assembler().assemble(null, List.of("sms.config.error.senderNumber"));

        assertThat(model.errorKeys()).containsExactly("sms.config.error.senderNumber");
    }

    @Test
    @DisplayName("still opens, showing STUB and a warning, when sms.provider.default names an unknown provider")
    void shouldShowStubWithWarning_whenPropertyProviderIsInvalid() {
        when(configService.current()).thenReturn(Optional.empty());
        SmsConfigViewModelAssembler assembler = assembler();
        when(providerResolver.configuredDefault()).thenThrow(new IllegalStateException("unknown provider"));

        SmsConfigViewModel model = assembler.assemble(null, List.of("sms.config.error.senderNumber"));

        assertThat(model.providerType()).isEqualTo("STUB");
        assertThat(model.errorKeys())
                .containsExactly("sms.config.error.senderNumber", "sms.config.error.invalidPropertyProvider");
    }

    @Test
    @DisplayName("after a rejected save, shows what was submitted rather than the stored settings, and no secret")
    void shouldShowSubmittedValues_whenSaveWasRejected() {
        SmsConfig stored = new SmsConfig();
        stored.setEnabled(true);
        stored.setSenderNumber("+14165551212");
        stored.setWebhookSecret("webhook-value-123");
        when(configService.current()).thenReturn(Optional.of(stored));
        SmsConfigUpdateDto submitted = new SmsConfigUpdateDto(
                SmsProviderType.STUB, false, true, "not-a-number", "new-secret-value", false, Map.of(), false);

        SmsConfigViewModel model = assembler().assembleRejected(submitted, List.of("sms.config.error.senderNumber"));

        assertThat(model)
                .extracting(SmsConfigViewModel::enabled, SmsConfigViewModel::schedulerEnabled,
                        SmsConfigViewModel::senderNumber, SmsConfigViewModel::webhookSecretSet,
                        SmsConfigViewModel::resultKey, SmsConfigViewModel::errorKeys)
                .containsExactly(false, true, "not-a-number", true, "", List.of("sms.config.error.senderNumber"));
        assertThat(model.toString()).doesNotContain("new-secret-value").doesNotContain("webhook-value-123");
    }

    @Test
    @DisplayName("after a rejected save, shows the submitted provider's stored credentials, not the saved provider's")
    void shouldShowSubmittedProvidersStoredFlags_whenSaveWasRejected() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        stored.setCredential(SmsProviderType.CLOUDLI, "account_id", "cloudli-value");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.VOIPMS)).thenReturn(List.of("field_one"));
        when(configService.credentialFields(SmsProviderType.CLOUDLI)).thenReturn(List.of("account_id", "field_one"));
        SmsConfigUpdateDto submitted = new SmsConfigUpdateDto(
                SmsProviderType.CLOUDLI, true, false, "not-a-number", "", false, Map.of(), false);

        SmsConfigViewModel model = assembler(installed(SmsProviderType.VOIPMS, SmsProviderType.CLOUDLI))
                .assembleRejected(submitted, List.of("sms.config.error.senderNumber"));

        assertThat(model.providerType()).isEqualTo("CLOUDLI");
        assertThat(model.credentialFields()).containsExactly(
                new SmsConfigViewModel.CredentialField("account_id", true),
                new SmsConfigViewModel.CredentialField("field_one", false));
        assertThat(model.credentialsStored()).isTrue();
    }

    @Test
    @DisplayName("after a rejected save, offers no removal when the submitted provider has no stored credentials")
    void shouldNotFlagStoredCredentials_whenRejectedProviderHasNone() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.CLOUDLI)).thenReturn(List.of("field_one"));
        SmsConfigUpdateDto submitted = new SmsConfigUpdateDto(
                SmsProviderType.CLOUDLI, true, false, "not-a-number", "", false, Map.of(), false);

        SmsConfigViewModel model = assembler(installed(SmsProviderType.VOIPMS, SmsProviderType.CLOUDLI))
                .assembleRejected(submitted, List.of("sms.config.error.senderNumber"));

        assertThat(model.credentialFields())
                .containsExactly(new SmsConfigViewModel.CredentialField("field_one", false));
        assertThat(model.credentialsStored()).isFalse();
    }

    @Test
    @DisplayName("still opens, with a warning, when the stored credentials cannot be read")
    void shouldWarn_whenStoredCredentialsAreUnreadable() {
        SmsConfig stored = new SmsConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "credentialsJson", "{not json");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of("field_one"));

        SmsConfigViewModel model = assembler().assemble(null, List.of());

        assertThat(model.errorKeys()).containsExactly("sms.config.error.credentialsUnreadable");
        assertThat(model.credentialFields())
                .containsExactly(new SmsConfigViewModel.CredentialField("field_one", false));
        assertThat(model.credentialsStored()).isFalse();
    }

    private SmsConfigViewModelAssembler assembler() {
        return assembler(clients);
    }

    private SmsConfigViewModelAssembler assembler(SmsProviderClientResolver installedClients) {
        when(providerResolver.configuredDefault()).thenReturn(SmsProviderType.STUB);
        return new SmsConfigViewModelAssembler(configService, installedClients, providerResolver, scheduler,
                () -> true);
    }

    /** STUB plus a client for each given provider, so the page can show and re-select them. */
    private static SmsProviderClientResolver installed(SmsProviderType... providerTypes) {
        List<SmsProviderClient> installed = new ArrayList<>();
        installed.add(new StubSmsProviderClient());
        for (SmsProviderType providerType : providerTypes) {
            installed.add(new StubSmsProviderClient() {
                @Override
                public SmsProviderType providerType() {
                    return providerType;
                }
            });
        }
        return new SmsProviderClientResolver(installed);
    }
}
