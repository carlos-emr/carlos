package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for mapping EmailLogStatus enum values.
 * <p>
 * Safely translates between the domain enumeration and its database column representation.
 *
 * @since 2026-05-05
 */

@Converter
public class EmailLogStatusConverter extends NullSafeEnumConverter<EmailStatus> {
    public EmailLogStatusConverter() {
        super(EmailStatus.class, null);
    }
}
