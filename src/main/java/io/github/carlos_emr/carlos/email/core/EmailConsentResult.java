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

    public String getConsentName() {
        return consentName;
    }

    public EmailConsentStatus getStatus() {
        return status;
    }

    public Integer getConsentId() {
        return consentId;
    }

    public Date getConsentLastUpdateDate() {
        return copyDate(consentLastUpdateDate);
    }

    /**
     * Returns a user-facing consent status, falling back to the {@code UNKNOWN} display name when
     * this result has no status.
     *
     * @return the display status
     */
    public String getDisplayStatus() {
        return status != null ? status.getDisplayName() : EmailConsentStatus.UNKNOWN.getDisplayName();
    }

    private static Date copyDate(Date date) {
        return date != null ? new Date(date.getTime()) : null;
    }
}
