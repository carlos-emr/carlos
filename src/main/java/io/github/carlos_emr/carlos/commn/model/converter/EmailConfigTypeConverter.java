package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter defining the scope or type of email configuration.
 * Determines if the config is system-wide or user-specific.
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        // Differentiate global SMTP bindings from provider-specific overriding configurations
        super(EmailType.class, null);
    }
}
