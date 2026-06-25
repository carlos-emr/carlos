package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the EmailProvider enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    // Initialize the null-safe converter with the specific EmailProvider class.
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
