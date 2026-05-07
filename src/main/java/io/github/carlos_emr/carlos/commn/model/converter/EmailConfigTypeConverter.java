package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;
/**
 * Configuration entity detailing SMTP settings and credentials for outgoing email.
 * <p>
 * Supports mapping multiple configuration profiles to different providers or system roles.
 *
 * @since 2026-05-05
 */

@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
