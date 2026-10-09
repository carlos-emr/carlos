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
import io.github.carlos_emr.carlos.sms.service.SmsCredentialField;
import io.github.carlos_emr.carlos.sms.service.SmsDefaultProviderResolver;
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

import java.util.List;
import java.util.Map;
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
        when(configService.providerReady(org.mockito.ArgumentMatchers.any())).thenReturn(true);
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
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "version", 7);
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of(
                new SmsCredentialField("field_two", "sms.test.fieldTwo", true),
                new SmsCredentialField("field_one", "sms.test.fieldOne", false)));
        when(scheduler.isRunning()).thenReturn(true);

        SmsConfigViewModel model = assembler().assemble("saved", List.of());

        assertThat(model)
                .extracting(SmsConfigViewModel::providerType, SmsConfigViewModel::enabled,
                        SmsConfigViewModel::schedulerEnabled, SmsConfigViewModel::schedulerRunning,
                        SmsConfigViewModel::senderNumber, SmsConfigViewModel::webhookSecretSet,
                        SmsConfigViewModel::stored, SmsConfigViewModel::resultKey)
                .containsExactly("STUB", true, true, true, "+14165551212", true, true, "sms.config.result.saved");
        assertThat(model.version()).as("sent back with the next save").isEqualTo("7");
        assertThat(model.credentialFields()).containsExactly(
                new SmsConfigViewModel.CredentialField("field_two", "sms.test.fieldTwo", true, true),
                new SmsConfigViewModel.CredentialField("field_one", "sms.test.fieldOne", false, false));
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
        assertThat(model.version()).as("nothing saved yet").isEmpty();
    }

    @Test
    @DisplayName("turns only known result codes into messages, so the query string cannot inject text")
    void shouldIgnoreResultCode_whenCodeIsUnknown() {
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
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "version", 8);
        when(configService.current()).thenReturn(Optional.of(stored));
        SmsConfigUpdateDto submitted = new SmsConfigUpdateDto(
                SmsProviderType.STUB, false, true, "not-a-number", "new-secret-value", false, Map.of(), 7);

        SmsConfigViewModel model = assembler().assembleRejected(submitted, List.of("sms.config.error.senderNumber"));

        assertThat(model)
                .extracting(SmsConfigViewModel::enabled, SmsConfigViewModel::schedulerEnabled,
                        SmsConfigViewModel::senderNumber, SmsConfigViewModel::webhookSecretSet,
                        SmsConfigViewModel::resultKey, SmsConfigViewModel::errorKeys)
                .containsExactly(false, true, "not-a-number", true, "", List.of("sms.config.error.senderNumber"));
        assertThat(model.toString()).doesNotContain("new-secret-value").doesNotContain("webhook-value-123");
        assertThat(model.version()).as("the form keeps the version it was based on, not the stored one")
                .isEqualTo("7");
    }

    @Test
    @DisplayName("still opens, with a warning, when the stored credentials cannot be read")
    void shouldWarn_whenStoredCredentialsAreUnreadable() {
        SmsConfig stored = new SmsConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "credentialsJson", "{not json");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of(
                new SmsCredentialField("field_one", "sms.test.fieldOne", false)));

        SmsConfigViewModel model = assembler().assemble(null, List.of());

        assertThat(model.errorKeys()).containsExactly("sms.config.error.credentialsUnreadable");
        assertThat(model.credentialFields())
                .containsExactly(new SmsConfigViewModel.CredentialField("field_one", "sms.test.fieldOne", false, false));
    }

    @Test
    @DisplayName("warns, and shows the field as not stored, when a stored credential no longer decrypts")
    void shouldWarn_whenStoredCredentialNoLongerDecrypts() throws Exception {
        SmsConfig stored = new SmsConfig();
        stored.setCredential("field_one", "value one");
        stored.setCredential("field_two", "value two");
        EncryptionKeyTestSupport.seedFreshKey();
        stored.setCredential("field_two", "entered again");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of(
                new SmsCredentialField("field_one", "sms.test.fieldOne", true),
                new SmsCredentialField("field_two", "sms.test.fieldTwo", true)));

        SmsConfigViewModel model = assembler().assemble(null, List.of());

        assertThat(model.errorKeys()).containsExactly("sms.config.error.credentialsUnreadable");
        assertThat(model.credentialFields()).extracting(SmsConfigViewModel.CredentialField::set)
                .as("only the value entered under the current key counts").containsExactly(false, true);
    }

    @Test
    @DisplayName("shows another provider's stored credentials as not stored, since saving the swap clears them")
    void shouldShowCredentialsAsNotStored_whenTheyBelongToAnotherProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential("field_one", "another provider's value");
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.credentialFields(SmsProviderType.STUB)).thenReturn(List.of(
                new SmsCredentialField("field_one", "sms.test.fieldOne", true)));
        SmsConfigUpdateDto submitted = new SmsConfigUpdateDto(SmsProviderType.STUB, true, false, "", "", false,
                Map.of(), null);

        SmsConfigViewModel model = assembler().assembleRejected(submitted, List.of("sms.config.error.credentialRequired"));

        assertThat(model.providerType()).isEqualTo("STUB");
        assertThat(model.credentialFields())
                .containsExactly(new SmsConfigViewModel.CredentialField("field_one", "sms.test.fieldOne", true, false));
    }

    @Test
    void shouldShowReadinessError_whenSavedProviderIsNotReady() {
        SmsConfig stored = new SmsConfig();
        stored.setEnabled(true);
        when(configService.current()).thenReturn(Optional.of(stored));
        when(configService.providerReady(SmsProviderType.STUB)).thenReturn(false);

        SmsConfigViewModel page = assembler().assemble(null, List.of());

        assertThat(page.enabled()).isTrue();
        assertThat(page.errorKeys()).containsExactly("sms.config.error.providerNotReady");
    }

    @Test
    void shouldShowReadinessError_whenPropertyProviderIsNotReady() {
        when(configService.current()).thenReturn(Optional.empty());
        when(configService.providerReady(SmsProviderType.STUB)).thenReturn(false);

        assertThat(assembler().assemble(null, List.of()).errorKeys())
                .containsExactly("sms.config.error.providerNotReady");
    }

    @Test
    void shouldShowReadinessErrorOnce_whenValidationAlreadyIncludedIt() {
        when(configService.current()).thenReturn(Optional.empty());
        when(configService.providerReady(SmsProviderType.STUB)).thenReturn(false);

        assertThat(assembler().assemble(null, List.of("sms.config.error.providerNotReady")).errorKeys())
                .containsExactly("sms.config.error.providerNotReady");
    }

    private SmsConfigViewModelAssembler assembler() {
        when(providerResolver.configuredDefault()).thenReturn(SmsProviderType.STUB);
        return new SmsConfigViewModelAssembler(configService, clients, providerResolver, scheduler, () -> true);
    }
}
