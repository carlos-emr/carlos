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
import io.github.carlos_emr.carlos.sms.model.SmsSecretEncryptionException;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
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

import java.util.Date;
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
        stored.setCredential("field_one", "old-value-one");
        stored.setCredential("field_two", "old-value-two");
        stored.setCredential("legacy_field", "stale-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.VOIPMS, true, "", "", false,
                Map.of("field_one", "new-value-one", "field_two", "", "injected", "x")), "999998");

        assertThat(saved.getCredential("field_one")).isEqualTo("new-value-one");
        assertThat(saved.getCredential("field_two")).isEqualTo("old-value-two");
        assertThat(saved.hasCredential("legacy_field")).isFalse();
        assertThat(saved.hasCredential("injected")).isFalse();
    }

    @Test
    @DisplayName("save announces the scheduler setting so the scheduler can start or stop without a restart")
    void shouldPublishSchedulerSetting_whenSaved() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        service().save(new SmsConfigUpdateDto(SmsProviderType.STUB, true, true, "", "", false, Map.of(), null),
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
    @DisplayName("save replaces stored credentials that cannot be read, instead of failing")
    void shouldReplaceUnreadableCredentials_whenSaving() {
        SmsConfig stored = new SmsConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "credentialsJson", "{not json");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        SmsConfig saved = service().save(update(SmsProviderType.VOIPMS, true, "", "", false,
                Map.of("field_one", "value one")), "999998");

        assertThat(saved.credentialsReadable()).isTrue();
        assertThat(saved.credentialNames()).containsExactly("field_one");
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
    @DisplayName("save refuses settings from a page that showed an older version, changing nothing")
    void shouldRefuseSave_whenPageShowedOlderVersion() {
        SmsConfig stored = storedAt(3, "222222");
        stored.setEnabled(false);
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service().save(
                update(SmsProviderType.STUB, true, "", "", false, Map.of(), 2), "111111"))
                .isInstanceOf(SmsConfigConflictException.class);

        assertThat(stored.isEnabled()).isFalse();
        assertThat(stored.getUpdatedBy()).isEqualTo("222222");
        verify(smsConfigDao, never()).merge(any());
        verify(smsConfigDao, never()).flush();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(auditRecorder, never()).recordSaved(any(), any(), any());
    }

    @Test
    @DisplayName("save refuses settings from a page that showed nothing saved once a row exists")
    void shouldRefuseSave_whenPageShowedNothingButRowExists() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(storedAt(0, "222222")));

        assertThatThrownBy(() -> service().save(
                update(SmsProviderType.STUB, true, "", "", false, Map.of(), null), "111111"))
                .isInstanceOf(SmsConfigConflictException.class);
        verify(smsConfigDao, never()).persist(any());
        verify(smsConfigDao, never()).merge(any());
    }

    @Test
    @DisplayName("save refuses a page version when nothing is stored, rather than creating the row")
    void shouldRefuseSave_whenPageShowedVersionButNothingIsStored() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().save(
                update(SmsProviderType.STUB, true, "", "", false, Map.of(), 0), "111111"))
                .isInstanceOf(SmsConfigConflictException.class);
        verify(smsConfigDao, never()).persist(any());
    }

    @Test
    @DisplayName("save stores nothing, audits nothing and announces nothing when a secret cannot be encrypted")
    void shouldStoreNothing_whenSecretCannotBeEncrypted() {
        when(smsConfigDao.findCurrent()).thenReturn(Optional.empty());
        EncryptionKeyTestSupport.restoreKey(null);

        assertThatThrownBy(() -> service().save(
                update(SmsProviderType.STUB, true, "", "webhook-value", false, Map.of()), "111111"))
                .isInstanceOf(SmsSecretEncryptionException.class);
        verify(smsConfigDao, never()).persist(any());
        verify(smsConfigDao, never()).merge(any());
        verify(smsConfigDao, never()).flush();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(auditRecorder, never()).recordSaved(any(), any(), any());
    }

    @Test
    @DisplayName("save goes ahead when the page showed the stored version")
    void shouldSave_whenPageShowedStoredVersion() {
        SmsConfig stored = storedAt(3, "222222");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        service().save(update(SmsProviderType.STUB, false, "", "", false, Map.of(), 3), "111111");

        verify(smsConfigDao).merge(stored);
        assertThat(stored.getUpdatedBy()).isEqualTo("111111");
        assertThat(stored.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("a double-click is recognized only when the same admin's save already stored the same settings")
    void shouldRecognizeAlreadySaved_onlyForSameAdminAndSameSettings() {
        SmsConfig stored = storedAt(1, "111111");
        stored.setEnabled(true);
        stored.setSenderNumber("+14165551212");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThat(service().alreadySaved(update(SmsProviderType.STUB, true, "416-555-1212", "", false, Map.of(), 0),
                "111111")).isTrue();
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, true, "416-555-1212", "", false, Map.of(), 0),
                "222222")).as("another administrator's save").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "416-555-1212", "", false, Map.of(), 0),
                "111111")).as("a different switch").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, true, "", "", false, Map.of(), 0),
                "111111")).as("a different sender number").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, true, "416-555-1212", "", false, Map.of(), 0),
                "111111")).as("a different provider").isFalse();
    }

    @Test
    @DisplayName("a double-click is recognized only shortly after the admin's own save")
    void shouldNotRecognizeAlreadySaved_whenOwnSaveIsNotRecent() {
        SmsConfig stored = storedAt(1, "111111");
        ReflectionTestUtils.setField(stored, "updatedAt", new Date(System.currentTimeMillis() - 120_000L));
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "", "", false, Map.of(), 0),
                "111111")).as("two minutes ago").isFalse();

        ReflectionTestUtils.setField(stored, "updatedAt", new Date(System.currentTimeMillis() + 3_600_000L));
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "", "", false, Map.of(), 0),
                "111111")).as("an hour in the future").isFalse();
    }

    @Test
    @DisplayName("a double-click compares typed secrets with the stored ones without needing them retyped")
    void shouldCompareSecrets_whenCheckingAlreadySaved() {
        SmsConfig stored = storedAt(1, "111111");
        stored.setProviderType(SmsProviderType.VOIPMS);
        stored.setWebhookSecret("webhook-value");
        stored.setCredential("field_one", "value-one");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored));

        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, false, "", "webhook-value", false,
                Map.of("field_one", "value-one", "field_two", ""), 0), "111111")).isTrue();
        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, false, "", "", false, Map.of(), 0),
                "111111")).as("blank fields keep what is stored").isTrue();
        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, false, "", "other-webhook-value", false,
                Map.of(), 0), "111111")).as("a different webhook secret").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, false, "", "", true, Map.of(), 0),
                "111111")).as("asking to remove a stored secret").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.VOIPMS, false, "", "", false,
                Map.of("field_one", "other-value"), 0), "111111")).as("a different credential").isFalse();
    }

    @Test
    @DisplayName("a double-click is not recognized when a stored secret cannot be read or a stale credential remains")
    void shouldNotRecognizeAlreadySaved_whenStoredSecretsDiffer() throws Exception {
        SmsConfig stored = storedAt(1, "111111");
        stored.setCredential("legacy_field", "stale-value");
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(stored), Optional.empty());

        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "", "", false, Map.of(), 0),
                "111111")).as("a credential the save would remove").isFalse();
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "", "", false, Map.of(), 0),
                "111111")).as("nothing stored").isFalse();

        SmsConfig unreadable = storedAt(1, "111111");
        unreadable.setWebhookSecret("webhook-value");
        EncryptionKeyTestSupport.seedFreshKey();
        when(smsConfigDao.findCurrent()).thenReturn(Optional.of(unreadable));
        assertThat(service().alreadySaved(update(SmsProviderType.STUB, false, "", "webhook-value", false, Map.of(), 0),
                "111111")).as("a secret encrypted under another key").isFalse();
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
        return new SmsConfigService(smsConfigDao, providerClients(), eventPublisher, auditRecorder);
    }

    static SmsProviderClientResolver providerClients() {
        StubSmsProviderClient voipMs = new StubSmsProviderClient() {
            @Override
            public SmsProviderType providerType() {
                return SmsProviderType.VOIPMS;
            }

            @Override
            public List<String> credentialFields() {
                return List.of("field_one", "field_two");
            }
        };
        return new SmsProviderClientResolver(List.of(new StubSmsProviderClient(), voipMs));
    }

    private static SmsConfigUpdateDto update(SmsProviderType providerType, boolean enabled, String senderNumber,
                                             String webhookSecret, boolean clearWebhookSecret,
                                             Map<String, String> credentials) {
        return update(providerType, enabled, senderNumber, webhookSecret, clearWebhookSecret, credentials, null);
    }

    /** @param expectedVersion the version the page showed; null when it showed nothing saved */
    private static SmsConfigUpdateDto update(SmsProviderType providerType, boolean enabled, String senderNumber,
                                             String webhookSecret, boolean clearWebhookSecret,
                                             Map<String, String> credentials, Integer expectedVersion) {
        return new SmsConfigUpdateDto(providerType, enabled, false, senderNumber, webhookSecret, clearWebhookSecret,
                credentials, expectedVersion);
    }

    /** A row as loaded from the database: Hibernate sets the version, which has no setter. */
    private static SmsConfig storedAt(int version, String updatedBy) {
        SmsConfig stored = new SmsConfig();
        ReflectionTestUtils.setField(stored, "version", version);
        stored.markUpdated(updatedBy);
        return stored;
    }
}
