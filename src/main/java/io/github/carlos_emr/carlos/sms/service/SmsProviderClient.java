package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
     * otherwise ambiguous outcome must return {@link SmsProviderSendResultDto#uncertain(String)}, with a fixed code
     * of the client's own (capitals, digits and underscores, never built from the provider's answer); it must
     * never be classified as a definite failure eligible for blind retry. An uncertain answer also ends that queue
     * run's sending, so it is for real doubt, not for a routine answer. A definite failure carries one
     * of the fixed CARLOS codes ({@code SmsProviderSendResultDto.failed(SmsProviderErrorCode)}); any other code is
     * recorded as {@code REJECTED_OTHER}, and the provider's own wording is never recorded.
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
                SmsProviderMessageStatusDto.LOOKUP_UNSUPPORTED_CODE,
                SmsProviderMessageStatusDto.LOOKUP_UNSUPPORTED_MESSAGE
        );
    }

    /**
     * Authenticate an SMS-provider callback before it is read or recorded. It is called only for the clinic's
     * active provider and a kind it declares in {@link #acceptedCallbacks()}.
     * <p>
     * Implementations MUST fail closed: if no secret/signing material is configured, or what the check needs is
     * missing from the request, return {@code false} rather than trusting the callback. Recording callbacks
     * persists rows, so a permissive default would let an unauthenticated caller inject SMS records.
     * Secret/signature comparisons should be constant-time (e.g. {@link java.security.MessageDigest#isEqual}).
     *
     * @param request       the callback as it reached CARLOS
     * @param webhookSecret the clinic's webhook secret from Administration &gt; SMS, or {@code null} when none is
     *                      saved; a provider that sends no signature (VoIP.ms) checks it as a token in the address
     * @param settings      this provider's settings, for a provider that signs callbacks with its own credentials
     */
    boolean validateCallback(SmsWebhookRequest request, String webhookSecret, SmsProviderSettings settings);

    /**
     * Read an authenticated callback about a text a patient sent. Empty when the request is not one. Its metadata
     * may hold only identifiers and status values.
     */
    Optional<SmsInboundWebhookDto> parseInboundWebhook(SmsWebhookRequest request);

    /**
     * Read an authenticated delivery report. Empty when the request is not one. Its metadata may hold only
     * identifiers and status values, never message text or phone numbers. A failure report should carry a
     * {@link io.github.carlos_emr.carlos.sms.SmsProviderErrorCode}; any other code is recorded as
     * {@code REJECTED_OTHER}, and the provider's own wording is never recorded.
     */
    Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(SmsWebhookRequest request);

    /**
     * The callbacks this provider sends. Defaults to none, as for the stub provider; a provider that reports
     * delivery only through {@link #lookupMessageStatus} (VoIP.ms) declares {@link SmsCallbackKind#INBOUND} only.
     */
    default Set<SmsCallbackKind> acceptedCallbacks() {
        return Set.of();
    }

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
