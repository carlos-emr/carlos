package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the EmailStatus enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    // Initialize the null-safe converter with the specific EmailStatus class.
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
