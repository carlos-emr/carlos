package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
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
        if (status != SmsStatus.SENT && status != SmsStatus.DELIVERED && status != SmsStatus.FAILED) {
            throw new IllegalArgumentException("delivery status must be SENT, DELIVERED or FAILED");
        }
        if ((providerMessageId != null && providerMessageId.length() > 128)
                || (clientReferenceId != null && clientReferenceId.length() > 64)) {
            throw new IllegalArgumentException("SMS provider identifiers exceed supported lengths");
        }
        clientReferenceId = blankToNull(clientReferenceId);
        providerMetadata = SmsProviderMetadataSanitizer.sanitize(providerMetadata);
    }

    /**
     * This callback as CARLOS records it, carrying no wording of the provider's: a failure carries a CARLOS code and
     * its fixed message (any other code becomes {@link SmsProviderErrorCode#REJECTED_OTHER}), and a sent or
     * delivered report carries no error.
     *
     * @return the callback to record
     */
    public SmsDeliveryWebhookDto withCarlosErrorCode() {
        if (status != SmsStatus.FAILED) {
            return errorCode == null && errorMessage == null ? this
                    : new SmsDeliveryWebhookDto(providerType, providerMessageId, status, eventAt, null, null,
                            clientReferenceId, providerMetadata);
        }
        SmsProviderErrorCode code =
                SmsProviderErrorCode.fromCode(errorCode).orElse(SmsProviderErrorCode.REJECTED_OTHER);
        return new SmsDeliveryWebhookDto(providerType, providerMessageId, status, eventAt, code.name(), code.message(),
                clientReferenceId, providerMetadata);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
    @Override
    public String toString() {
        return "SmsDeliveryWebhookDto[redacted]";
    }
}
