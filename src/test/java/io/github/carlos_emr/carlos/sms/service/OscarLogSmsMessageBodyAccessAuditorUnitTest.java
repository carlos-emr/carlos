package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class OscarLogSmsMessageBodyAccessAuditorUnitTest {
    @Mock
    private OscarLogDao oscarLogDao;

    @Test
    @DisplayName("audit data excludes SMS body and phone numbers")
    void shouldExcludePhi_whenBuildingAuditData() {
        OscarLogSmsMessageBodyAccessAuditor auditor = new OscarLogSmsMessageBodyAccessAuditor(oscarLogDao);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        String data = ReflectionTestUtils.invokeMethod(auditor, "dataFor", transaction, "CARE_REVIEW");

        assertThat(data)
                .contains("demographicNo=123", "bodySha256=", "bodyLength=20", "reasonCode=CARE_REVIEW")
                .doesNotContain("Appointment reminder", "416-555-1212", "+14165551212");
    }

    @Test
    @DisplayName("recordFullBodyRead persists and flushes OscarLog synchronously")
    void shouldPersistAndFlushLog_whenRecordingFullBodyRead() {
        OscarLogSmsMessageBodyAccessAuditor auditor = new OscarLogSmsMessageBodyAccessAuditor(oscarLogDao);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );

        auditor.recordFullBodyRead(transaction, null, "CARE_REVIEW");

        ArgumentCaptor<OscarLog> logCaptor = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(logCaptor.capture());
        verify(oscarLogDao).flush();
        assertThat(logCaptor.getValue())
                .extracting(OscarLog::getAction, OscarLog::getContent, OscarLog::getDemographicId)
                .containsExactly("SmsMessageBody.readFullBody", "sms_transaction", 123);
        assertThat(logCaptor.getValue().getData())
                .contains("bodySha256=", "bodyLength=20", "reasonCode=CARE_REVIEW")
                .doesNotContain("Appointment reminder", "416-555-1212", "+14165551212");
    }

    @Test
    @DisplayName("recordFullBodyRead propagates audit persistence failures")
    void shouldPropagateException_whenAuditFlushFails() {
        OscarLogSmsMessageBodyAccessAuditor auditor = new OscarLogSmsMessageBodyAccessAuditor(oscarLogDao);
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        doThrow(new IllegalStateException("audit flush failed")).when(oscarLogDao).flush();

        assertThatThrownBy(() -> auditor.recordFullBodyRead(transaction, null, "CARE_REVIEW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit flush failed");
    }
}
