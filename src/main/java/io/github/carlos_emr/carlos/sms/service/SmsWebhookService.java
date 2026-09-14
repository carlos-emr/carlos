package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Service
public class SmsWebhookService {
    private final SmsProviderClientResolver providerResolver;
    private final SmsTransactionService transactionRecorder;

    public SmsWebhookService(
            SmsProviderClientResolver providerResolver,
            SmsTransactionService transactionRecorder
    ) {
        this.providerResolver = providerResolver;
        this.transactionRecorder = transactionRecorder;
    }

    public Optional<SmsTransaction> processInboundWebhook(
            SmsProviderType providerType,
            String payload,
            Map<String, String> headers,
            String secret
    ) {
        Objects.requireNonNull(providerType, "SMS webhook provider type is required");
        SmsProviderClient providerClient = providerResolver.resolve(providerType);
        if (!providerClient.validateCallback(payload, headers, secret)) {
            return Optional.empty();
        }
        return providerClient.parseInboundWebhook(payload, headers)
                .map(webhook -> {
                    requireMatchingProvider(providerType, webhook.providerType());
                    return transactionRecorder.recordInboundMessage(webhook);
                });
    }

    public Optional<SmsTransaction> processDeliveryWebhook(
            SmsProviderType providerType,
            String payload,
            Map<String, String> headers,
            String secret
    ) {
        Objects.requireNonNull(providerType, "SMS webhook provider type is required");
        SmsProviderClient providerClient = providerResolver.resolve(providerType);
        if (!providerClient.validateCallback(payload, headers, secret)) {
            return Optional.empty();
        }
        return providerClient.parseDeliveryWebhook(payload, headers)
                .map(webhook -> {
                    requireMatchingProvider(providerType, webhook.providerType());
                    return transactionRecorder.recordDeliveryEvent(webhook);
                });
    }
    private static void requireMatchingProvider(SmsProviderType expected, SmsProviderType actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("Parsed webhook does not match its authenticated SMS provider");
        }
    }
}
