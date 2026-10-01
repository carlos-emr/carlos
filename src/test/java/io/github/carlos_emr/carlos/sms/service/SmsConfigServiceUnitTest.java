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
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
class SmsConfigServiceUnitTest {
    private final SmsConfigDao smsConfigDao = mock(SmsConfigDao.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SmsConfigAuditRecorder auditRecorder = mock(SmsConfigAuditRecorder.class);
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

        SmsConfig saved = service().save(update(SmsProviderType.STUB, true, "416-555-1212", "webhook-value", false,
                Map.of()), "999998");

        verify(smsConfigDao).persist(saved);
        verify(smsConfigDao, never()).merge(any());
        assertThat(saved)
                .extracting(SmsConfig::getProviderType, SmsConfig::isEnabled, SmsConfig::getSenderNumber,
                        SmsConfig::getWebhookSecret, SmsConfig::getUpdatedBy)
                .containsExactly(SmsProviderType.STUB, true, "+14165551212", "webhook-value", "999998");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save keeps the stored webhook secret when the field is left blank")
    void shouldKeepWebhookSecret_whenFieldIsBlank() {
        SmsConfig stored = new SmsConfig();
        stored.setWebhookSecret("original-webhook-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998");

        verify(smsConfigDao).merge(stored);
        assertThat(saved.getWebhookSecret()).isEqualTo("original-webhook-value");
        assertThat(saved.getSenderNumber()).isNull();
    }

    @Test
    @DisplayName("save replaces the webhook secret when a new one is typed, and clears it when asked")
    void shouldReplaceOrClearWebhookSecret_whenRequested() {
        SmsConfig stored = new SmsConfig();
        stored.setWebhookSecret("original-webhook-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThat(service().save(update(SmsProviderType.STUB, true, "", "new-webhook-value", false, Map.of()), "999998")
                .getWebhookSecret()).isEqualTo("new-webhook-value");
        assertThat(service().save(update(SmsProviderType.STUB, true, "", "", true, Map.of()), "999998")
                .hasWebhookSecret()).isFalse();
    }

    @Test
    @DisplayName("save stores only the credentials the provider declares, keeps blank ones, and drops the rest")
    void shouldStoreDeclaredCredentialsOnly_forProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "old-value-one");
        stored.setCredential(SmsProviderType.VOIPMS, "field_two", "old-value-two");
        stored.setCredential(SmsProviderType.VOIPMS, "legacy_field", "stale-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.VOIPMS, true, "", "", false,
                Map.of("field_one", "new-value-one", "field_two", "", "injected", "x")), "999998");

        assertThat(saved.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("new-value-one");
        assertThat(saved.getCredential(SmsProviderType.VOIPMS, "field_two")).isEqualTo("old-value-two");
        assertThat(saved.hasCredential(SmsProviderType.VOIPMS, "legacy_field")).isFalse();
        assertThat(saved.hasCredential(SmsProviderType.VOIPMS, "injected")).isFalse();
    }

    @Test
    @DisplayName("switching provider keeps the first provider's credentials, and switching back finds them")
    void shouldKeepOtherProvidersCredentials_whenSwitchingProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value-one");
        stored.setCredential(SmsProviderType.VOIPMS, "field_two", "voipms-value-two");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));
        SmsConfigService service = service(providerClientsWithCloudli());

        service.save(credentialsUpdate(SmsProviderType.CLOUDLI, Map.of("account_id", "cloudli-value"), false),
                "999998");

        assertThat(stored.getCredential(SmsProviderType.CLOUDLI, "account_id")).isEqualTo("cloudli-value");
        assertThat(stored.credentialNames(SmsProviderType.VOIPMS)).containsExactlyInAnyOrder("field_one", "field_two");

        SmsConfig switchedBack = service.save(credentialsUpdate(SmsProviderType.VOIPMS, Map.of(), false), "999998");

        assertThat(switchedBack.getProviderType()).isEqualTo(SmsProviderType.VOIPMS);
        assertThat(switchedBack.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("voipms-value-one");
        assertThat(switchedBack.getCredential(SmsProviderType.VOIPMS, "field_two")).isEqualTo("voipms-value-two");
        assertThat(switchedBack.getCredential(SmsProviderType.CLOUDLI, "account_id")).isEqualTo("cloudli-value");
    }

    @Test
    @DisplayName("save removes names the chosen provider no longer declares, and leaves other providers' alone")
    void shouldRemoveUndeclaredNames_onlyForChosenProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value-one");
        stored.setCredential(SmsProviderType.VOIPMS, "legacy_field", "stale-value");
        stored.setCredential(SmsProviderType.CLOUDLI, "legacy_field", "cloudli-legacy-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service(providerClientsWithCloudli())
                .save(credentialsUpdate(SmsProviderType.VOIPMS, Map.of(), false), "999998");

        assertThat(saved.credentialNames(SmsProviderType.VOIPMS)).containsExactly("field_one");
        assertThat(saved.getCredential(SmsProviderType.CLOUDLI, "legacy_field")).isEqualTo("cloudli-legacy-value");
    }

    @Test
    @DisplayName("save removes the chosen provider's stored credentials when asked, and only that provider's")
    void shouldRemoveChosenProvidersCredentials_whenClearIsRequested() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value-one");
        stored.setCredential(SmsProviderType.VOIPMS, "field_two", "voipms-value-two");
        stored.setCredential(SmsProviderType.CLOUDLI, "account_id", "cloudli-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service(providerClientsWithCloudli())
                .save(credentialsUpdate(SmsProviderType.VOIPMS, Map.of(), true), "999998");

        assertThat(saved.credentialNames(SmsProviderType.VOIPMS)).isEmpty();
        assertThat(saved.getCredential(SmsProviderType.CLOUDLI, "account_id")).isEqualTo("cloudli-value");
    }

    @Test
    @DisplayName("save that removes the stored credentials and types new ones ends with just the new ones")
    void shouldKeepOnlyNewValues_whenClearAndNewValuesAreSavedTogether() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "old-value-one");
        stored.setCredential(SmsProviderType.VOIPMS, "field_two", "old-value-two");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(
                credentialsUpdate(SmsProviderType.VOIPMS, Map.of("field_one", "new-value-one"), true), "999998");

        assertThat(saved.credentialNames(SmsProviderType.VOIPMS)).containsExactly("field_one");
        assertThat(saved.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("new-value-one");
    }

    @Test
    @DisplayName("save announces the scheduler setting so the scheduler can start or stop without a restart")
    void shouldPublishSchedulerSetting_whenSaved() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        service().save(new SmsConfigUpdateDto(SmsProviderType.STUB, true, true, "", "", false, Map.of(), false, SmsProviderType.STUB, java.util.Set.of()),
                "999998");

        ArgumentCaptor<SmsConfigChangedEvent> event = ArgumentCaptor.forClass(SmsConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().schedulerEnabled()).isTrue();
    }

    @Test
    @DisplayName("save audits which settings changed, by name only")
    void shouldAuditChangedSettings_byNameOnly() {
        SmsConfig stored = new SmsConfig();
        stored.setEnabled(true);
        stored.setWebhookSecret("old-webhook-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service().save(update(SmsProviderType.STUB, false, "", "new-webhook-value", false, Map.of()), "999998");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> changed = ArgumentCaptor.forClass(List.class);
        verify(auditRecorder).recordSaved(org.mockito.ArgumentMatchers.same(stored),
                org.mockito.ArgumentMatchers.eq("999998"), changed.capture());
        assertThat(changed.getValue()).containsExactly("enabled", "webhookSecret");
        assertThat(changed.getValue().toString()).doesNotContain("webhook-value");
    }

    @Test
    @DisplayName("save audits a credential change by provider name only, never a value")
    void shouldAuditCredentialChange_byProviderNameOnly() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "old-value-one");
        stored.setCredential(SmsProviderType.CLOUDLI, "account_id", "cloudli-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service(providerClientsWithCloudli()).save(
                credentialsUpdate(SmsProviderType.VOIPMS, Map.of("field_one", "new-value-one"), false), "999998");

        List<String> changed = auditedChanges(stored);
        assertThat(changed).containsExactly("credentials:VOIPMS");
        assertThat(String.join(",", changed)).doesNotContain("value").doesNotContain("{ENC}");
    }

    @Test
    @DisplayName("save audits removing the chosen provider's credentials as a change to that provider's credentials")
    void shouldAuditClearedCredentials_asThatProvidersChange() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "old-value-one");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service().save(credentialsUpdate(SmsProviderType.VOIPMS, Map.of(), true), "999998");

        assertThat(auditedChanges(stored)).containsExactly("credentials:VOIPMS");
    }

    @Test
    @DisplayName("save audits a provider switch without a credential change, since no credential changed")
    void shouldNotAuditCredentials_whenOnlyProviderSwitches() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value-one");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service(providerClientsWithCloudli()).save(credentialsUpdate(SmsProviderType.CLOUDLI, Map.of(), false),
                "999998");

        assertThat(auditedChanges(stored)).containsExactly("providerType");
    }

    @Test
    @DisplayName("save of a row in the earlier flat shape keeps its credentials with the stored provider, unaudited")
    void shouldKeepFlatCredentialsWithStoredProvider_whenSwitchingProvider() throws Exception {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        // The earlier flat shape: one object of field name to encrypted value, for the row's provider.
        ReflectionTestUtils.setField(stored, "credentialsJson",
                "{\"field_one\":\"" + EncryptionUtils.encrypt("voipms-value-one") + "\"}");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service(providerClientsWithCloudli())
                .save(credentialsUpdate(SmsProviderType.CLOUDLI, Map.of(), false), "999998");

        assertThat(saved.credentialNames(SmsProviderType.CLOUDLI)).isEmpty();
        assertThat(saved.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("voipms-value-one");
        assertThat(saved.storedCredentials()).startsWith("{\"VOIPMS\":{");
        assertThat(auditedChanges(stored)).containsExactly("providerType");
    }

    @Test
    @DisplayName("save audits replacing unreadable stored credentials as plain credentials")
    void shouldAuditPlainCredentials_whenStoredCredentialsWereUnreadable() {
        SmsConfig stored = new SmsConfig();
        stored.setProviderType(SmsProviderType.VOIPMS);
        ReflectionTestUtils.setField(stored, "credentialsJson", "{not json");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service().save(credentialsUpdate(SmsProviderType.VOIPMS, Map.of("field_one", "value one"), false), "999998");

        assertThat(auditedChanges(stored)).containsExactly("credentials");
    }

    @Test
    @DisplayName("save replaces stored credentials that cannot be read, instead of failing")
    void shouldReplaceUnreadableCredentials_whenSaving() {
        SmsConfig stored = new SmsConfig();
        ReflectionTestUtils.setField(stored, "credentialsJson", "{not json");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.VOIPMS, true, "", "", false,
                Map.of("field_one", "value one")), "999998");

        assertThat(saved.credentialsReadable()).isTrue();
        assertThat(saved.credentialNames(SmsProviderType.VOIPMS)).containsExactly("field_one");
    }

    @Test
    @DisplayName("save reports a conflict and announces nothing when another save changed the row first")
    void shouldReportConflict_whenVersionCheckFails() {
        SmsConfig stored = new SmsConfig();
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));
        OptimisticLockException stale = new OptimisticLockException("row was updated");
        doThrow(stale).when(smsConfigDao).flush();

        assertThatThrownBy(() -> service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998"))
                .isInstanceOf(SmsConfigConflictException.class)
                .hasCause(stale);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(auditRecorder, never()).recordSaved(any(), any(), any());
    }

    @Test
    @DisplayName("save reports a conflict when another first save created the row first")
    void shouldReportConflict_whenRowWasCreatedConcurrently() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());
        doThrow(new PersistenceException("could not insert",
                new java.sql.SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)))
                .when(smsConfigDao).flush();

        assertThatThrownBy(() -> service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998"))
                .isInstanceOf(SmsConfigConflictException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("save reports a conflict when MariaDB says the row changed since it was read")
    void shouldReportConflict_whenRecordChangedSinceLastRead() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(new SmsConfig()));
        doThrow(new PersistenceException("could not update",
                new java.sql.SQLException("Record has changed since last read in table 'sms_config'", "HY000", 1020)))
                .when(smsConfigDao).flush();

        assertThatThrownBy(() -> service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998"))
                .isInstanceOf(SmsConfigConflictException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(auditRecorder, never()).recordSaved(any(), any(), any());
    }

    @Test
    @DisplayName("save does not call a constraint failure of another kind a conflict")
    void shouldNotReportConflict_whenAnotherConstraintFails() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());
        PersistenceException missingValue = new PersistenceException("could not insert",
                new java.sql.SQLException("Column 'updated_at' cannot be null", "23000", 1048));
        doThrow(missingValue).when(smsConfigDao).flush();

        assertThatThrownBy(() -> service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998"))
                .isSameAs(missingValue);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("save passes other database failures through unchanged rather than calling them conflicts")
    void shouldNotReportConflict_forOtherDatabaseFailures() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());
        PersistenceException unavailable = new PersistenceException("sms_config is missing");
        doThrow(unavailable).when(smsConfigDao).flush();

        assertThatThrownBy(() -> service().save(update(SmsProviderType.STUB, true, "", "", false, Map.of()), "999998"))
                .isSameAs(unavailable);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
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
        assertThat(service().credentialFields(SmsProviderType.VOIPMS)).containsExactly("field_one", "field_two");
        assertThat(service().credentialFields(SmsProviderType.STUB)).isEmpty();
        assertThat(service().credentialFields(SmsProviderType.CLOUDLI)).isEmpty();
    }

    private SmsConfigService service() {
        return service(providerClients());
    }

    private SmsConfigService service(SmsProviderClientResolver clients) {
        return new SmsConfigService(smsConfigDao, clients, eventPublisher, auditRecorder);
    }

    private List<String> auditedChanges(SmsConfig saved) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> changed = ArgumentCaptor.forClass(List.class);
        verify(auditRecorder).recordSaved(org.mockito.ArgumentMatchers.same(saved),
                org.mockito.ArgumentMatchers.eq("999998"), changed.capture());
        return changed.getValue();
    }

    static SmsProviderClientResolver providerClients() {
        return new SmsProviderClientResolver(List.of(new StubSmsProviderClient(),
                fakeClient(SmsProviderType.VOIPMS, List.of("field_one", "field_two"))));
    }

    /** {@link #providerClients()} plus a CLOUDLI client with a credential field of its own. */
    private static SmsProviderClientResolver providerClientsWithCloudli() {
        return new SmsProviderClientResolver(List.of(new StubSmsProviderClient(),
                fakeClient(SmsProviderType.VOIPMS, List.of("field_one", "field_two")),
                fakeClient(SmsProviderType.CLOUDLI, List.of("account_id"))));
    }

    private static SmsProviderClient fakeClient(SmsProviderType providerType, List<String> credentialFields) {
        return new StubSmsProviderClient() {
            @Override
            public SmsProviderType providerType() {
                return providerType;
            }

            @Override
            public List<String> credentialFields() {
                return credentialFields;
            }
        };
    }

    @Test
    void shouldIgnoreCredentialChanges_whenDisplayedProviderDoesNotMatch() {
        for (SmsProviderType displayed : new SmsProviderType[] {null, SmsProviderType.CLOUDLI}) {
            SmsConfig stored = new SmsConfig();
            stored.setProviderType(SmsProviderType.VOIPMS);
            stored.setCredential(SmsProviderType.VOIPMS, "field_one", "kept-value");
            stored.setCredential(SmsProviderType.VOIPMS, "legacy_field", "kept-legacy");
            when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));
            String before = stored.storedCredentials();
            service().save(new SmsConfigUpdateDto(SmsProviderType.VOIPMS, true, false, "", "", false,
                    Map.of("field_one", "wrong-value"), true, displayed, java.util.Set.of()), "999998");
            assertThat(stored.storedCredentials()).isEqualTo(before);
            assertThat(stored.isEnabled()).isTrue();
        }
    }

    @Test
    void shouldRemoveUninstalledProviderCredentials_withoutActivatingProvider() {
        SmsConfig stored = new SmsConfig();
        stored.setCredential(SmsProviderType.CLOUDLI, "account_id", "old-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));
        service().save(new SmsConfigUpdateDto(SmsProviderType.STUB, false, false, "", "", false,
                Map.of(), false, SmsProviderType.STUB, java.util.Set.of(SmsProviderType.CLOUDLI)), "999998");
        assertThat(stored.getProviderType()).isEqualTo(SmsProviderType.STUB);
        assertThat(stored.credentialNames(SmsProviderType.CLOUDLI)).isEmpty();
        verify(auditRecorder).recordSaved(org.mockito.ArgumentMatchers.same(stored), org.mockito.ArgumentMatchers.eq("999998"),
                org.mockito.ArgumentMatchers.eq(List.of("credentials:CLOUDLI")));
    }

    private static SmsConfigUpdateDto update(SmsProviderType providerType, boolean enabled, String senderNumber,
                                             String webhookSecret, boolean clearWebhookSecret,
                                             Map<String, String> credentials) {
        return new SmsConfigUpdateDto(providerType, enabled, false, senderNumber, webhookSecret, clearWebhookSecret,
                credentials, false, providerType, java.util.Set.of());
    }

    /** Leaves every other setting as a new row has it, so only the provider and credentials can change. */
    private static SmsConfigUpdateDto credentialsUpdate(SmsProviderType providerType, Map<String, String> credentials,
                                                        boolean clearCredentials) {
        return new SmsConfigUpdateDto(providerType, false, false, "", "", false, credentials, clearCredentials, providerType, java.util.Set.of());
    }
}
