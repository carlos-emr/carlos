package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailConfig.EmailProvider;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailConfigProviderConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailConfigProviderConverter extends NullSafeEnumConverter<EmailProvider> {
    public EmailConfigProviderConverter() {
        // Internal logic boundary for EmailConfigProviderConverter state management
        super(EmailProvider.class, null);
    }
}
