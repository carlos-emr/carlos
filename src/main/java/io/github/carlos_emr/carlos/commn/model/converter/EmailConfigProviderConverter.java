package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter mapping the EmailConfigProvider enum (e.g., SMTP, SendGrid, AmazonSES) to its database column representation.
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
