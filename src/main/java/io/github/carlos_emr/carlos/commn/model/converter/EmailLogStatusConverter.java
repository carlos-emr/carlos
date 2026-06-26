package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailLogStatusConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    // Handles the conversion logic for EmailLogStatusConverter to maintain data persistence

    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
