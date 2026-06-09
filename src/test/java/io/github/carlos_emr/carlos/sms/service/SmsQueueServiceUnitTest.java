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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsQueueServiceUnitTest {
    @Test
    @DisplayName("enqueue returns queued after validation and consent pass")
    void shouldQueueMessage_whenConsentAllows() {
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                recorder
        );

        SmsSendResultDto result = service.enqueue(
                SmsSendCommand.direct(
                        123,
                        "416-555-1212",
                        SmsRecipientPhoneType.HOME,
                        "Appointment reminder",
                        "999998"
                )
        );

        assertThat(result)
                .extracting(SmsSendResultDto::accepted, SmsSendResultDto::status)
                .containsExactly(true, SmsStatus.QUEUED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getProviderType,
                        SmsTransaction::getRecipientPhoneType
                )
                .containsExactly(SmsStatus.QUEUED, SmsProviderType.STUB, SmsRecipientPhoneType.HOME);
    }

    @Test
    @DisplayName("enqueue records consent blocks without sending")
    void shouldBlockMessage_whenConsentDeniesQueue() {
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.blocked(
                        SmsStatus.CONSENT_BLOCKED,
                        "CONSENT_MODEL_PENDING",
                        "SMS consent integration is pending"
                ),
                recorder
        );

        SmsSendResultDto result = service.enqueue(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998")
        );

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(recorder.transactions()).singleElement()
                .extracting(SmsTransaction::getStatus, SmsTransaction::getConsentReasonCode)
                .containsExactly(SmsStatus.CONSENT_BLOCKED, "CONSENT_MODEL_PENDING");
    }

    @Test
    @DisplayName("enqueue does not create transactions for invalid commands")
    void shouldSkipTransactionRecord_whenQueueValidationFails() {
        RecordingSmsTransactionRecorder recorder = new RecordingSmsTransactionRecorder();
        SmsQueueService service = new SmsQueueService(
                new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permit(),
                recorder
        );

        SmsSendResultDto result = service.enqueue(SmsSendCommand.direct(0, "not-a-phone", " ", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.transactions()).isEmpty();
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
        public List<SmsTransaction> claimDueOutboundQueue(SmsProviderType providerType, Date now, int limit) {
            return List.of();
        }

        private List<SmsTransaction> transactions() {
            return transactions;
        }
    }
}
