package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.support.SmsAuditRedactor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

@Service
public class StubSmsProviderClient implements SmsProviderClient {
    @Override
    public SmsProviderType providerType() {
        return SmsProviderType.STUB;
    }

    @Override
    public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId,
                                         SmsProviderSettings settings) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(clientReferenceId, "clientReferenceId is required");
        if (clientReferenceId.isBlank()) {
            throw new IllegalArgumentException("clientReferenceId must not be blank");
        }
        // clientReferenceId is "sms-transaction-<id>", unique per sms_transaction row, so the stub
        // message id is unique per send and never collides on the provider-message-id unique key.
        return acceptedForSeed(clientReferenceId);
    }

    private static SmsProviderSendResultDto acceptedForSeed(String seed) {
        return SmsProviderSendResultDto.accepted("stub-" + SmsAuditRedactor.digest(seed, 64), SmsStatus.SENT);
    }

    @Override
    public boolean validateCallback(SmsWebhookRequest request, String webhookSecret, SmsProviderSettings settings) {
        // Fail closed: with no configured secret a callback cannot be authenticated, so it must be
        // rejected rather than trusted. Recording inbound/delivery callbacks persists rows, so an
        // unauthenticated callback would otherwise let an unauthenticated caller inject SMS records.
        // Not called while the stub declares no callbacks. Kept so the endpoint work (#3837) can have the stub
        // declare them for end-to-end tests without a real provider.
        if (webhookSecret == null || webhookSecret.isBlank() || request == null) {
            return false;
        }
        return request.header("X-Carlos-Sms-Stub-Secret")
                .map(provided -> constantTimeEquals(webhookSecret, provided))
                .orElse(false);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public Optional<SmsInboundWebhookDto> parseInboundWebhook(SmsWebhookRequest request) {
        // The outbound-only stub has no callback payload format to parse.
        return Optional.empty();
    }

    @Override
    public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(SmsWebhookRequest request) {
        // The outbound-only stub has no callback payload format to parse.
        return Optional.empty();
    }
}
