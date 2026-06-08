package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsSendServiceUnitTest {
    @Test
    @DisplayName("send returns consent-blocked until consent integration is implemented")
    void shouldBlockSend_whenDeferredConsentServiceIsUsed() {
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                new DeferredSmsConsentService(),
                new SmsProviderResolver(List.of(new StubSmsProviderClient()))
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("send uses the provider once validation and consent pass")
    void shouldUseProvider_whenConsentAllows() {
        SmsConsentService allowConsent = command -> SmsConsentDecisionDto.permit();
        SmsSendService service = new SmsSendService(
                new SmsSendValidator(),
                allowConsent,
                new SmsProviderResolver(List.of(new StubSmsProviderClient()))
        );

        SmsSendResultDto result = service.send(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(result.providerMessageId()).startsWith("stub-");
    }
}
