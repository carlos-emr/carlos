package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email configuration types.
 * <p>
 * Maps the email configuration category enum to the corresponding database
 * representation for CARLOS EMR settings.
 * </p>
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        // Map email configuration type enum to its legacy database representation.
        super(EmailType.class, null);
    }
}
