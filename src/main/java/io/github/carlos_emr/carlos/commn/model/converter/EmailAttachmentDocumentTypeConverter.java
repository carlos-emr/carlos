package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;
/**
 * JPA Converter for translating EmailAttachmentDocumentType enums and attributes to and from database columns.
 *
 * @since 2026-06-26
 */

@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
