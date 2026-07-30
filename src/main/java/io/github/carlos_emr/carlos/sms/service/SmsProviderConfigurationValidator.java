package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Validates SMS provider configuration at startup so a misconfiguration surfaces immediately instead
 * of silently failing every send.
 * <p>
 * If {@code sms.provider.default} names a provider with no registered {@link SmsProviderClient} (for
 * example {@code VOIPMS} before its client is deployed), every outbound row would be tagged with that
 * provider, fail resolution in the queue worker, and exhaust its retries as {@code FAILED}. Logging a
 * loud error at boot makes that operator mistake obvious rather than only visible after sends fail.
 */
@Component
public class SmsProviderConfigurationValidator {
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final SmsProviderSelector providerSelector;
    private final SmsProviderResolver providerResolver;

    public SmsProviderConfigurationValidator(
            SmsProviderSelector providerSelector,
            SmsProviderResolver providerResolver
    ) {
        this.providerSelector = providerSelector;
        this.providerResolver = providerResolver;
    }

    @PostConstruct
    public void validateConfiguredDefaultProvider() {
        SmsProviderType configuredDefault = providerSelector.configuredDefault();
        if (configuredDefaultHasRegisteredClient()) {
            LOGGER.info("SMS default provider {} resolved to a registered client.", configuredDefault);
            return;
        }
        LOGGER.error(
                "Configured default SMS provider {}={} has no registered SmsProviderClient; outbound SMS "
                        + "will be queued and then fail until a client for it is deployed. Registered providers: {}.",
                SmsProviderSelector.DEFAULT_PROVIDER_PROPERTY,
                configuredDefault,
                providerResolver.registeredProviderTypes()
        );
    }

    boolean configuredDefaultHasRegisteredClient() {
        return providerResolver.registeredProviderTypes().contains(providerSelector.configuredDefault());
    }
}
