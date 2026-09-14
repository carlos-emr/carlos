package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("service")
class SmsDefaultProviderResolverUnitTest {

    @Test
    @DisplayName("selects the configured provider for outbound sends")
    void shouldSelectConfiguredProvider_whenPropertyIsSet() {
        SmsDefaultProviderResolver selector = new SmsDefaultProviderResolver(() -> "VOIPMS");

        assertThat(selector.configuredDefault()).isEqualTo(SmsProviderType.VOIPMS);
    }

    @Test
    @DisplayName("parses the configured provider case-insensitively and trims whitespace")
    void shouldParseProvider_whenPropertyHasMixedCaseAndPadding() {
        SmsDefaultProviderResolver selector = new SmsDefaultProviderResolver(() -> "  Cloudli  ");

        assertThat(selector.configuredDefault()).isEqualTo(SmsProviderType.CLOUDLI);
    }

    @Test
    @DisplayName("defaults to STUB when no provider is configured")
    void shouldDefaultToStub_whenPropertyIsMissingOrBlank() {
        assertThat(new SmsDefaultProviderResolver(() -> null).configuredDefault()).isEqualTo(SmsProviderType.STUB);
        assertThat(new SmsDefaultProviderResolver(() -> "   ").configuredDefault()).isEqualTo(SmsProviderType.STUB);
    }

    @Test
    @DisplayName("rejects an unknown configured provider without silently using STUB")
    void shouldRejectConfiguration_whenPropertyIsUnknown() {
        SmsDefaultProviderResolver selector = new SmsDefaultProviderResolver(() -> "TWILIO");

        assertThatThrownBy(selector::configuredDefault).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sms.provider.default");
    }
}
