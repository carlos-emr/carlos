package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for handling specific email configuration provider enumerations.
 */

@Converter

public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
