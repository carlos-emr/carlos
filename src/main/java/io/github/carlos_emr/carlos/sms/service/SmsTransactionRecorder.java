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

    SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook);

    SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook);

    List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit);
}
