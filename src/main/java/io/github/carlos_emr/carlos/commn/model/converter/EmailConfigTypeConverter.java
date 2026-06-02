package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for the EmailConfigType enumeration.
 * Maps the type of email configuration to its respective database representation.
 */

@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
