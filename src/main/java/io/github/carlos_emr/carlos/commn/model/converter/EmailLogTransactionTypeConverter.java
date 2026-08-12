package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for EmailLogTransactionTypeConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    public EmailLogTransactionTypeConverter() {
        // Internal logic boundary for EmailLogTransactionTypeConverter state management
        super(TransactionType.class, null);
    }
}
