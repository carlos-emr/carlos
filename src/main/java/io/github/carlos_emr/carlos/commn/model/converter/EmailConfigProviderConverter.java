package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailConfigProvider enum to its database column.
 * Ensures that EmailConfigProvider values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        // Initialize the converter mapping to the target enum class
        super(EmailProvider.class, null);
    }
}
