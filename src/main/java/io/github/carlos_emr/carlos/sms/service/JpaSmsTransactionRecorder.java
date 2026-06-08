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

import java.util.Objects;

@Service
public class JpaSmsTransactionRecorder implements SmsTransactionRecorder {
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
        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(decision, "decision is required");
        transaction.markConsentBlocked(decision);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(providerResult, "providerResult is required");
        transaction.markProviderResult(providerResult);
        smsTransactionDao.merge(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
        Objects.requireNonNull(webhook, "webhook is required");
        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
        smsTransactionDao.persist(transaction);
        smsTransactionDao.flush();
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
        Objects.requireNonNull(webhook, "webhook is required");
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
}
