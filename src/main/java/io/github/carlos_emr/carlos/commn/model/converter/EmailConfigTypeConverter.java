package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailConfigType enumeration and its database column representation.
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        // Initialize execution context for EmailConfigTypeConverter

        super(EmailType.class, null);
    }
}
