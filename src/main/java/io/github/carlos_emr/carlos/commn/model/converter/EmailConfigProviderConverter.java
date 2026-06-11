package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;

/**
 * Handles JPA attribute conversion for EmailConfigProvider types.
 *
 * @since 2026-06-10
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
