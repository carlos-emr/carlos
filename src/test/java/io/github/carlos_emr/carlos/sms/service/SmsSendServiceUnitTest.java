package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsSendServiceUnitTest {
    @Test
    @DisplayName("send returns consent-blocked until consent integration is implemented")
    void shouldBlockSend_whenDeferredConsentServiceIsUsed() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                new DeferredSmsConsentService(),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(result.providerMessageId()).isNull();
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "CONSENT_MODEL_PENDING");
    }

    @Test
    @DisplayName("send uses the SMS provider once validation and consent pass")
    void shouldUseProvider_whenConsentAllows() {
        AtomicReference<SmsSendCommand> consentCommand = new AtomicReference<>();
        SmsConsentService allowConsent = command -> {
            consentCommand.set(command);
            return SmsConsentDecisionDto.permit();
        };
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(
                123,
                "416-555-1212",
                SmsRecipientPhoneType.WORK,
                "Appointment reminder",
                "999998"
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(result.providerMessageId()).startsWith("stub-");
        assertThat(consentCommand.get().recipientPhoneType()).isEqualTo(SmsRecipientPhoneType.WORK);
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
                    assertThat(transaction.getProviderMessageId()).startsWith("stub-");
                    assertThat(transaction.getRecipientPhoneType()).isEqualTo(SmsRecipientPhoneType.WORK);
                });
    }

    @Test
    @DisplayName("send marks the transaction sending before calling the SMS provider")
    void shouldMarkSendingBeforeProviderCall_whenConsentAllows() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))),
                recorder,
                providerType -> events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(events).containsExactly(
                "recordOutboundAttempt",
                "markSending",
                "tryAcquire",
                "providerSend",
                "markProviderResult"
        );
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
                    assertThat(transaction.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("send neither calls the SMS provider nor takes a rate-limit permit when the row is already claimed")
    void shouldSkipProviderSend_whenClaimConflictOccurs() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events) {
            @Override
            public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
                events.add("markSending");
                throw new SmsTransactionClaimConflictException(transaction.getId());
            }
        };
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                new SmsProviderClientResolver(List.of(new EventRecordingStubSmsProviderClient(events))),
                recorder,
                providerType -> events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(events).containsExactly(
                "recordOutboundAttempt",
                "markSending"
        );
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getAttemptCount()).isZero();
                });
    }

    @Test
    @DisplayName("send releases its claim, leaves the message queued and skips the SMS provider when rate limited")
    void shouldLeaveQueued_whenRateLimited() {
        List<String> events = new ArrayList<>();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService(events);
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> !events.add("tryAcquire"),
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(result.providerMessageId()).isNull();
        assertThat(events).containsExactly("recordOutboundAttempt", "markSending", "tryAcquire", "releaseClaim");
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.QUEUED);
                    assertThat(transaction.getProviderMessageId()).isNull();
                    assertThat(transaction.getAttemptCount()).isZero();
                    assertThat(transaction.getNextAttemptAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("send does not create a transaction for validation failures")
    void shouldSkipTransactionRecord_whenValidationFails() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(0, "not-a-phone", " ", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("send records uncertain SMS outcomes for status lookup")
    void shouldLeaveOutcomeUncertain_whenProviderThrows() {
        SmsConsentService allowConsent = command -> SmsConsentDecisionDto.permit();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of(new ThrowingSmsProviderClient())),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.messages())
                .containsExactly("SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(
                        SmsStatus.SENDING,
                        "DIRECT_PROVIDER_EXCEPTION",
                        "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually."
                );
    }

    @Test
    @DisplayName("send preserves unresolved provider outcomes for manual recovery")
    void shouldLeaveOutcomeUncertain_whenProviderResolutionThrows() {
        SmsConsentService allowConsent = command -> SmsConsentDecisionDto.permit();
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderClientResolver(List.of()),
                recorder,
                providerType -> true,
                new SmsDefaultProviderResolver(() -> "STUB")
        );

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(
                        SmsStatus.SENDING,
                        "DIRECT_PROVIDER_EXCEPTION",
                        "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually."
                );
    }

    @Test
    @DisplayName("consent is evaluated before any outbound attempt becomes visible")
    void shouldNotPersistAnAttempt_whenConsentEvaluationThrows() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService();
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> {
            assertThat(recorder.transactions()).isEmpty();
            throw new IllegalStateException("synthetic consent outage");
        }, new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder, type -> true,
                new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendCommand command = SmsSendCommand.patientMessage(123, "416-555-1212", "Synthetic message", "999998");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(command))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("direct send reports the persisted delivery webhook when it beats the send result")
    void shouldReturnDelivered_whenWebhookWinsTheWriteRace() {
        RecordingSmsTransactionService recorder = new RecordingSmsTransactionService() {
            @Override
            public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto result) {
                transaction.markDeliveryEvent(new SmsDeliveryWebhookDto(SmsProviderType.STUB,
                        "webhook-result", SmsStatus.DELIVERED, java.time.Instant.now(), null, null, null));
                return transaction;
            }
        };
        SmsSendService service = new SmsSendService(new SmsSendValidator(), command -> SmsConsentDecisionDto.permit(),
                new SmsProviderClientResolver(List.of(new StubSmsProviderClient())), recorder, type -> true,
                new SmsDefaultProviderResolver(() -> "STUB"));

        SmsSendResultDto result = service.send(SmsSendCommand.patientMessage(
                123, "416-555-1212", "Synthetic message", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(result.providerMessageId()).isEqualTo("webhook-result");
    }

    private static class RecordingSmsTransactionService implements SmsTransactionService {
        private final List<SmsTransaction> transactions = new ArrayList<>();
        private final List<String> events;
        private long nextTransactionId = 1L;

        private RecordingSmsTransactionService() {
            this(new ArrayList<>());
        }

        private RecordingSmsTransactionService(List<String> events) {
            this.events = events;
        }

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType,
                                                    SmsConsentDecisionDto decision) {
            events.add("recordOutboundAttempt");
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            assignId(transaction, nextTransactionId++);
            transactions.add(transaction);
            if (!decision.allowed()) {
                transaction.markConsentBlocked(decision);
            }
            return transaction;
        }

        @Override
        public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            events.add("markConsentBlocked");
            transaction.markConsentBlocked(decision);
            return transaction;
        }

        @Override
        public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
            events.add("markSending");
            transaction.markSending(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
            events.add("markProviderResult");
            transaction.markProviderResult(providerResult);
            return transaction;
        }

        @Override
        public SmsTransaction markRetryScheduled(
                SmsTransaction transaction,
                SmsProviderSendResultDto providerResult,
                Date nextAttemptAt
        ) {
            events.add("markRetryScheduled");
            transaction.markRetryScheduled(providerResult, nextAttemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction releaseClaim(SmsTransaction transaction, Date dueAt) {
            events.add("releaseClaim");
            transaction.markClaimReleased(dueAt);
            return transaction;
        }

        @Override
        public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
            events.add("recordInboundMessage");
            SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
            events.add("recordDeliveryEvent");
            SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            events.add("claimDueOutboundQueue");
            return transactions.stream()
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .peek(transaction -> transaction.markSending(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SmsTransaction> claimStaleSendingForRecovery(
                SmsProviderType providerType,
                Date staleBefore,
                Date recoveryAt,
                int limit
        ) {
            return List.of();
        }

        private List<SmsTransaction> transactions() {
            return transactions;
        }
    }

    private static void assignId(SmsTransaction transaction, long id) {
        try {
            Field idField = SmsTransaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, id);
            transaction.assignClientReferenceId(SmsTransaction.clientReferenceIdFor(id));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to assign SMS transaction id for test", e);
        }
    }

    private static class EventRecordingStubSmsProviderClient extends StubSmsProviderClient {
        private final List<String> events;

        private EventRecordingStubSmsProviderClient(List<String> events) {
            this.events = events;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            events.add("providerSend");
            return super.send(command, clientReferenceId);
        }
    }

    private static class ThrowingSmsProviderClient implements SmsProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.STUB;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
            throw new IllegalStateException("provider unavailable");
        }

        @Override
        public boolean validateCallback(String payload, Map<String, String> headers, String secret) {
            return false;
        }

        @Override
        public Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }

        @Override
        public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
            return Optional.empty();
        }
    }
}
