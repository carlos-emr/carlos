package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;

import java.util.Map;
import java.util.Optional;

public interface SmsProviderClient {
    SmsProviderType providerType();

    SmsProviderSendResultDto send(SmsSendCommand command);

    boolean validateCallback(String payload, Map<String, String> headers, String secret);

    Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers);

    Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers);
}
