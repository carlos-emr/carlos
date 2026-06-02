package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for the EmailLogTransactionType enumeration.
 * Maps the type of email transaction (e.g., initial send, retry, bounce) to its persistent database value.
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
