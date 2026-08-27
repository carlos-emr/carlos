package io.github.carlos_emr.carlos.email.core;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;

/**
 * Immutable result of resolving patient email consent for compose display and send-time enforcement.
 *
 * @since 2026-07-06
 */
public class EmailConsentResult {
    private final String consentName;
    private final EmailConsentStatus status;
    private final Integer consentId;
    private final Date consentLastUpdateDate;

    /**
     * Creates an immutable consent result, defensively copying the mutable update timestamp.
     *
     * @param consentName display name of the configured consent type
     * @param status resolved consent status
     * @param consentId identifier of the consent row, when present
     * @param consentLastUpdateDate last update time of the consent row, when present
     */
    public EmailConsentResult(String consentName, EmailConsentStatus status, Integer consentId, Date consentLastUpdateDate) {
        this.consentName = consentName;
        this.status = status;
        this.consentId = consentId;
        this.consentLastUpdateDate = copyDate(consentLastUpdateDate);
    }

    /** @return the configured consent type's display name */
    public String getConsentName() {
        return consentName;
    }

    /** @return the resolved patient email-consent state */
    public EmailConsentStatus getStatus() {
        return status;
    }

    /** @return the source consent-record identifier, or {@code null} */
    public Integer getConsentId() {
        return consentId;
    }

    /** @return a defensive copy of the source consent record's update time */
    public Date getConsentLastUpdateDate() {
        return copyDate(consentLastUpdateDate);
    }

    /** @return the stable consent-state code, falling back to {@code UNKNOWN} */
    public String getStatusCode() {
        return getStatusOrUnknown().name();
    }

    /** @return the resource-bundle key for the consent-state label */
    public String getMessageKey() {
        return getStatusOrUnknown().getMessageKey();
    }

    private EmailConsentStatus getStatusOrUnknown() {
        return status != null ? status : EmailConsentStatus.UNKNOWN;
    }

    private static Date copyDate(Date date) {
        return date != null ? new Date(date.getTime()) : null;
    }
}
