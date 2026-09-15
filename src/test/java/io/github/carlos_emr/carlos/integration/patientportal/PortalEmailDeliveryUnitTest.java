/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.PortalDeliveryState;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class PortalEmailDeliveryUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final EmailLogDaoImpl logs = mock(EmailLogDaoImpl.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PatientPortalSettings settings = mock(PatientPortalSettings.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private final EmailLog log = new EmailLog();
    private final EmailData data = new EmailData();
    private final List<String> operations = new ArrayList<>();
    private PortalEmailDelivery delivery;
    private io.github.carlos_emr.carlos.email.core.EmailSendResult outcome;

    @BeforeEach
    void setUp() {
        var provider = new Provider(); provider.setProviderNo("999998"); user.setLoggedInProvider(provider);
        var demographic = new Demographic(); demographic.setDemographicNo(123); demographic.setEmail("patient@example.org");
        org.springframework.test.util.ReflectionTestUtils.setField(log, "id", 45); log.setDemographic(demographic); log.setPassword(""); log.setPasswordClue(""); log.setStatus(EmailStatus.PENDING);
        data.setDemographicNo(123); data.setRecipients(new String[]{"patient@example.org"}); data.setIsEncrypted(true);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 123)).thenReturn(true);
        when(settings.clinicId()).thenReturn("clinic"); when(settings.baseUrl()).thenReturn("https://portal.example.org");
        when(logs.initializePortalDelivery(log)).thenReturn(true);
        when(logs.transitionPortalDelivery(eq(log), any(), any(), nullable(Long.class))).thenReturn(true);
        when(logs.find(45)).thenReturn(log);
        when(logs.transitionEmailStatus(eq(45), any(), any(), anyString(), any())).thenReturn(1);
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", false, false));
        when(portal.createUnlockSecret(eq(123), anyString(), eq("Email 45"), any())).thenAnswer(call -> {
            operations.add("create");
            return new PatientPortalUnlockSecretDto(77, true, PortalSecret.of("synthetic-password-29"), call.getArgument(1), "pending");
        });
        when(portal.publishUnlockSecret(eq(77L), any())).thenAnswer(call -> { operations.add("publish"); return null; });
        when(portal.revokeUnlockSecret(eq(77L), eq("email_not_sent"), any())).thenAnswer(call -> { operations.add("revoke"); return null; });
        delivery = new PortalEmailDelivery(security, logs, () -> portal, () -> settings);
    }

    private PatientPortalAccountDto account(String status, boolean locked, boolean reset) {
        return new PatientPortalAccountDto(1, "clinic", 123, status, locked, reset, null, null);
    }
    private void encrypt() {
        assertThat(data.getPassword()).isEqualTo("synthetic-password-29");
        assertThat(log.getPassword()).isEmpty();
        operations.add("encrypt");
    }
    private void send() {
        assertThat(data.getPassword()).isEmpty();
        assertThat(data.getPasswordClue()).isEmpty();
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENDING);
        assertThat(data.getBody()).contains("Email 45").doesNotContain("synthetic-password-29");
        operations.add("send");
    }
    private void stored(PortalDeliveryState state) {
        log.setPortalDeliveryState(state); log.setTimestamp(new java.util.Date(System.currentTimeMillis()-16L*60L*1000L)); log.setPortalSecretId(77L); log.setPortalSourceReference("email-fixed-reference");
        log.setPortalOrigin(settings.baseUrl()); log.setPortalClinicId(settings.clinicId());
    }

    @Test void shouldPublishOnlyAfterEncryptionAndTransportAcceptance() {
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).containsExactly("create", "encrypt", "send", "publish");
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
        assertThat(outcome.isTransportAccepted()).isTrue();
        assertThat(data.getPassword()).isEmpty();
        var order = inOrder(logs, portal);
        order.verify(logs).initializePortalDelivery(log);
        order.verify(portal).createUnlockSecret(eq(123), anyString(), eq("Email 45"), any());
        order.verify(logs).transitionPortalDelivery(log, PortalDeliveryState.PREPARING, PortalDeliveryState.READY, 77L);
        order.verify(logs).transitionPortalDelivery(log, PortalDeliveryState.READY, PortalDeliveryState.SENDING, 77L);
        order.verify(logs).transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L);
        order.verify(portal).publishUnlockSecret(eq(77L), any());
    }

    @Test void shouldRevokeBeforeSendFailureWithoutPublishingOrSending() {
        outcome = delivery.send(user, log, data, () -> { throw new EmailSendingException("sensitive failure"); }, this::send);
        assertThat(operations).containsExactly("create", "revoke");
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(outcome.getTransportOutcome()).isEqualTo(io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
        assertThat(log.getErrorMessage()).doesNotContain("sensitive");
        assertThat(data.getPassword()).isEmpty();
    }

    @Test void shouldKeepPendingPasswordWhenTransportAcceptanceIsUnknown() {
        outcome = delivery.send(user, log, data, this::encrypt, () -> { throw new EmailSendingException("connection lost", null, true); });
        assertThat(operations).containsExactly("create", "encrypt");
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENDING);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDelivery.UNCERTAIN);
        assertThat(data.getPassword()).isEmpty();
    }

    @Test void shouldRetryOnlyPublicationAfterSentEmail() {
        when(portal.publishUnlockSecret(eq(77L), any())).thenThrow(new IllegalStateException("portal outage"));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(outcome.isTransportAccepted()).isTrue();
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENT);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDelivery.PUBLISH_PENDING);
        when(portal.publishUnlockSecret(eq(77L), any())).thenReturn(null);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
        assertThat(operations).containsExactly("create", "encrypt", "send");
        verify(portal, times(1)).createUnlockSecret(eq(123), anyString(), anyString(), any());
    }

    @Test void shouldRetainRecoverableRevokeIntentIfPortalIsDown() {
        when(portal.revokeUnlockSecret(anyLong(), anyString(), any())).thenThrow(new IllegalStateException("outage"));
        outcome = delivery.send(user, log, data, () -> { throw new EmailSendingException("render failed"); }, this::send);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKE_PENDING);
        doReturn(null).when(portal).revokeUnlockSecret(anyLong(), anyString(), any());
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).doesNotContain("send", "publish");
    }

    @Test void shouldRecoverLostCreateResponseUsingSameStoredReference() {
        when(portal.createUnlockSecret(eq(123), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("timeout"));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        String reference = log.getPortalSourceReference();
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKE_PENDING);
        assertThat(log.getPortalSecretId()).isNull();
        when(portal.createUnlockSecret(eq(123), eq(reference), eq("Email 45"), any()))
                .thenReturn(new PatientPortalUnlockSecretDto(77, false, PortalSecret.of("existing-password"), reference, "pending"));
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        verify(portal).revokeUnlockSecret(eq(77L), eq("email_not_sent"), any());
    }

    @ParameterizedTest @ValueSource(strings={"wrong@example.org", "patient@example.org,other@example.org", "Patient <patient@example.org>"})
    void shouldRejectRecipientOutsideRecordedPatientAddress(String recipient) {
        data.setRecipients(new String[]{recipient});
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
        assertThat(operations).isEmpty();
        assertThat(outcome.getTransportOutcome()).isEqualTo(io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
    }

    @Test void shouldRejectMultipleRecipients() {
        data.setRecipients(new String[]{"patient@example.org", "patient@example.org"});
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
    }

    @Test void shouldRejectChangedPatientNumber() {
        data.setDemographicNo(456);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
    }

    @Test void shouldRequirePatientScopedSecretWritePermission() {
        when(security.hasPrivilege(user, "_portal.secret", SecurityInfoManager.WRITE, "123")).thenReturn(false);
        assertThatThrownBy(() -> outcome = delivery.send(user, log, data, this::encrypt, this::send)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
    }

    @ParameterizedTest @ValueSource(strings={"disabled", "pending"})
    void shouldBlockUnavailableAccount(String status) {
        when(portal.findAccount(eq(123), any())).thenReturn(account(status, false, false));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verify(portal, never()).createUnlockSecret(anyInt(), anyString(), anyString(), any());
        assertThat(operations).isEmpty();
    }

    @Test void shouldBlockLockedAccount() {
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", true, false));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).isEmpty();
    }

    @Test void shouldBlockAccountRequiringPasswordReset() {
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", false, true));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).isEmpty();
    }

    @Test void shouldNotPublishOrRevokeUnconfirmedTransportOutcome() {
        stored(PortalDeliveryState.SENDING);
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", false)).isInstanceOf(IllegalArgumentException.class);
        verify(portal, never()).publishUnlockSecret(anyLong(), any());
        verify(portal, never()).revokeUnlockSecret(anyLong(), anyString(), any());
    }

    @Test void shouldPublishExplicitlyConfirmedAcceptanceWithoutResending() {
        stored(PortalDeliveryState.SENDING);
        delivery.recover(user, 45, "confirmSent", true);
        assertThat(operations).containsExactly("publish");
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
    }

    @Test void shouldRevokeExplicitlyConfirmedNonAcceptance() {
        stored(PortalDeliveryState.SENDING);
        delivery.recover(user, 45, "confirmNotSent", true);
        assertThat(operations).containsExactly("revoke");
    }

    @Test void shouldNotPublishIfConcurrentRecoveryChangedTheDecision() {
        stored(PortalDeliveryState.SENDING);
        when(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L)).thenReturn(false);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", true)).isInstanceOf(IllegalStateException.class);
        assertThat(operations).isEmpty();
    }

    @Test void shouldNotSendIfRecoveryCancelledPreparedEmail() {
        when(logs.transitionPortalDelivery(log, PortalDeliveryState.READY, PortalDeliveryState.SENDING, 77L)).thenReturn(false);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).doesNotContain("send", "publish");
    }

    @Test void shouldRejectRestrictedPatientRecordBeforeCallingPortal() {
        when(security.isAllowedAccessToPatientRecord(user, 123)).thenReturn(false);
        assertThatThrownBy(() -> outcome = delivery.send(user, log, data, this::encrypt, this::send))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
        stored(PortalDeliveryState.SENT);
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
    }

    @Test void shouldRevokeWhenTransportDefinitelyDidNotAcceptEmail() {
        outcome = delivery.send(user, log, data, this::encrypt,
                () -> { throw new EmailSendingException("connection refused"); });
        assertThat(outcome.getTransportOutcome()).isEqualTo(
                io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).containsExactly("create", "encrypt", "revoke");
    }

    @Test void shouldKeepRecoveryAwayFromRecentActiveSends() {
        stored(PortalDeliveryState.SENDING); log.setTimestamp(new java.util.Date());
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(portal);
    }

    @Test void shouldRepairPublishedEmailTrackingWithoutSendingOrRepublishing() {
        stored(PortalDeliveryState.PUBLISHED);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        verifyNoInteractions(portal);
    }

    @Test void shouldRejectRecoveryAgainstDifferentPortal() {
        stored(PortalDeliveryState.SENT); log.setPortalOrigin("https://old.example.org");
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(portal);
    }
}
