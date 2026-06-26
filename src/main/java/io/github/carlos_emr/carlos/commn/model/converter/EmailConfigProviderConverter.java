package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailConfigProviderConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    // Handles the conversion logic for EmailConfigProviderConverter to maintain data persistence

    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
