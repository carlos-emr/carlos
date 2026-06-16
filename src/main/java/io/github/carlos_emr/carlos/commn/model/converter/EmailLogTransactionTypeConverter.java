package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * JPA Attribute Converter for the EmailLogTransactionType enumeration.
 * Transparently maps between the Java enum and the legacy database column value during persistence.
 * This pattern avoids hardcoding enum ordinals and preserves schema compatibility with older application versions.
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
