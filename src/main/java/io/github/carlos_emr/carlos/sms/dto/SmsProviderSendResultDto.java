package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;

public record SmsProviderSendResultDto(
        boolean accepted,
        String providerMessageId,
        SmsStatus status,
        String errorCode,
        String errorMessage
) {
    public static SmsProviderSendResultDto accepted(String providerMessageId, SmsStatus status) {
        return new SmsProviderSendResultDto(true, providerMessageId, status, null, null);
    }

    public static SmsProviderSendResultDto failed(String errorCode, String errorMessage) {
        return new SmsProviderSendResultDto(false, null, SmsStatus.FAILED, errorCode, errorMessage);
    }
}
