package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.Date;
import java.util.List;

public interface SmsTransactionRecorder {
    SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType);

    SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision);

    SmsTransaction markSending(SmsTransaction transaction, Date attemptAt);

    SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult);

    SmsTransaction markRetryScheduled(SmsTransaction transaction, SmsProviderSendResultDto providerResult, Date nextAttemptAt);

    /**
     * Returns a claimed-but-unsent row to the queue, rolling back the claim's attempt increment.
     * Used when the SMS-provider rate limiter denies a token after the row was already claimed.
     */
    SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt);

    SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook);

    SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook);

    List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit);

    List<SmsTransaction> claimStaleSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    );
}
