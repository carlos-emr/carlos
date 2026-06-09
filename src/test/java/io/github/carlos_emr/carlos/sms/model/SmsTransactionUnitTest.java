package io.github.carlos_emr.carlos.sms.model;

import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.SmsTransactionType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("model")
class SmsTransactionUnitTest {
    @Test
    @DisplayName("transaction type exposes only supported SMS workflows")
    void shouldExposeSupportedValues_whenReadingTransactionTypes() {
        assertThat(SmsTransactionType.values())
                .containsExactly(
                        SmsTransactionType.DIRECT,
                        SmsTransactionType.APPOINTMENT_REMINDER,
                        SmsTransactionType.SYSTEM_TEST
                );
    }

    @Test
    @DisplayName("recipient phone type exposes supported patient phone fields")
    void shouldExposeSupportedValues_whenReadingRecipientPhoneTypes() {
        assertThat(SmsRecipientPhoneType.values())
                .containsExactly(
                        SmsRecipientPhoneType.CELL,
                        SmsRecipientPhoneType.HOME,
                        SmsRecipientPhoneType.WORK,
                        SmsRecipientPhoneType.OTHER
                );
    }

    @Test
    @DisplayName("outbound transaction captures normalized send metadata")
    void shouldCreateOutboundTransaction_whenCommandIsValid() {
        SmsSendCommand command = SmsSendCommand.direct(
                123,
                "(416) 555-1212",
                SmsRecipientPhoneType.WORK,
                "Appointment reminder",
                "999998",
                1001
        );

        SmsTransaction transaction = SmsTransaction.outboundAttempt(command, SmsProviderType.VOIPMS);

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getDirection,
                        SmsTransaction::getProviderType,
                        SmsTransaction::getStatus,
                        SmsTransaction::getDemographicNo,
                        SmsTransaction::getRequestedByProviderNo,
                        SmsTransaction::getRequestedBySecurityNo,
                        SmsTransaction::getToPhoneNumber,
                        SmsTransaction::getRecipientPhoneType,
                        SmsTransaction::getMessageBody,
                        SmsTransaction::getMessageBodyLength
                )
                .containsExactly(
                        SmsDirection.OUTBOUND,
                        SmsProviderType.VOIPMS,
                        SmsStatus.QUEUED,
                        123,
                        "999998",
                        1001,
                        "+14165551212",
                        SmsRecipientPhoneType.WORK,
                        "Appointment reminder",
                        20
                );
        assertThat(transaction.getMessageBodySha256()).hasSize(64);
        assertThat(transaction.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("consent-blocked transaction stores block status and reason")
    void shouldMarkConsentBlocked_whenDecisionDeniesSend() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        transaction.markConsentBlocked(SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                "CONSENT_MODEL_PENDING",
                "SMS consent integration is pending"
        ));

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getConsentReasonCode,
                        SmsTransaction::getErrorMessage
                )
                .containsExactly(
                        SmsStatus.CONSENT_BLOCKED,
                        "CONSENT_MODEL_PENDING",
                        "SMS consent integration is pending"
                );
    }

    @Test
    @DisplayName("provider result updates status, provider id, and sent timestamp")
    void shouldMarkProviderResult_whenSendIsAccepted() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        transaction.markSending(Date.from(Instant.parse("2026-06-08T12:00:00Z")));

        transaction.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getProviderMessageId,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getNextAttemptAt
                )
                .containsExactly(SmsStatus.SENT, "provider-1", 1, null);
        assertThat(transaction.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("retry scheduling preserves failed provider details")
    void shouldScheduleRetry_whenProviderSendFails() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        Date attemptAt = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date nextAttemptAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));

        transaction.markSending(attemptAt);
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected message"),
                nextAttemptAt
        );

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getStatus,
                        SmsTransaction::getAttemptCount,
                        SmsTransaction::getLastAttemptAt,
                        SmsTransaction::getNextAttemptAt,
                        SmsTransaction::getErrorCode
                )
                .containsExactly(SmsStatus.QUEUED, 1, attemptAt, nextAttemptAt, "PROVIDER_ERROR");
    }

    @Test
    @DisplayName("transaction can rebuild send command for queued workers")
    void shouldCreateSendCommand_whenTransactionIsQueued() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                new SmsSendCommand(
                        123,
                        "416-555-1212",
                        SmsRecipientPhoneType.HOME,
                        "Appointment reminder",
                        null,
                        "999998",
                        1001,
                        456
                ),
                SmsProviderType.STUB
        );

        SmsSendCommand command = transaction.toSendCommand();

        assertThat(command)
                .extracting(
                        SmsSendCommand::demographicNo,
                        SmsSendCommand::recipientPhoneNumber,
                        SmsSendCommand::body,
                        SmsSendCommand::recipientPhoneType,
                        SmsSendCommand::requestedByProviderNo,
                        SmsSendCommand::requestedBySecurityNo,
                        SmsSendCommand::appointmentNo
                )
                .containsExactly(
                        123,
                        "+14165551212",
                        "Appointment reminder",
                        SmsRecipientPhoneType.HOME,
                        "999998",
                        1001,
                        456
                );
    }

    @Test
    @DisplayName("inbound webhook transaction tolerates missing provider timestamp")
    void shouldCreateInboundTransaction_whenWebhookTimestampIsMissing() {
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.VOIPMS,
                "provider-1",
                "416-555-1212",
                "647-555-1000",
                "Reply text",
                null,
                Map.of("messageType", "sms", "providerStatus", "received")
        );

        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);

        assertThat(transaction)
                .extracting(
                        SmsTransaction::getDirection,
                        SmsTransaction::getProviderType,
                        SmsTransaction::getStatus,
                        SmsTransaction::getFromPhoneNumber,
                        SmsTransaction::getToPhoneNumber,
                        SmsTransaction::getMessageBody
                )
                .containsExactly(
                        SmsDirection.INBOUND,
                        SmsProviderType.VOIPMS,
                        SmsStatus.RECEIVED,
                        "+14165551212",
                        "+16475551000",
                        "Reply text"
                );
        assertThat(transaction.getReceivedAt()).isNotNull();
        assertThat(transaction.getProviderMetadata())
                .isEqualTo("{\"messageType\":\"sms\",\"providerStatus\":\"received\"}");
    }

    @Test
    @DisplayName("delivery webhook updates outbound status and delivery timestamp")
    void shouldMarkDeliveryEvent_whenWebhookIsDelivered() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        transaction.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));
        Instant eventAt = Instant.parse("2026-06-08T12:00:00Z");

        transaction.markDeliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.DELIVERED,
                eventAt,
                null,
                null,
                Map.of("providerStatus", "delivered")
        ));

        assertThat(transaction)
                .extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.DELIVERED, "provider-1");
        assertThat(transaction.getDeliveredAt()).isEqualTo(Date.from(eventAt));
        assertThat(transaction.getProviderMetadata()).isEqualTo("{\"providerStatus\":\"delivered\"}");
    }
}
