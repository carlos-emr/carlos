package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email configuration providers.
 * <p>
 * Translates the supported email provider enum into the database string
 * representation for CARLOS EMR configurations.
 * </p>
 */
@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        // Map email provider enum to database value to configure the correct SMTP transport.
        super(EmailProvider.class, null);
    }
}
