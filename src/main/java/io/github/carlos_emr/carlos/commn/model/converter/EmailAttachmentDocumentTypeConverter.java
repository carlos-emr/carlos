package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the DocumentType enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    // Initialize the null-safe converter with the specific DocumentType class.
    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
