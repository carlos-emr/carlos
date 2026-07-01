package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email attachment document types.
 * <p>
 * Handles mapping the document type enum for email attachments to the
 * appropriate database column in CARLOS EMR.
 * </p>
 */
@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        // Convert email attachment type enum to maintain reference integrity with the document manager.
        super(DocumentType.class, null);
    }
}
