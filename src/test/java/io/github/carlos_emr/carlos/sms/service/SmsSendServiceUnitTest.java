package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
class SmsSendServiceUnitTest {
    private static final SmsConsentDecisionDto CONSENTED = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"));

    @Test
    @DisplayName("send records a consent-blocked row and never reaches the SMS provider when consent denies")
    void shouldBlockSend_whenConsentServiceDenies() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.blocked(
                        SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", "No SMS consent is recorded."),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(result.providerMessageId()).isNull();
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN");
    }

    @Test
    @DisplayName("send uses the SMS provider once validation and consent pass")
    void shouldUseProvider_whenConsentAllows() {
        AtomicReference<SmsSendCommand> consentCommand = new AtomicReference<>();
        SmsConsentService allowConsent = command -> {
            consentCommand.set(command);
            return CONSENTED;
        };
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(
                123,
                "416-555-1212",
                SmsRecipientPhoneType.WORK,
                "Appointment reminder",
                "999998"
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(result.providerMessageId()).startsWith("stub-");
        assertThat(consentCommand.get().recipientPhoneType()).isEqualTo(SmsRecipientPhoneType.WORK);
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
                    assertThat(transaction.getProviderMessageId()).startsWith("stub-");
                    assertThat(transaction.getRecipientPhoneType()).isEqualTo(SmsRecipientPhoneType.WORK);
                });
    }

    @Test
    @DisplayName("send marks the transaction sending before calling the SMS provider")
    void shouldMarkSendingBeforeProviderCall_whenConsentAllows() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))),
                recorder,
                providerType -> events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(events).containsExactly(
                "recordOutboundAttempt",
                "markSending",
                "tryAcquire",
                "renewClaim",
                "providerSend",
                "markProviderResult"
        );
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
                    assertThat(transaction.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("send neither calls the SMS provider nor takes a rate-limit permit when the row is already claimed")
    void shouldSkipProviderSend_whenClaimConflictOccurs() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events) {
            @Override
            public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
                events.add("markSending");
                throw new SmsTransactionClaimConflictException(transaction.getId());
            }
        };
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))),
                recorder,
                providerType -> events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(events).containsExactly(
                "recordOutboundAttempt",
                "markSending"
        );
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getAttemptCount()).isZero();
                });
    }

    @Test
    @DisplayName("send releases its claim, leaves the message queued and skips the SMS provider when rate limited")
    void shouldLeaveQueued_whenRateLimited() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> !events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(result.providerMessageId()).isNull();
        assertThat(events).containsExactly("recordOutboundAttempt", "markSending", "tryAcquire", "releaseClaim");
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getProviderMessageId()).isNull();
                    assertThat(transaction.getAttemptCount()).isZero();
                    assertThat(transaction.getNextAttemptAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("send reports the failure when the claim cannot be handed back after a rate-limit denial")
    void shouldReportFailure_whenReleaseFailsAfterRateLimitDenial() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                throw new IllegalStateException("release failed");
            }
        };
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> false,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendCommand command =
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("release failed");
    }

    @Test
    @DisplayName("send reports an unknown outcome when the hand-back loses to another claim and the row stays SENDING")
    void shouldReportOutcomeUnknown_whenReleaseReturnsSendingRow() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                // Another worker's claim won the version race: the row comes back still SENDING.
                return transaction;
            }
        };
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> false,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.messages()).containsExactly(SmsProviderSendResultDto.OUTCOME_UNKNOWN_MESSAGE);
    }

    @Test
    @DisplayName("send reports the newer outcome when the hand-back finds the row already delivered")
    void shouldReportNewerOutcome_whenReleaseReturnsDeliveredRow() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                // A delivery result was recorded first, so the hand-back returns that newer row.
                transaction.markProviderResult(SmsProviderSendResultDto.accepted("provider-42", SmsStatus.DELIVERED));
                return transaction;
            }
        };
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> false,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(result.providerMessageId()).isEqualTo("provider-42");
    }

    @Test
    @DisplayName("send confirms a queued result when the limiter fails and the claim is released")
    void shouldReleaseClaim_whenRateLimiterThrows() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events);
        IllegalStateException limiterFailure = new IllegalStateException("rate-limit row lock timed out");
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))),
                recorder,
                providerType -> {
                    events.add("tryAcquire");
                    throw limiterFailure;
                },
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));
        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);

        // Nothing was sent, so the row must not be left SENDING for stale recovery to misjudge.
        assertThat(events).containsExactly("recordOutboundAttempt", "markSending", "tryAcquire", "releaseClaim");
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getAttemptCount()).isZero();
                });
    }

    @Test
    @DisplayName("send logs only safe diagnostics when the limiter fails and release succeeds")
    void shouldLogSafeWarning_whenLimiterFailsAndClaimIsReleased() {
        String sensitiveCanary = "FAKE-PHI query parameters and credential canary";
        IllegalStateException limiterFailure = new IllegalStateException(
                sensitiveCanary, new IllegalArgumentException(sensitiveCanary));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder,
                type -> { throw limiterFailure; }, new SmsDefaultProviderResolver(() -> "STUB"));

        try (LogCapture logs = LogCapture.forLogger(SmsSendService.class)) {
            SmsSendResultDto result = service.send(
                    SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic log regression", "999998"));

            assertThat(result.accepted()).isTrue();
            assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrown()).isNull();
                assertThat(event.getMessage().getParameters())
                        .containsExactly("rate limiter", SmsStatus.QUEUED, "IllegalStateException");
            });
            assertThat(logs.messages()).containsExactly("SMS rate limiter failed before sending; claim release "
                    + "returned QUEUED. Failure type: IllegalStateException");
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message).doesNotContain(sensitiveCanary));
        }
    }

    @Test
    @DisplayName("send skips the SMS provider and reports the current row when stale recovery took it over during the permit wait")
    void shouldNotSend_whenClaimRenewalConflicts() {
        List<String> events = new ArrayList<>();
        Date recoveryRetryAt = new Date(System.currentTimeMillis() + 60_000);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
                events.add("renewClaim");
                throw new SmsTransactionClaimConflictException(transaction.getId());
            }

            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                // The version check drops the release and returns the row as recovery left it: requeued for later.
                events.add("releaseClaim");
                transaction.markRetryScheduled(SmsProviderSendResultDto.failed(
                        "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED", "synthetic recovery"), recoveryRetryAt);
                return transaction;
            }
        };
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))), recorder,
                providerType -> events.add("tryAcquire"), new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendResultDto result = service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(events).containsExactly(
                "recordOutboundAttempt", "markSending", "tryAcquire", "renewClaim", "releaseClaim");
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getNextAttemptAt).isEqualTo(recoveryRetryAt);
    }

    @Test
    @DisplayName("send hands the row back to the queue without sending when its claim cannot be renewed")
    void shouldReleaseClaim_whenClaimRenewalFails() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
                events.add("renewClaim");
                throw new IllegalStateException("synthetic renewal failure");
            }
        };
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))), recorder,
                providerType -> events.add("tryAcquire"), new SmsDefaultProviderResolver(() -> "STUB"));

        try (LogCapture logs = LogCapture.forLogger(SmsSendService.class)) {
            SmsSendResultDto result = service.send(
                    SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

            assertThat(result.accepted()).isTrue();
            assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
            assertThat(logs.messages()).containsExactly("SMS claim renewal failed before sending; claim release "
                    + "returned QUEUED. Failure type: IllegalStateException");
        }
        assertThat(events).containsExactly(
                "recordOutboundAttempt", "markSending", "tryAcquire", "renewClaim", "releaseClaim");
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getAttemptCount()).isZero();
                });
    }

    @Test
    @DisplayName("send preserves the limiter failure when releasing its claim also fails")
    void shouldPreserveFailure_whenLimiterAndClaimReleaseThrow() {
        IllegalStateException limiterFailure = new IllegalStateException("synthetic limiter failure");
        IllegalStateException releaseFailure = new IllegalStateException("synthetic release failure");
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                throw releaseFailure;
            }
        };
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder,
                type -> { throw limiterFailure; }, new SmsDefaultProviderResolver(() -> "STUB"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic failure", "999998")))
                .isSameAs(limiterFailure);
        assertThat(limiterFailure.getSuppressed()).containsExactly(releaseFailure);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus).isEqualTo(SmsStatus.SENDING);
    }

    @Test
    @DisplayName("send does not create a transaction for validation failures")
    void shouldSkipTransactionRecord_whenValidationFails() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(0, "not-a-phone", " ", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("send records uncertain SMS outcomes for status lookup")
    void shouldLeaveOutcomeUncertain_whenProviderThrows() {
        SmsConsentService allowConsent = command -> CONSENTED;
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of(new ThrowingSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.messages())
                .containsExactly("SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(
                        SmsStatus.SENDING,
                        "DIRECT_PROVIDER_EXCEPTION",
                        "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually."
                );
    }

    @Test
    @DisplayName("send preserves unresolved provider outcomes for manual recovery")
    void shouldLeaveOutcomeUncertain_whenProviderResolutionThrows() {
        SmsConsentService allowConsent = command -> CONSENTED;
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of()),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(
                        SmsStatus.SENDING,
                        "DIRECT_PROVIDER_EXCEPTION",
                        "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually."
                );
    }

    @Test
    @DisplayName("consent is evaluated before any outbound attempt becomes visible")
    void shouldNotPersistAnAttempt_whenConsentEvaluationThrows() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> {
            assertThat(recorder.transactions()).isEmpty();
            throw new IllegalStateException("synthetic consent outage");
        }, new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder, type -> true,
                new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendCommand command = SmsSendCommand.patientMessage(123, "416-555-1212", "Synthetic message", "999998");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("direct send reports the persisted delivery webhook when it beats the send result")
    void shouldReturnDelivered_whenWebhookWinsTheWriteRace() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto result) {
                transaction.markDeliveryEvent(new SmsDeliveryWebhookDto(SmsProviderType.STUB,
                        "webhook-result", SmsStatus.DELIVERED, java.time.Instant.now(), null, null, null));
                return transaction;
            }
        };
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder, type -> true,
                new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(
                123, "416-555-1212", "Synthetic message", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(result.providerMessageId()).isEqualTo("webhook-result");
    }

    @Test
    @DisplayName("send refuses without recording anything when SMS is turned off in Administration")
    void shouldRefuseSend_whenSmsIsTurnedOff() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsConfigService configService = mock(SmsConfigService.class);
        when(configService.sendingEnabled()).thenReturn(false);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB"),
                configService
        );

        SmsSendResultDto result = service.send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.messages()).containsExactly(SmsSendService.SMS_TURNED_OFF_MESSAGE);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("sendSystemTest sends a synthetic SYSTEM_TEST through STUB only, even while SMS is turned off")
    void shouldSendSystemTest_throughStubOnly() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsConfigService configService = mock(SmsConfigService.class);
        when(configService.sendingEnabled()).thenReturn(false);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permitted(SmsConsentStatus.SYSTEM_TEST, null, null),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "VOIPMS"),
                configService
        );

        SmsSendResultDto result = service.sendSystemTest("416-555-1212", "999998", 1001);

        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(recorder.transactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getProviderType()).isEqualTo(SmsProviderType.STUB);
            assertThat(transaction.getMessagePurpose()).isEqualTo(SmsMessagePurpose.SYSTEM_TEST);
            assertThat(transaction.getDemographicNo()).isNull();
            assertThat(transaction.getRequestedByHealthcareProviderNo()).isEqualTo("999998");
        });
    }

    @Test
    @DisplayName("sendSystemTest refuses a number that is not a valid phone number")
    void shouldRejectSystemTest_whenNumberIsInvalid() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> CONSENTED,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.sendSystemTest("not-a-number", "999998", 1001);

        assertThat(result.accepted()).isFalse();
        assertThat(recorder.transactions()).isEmpty();
    }

    private static class RecordingSmsTransactionService implements SmsTransactionService {
        private final List<SmsTransaction> transactions = new ArrayList<>();
        private final List<String> events;
        private long nextTransactionId = 1L;

        private RecordingSmsTransactionService() {
            this(new ArrayList<>());
        }

        private RecordingSmsTransactionService(List<String> events) {
            this.events = events;
        }

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                    SmsConsentDecisionDto decision) {
            events.add("recordOutboundAttempt");
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            assignId(transaction, nextTransactionId++);
            transactions.add(transaction);
            if (decision.allowed()) {
                transaction.recordConsentDecision(decision);
            } else {
                transaction.markConsentBlocked(decision);
            }
            return transaction;
        }

        @Override
        public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            events.add("markConsentBlocked");
            transaction.markConsentBlocked(decision);
            return transaction;
        }

        @Override
        public SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            transaction.recordConsentDecision(decision);
            return transaction;
        }

        @Override
        public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
            events.add("markSending");
            transaction.markSending(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
            events.add("markProviderResult");
            transaction.markProviderResult(providerResult);
            return transaction;
        }

        @Override
        public SmsTransaction markRetryScheduled(
                SmsTransaction transaction,
                SmsProviderSendResultDto providerResult,
                Date nextAttemptAt
        ) {
            events.add("markRetryScheduled");
            transaction.markRetryScheduled(providerResult, nextAttemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
            events.add("releaseClaim");
            transaction.markClaimReleased(dueAt);
            return transaction;
        }

        @Override
        public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
            events.add("renewClaim");
            transaction.renewSendingClaim(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
            events.add("recordInboundMessage");
            SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
            events.add("recordDeliveryEvent");
            SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            events.add("claimDueOutboundQueue");
            return transactions.stream()
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .peek(transaction -> transaction.markSending(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SmsTransaction> claimStaleSendingForRecovery(
                SmsProviderType providerType,
                Date staleBefore,
                Date recoveryAt,
                int limit
        ) {
            return List.of();
        }

        private List<SmsTransaction> transactions() {
            return transactions;
        }
    }

    private static void assignId(SmsTransaction transaction, long id) {
        try {
            Field idField = SmsTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
            transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(id));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to assign SMS transaction id for test", e);
        }
    }

    private static class EventRecordingStubSmsProviderClient extends StubSmsProviderClient {
        private final List<String> events;

        private EventRecordingStubSmsProviderClient(List<String> events) {
            this.events = events;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            events.add("providerSend");
            return super.send(command, clientReferenceId);
        }
    }

    private static class ThrowingSmsProviderClient implements SmsProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.STUB;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            throw new IllegalStateException("provider unavailable");
        }

        @Override
        public boolean validateCallback(String payload, Map<String, String> headers, String secret) {
            return false;
        }

        @Override
        public Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }

        @Override
        public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }
    }
}
