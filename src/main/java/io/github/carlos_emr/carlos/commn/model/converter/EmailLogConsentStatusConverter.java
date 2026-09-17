package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import jakarta.persistence.Converter;

/**
 * Converts email consent snapshot status values for EmailLog persistence.
 *
 * @since 2026-07-06
 */
@Converter
public class EmailLogConsentStatusConverter extends NullSafeEnumConverter<EmailConsentStatus> {
    /** Creates a null-safe converter for email consent statuses. */
    public EmailLogConsentStatusConverter() {
        super(EmailConsentStatus.class, null);
    }
}
