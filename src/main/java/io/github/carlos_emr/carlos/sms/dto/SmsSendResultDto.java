package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.List;

public record SmsSendResultDto(
        boolean accepted,
        SmsStatus status,
        String providerMessageId,
        List<String> messages,
        Long transactionId
) {
    public SmsSendResultDto {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** A result that names no stored text: nothing was recorded, or the caller does not need its id. */
    public SmsSendResultDto(boolean accepted, SmsStatus status, String providerMessageId, List<String> messages) {
        this(accepted, status, providerMessageId, messages, null);
    }

    public static SmsSendResultDto validationFailed(List<String> messages) {
        return new SmsSendResultDto(false, SmsStatus.FAILED, null, messages);
    }

    public static SmsSendResultDto consentBlocked(SmsConsentDecisionDto decision) {
        return consentBlocked(decision, null);
    }

    /** @param transactionId the blocked text's stored row, so a caller can show it (#884) */
    public static SmsSendResultDto consentBlocked(SmsConsentDecisionDto decision, Long transactionId) {
        return new SmsSendResultDto(
                false,
                decision.blockedStatus(),
                null,
                decision.operatorMessage() == null ? List.of() : List.of(decision.operatorMessage()),
                transactionId
        );
    }

    public static SmsSendResultDto queued() {
        return queued(null);
    }

    /** @param transactionId the queued text's row, so a caller can follow it (#884) */
    public static SmsSendResultDto queued(Long transactionId) {
        return new SmsSendResultDto(true, SmsStatus.QUEUED, null, List.of(), transactionId);
    }

    /** Uses the persisted result, including a delivery webhook that won a concurrent update. */
    public static SmsSendResultDto fromTransaction(SmsTransaction transaction) {
        SmsStatus status = transaction.getStatus();
        return new SmsSendResultDto(status == SmsStatus.SENT || status == SmsStatus.DELIVERED,
                status, transaction.getProviderMessageId(), transaction.getErrorMessage() == null
                ? List.of() : List.of(transaction.getErrorMessage()), transaction.getId());
    }

    public static SmsSendResultDto fromProvider(SmsProviderSendResultDto providerResult) {
        return new SmsSendResultDto(
                providerResult.accepted(),
                providerResult.status(),
                providerResult.providerMessageId(),
                providerResult.errorMessage() == null ? List.of() : List.of(providerResult.errorMessage())
        );
    }
    @Override
    public String toString() {
        return "SmsSendResultDto[redacted]";
    }
}
