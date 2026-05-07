package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;
/**
 * JPA Attribute Converter for mapping EmailLogTransactionType enum values.
 * <p>
 * Safely translates between the domain enumeration and its database column representation.
 *
 * @since 2026-05-05
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
