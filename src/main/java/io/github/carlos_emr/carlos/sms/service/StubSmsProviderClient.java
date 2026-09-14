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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class StubSmsProviderClient implements SmsProviderClient {
    @Override
    public SmsProviderType providerType() {
        return SmsProviderType.STUB;
    }

    @Override
    public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
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
    public boolean validateCallback(String payload, Map<String, String> headers, String secret) {
        // Fail closed: with no configured secret a callback cannot be authenticated, so it must be
        // rejected rather than trusted. Recording inbound/delivery callbacks persists rows, so an
        // unauthenticated callback would otherwise let an unauthenticated caller inject SMS records.
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (payload == null || headers == null) {
            return false;
        }
        String provided = headers.get("X-Carlos-Sms-Stub-Secret");
        return provided != null && constantTimeEquals(secret, provided);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers) {
        // The outbound-only stub has no callback payload format to parse.
        return Optional.empty();
    }

    @Override
    public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
        // The outbound-only stub has no callback payload format to parse.
        return Optional.empty();
    }
}
