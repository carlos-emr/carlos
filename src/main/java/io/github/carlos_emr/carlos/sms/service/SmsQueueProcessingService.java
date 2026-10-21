package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.persistence.OptimisticLockException;
import org.apache.logging.log4j.Logger;
import org.hibernate.StaleStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
                } else if (!acquirePermit(claimed, providerType)) {
                    shouldContinue = false;
                } else {
                    DispatchOutcome outcome = sendOnRecordedConsent(claimed, decision);
                    if (outcome == DispatchOutcome.SENT) {
                        processed++;
                    }
                    // A write failure is unlikely to be about one row, and an unusable permit is handled like a
                    // failed consent check: stop rather than work through the rest of the queue one row at a time.
                    shouldContinue = outcome != DispatchOutcome.WRITE_FAILED
                            && outcome != DispatchOutcome.CONSENT_UNUSABLE;
                }
            }
        }
        return processed;
    }

    /**
     * Takes a rate-limit permit for a claimed row. Without one nothing is sent, so the claim is handed back and
     * the row stays QUEUED and due, as on the direct-send path. A limiter or hand-back failure ends this
     * provider's drain only, never the whole run: if the hand-back fails, the row stays SENDING and stale
     * recovery reconciles it.
     *
     * @return true when a permit was taken
     */
    private boolean acquirePermit(SmsTransaction claimed, SmsProviderType providerType) {
        RuntimeException limiterFailure = null;
        try {
            if (rateLimiter.tryAcquire(providerType)) {
                return true;
            }
        } catch (RuntimeException e) {
            limiterFailure = e;
        }
        try {
            transactionRecorder.releaseClaim(claimed, new Date());
        } catch (RuntimeException releaseFailure) {
            if (limiterFailure != null && limiterFailure != releaseFailure) {
                releaseFailure.addSuppressed(limiterFailure);
            }
            LOGGER.warn("SMS transaction {} not sent: no rate-limit permit, and its claim could not be handed back;{}",
                    claimed.getId(), LogSafe.exceptionTrace(releaseFailure));
            return false;
        }
        if (limiterFailure != null) {
            LOGGER.warn("SMS transaction {} not sent: the rate limiter failed, so its claim release was requested;{}",
                    claimed.getId(), LogSafe.exceptionTrace(limiterFailure));
        }
        return false;
    }

    /**
     * Sends a claimed row once its claim is renewed and its audit snapshot names the consent record this
     * dispatch relied on. The admission snapshot is usually still current and costs no write.
     *
     * @return {@link DispatchOutcome#SENT} once the send was attempted; otherwise nothing was sent
     */
    private DispatchOutcome sendOnRecordedConsent(SmsTransaction claimed, SmsConsentDecisionDto decision) {
        if (decision.consentStatus() == null) {
            // A permit naming no consent state can never be recorded on the row. Handing the claim back would
            // retry it at once on every run, so it is backed off like a failed consent check instead: the
            // attempt counts, and the row fails for review at the retry limit.
            deferAfterConsentCheckFailure(claimed);
            return DispatchOutcome.CONSENT_UNUSABLE;
        }
        SmsTransaction recorded = claimed;
        try {
            // The permit wait can outlast the stale-send timeout, and another worker run's stale recovery may
            // then have taken the row over and found it unsent at the SMS provider.
            recorded = transactionRecorder.renewClaim(claimed, new Date());
            if (!recorded.hasConsentSnapshot(decision)) {
                recorded = transactionRecorder.recordConsentDecision(recorded, decision);
            }
        } catch (SmsTransactionClaimConflictException e) {
            // The row changed or vanished under the claim, so whoever changed it decides what happens
            // next. The recorder logs why the write was dropped.
            return DispatchOutcome.ROW_CHANGED_UNDER_CLAIM;
        } catch (RuntimeException e) {
            if (isConcurrentChange(e)) {
                // A version check at flush lost to another writer: the row changed under the claim, as above.
                LOGGER.info("SMS transaction {} not sent: another update changed it under the claim", claimed.getId());
                return DispatchOutcome.ROW_CHANGED_UNDER_CLAIM;
            }
            // Nothing has been sent, so hand the claim back (the row is retried when due). The latest row is
            // used so a renewal that did commit does not make the hand-back's own version check drop it.
            handBackAfterWriteFailure(recorded, e);
            return DispatchOutcome.WRITE_FAILED;
        }
        processTransaction(recorded);
        return DispatchOutcome.SENT;
    }

    /**
     * Hands an unsent row's claim back after its renewal or consent snapshot could not be written. Never
     * throws: if the hand-back fails too, the row stays SENDING and stale recovery reconciles it.
     */
    private void handBackAfterWriteFailure(SmsTransaction row, RuntimeException failure) {
        try {
            SmsTransaction released = transactionRecorder.releaseClaim(row, new Date());
            if (released != null && released.getStatus() == SmsStatus.QUEUED) {
                LOGGER.warn("SMS transaction {} not sent: its claim or dispatch-time consent could not be recorded,"
                        + " so the claim was handed back;{}", row.getId(), LogSafe.exceptionTrace(failure));
            } else {
                // The hand-back's version check lost to a newer write, which stands.
                LOGGER.warn("SMS transaction {} not sent: its claim or dispatch-time consent could not be recorded,"
                                + " and the claim was not handed back (the row is {}); stale recovery reconciles it;{}",
                        row.getId(), released == null ? "gone" : released.getStatus(), LogSafe.exceptionTrace(failure));
            }
        } catch (RuntimeException releaseFailure) {
            if (releaseFailure != failure) {
                releaseFailure.addSuppressed(failure);
            }
            LOGGER.warn("SMS transaction {} not sent: its claim or dispatch-time consent could not be recorded,"
                    + " and the claim could not be handed back;{}", row.getId(), LogSafe.exceptionTrace(releaseFailure));
        }
    }

    /** Whether {@code failure} is a lost version race (optimistic lock) rather than a failed write. */
    static boolean isConcurrentChange(Throwable failure) {
        // Bounded and cycle-safe: a cause chain can loop through initCause.
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.size() < 16 && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof OptimisticLockingFailureException
                    || cause instanceof StaleStateException) {
                return true;
            }
        }
        return false;
    }

    /** What became of a claimed row whose dispatch-time consent recheck permitted the send. */
    private enum DispatchOutcome {
        /** The send was attempted and its result recorded; the row counts as processed. */
        SENT,
        /** The row changed or vanished under the claim: nothing sent, and the next row may be tried. */
        ROW_CHANGED_UNDER_CLAIM,
        /**
         * The claim renewal or consent snapshot could not be written: nothing sent, the claim is handed back
         * when possible, and draining stops for this run.
         */
        WRITE_FAILED,
        /**
         * The permit named no consent state: nothing sent, the row is backed off like a failed consent check,
         * and draining stops for this run.
         */
        CONSENT_UNUSABLE
    }

    /**
     * Reschedules a claimed row whose consent recheck threw, using the normal retry backoff, or fails it for
     * manual review at the retry limit. Never throws: a row that cannot be rescheduled stays {@code SENDING}
     * for stale recovery so the other SMS providers' queues still drain.
     */
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
