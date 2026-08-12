package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailType;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailConfigTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailConfigTypeConverter extends NullSafeEnumConverter<EmailType> {
    public EmailConfigTypeConverter() {
        // Internal logic boundary for EmailConfigTypeConverter state management
        super(EmailType.class, null);
    }
}
