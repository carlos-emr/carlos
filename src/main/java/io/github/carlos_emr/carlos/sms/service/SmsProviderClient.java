package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface SmsProviderClient {
    SmsProviderType providerType();

    /**
     * Send an outbound SMS through this SMS provider.
     * <p>
     * Provider adapters should include {@code clientReferenceId} in the SMS provider request when the
     * SMS provider supports client references/idempotency keys. Expected SMS provider rejections, timeouts,
     * validation failures, and other SMS provider-classified send failures should return a failed
     * {@link SmsProviderSendResultDto}. Throwing a runtime exception should be reserved for unexpected
     * adapter defects or infrastructure failures the adapter cannot safely classify.
     */
    SmsProviderSendResultDto send(SmsSendCommand command);

    default SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
        Objects.requireNonNull(clientReferenceId, "clientReferenceId is required");
        return send(command);
    }

    /**
     * Look up SMS provider state for a previously attempted send.
     * <p>
     * Real SMS providers should prefer lookup by {@code clientReferenceId} when supported, because CARLOS
     * may crash after the SMS provider accepts a send but before the SMS provider message id is persisted.
     */
    default SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId) {
        String safeClientReferenceId = Objects.requireNonNull(clientReferenceId, "clientReferenceId is required");
        if (safeClientReferenceId.isBlank() && (providerMessageId == null || providerMessageId.isBlank())) {
            throw new IllegalArgumentException("clientReferenceId or providerMessageId is required");
        }
        return SmsProviderMessageStatusDto.unavailable(
                "PROVIDER_STATUS_LOOKUP_UNSUPPORTED",
                "SMS provider message status lookup is not implemented."
        );
    }

    /**
     * Authenticate an inbound SMS-provider callback before it is recorded.
     * <p>
     * Implementations MUST fail closed: if no secret/signing material is configured, or the payload or
     * headers are missing, return {@code false} rather than trusting the callback. Recording callbacks
     * persists rows, so a permissive default would let an unauthenticated caller inject SMS records.
     * Secret/signature comparisons should be constant-time (e.g. {@link java.security.MessageDigest#isEqual}).
     */
    boolean validateCallback(String payload, Map<String, String> headers, String secret);

    Optional<SmsInboundWebhookDto> parseInboundWebhook(String payload, Map<String, String> headers);

    Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(String payload, Map<String, String> headers);
}
