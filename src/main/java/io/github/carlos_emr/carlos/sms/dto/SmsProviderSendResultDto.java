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
        if (providerMessageId != null && providerMessageId.length() > 128) {
            throw new IllegalArgumentException("SMS provider message identifier exceeds supported length");
        }
        if (accepted) {
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new IllegalArgumentException("providerMessageId is required for accepted SMS provider results");
            }
            if (status != SmsStatus.SENT && status != SmsStatus.DELIVERED) {
                throw new IllegalArgumentException("accepted SMS provider results must be SENT or DELIVERED");
            }
        } else {
            status = status == null ? SmsStatus.FAILED : status;
            if (status != SmsStatus.FAILED && status != SmsStatus.SENDING) {
                throw new IllegalArgumentException("unconfirmed SMS provider results must be FAILED or SENDING");
            }
        }
    }

    public static SmsProviderSendResultDto accepted(String providerMessageId, SmsStatus status) {
        return new SmsProviderSendResultDto(true, providerMessageId, status, null, null);
    }

    /** The provider may have accepted the send; look up its status before any retry. */
    public static SmsProviderSendResultDto uncertain(String errorCode) {
        return new SmsProviderSendResultDto(false, null, SmsStatus.SENDING, errorCode,
                "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
    }

    public static SmsProviderSendResultDto failed(String errorCode, String errorMessage) {
        return new SmsProviderSendResultDto(false, null, SmsStatus.FAILED, errorCode, errorMessage);
    }
    @Override
    public String toString() {
        return "SmsProviderSendResultDto[redacted]";
    }
}
