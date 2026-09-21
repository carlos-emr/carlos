package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsStatus;

import java.time.Instant;

/**
 * Outcome of an SMS consent check, plus the consent record it relied on.
 * <p>
 * {@code consentStatus}, {@code consentId} and {@code consentLastUpdateDate} form the audit snapshot
 * persisted on the {@code sms_transaction} row. A permitting decision must name its {@code consentStatus};
 * only the snapshot-free {@link #blocked(SmsStatus, String, String)} factory leaves all three null.
 */
public record SmsConsentDecisionDto(
        boolean allowed,
        SmsStatus blockedStatus,
        String reasonCode,
        String operatorMessage,
        SmsConsentStatus consentStatus,
        Integer consentId,
        Instant consentLastUpdateDate
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

    public static SmsConsentDecisionDto permitted(
            SmsConsentStatus consentStatus,
            Integer consentId,
            Instant consentLastUpdateDate
    ) {
        return new SmsConsentDecisionDto(true, null, null, null, consentStatus, consentId, consentLastUpdateDate);
    }

    public static SmsConsentDecisionDto blocked(SmsStatus blockedStatus, String reasonCode, String operatorMessage) {
        return blocked(blockedStatus, reasonCode, operatorMessage, null, null, null);
    }

    public static SmsConsentDecisionDto blocked(
            SmsStatus blockedStatus,
            String reasonCode,
            String operatorMessage,
            SmsConsentStatus consentStatus,
            Integer consentId,
            Instant consentLastUpdateDate
    ) {
        return new SmsConsentDecisionDto(
                false, blockedStatus, reasonCode, operatorMessage, consentStatus, consentId, consentLastUpdateDate);
    }

    private static boolean isBlockingStatus(SmsStatus status) {
        return status == SmsStatus.CONSENT_BLOCKED || status == SmsStatus.OPTOUT_BLOCKED;
    }
    @Override
    public String toString() {
        return "SmsConsentDecisionDto[redacted]";
    }
}
