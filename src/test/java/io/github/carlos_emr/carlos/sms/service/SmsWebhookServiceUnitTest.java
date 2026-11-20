package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsWebhookServiceUnitTest {
    private static final SmsWebhookRequest REQUEST = new SmsWebhookRequest("POST", Map.of(),
            Map.of("X-Test", "value"), "{\"message\":\"reply\"}");
    private static final SmsProviderSettings SETTINGS = SmsProviderSettings.of(SmsProviderType.STUB, null,
            Map.of("api_user", "fake-user"));

    @Mock
    private SmsProviderClient providerClient;

    @Mock
    private SmsTransactionService transactionRecorder;

    @Mock
    private SmsConfigService configService;

    @Test
    @DisplayName("authenticated callbacks cannot write records for another SMS provider")
    void shouldRejectCrossProviderData_whenParserReturnsDifferentProvider() {
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseInboundWebhook(REQUEST)).thenReturn(Optional.of(
                new SmsInboundWebhookDto(SmsProviderType.VOIPMS, "other-provider-id", null, null,
                        "synthetic", Instant.EPOCH, null)));

        assertThatThrownBy(() -> processor.processInboundWebhook(SmsProviderType.STUB, REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("processInboundWebhook checks the callback with the saved secret and settings, then records it")
    void shouldRecordInboundWebhook_whenCallbackIsValidAndParsed() {
        SmsInboundWebhookDto webhook = new SmsInboundWebhookDto(SmsProviderType.STUB, "provider-1", "+14165551212",
                "+14165550000", "reply", Instant.EPOCH, null);
        SmsTransaction transaction = SmsTransaction.inboundMessage(webhook);
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseInboundWebhook(REQUEST)).thenReturn(Optional.of(webhook));
        when(transactionRecorder.recordInboundMessage(webhook)).thenReturn(transaction);

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).containsSame(transaction);
    }

    @Test
    @DisplayName("processDeliveryWebhook validates, parses, and records delivery callbacks")
    void shouldRecordDeliveryWebhook_whenCallbackIsValidAndParsed() {
        SmsDeliveryWebhookDto webhook = new SmsDeliveryWebhookDto(SmsProviderType.STUB, "provider-1",
                SmsStatus.DELIVERED, Instant.EPOCH, null, null, null);
        SmsTransaction transaction = SmsTransaction.deliveryEvent(webhook);
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseDeliveryWebhook(REQUEST)).thenReturn(Optional.of(webhook));
        when(transactionRecorder.recordDeliveryEvent(webhook)).thenReturn(transaction);

        assertThat(processor.processDeliveryWebhook(SmsProviderType.STUB, REQUEST)).containsSame(transaction);
    }

    @Test
    @DisplayName("a failure report is recorded with a CARLOS code and its fixed message, never the provider's words")
    void shouldRecordCarlosCode_whenFailureReportUsesProvidersOwnWording() {
        SmsDeliveryWebhookDto webhook = new SmsDeliveryWebhookDto(SmsProviderType.STUB, "provider-1",
                SmsStatus.FAILED, Instant.EPOCH, "carrier said 4165551212 is unreachable", "free text", null);
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseDeliveryWebhook(REQUEST)).thenReturn(Optional.of(webhook));

        processor.processDeliveryWebhook(SmsProviderType.STUB, REQUEST);

        ArgumentCaptor<SmsDeliveryWebhookDto> recorded = ArgumentCaptor.forClass(SmsDeliveryWebhookDto.class);
        verify(transactionRecorder).recordDeliveryEvent(recorded.capture());
        assertThat(recorded.getValue())
                .extracting(SmsDeliveryWebhookDto::errorCode, SmsDeliveryWebhookDto::errorMessage)
                .containsExactly("REJECTED_OTHER", SmsProviderErrorCode.REJECTED_OTHER.message());
    }

    @Test
    @DisplayName("processDeliveryWebhook stops before parsing invalid callbacks")
    void shouldNotParseWebhook_whenCallbackValidationFails() {
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(false);

        assertThat(processor.processDeliveryWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

        verify(providerClient, never()).parseDeliveryWebhook(any());
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("processInboundWebhook returns empty when SMS provider parser does not produce a DTO")
    void shouldReturnEmpty_whenInboundParserDoesNotProduceWebhookDto() {
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseInboundWebhook(REQUEST)).thenReturn(Optional.empty());

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a callback for a provider that is not the clinic's active one is refused unchecked")
    void shouldRefuseCallback_whenProviderIsNotActive() {
        SmsWebhookService processor = processor(() -> SmsProviderType.VOIPMS);

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

        verify(providerClient, never()).validateCallback(any(), any(), any());
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a kind of callback the provider does not declare is refused unchecked")
    void shouldRefuseCallback_whenProviderDoesNotSendThatKind() {
        SmsWebhookService processor = processor();
        when(providerClient.acceptedCallbacks()).thenReturn(Set.of(SmsCallbackKind.INBOUND));

        assertThat(processor.processDeliveryWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

        verify(providerClient, never()).validateCallback(any(), any(), any());
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a callback naming a provider with no installed client is refused without an error")
    void shouldRefuseCallback_whenProviderIsNotInstalled() {
        SmsWebhookService processor = processor();

        assertThat(processor.processInboundWebhook(SmsProviderType.VOIPMS, REQUEST)).isEmpty();
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a callback is refused while the saved webhook secret cannot be read")
    void shouldRefuseCallback_whenWebhookSecretCannotBeRead() {
        SmsWebhookService processor = processor();
        when(configService.webhookSecret())
                .thenThrow(new SmsProviderNotReadyException("the stored SMS webhook secret cannot be decrypted"));

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

        verify(providerClient, never()).validateCallback(any(), any(), any());
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a provider check that throws refuses the callback without passing its message on")
    void shouldRefuseCallback_whenProviderCheckThrows() {
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS))
                .thenThrow(new NumberFormatException("For input string: \"4165551212x\""));

        try (LogCapture logs = LogCapture.forLogger(SmsWebhookService.class)) {
            assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("4165551212");
                assertThat(event.getThrown()).isNull();
            });
        }
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a provider parser that throws records nothing and passes nothing on")
    void shouldRecordNothing_whenProviderParserThrows() {
        SmsWebhookService processor = processor();
        when(providerClient.validateCallback(REQUEST, "secret", SETTINGS)).thenReturn(true);
        when(providerClient.parseDeliveryWebhook(REQUEST)).thenThrow(new IllegalArgumentException("bad 4165551212"));

        try (LogCapture logs = LogCapture.forLogger(SmsWebhookService.class)) {
            assertThat(processor.processDeliveryWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();

            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("4165551212");
                assertThat(event.getThrown()).isNull();
            });
        }
        verifyNoInteractions(transactionRecorder);
    }

    @Test
    @DisplayName("a callback is refused while the active provider cannot be determined")
    void shouldRefuseCallback_whenActiveProviderCannotBeDetermined() {
        SmsWebhookService processor = processor(() -> {
            throw new IllegalStateException("Invalid sms.provider.default; configure a supported SMS provider.");
        });

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();
        verify(providerClient, never()).validateCallback(any(), any(), any());
    }

    @Test
    @DisplayName("a callback is refused while the provider's saved credentials cannot be read")
    void shouldRefuseCallback_whenProviderSettingsCannotBeRead() {
        SmsWebhookService processor = processor();
        when(configService.providerSettings(SmsProviderType.STUB))
                .thenThrow(new SmsProviderNotReadyException("a stored SMS provider credential cannot be decrypted"));

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();
        verify(providerClient, never()).validateCallback(any(), any(), any());
    }

    @Test
    @DisplayName("with no webhook secret saved, the provider's check is handed none and must fail closed")
    void shouldHandCheckNoSecret_whenNoneIsSaved() {
        SmsWebhookService processor = processor();
        when(configService.webhookSecret()).thenReturn(Optional.empty());
        when(providerClient.validateCallback(REQUEST, null, SETTINGS)).thenReturn(false);

        assertThat(processor.processInboundWebhook(SmsProviderType.STUB, REQUEST)).isEmpty();
        verify(providerClient).validateCallback(REQUEST, null, SETTINGS);
    }

    private SmsWebhookService processor() {
        return processor(() -> SmsProviderType.STUB);
    }

    private SmsWebhookService processor(Supplier<SmsProviderType> active) {
        lenient().when(providerClient.providerType()).thenReturn(SmsProviderType.STUB);
        lenient().when(providerClient.acceptedCallbacks())
                .thenReturn(Set.of(SmsCallbackKind.INBOUND, SmsCallbackKind.DELIVERY));
        lenient().when(configService.webhookSecret()).thenReturn(Optional.of("secret"));
        lenient().when(configService.providerSettings(SmsProviderType.STUB)).thenReturn(SETTINGS);
        return new SmsWebhookService(new SmsProviderClientResolver(List.of(providerClient)), transactionRecorder,
                active, configService);
    }
}
