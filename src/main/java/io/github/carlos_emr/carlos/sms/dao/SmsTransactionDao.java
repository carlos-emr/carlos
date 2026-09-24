package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface SmsTransactionDao extends AbstractDao<SmsTransaction> {
    List<SmsTransaction> findByDemographicNo(Integer demographicNo, int limit);

    /**
     * One page of a patient's messages, ordered by {@code createdAt DESC, id DESC}. The id tie-breaker
     * gives rows created in the same instant a fixed order; a message recorded between two page loads
     * still shifts later rows by one page position.
     *
     * @param demographicNo the patient; {@code null} returns an empty list
     * @param offset        zero-based first row; negative values are treated as 0
     * @param limit         page size; non-positive values use the default (100) and larger ones are capped at 500
     * @return the page, never {@code null}
     */
    List<SmsTransaction> findByDemographicNo(Integer demographicNo, int offset, int limit);

    /**
     * @param demographicNo the patient; {@code null} returns 0
     * @return how many messages are recorded for the patient
     */
    long countByDemographicNo(Integer demographicNo);

    Optional<SmsTransaction> findByProviderMessageId(SmsProviderType providerType, String providerMessageId);

    Optional<SmsTransaction> findByClientReferenceId(SmsProviderType providerType, String clientReferenceId);

    List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date claimAt, int limit);

    List<SmsTransaction> claimStaleOutboundSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    );
}
