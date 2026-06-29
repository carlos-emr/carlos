package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsWebhookProcessorUnitTest {
    @Mock
    private SmsProviderClient providerClient;

    @Mock
    private SmsTransactionRecorder transactionRecorder;

    @Test
    @DisplayName("processInboundWebhook validates, parses, and records inbound callbacks")
    void shouldRecordInboundWebhook_whenCallbackIsValidAndParsed() {
        Map<String, String> headers = Map.of("X-Test", "value");
        String payload = "{\"message\":\"reply\"}";
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                "+14165551212",
                "+14165550000",
                "reply",
                Instant.EPOCH,
                null
        );
        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
        SmsWebhookProcessor processor = processor();

        when(providerClient.validateCallback(payload, headers, "secret")).thenReturn(true);
        when(providerClient.parseInboundWebhook(payload, headers)).thenReturn(Optional.of(webhook));
        when(transactionRecorder.recordInboundMessage(webhook)).thenReturn(transaction);

        Optional<SmsTransaction> result = processor.processInboundWebhook(
                SmsProviderType.STUB,
                payload,
                headers,
                "secret"
        );

        assertThat(result).containsSame(transaction);
        verify(transactionRecorder).recordInboundMessage(webhook);
    }

    @Test
    @DisplayName("processDeliveryWebhook validates, parses, and records delivery callbacks")
    void shouldRecordDeliveryWebhook_whenCallbackIsValidAndParsed() {
        Map<String, String> headers = Map.of("X-Test", "value");
        String payload = "{\"status\":\"delivered\"}";
        SmsDeliveryWebhookDto webhook = new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                SmsStatus.DELIVERED,
                Instant.EPOCH,
                null,
                null,
                null
        );
        SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
        SmsWebhookProcessor processor = processor();

        when(providerClient.validateCallback(payload, headers, "secret")).thenReturn(true);
        when(providerClient.parseDeliveryWebhook(payload, headers)).thenReturn(Optional.of(webhook));
        when(transactionRecorder.recordDeliveryEvent(webhook)).thenReturn(transaction);

        Optional<SmsTransaction> result = processor.processDeliveryWebhook(
                SmsProviderType.STUB,
                payload,
                headers,
                "secret"
        );

        assertThat(result).containsSame(transaction);
        verify(transactionRecorder).recordDeliveryEvent(webhook);
    }

    @Test
    @DisplayName("processDeliveryWebhook stops before parsing invalid callbacks")
    void shouldNotParseWebhook_whenCallbackValidationFails() {
        Map<String, String> headers = Map.of("X-Test", "value");
        String payload = "{\"status\":\"delivered\"}";
        SmsWebhookProcessor processor = processor();

        when(providerClient.validateCallback(payload, headers, "secret")).thenReturn(false);

        Optional<SmsTransaction> result = processor.processDeliveryWebhook(
                SmsProviderType.STUB,
                payload,
                headers,
                "secret"
        );

        assertThat(result).isEmpty();
        verify(providerClient, never()).parseDeliveryWebhook(payload, headers);
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("processInboundWebhook returns empty when SMS provider parser does not produce a DTO")
    void shouldReturnEmpty_whenInboundParserDoesNotProduceWebhookDto() {
        Map<String, String> headers = Map.of("X-Test", "value");
        String payload = "{\"message\":\"reply\"}";
        SmsWebhookProcessor processor = processor();

        when(providerClient.validateCallback(payload, headers, "secret")).thenReturn(true);
        when(providerClient.parseInboundWebhook(payload, headers)).thenReturn(Optional.empty());

        Optional<SmsTransaction> result = processor.processInboundWebhook(
                SmsProviderType.STUB,
                payload,
                headers,
                "secret"
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(transactionRecorder);
    }

    private SmsWebhookProcessor processor() {
        when(providerClient.providerType()).thenReturn(SmsProviderType.STUB);
        return new SmsWebhookProcessor(new SmsProviderResolver(List.of(providerClient)), transactionRecorder);
    }
}
