package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.event.SmsSendFailedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class JpaSmsTransactionRecorder implements SmsTransactionRecorder {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String TRANSACTION_REQUIRED_MESSAGE = "transaction is required";
    private static final String WEBHOOK_REQUIRED_MESSAGE = "webhook is required";

    private final SmsTransactionDao smsTransactionDao;
    private final ApplicationEventPublisher eventPublisher;

    public JpaSmsTransactionRecorder(SmsTransactionDao smsTransactionDao, ApplicationEventPublisher eventPublisher) {
        this.smsTransactionDao = smsTransactionDao;
        this.eventPublisher = eventPublisher;
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
        // Only fire when this call actually applies the write (applyLastWriterWins drops it on a
        // concurrent-modification conflict) and the row lands terminal FAILED.
        return applyLastWriterWins(
                transaction,
                "markProviderResult",
                row -> row.markProviderResult(providerResult),
                this::publishIfTerminalFailure
        );
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
        return applyLastWriterWins(
                transaction,
                "markRetryScheduled",
                row -> row.markRetryScheduled(providerResult, nextAttemptAt)
        );
    }

    @Override
    @Transactional
    public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        return applyLastWriterWins(transaction, "releaseClaim", row -> row.markClaimReleased(dueAt));
    }

    @Override
    @Transactional
    public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        // SMS providers deliver webhooks at least once, so a redelivered inbound message must be
        // idempotent. An existing inbound row for the same (provider, providerMessageId) is returned
        // unchanged instead of persisting a duplicate, which would otherwise violate the
        // (provider_type, provider_message_id) unique key and fail the retried callback.
        SmsProviderType providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        String providerMessageId = webhook.providerMessageId();
        if (providerMessageId != null && !providerMessageId.isBlank()) {
            SmsTransaction existing = smsTransactionDao
                    .findByProviderMessageId(providerType, providerMessageId)
                    .filter(found -> found.getDirection() == SmsDirection.INBOUND)
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }
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

    /**
     * Applies a worker-origin mutation under "last writer wins" semantics.
     * <p>
     * The worker claims a row, calls the SMS provider, then writes the result in a separate transaction,
     * which can race a delivery/inbound webhook updating the same row. Rather than merging the stale
     * detached row (which would raise an optimistic-lock failure at commit and poison the transaction),
     * the current row is re-loaded and its {@code @Version} is compared with the version the worker
     * observed at claim time. If the row advanced, a webhook (or another worker) already wrote it, so the
     * stale write is dropped with a warning and the current row is returned. Otherwise the mutation is
     * applied to the managed row and flushed at commit (bumping the version).
     */
    private SmsTransaction applyLastWriterWins(
            SmsTransaction claimed,
            String context,
            Consumer<SmsTransaction> mutation
    ) {
        return applyLastWriterWins(claimed, context, mutation, row -> { });
    }

    /**
     * @param onApplied invoked with the written row only when the mutation is actually applied (not when
     *                  a concurrent-modification conflict drops it), so side effects such as event
     *                  publication fire exactly once and only for writes that will be committed.
     */
    private SmsTransaction applyLastWriterWins(
            SmsTransaction claimed,
            String context,
            Consumer<SmsTransaction> mutation,
            Consumer<SmsTransaction> onApplied
    ) {
        Long id = claimed.getId();
        if (id == null) {
            // Not yet persisted: apply directly and merge (defensive; production rows always carry an id).
            mutation.accept(claimed);
            smsTransactionDao.merge(claimed);
            onApplied.accept(claimed);
            return claimed;
        }
        SmsTransaction current = smsTransactionDao.find(id);
        if (current == null) {
            return claimed;
        }
        if (current.getVersion() != claimed.getVersion()) {
            LOGGER.warn(
                    "SMS transaction {} {} skipped due to concurrent modification; last writer wins.",
                    id,
                    context
            );
            return current;
        }
        mutation.accept(current);
        onApplied.accept(current);
        return current;
    }

    private void publishIfTerminalFailure(SmsTransaction transaction) {
        if (transaction.getStatus() == SmsStatus.FAILED) {
            eventPublisher.publishEvent(SmsSendFailedEvent.from(transaction));
        }
    }
}
