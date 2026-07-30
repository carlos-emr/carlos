package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("service")
class SmsMessageBodyReaderUnitTest {
    @Test
    @DisplayName("readFullMessageBody audits access before returning body")
    void shouldAuditAccess_whenReadingFullBody() {
        RecordingAuthorizer authorizer = new RecordingAuthorizer();
        RecordingAuditor auditor = new RecordingAuditor();
        SmsMessageBodyReader reader = new SmsMessageBodyReader(authorizer, auditor);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        Optional<String> messageBody = reader.readFullMessageBody(transaction, null, "CARE_REVIEW");

        assertThat(messageBody).contains("Appointment reminder");
        assertThat(authorizer.records()).singleElement()
                .satisfies(captured -> assertThat(captured.transaction()).isSameAs(transaction));
        assertThat(auditor.records()).singleElement()
                .satisfies(captured -> {
                    assertThat(captured.transaction()).isSameAs(transaction);
                    assertThat(captured.reasonCode()).isEqualTo("CARE_REVIEW");
                });
    }

    @Test
    @DisplayName("readFullMessageBody audits access when body is missing")
    void shouldAuditAccess_whenFullBodyIsMissing() {
        RecordingAuthorizer authorizer = new RecordingAuthorizer();
        RecordingAuditor auditor = new RecordingAuditor();
        SmsMessageBodyReader reader = new SmsMessageBodyReader(authorizer, auditor);
        SmsTransaction transaction = SmsTransaction.deliveryEvent(new SmsDeliveryWebhookDto(
                SmsProviderType.STUB,
                "provider-1",
                null,
                null,
                null,
                null,
                null
        ));

        Optional<String> messageBody = reader.readFullMessageBody(transaction, null, "DELIVERY_REVIEW");

        assertThat(messageBody).isEmpty();
        assertThat(auditor.records()).singleElement()
                .satisfies(captured -> {
                    assertThat(captured.transaction()).isSameAs(transaction);
                    assertThat(captured.reasonCode()).isEqualTo("DELIVERY_REVIEW");
                });
    }

    @Test
    @DisplayName("readFullMessageBody fails before returning body when audit fails")
    void shouldNotReturnBody_whenAuditFails() {
        SmsMessageBodyReader reader = new SmsMessageBodyReader(
                new RecordingAuthorizer(),
                (transaction, loggedInInfo, reasonCode) -> {
                    throw new IllegalStateException("audit failed");
                }
        );
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        assertThatThrownBy(() -> reader.readFullMessageBody(transaction, null, "CARE_REVIEW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit failed");
    }

    @Test
    @DisplayName("readFullMessageBody denies access before audit")
    void shouldNotAuditOrReturnBody_whenAccessDenied() {
        RecordingAuditor auditor = new RecordingAuditor();
        SmsMessageBodyReader reader = new SmsMessageBodyReader(
                (transaction, loggedInInfo) -> {
                    throw new AccessDeniedException("_msgSMS", "r", transaction.getDemographicNo());
                },
                auditor
        );
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        assertThatThrownBy(() -> reader.readFullMessageBody(transaction, null, "CARE_REVIEW"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(auditor.records()).isEmpty();
    }

    private record AccessRecord(SmsTransaction transaction, String reasonCode) {
    }

    private record AuthorizationRecord(SmsTransaction transaction) {
    }

    private static class RecordingAuthorizer implements SmsMessageBodyAccessAuthorizer {
        private final List<AuthorizationRecord> records = new ArrayList<>();

        @Override
        public void assertCanReadFullBody(SmsTransaction transaction, LoggedInInfo loggedInInfo) {
            records.add(new AuthorizationRecord(transaction));
        }

        private List<AuthorizationRecord> records() {
            return records;
        }
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
