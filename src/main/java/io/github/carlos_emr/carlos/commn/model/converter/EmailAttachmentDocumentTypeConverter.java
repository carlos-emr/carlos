package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailAttachmentDocumentType enum to its database column.
 * Ensures that EmailAttachmentDocumentType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(DocumentType.class, null);
    }
}
