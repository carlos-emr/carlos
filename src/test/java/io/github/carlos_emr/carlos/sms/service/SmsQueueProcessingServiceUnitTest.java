package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@Tag("unit")
@Tag("service")
class SmsQueueProcessingServiceUnitTest {
    private static final SmsConsentDecisionDto CONSENTED = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"));

    private static final Instant FIRST_ATTEMPT_AT = Instant.parse("2026-06-08T12:00:00Z");
    private static final Instant RETRY_SCHEDULED_AT = Instant.parse("2026-06-08T12:05:00Z");
    private static final Instant SECOND_ATTEMPT_AT = Instant.parse("2026-06-08T12:10:00Z");

    @Test
    @DisplayName("processDueMessages sends queued messages when rate limit permits")
    void shouldSendMessage_whenQueueItemIsDue() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.SENT, 1);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-1");
    }

    @Test
    @DisplayName("processDueMessages drains queued rows for a non-STUB provider")
    void shouldSendMessage_whenQueueItemIsForNonStubProvider() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.VOIPMS
        );
        assignId(transaction, 1L);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new VoipMsAcceptingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderType)
                .containsExactly(SmsStatus.SENT, SmsProviderType.VOIPMS);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-voipms-1");
    }

    @Test
    @DisplayName("processDueMessages leaves queued messages untouched when rate limited")
    void shouldSkipMessage_whenRateLimitIsExceeded() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
    }

    @Test
    @DisplayName("processDueMessages schedules retry for failed SMS provider attempts")
    void shouldScheduleRetry_whenProviderFailsBeforeMaxAttempts() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new FailingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS queued provider failure recorded; retry scheduled.");
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("processDueMessages marks final failure after retry limit")
    void shouldMarkFailed_whenRetryLimitIsReached() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new FailingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getErrorCode,
                        SmsTransaction::getErrorMessage,
                        SmsTransaction::getNextAttemptAt
                )
                .containsExactly(
                        SmsStatus.FAILED,
                        3,
                        "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED",
                        "SMS queued provider failure reached retry limit; no further retry scheduled.",
                        null
                );
    }

    @Test
    @DisplayName("processDueMessages records queue SMS provider exceptions distinctly")
    void shouldAwaitStatusLookup_whenQueuedProviderThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new ThrowingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.SENDING, 1, "QUEUE_PROVIDER_EXCEPTION");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
    }

    @Test
    @DisplayName("processDueMessages records SMS provider resolution exceptions distinctly")
    void shouldAwaitStatusLookup_whenQueuedProviderResolutionThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of()),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.SENDING, 1, "QUEUE_PROVIDER_EXCEPTION");
        assertThat(transaction.getErrorMessage())
                .isEqualTo("SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
    }

    @Test
    @DisplayName("processDueMessages marks final queue SMS provider exceptions distinctly")
    void shouldAwaitStatusLookup_whenQueuedProviderThrowsAtRetryLimit() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new ThrowingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> true,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isEqualTo(1);
        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getErrorCode,
                        SmsTransaction::getErrorMessage,
                        SmsTransaction::getNextAttemptAt
                )
                .containsExactly(
                        SmsStatus.SENDING,
                        3,
                        "QUEUE_PROVIDER_EXCEPTION",
                        "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.",
                        null
                );
    }

    @Test
    @DisplayName("processDueMessages reconciles stale sending rows when SMS provider status is found")
    void shouldMarkSent_whenStaleProviderStatusIsFound() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new FoundStatusProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.SENT, 1, null);
        assertThat(transaction.getProviderMessageId()).isEqualTo("provider-1");
    }

    @Test
    @DisplayName("processDueMessages retries stale sending rows when SMS provider status is not found")
    void shouldScheduleRetry_whenStaleProviderStatusIsNotFound() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new NotFoundStatusProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED");
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider status is not found at retry limit")
    void shouldMarkFailed_whenStaleProviderStatusIsNotFoundAtRetryLimit() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        transaction.markSending(new Date(0));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new NotFoundStatusProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 3, "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED");
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider status lookup is unavailable")
    void shouldMarkFailed_whenStaleProviderStatusLookupIsUnavailable() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 1, "PROVIDER_STATUS_LOOKUP_UNSUPPORTED");
    }

    @Test
    @DisplayName("processDueMessages fails stale sending rows when SMS provider resolution fails")
    void shouldMarkFailed_whenStaleProviderResolutionThrows() {
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(new Date(0));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of()),
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                providerType -> false,
                command -> CONSENTED
        );

        int processed = worker.processDueMessages(25);

        assertThat(processed).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 1, "QUEUE_STALE_STATUS_LOOKUP_EXCEPTION");
    }

    @Test
    @DisplayName("queued sends recheck consent before using a provider or rate-limit permit")
    void shouldBlockQueuedSend_whenConsentIsNoLongerAllowed() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsProviderClientResolver resolver = mock(SmsProviderClientResolver.class);
        SmsSendRateLimitService limiter = mock(SmsSendRateLimitService.class);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, resolver, new SmsRetryCalculator(), limiter,
                command -> SmsConsentDecisionDto.blocked(SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", "blocked"));

        assertThat(worker.processDueMessages(1)).isEqualTo(1);
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.OPTOUT_BLOCKED);
        assertThat(transaction.toSendCommand().body()).isNull();
        org.mockito.Mockito.verifyNoInteractions(resolver, limiter);
    }

    @Test
    @DisplayName("a consent lookup failure schedules a backed-off retry without sending")
    void shouldScheduleBackedOffRetry_whenConsentRecheckThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsProviderClientResolver resolver = mock(SmsProviderClientResolver.class);
        SmsSendRateLimitService limiter = mock(SmsSendRateLimitService.class);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, resolver, new SmsRetryCalculator(), limiter,
                command -> {
                    throw new IllegalStateException("consent store unavailable");
                });
        Date startedAt = new Date();

        assertThat(worker.processDueMessages(5)).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, 1, "QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED");
        assertThat(transaction.getNextAttemptAt()).isAfterOrEqualTo(new Date(startedAt.getTime() + 60_000));
        assertThat(transaction.toSendCommand().body()).isNotNull();
        org.mockito.Mockito.verifyNoInteractions(resolver, limiter);
    }

    @Test
    @DisplayName("a row whose consent recheck keeps failing does not block newer rows on later runs")
    void shouldSendNewerMessage_whenOlderRowKeepsFailingConsentRecheck() {
        SmsTransaction poisoned = queuedTransaction();
        SmsTransaction healthy = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(healthy, 2L);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(poisoned, healthy));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> {
                    if (Integer.valueOf(123).equals(command.demographicNo())) {
                        throw new IllegalStateException("consent record cannot be loaded");
                    }
                    return CONSENTED;
                });

        assertThat(worker.processDueMessages(5)).isZero();
        assertThat(healthy)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(healthy.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(poisoned.getStatus()).isEqualTo(SmsStatus.QUEUED);
    }

    @Test
    @DisplayName("a consent recheck that still fails at the retry limit fails the row for manual review")
    void shouldMarkFailed_whenConsentRecheckThrowsAtRetryLimit() {
        SmsTransaction transaction = queuedTransaction();
        scheduleTwoFailedAttempts(transaction);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsProviderClientResolver resolver = mock(SmsProviderClientResolver.class);
        SmsSendRateLimitService limiter = mock(SmsSendRateLimitService.class);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                resolver,
                new SmsRetryCalculator(3, Duration.ofSeconds(1), Duration.ofSeconds(10)),
                limiter,
                command -> {
                    throw new IllegalStateException("consent store unavailable");
                });

        assertThat(worker.processDueMessages(5)).isZero();
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, 3, "QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED");
        org.mockito.Mockito.verifyNoInteractions(resolver, limiter);
    }

    @Test
    @DisplayName("a failure to record the consent-check failure does not abort the run")
    void shouldFinishRun_whenRecordingConsentCheckFailureThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction)) {
            @Override
            public SmsTransaction markRetryScheduled(
                    SmsTransaction row, SmsProviderSendResultDto providerResult, Date nextAttemptAt) {
                throw new IllegalStateException("database unavailable");
            }
        };
        SmsProviderClientResolver resolver = mock(SmsProviderClientResolver.class);
        SmsSendRateLimitService limiter = mock(SmsSendRateLimitService.class);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, resolver, new SmsRetryCalculator(), limiter,
                command -> {
                    throw new IllegalStateException("consent store unavailable");
                });

        assertThat(worker.processDueMessages(5)).isZero();
        // The claim could not be handed back, so the row stays SENDING for stale recovery to reconcile.
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENDING);
        org.mockito.Mockito.verifyNoInteractions(resolver, limiter);
    }

    @Test
    @DisplayName("a dispatch-time permit that relies on a different consent record rewrites the audit snapshot")
    void shouldRecordDispatchConsent_whenPermitReliesOnDifferentRecord() {
        SmsTransaction transaction = queuedTransaction();
        transaction.recordConsentDecision(SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 5, Instant.parse("2026-09-01T14:30:00Z")));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        Instant reconsentedAt = Instant.parse("2026-09-10T09:00:00Z");
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 9, reconsentedAt));

        assertThat(worker.processDueMessages(1)).isEqualTo(1);

        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(transaction.getConsentId()).isEqualTo(9);
        assertThat(transaction.getConsentLastUpdateDate()).isEqualTo(Date.from(reconsentedAt));
    }

    @Test
    @DisplayName("a dispatch-time permit that matches the admission snapshot costs no extra write")
    void shouldNotRewriteSnapshot_whenPermitMatchesAdmissionRecord() {
        SmsTransaction transaction = queuedTransaction();
        SmsConsentDecisionDto admitted = SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 5, Instant.parse("2026-09-01T14:30:00Z"));
        transaction.recordConsentDecision(admitted);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> admitted);

        assertThat(worker.processDueMessages(1)).isEqualTo(1);

        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(recorder.consentDecisionWrites).isZero();
    }

    @Test
    @DisplayName("a row whose dispatch-time snapshot write is rejected is not sent on an unrecorded consent")
    void shouldNotSend_whenDispatchConsentSnapshotIsRejected() {
        SmsTransaction transaction = queuedTransaction();
        transaction.recordConsentDecision(SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 5, Instant.parse("2026-09-01T14:30:00Z")));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction)) {
            @Override
            public SmsTransaction recordConsentDecision(SmsTransaction row, SmsConsentDecisionDto decision) {
                // The JPA recorder's answer when the row changed under the claim.
                throw new SmsTransactionClaimConflictException(row.getId());
            }
        };
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> SmsConsentDecisionDto.permitted(
                        SmsConsentStatus.OPT_IN, 9, Instant.parse("2026-09-10T09:00:00Z")));

        assertThat(worker.processDueMessages(1)).isZero();

        verify(client, never()).send(any(), anyString());
        assertThat(transaction.getConsentId()).isEqualTo(5);
    }

    @Test
    @DisplayName("a rate limiter failure hands the claim back and leaves the other providers draining")
    void shouldReleaseClaimAndDrainOtherProvider_whenRateLimiterThrows() {
        SmsTransaction stubRow = queuedTransaction();
        SmsTransaction voipMsRow = queuedVoipMsTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(stubRow, voipMsRow));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient(), new VoipMsAcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> {
                    // VoIP.ms drains before the stub provider, so this proves the providers after it still run.
                    if (providerType == SmsProviderType.VOIPMS) {
                        throw new IllegalStateException("rate limit row cannot be written");
                    }
                    return true;
                },
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(voipMsRow)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
        assertThat(stubRow.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("a claim that cannot be handed back stays for stale recovery and the other providers keep draining")
    void shouldDrainOtherProvider_whenClaimCannotBeHandedBack() {
        SmsTransaction stubRow = queuedTransaction();
        SmsTransaction voipMsRow = queuedVoipMsTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(stubRow, voipMsRow)) {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                throw new IllegalStateException("claim release cannot be written");
            }
        };
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient(), new VoipMsAcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> providerType != SmsProviderType.VOIPMS,
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(voipMsRow.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(stubRow.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("a limiter failure whose hand-back fails with the same exception still leaves the other providers draining")
    void shouldDrainOtherProvider_whenLimiterAndHandBackFailTogether() {
        SmsTransaction stubRow = queuedTransaction();
        SmsTransaction voipMsRow = queuedVoipMsTransaction();
        IllegalStateException databaseDown = new IllegalStateException("database unavailable");
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(stubRow, voipMsRow)) {
            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                throw databaseDown;
            }
        };
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient(), new VoipMsAcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> {
                    if (providerType == SmsProviderType.VOIPMS) {
                        throw databaseDown;
                    }
                    return true;
                },
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(voipMsRow.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(stubRow.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(databaseDown.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("a consent recheck failure for one SMS provider leaves the other providers draining")
    void shouldDrainOtherProvider_whenConsentRecheckFailsForOneProvider() {
        SmsTransaction stubRow = queuedTransaction();
        SmsTransaction voipMsRow = queuedVoipMsTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(stubRow, voipMsRow));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient(), new VoipMsAcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> {
                    // The VoIP.ms row (patient 456) drains first, so the stub provider must still run after it.
                    if (Integer.valueOf(456).equals(command.demographicNo())) {
                        throw new IllegalStateException("consent record cannot be loaded");
                    }
                    return CONSENTED;
                });

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(stubRow.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(voipMsRow)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.QUEUED, "QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED");
    }

    @Test
    @DisplayName("the send and its result are applied to the row returned by the snapshot rewrite")
    void shouldSendOnReturnedRow_whenDispatchSnapshotIsRewritten() {
        SmsTransaction claimedCopy = queuedTransaction();
        SmsTransaction rewrittenCopy = queuedTransaction();
        SmsConsentDecisionDto reconsented = SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 9, Instant.parse("2026-09-10T09:00:00Z"));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(claimedCopy)) {
            @Override
            public SmsTransaction recordConsentDecision(SmsTransaction row, SmsConsentDecisionDto decision) {
                // The JPA recorder returns the freshly loaded row, whose version has advanced past the claim's.
                rewrittenCopy.markSending(new Date());
                rewrittenCopy.recordConsentDecision(decision);
                return rewrittenCopy;
            }
        };
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> reconsented);

        assertThat(worker.processDueMessages(1)).isEqualTo(1);

        assertThat(rewrittenCopy.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(claimedCopy.getStatus()).isEqualTo(SmsStatus.SENDING);
    }

    @Test
    @DisplayName("a consent snapshot that cannot be written hands the claim back and stops draining that SMS provider")
    void shouldStopDraining_whenDispatchConsentSnapshotCannotBeWritten() {
        SmsTransaction first = queuedTransaction();
        first.recordConsentDecision(SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 5, Instant.parse("2026-09-01T14:30:00Z")));
        SmsTransaction second = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(second, 2L);
        second.recordConsentDecision(SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 6, Instant.parse("2026-09-01T14:30:00Z")));
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(first, second)) {
            @Override
            public SmsTransaction recordConsentDecision(SmsTransaction row, SmsConsentDecisionDto decision) {
                throw new IllegalStateException("database unavailable");
            }
        };
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isZero();

        // A write failure is unlikely to be about one row, so the rest of the queue is left unclaimed; the
        // row whose snapshot failed is handed back, unsent.
        assertThat(first)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
        assertThat(second)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
    }

    @Test
    @DisplayName("a row taken over by another run's stale recovery during the permit wait is not sent, and the next row is")
    void shouldNotSend_whenClaimRenewalConflicts() {
        SmsTransaction first = queuedTransaction();
        SmsTransaction second = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(second, 2L);
        second.recordConsentDecision(CONSENTED);
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(first, second)) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction row, Date attemptAt) {
                events.add("renewClaim " + row.getId());
                if (row == first) {
                    // The JPA recorder's answer when the row changed under the claim.
                    throw new SmsTransactionClaimConflictException(row.getId());
                }
                return super.renewClaim(row, attemptAt);
            }
        };
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        when(client.send(any(), anyString())).thenReturn(SmsProviderSendResultDto.accepted("provider-2", SmsStatus.SENT));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(),
                providerType -> events.add("tryAcquire"),
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(events).containsExactly("tryAcquire", "renewClaim 1", "tryAcquire", "renewClaim 2");
        verify(client).send(any(), eq(second.getClientReferenceId()));
        verify(client, never()).send(any(), eq(first.getClientReferenceId()));
        assertThat(second.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("a claim renewal that cannot be written hands the claim back and stops draining that SMS provider")
    void shouldStopDraining_whenClaimRenewalCannotBeWritten() {
        SmsTransaction first = queuedTransaction();
        SmsTransaction second = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(second, 2L);
        second.recordConsentDecision(CONSENTED);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(first, second)) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction row, Date attemptAt) {
                throw new IllegalStateException("database unavailable");
            }
        };
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isZero();

        verify(client, never()).send(any(), anyString());
        // Nothing was sent, so the claim is handed back rather than left SENDING for stale recovery.
        assertThat(first)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
        assertThat(second)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
    }

    @Test
    @DisplayName("a claim renewal that loses a version race is treated as a changed row and the next row is sent")
    void shouldSendNextRow_whenClaimRenewalLosesVersionRace() {
        SmsTransaction first = queuedTransaction();
        SmsTransaction second = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(second, 2L);
        second.recordConsentDecision(CONSENTED);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(first, second)) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction row, Date attemptAt) {
                if (Long.valueOf(1L).equals(row.getId())) {
                    // The flush's version check lost to another writer.
                    throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SmsTransaction.class, 1L);
                }
                return super.renewClaim(row, attemptAt);
            }

            @Override
            public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
                throw new AssertionError("a row that changed under the claim belongs to whoever changed it");
            }
        };
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(new AcceptingProviderClient())),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isEqualTo(1);

        assertThat(first.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(second.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("a dispatch-time permit that names no consent state is never sent on")
    void shouldNotSend_whenDispatchPermitNamesNoConsentState() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(transaction, 1L);
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> SmsConsentDecisionDto.permitted(null, null, null));

        assertThat(worker.processDueMessages(1)).isZero();

        verify(client, never()).send(any(), anyString());
        // Backed off like a failed consent check, with the attempt counted, rather than handed back.
        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, "QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED", 1);

        // Not due again at once, so a permit that can never be recorded does not loop on every run.
        assertThat(worker.processDueMessages(1)).isZero();
        assertThat(transaction.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a claim renewal failure whose hand-back also fails leaves the row SENDING and ends the run cleanly")
    void shouldLeaveRowSending_whenHandBackAlsoFails() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction)) {
            @Override
            public SmsTransaction renewClaim(SmsTransaction row, Date attemptAt) {
                throw new IllegalStateException("database unavailable");
            }

            @Override
            public SmsTransaction releaseClaim(SmsTransaction row, Date dueAt) {
                throw new IllegalStateException("database still unavailable");
            }
        };
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(),
                providerType -> true,
                command -> CONSENTED);

        assertThat(worker.processDueMessages(5)).isZero();

        verify(client, never()).send(any(), anyString());
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENDING);
    }

    @Test
    @DisplayName("a consent recheck failure is logged without the exception message, which may carry PHI")
    void shouldNotLogExceptionMessage_whenConsentRecheckThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(
                recorder,
                mock(SmsProviderClientResolver.class),
                new SmsRetryCalculator(),
                mock(SmsSendRateLimitService.class),
                command -> {
                    throw new IllegalStateException("synthetic-hin-9876543210");
                });

        try (LogCapture logs = LogCapture.forLogger(SmsQueueProcessingService.class)) {
            worker.processDueMessages(1);

            assertThat(logs.events()).isNotEmpty();
            assertThat(logs.events()).allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("synthetic-hin-9876543210");
                assertThat(event.getThrown()).isNull();
            });
        }
    }

    @Test
    @DisplayName("an accepted send followed by a timeout is reconciled without a second send")
    void shouldReconcileWithoutResending_whenProviderAcceptsThenThrows() {
        SmsTransaction transaction = queuedTransaction();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(List.of(transaction));
        SmsProviderClient client = mock(SmsProviderClient.class);
        when(client.providerType()).thenReturn(SmsProviderType.STUB);
        when(client.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("synthetic timeout after acceptance"));
        when(client.lookupMessageStatus(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(SmsProviderMessageStatusDto.found(
                        SmsProviderSendResultDto.accepted("accepted-once", SmsStatus.SENT)));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(), type -> true, command -> CONSENTED);

        assertThat(worker.processDueMessages(1)).isEqualTo(1);
        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(transaction.getNextAttemptAt()).isNull();
        assertThat(worker.processDueMessages(1)).isZero();
        transaction.markStaleRecoveryStarted(new Date(0));
        worker.processDueMessages(1);

        assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
        verify(client, times(1)).send(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("sms-transaction-1"));
    }

    private static void scheduleTwoFailedAttempts(SmsTransaction transaction) {
        transaction.markSending(dateAt(FIRST_ATTEMPT_AT));
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                dateAt(RETRY_SCHEDULED_AT)
        );
        transaction.markSending(dateAt(SECOND_ATTEMPT_AT));
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                dateAt(RETRY_SCHEDULED_AT)
        );
    }

    private static SmsTransaction queuedVoipMsTransaction() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(456, "416-555-3434", "Appointment reminder", "999998"),
                SmsProviderType.VOIPMS
        );
        assignId(transaction, 2L);
        transaction.recordConsentDecision(CONSENTED);
        return transaction;
    }

    private static SmsTransaction queuedTransaction() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        assignId(transaction, 1L);
        transaction.recordConsentDecision(CONSENTED);
        return transaction;
    }

    private static Date dateAt(Instant instant) {
        return Date.from(instant);
    }

    private static class RecordingSmsTransactionService implements SmsTransactionService {
        private final List<SmsTransaction> transactions;
        private int consentDecisionWrites;

        private RecordingSmsTransactionService(List<SmsTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
        }

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                    SmsConsentDecisionDto decision) {
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            assignId(transaction, transactions.size() + 1L);
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
            transaction.markConsentBlocked(decision);
            return transaction;
        }

        @Override
        public SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            consentDecisionWrites++;
            transaction.recordConsentDecision(decision);
            return transaction;
        }

        @Override
        public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
            transaction.markSending(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
            transaction.markProviderResult(providerResult);
            return transaction;
        }

        @Override
        public SmsTransaction markRetryScheduled(
                SmsTransaction transaction,
                SmsProviderSendResultDto providerResult,
                Date nextAttemptAt
        ) {
            transaction.markRetryScheduled(providerResult, nextAttemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
            transaction.markClaimReleased(dueAt);
            return transaction;
        }

        @Override
        public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
            transaction.renewSendingClaim(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            return transactions.stream()
                    .filter(transaction -> transaction.getProviderType() == providerType)
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .filter(transaction ->
                            transaction.getNextAttemptAt() == null || !transaction.getNextAttemptAt().after(now)
                    )
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
            List<SmsTransaction> staleTransactions = transactions.stream()
                    .filter(transaction -> transaction.getProviderType() == providerType)
                    .filter(transaction -> transaction.getStatus() == SmsStatus.SENDING)
                    .filter(transaction -> transaction.getLastAttemptAt() != null)
                    .filter(transaction -> transaction.getLastAttemptAt().before(staleBefore))
                    .limit(limit)
                    .toList();
            staleTransactions.forEach(transaction -> transaction.markStaleRecoveryStarted(recoveryAt));
            return staleTransactions;
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

    private static class AcceptingProviderClient implements SmsProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.STUB;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            return SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT);
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

    private static class VoipMsAcceptingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.VOIPMS;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            return SmsProviderSendResultDto.accepted("provider-voipms-1", SmsStatus.SENT);
        }
    }

    private static class FailingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            return SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message");
        }
    }

    private static class ThrowingProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            throw new IllegalStateException("provider unavailable");
        }
    }

    private static class FoundStatusProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId) {
            return SmsProviderMessageStatusDto.found(
                    SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT)
            );
        }
    }

    private static class NotFoundStatusProviderClient extends AcceptingProviderClient {
        @Override
        public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId) {
            return SmsProviderMessageStatusDto.notFound();
        }
    }
}
