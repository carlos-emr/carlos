package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("service")
class StubSmsProviderClientUnitTest {
    @Test
    @DisplayName("stub SMS provider returns stable non-blank SMS provider ids")
    void shouldReturnStableProviderId_whenCommandRepeats() {
        StubSmsProviderClient client = new StubSmsProviderClient();
        SmsSendCommand command = SmsSendCommand.direct(123, "(416) 555-1212", "Appointment reminder", "999998");

        SmsProviderSendResultDto first = client.send(command);
        SmsProviderSendResultDto second = client.send(command);

        assertThat(first.accepted()).isTrue();
        assertThat(first.status()).isEqualTo(SmsStatus.SENT);
        assertThat(first.providerMessageId()).startsWith("stub-");
        assertThat(first.providerMessageId()).isEqualTo(second.providerMessageId());
    }

    @Test
    @DisplayName("stub SMS provider derives a per-transaction-unique id from the client reference")
    void shouldReturnUniqueProviderId_whenClientReferenceDiffers() {
        StubSmsProviderClient client = new StubSmsProviderClient();
        SmsSendCommand command = SmsSendCommand.direct(123, "(416) 555-1212", "Appointment reminder", "999998");

        // Same body+number, different sms_transaction rows: ids must differ so the second send does not
        // collide on the (provider_type, provider_message_id) unique key.
        SmsProviderSendResultDto first = client.send(command, "sms-transaction-1");
        SmsProviderSendResultDto second = client.send(command, "sms-transaction-2");

        assertThat(first.providerMessageId()).startsWith("stub-");
        assertThat(second.providerMessageId()).startsWith("stub-");
        assertThat(first.providerMessageId()).isNotEqualTo(second.providerMessageId());
    }

    @Test
    @DisplayName("stub callback validation fails closed when no secret is configured")
    void shouldRejectCallback_whenSecretIsBlank() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.validateCallback("{}", Map.of("X-Carlos-Sms-Stub-Secret", "anything"), " ")).isFalse();
        assertThat(client.validateCallback("{}", Map.of(), null)).isFalse();
    }

    @Test
    @DisplayName("stub callback validation requires matching secret header")
    void shouldValidateCallback_whenSecretIsConfigured() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.validateCallback("{}", Map.of("X-Carlos-Sms-Stub-Secret", "secret"), "secret")).isTrue();
        assertThat(client.validateCallback("{}", Map.of("X-Carlos-Sms-Stub-Secret", "wrong"), "secret")).isFalse();
        assertThat(client.validateCallback(null, Map.of("X-Carlos-Sms-Stub-Secret", "secret"), "secret")).isFalse();
    }

    @Test
    @DisplayName("stub webhook parsing stays empty for both missing and placeholder payloads")
    void shouldReturnEmptyWebhookDto_whenParsingStubPayload() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.parseInboundWebhook(null, null)).isEmpty();
        assertThat(client.parseInboundWebhook("{}", Map.of())).isEmpty();
        assertThat(client.parseDeliveryWebhook(null, null)).isEmpty();
        assertThat(client.parseDeliveryWebhook("{}", Map.of())).isEmpty();
    }

    @Test
    @DisplayName("default SMS provider status lookup consumes client and SMS provider references")
    void shouldReturnUnavailableStatus_whenDefaultStatusLookupIsUsed() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.lookupMessageStatus("sms-transaction-1", null).status())
                .isEqualTo(SmsProviderMessageStatusDto.Status.UNAVAILABLE);
        assertThat(client.lookupMessageStatus(" ", "provider-1").status())
                .isEqualTo(SmsProviderMessageStatusDto.Status.UNAVAILABLE);
        assertThatThrownBy(() -> client.lookupMessageStatus(null, "provider-1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientReferenceId");
        assertThatThrownBy(() -> client.lookupMessageStatus(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientReferenceId or providerMessageId");
    }
}
