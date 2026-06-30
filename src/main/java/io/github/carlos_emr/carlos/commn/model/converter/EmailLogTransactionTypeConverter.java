package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter mapping email transaction types.
 * Defines whether an email was an automated reminder, explicit provider message, etc.
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        // Record the communication context (e.g. reminder vs clinical note) for audit purposes
        super(TransactionType.class, null);
    }
}
