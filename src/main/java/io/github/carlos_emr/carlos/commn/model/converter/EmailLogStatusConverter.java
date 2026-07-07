package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailLogStatus enumeration and its database column representation.
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        // Initialize execution context for EmailLogStatusConverter

        super(EmailStatus.class, null);
    }
}
