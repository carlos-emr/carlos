package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import jakarta.persistence.Converter;

/**
 * Converts email consent snapshot status values for EmailLog persistence.
 */
@Converter
public class EmailLogConsentStatusConverter extends NullSafeEnumConverter<EmailConsentStatus> {
    public EmailLogConsentStatusConverter() {
        super(EmailConsentStatus.class, null);
    }
}
