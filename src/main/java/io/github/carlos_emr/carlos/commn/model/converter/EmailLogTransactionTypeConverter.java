package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;

/**
 * EmailLogTransactionTypeConverter provides functionality and data models for the EmailLogTransactionTypeConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
