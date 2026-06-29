package io.github.carlos_emr.carlos.sms.event;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsSendFailedEventUnitTest {

    @Test
    @DisplayName("from excludes free-text provider error messages")
    void shouldExcludeFreeTextErrorMessage_whenCreatedFromTransaction() {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        transaction.markProviderResult(SmsProviderSendResultDto.failed(
                "PROVIDER_REJECTED",
                "Provider rejected SMS for patient Jane Smith"
        ));

        SmsSendFailedEvent event = SmsSendFailedEvent.from(transaction);

        assertThat(event.errorCode()).isEqualTo("PROVIDER_REJECTED");
        assertThat(event.demographicNo()).isEqualTo(123);
        assertThat(SmsSendFailedEvent.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .contains("errorCode")
                .doesNotContain("errorMessage");
    }
}
