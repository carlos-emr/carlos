package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the EmailLogTransactionType enum to its database column.
 * Ensures that EmailLogTransactionType values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        // Initialize the converter mapping to the target enum class
        super(TransactionType.class, null);
    }
}
