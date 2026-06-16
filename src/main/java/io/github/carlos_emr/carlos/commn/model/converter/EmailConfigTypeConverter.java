package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the EmailConfigType enumeration.
 * Transparently maps between the Java enum and the legacy database column value during persistence.
 * This pattern avoids hardcoding enum ordinals and preserves schema compatibility with older application versions.
 */
@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        super(EmailType.class, null);
    }
}
