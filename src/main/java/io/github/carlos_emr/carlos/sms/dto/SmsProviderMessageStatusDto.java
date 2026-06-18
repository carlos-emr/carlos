package io.github.carlos_emr.carlos.sms.dto;

import java.util.Objects;

public record SmsProviderMessageStatusDto(
        Status status,
        SmsProviderSendResultDto providerResult,
        String errorCode,
        String errorMessage
) {
    public SmsProviderMessageStatusDto {
        status = status == null ? Status.UNAVAILABLE : status;
        if (status == Status.FOUND) {
            Objects.requireNonNull(providerResult, "providerResult is required when status is FOUND");
        }
    }

    public static SmsProviderMessageStatusDto found(SmsProviderSendResultDto providerResult) {
        return new SmsProviderMessageStatusDto(Status.FOUND, providerResult, null, null);
    }

    public static SmsProviderMessageStatusDto notFound() {
        return new SmsProviderMessageStatusDto(Status.NOT_FOUND, null, null, null);
    }

    public static SmsProviderMessageStatusDto unavailable(String errorCode, String errorMessage) {
        return new SmsProviderMessageStatusDto(Status.UNAVAILABLE, null, errorCode, errorMessage);
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    public boolean isNotFound() {
        return status == Status.NOT_FOUND;
    }

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }
}
