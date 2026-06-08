package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsMessageBodyReaderUnitTest {
    @Test
    @DisplayName("readFullMessageBody audits access before returning body")
    void shouldAuditAccess_whenReadingFullBody() {
        RecordingAuditor auditor = new RecordingAuditor();
        SmsMessageBodyReader reader = new SmsMessageBodyReader(auditor);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        Optional<String> body = reader.readFullMessageBody(transaction, null, "CARE_REVIEW");

        assertThat(body).contains("Appointment reminder");
        assertThat(auditor.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.transaction()).isSameAs(transaction);
                    assertThat(record.reasonCode()).isEqualTo("CARE_REVIEW");
                });
    }

    @Test
    @DisplayName("readFullMessageBody audits access when body is missing")
    void shouldAuditAccess_whenFullBodyIsMissing() {
        RecordingAuditor auditor = new RecordingAuditor();
        SmsMessageBodyReader reader = new SmsMessageBodyReader(auditor);
        SmsTransaction transaction = SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                null,
                null,
                null,
                null,
                null
        ));

        Optional<String> body = reader.readFullMessageBody(transaction, null, "DELIVERY_REVIEW");

        assertThat(body).isEmpty();
        assertThat(auditor.records()).singleElement()
                .satisfies(record -> {
                    assertThat(record.transaction()).isSameAs(transaction);
                    assertThat(record.reasonCode()).isEqualTo("DELIVERY_REVIEW");
                });
    }

    private record AccessRecord(SmsTransaction transaction, String reasonCode) {
    }

    private static class RecordingAuditor implements SmsMessageBodyAccessAuditor {
        private final List<AccessRecord> records = new ArrayList<>();

        @Override
        public void recordFullBodyRead(SmsTransaction transaction, LoggedInInfo loggedInInfo, String reasonCode) {
            records.add(new AccessRecord(transaction, reasonCode));
        }

        private List<AccessRecord> records() {
            return records;
        }
    }
}
