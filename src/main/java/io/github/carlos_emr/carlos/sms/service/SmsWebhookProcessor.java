package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SmsWebhookProcessor {
    private final SmsProviderResolver providerResolver;
    private final SmsTransactionRecorder transactionRecorder;

    public SmsWebhookProcessor(
            SmsProviderResolver providerResolver,
            SmsTransactionRecorder transactionRecorder
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
        SmsProviderClient providerClient = providerResolver.resolve(providerType);
        if (!providerClient.validateCallback(payload, headers, secret)) {
            return Optional.empty();
        }
        return providerClient.parseInboundWebhook(payload, headers)
                .map(transactionRecorder::recordInboundMessage);
    }

    public Optional<SmsTransaction> processDeliveryWebhook(
            SmsProviderType providerType,
            String payload,
            Map<String, String> headers,
            String secret
    ) {
        SmsProviderClient providerClient = providerResolver.resolve(providerType);
        if (!providerClient.validateCallback(payload, headers, secret)) {
            return Optional.empty();
        }
        return providerClient.parseDeliveryWebhook(payload, headers)
                .map(transactionRecorder::recordDeliveryEvent);
    }
}
