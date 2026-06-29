package io.github.carlos_emr.carlos.sms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("unit")
@Tag("service")
class SmsProviderConfigurationValidatorUnitTest {
    private static final SmsProviderResolver STUB_ONLY_RESOLVER =
            new SmsProviderResolver(List.of(new StubSmsProviderClient()));

    @Test
    @DisplayName("reports the configured default as valid when a client is registered for it")
    void shouldReportValid_whenConfiguredDefaultHasClient() {
        SmsProviderConfigurationValidator validator = new SmsProviderConfigurationValidator(
                new SmsProviderSelector(() -> "STUB"),
                STUB_ONLY_RESOLVER
        );

        assertThat(validator.configuredDefaultHasRegisteredClient()).isTrue();
        assertThatCode(validator::validateConfiguredDefaultProvider).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reports the configured default as invalid when no client is registered for it")
    void shouldReportInvalid_whenConfiguredDefaultHasNoClient() {
        SmsProviderConfigurationValidator validator = new SmsProviderConfigurationValidator(
                new SmsProviderSelector(() -> "VOIPMS"),
                STUB_ONLY_RESOLVER
        );

        assertThat(validator.configuredDefaultHasRegisteredClient()).isFalse();
        // Startup validation logs an error but must not block context startup.
        assertThatCode(validator::validateConfiguredDefaultProvider).doesNotThrowAnyException();
    }
}
