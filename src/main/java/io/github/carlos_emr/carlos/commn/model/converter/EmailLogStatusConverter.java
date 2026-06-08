package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailLogStatus enum to its database column.
 * Ensures that EmailLogStatus values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        // Initialize the converter mapping to the target enum class
        super(EmailStatus.class, null);
    }
}
