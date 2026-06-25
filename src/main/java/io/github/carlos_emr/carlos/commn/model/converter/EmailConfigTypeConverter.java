package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the EmailType enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    // Initialize the null-safe converter with the specific EmailType class.
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
