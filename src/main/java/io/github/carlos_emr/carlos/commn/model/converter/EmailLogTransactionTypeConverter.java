package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for email transaction types.
 *
 * <p>Distinguishes between different categories of outbound emails,
 * such as appointment reminders, referrals, or billing notices.</p>
 */

@Converter
public class EmailLogTransactionTypeConverter extends NullSafeEnumConverter<TransactionType> {
    // Transaction types dictate the retention policy for the email log
    public EmailLogTransactionTypeConverter() {
        super(TransactionType.class, null);
    }
}
