package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.support.SmsProviderMetadataSanitizer;

import java.time.Instant;
import java.util.Map;

public record SmsInboundWebhookDto(
        SmsProviderType providerType,
        String providerMessageId,
        String fromPhoneNumber,
        String toPhoneNumber,
        String body,
        Instant receivedAt,
        Map<String, String> providerMetadata
) {
    public SmsInboundWebhookDto {
        if (providerMessageId != null && providerMessageId.length() > 128) {
            throw new IllegalArgumentException("SMS provider message identifier exceeds supported length");
        }
        providerMetadata = SmsProviderMetadataSanitizer.sanitize(providerMetadata);
    }
    @Override
    public String toString() {
        return "SmsInboundWebhookDto[redacted]";
    }
}
