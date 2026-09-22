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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientPortalInviteDeliveryDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.Decision;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.InviteRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

@DisplayName("PortalInviteDeliveryService")
class PortalInviteDeliveryServiceUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 1234;
    private static final long INVITE = 41L;
    private static final int EMAIL_LOG = 77;
    private static final String CODE = "invite-code-Xy7";
    private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");
    private static final Instant PATIENT_EXPIRY = Instant.parse("2026-09-29T15:00:00Z");

    private PatientPortalService portal;
    private EmailManager emailManager;
    private PatientPortalInviteDeliveryDao deliveries;
    private EmailLogDao emailLogs;
    private LoggedInInfo user;
    private PatientPortalStaffContext staff;
    private PortalInviteDeliveryService service;
    private final Map<Long, PatientPortalInviteDelivery> rows = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final List<String> events = new ArrayList<>();
    private Instant clock = NOW;
    /** What the scripted transport does once the gate has passed. */
    private EmailSendResult.TransportOutcome transport = EmailSendResult.TransportOutcome.ACCEPTED;
    private String bodyAtSend;

    @BeforeEach
    void setUp() throws Exception {
        portal = mock(PatientPortalService.class);
        emailManager = mock(EmailManager.class);
        deliveries = mock(PatientPortalInviteDeliveryDao.class);
        emailLogs = mock(EmailLogDao.class);
        EmailConfigDao emailConfigs = mock(EmailConfigDao.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        staff = new PatientPortalStaffContext("999998", "Dr Example",
                Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));

        EmailConfig sender = new EmailConfig(EmailConfig.EmailType.SMTP, EmailConfig.EmailProvider.LOCAL,
                "clinic@example.invalid");
        injectDependency(sender, "id", 5);
        when(emailConfigs.findActiveEmailConfig("clinic@example.invalid")).thenReturn(sender);

        backDeliveriesInMemory();
        scriptPortal();
        scriptEmailManager();

        service = new PortalInviteDeliveryService(portal, portalSettings("https://portal-api.clinic.example"),
                new PortalInviteSettings("https://portal.clinic.example", "clinic@example.invalid"),
                emailManager, deliveries, emailConfigs, emailLogs, clockAtNow());
    }

    @Nested
    @DisplayName("first invitation")
    class FirstInvitation {

        @Test
        @DisplayName("should prepare, store the email, commit, then send, in that order")
        void shouldDeliver_inContractOrder() throws Exception {
            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(events).containsExactly("prepare", "stored", "commit", "send");
            assertThat(row.getState()).isEqualTo(State.SENT);
            assertThat(row.getPortalInviteId()).isEqualTo(INVITE);
            assertThat(row.getEmailLogId()).isEqualTo(EMAIL_LOG);
            assertThat(row.getExpiresAt()).isEqualTo(Date.from(PATIENT_EXPIRY));
            assertThat(row.getChannel()).isEqualTo(Channel.EMAIL);
            assertThat(row.getPortalOrigin()).isEqualTo("https://portal-api.clinic.example");

            ArgumentCaptor<String> operation = ArgumentCaptor.forClass(String.class);
            verify(portal).prepareInvite(eq(PATIENT), eq("patient@example.com"), eq(LocalDate.of(1980, 5, 20)),
                    eq("1234567890"), operation.capture(), eq(staff));
            assertThat(operation.getValue()).startsWith("inv-").isEqualTo(row.getDeliveryOperationId());
            verify(portal).commitInviteDelivery(INVITE, operation.getValue(), "emaillog:77", staff);
        }

        @Test
        @DisplayName("should put the activation page and code in the email body, and never in a URL")
        void shouldCarryTheCode_asTextBesideAPlainLink() {
            service.invite(user, patient(), staff, emailRequest());

            assertThat(bodyAtSend)
                    .contains("https://portal.clinic.example/auth/activate\n")
                    .contains("invitation code: " + CODE)
                    .doesNotContain("activate?")
                    .doesNotContain("/auth/activate/" + CODE);
        }

        @Test
        @DisplayName("should mark the email as a portal invitation")
        void shouldMarkTheEmail_asAPortalInvite() {
            service.invite(user, patient(), staff, emailRequest());

            ArgumentCaptor<EmailData> email = ArgumentCaptor.forClass(EmailData.class);
            verify(emailManager).sendEmailWithResult(eq(user), email.capture(), any(EmailManager.DispatchGate.class));
            assertThat(email.getValue().getTransactionType()).isEqualTo(TransactionType.PORTAL_INVITE);
            assertThat(email.getValue().getRecipients()).containsExactly("patient@example.com");
            assertThat(email.getValue().getSenderConfigId()).isEqualTo(5);
        }

        @Test
        @DisplayName("should drop the code from the stored email once the send has resolved")
        void shouldForgetTheCode_afterTheSendResolves() {
            service.invite(user, patient(), staff, emailRequest());

            verify(emailLogs).replaceBody(EMAIL_LOG, PortalInviteDeliveryService.CODE_FORGOTTEN);
        }

        @Test
        @DisplayName("should recover a lost prepare response by retrying with the same operation id")
        void shouldRetryPrepare_withTheSameOperationId() {
            when(portal.prepareInvite(anyInt(), anyString(), any(), anyString(), anyString(), any()))
                    .thenThrow(PatientPortalException.ofTransportFailure("/prepare", null))
                    .thenAnswer(invocation -> prepared(invocation.getArgument(4), null));

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            ArgumentCaptor<String> operation = ArgumentCaptor.forClass(String.class);
            verify(portal, org.mockito.Mockito.times(2)).prepareInvite(anyInt(), anyString(), any(), anyString(),
                    operation.capture(), any());
            assertThat(operation.getAllValues()).containsOnly(row.getDeliveryOperationId());
            assertThat(row.getState()).isEqualTo(State.SENT);
        }

        @Test
        @DisplayName("should stop and record nothing sent when the portal refuses to prepare")
        void shouldAbandon_whenPrepareIsRefused() {
            when(portal.prepareInvite(anyInt(), anyString(), any(), anyString(), anyString(), any()))
                    .thenThrow(PatientPortalException.ofStatus(409, "/prepare", "portal account already exists"));

            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOf(PatientPortalException.class);

            assertThat(onlyRow().getState()).isEqualTo(State.ABANDONED);
            verify(emailManager, never()).sendEmailWithResult(any(), any(), any(EmailManager.DispatchGate.class));
        }
    }

    @Nested
    @DisplayName("refusals before any portal call")
    class Refusals {

        @Test
        @DisplayName("should refuse a patient with no email on the chart")
        void shouldRefuse_whenEmailIsMissing() {
            Demographic patient = patient();
            patient.setEmail("  ");

            assertRefusedBeforePortal(() -> service.invite(user, patient, staff, emailRequest()), Reason.MISSING_EMAIL);
        }

        @Test
        @DisplayName("should refuse an email address that is not valid")
        void shouldRefuse_whenEmailIsInvalid() {
            Demographic patient = patient();
            patient.setEmail("not-an-address");

            assertRefusedBeforePortal(() -> service.invite(user, patient, staff, emailRequest()), Reason.INVALID_EMAIL);
        }

        @Test
        @DisplayName("should refuse an incomplete date of birth")
        void shouldRefuse_whenDateOfBirthIsIncomplete() {
            Demographic patient = patient();
            patient.setDateOfBirth("");

            assertRefusedBeforePortal(() -> service.invite(user, patient, staff, emailRequest()),
                    Reason.INCOMPLETE_DATE_OF_BIRTH);
        }

        @Test
        @DisplayName("should refuse a date that does not exist")
        void shouldRefuse_whenDateOfBirthIsImpossible() {
            Demographic patient = patient();
            patient.setMonthOfBirth("02");
            patient.setDateOfBirth("31");

            assertRefusedBeforePortal(() -> service.invite(user, patient, staff, emailRequest()),
                    Reason.INCOMPLETE_DATE_OF_BIRTH);
        }

        @Test
        @DisplayName("should refuse a patient with no health card number")
        void shouldRefuse_whenHealthCardIsMissing() {
            Demographic patient = patient();
            patient.setHin(" - ");

            assertRefusedBeforePortal(() -> service.invite(user, patient, staff, emailRequest()),
                    Reason.MISSING_HEALTH_CARD);
        }

        @Test
        @DisplayName("should refuse when consent blocks the email, with the email gate's own message")
        void shouldRefuse_whenConsentBlocks() {
            when(emailManager.consentBlockMessage(any(), any()))
                    .thenReturn("Email blocked: patient has explicitly opted out of email communication.");

            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOfSatisfying(PortalInviteException.class, exception -> {
                        assertThat(exception.reason()).isEqualTo(Reason.CONSENT_BLOCKED);
                        assertThat(exception.getMessage()).contains("opted out");
                    });
            verifyNoInteractions(portal);
            verify(deliveries, never()).claim(any());
        }

        @Test
        @DisplayName("should refuse text message invitations until an SMS provider exists")
        void shouldRefuse_whenChannelIsSms() {
            assertRefusedBeforePortal(() -> service.invite(user, patient(), staff,
                    new InviteRequest(Channel.SMS, false, false, null)), Reason.CHANNEL_UNAVAILABLE);
        }

        @Test
        @DisplayName("should refuse when the public portal address is not configured")
        void shouldRefuse_whenNotConfigured() {
            service = new PortalInviteDeliveryService(portal, portalSettings("https://portal-api.clinic.example"),
                    new PortalInviteSettings(null, "clinic@example.invalid"), emailManager, deliveries,
                    mock(EmailConfigDao.class), emailLogs, clockAtNow());

            assertRefusedBeforePortal(() -> service.invite(user, patient(), staff, emailRequest()),
                    Reason.NOT_CONFIGURED);
        }

        private void assertRefusedBeforePortal(Runnable call, Reason reason) {
            assertThatThrownBy(call::run)
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(reason));
            verifyNoInteractions(portal);
            verify(deliveries, never()).claim(any());
        }
    }

    @Nested
    @DisplayName("replacing a pending invitation")
    class Replacement {

        @BeforeEach
        void pendingInviteExists() {
            when(portal.listInvites(PATIENT, staff)).thenReturn(List.of(invite(9L, "pending")));
        }

        @Test
        @DisplayName("should refuse to replace a pending invitation without confirmation")
        void shouldRefuse_withoutConfirmation() {
            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.PENDING_INVITE_EXISTS));
            verify(portal, never()).prepareInvite(anyInt(), anyString(), any(), anyString(), anyString(), any());
            verify(deliveries, never()).claim(any());
        }

        @Test
        @DisplayName("should resend the pending invitation when replacement is confirmed")
        void shouldResend_whenConfirmed() {
            PatientPortalInviteDelivery row = service.invite(user, patient(), staff,
                    new InviteRequest(Channel.EMAIL, true, false, null));

            verify(portal).prepareInviteResend(eq(9L), eq(row.getDeliveryOperationId()), eq(staff));
            assertThat(row.getSupersededInviteId()).isEqualTo(9L);
            assertThat(row.getState()).isEqualTo(State.SENT);
        }

        @Test
        @DisplayName("should refuse to resend an invitation that is not pending")
        void shouldRefuseResend_whenNotPending() {
            assertThatThrownBy(() -> service.resend(user, patient(), 8L, staff, emailRequest()))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.INVITE_NOT_PENDING));
            verify(deliveries, never()).claim(any());
        }
    }

    @Nested
    @DisplayName("failures after preparation")
    class FailuresAfterPreparation {

        @Test
        @DisplayName("should not send, and should withdraw the token, when the portal refuses the commit")
        void shouldNotSend_whenCommitIsRefused() {
            when(portal.commitInviteDelivery(anyLong(), anyString(), anyString(), any()))
                    .thenThrow(PatientPortalException.ofStatus(409, "/commit", "invite delivery conflicts"));

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(events).doesNotContain("send");
            assertThat(row.getState()).isEqualTo(State.ABANDONED);
            assertThat(row.getErrorMessage()).startsWith(PortalInviteDeliveryService.COMMIT_REFUSED);
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
        }

        @Test
        @DisplayName("should not send when the commit's outcome is unknown, and withdraw the token")
        void shouldNotSend_whenCommitOutcomeIsUnknown() {
            when(portal.commitInviteDelivery(anyLong(), anyString(), anyString(), any()))
                    .thenThrow(PatientPortalException.ofTransportFailure("/commit", null));

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(events).doesNotContain("send");
            assertThat(row.getState()).isEqualTo(State.ABANDONED);
            assertThat(row.getErrorMessage()).startsWith(PortalInviteDeliveryService.COMMIT_UNKNOWN);
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
        }

        @Test
        @DisplayName("should record an uncertain send and never revoke the live token")
        void shouldRecordUncertainSend_withoutRevoking() {
            transport = EmailSendResult.TransportOutcome.UNCONFIRMED;

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(row.getState()).isEqualTo(State.SEND_UNCERTAIN);
            verify(portal, never()).revokeInvite(anyInt(), anyLong(), any());
        }

        @Test
        @DisplayName("should record a refused send and keep the token for a resend to replace")
        void shouldRecordRefusedSend_withoutRevoking() {
            transport = EmailSendResult.TransportOutcome.FAILED;

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(row.getState()).isEqualTo(State.SEND_FAILED);
            assertThat(row.getErrorMessage()).isEqualTo(PortalInviteDeliveryService.SEND_REFUSED);
            verify(portal, never()).revokeInvite(anyInt(), anyLong(), any());
        }

        @Test
        @DisplayName("should withdraw the token when the email is blocked before the gate runs")
        void shouldAbandon_whenTheEmailIsBlockedInsideTheSend() {
            when(emailManager.sendEmailWithResult(any(), any(), any(EmailManager.DispatchGate.class)))
                    .thenAnswer(invocation -> EmailSendResult.failed(emailLog(), true));

            PatientPortalInviteDelivery row = service.invite(user, patient(), staff, emailRequest());

            assertThat(row.getState()).isEqualTo(State.ABANDONED);
            verify(portal, never()).commitInviteDelivery(anyLong(), anyString(), anyString(), any());
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
        }
    }

    @Nested
    @DisplayName("interruptions")
    class Interruptions {

        @Test
        @DisplayName("should keep a lost prepare response resolvable rather than finishing the attempt")
        void shouldLeaveAttemptOpen_whenThePrepareOutcomeIsUnknown() {
            when(portal.prepareInvite(anyInt(), anyString(), any(), anyString(), anyString(), any()))
                    .thenThrow(PatientPortalException.ofTransportFailure("/prepare", null));

            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOf(PatientPortalException.class);

            PatientPortalInviteDelivery row = onlyRow();
            assertThat(row.getState()).isEqualTo(State.PREPARING);
            assertThat(row.getState().isTerminal()).isFalse();
            assertThat(PortalInviteDeliveryService.decisionsFor(row.getState())).contains(Decision.ABANDON);
            assertThat(row.getErrorMessage()).isEqualTo(PortalInviteDeliveryService.PREPARE_UNKNOWN);
        }

        @Test
        @DisplayName("should record an uncertain send and keep the live token when the send throws")
        void shouldRecordUncertainSend_whenTheSendThrows() {
            when(emailManager.sendEmailWithResult(any(), any(), any(EmailManager.DispatchGate.class)))
                    .thenAnswer(invocation -> {
                        EmailManager.DispatchGate gate = invocation.getArgument(2);
                        gate.beforeDispatch(emailLog());
                        throw new IllegalStateException("mail session died");
                    });

            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(onlyRow().getState()).isEqualTo(State.SEND_UNCERTAIN);
            verify(portal, never()).revokeInvite(anyInt(), anyLong(), any());
        }

        @Test
        @DisplayName("should withdraw the token when the send throws before the gate runs")
        void shouldAbandon_whenTheSendThrowsBeforeTheGate() {
            when(emailManager.sendEmailWithResult(any(), any(), any(EmailManager.DispatchGate.class)))
                    .thenThrow(new RuntimeException("missing required sec object (_email)"));

            assertThatThrownBy(() -> service.invite(user, patient(), staff, emailRequest()))
                    .isInstanceOf(RuntimeException.class);

            assertThat(onlyRow().getState()).isEqualTo(State.ABANDONED);
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
        }
    }

    @Nested
    @DisplayName("recovery")
    class Recovery {

        @Test
        @DisplayName("should refuse to resolve an attempt that changed less than 15 minutes ago")
        void shouldRefuse_whenTooEarly() {
            PatientPortalInviteDelivery row = storedRow(State.SEND_UNCERTAIN, Duration.ofMinutes(14));

            assertThatThrownBy(() -> service.recover(user, patient(), row.getId(), Decision.CONFIRM_SENT, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.RECOVERY_TOO_EARLY));
        }

        @Test
        @DisplayName("should confirm an uncertain send and resolve its email")
        void shouldConfirmSent_afterTheGuard() {
            PatientPortalInviteDelivery row = storedRow(State.SEND_UNCERTAIN, Duration.ofMinutes(16));

            PatientPortalInviteDelivery resolved =
                    service.recover(user, patient(), row.getId(), Decision.CONFIRM_SENT, staff);

            assertThat(resolved.getState()).isEqualTo(State.SENT);
            verify(emailLogs).transitionEmailStatus(eq(EMAIL_LOG), eq(EmailStatus.PENDING), eq(EmailStatus.RESOLVED),
                    anyString(), any(Date.class));
            verify(portal, never()).revokeInvite(anyInt(), anyLong(), any());
        }

        @Test
        @DisplayName("should revoke the live token when staff confirm the email never arrived")
        void shouldRevoke_whenConfirmedNotSent() {
            PatientPortalInviteDelivery row = storedRow(State.SEND_UNCERTAIN, Duration.ofMinutes(16));

            PatientPortalInviteDelivery resolved =
                    service.recover(user, patient(), row.getId(), Decision.CONFIRM_NOT_SENT, staff);

            verify(portal).revokeInvite(PATIENT, INVITE, staff);
            assertThat(resolved.getState()).isEqualTo(State.REVOKED);
        }

        @Test
        @DisplayName("should abandon a queued attempt, withdraw its token and close its email as not sent")
        void shouldAbandon_aQueuedAttempt() {
            PatientPortalInviteDelivery row = storedRow(State.QUEUED, Duration.ofMinutes(16));

            PatientPortalInviteDelivery resolved =
                    service.recover(user, patient(), row.getId(), Decision.ABANDON, staff);

            assertThat(resolved.getState()).isEqualTo(State.ABANDONED);
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
            verify(emailLogs).transitionEmailStatus(eq(EMAIL_LOG), eq(EmailStatus.PENDING), eq(EmailStatus.FAILED),
                    anyString(), any(Date.class));
        }

        @Test
        @DisplayName("should recover a lost prepare response before abandoning, so its token can be withdrawn")
        void shouldAbandonPreparing_byNamingTheLostPreparation() {
            PatientPortalInviteDelivery row = storedRow(State.PREPARING, Duration.ofMinutes(16));
            injectDependency(row, "portalInviteId", null);

            service.recover(user, patient(), row.getId(), Decision.ABANDON, staff);

            verify(portal).prepareInvite(eq(PATIENT), anyString(), any(), anyString(),
                    eq(row.getDeliveryOperationId()), eq(staff));
            verify(portal).revokeInvite(PATIENT, INVITE, staff);
        }

        @ParameterizedTest
        @EnumSource(value = State.class, names = {"SENT", "SEND_FAILED", "ABANDONED", "REVOKED"})
        @DisplayName("should refuse to resolve an attempt that already finished")
        void shouldRefuse_whenFinished(State state) {
            PatientPortalInviteDelivery row = storedRow(state, Duration.ofHours(1));

            assertThatThrownBy(() -> service.recover(user, patient(), row.getId(), Decision.ABANDON, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.RECOVERY_NOT_ALLOWED));
        }

        @Test
        @DisplayName("should refuse a decision that does not fit the attempt's state")
        void shouldRefuse_whenDecisionDoesNotFit() {
            PatientPortalInviteDelivery row = storedRow(State.QUEUED, Duration.ofHours(1));

            assertThatThrownBy(() -> service.recover(user, patient(), row.getId(), Decision.CONFIRM_SENT, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.RECOVERY_NOT_ALLOWED));
        }

        @Test
        @DisplayName("should refuse to resolve another patient's attempt")
        void shouldRefuse_forAnotherPatient() {
            PatientPortalInviteDelivery row = storedRow(State.QUEUED, Duration.ofHours(1));
            Demographic other = patient();
            other.setDemographicNo(PATIENT + 1);

            assertThatThrownBy(() -> service.recover(user, other, row.getId(), Decision.ABANDON, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.DELIVERY_NOT_FOUND));
            verifyNoInteractions(portal);
        }

        @Test
        @DisplayName("should refuse recovery against a different clinic on the same portal")
        void shouldRefuse_whenTheClinicChanged() {
            PatientPortalInviteDelivery row = storedRow(State.QUEUED, Duration.ofHours(1));
            PatientPortalSettings otherClinic = PatientPortalSettings.fromProperties(Map.of(
                    PatientPortalSettings.BASE_URL_KEY, "https://portal-api.clinic.example",
                    PatientPortalSettings.CLINIC_ID_KEY, "othertown",
                    PatientPortalSettings.SERVICE_TOKEN_KEY, "t".repeat(32),
                    PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                    PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                    PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
            service = new PortalInviteDeliveryService(portal, otherClinic,
                    new PortalInviteSettings("https://portal.clinic.example", "clinic@example.invalid"),
                    emailManager, deliveries, mock(EmailConfigDao.class), emailLogs, clockAtNow());

            assertThatThrownBy(() -> service.recover(user, patient(), row.getId(), Decision.ABANDON, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.PORTAL_CONNECTION_CHANGED));
            verifyNoInteractions(portal);
        }

        @Test
        @DisplayName("should refuse recovery against a different portal address")
        void shouldRefuse_whenThePortalAddressChanged() {
            PatientPortalInviteDelivery row = storedRow(State.QUEUED, Duration.ofHours(1));
            service = new PortalInviteDeliveryService(portal, portalSettings("https://other-portal.clinic.example"),
                    new PortalInviteSettings("https://portal.clinic.example", "clinic@example.invalid"),
                    emailManager, deliveries, mock(EmailConfigDao.class), emailLogs, clockAtNow());

            assertThatThrownBy(() -> service.recover(user, patient(), row.getId(), Decision.ABANDON, staff))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.PORTAL_CONNECTION_CHANGED));
            verifyNoInteractions(portal);
        }

        private PatientPortalInviteDelivery storedRow(State state, Duration idle) {
            PatientPortalInviteDelivery row = deliveries.claim(new PatientPortalInviteDelivery("inv-stored", PATIENT,
                    "maplecreek", "https://portal-api.clinic.example", Channel.EMAIL, null, "999998"));
            row.setState(state);
            row.setPortalInviteId(INVITE);
            row.setEmailLogId(EMAIL_LOG);
            injectDependency(row, "updatedAt", Date.from(NOW.minus(idle)));
            return row;
        }
    }

    // --- fixtures ----------------------------------------------------------------------------

    private void backDeliveriesInMemory() {
        when(deliveries.claim(any())).thenAnswer(invocation -> {
            PatientPortalInviteDelivery row = invocation.getArgument(0);
            injectDependency(row, "id", ids.incrementAndGet());
            touch(row);
            rows.put(row.getId(), row);
            return row;
        });
        when(deliveries.find(org.mockito.ArgumentMatchers.<Object>any()))
                .thenAnswer(invocation -> rows.get(((Number) invocation.getArgument(0)).longValue()));
        when(deliveries.advance(anyLong(), any(State.class), any(State.class), any())).thenAnswer(invocation -> {
            PatientPortalInviteDelivery row = rows.get((Long) invocation.getArgument(0));
            if (row == null || row.getState() != invocation.getArgument(1)) {
                return null;
            }
            Consumer<PatientPortalInviteDelivery> change = invocation.getArgument(3);
            if (change != null) {
                change.accept(row);
            }
            row.setState(invocation.getArgument(2));
            touch(row);
            return row;
        });
    }

    private void scriptPortal() {
        when(portal.listInvites(anyInt(), any())).thenReturn(List.of());
        when(portal.prepareInvite(anyInt(), anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    events.add("prepare");
                    return prepared(invocation.getArgument(4), null);
                });
        when(portal.prepareInviteResend(anyLong(), anyString(), any())).thenAnswer(invocation -> {
            events.add("prepare");
            return prepared(invocation.getArgument(1), invocation.getArgument(0));
        });
        when(portal.commitInviteDelivery(anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            events.add("commit");
            return new PatientPortalInviteDto(INVITE, "maplecreek", PATIENT, "pending", "999998", "Dr Example", 1,
                    NOW, "Dr Example", PATIENT_EXPIRY, null, null);
        });
    }

    /** Behaves as EmailManager does: the gate runs on the stored row, and a refusal means nothing is sent. */
    private void scriptEmailManager() throws Exception {
        when(emailManager.sendEmailWithResult(any(), any(), any(EmailManager.DispatchGate.class)))
                .thenAnswer(invocation -> {
                    EmailData email = invocation.getArgument(1);
                    EmailManager.DispatchGate gate = invocation.getArgument(2);
                    EmailLog log = emailLog();
                    events.add("stored");
                    try {
                        gate.beforeDispatch(log);
                    } catch (EmailSendingException refused) {
                        // completeFailedSend records a definite failure on the outbox row.
                        log.setStatus(EmailStatus.FAILED);
                        return EmailSendResult.failed(log, true);
                    }
                    bodyAtSend = email.getBody();
                    events.add("send");
                    return switch (transport) {
                        case ACCEPTED -> EmailSendResult.accepted(log, true);
                        case UNCONFIRMED -> EmailSendResult.unconfirmed(log);
                        case FAILED -> EmailSendResult.failed(log, true);
                    };
                });
    }

    private PatientPortalPreparedInviteDto prepared(String operationId, Long supersedes) {
        PatientPortalInviteDto invite = new PatientPortalInviteDto(INVITE, "maplecreek", PATIENT, "prepared",
                "999998", "Dr Example", 1, NOW, "Dr Example", NOW.plus(Duration.ofDays(7)), null, supersedes);
        return new PatientPortalPreparedInviteDto(
                new PatientPortalIssuedInviteDto(invite, PortalSecret.of(CODE)), operationId);
    }

    private PatientPortalInviteDto invite(long id, String status) {
        return new PatientPortalInviteDto(id, "maplecreek", PATIENT, status, "999998", "Dr Example", 1, NOW,
                "Dr Example", PATIENT_EXPIRY, null, null);
    }

    private EmailLog emailLog() {
        EmailLog log = new EmailLog();
        injectDependency(log, "id", EMAIL_LOG);
        log.setStatus(EmailStatus.PENDING);
        return log;
    }

    private PatientPortalInviteDelivery onlyRow() {
        assertThat(rows).hasSize(1);
        return rows.values().iterator().next();
    }

    private void touch(PatientPortalInviteDelivery row) {
        injectDependency(row, "updatedAt", Date.from(clock));
    }

    private Clock clockAtNow() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static InviteRequest emailRequest() {
        return new InviteRequest(Channel.EMAIL, false, false, null);
    }

    private static Demographic patient() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setEmail("patient@example.com");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("05");
        patient.setDateOfBirth("20");
        patient.setHin("1234 567 890");
        return patient;
    }

    private static PatientPortalSettings portalSettings(String baseUrl) {
        return PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, baseUrl,
                PatientPortalSettings.CLINIC_ID_KEY, "maplecreek",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "t".repeat(32),
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
    }
}
