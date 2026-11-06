package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
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
    private static final SmsProviderSettings NO_SETTINGS = SmsProviderSettings.none(SmsProviderType.STUB);

    @Test
    @DisplayName("stub SMS provider returns stable non-blank SMS provider ids")
    void shouldReturnStableProviderId_whenCommandRepeats() {
        StubSmsProviderClient client = new StubSmsProviderClient();
        SmsSendCommand command = SmsSendCommand.patientMessage(123, "(416) 555-1212", "Appointment reminder", "999998");

        SmsProviderSendResultDto first = client.send(command, "sms-transaction-1", NO_SETTINGS);
        SmsProviderSendResultDto second = client.send(command, "sms-transaction-1", NO_SETTINGS);

        assertThat(first.accepted()).isTrue();
        assertThat(first.status()).isEqualTo(SmsStatus.SENT);
        assertThat(first.providerMessageId()).startsWith("stub-");
        assertThat(first.providerMessageId()).isEqualTo(second.providerMessageId());
    }

    @Test
    @DisplayName("stub SMS provider derives a per-transaction-unique id from the client reference")
    void shouldReturnUniqueProviderId_whenClientReferenceDiffers() {
        StubSmsProviderClient client = new StubSmsProviderClient();
        SmsSendCommand command = SmsSendCommand.patientMessage(123, "(416) 555-1212", "Appointment reminder", "999998");

        // Same body+number, different sms_transaction rows: ids must differ so the second send does not
        // collide on the (provider_type, provider_message_id) unique key.
        SmsProviderSendResultDto first = client.send(command, "sms-transaction-1", NO_SETTINGS);
        SmsProviderSendResultDto second = client.send(command, "sms-transaction-2", NO_SETTINGS);

        assertThat(first.providerMessageId()).startsWith("stub-");
        assertThat(second.providerMessageId()).startsWith("stub-");
        assertThat(first.providerMessageId()).isNotEqualTo(second.providerMessageId());
    }

    @Test
    @DisplayName("stub callback validation fails closed when no secret is configured")
    void shouldRejectCallback_whenSecretIsBlank() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.validateCallback(request(Map.of("X-Carlos-Sms-Stub-Secret", "anything")), " ", NO_SETTINGS))
                .isFalse();
        assertThat(client.validateCallback(request(Map.of()), null, NO_SETTINGS)).isFalse();
        assertThat(client.acceptedCallbacks()).as("the stub sends no callbacks, so CARLOS never checks one").isEmpty();
    }

    @Test
    @DisplayName("stub callback validation requires matching secret header")
    void shouldValidateCallback_whenSecretIsConfigured() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.validateCallback(request(Map.of("x-carlos-sms-stub-secret", "secret")), "secret", NO_SETTINGS))
                .as("header names match ignoring case").isTrue();
        assertThat(client.validateCallback(request(Map.of("X-Carlos-Sms-Stub-Secret", "wrong")), "secret", NO_SETTINGS))
                .isFalse();
        assertThat(client.validateCallback(null, "secret", NO_SETTINGS)).isFalse();
    }

    @Test
    @DisplayName("stub webhook parsing stays empty for both missing and placeholder payloads")
    void shouldReturnEmptyWebhookDto_whenParsingStubPayload() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.parseInboundWebhook(null)).isEmpty();
        assertThat(client.parseInboundWebhook(request(Map.of()))).isEmpty();
        assertThat(client.parseDeliveryWebhook(null)).isEmpty();
        assertThat(client.parseDeliveryWebhook(request(Map.of()))).isEmpty();
    }

    @Test
    @DisplayName("default SMS provider status lookup consumes client and SMS provider references")
    void shouldReturnUnavailableStatus_whenDefaultStatusLookupIsUsed() {
        StubSmsProviderClient client = new StubSmsProviderClient();

        assertThat(client.lookupMessageStatus("sms-transaction-1", null, NO_SETTINGS).status())
                .isEqualTo(SmsProviderMessageStatusDto.Status.UNAVAILABLE);
        assertThat(client.lookupMessageStatus(" ", "provider-1", NO_SETTINGS).status())
                .isEqualTo(SmsProviderMessageStatusDto.Status.UNAVAILABLE);
        assertThatThrownBy(() -> client.lookupMessageStatus(null, "provider-1", NO_SETTINGS))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clientReferenceId");
        assertThatThrownBy(() -> client.lookupMessageStatus(" ", null, NO_SETTINGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientReferenceId or providerMessageId");
    }

    private static SmsWebhookRequest request(Map<String, String> headers) {
        return new SmsWebhookRequest("POST", Map.of(), headers, "{}");
    }
}
