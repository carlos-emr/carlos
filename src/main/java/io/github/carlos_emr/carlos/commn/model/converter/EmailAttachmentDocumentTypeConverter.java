package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the EmailAttachmentDocumentType enum.
 * Categorizes the nature of the attached file (e.g., Invoice, Lab Result, Consult Note) for organizational purposes.
 */

@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
