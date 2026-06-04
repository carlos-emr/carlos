package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the EmailLogStatus enum.
 * Reflects the delivery state of an email (e.g., Sent, Failed, Bounced) as recorded by the SMTP server.
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
