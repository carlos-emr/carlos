package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;

public record SmsProviderSendResultDto(
        boolean accepted,
        String providerMessageId,
        SmsStatus status,
        String errorCode,
        String errorMessage
) {
    public SmsProviderSendResultDto {
        if (accepted) {
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new IllegalArgumentException("providerMessageId is required for accepted SMS provider results");
            }
            if (status != SmsStatus.SENT && status != SmsStatus.DELIVERED) {
                throw new IllegalArgumentException("accepted SMS provider results must be SENT or DELIVERED");
            }
        } else if (status == null) {
            status = SmsStatus.FAILED;
        }
    }

    public static SmsProviderSendResultDto accepted(String providerMessageId, SmsStatus status) {
        return new SmsProviderSendResultDto(true, providerMessageId, status, null, null);
    }

    public static SmsProviderSendResultDto failed(String errorCode, String errorMessage) {
        return new SmsProviderSendResultDto(false, null, SmsStatus.FAILED, errorCode, errorMessage);
    }
}
