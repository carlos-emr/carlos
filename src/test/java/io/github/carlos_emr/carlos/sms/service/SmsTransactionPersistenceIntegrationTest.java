package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the {@code sms_transaction} system-of-record against the H2 (MySQL-mode) schema
 * generated from the entities. These exercise behaviour that mocked DAO unit tests cannot:
 * {@code @Version} version-checked updates, and the {@code (provider_type, provider_message_id)} unique key
 * (incl. inbound-webhook idempotency).
 *
 * Queue locking and rate-limiter SQL are exercised separately by SmsQueuePersistenceIntegrationTest.
 */
@Tag("integration")
@Tag("service")
@DisplayName("SmsTransaction persistence integration")
class SmsTransactionPersistenceIntegrationTest extends CarlosTestBase {

    @Autowired
    private SmsTransactionDao smsTransactionDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private JpaSmsTransactionService recorder;

    @BeforeEach
    void setUp() {
        recorder = new JpaSmsTransactionService(smsTransactionDao, mock(ApplicationEventPublisher.class));
    }

    @Test
    @DisplayName("delivery callbacks cannot mutate an inbound message with a matching provider id")
    void shouldPreserveInboundMessage_whenDeliveryCallbackTargetsIt() {
        SmsTransaction inbound = recorder.recordInboundMessage(new SmsInboundWebhookDto(SmsProviderType.STUB,
                "inbound-target", "+14165551212", "+14165550000", "synthetic reply", Instant.EPOCH, null));
        SmsDeliveryWebhookDto callback = new SmsDeliveryWebhookDto(SmsProviderType.STUB,
                "inbound-target", SmsStatus.DELIVERED, Instant.now(), null, null, null);
        assertThatThrownBy(() -> recorder.recordDeliveryEvent(callback))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inbound.getStatus()).isEqualTo(SmsStatus.RECEIVED);
    }

    @Test
    @DisplayName("conflicting callback identifiers cannot overwrite a different outbound message")
    void shouldRejectConflictingIdentifiers_whenDeliveryCallbackMatchesOnlyClientReference() {
        SmsTransaction outbound = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic", "999998"),
                SmsProviderType.STUB, SmsConsentDecisionDto.permit());
        outbound.markProviderResult(SmsProviderSendResultDto.accepted("correct-provider-id", SmsStatus.SENT));
        entityManager.flush();
        SmsDeliveryWebhookDto callback = new SmsDeliveryWebhookDto(SmsProviderType.STUB,
                "wrong-provider-id", SmsStatus.DELIVERED, Instant.now(), null, null,
                outbound.getClientReferenceId(), null);
        assertThatThrownBy(() -> recorder.recordDeliveryEvent(callback)).isInstanceOf(IllegalArgumentException.class);
        assertThat(outbound.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("denied outbound attempts are never claimable and do not store the message body")
    void shouldPersistOnlyBlockedAudit_whenConsentDeniesAdmission() {
        SmsTransaction blocked = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Synthetic confidential message", "999998"),
                SmsProviderType.STUB, SmsConsentDecisionDto.blocked(
                        SmsStatus.CONSENT_BLOCKED, "DENIED", "Consent denied"));
        Long id = blocked.getId();
        String digest = blocked.getMessageBodySha256();
        entityManager.flush();
        entityManager.clear();

        SmsTransaction reloaded = entityManager.find(SmsTransaction.class, id);
        assertThat(reloaded.getStatus()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(reloaded.toSendCommand().body()).isNull();
        assertThat(reloaded.getMessageBodySha256()).isEqualTo(digest);
        assertThat(reloaded.getMessageBodyLength()).isEqualTo("Synthetic confidential message".length());
        assertThat(smsTransactionDao.claimDueOutboundQueue(SmsProviderType.STUB, new java.util.Date(), 10))
                .isEmpty();
    }

    @Test
    @DisplayName("keeps the webhook state when a stale worker write conflicts (newer version preserved)")
    void shouldKeepWebhookState_whenStaleWorkerWriteConflicts() {
        SmsTransaction claimed = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        entityManager.persist(claimed);
        entityManager.flush();
        Long id = claimed.getId();
        // Detach a copy at the version the worker observed when it claimed the row.
        entityManager.detach(claimed);

        // A delivery webhook lands first and advances the row's @Version.
        SmsTransaction managed = entityManager.find(SmsTransaction.class, id);
        managed.markDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "pm-conflict",
                SmsStatus.DELIVERED,
                Instant.now(),
                null,
                null,
                null
        ));
        entityManager.flush();

        // The worker then tries to write its (now stale) provider result.
        SmsTransaction result = recorder.markProviderResult(
                claimed,
                SmsProviderSendResultDto.failed("WORKER_LATE", "late worker write")
        );

        assertThat(result.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(result.getErrorCode()).isNotEqualTo("WORKER_LATE");

        entityManager.flush();
        entityManager.clear();
        SmsTransaction reloaded = entityManager.find(SmsTransaction.class, id);
        assertThat(reloaded.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(reloaded.getErrorCode()).isNotEqualTo("WORKER_LATE");
    }

    @Test
    @DisplayName("matches early delivery webhooks by client reference before provider id is recorded")
    void shouldMatchOutboundRow_whenDeliveryWebhookArrivesBeforeProviderResultIsRecorded() {
        SmsTransaction outbound = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB,
                SmsConsentDecisionDto.permit()
        );
        String clientReferenceId = outbound.getClientReferenceId();
        assertThat(clientReferenceId).isEqualTo(SmsTransaction.clientReferenceIdFor(outbound.getId()));
        entityManager.detach(outbound);

        SmsTransaction delivery = recorder.recordDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-early",
                SmsStatus.DELIVERED,
                Instant.parse("2026-06-08T12:00:00Z"),
                null,
                null,
                clientReferenceId,
                null
        ));
        entityManager.flush();

        SmsTransaction staleProviderWrite = recorder.markProviderResult(
                outbound,
                SmsProviderSendResultDto.accepted("provider-early", SmsStatus.SENT)
        );

        assertThat(delivery.getId()).isEqualTo(outbound.getId());
        assertThat(staleProviderWrite.getId()).isEqualTo(outbound.getId());
        assertThat(staleProviderWrite.getStatus()).isEqualTo(SmsStatus.DELIVERED);
        Long count = entityManager.createQuery(
                        "SELECT COUNT(t) FROM SmsTransaction t WHERE t.providerType = :providerType "
                                + "AND t.providerMessageId = :providerMessageId",
                        Long.class
                )
                .setParameter("providerType", SmsProviderType.STUB)
                .setParameter("providerMessageId", "provider-early")
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("applies direct provider result after marking the row SENDING")
    void shouldApplyDirectProviderResult_afterMarkingSending() {
        SmsTransaction outbound = recorder.recordOutboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB,
                SmsConsentDecisionDto.permit()
        );
        Long id = outbound.getId();
        long queuedVersion = outbound.getVersion();
        entityManager.detach(outbound);

        SmsTransaction sending = recorder.markSending(
                outbound,
                java.util.Date.from(Instant.parse("2026-06-08T12:00:00Z"))
        );

        assertThat(sending.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(sending.getAttemptCount()).isEqualTo(1);
        assertThat(sending.getVersion()).isGreaterThan(queuedVersion);
        entityManager.detach(sending);

        SmsTransaction result = recorder.markProviderResult(
                sending,
                SmsProviderSendResultDto.accepted("provider-direct", SmsStatus.SENT)
        );

        assertThat(result.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(result.getProviderMessageId()).isEqualTo("provider-direct");
        entityManager.flush();
        entityManager.clear();
        SmsTransaction reloaded = entityManager.find(SmsTransaction.class, id);
        assertThat(reloaded.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(reloaded.getProviderMessageId()).isEqualTo("provider-direct");
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a second row with the same SMS-provider message id (unique key)")
    void shouldRejectDuplicate_whenSameProviderMessageId() {
        entityManager.persist(SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB, "dup-1", SmsStatus.DELIVERED, Instant.EPOCH, null, null, null
        )));
        entityManager.flush();

        // The duplicate insert fires inside persist() (IDENTITY id generation) or at flush, so both are
        // inside the lambda. Assert on the stack trace to stay agnostic to how Hibernate/JPA wraps the
        // underlying integrity-constraint violation.
        assertThatThrownBy(() -> {
            entityManager.persist(SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                    SmsProviderType.STUB, "dup-1", SmsStatus.DELIVERED, Instant.EPOCH, null, null, null
            )));
            entityManager.flush();
        }).hasStackTraceContaining("sms_transaction_provider_message_uidx");
    }

    @Test
    @DisplayName("does not duplicate an inbound message when the webhook is redelivered")
    void shouldNotDuplicateInbound_whenWebhookIsRedelivered() {
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                "inbound-1",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                Instant.EPOCH,
                null
        );

        SmsTransaction first = recorder.recordInboundMessage(webhook);
        SmsTransaction second = recorder.recordInboundMessage(webhook);

        assertThat(second.getId()).isEqualTo(first.getId());
        Long count = entityManager.createQuery(
                        "SELECT COUNT(t) FROM SmsTransaction t WHERE t.providerType = :providerType "
                                + "AND t.providerMessageId = :providerMessageId",
                        Long.class
                )
                .setParameter("providerType", SmsProviderType.VOIPMS)
                .setParameter("providerMessageId", "inbound-1")
                .getSingleResult();
        assertThat(count).isEqualTo(1L);
    }
}
