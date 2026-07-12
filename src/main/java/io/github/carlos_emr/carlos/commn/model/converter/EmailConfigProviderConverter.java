package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email service providers.
 *
 * <p>Translates the chosen email vendor or system into the database format.</p>
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    // Ensure custom provider configurations map to a generic type if unsupported
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
