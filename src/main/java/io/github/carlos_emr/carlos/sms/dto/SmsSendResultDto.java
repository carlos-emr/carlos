package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.List;

public record SmsSendResultDto(
        boolean accepted,
        SmsStatus status,
        String providerMessageId,
        List<String> messages
) {
    public SmsSendResultDto {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static SmsSendResultDto validationFailed(List<String> messages) {
        return new SmsSendResultDto(false, SmsStatus.FAILED, null, messages);
    }

    public static SmsSendResultDto consentBlocked(SmsConsentDecisionDto decision) {
        return new SmsSendResultDto(
                false,
                decision.blockedStatus(),
                null,
                decision.operatorMessage() == null ? List.of() : List.of(decision.operatorMessage())
        );
    }

    public static SmsSendResultDto queued() {
        return new SmsSendResultDto(true, SmsStatus.QUEUED, null, List.of());
    }

    /** Uses the persisted result, including a delivery webhook that won a concurrent update. */
    public static SmsSendResultDto fromTransaction(SmsTransaction transaction) {
        SmsStatus status = transaction.getStatus();
        return new SmsSendResultDto(status == SmsStatus.SENT || status == SmsStatus.DELIVERED,
                status, transaction.getProviderMessageId(), transaction.getErrorMessage() == null
                ? List.of() : List.of(transaction.getErrorMessage()));
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
