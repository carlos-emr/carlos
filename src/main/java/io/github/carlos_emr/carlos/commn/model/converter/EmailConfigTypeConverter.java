package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailConfigType enum to its database column.
 * Ensures that EmailConfigType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(EmailType.class, null);
    }
}
