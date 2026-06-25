package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the TransactionType enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    // Initialize the null-safe converter with the specific TransactionType class.
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
