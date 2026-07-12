package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email transmission statuses.
 *
 * <p>Maps the current state (e.g., Pending, Sent, Failed) of an outbound email.</p>
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    // Failed statuses should trigger a retry mechanism or alert
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
