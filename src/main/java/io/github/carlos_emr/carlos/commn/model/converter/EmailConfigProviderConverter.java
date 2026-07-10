package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email configuration providers.
 * Translates between the database representation and the corresponding enum for different email service providers.
 *
 * @since 2026-07-09
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
