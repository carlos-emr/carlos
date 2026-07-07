package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailAttachmentDocumentType enumeration and its database column representation.
 */
@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        // Initialize execution context for EmailAttachmentDocumentTypeConverter

        super(DocumentType.class, null);
    }
}
