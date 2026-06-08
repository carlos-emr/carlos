package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsSendServiceUnitTest {
    @Test
    @DisplayName("send returns consent-blocked until consent integration is implemented")
    void shouldBlockSend_whenDeferredConsentServiceIsUsed() {
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                new DeferredSmsConsentService(),
                new SmsProviderResolver(List.of(new StubSmsProviderClient())),
                recorder
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(result.providerMessageId()).isNull();
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "CONSENT_MODEL_PENDING");
    }

    @Test
    @DisplayName("send uses the provider once validation and consent pass")
    void shouldUseProvider_whenConsentAllows() {
        SmsConsentService allowConsent = command -> SmsConsentDecisionDto.permit();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderResolver(List.of(new StubSmsProviderClient())),
                recorder
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(result.providerMessageId()).startsWith("stub-");
        assertThat(recorder.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getStatus()).isEqualTo(SmsStatus.SENT);
                    assertThat(transaction.getProviderMessageId()).startsWith("stub-");
                });
    }

    @Test
    @DisplayName("send does not create a transaction for validation failures")
    void shouldSkipTransactionRecord_whenValidationFails() {
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                new SmsProviderResolver(List.of(new StubSmsProviderClient())),
                recorder
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(0, "not-a-phone", " ", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
    }

    @Test
    @DisplayName("send records provider exceptions as failed SMS transactions")
    void shouldMarkTransactionFailed_whenProviderThrows() {
        SmsConsentService allowConsent = command -> SmsConsentDecisionDto.permit();
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderResolver(List.of(new ThrowingSmsProviderClient())),
                recorder
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(result.messages()).containsExactly("SMS provider send failed.");
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode, SmsTransaction::getErrorMessage)
                .containsExactly(SmsStatus.FAILED, "PROVIDER_EXCEPTION", "SMS provider send failed.");
    }

    private static class RecordingSmsTransactionRecorder implements SmsTransactionRecorder {
        private final List<SmsTransaction> transactions = new ArrayList<>();

        @Override
        public SmsTransaction recordOutboundAttempt(SmsSendCommand command, SmsProviderType providerType) {
            SmsTransaction transaction = SmsTransaction.outboundAttempt(command, providerType);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction markConsentBlocked(SmsTransaction transaction, SmsConsentDecisionDto decision) {
            transaction.markConsentBlocked(decision);
            return transaction;
        }

        @Override
        public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
            transaction.markSending(attemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction markProviderResult(SmsTransaction transaction, SmsProviderSendResultDto providerResult) {
            transaction.markProviderResult(providerResult);
            return transaction;
        }

        @Override
        public SmsTransaction markRetryScheduled(
                SmsTransaction transaction,
                SmsProviderSendResultDto providerResult,
                Date nextAttemptAt
        ) {
            transaction.markRetryScheduled(providerResult, nextAttemptAt);
            return transaction;
        }

        @Override
        public SmsTransaction recordInboundMessage(SmsInboundWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public SmsTransaction recordDeliveryEvent(SmsDeliveryWebhookDto webhook) {
            SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<SmsTransaction> findDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            return transactions.stream()
                    .filter(transaction -> transaction.getStatus() == SmsStatus.QUEUED)
                    .limit(limit)
                    .toList();
        }

        private List<SmsTransaction> transactions() {
            return transactions;
        }
    }

    private static class ThrowingSmsProviderClient implements SmsProviderClient {
        @Override
        public SmsProviderType providerType() {
            return SmsProviderType.STUB;
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command) {
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
