package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SmsQueueProcessingService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    // Per-run cap for worker calls without an explicit limit; tune with SMS provider throughput and queue volume.
    private static final int DEFAULT_BATCH_SIZE = 60;
    private static final Duration DEFAULT_STALE_SENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final String QUEUE_PROVIDER_EXCEPTION_CODE = "QUEUE_PROVIDER_EXCEPTION";
    private static final String QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_CODE =
            "QUEUE_STALE_STATUS_LOOKUP_EXCEPTION";
    private static final String QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE =
            "QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_CODE =
            "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_CODE =
            "QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE =
            "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_CODE =
            "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_CODE =
            "QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_CODE =
            "QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_MESSAGE =
            "SMS consent could not be checked before sending; retry scheduled.";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_MESSAGE =
            "SMS consent could not be checked before sending and retry limit was reached; nothing was sent.";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_MESSAGE =
            "SMS queued provider failure recorded; retry scheduled.";
    private static final String QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_MESSAGE =
            "SMS queued provider failure reached retry limit; no further retry scheduled.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE =
            "SMS stale send status lookup threw an exception; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE =
            "SMS stale send status lookup was unavailable; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_MESSAGE =
            "SMS stale send was not found by SMS provider status lookup; retry scheduled.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_MESSAGE =
            "SMS stale send was not found by SMS provider status lookup and retry limit was reached.";

    private final SmsTransactionService transactionRecorder;
    private final SmsProviderClientResolver providerResolver;
    private final SmsRetryCalculator retryPolicy;
    private final SmsSendRateLimitService rateLimiter;
    private final SmsConsentService consentService;

    public SmsQueueProcessingService(
            SmsTransactionService transactionRecorder,
            SmsProviderClientResolver providerResolver,
            SmsRetryCalculator retryPolicy,
            SmsSendRateLimitService rateLimiter,
            SmsConsentService consentService
    ) {
        this.transactionRecorder = transactionRecorder;
        this.providerResolver = providerResolver;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.consentService = consentService;
    }

    public int processDueMessages() {
        return processDueMessages(DEFAULT_BATCH_SIZE);
    }

    public int processDueMessages(int limit) {
        int safeLimit = Math.max(1, limit);
        // Drive every provider type, not a hardcoded default, so a row's queue is drained and rate-limited
        // under its own provider. Adding a provider to SmsProviderType is then enough for the worker to
        // pick up its queued rows; an unconfigured provider's rows retain a visible unresolved outcome
        // for recovery/manual review rather than sitting in the queue forever.
        for (SmsProviderType providerType : SmsProviderType.values()) {
            recoverStaleSending(providerType, safeLimit);
        }
        int processed = 0;
        for (SmsProviderType providerType : SmsProviderType.values()) {
            if (processed >= safeLimit) {
                break;
            }
            processed += drainProvider(providerType, safeLimit - processed);
        }
        return processed;
    }

    private int drainProvider(SmsProviderType providerType, int remaining) {
        int processed = 0;
        boolean shouldContinue = true;
        while (processed < remaining && shouldContinue) {
            // Claim before acquiring the rate-limit token so an empty queue never burns budget. If the
            // limiter then denies, release the claim back to QUEUED so the row is retried without losing
            // the rolled-back attempt, and move on to the next provider.
            List<SmsTransaction> transactions = transactionRecorder.claimDueOutboundQueue(
                    providerType,
                    new Date(),
                    1
            );
            if (transactions.isEmpty()) {
                shouldContinue = false;
            } else {
                SmsTransaction claimed = transactions.get(0);
                SmsConsentDecisionDto decision = recheckConsent(claimed);
                if (decision == null) {
                    // Consent could not be determined, so nothing may be sent on it. The failure may be an
                    // outage or may belong to this row alone, so the row is backed off like a failed send
                    // instead of being made due again, where it would head the queue on every run. Stop
                    // draining so an outage costs one row an attempt per run rather than the whole batch.
                    deferAfterConsentCheckFailure(claimed);
                    shouldContinue = false;
                } else if (!decision.allowed()) {
                    transactionRecorder.markConsentBlocked(claimed, decision);
                    processed++;
                } else if (!rateLimiter.tryAcquire(providerType)) {
                    transactionRecorder.releaseClaim(claimed, new Date());
                    shouldContinue = false;
                } else {
                    DispatchOutcome outcome = sendOnRecordedConsent(claimed, decision);
                    if (outcome == DispatchOutcome.SENT) {
                        processed++;
                    }
                    // A write failure is unlikely to be about one row; stop rather than strand the rest of
                    // the queue SENDING one row at a time.
                    shouldContinue = outcome != DispatchOutcome.SNAPSHOT_WRITE_FAILED;
                }
            }
        }
        return processed;
    }

    /**
     * Sends a claimed row once its audit snapshot names the consent record this dispatch relied on. The
     * admission snapshot is usually still current and costs no write.
     *
     * @return {@link DispatchOutcome#SENT} once the send was attempted; otherwise nothing was sent
     */
    private DispatchOutcome sendOnRecordedConsent(SmsTransaction claimed, SmsConsentDecisionDto decision) {
        SmsTransaction recorded = claimed;
        // A permit naming no consent state never counts as already recorded, even against an empty snapshot;
        // the row refuses to record it, so it ends below as not sent.
        if (decision.consentStatus() == null || !claimed.hasConsentSnapshot(decision)) {
            try {
                recorded = transactionRecorder.recordConsentDecision(claimed, decision);
            } catch (SmsTransactionClaimConflictException e) {
                // The row changed or vanished under the claim, so whoever changed it decides what happens
                // next. The recorder logs why the write was dropped.
                return DispatchOutcome.ROW_CHANGED_UNDER_CLAIM;
            } catch (RuntimeException e) {
                // The row stays SENDING, and stale recovery will fail it for manual review unless the SMS
                // provider can confirm by lookup that it never received the message.
                LOGGER.warn("SMS transaction {} not sent: its dispatch-time consent could not be recorded;{}",
                        claimed.getId(), LogSafe.exceptionTrace(e));
                return DispatchOutcome.SNAPSHOT_WRITE_FAILED;
            }
        }
        processTransaction(recorded);
        return DispatchOutcome.SENT;
    }

    private enum DispatchOutcome { SENT, ROW_CHANGED_UNDER_CLAIM, SNAPSHOT_WRITE_FAILED }

    private void deferAfterConsentCheckFailure(SmsTransaction claimed) {
        try {
            if (!retryPolicy.canRetry(claimed)) {
                transactionRecorder.markProviderResult(claimed, SmsProviderSendResultDto.failed(
                        QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_CODE,
                        QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_MESSAGE));
                return;
            }
            transactionRecorder.markRetryScheduled(
                    claimed,
                    SmsProviderSendResultDto.failed(
                            QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_CODE,
                            QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_MESSAGE),
                    retryPolicy.nextAttemptAt(claimed, new Date()));
        } catch (RuntimeException e) {
            // Whatever broke the consent lookup has likely broken this write too. The row stays SENDING, and
            // stale recovery will fail it for manual review unless the SMS provider can confirm by lookup
            // that it never received the message. Rethrowing would abort the other SMS providers' queues.
            LOGGER.warn("SMS transaction {} could not be rescheduled after a failed consent recheck; nothing "
                            + "was sent; left for stale recovery;{}",
                    claimed.getId(), LogSafe.exceptionTrace(e));
        }
    }

    /**
     * @return the dispatch-time consent decision, or {@code null} when the consent check failed
     */
    private SmsConsentDecisionDto recheckConsent(SmsTransaction claimed) {
        try {
            return Objects.requireNonNull(
                    consentService.evaluate(claimed.toSendCommand()), "SMS consent decision is required");
        } catch (RuntimeException e) {
            // Types and frames only: consent lookups run against patient records, so messages may carry PHI.
            LOGGER.warn("SMS transaction {} consent recheck failed; nothing sent;{}",
                    claimed.getId(), LogSafe.exceptionTrace(e));
            return null;
        }
    }

    private void recoverStaleSending(SmsProviderType providerType, int limit) {
        Date recoveryAt = new Date();
        Date staleBefore = new Date(recoveryAt.getTime() - DEFAULT_STALE_SENDING_TIMEOUT.toMillis());
        List<SmsTransaction> transactions = transactionRecorder.claimStaleSendingForRecovery(
                providerType,
                staleBefore,
                recoveryAt,
                limit
        );
        transactions.forEach(this::recoverStaleTransaction);
    }

    private void recoverStaleTransaction(SmsTransaction transaction) {
        SmsProviderMessageStatusDto status = lookupProviderStatus(transaction);
        if (status.isFound()) {
            transactionRecorder.markProviderResult(transaction, status.providerResult());
            return;
        }

        if (status.isNotFound()) {
            handleStaleProviderNotFound(transaction);
            return;
        }

        transactionRecorder.markProviderResult(
                transaction,
                SmsProviderSendResultDto.failed(
                        status.errorCode() == null ? QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE : status.errorCode(),
                        status.errorMessage() == null
                                ? QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE
                                : status.errorMessage()
                )
        );
    }

    private SmsProviderMessageStatusDto lookupProviderStatus(SmsTransaction transaction) {
        try {
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            return Objects.requireNonNull(providerClient.lookupMessageStatus(
                    clientReferenceId(transaction), transaction.getProviderMessageId()),
                    "SMS provider lookup result is required");
        } catch (RuntimeException e) {
            return SmsProviderMessageStatusDto.unavailable(
                    QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_CODE,
                    QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE
            );
        }
    }

    private void handleStaleProviderNotFound(SmsTransaction transaction) {
        if (!retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(
                    transaction,
                    SmsProviderSendResultDto.failed(
                            QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_CODE,
                            QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_MESSAGE
                    )
            );
            return;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(
                transaction,
                SmsProviderSendResultDto.failed(
                        QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_CODE,
                        QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_MESSAGE
                ),
                nextAttemptAt
        );
    }

    private void processTransaction(SmsTransaction transaction) {
        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            providerResult = Objects.requireNonNull(
                    providerClient.send(transaction.toSendCommand(), clientReferenceId(transaction)),
                    "SMS provider result is required");
        } catch (RuntimeException e) {
            providerResult = SmsProviderSendResultDto.uncertain(QUEUE_PROVIDER_EXCEPTION_CODE);
        }

        if (providerResult.accepted() || providerResult.status() == SmsStatus.SENDING) {
            transactionRecorder.markProviderResult(transaction, providerResult);
            return;
        }

        if (!retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(transaction, retryExhaustedResult());
            return;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(transaction, retryScheduledResult(), nextAttemptAt);
    }

    private SmsProviderSendResultDto retryScheduledResult() {
        return SmsProviderSendResultDto.failed(
                QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE,
                QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED_MESSAGE
        );
    }

    private SmsProviderSendResultDto retryExhaustedResult() {
        return SmsProviderSendResultDto.failed(
                QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_CODE,
                QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED_MESSAGE
        );
    }

    private String clientReferenceId(SmsTransaction transaction) {
        return Objects.requireNonNull(
                transaction.providerClientReferenceId(),
                "sms_transaction client reference id is required before SMS provider send"
        );
    }
}
