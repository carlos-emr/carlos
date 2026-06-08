package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubSmsProviderClientUnitTest {
    @Test
    @DisplayName("stub provider returns stable non-blank provider ids")
    void sendReturnsStableProviderId() {
        StubSmsProviderClient client = new StubSmsProviderClient();
        SmsSendCommand command = SmsSendCommand.direct(123, "(416) 555-1212", "Appointment reminder", "999998");

        SmsProviderSendResultDto first = client.send(command);
        SmsProviderSendResultDto second = client.send(command);

        assertThat(first.accepted()).isTrue();
        assertThat(first.status()).isEqualTo(SmsStatus.SENT);
        assertThat(first.providerMessageId()).startsWith("stub-");
        assertThat(first.providerMessageId()).isEqualTo(second.providerMessageId());
    }
}
