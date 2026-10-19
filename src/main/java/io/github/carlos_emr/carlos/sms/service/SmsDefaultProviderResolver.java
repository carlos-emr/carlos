package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Chooses the {@link SmsProviderType} for an outbound send so provider selection lives in exactly one
 * place instead of being hardcoded at every send entry point.
 * <p>
 * Selection is the configured default ({@code sms.provider.default}, falling back to {@link
 * SmsProviderType#STUB} when unset). Onboarding a provider that is already in the enum is then
 * a config change plus a new {@link SmsProviderClient} bean — no edits to the send/queue/worker code.
 * Per-message routing (a per-command override) can be added here when a use case needs it.
 */
@Service
public class SmsDefaultProviderResolver {
    static final String DEFAULT_PROVIDER_PROPERTY = "sms.provider.default";

    private final Supplier<String> defaultProviderProperty;
    private final Supplier<Optional<SmsProviderType>> storedProvider;

    @Autowired
    public SmsDefaultProviderResolver(SmsConfigService smsConfigService) {
        this(() -> CarlosProperties.getInstance().getProperty(DEFAULT_PROVIDER_PROPERTY),
                smsConfigService::storedProvider);
    }

    SmsDefaultProviderResolver(Supplier<String> defaultProviderProperty,
                               Supplier<Optional<SmsProviderType>> storedProvider) {
        this.defaultProviderProperty = defaultProviderProperty == null ? () -> null : defaultProviderProperty;
        this.storedProvider = storedProvider == null ? Optional::empty : storedProvider;
    }

    SmsDefaultProviderResolver(Supplier<String> defaultProviderProperty) {
        this(defaultProviderProperty, Optional::empty);
    }

    /**
     * The provider type to use for an outbound send: the one saved in Administration &gt; SMS, or
     * {@code sms.provider.default} while nothing is saved.
     */
    public SmsProviderType configuredDefault() {
        return storedProvider.get().orElseGet(() -> parse(defaultProviderProperty.get()));
    }

    private static SmsProviderType parse(String value) {
        if (value == null || value.isBlank()) {
            return SmsProviderType.STUB;
        }
        try {
            return SmsProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid sms.provider.default; configure a supported SMS provider.");
        }
    }
}
