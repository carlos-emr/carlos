package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checks and records SMS-provider callbacks. Only the clinic's active provider is heard, and only for the kinds of
 * callback it declares; the webhook secret and the provider's settings come from Administration &gt; SMS, never
 * from the caller. Every refusal answers empty and records nothing, so the callback endpoint (#3837) can answer
 * without saying why.
 */
@Service
public class SmsWebhookService {
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final SmsProviderClientResolver providerResolver;
    private final SmsTransactionService transactionRecorder;
    private final Supplier<SmsProviderType> activeProvider;
    private final SmsConfigService configService;

    @Autowired
    public SmsWebhookService(
            SmsProviderClientResolver providerResolver,
            SmsTransactionService transactionRecorder,
            SmsDefaultProviderResolver providerSelector,
            SmsConfigService configService
    ) {
        this(providerResolver, transactionRecorder, providerSelector::configuredDefault, configService);
    }

    SmsWebhookService(
            SmsProviderClientResolver providerResolver,
            SmsTransactionService transactionRecorder,
            Supplier<SmsProviderType> activeProvider,
            SmsConfigService configService
    ) {
        this.providerResolver = providerResolver;
        this.transactionRecorder = transactionRecorder;
        this.activeProvider = Objects.requireNonNull(activeProvider, "active SMS provider is required");
        this.configService = Objects.requireNonNull(configService, "SMS settings are required");
    }

    /**
     * @param providerType the provider the callback's address names
     * @param request      the callback as it reached CARLOS
     * @return the recorded incoming text, or empty when the callback was refused or is not an incoming text
     */
    public Optional<SmsTransaction> processInboundWebhook(SmsProviderType providerType, SmsWebhookRequest request) {
        return authenticatedClient(providerType, SmsCallbackKind.INBOUND, request)
                .flatMap(client -> parsed(SmsCallbackKind.INBOUND, () -> client.parseInboundWebhook(request)))
                .map(webhook -> {
                    requireMatchingProvider(providerType, webhook.providerType());
                    return transactionRecorder.recordInboundMessage(webhook);
                });
    }

    /**
     * @param providerType the provider the callback's address names
     * @param request      the callback as it reached CARLOS
     * @return the recorded delivery event, or empty when the callback was refused or is not a delivery report
     */
    public Optional<SmsTransaction> processDeliveryWebhook(SmsProviderType providerType, SmsWebhookRequest request) {
        return authenticatedClient(providerType, SmsCallbackKind.DELIVERY, request)
                .flatMap(client -> parsed(SmsCallbackKind.DELIVERY, () -> client.parseDeliveryWebhook(request)))
                .map(webhook -> {
                    requireMatchingProvider(providerType, webhook.providerType());
                    return transactionRecorder.recordDeliveryEvent(webhook.withCarlosErrorCode());
                });
    }

    /** @return the provider's client once the callback is one it sends and passes its check; otherwise empty */
    private Optional<SmsProviderClient> authenticatedClient(SmsProviderType providerType, SmsCallbackKind kind,
                                                            SmsWebhookRequest request) {
        Objects.requireNonNull(providerType, "SMS webhook provider type is required");
        Objects.requireNonNull(request, "SMS webhook request is required");
        if (!providerResolver.registeredProviderTypes().contains(providerType)) {
            return Optional.empty();
        }
        SmsProviderClient client = providerResolver.resolve(providerType);
        if (!client.acceptedCallbacks().contains(kind)) {
            return Optional.empty();
        }
        if (!isActive(providerType)) {
            LOGGER.warn("SMS {} callback refused: provider {} is not the clinic's active SMS provider.", kind,
                    providerType);
            return Optional.empty();
        }
        String webhookSecret;
        SmsProviderSettings settings;
        try {
            webhookSecret = configService.webhookSecret().orElse(null);
            settings = configService.providerSettings(providerType);
        } catch (SmsProviderNotReadyException e) {
            LOGGER.error("SMS {} callback refused: the saved SMS settings cannot be read ({}).", kind, e.getMessage());
            return Optional.empty();
        }
        boolean valid;
        try {
            valid = client.validateCallback(request, webhookSecret, settings);
        } catch (RuntimeException e) {
            // Provider code reading patient data: its message may quote the request, so only types and frames
            // are logged.
            LOGGER.warn("SMS {} callback check for provider {} failed with an error; nothing was recorded;{}", kind,
                    providerType, LogSafe.exceptionTrace(e));
            return Optional.empty();
        }
        if (!valid) {
            LOGGER.warn("SMS {} callback for provider {} failed its check; nothing was recorded.", kind, providerType);
            return Optional.empty();
        }
        return Optional.of(client);
    }

    /** Reads an authenticated callback; a parser that throws records nothing, and its message is not kept. */
    private static <T> Optional<T> parsed(SmsCallbackKind kind, Supplier<Optional<T>> parser) {
        try {
            return Objects.requireNonNull(parser.get(), "SMS webhook parser result is required");
        } catch (RuntimeException e) {
            LOGGER.warn("SMS {} callback could not be read; nothing was recorded;{}", kind, LogSafe.exceptionTrace(e));
            return Optional.empty();
        }
    }

    private boolean isActive(SmsProviderType providerType) {
        try {
            return activeProvider.get() == providerType;
        } catch (RuntimeException e) {
            LOGGER.warn("SMS active provider could not be determined. exceptionClass={}", e.getClass().getName());
            return false;
        }
    }

    private static void requireMatchingProvider(SmsProviderType expected, SmsProviderType actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("Parsed webhook does not match its authenticated SMS provider");
        }
    }
}
