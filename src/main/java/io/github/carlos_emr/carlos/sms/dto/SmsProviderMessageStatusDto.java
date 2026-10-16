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

    /** The code a provider that cannot look texts up answers with, through the interface's default. */
    public static final String LOOKUP_UNSUPPORTED_CODE = "PROVIDER_STATUS_LOOKUP_UNSUPPORTED";
    /** The fixed message stored with {@link #LOOKUP_UNSUPPORTED_CODE}. */
    public static final String LOOKUP_UNSUPPORTED_MESSAGE = "SMS provider message status lookup is not implemented.";

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
    @Override
    public String toString() {
        return "SmsProviderMessageStatusDto[redacted]";
    }
}
