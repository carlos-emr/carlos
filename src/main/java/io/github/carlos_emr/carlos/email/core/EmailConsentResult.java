package io.github.carlos_emr.carlos.email.core;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;

/**
 * Immutable result of resolving patient email consent for compose display and send-time enforcement.
 */
public class EmailConsentResult {
    private final String consentName;
    private final EmailConsentStatus status;
    private final Integer consentId;
    private final Date consentLastUpdateDate;

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

    public String getDisplayStatus() {
        return status != null ? status.getDisplayName() : EmailConsentStatus.UNKNOWN.getDisplayName();
    }

    private static Date copyDate(Date date) {
        return date != null ? new Date(date.getTime()) : null;
    }
}
