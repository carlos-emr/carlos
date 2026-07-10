package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email log statuses.
 * Translates between the database representation and the EmailLogStatus enum to track email delivery success or failure.
 *
 * @since 2026-07-09
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
