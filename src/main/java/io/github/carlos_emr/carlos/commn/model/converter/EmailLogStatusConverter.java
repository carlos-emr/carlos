package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailLogStatusConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        // Internal logic boundary for EmailLogStatusConverter state management
        super(EmailStatus.class, null);
    }
}
