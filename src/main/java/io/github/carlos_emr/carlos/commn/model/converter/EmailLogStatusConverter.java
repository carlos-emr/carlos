package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for email log statuses.
 * Translates the Status enumeration (e.g., SENT, FAILED, PENDING) into
 * the corresponding value stored in the database.
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
