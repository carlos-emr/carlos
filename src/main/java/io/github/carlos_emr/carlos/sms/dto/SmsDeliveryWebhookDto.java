package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.support.SmsProviderMetadataSanitizer;

import java.time.Instant;
import java.util.Map;

public record SmsDeliveryWebhookDto(
        SmsProviderType providerType,
        String providerMessageId,
        SmsStatus status,
        Instant eventAt,
        String errorCode,
        String errorMessage,
        String clientReferenceId,
        Map<String, String> providerMetadata
) {
    public SmsDeliveryWebhookDto(
            SmsProviderType providerType,
            String providerMessageId,
            SmsStatus status,
            Instant eventAt,
            String errorCode,
            String errorMessage,
            Map<String, String> providerMetadata
    ) {
        this(providerType, providerMessageId, status, eventAt, errorCode, errorMessage, null, providerMetadata);
    }

    public SmsDeliveryWebhookDto {
        clientReferenceId = blankToNull(clientReferenceId);
        providerMetadata = SmsProviderMetadataSanitizer.sanitize(providerMetadata);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
