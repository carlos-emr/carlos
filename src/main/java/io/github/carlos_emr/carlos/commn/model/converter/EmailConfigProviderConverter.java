package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * Configuration entity detailing SMTP settings and credentials for outgoing email.
 * <p>
 * Supports mapping multiple configuration profiles to different providers or system roles.
 *
 * @since 2026-05-05
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        super(EmailProvider.class, null);
    }
}
