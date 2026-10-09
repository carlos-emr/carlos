package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class JpaSmsTransactionService implements SmsTransactionService {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String TRANSACTION_REQUIRED_MESSAGE = "transaction is required";
    private static final String WEBHOOK_REQUIRED_MESSAGE = "webhook is required";
    private static final String PROVIDER_MESSAGE_UNIQUE_CONSTRAINT = "sms_transaction_provider_message_uidx";

    private final SmsTransactionDao smsTransactionDao;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate inboundWriteTransaction;
    private final SmsProviderRetirementService retirementService;

    public JpaSmsTransactionService(
            SmsTransactionDao smsTransactionDao,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager
    ) {
        this(smsTransactionDao, eventPublisher, transactionManager, null);
    }

    @Autowired
    public JpaSmsTransactionService(SmsTransactionDao smsTransactionDao, ApplicationEventPublisher eventPublisher,
                                    PlatformTransactionManager transactionManager,
                                    SmsProviderRetirementService retirementService) {
        this.smsTransactionDao = smsTransactionDao;
        this.eventPublisher = eventPublisher;
        this.inboundWriteTransaction = new TransactionTemplate(transactionManager);
        this.inboundWriteTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.retirementService = retirementService;
    }

    JpaSmsTransactionService(SmsTransactionDao smsTransactionDao, ApplicationEventPublisher eventPublisher) {
        this.smsTransactionDao = smsTransactionDao;
        this.eventPublisher = eventPublisher;
        this.inboundWriteTransaction = null;
        this.retirementService = null;
    }

    @Override
    @Transactional
    public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                SmsConsentDecisionDto decision) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(decision, "decision is required before recording an outbound attempt");
        if (retirementService != null && !retirementService.admissionAllowed(retirementService.lockSelection(),
                providerType, command.messagePurpose())) {
            throw new SmsProviderSelectionChangedException();
        }
        SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
        if (decision.allowed()) {
            transaction.recordConsentDecision(decision);
        } else {
            transaction.markConsentBlocked(decision);
        }
        smsTransactionDao.persist(transaction);
        smsTransactionDao.flush();
        if (transaction.getId() != null) {
            transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(transaction.getId()));
            smsTransactionDao.flush();
        }
        return transaction;
    }

    @Override
    @Transactional
    public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(decision, "decision is required");
        return applyIfVersionMatches(transaction, "markConsentBlocked", row -> row.markConsentBlocked(decision));
    }

    @Override
    @Transactional
    public SmsTransaction recordConsentDecision(SmsTransaction transaction, SmsConsentDecisionDto decision) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(decision, "decision is required");
        // The caller sends on this snapshot, so a write the version check dropped must not look like success.
        AtomicBoolean applied = new AtomicBoolean();
        SmsTransaction recorded = applyIfVersionMatches(
                transaction,
                "recordConsentDecision",
                row -> row.recordConsentDecision(decision),
                row -> applied.set(true)
        );
        if (!applied.get()) {
            throw new SmsTransactionClaimConflictException(transaction.getId());
        }
        return recorded;
    }

    @Override
    @Transactional
    public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        requireUnretiredClaim(transaction);
        SmsTransaction marked = claimForSending(transaction, attemptAt);
        smsTransactionDao.flush();
        return marked;
    }

    @Override
    @Transactional
    public SmsTransaction renewClaim(SmsTransaction transaction, Date attemptAt) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        requireUnretiredClaim(transaction);
        // The caller sends on this claim, so a write the version check dropped must not look like success.
        AtomicBoolean applied = new AtomicBoolean();
        SmsTransaction renewed = applyIfVersionMatches(
                transaction,
                "renewClaim",
                row -> {
                    if (row.getStatus() == SmsStatus.SENDING) {
                        row.renewSendingClaim(attemptAt);
                        applied.set(true);
                    }
                }
        );
        if (!applied.get()) {
            throw new SmsTransactionClaimConflictException(transaction.getId());
        }
        // Run the version-checked update now, even inside a caller's transaction, so a conflict surfaces before any send.
        smsTransactionDao.flush();
        return renewed;
    }

    @Override
    @Transactional
    public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        Objects.requireNonNull(providerResult, "providerResult is required");
        // Only fire when this call actually applies the write (applyIfVersionMatches drops it on a
        // concurrent-modification conflict) and the row lands terminal FAILED.
        return applyIfVersionMatches(
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
        if (retirementService != null) {
            retirementService.lockSelection();
            if (retirementService.retired(transaction)) {
                return failRetiredUnsent(transaction, "retiredRetry");
            }
        }
        return applyIfVersionMatches(
                transaction,
                "markRetryScheduled",
                row -> row.markRetryScheduled(providerResult, nextAttemptAt)
        );
    }

    @Override
    @Transactional
    public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
        Objects.requireNonNull(transaction, TRANSACTION_REQUIRED_MESSAGE);
        if (retirementService != null) {
            retirementService.lockSelection();
            if (retirementService.retired(transaction)) {
                return failRetiredUnsent(transaction, "retiredRelease");
            }
        }
        return applyIfVersionMatches(transaction, "releaseClaim", row -> row.markClaimReleased(dueAt));
    }

    @Override
    public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
        Objects.requireNonNull(webhook, WEBHOOK_REQUIRED_MESSAGE);
        if (inboundWriteTransaction == null) {
            return recordInboundMessageInCurrentTransaction(webhook);
        }

        SmsProviderType providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        String providerMessageId = requireProviderMessageId(webhook.providerMessageId(), "inbound webhooks");
        try {
            return Objects.requireNonNull(inboundWriteTransaction.execute(
                    status -> recordInboundMessageInCurrentTransaction(webhook)
            ));
        } catch (RuntimeException e) {
            if (!isProviderMessageDuplicate(e)) {
                throw e;
            }
            return Objects.requireNonNull(inboundWriteTransaction.execute(status -> findInboundMessage(
                    providerType,
                    providerMessageId
            ).orElseThrow(() -> e)));
        }
    }

    private SmsTransaction recordInboundMessageInCurrentTransaction(SmsInboundWebhookDto webhook) {
        // SMS providers deliver webhooks at least once, so a redelivered inbound message must be
        // idempotent. An existing inbound row for the same (provider, providerMessageId) is returned
        // unchanged instead of persisting a duplicate, which would otherwise violate the
        // (provider_type, provider_message_id) unique key and fail the retried callback.
        SmsProviderType providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        String providerMessageId = requireProviderMessageId(webhook.providerMessageId(), "inbound webhooks");
        SmsTransaction existing = findInboundMessage(providerType, providerMessageId).orElse(null);
        if (existing != null) {
            return existing;
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
        if (isBlank(webhook.providerMessageId()) && isBlank(webhook.clientReferenceId())) {
            throw new IllegalArgumentException(
                    "providerMessageId or clientReferenceId is required for delivery webhooks"
            );
        }
        SmsProviderType providerType = webhook.providerType() == null ? SmsProviderType.STUB : webhook.providerType();
        SmsTransaction transaction = findDeliveryTarget(providerType, webhook).orElse(null);
        if (transaction == null) {
            transaction = SmsTransaction.deliveryEvent(webhook);
            smsTransactionDao.persist(transaction);
            smsTransactionDao.flush();
        } else {
            requireMatchingDeliveryTarget(transaction, webhook);
            // Publish only when a message CARLOS handed to the carrier turns FAILED. Replayed or ignored
            // callbacks stay silent, and so do rows that were never sent (still queued, or blocked by
            // consent) and the placeholder rows that unmatched callbacks create, which name no patient.
            boolean awaitingCarrier = transaction.isAwaitingCarrierOutcome();
            transaction.markDeliveryEvent(webhook);
            smsTransactionDao.merge(transaction);
            if (awaitingCarrier) {
                publishIfTerminalFailure(transaction);
            }
        }
        return transaction;
    }

    private static void requireMatchingDeliveryTarget(SmsTransaction transaction, SmsDeliveryWebhookDto webhook) {
        if (transaction.getDirection() != SmsDirection.OUTBOUND
                || identifiersConflict(transaction.getProviderMessageId(), webhook.providerMessageId())
                || identifiersConflict(transaction.getClientReferenceId(), webhook.clientReferenceId())) {
            throw new IllegalArgumentException("Delivery callback identifiers conflict with the stored outbound message");
        }
    }

    private static boolean identifiersConflict(String stored, String received) {
        return !isBlank(stored) && !isBlank(received) && !stored.equals(received);
    }

    private Optional<SmsTransaction> findInboundMessage(SmsProviderType providerType, String providerMessageId) {
        return smsTransactionDao
                .findByProviderMessageId(providerType, providerMessageId)
                .filter(found -> found.getDirection() == SmsDirection.INBOUND);
    }

    private Optional<SmsTransaction> findDeliveryTarget(SmsProviderType providerType, SmsDeliveryWebhookDto webhook) {
        if (!isBlank(webhook.clientReferenceId())) {
            Optional<SmsTransaction> byClientReference = smsTransactionDao.findByClientReferenceId(
                    providerType,
                    webhook.clientReferenceId()
            );
            if (byClientReference.isPresent()) {
                return byClientReference;
            }
        }
        if (isBlank(webhook.providerMessageId())) {
            return Optional.empty();
        }
        return smsTransactionDao.findByProviderMessageId(providerType, webhook.providerMessageId());
    }

    @Override
    @Transactional
    public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
        Date claimAt = now == null ? new Date() : new Date(now.getTime());
        if (retirementService != null) {
            if (!retirementService.admissionAllowed(retirementService.lockSelection(), providerType,
                    SmsMessagePurpose.PATIENT_MESSAGE)) {
                return List.of();
            }
            return smsTransactionDao.claimDueOutboundQueue(providerType, claimAt, limit,
                    retirementService.retiredThrough(providerType));
        }
        return smsTransactionDao.claimDueOutboundQueue(providerType, claimAt, limit);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int failRetiredOutboundQueue(SmsProviderType providerType, int limit) {
        if (retirementService == null || providerType == null) {
            return 0;
        }
        List<Long> failedIds = new ArrayList<>();
        int failed = 0;
        for (int handled = 0; handled < Math.max(1, limit); handled++) {
            AtomicReference<Long> attemptedId = new AtomicReference<>();
            try {
                boolean found = Boolean.TRUE.equals(inboundWriteTransaction.execute(status -> {
                    var config = retirementService.lockSelection();
                    long cutoff = retirementService.retiredThrough(providerType);
                    boolean inactive = !retirementService.admissionAllowed(config, providerType,
                            SmsMessagePurpose.PATIENT_MESSAGE);
                    // Sending disabled does not make the selected provider's queued tests inactive.
                    inactive = config.map(c -> c.getProviderType() != providerType).orElse(inactive);
                    List<SmsTransaction> rows = smsTransactionDao.findRetiredQueuedForUpdate(providerType, cutoff,
                            inactive, new Date(), failedIds);
                    if (rows.isEmpty()) {
                        return false;
                    }
                    SmsTransaction row = rows.get(0);
                    attemptedId.set(row.getId());
                    row.markProviderResult(SmsProviderRetirementService.retirementFailure(row));
                    smsTransactionDao.flush();
                    publishIfTerminalFailure(row);
                    return true;
                }));
                if (!found) {
                    break;
                }
                failed++;
            } catch (RuntimeException e) {
                Long id = attemptedId.get();
                LOGGER.warn("SMS retirement write failed; exceptionClass={}", e.getClass().getName());
                if (id == null) {
                    break; // Configuration/storage failure: do not spin through more work.
                }
                failedIds.add(id);
                if (failedIds.size() >= 2) {
                    break;
                }
            }
        }
        return failed;
    }

    private void requireUnretiredClaim(SmsTransaction transaction) {
        if (retirementService != null) {
            retirementService.lockSelection();
            boolean initialSystemTest = transaction.getMessagePurpose()
                    == SmsMessagePurpose.SYSTEM_TEST
                    && transaction.getProviderType() == SmsProviderType.STUB;
            if (!initialSystemTest && retirementService.retired(transaction)) {
                throw new SmsTransactionClaimConflictException(transaction.getId());
            }
        }
    }

    private SmsTransaction failRetiredUnsent(SmsTransaction transaction, String context) {
        AtomicBoolean failed = new AtomicBoolean();
        return applyIfVersionMatches(transaction, context, row -> {
            if (row.getStatus() == SmsStatus.QUEUED || row.getStatus() == SmsStatus.SENDING) {
                row.markProviderResult(SmsProviderRetirementService.retirementFailure(row));
                failed.set(true);
            }
        }, row -> {
            if (failed.get()) {
                publishIfTerminalFailure(row);
            }
        });
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
     * Applies a worker-origin mutation only when the claimed version still matches.
     * <p>
     * The worker claims a row, calls the SMS provider, then writes the result in a separate transaction,
     * which can race a delivery/inbound webhook updating the same row. Rather than merging the stale
     * detached row (which would raise an optimistic-lock failure at commit and poison the transaction),
     * the current row is re-loaded and its {@code @Version} is compared with the version the worker
     * observed at claim time. If the row advanced, a webhook (or another worker) already wrote it, so the
     * stale write is dropped with a warning and the current row is returned. Otherwise the mutation is
     * applied to the managed row and flushed at commit (bumping the version).
     */
    private SmsTransaction applyIfVersionMatches(
            SmsTransaction claimed,
            String context,
            Consumer<SmsTransaction> mutation
    ) {
        return applyIfVersionMatches(claimed, context, mutation, row -> { });
    }

    /**
     * @param onApplied invoked with the written row only when the mutation is actually applied (not when
     *                  a concurrent-modification conflict drops it), so side effects such as event
     *                  publication uses AFTER_COMMIT listeners so rolled-back writes have no external effects.
     */
    private SmsTransaction applyIfVersionMatches(
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
            LOGGER.warn("SMS transaction {} {} skipped: the row no longer exists.", id, context);
            return claimed;
        }
        if (current.getVersion() != claimed.getVersion()) {
            LOGGER.warn(
                    "SMS transaction {} {} skipped due to concurrent modification; preserving the newer version.",
                    id,
                    context
            );
            return current;
        }
        mutation.accept(current);
        onApplied.accept(current);
        return current;
    }

    private SmsTransaction claimForSending(SmsTransaction claimed, Date attemptAt) {
        Long id = claimed.getId();
        if (id == null) {
            claimed.markSending(attemptAt);
            smsTransactionDao.merge(claimed);
            return claimed;
        }
        SmsTransaction current = smsTransactionDao.find(id);
        if (current == null || current.getVersion() != claimed.getVersion() || current.getStatus() != SmsStatus.QUEUED) {
            LOGGER.warn(
                    "SMS transaction {} markSending skipped because the row is no longer claimable.",
                    id
            );
            throw new SmsTransactionClaimConflictException(id);
        }
        current.markSending(attemptAt);
        return current;
    }

    private void publishIfTerminalFailure(SmsTransaction transaction) {
        if (transaction.getStatus() == SmsStatus.FAILED) {
            eventPublisher.publishEvent(SmsSendFailedEvent.from(transaction));
        }
    }

    private static String requireProviderMessageId(String providerMessageId, String context) {
        if (isBlank(providerMessageId)) {
            throw new IllegalArgumentException("providerMessageId is required for " + context);
        }
        return providerMessageId;
    }

    private static boolean isProviderMessageDuplicate(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(PROVIDER_MESSAGE_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
