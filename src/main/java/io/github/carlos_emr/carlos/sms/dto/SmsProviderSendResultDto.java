package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsStatus;

import java.util.Objects;
import java.util.regex.Pattern;

public record SmsProviderSendResultDto(
        boolean accepted,
        String providerMessageId,
        SmsStatus status,
        String errorCode,
        String errorMessage
) {
    /** The code stored for an unclear outcome whose own code does not look like a code. */
    public static final String OUTCOME_UNKNOWN_CODE = "PROVIDER_OUTCOME_UNKNOWN";
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** What staff are told when a send may or may not have reached the SMS provider. */
    public static final String OUTCOME_UNKNOWN_MESSAGE =
            "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.";

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
        return new SmsProviderSendResultDto(false, null, SmsStatus.SENDING, errorCode, OUTCOME_UNKNOWN_MESSAGE);
    }

    public static SmsProviderSendResultDto failed(String errorCode, String errorMessage) {
        return new SmsProviderSendResultDto(false, null, SmsStatus.FAILED, errorCode, errorMessage);
    }

    /** A definite failure an SMS provider client reports, recorded with the code's fixed message. */
    public static SmsProviderSendResultDto failed(SmsProviderErrorCode code) {
        Objects.requireNonNull(code, "SMS provider error code is required");
        return failed(code.name(), code.message());
    }

    /**
     * This result as CARLOS records an SMS provider client's answer, carrying no wording of the provider's: a
     * definite failure carries a CARLOS code and its fixed message (any other code becomes
     * {@link SmsProviderErrorCode#REJECTED_OTHER}), an uncertain one keeps its code (when it looks like one, else
     * {@link #OUTCOME_UNKNOWN_CODE}) with the fixed "outcome unknown" message, and an accepted one carries no
     * error. The provider's message id is kept.
     *
     * @return the result to record
     */
    public SmsProviderSendResultDto withCarlosErrorCode() {
        if (accepted) {
            return errorCode == null && errorMessage == null ? this
                    : new SmsProviderSendResultDto(true, providerMessageId, status, null, null);
        }
        if (status == SmsStatus.SENDING) {
            // A client's own code is kept only when it looks like one; anything else may be the provider's text.
            String code = errorCode != null && CODE.matcher(errorCode).matches() ? errorCode : OUTCOME_UNKNOWN_CODE;
            return code.equals(errorCode) && OUTCOME_UNKNOWN_MESSAGE.equals(errorMessage) ? this
                    : new SmsProviderSendResultDto(false, providerMessageId, status, code, OUTCOME_UNKNOWN_MESSAGE);
        }
        SmsProviderErrorCode code =
                SmsProviderErrorCode.fromCode(errorCode).orElse(SmsProviderErrorCode.REJECTED_OTHER);
        return new SmsProviderSendResultDto(false, providerMessageId, SmsStatus.FAILED, code.name(), code.message());
    }
    @Override
    public String toString() {
        return "SmsProviderSendResultDto[redacted]";
    }
}
