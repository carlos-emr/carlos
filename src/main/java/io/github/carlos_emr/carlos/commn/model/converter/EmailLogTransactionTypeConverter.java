package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between EmailLogTransactionType enumeration and its database column representation.
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        // Initialize execution context for EmailLogTransactionTypeConverter

        super(TransactionType.class, null);
    }
}
