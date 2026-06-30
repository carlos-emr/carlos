package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter mapping email attachment types.
 * Specifies the nature of the attached document (e.g., PDF, image).
 */
@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        // Track underlying MIME contexts within the persistence layer for later retrieval
        super(DocumentType.class, null);
    }
}
