package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Chooses the {@link SmsProviderType} for an outbound send so provider selection lives in exactly one
 * place instead of being hardcoded at every send entry point.
 * <p>
 * Selection is the configured default ({@code sms.provider.default}, falling back to {@link
 * SmsProviderType#STUB} when unset/invalid). Onboarding a provider that is already in the enum is then
 * a config change plus a new {@link SmsProviderClient} bean — no edits to the send/queue/worker code.
 * Per-message routing (a per-command override) can be added here when a use case needs it.
 */
@Service
public class SmsProviderSelector {
    static final String DEFAULT_PROVIDER_PROPERTY = "sms.provider.default";
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final Supplier<String> defaultProviderProperty;

    @Autowired
    public SmsProviderSelector() {
        this(() -> CarlosProperties.getInstance().getProperty(DEFAULT_PROVIDER_PROPERTY));
    }

    SmsProviderSelector(Supplier<String> defaultProviderProperty) {
        this.defaultProviderProperty = defaultProviderProperty == null ? () -> null : defaultProviderProperty;
    }

    /**
     * The provider type to use for an outbound send. Currently the configured default for all sends.
     */
    public SmsProviderType configuredDefault() {
        return parse(defaultProviderProperty.get());
    }

    private static SmsProviderType parse(String value) {
        if (value == null || value.isBlank()) {
            return SmsProviderType.STUB;
        }
        try {
            return SmsProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn(
                    "Unknown {} value '{}'; defaulting outbound SMS to STUB until a valid provider is configured.",
                    DEFAULT_PROVIDER_PROPERTY,
                    value
            );
            return SmsProviderType.STUB;
        }
    }
}
