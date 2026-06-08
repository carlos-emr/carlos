package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.support.SmsAuditRedactor;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class StubSmsProviderClient implements SmsProviderClient {
    @Override
    public SmsProviderType providerType() {
        return SmsProviderType.STUB;
    }

    @Override
    public SmsProviderSendResultDto send(SmsSendCommand command) {
        String normalizedPhoneNumber = SmsPhoneNumbers.normalizeToE164(command.recipientPhoneNumber())
                .orElse(command.recipientPhoneNumber());
        String stableKey = normalizedPhoneNumber + "|" + command.body();
        return SmsProviderSendResultDto.accepted("stub-" + SmsAuditRedactor.digest(stableKey, 12), SmsStatus.SENT);
    }

    @Override
    public boolean validateCallback(String payload, Map<String, String> headers, String secret) {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        return payload != null
                && headers != null
                && secret.equals(headers.get("X-Carlos-Sms-Stub-Secret"));
    }

    @Override
    public Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers) {
        if (payload == null || headers == null) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
        if (payload == null || headers == null) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
