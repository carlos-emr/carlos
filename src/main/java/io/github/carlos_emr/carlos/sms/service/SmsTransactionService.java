package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.Date;
import java.util.List;

public interface SmsTransactionService {
    /** Persists the consent decision and initial queue state atomically. */
    SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                         SmsConsentDecisionDto decision);

    SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision);

    /**
     * Rewrites the consent snapshot of a claimed row with the permitting decision its send relies on.
     * Callers must continue with the returned row: the write advances the row version.
     *
     * @throws RuntimeException when the snapshot was not written, including when the row changed or
     *                          disappeared under the claim; the caller must not send
     */
    SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision);

    SmsTransaction markSending(SmsTransaction transaction, Date attemptAt);

    /**
     * Renews a {@code SENDING} claim after the rate-limit permit and before the provider call. A permit wait
     * can outlast the stale-send timeout, and stale recovery may then have taken the row over and found it
     * unsent at the SMS provider. Callers must continue with the returned row: the write advances the row version.
     *
     * @throws RuntimeException when the claim was not renewed, including when the row changed, disappeared or
     *                          left {@code SENDING} under the claim; the caller must not send
     */
    SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt);

    SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult);

    SmsTransaction markRetryScheduled(SmsTransaction transaction, SmsProviderSendResultDto providerResult, Date nextAttemptAt);

    /**
     * Returns a claimed-but-unsent row to the queue, rolling back the claim's attempt increment.
     * Used when the SMS-provider rate limiter denies a token after the row was already claimed. It records
     * no reason: the row keeps whatever error it already had.
     */
    SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt);

    /**
     * Returns a claimed-but-unsent row to the queue as {@link #releaseClaim(SmsTransaction, Date)} does, with the
     * same version check, and also records why it waits: {@code reason}'s code and fixed message, never a
     * provider's wording. Used when the SMS provider asked CARLOS to send more slowly
     * ({@link SmsProviderErrorCode#RATE_LIMITED}), which uses none of the text's attempts. Every other hand-back
     * uses {@link #releaseClaim(SmsTransaction, Date)} and records no reason.
     */
    SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt, SmsProviderErrorCode reason);

    SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook);

    SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook);

    List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit);

    /**
     * Settles a retired provider's waiting texts, one row per transaction, without dispatching anything or
     * touching in-flight sends: a text that certainly never went out is queued again, as a new row, for the
     * provider the clinic selects now, and its retired row is closed as moved; any other is failed for staff to
     * review.
     *
     * @return how many retired rows were moved or failed, at most {@code limit}
     */
    default int moveOrFailRetiredOutboundQueue(SmsProviderType providerType, int limit) {
        return 0;
    }

    List<SmsTransaction> claimStaleSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    );
}
