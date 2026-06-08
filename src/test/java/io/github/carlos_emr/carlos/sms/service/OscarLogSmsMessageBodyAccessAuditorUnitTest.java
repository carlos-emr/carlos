package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class OscarLogSmsMessageBodyAccessAuditorUnitTest {
    @Test
    @DisplayName("audit data excludes SMS body and phone numbers")
    void shouldExcludePhi_whenBuildingAuditData() {
        OscarLogSmsMessageBodyAccessAuditor auditor = new OscarLogSmsMessageBodyAccessAuditor();
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        String data = ReflectionTestUtils.invokeMethod(auditor, "dataFor", transaction, "CARE_REVIEW");

        assertThat(data)
                .contains("demographicNo=123", "bodySha256=", "bodyLength=20", "reasonCode=CARE_REVIEW")
                .doesNotContain("Appointment reminder", "416-555-1212", "+14165551212");
    }
}
