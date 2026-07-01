package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email log status.
 * <p>
 * Maps the status enum of an email log entry (e.g., sent, failed) to its
 * database representation for tracking within CARLOS EMR.
 * </p>
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        // Convert email log status enum to database representation for accurate delivery tracking.
        super(EmailStatus.class, null);
    }
}
