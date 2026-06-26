package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailConfigTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    // Handles the conversion logic for EmailConfigTypeConverter to maintain data persistence

    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
