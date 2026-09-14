package io.github.carlos_emr.carlos.sms.dto;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class SmsBoundarySafetyUnitTest {
    @Test
    void shouldRedactDiagnostics_whenRecordsContainSensitiveValues() {
        String sensitive = "SYNTHETIC_CONFIDENTIAL_VALUE";
        List<Object> records = List.of(
                SmsSendCommand.patientMessage(1234567, "+14165551212", sensitive, "999998"),
                new SmsInboundWebhookDto(SmsProviderType.STUB, "id", "+14165551212", "+14165550000",
                        sensitive, Instant.EPOCH, Map.of("payload", sensitive)),
                new SmsDeliveryWebhookDto(SmsProviderType.STUB, "id", SmsStatus.FAILED, Instant.EPOCH,
                        sensitive, sensitive, Map.of("payload", sensitive)),
                SmsProviderSendResultDto.failed(sensitive, sensitive),
                SmsProviderMessageStatusDto.unavailable(sensitive, sensitive),
                SmsConsentDecisionDto.blocked(SmsStatus.CONSENT_BLOCKED, sensitive, sensitive),
                SmsSendResultDto.validationFailed(List.of(sensitive)));
        assertThat(records).allSatisfy(value -> assertThat(value.toString())
                .contains("redacted").doesNotContain(sensitive, "14165551212", "1234567"));
    }

    @Test
    void shouldRejectInternalQueueStates_whenConstructingDeliveryCallback() {
        for (SmsStatus status : new SmsStatus[]{null, SmsStatus.QUEUED, SmsStatus.SENDING, SmsStatus.RECEIVED,
                SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED}) {
            assertThatThrownBy(() -> new SmsDeliveryWebhookDto(SmsProviderType.STUB, "id", status,
                    Instant.EPOCH, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldRejectContradictoryState_whenProviderResultIsNotAccepted() {
        assertThatThrownBy(() -> new SmsProviderSendResultDto(false, "id", SmsStatus.DELIVERED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SmsProviderSendResultDto.uncertain("TIMEOUT").status()).isEqualTo(SmsStatus.SENDING);
    }

    @Test
    void shouldRejectOversizedIdentifiers_whenTruncationWouldBreakCorrelation() {
        String oversized = "x".repeat(129);
        assertThatThrownBy(() -> SmsProviderSendResultDto.accepted(oversized, SmsStatus.SENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsInboundWebhookDto(SmsProviderType.STUB, oversized, null, null,
                "synthetic", Instant.EPOCH, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsDeliveryWebhookDto(SmsProviderType.STUB, "id", SmsStatus.DELIVERED,
                Instant.EPOCH, null, null, "x".repeat(65), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
