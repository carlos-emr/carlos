package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * Converter class for EmailLogStatus to handle database transformations.
 *
 * This class is part of the CARLOS EMR system.
 */


@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
