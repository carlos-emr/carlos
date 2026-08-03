package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for the {@link EmailLogTransactionType} enum used in audit logging.
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
