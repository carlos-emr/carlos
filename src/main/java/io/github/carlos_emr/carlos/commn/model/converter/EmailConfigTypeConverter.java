package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email configuration types.
 *
 * <p>Maps the protocol or security type (e.g., SMTP, SMTPS) to the database.</p>
 */

@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    // Default to secure protocols (SMTPS) if the configuration is ambiguous
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
