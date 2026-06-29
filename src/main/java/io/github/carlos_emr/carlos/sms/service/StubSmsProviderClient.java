package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.support.SmsAuditRedactor;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
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
    public SmsProviderSendResultDto send(SmsSendCommand command) {
        // No per-send client reference is available on this overload, so the id is derived from the
        // message itself and is only stable for direct/unit use. Production send paths
        // (SmsSendService, SmsQueueWorker) always call send(command, clientReferenceId) below, which
        // derives a per-transaction-unique id so resending an identical body to the same number does
        // not collide on the (provider_type, provider_message_id) unique key.
        String normalizedPhoneNumber = SmsPhoneNumbers.normalizeToE164(command.recipientPhoneNumber())
                .orElse(command.recipientPhoneNumber());
        return acceptedForSeed(normalizedPhoneNumber + "|" + command.body());
    }

    @Override
    public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
        Objects.requireNonNull(clientReferenceId, "clientReferenceId is required");
        // clientReferenceId is "sms-transaction-<id>", unique per sms_transaction row, so the stub
        // message id is unique per send and never collides on the provider-message-id unique key.
        return acceptedForSeed(clientReferenceId);
    }

    private static SmsProviderSendResultDto acceptedForSeed(String seed) {
        return SmsProviderSendResultDto.accepted("stub-" + SmsAuditRedactor.digest(seed, 12), SmsStatus.SENT);
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
        if (payload == null || headers == null) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers) {
        if (payload == null || headers == null) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
