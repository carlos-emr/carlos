package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailAttachmentDocumentTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        // Internal logic boundary for EmailAttachmentDocumentTypeConverter state management
        super(DocumentType.class, null);
    }
}
