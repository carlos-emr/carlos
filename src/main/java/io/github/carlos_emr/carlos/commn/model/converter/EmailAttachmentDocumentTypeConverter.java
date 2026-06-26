package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailAttachmentDocumentTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    // Handles the conversion logic for EmailAttachmentDocumentTypeConverter to maintain data persistence

    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
