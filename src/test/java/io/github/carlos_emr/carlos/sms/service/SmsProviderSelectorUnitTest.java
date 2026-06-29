package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsProviderSelectorUnitTest {

    @Test
    @DisplayName("selects the configured provider for outbound sends")
    void shouldSelectConfiguredProvider_whenPropertyIsSet() {
        SmsProviderSelector selector = new SmsProviderSelector(() -> "VOIPMS");

        assertThat(selector.configuredDefault()).isEqualTo(SmsProviderType.VOIPMS);
    }

    @Test
    @DisplayName("parses the configured provider case-insensitively and trims whitespace")
    void shouldParseProvider_whenPropertyHasMixedCaseAndPadding() {
        SmsProviderSelector selector = new SmsProviderSelector(() -> "  Cloudli  ");

        assertThat(selector.configuredDefault()).isEqualTo(SmsProviderType.CLOUDLI);
    }

    @Test
    @DisplayName("defaults to STUB when no provider is configured")
    void shouldDefaultToStub_whenPropertyIsMissingOrBlank() {
        assertThat(new SmsProviderSelector(() -> null).configuredDefault()).isEqualTo(SmsProviderType.STUB);
        assertThat(new SmsProviderSelector(() -> "   ").configuredDefault()).isEqualTo(SmsProviderType.STUB);
    }

    @Test
    @DisplayName("defaults to STUB when the configured provider is unknown")
    void shouldDefaultToStub_whenPropertyIsUnknown() {
        SmsProviderSelector selector = new SmsProviderSelector(() -> "TWILIO");

        assertThat(selector.configuredDefault()).isEqualTo(SmsProviderType.STUB);
    }
}
