package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsStatus;

public record SmsConsentDecisionDto(
        boolean allowed,
        SmsStatus blockedStatus,
        String reasonCode,
        String operatorMessage
) {
    public static SmsConsentDecisionDto permit() {
        return new SmsConsentDecisionDto(true, null, null, null);
    }

    public static SmsConsentDecisionDto blocked(SmsStatus blockedStatus, String reasonCode, String operatorMessage) {
        if (blockedStatus == null) {
            throw new IllegalArgumentException("blockedStatus is required");
        }
        return new SmsConsentDecisionDto(false, blockedStatus, reasonCode, operatorMessage);
    }
}
