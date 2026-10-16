package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.Optional;
import java.util.function.Supplier;

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
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_CODE =
            "QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_CODE =
            "QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_MESSAGE =
            "SMS consent could not be checked before sending; retry scheduled.";
    private static final String QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED_MESSAGE =
            "SMS consent could not be checked before sending and retry limit was reached; nothing was sent.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE =
            "SMS stale send status lookup threw an exception; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE =
            "SMS stale send status lookup was unavailable; marked failed for manual review.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_MESSAGE =
            "SMS stale send was not found by SMS provider status lookup; retry scheduled.";
    private static final String QUEUE_STALE_STATUS_NOT_FOUND_RETRY_EXHAUSTED_MESSAGE =
            "SMS stale send was not found by SMS provider status lookup and retry limit was reached.";
    private static final String QUEUE_PROVIDER_NOT_ACTIVE_CODE = "QUEUE_PROVIDER_NOT_ACTIVE";
    private static final String QUEUE_PROVIDER_NOT_ACTIVE_MESSAGE =
            "SMS not sent: its SMS provider is no longer the one chosen in Administration > SMS; send it again.";
    private static final String QUEUE_SYSTEM_TEST_PROVIDER_CHANGED_CODE = "QUEUE_SYSTEM_TEST_PROVIDER_CHANGED";
    private static final String QUEUE_SYSTEM_TEST_PROVIDER_CHANGED_MESSAGE =
            "SMS system test not sent: its provider is not active; use Send test in Administration > SMS again.";
    /**
     * How long a former provider's row waits after it could not be marked failed, so a row whose write keeps
     * failing doesn't head the queue and hold up the others on every run.
     */
    static final Duration INACTIVE_PROVIDER_RETRY_DELAY = Duration.ofMinutes(5);

    private final SmsTransactionService transactionRecorder;
    private final SmsProviderClientResolver providerResolver;
    private final SmsRetryCalculator retryPolicy;
    private final SmsSendRateLimitService rateLimiter;
    private final SmsConsentService consentService;
    /** The clinic's active provider, checked before claims and again just before dispatch. */
    private final Supplier<SmsProviderType> activeProvider;
    private final Function<SmsProviderType, SmsProviderSettings> providerSettings;
    private final BooleanSupplier sendingEnabled;
    private final Function<SmsTransaction, Optional<SmsProviderSettings>> lookupSettings;
    private final Function<SmsTransaction, Optional<SmsProviderSettings>> dispatchSettings;
    private final boolean durableRetirement;

    /**
     * Only the clinic's active provider (Administration &gt; SMS, or {@code sms.provider.default} while nothing
     * is saved) is sent through, with its saved settings.
     */
    @Autowired
    public SmsQueueProcessingService(
            SmsTransactionService transactionRecorder,
            SmsProviderClientResolver providerResolver,
            SmsRetryCalculator retryPolicy,
            SmsSendRateLimitService rateLimiter,
            SmsConsentService consentService,
            SmsDefaultProviderResolver providerSelector,
            SmsConfigService configService
    ) {
        this(transactionRecorder, providerResolver, retryPolicy, rateLimiter, consentService,
                providerSelector::configuredDefault, configService::readyProviderSettings,
                configService::sendingEnabled, configService::statusLookupSettings,
                configService::dispatchSettings, configService.retirementTrackingEnabled());
    }

    /** For tests: the stub is the active provider, with no saved settings. */
    SmsQueueProcessingService(
            SmsTransactionService transactionRecorder,
            SmsProviderClientResolver providerResolver,
            SmsRetryCalculator retryPolicy,
            SmsSendRateLimitService rateLimiter,
            SmsConsentService consentService
    ) {
        this(transactionRecorder, providerResolver, retryPolicy, rateLimiter, consentService,
                () -> SmsProviderType.STUB, SmsProviderSettings::none);
    }

    SmsQueueProcessingService(
            SmsTransactionService transactionRecorder,
            SmsProviderClientResolver providerResolver,
            SmsRetryCalculator retryPolicy,
            SmsSendRateLimitService rateLimiter,
            SmsConsentService consentService,
            Supplier<SmsProviderType> activeProvider,
            Function<SmsProviderType, SmsProviderSettings> providerSettings
    ) {
        this(transactionRecorder, providerResolver, retryPolicy, rateLimiter, consentService, activeProvider,
                providerSettings, () -> true,
                row -> Optional.of(activeProvider.get() == row.getProviderType()
                        ? providerSettings.apply(row.getProviderType()) : SmsProviderSettings.none(row.getProviderType())),
                row -> Optional.of(providerSettings.apply(row.getProviderType())), false);
    }

    private SmsQueueProcessingService(SmsTransactionService transactionRecorder,
            SmsProviderClientResolver providerResolver, SmsRetryCalculator retryPolicy,
            SmsSendRateLimitService rateLimiter, SmsConsentService consentService,
            Supplier<SmsProviderType> activeProvider, Function<SmsProviderType, SmsProviderSettings> providerSettings,
            BooleanSupplier sendingEnabled, Function<SmsTransaction, Optional<SmsProviderSettings>> lookupSettings,
            Function<SmsTransaction, Optional<SmsProviderSettings>> dispatchSettings, boolean durableRetirement) {
        this.transactionRecorder = transactionRecorder;
        this.providerResolver = providerResolver;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.consentService = consentService;
        this.activeProvider = Objects.requireNonNull(activeProvider, "active SMS provider is required");
        this.providerSettings = Objects.requireNonNull(providerSettings, "SMS provider settings are required");
        this.sendingEnabled = Objects.requireNonNull(sendingEnabled, "SMS sending switch is required");
        this.lookupSettings = Objects.requireNonNull(lookupSettings, "SMS lookup settings are required");
        this.dispatchSettings = Objects.requireNonNull(dispatchSettings, "SMS dispatch settings are required");
        this.durableRetirement = durableRetirement;
    }

    public int processDueMessages() {
        return processDueMessages(DEFAULT_BATCH_SIZE);
    }

    /**
     * Recovers stale sends, sends the active provider's due rows, then fails the due rows of any provider the
     * clinic has left.
     *
     * @param limit the most rows to send in this run; failing a former provider's rows has a limit of its own,
     *              so a backlog left by a swap never holds up the active provider's texts
     * @return how many of the active provider's rows were sent or consent-blocked
     */
    public int processDueMessages(int limit) {
        if (!sendingEnabled.getAsBoolean()) {
            return 0;
        }
        int safeLimit = Math.max(1, limit);
        SmsProviderType active = currentActiveProvider();
        if (active == null) {
            // An invalid sms.provider.default, or the database could not be read. Rows wait: failing them would
            // punish a typo.
            LOGGER.error("SMS queue run skipped: the active SMS provider could not be determined; nothing was sent.");
            return 0;
        }
        SmsProviderSettings activeSettings = loadSettings(active);
        // Stale sends of a former provider are still looked up, with no settings, so its answer can still settle
        // them. The active provider's stale sends wait, like its queue, while its settings cannot be read.
        for (SmsProviderType providerType : SmsProviderType.values()) {
            if (providerType != active) {
                recoverStaleSending(providerType, safeLimit);
            } else if (activeSettings != null) {
                recoverStaleSending(providerType, safeLimit);
            }
        }
        int processed = activeSettings == null ? 0 : drainProvider(active, safeLimit, activeSettings);
        // Materialize retirement failures in bounded batches, including a provider reselected before
        // cleanup ran. The durable boundary already prevents these queued rows from being dispatched.
        for (SmsProviderType providerType : SmsProviderType.values()) {
            if (durableRetirement) {
                transactionRecorder.failRetiredOutboundQueue(providerType, safeLimit);
            } else if (providerType != active) {
                failInactiveProvider(providerType, safeLimit);
            }
        }
        return processed;
    }

    /** @return the active provider, or {@code null} (logged) when it cannot be determined */
    private SmsProviderType currentActiveProvider() {
        try {
            return activeProvider.get();
        } catch (RuntimeException e) {
            LOGGER.warn("SMS active provider could not be determined. exceptionClass={}", e.getClass().getName());
            return null;
        }
    }

    /**
     * @return the provider's settings, or {@code null} (logged) when it is not ready or they cannot be read; its
     *         rows then wait, unclaimed, for a later run
     */
    private SmsProviderSettings loadSettings(SmsProviderType providerType) {
        try {
            return Objects.requireNonNull(providerSettings.apply(providerType), "SMS provider settings are required");
        } catch (SmsProviderNotReadyException e) {
            LOGGER.error("SMS queue run skipped provider {}: it is not ready ({}); fix it in Administration > SMS. "
                    + "Nothing was sent.", providerType, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            LOGGER.error("SMS queue run skipped provider {}: its saved settings could not be read; nothing was sent. "
                    + "exceptionClass={}", providerType, e.getClass().getName());
            return null;
        }
    }

    /**
     * Fails the due rows of a provider that is no longer the clinic's active one, without sending them: their
     * provider would need the logins of an account the clinic has left. Staff see them as failed and resend.
     * Each run fails at most {@code limit} of them, apart from the active provider's send batch.
     */
    private void failInactiveProvider(SmsProviderType providerType, int limit) {
        int writeFailures = 0;
        for (int handled = 0; handled < limit; handled++) {
            // Read again before each row: the clinic may have chosen this provider since the run started, and
            // its new texts must then be sent, not failed.
            SmsProviderType nowActive = currentActiveProvider();
            if (nowActive == null || nowActive == providerType) {
                return;
            }
            List<SmsTransaction> transactions = transactionRecorder.claimDueOutboundQueue(providerType, new Date(), 1);
            if (transactions.isEmpty()) {
                return;
            }
            SmsTransaction claimed = transactions.get(0);
            try {
                // A system test held back by the rate limit is not a patient's text: say so, rather than
                // asking staff to resend it.
                SmsProviderSendResultDto result = claimed.getMessagePurpose() == SmsMessagePurpose.SYSTEM_TEST
                        ? SmsProviderSendResultDto.failed(
                                QUEUE_SYSTEM_TEST_PROVIDER_CHANGED_CODE, QUEUE_SYSTEM_TEST_PROVIDER_CHANGED_MESSAGE)
                        : SmsProviderSendResultDto.failed(
                                QUEUE_PROVIDER_NOT_ACTIVE_CODE, QUEUE_PROVIDER_NOT_ACTIVE_MESSAGE);
                transactionRecorder.markProviderResult(claimed, result);
            } catch (RuntimeException e) {
                // Nothing was sent, so the claim goes back rather than leaving the row for stale recovery, which
                // would report an unknown outcome. It comes due again a little later, so the next row gets its
                // turn. Releasing gives the attempt back, so a row that can never be failed is retried every few
                // minutes, with a warning each time, rather than an unsent text being dropped.
                try {
                    SmsTransaction released = transactionRecorder.releaseClaim(claimed,
                            Date.from(Instant.now().plus(INACTIVE_PROVIDER_RETRY_DELAY)));
                    LOGGER.warn("SMS transaction {} of inactive provider {} could not be failed; nothing was sent; the"
                                    + " claim {};{}", claimed.getId(), providerType,
                            released != null && released.getStatus() == SmsStatus.QUEUED
                                    ? "was handed back to the queue"
                                    : "was not handed back, as a newer write stands",
                            LogSafe.exceptionTrace(e));
                } catch (RuntimeException releaseFailure) {
                    if (releaseFailure != e) {
                        releaseFailure.addSuppressed(e);
                    }
                    LOGGER.warn("SMS transaction {} of inactive provider {} could not be failed or handed back; nothing"
                            + " was sent; stale recovery reconciles it;{}", claimed.getId(), providerType,
                            LogSafe.exceptionTrace(releaseFailure));
                }
                // Back off the faulty row and let a healthy row proceed. Two failed writes suggest an outage.
                if (++writeFailures >= 2) {
                    return;
                }
            }
        }
    }

    private int drainProvider(SmsProviderType providerType, int remaining, SmsProviderSettings settings) {
        int processed = 0;
        boolean shouldContinue = true;
        while (processed < remaining && shouldContinue) {
            if (!sendingEnabled.getAsBoolean() || currentActiveProvider() != providerType) {
                // The clinic chose another provider during the run: stop sending through this one.
                return processed;
            }
            // Claim before acquiring the rate-limit token so an empty queue never burns budget. If the
            // limiter then denies, release the claim back to QUEUED so the row is retried without losing
            // the rolled-back attempt, and end this run's sending.
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
                    DispatchOutcome outcome = sendOnRecordedConsent(claimed, decision, settings);
                    if (outcome == DispatchOutcome.SENT || outcome == DispatchOutcome.SENT_FAILED_FOR_EVERY_TEXT
                            || outcome == DispatchOutcome.SENT_OUTCOME_UNKNOWN) {
                        processed++;
                    }
                    // A write failure is unlikely to be about one row, an unusable permit is handled like a failed
                    // consent check, a failure that affects every text (credentials, account, the provider itself)
                    // would fail the next one too, and an unclear answer (a timeout) likely means the provider is in
                    // trouble. A settings change also stops dispatch before sending the next row.
                    shouldContinue = outcome == DispatchOutcome.SENT
                            || outcome == DispatchOutcome.ROW_CHANGED_UNDER_CLAIM;
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
     * @return {@link DispatchOutcome#SENT}, {@link DispatchOutcome#SENT_FAILED_FOR_EVERY_TEXT} or
     *         {@link DispatchOutcome#SENT_OUTCOME_UNKNOWN} once the send was attempted; otherwise nothing was sent
     */
    private DispatchOutcome sendOnRecordedConsent(SmsTransaction claimed, SmsConsentDecisionDto decision,
                                                  SmsProviderSettings settings) {
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
        // Recheck after the permit wait and all claim writes. A swap or re-save must not let the rest of
        // this run keep using the settings it read at startup. No lock is held across the provider call.
        SmsProviderType providerType = recorded.getProviderType();
        boolean settingsCurrent;
        try {
            settingsCurrent = sendingEnabled.getAsBoolean() && currentActiveProvider() == providerType
                    && settings.equals(durableRetirement ? dispatchSettings.apply(recorded).orElse(null)
                    : loadSettings(providerType));
        } catch (RuntimeException e) {
            settingsCurrent = false;
        }
        if (!settingsCurrent) {
            try {
                transactionRecorder.releaseClaim(recorded, new Date());
                LOGGER.info("SMS dispatch stopped: provider or settings changed or could not be read; claim release"
                        + " requested; nothing was sent.");
            } catch (RuntimeException e) {
                LOGGER.warn("SMS dispatch stopped before sending, but its claim could not be handed back;{}",
                        LogSafe.exceptionTrace(e));
            }
            return DispatchOutcome.SETTINGS_CHANGED;
        }
        SmsProviderSendResultDto result = processTransaction(recorded, settings);
        if (!result.accepted() && result.status() == SmsStatus.SENDING) {
            return DispatchOutcome.SENT_OUTCOME_UNKNOWN;
        }
        boolean failedForEveryText = result.status() == SmsStatus.FAILED && SmsProviderErrorCode
                .fromCode(result.errorCode()).map(SmsProviderErrorCode::affectsEveryText).orElse(false);
        return failedForEveryText ? DispatchOutcome.SENT_FAILED_FOR_EVERY_TEXT : DispatchOutcome.SENT;
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
        /**
         * The send was attempted and failed in a way that affects every text (see
         * {@link SmsProviderErrorCode#affectsEveryText()}); the row counts as processed, and draining stops for
         * this run.
         */
        SENT_FAILED_FOR_EVERY_TEXT,
        /**
         * The send was attempted and its outcome is unknown (a timeout or unclear answer); the row counts as
         * processed and waits for stale recovery, and draining stops for this run.
         */
        SENT_OUTCOME_UNKNOWN,
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
        CONSENT_UNUSABLE,
        /** Provider/settings changed or became unreadable: claim release requested, and draining stops. */
        SETTINGS_CHANGED
    }

    /**
     * Reschedules a claimed row whose consent recheck threw, using the normal retry backoff, or fails it for
     * manual review at the retry limit. Never throws: a row that cannot be rescheduled stays {@code SENDING}
     * for stale recovery, and the run goes on to its remaining work.
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
            // that it never received the message. Rethrowing would abort the rest of the run.
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
            transactionRecorder.markProviderResult(transaction, status.providerResult().withCarlosErrorCode());
            return;
        }

        if (status.isNotFound()) {
            handleStaleProviderNotFound(transaction);
            return;
        }

        // Only CARLOS's own codes and fixed messages are stored; a provider's answer may quote a phone number.
        String code = status.errorCode();
        String message;
        if (QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_CODE.equals(code)) {
            message = QUEUE_STALE_STATUS_LOOKUP_EXCEPTION_MESSAGE;
        } else if (SmsProviderMessageStatusDto.LOOKUP_UNSUPPORTED_CODE.equals(code)) {
            message = SmsProviderMessageStatusDto.LOOKUP_UNSUPPORTED_MESSAGE;
        } else {
            code = QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE;
            message = QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE;
        }
        transactionRecorder.markProviderResult(transaction, SmsProviderSendResultDto.failed(code, message));
    }

    private SmsProviderMessageStatusDto lookupProviderStatus(SmsTransaction transaction) {
        try {
            Optional<SmsProviderSettings> currentSettings = lookupSettings.apply(transaction);
            if (currentSettings.isEmpty()) {
                return SmsProviderMessageStatusDto.unavailable(QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_CODE,
                        QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE_MESSAGE);
            }
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            return Objects.requireNonNull(providerClient.lookupMessageStatus(
                    clientReferenceId(transaction), transaction.getProviderMessageId(), currentSettings.get()),
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

    /** @return the provider's answer as recorded */
    private SmsProviderSendResultDto processTransaction(SmsTransaction transaction, SmsProviderSettings settings) {
        SmsProviderSendResultDto providerResult;
        try {
            SmsProviderClient providerClient = providerResolver.resolve(transaction.getProviderType());
            providerResult = Objects.requireNonNull(
                    providerClient.send(transaction.toSendCommand(), clientReferenceId(transaction), settings),
                    "SMS provider result is required");
        } catch (RuntimeException e) {
            // Types and frames only: provider code handles the patient's number and the message text.
            LOGGER.warn("SMS transaction {} send through provider {} failed with an error; its outcome is unknown and"
                    + " this run's sending stops;{}", transaction.getId(), transaction.getProviderType(),
                    LogSafe.exceptionTrace(e));
            providerResult = SmsProviderSendResultDto.uncertain(QUEUE_PROVIDER_EXCEPTION_CODE);
        }
        providerResult = providerResult.withCarlosErrorCode();

        if (providerResult.accepted() || providerResult.status() == SmsStatus.SENDING) {
            transactionRecorder.markProviderResult(transaction, providerResult);
            return providerResult;
        }

        // The row keeps the provider's reason, as a CARLOS code; whether it will be retried shows in its status
        // (QUEUED with a due time, or FAILED) and its attempt count. A permanent failure is never retried.
        boolean permanent = SmsProviderErrorCode.fromCode(providerResult.errorCode())
                .map(SmsProviderErrorCode::permanent).orElse(false);
        if (permanent || !retryPolicy.canRetry(transaction)) {
            transactionRecorder.markProviderResult(transaction, providerResult);
            return providerResult;
        }

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, new Date());
        transactionRecorder.markRetryScheduled(transaction, providerResult, nextAttemptAt);
        return providerResult;
    }

    private String clientReferenceId(SmsTransaction transaction) {
        return Objects.requireNonNull(
                transaction.providerClientReferenceId(),
                "sms_transaction client reference id is required before SMS provider send"
        );
    }
}
