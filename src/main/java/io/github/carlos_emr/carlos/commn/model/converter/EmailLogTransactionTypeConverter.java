package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for email log transaction types.
 * <p>
 * Facilitates the mapping between the transaction type enum and the database
 * column used for auditing email communications in CARLOS EMR.
 * </p>
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        // Map email transaction type to string representation for legacy audit log compatibility.
        super(TransactionType.class, null);
    }
}
