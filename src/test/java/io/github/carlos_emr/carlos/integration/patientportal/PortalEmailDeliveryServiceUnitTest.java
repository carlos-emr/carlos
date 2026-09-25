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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class PortalEmailDeliveryServiceUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final EmailLogDaoImpl logs = mock(EmailLogDaoImpl.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PatientPortalSettings settings = mock(PatientPortalSettings.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private final EmailLog log = new EmailLog();
    private final EmailData data = new EmailData();
    private final List<String> operations = new ArrayList<>();
    private PortalEmailDeliveryService delivery;
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
        delivery = new PortalEmailDeliveryService(security, logs, () -> portal, () -> settings);
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

    @Test void shouldPublishPassword_onlyAfterEncryptionAndTransportAcceptance() {
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

    @Test void shouldRevokeWithoutPublishingOrSending_whenEncryptionFails() {
        outcome = delivery.send(user, log, data, () -> { throw new EmailSendingException("sensitive failure"); }, this::send);
        assertThat(operations).containsExactly("create", "revoke");
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(outcome.getTransportOutcome()).isEqualTo(io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
        assertThat(log.getErrorMessage()).doesNotContain("sensitive");
        assertThat(data.getPassword()).isEmpty();
    }

    @Test void shouldKeepPasswordPending_whenTransportAcceptanceIsUnknown() {
        outcome = delivery.send(user, log, data, this::encrypt, () -> { throw new EmailSendingException("connection lost", null, true); });
        assertThat(operations).containsExactly("create", "encrypt");
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENDING);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDeliveryService.UNCERTAIN);
        assertThat(data.getPassword()).isEmpty();
    }

    @Test void shouldRetryOnlyPublication_afterSentEmail() {
        when(portal.publishUnlockSecret(eq(77L), any())).thenThrow(new IllegalStateException("portal outage"));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(outcome.isTransportAccepted()).isTrue();
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENT);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDeliveryService.PUBLISH_PENDING);
        log.setStatus(EmailStatus.SUCCESS); // EmailManager records acceptance once send returns
        when(portal.publishUnlockSecret(eq(77L), any())).thenReturn(null);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
        assertThat(operations).containsExactly("create", "encrypt", "send");
        verify(portal, times(1)).createUnlockSecret(eq(123), anyString(), anyString(), any());
        verify(logs).transitionEmailStatus(eq(45), eq(EmailStatus.PENDING), eq(EmailStatus.SUCCESS), eq(""), any());
    }

    @Test void shouldRetainRecoverableRevokeIntent_whenPortalIsDown() {
        when(portal.revokeUnlockSecret(anyLong(), anyString(), any())).thenThrow(new IllegalStateException("outage"));
        outcome = delivery.send(user, log, data, () -> { throw new EmailSendingException("render failed"); }, this::send);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKE_PENDING);
        log.setStatus(EmailStatus.FAILED); // EmailManager records the failure once send returns
        doReturn(null).when(portal).revokeUnlockSecret(anyLong(), anyString(), any());
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).doesNotContain("send", "publish");
        verify(logs).transitionEmailStatus(eq(45), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                eq("Email was not sent. Its portal password has been revoked."), any());
    }

    @Test void shouldRecoverLostCreateResponse_usingSameStoredReference() {
        when(portal.createUnlockSecret(eq(123), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("timeout"));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        String reference = log.getPortalSourceReference();
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKE_PENDING);
        assertThat(log.getPortalSecretId()).isNull();
        log.setStatus(EmailStatus.FAILED); // EmailManager records the failure once send returns
        when(portal.createUnlockSecret(eq(123), eq(reference), eq("Email 45"), any()))
                .thenReturn(new PatientPortalUnlockSecretDto(77, false, PortalSecret.of("existing-password"), reference, "pending"));
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        verify(portal).revokeUnlockSecret(eq(77L), eq("email_not_sent"), any());
    }

    @ParameterizedTest @ValueSource(strings={"wrong@example.org", "patient@example.org,other@example.org", "Patient <patient@example.org>"})
    void shouldRejectRecipient_outsideRecordedPatientAddress(String recipient) {
        data.setRecipients(new String[]{recipient});
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
        assertThat(operations).isEmpty();
        assertThat(outcome.getTransportOutcome()).isEqualTo(io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
    }

    @Test void shouldRejectSend_withMultipleRecipients() {
        data.setRecipients(new String[]{"patient@example.org", "patient@example.org"});
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
    }

    @Test void shouldRejectSend_whenPatientNumberChanged() {
        data.setDemographicNo(456);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verifyNoInteractions(portal);
    }

    @Test void shouldRequireSecretWritePermission_forThePatient() {
        when(security.hasPrivilege(user, "_portal.secret", SecurityInfoManager.WRITE, "123")).thenReturn(false);
        assertThatThrownBy(() -> outcome = delivery.send(user, log, data, this::encrypt, this::send)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
    }

    @ParameterizedTest @ValueSource(strings={"disabled", "pending"})
    void shouldBlockSend_forUnavailableAccount(String status) {
        when(portal.findAccount(eq(123), any())).thenReturn(account(status, false, false));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        verify(portal, never()).createUnlockSecret(anyInt(), anyString(), anyString(), any());
        assertThat(operations).isEmpty();
    }

    @Test void shouldBlockSend_forLockedAccount() {
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", true, false));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).isEmpty();
    }

    @Test void shouldBlockSend_forAccountRequiringPasswordReset() {
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", false, true));
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).isEmpty();
    }

    @Test void shouldNotPublishOrRevoke_whenTransportOutcomeIsUnconfirmed() {
        stored(PortalDeliveryState.SENDING);
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false)).isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", false)).isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        verify(portal, never()).publishUnlockSecret(anyLong(), any());
        verify(portal, never()).revokeUnlockSecret(anyLong(), anyString(), any());
    }

    @Test void shouldPublishWithoutResending_whenAcceptanceIsConfirmed() {
        stored(PortalDeliveryState.SENDING);
        delivery.recover(user, 45, "confirmSent", true);
        assertThat(operations).containsExactly("publish");
        // Staff asserted the outcome, so it is recorded as a manual resolution, not an observed success.
        assertThat(log.getStatus()).isEqualTo(EmailStatus.RESOLVED);
    }

    @Test void shouldRevoke_whenNonAcceptanceIsConfirmed() {
        stored(PortalDeliveryState.SENDING);
        delivery.recover(user, 45, "confirmNotSent", true);
        assertThat(operations).containsExactly("revoke");
    }

    @Test void shouldNotPublish_whenConcurrentRecoveryChangedTheDecision() {
        stored(PortalDeliveryState.SENDING);
        when(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L)).thenReturn(false);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", true)).isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        assertThat(operations).isEmpty();
    }

    @Test void shouldNotSend_whenRecoveryCancelledPreparedEmail() {
        when(logs.transitionPortalDelivery(log, PortalDeliveryState.READY, PortalDeliveryState.SENDING, 77L)).thenReturn(false);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(operations).doesNotContain("send", "publish");
    }

    @Test void shouldRejectRestrictedPatientRecord_beforeCallingPortal() {
        when(security.isAllowedAccessToPatientRecord(user, 123)).thenReturn(false);
        assertThatThrownBy(() -> outcome = delivery.send(user, log, data, this::encrypt, this::send))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
        stored(PortalDeliveryState.SENT);
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(portal);
    }

    @Test void shouldRevoke_whenTransportDefinitelyDidNotAcceptEmail() {
        outcome = delivery.send(user, log, data, this::encrypt,
                () -> { throw new EmailSendingException("connection refused"); });
        assertThat(outcome.getTransportOutcome()).isEqualTo(
                io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).containsExactly("create", "encrypt", "revoke");
    }

    @Test void shouldKeepRecoveryAway_fromRecentActiveSends() {
        stored(PortalDeliveryState.SENDING); log.setTimestamp(new java.util.Date());
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", true))
                .isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        verifyNoInteractions(portal);
    }

    @Test void shouldRepairTrackingWithoutSendingOrRepublishing_forPublishedEmail() {
        stored(PortalDeliveryState.PUBLISHED);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SUCCESS);
        verifyNoInteractions(portal);
    }

    @Test void shouldRejectRecovery_againstDifferentPortal() {
        stored(PortalDeliveryState.SENT); log.setPortalOrigin("https://old.example.org");
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false)).isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        verifyNoInteractions(portal);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PortalDeliveryState.class, names = {"PREPARING", "READY", "SENT"})
    void shouldRefuseRecovery_whenPendingSendMayStillBeRunning(PortalDeliveryState state) {
        stored(state); log.setTimestamp(new java.util.Date());
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false))
                .isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        verifyNoInteractions(portal);
        verify(logs, never()).transitionPortalDelivery(any(), any(), any(), nullable(Long.class));
    }

    @Test void shouldAllowRecovery_whenRecentSendAlreadyHasAnOutcome() {
        stored(PortalDeliveryState.SENT); log.setTimestamp(new java.util.Date()); log.setStatus(EmailStatus.SUCCESS);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
    }

    @Test void shouldRevokeAndRethrow_whenTransportStepIsRefused() {
        var refused = new SecurityException("missing required sec object (_email)");
        assertThatThrownBy(() -> delivery.send(user, log, data, this::encrypt, () -> { throw refused; }))
                .isSameAs(refused);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).containsExactly("create", "encrypt", "revoke");
    }

    @Test void shouldReportFailedSendWithoutCallingPortal_whenPatientNumberIsMissing() {
        log.getDemographic().setDemographicNo(null);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(outcome.getTransportOutcome()).isEqualTo(
                io.github.carlos_emr.carlos.email.core.EmailSendResult.TransportOutcome.FAILED);
        verifyNoInteractions(portal);
    }

    @Test void shouldPublishNotRevoke_whenAcceptedSendLostItsSentTransition() {
        stored(PortalDeliveryState.SENDING); log.setStatus(EmailStatus.SUCCESS);
        assertThat(PortalEmailDeliveryService.classify(log)).isEqualTo(PortalEmailDeliveryService.RecoveryView.RETRY_PUBLISH);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmNotSent", true))
                .isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
        assertThat(operations).containsExactly("publish");
    }

    @Test void shouldRevokeNotPublish_whenFailedSendLostItsRevokeTransition() {
        stored(PortalDeliveryState.SENDING); log.setStatus(EmailStatus.FAILED);
        assertThat(PortalEmailDeliveryService.classify(log)).isEqualTo(PortalEmailDeliveryService.RecoveryView.RETRY_REVOKE);
        assertThatThrownBy(() -> delivery.recover(user, 45, "confirmSent", true))
                .isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        delivery.recover(user, 45, "retry", false);
        assertThat(log.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
        assertThat(operations).containsExactly("revoke");
    }

    @Test void shouldRefuseAllRecovery_whenStateContradictsStatus() {
        stored(PortalDeliveryState.READY); log.setStatus(EmailStatus.SUCCESS);
        assertThat(PortalEmailDeliveryService.classify(log)).isEqualTo(PortalEmailDeliveryService.RecoveryView.INCONSISTENT);
        assertThatThrownBy(() -> delivery.recover(user, 45, "retry", false))
                .isInstanceOf(PortalEmailDeliveryService.RecoveryRefusedException.class);
        verifyNoInteractions(portal);
    }

    @Test void shouldRecordReasonsStaffCanActOn_forDefiniteFailures() {
        data.setRecipients(new String[]{"wrong@example.org"});
        delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDeliveryService.RECIPIENT_NOT_RECORDED);

        data.setRecipients(new String[]{"patient@example.org"});
        when(portal.findAccount(eq(123), any())).thenReturn(account("active", true, false));
        delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(log.getErrorMessage()).isEqualTo(PortalEmailDeliveryService.ACCOUNT_NOT_READY);
    }

    @Test void shouldStoreTheBodyActuallySent_withThePortalReference() {
        data.setBody("Localized notice.");
        delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(log.getBody()).isEqualTo("Localized notice.\n\nEmail 45");
        assertThat(data.getBody()).isEqualTo(log.getBody());
    }

    @Test void shouldReportContradictions_asInconsistentAndUnresolved() {
        stored(PortalDeliveryState.PUBLISHED); log.setStatus(EmailStatus.FAILED);
        assertThat(PortalEmailDeliveryService.classify(log)).isEqualTo(PortalEmailDeliveryService.RecoveryView.INCONSISTENT);
        assertThat(log.isPortalDeliveryUnresolved()).isTrue();
        stored(PortalDeliveryState.REVOKED); log.setStatus(EmailStatus.SUCCESS);
        assertThat(PortalEmailDeliveryService.classify(log)).isEqualTo(PortalEmailDeliveryService.RecoveryView.INCONSISTENT);
        assertThat(log.isPortalDeliveryUnresolved()).isTrue();
        stored(PortalDeliveryState.PUBLISHED); log.setStatus(EmailStatus.SUCCESS);
        assertThat(log.isPortalDeliveryUnresolved()).isFalse();
        stored(PortalDeliveryState.REVOKED); log.setStatus(EmailStatus.FAILED);
        assertThat(log.isPortalDeliveryUnresolved()).isFalse();
    }

    @Test void shouldReportAcceptedWithPublicationPending_whenRecoveryChangedStateDuringTheSend() {
        when(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L)).thenReturn(false);
        outcome = delivery.send(user, log, data, this::encrypt, this::send);
        assertThat(outcome.isTransportAccepted()).isTrue();
        assertThat(outcome.isFollowUpRequired()).isTrue();
        assertThat(operations).containsExactly("create", "encrypt", "send");
    }

    @Test void shouldClearThePendingNote_afterPublishingOnRetry() {
        stored(PortalDeliveryState.SENT); log.setStatus(EmailStatus.SUCCESS);
        log.setErrorMessage(PortalEmailDeliveryService.PUBLISH_PENDING);
        // The row is already SUCCESS, so the PENDING compare-and-set cannot match.
        when(logs.transitionEmailStatus(eq(45), eq(EmailStatus.PENDING), any(), anyString(), any())).thenReturn(0);
        delivery.recover(user, 45, "retry", false);
        verify(logs).transitionEmailStatus(eq(45), eq(EmailStatus.SUCCESS), eq(EmailStatus.SUCCESS), eq(""), eq(log.getTimestamp()));
        assertThat(log.getErrorMessage()).isEmpty();
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        @Test
        @DisplayName("should enable portal delivery when the setting is true and the Portal is configured")
        void shouldEnable_whenTrueAndPortalConfigured() {
            assertThat(PortalEmailDeliveryService.isEnabled(" true ", () -> true)).isTrue();
        }

        @Test
        @DisplayName("should stay off, without consulting the Portal, when the setting is false or absent")
        void shouldStayOff_whenFalseOrAbsent() {
            java.util.function.BooleanSupplier unused = () -> {
                throw new AssertionError("the Portal must not be consulted when email delivery is off");
            };
            assertThat(PortalEmailDeliveryService.isEnabled("false", unused)).isFalse();
            assertThat(PortalEmailDeliveryService.isEnabled(null, unused)).isFalse();
        }

        @Test
        @DisplayName("should report a misconfiguration when the setting is true but the Portal is not configured")
        void shouldReportMisconfiguration_whenTrueButPortalNotConfigured() {
            assertThatThrownBy(() -> PortalEmailDeliveryService.isEnabled("true", () -> false))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining("not enabled and configured");
        }

        @Test
        @DisplayName("should report a misconfiguration for a value that is neither true nor false")
        void shouldReportMisconfiguration_whenValueMalformed() {
            assertThatThrownBy(() -> PortalEmailDeliveryService.isEnabled("yes", () -> true))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageContaining("must be true or false");
        }
    }
}
