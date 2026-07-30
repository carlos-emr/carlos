package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.SmsTransactionType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsDeferredConsentServiceUnitTest {
    @Test
    @DisplayName("deferred consent blocks direct sends even when system-test override is enabled")
    void shouldBlockDirectSend_whenSystemTestOverrideIsEnabled() {
        SmsConsentDecisionDto decision = new DeferredSmsConsentService(() -> true)
                .evaluate(SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"));

        assertBlockedForPendingConsent(decision);
    }

    @Test
    @DisplayName("deferred consent blocks appointment reminders even when system-test override is enabled")
    void shouldBlockAppointmentReminder_whenSystemTestOverrideIsEnabled() {
        SmsConsentDecisionDto decision = new DeferredSmsConsentService(() -> true)
                .evaluate(appointmentReminderCommand());

        assertBlockedForPendingConsent(decision);
    }

    @Test
    @DisplayName("deferred consent blocks missing commands even when system-test override is enabled")
    void shouldBlockMissingCommand_whenSystemTestOverrideIsEnabled() {
        SmsConsentDecisionDto decision = new DeferredSmsConsentService(() -> true).evaluate(null);

        assertBlockedForPendingConsent(decision);
    }

    @Test
    @DisplayName("deferred consent blocks system-test sends when override is disabled")
    void shouldBlockSystemTest_whenSystemTestOverrideIsDisabled() {
        SmsConsentDecisionDto decision = new DeferredSmsConsentService(() -> false)
                .evaluate(systemTestCommand());

        assertBlockedForPendingConsent(decision);
    }

    @Test
    @DisplayName("deferred consent permits system-test sends when override is enabled")
    void shouldPermitSystemTest_whenSystemTestOverrideIsEnabled() {
        SmsConsentDecisionDto decision = new DeferredSmsConsentService(() -> true)
                .evaluate(systemTestCommand());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.blockedStatus()).isNull();
        assertThat(decision.reasonCode()).isNull();
        assertThat(decision.operatorMessage()).isNull();
    }

    private static SmsSendCommand appointmentReminderCommand() {
        return new SmsSendCommand(
                123,
                "416-555-1212",
                SmsRecipientPhoneType.CELL,
                "Appointment reminder",
                SmsTransactionType.APPOINTMENT_REMINDER,
                "999998",
                1001,
                456
        );
    }

    private static SmsSendCommand systemTestCommand() {
        return new SmsSendCommand(
                123,
                "416-555-1212",
                SmsRecipientPhoneType.CELL,
                "SMS system test",
                SmsTransactionType.SYSTEM_TEST,
                "999998",
                1001,
                null
        );
    }

    private static void assertBlockedForPendingConsent(SmsConsentDecisionDto decision) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockedStatus()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(decision.reasonCode()).isEqualTo("CONSENT_MODEL_PENDING");
        assertThat(decision.operatorMessage())
                .isEqualTo("SMS consent integration is pending consent audit model issue #2674.");
    }
}
