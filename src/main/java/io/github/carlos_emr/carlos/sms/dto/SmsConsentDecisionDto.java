package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;

public record SmsConsentDecisionDto(
        boolean allowed,
        SmsStatus blockedStatus,
        String reasonCode,
        String operatorMessage
) {
    public SmsConsentDecisionDto {
        if (allowed) {
            if (blockedStatus != null) {
                throw new IllegalArgumentException("allowed SMS consent decisions cannot include a blockedStatus");
            }
        } else if (!isBlockingStatus(blockedStatus)) {
            throw new IllegalArgumentException("blockedStatus must be CONSENT_BLOCKED or OPTOUT_BLOCKED");
        }
    }

    public static SmsConsentDecisionDto permit() {
        return new SmsConsentDecisionDto(true, null, null, null);
    }

    public static SmsConsentDecisionDto blocked(SmsStatus blockedStatus, String reasonCode, String operatorMessage) {
        return new SmsConsentDecisionDto(false, blockedStatus, reasonCode, operatorMessage);
    }

    private static boolean isBlockingStatus(SmsStatus status) {
        return status == SmsStatus.CONSENT_BLOCKED || status == SmsStatus.OPTOUT_BLOCKED;
    }
}
