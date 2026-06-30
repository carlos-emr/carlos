package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter tracking the delivery state of an email.
 * Maps delivery states (e.g., PENDING, DELIVERED, BOUNCED) to database codes.
 */
@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        // Store final delivery states allowing for bounce analysis and compliance tracking
        super(EmailStatus.class, null);
    }
}
