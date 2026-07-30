package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.persistence.Converter;

/**
 * Converter class for EmailAttachmentDocumentType to handle database transformations.
 *
 * This class is part of the CARLOS EMR system.
 */


@Converter
public class EmailAttachmentDocumentTypeConverter extends NullSafeEnumConverter<DocumentType> {
    public EmailAttachmentDocumentTypeConverter() {
        super(DocumentType.class, null);
    }
}
