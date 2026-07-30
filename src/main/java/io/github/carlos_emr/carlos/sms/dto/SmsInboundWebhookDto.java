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
        providerMetadata = SmsProviderMetadataSanitizer.sanitize(providerMetadata);
    }
}
