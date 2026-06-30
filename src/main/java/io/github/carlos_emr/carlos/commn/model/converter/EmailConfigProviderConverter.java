package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter identifying the underlying email service provider.
 * Distinguishes between standard SMTP and vendor-specific APIs.
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        // Determine external vendor integration targets from standardized configuration sets
        super(EmailProvider.class, null);
    }
}
