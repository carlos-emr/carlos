package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email attachment document types.
 *
 * <p>Categorizes attachments (e.g., PDF, Image, CDA) for proper handling
 * and display in the UI.</p>
 */

@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    // Unknown document types should be treated as generic binaries
    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
