package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * JPA Converter for translating EmailConfigProvider enums and attributes to and from database columns.
 *
 * @since 2026-06-26
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
