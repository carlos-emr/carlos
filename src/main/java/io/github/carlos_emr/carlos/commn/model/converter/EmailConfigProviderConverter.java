package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailConfigProvider enumeration and its database column representation.
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        // Initialize execution context for EmailConfigProviderConverter

        super(EmailProvider.class, null);
    }
}
