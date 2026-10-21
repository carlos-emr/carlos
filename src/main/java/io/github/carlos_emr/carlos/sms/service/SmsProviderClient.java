package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One SMS provider, such as VoIP.ms. Everything that differs between providers lives behind this interface: how a
 * text is sent and looked up, which logins and limits the provider needs, and how its callbacks are checked and
 * read. The send, queue, settings and callback code is shared and never names a provider, so adding one means a
 * new Spring bean implementing this interface, its name in {@link SmsProviderType}, and the label keys of its
 * credential fields in every {@code oscarResources} bundle.
 * <p>
 * Each clinic has one active provider, chosen in Administration &gt; SMS. Only that provider is handed the
 * clinic's sender number and logins ({@link SmsProviderSettings}).
 */
public interface SmsProviderClient {
    SmsProviderType providerType();

    /**
     * Send an outbound SMS through this SMS provider.
     * <p>
     * Provider adapters should include {@code clientReferenceId} in the SMS provider request when the
     * SMS provider supports client references/idempotency keys. Definite SMS provider rejections and
     * validation failures should return a failed
     * {@link SmsProviderSendResultDto}. Throwing a runtime exception should be reserved for unexpected
     * adapter defects or infrastructure failures the adapter cannot safely classify. A timeout or
     * otherwise ambiguous outcome must return {@link SmsProviderSendResultDto#uncertain(String)};
     * it must never be classified as a definite failure eligible for blind retry.
     * <p>
     * The recipient is always in E.164 form ({@code +14165550123}); a provider that wants another form converts
     * it. A provider whose settings lack something it needs (a login, the sender number) must return a failed
     * result without contacting the SMS provider. Network timeouts must be well under the queue's 5-minute
     * stuck-send check, or the check may look the text up while the send is still running.
     *
     * @param command           the text to send
     * @param clientReferenceId CARLOS's own reference for this text, unique per text
     * @param settings          the clinic's sender number and this provider's logins
     */
    SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId, SmsProviderSettings settings);

    /**
     * Look up SMS provider state for a previously attempted send.
     * <p>
     * Real SMS providers should prefer lookup by {@code clientReferenceId} when supported, because CARLOS
     * may crash after the SMS provider accepts a send but before the SMS provider message id is persisted.
     * Answer "not found" only when the SMS provider definitely has no such text: the queue then sends it
     * again. When that cannot be known (a provider that takes no client reference, and no message id was
     * saved), answer "unavailable", and the text is left for staff to check. Do the same, without contacting the
     * SMS provider, when {@code settings} lack something the lookup needs: a former provider is looked up with
     * no settings at all.
     *
     * @param clientReferenceId CARLOS's own reference for the text
     * @param providerMessageId the SMS provider's message id, or {@code null} when none was saved
     * @param settings          the clinic's sender number and this provider's logins
     */
    default SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId,
                                                            SmsProviderSettings settings) {
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

    /**
     * The login fields this provider needs (for example an API user and password), in the order
     * Administration &gt; SMS should show them. Values are stored encrypted in {@code sms_config} and never
     * shown back. Defaults to none, as for the stub provider.
     */
    default List<SmsCredentialField> credentialFields() {
        return List.of();
    }

    /** @return whether sending can be switched on only once a sender number is saved; defaults to no */
    default boolean requiresSenderNumber() {
        return false;
    }

    /** @return how many texts this provider may be sent per window; defaults to {@link SmsSendRateLimit#DEFAULT} */
    default SmsSendRateLimit sendRateLimit() {
        return SmsSendRateLimit.DEFAULT;
    }
}
