package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record SmsDeliveryWebhookDto(
        SmsProviderType providerType,
        String providerMessageId,
        SmsStatus status,
        Instant eventAt,
        String errorCode,
        String errorMessage,
        Map<String, String> providerMetadata
) {
    public SmsDeliveryWebhookDto {
        providerMetadata = providerMetadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(providerMetadata));
    }
}
