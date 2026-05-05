package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * EmailConfigTypeConverter provides functionality and data models for the EmailConfigTypeConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
