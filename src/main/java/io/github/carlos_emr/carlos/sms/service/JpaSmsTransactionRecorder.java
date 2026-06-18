package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class JpaSmsTransactionRecorder implements SmsTransactionRecorder {
    private static final String TRANSACTION_REQUIRED_MESSAGE = "transaction is required";
    private static final String WEBHOOK_REQUIRED_MESSAGE = "webhook is required";

    private final SmsTransactionDao smsTransactionDao;

    public JpaSmsTransactionRecorder(SmsTransactionDao smsTransactionDao) {
        this.smsTransactionDao = smsTransactionDao;
    }

    @Override
    @Transactional
    public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType) {
        Objects.requireNonNull(command, "command is required");
        SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
        smsTransactionDao.persist(transaction);
        smsTransactionDao.flush();
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(decision, "decision is required");
        transaction.markConsentBlocked(decision);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        transaction.markSending(attemptAt);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(providerResult, "providerResult is required");
        transaction.markProviderResult(providerResult);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markRetryScheduled(
            SmsTransaction transaction,
            SmsProviderSendResultDto providerResult,
            Date nextAttemptAt
    ) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(providerResult, "providerResult is required");
        transaction.markRetryScheduled(providerResult, nextAttemptAt);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
        smsTransactionDao.persist(transaction);
        smsTransactionDao.flush();
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        if (webhook.providerMessageId() == null || webhook.providerMessageId().isBlank()) {
            throw new IllegalArgumentException("providerMessageId is required for delivery webhooks");
        }
        SmsProviderType providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        SmsTransaction transaction = smsTransactionDao
                .findByProviderMessageId(providerType, webhook.providerMessageId())
                .orElse(null);
        if (transaction == null) {
            transaction = SmsTransaction.deliveryEvent(webhook);
            smsTransactionDao.persist(transaction);
            smsTransactionDao.flush();
        } else {
            transaction.markDeliveryEvent(webhook);
            smsTransactionDao.merge(transaction);
        }
        return transaction;
    }

    @Override
    @Transactional
    public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
        Date claimAt = now == null ? new Date() : new Date(now.getTime());
        return smsTransactionDao.claimDueOutboundQueue(providerType, claimAt, limit);
    }

    @Override
    @Transactional
    public List<SmsTransaction> claimStaleSendingForRecovery(
            SmsProviderType providerType,
            Date staleBefore,
            Date recoveryAt,
            int limit
    ) {
        Date safeStaleBefore = staleBefore == null ? new Date() : new Date(staleBefore.getTime());
        Date safeRecoveryAt = recoveryAt == null ? new Date() : new Date(recoveryAt.getTime());
        return smsTransactionDao.claimStaleOutboundSendingForRecovery(
                providerType,
                safeStaleBefore,
                safeRecoveryAt,
                limit
        );
    }
}
