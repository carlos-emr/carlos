package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface SmsTransactionDao extends AbstractDao<SmsTransaction> {
    List<SmsTransaction> findByDemographicNo(Integer demographicNo, int limit);

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
