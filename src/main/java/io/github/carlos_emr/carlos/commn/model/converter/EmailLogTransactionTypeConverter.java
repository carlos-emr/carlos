package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for EmailLogTransactionTypeConverter, mapping entity attributes to database columns.
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    // Handles the conversion logic for EmailLogTransactionTypeConverter to maintain data persistence

    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
