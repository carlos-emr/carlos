package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("service")
class SmsRetryPolicyUnitTest {
    private static final Instant FIRST_ATTEMPT_AT = Instant.parse("2026-06-08T12:00:00Z");
    private static final Instant RETRY_SCHEDULED_AT = Instant.parse("2026-06-08T12:05:00Z");
    private static final Instant SECOND_ATTEMPT_AT = Instant.parse("2026-06-08T12:10:00Z");

    @Test
    @DisplayName("canRetry permits attempts below the configured maximum")
    void shouldAllowRetry_whenAttemptsRemain() {
        SmsRetryPolicy retryPolicy = new SmsRetryPolicy(3, Duration.ofMinutes(1), Duration.ofHours(1));
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(Date.from(Instant.parse("2026-06-08T12:00:00Z")));

        assertThat(retryPolicy.canRetry(transaction)).isTrue();
    }

    @Test
    @DisplayName("canRetry rejects attempts at the configured maximum")
    void shouldRejectRetry_whenMaximumAttemptsReached() {
        SmsRetryPolicy retryPolicy = new SmsRetryPolicy(2, Duration.ofMinutes(1), Duration.ofHours(1));
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(dateAt(FIRST_ATTEMPT_AT));
        transaction.markRetryScheduled(
                SmsProviderSendResultDto.failed("PROVIDER_ERROR", "Provider rejected"),
                dateAt(RETRY_SCHEDULED_AT)
        );
        transaction.markSending(dateAt(SECOND_ATTEMPT_AT));

        assertThat(retryPolicy.canRetry(transaction)).isFalse();
    }

    @Test
    @DisplayName("nextAttemptAt backs off from the failure timestamp")
    void shouldScheduleBackoff_whenAttemptFails() {
        SmsRetryPolicy retryPolicy = new SmsRetryPolicy(3, Duration.ofMinutes(1), Duration.ofHours(1));
        SmsTransaction transaction = queuedTransaction();
        transaction.markSending(Date.from(Instant.parse("2026-06-08T12:00:00Z")));
        Date failedAt = Date.from(Instant.parse("2026-06-08T12:30:00Z"));

        Date nextAttemptAt = retryPolicy.nextAttemptAt(transaction, failedAt);

        assertThat(nextAttemptAt).isEqualTo(Date.from(Instant.parse("2026-06-08T12:31:00Z")));
    }

    private static SmsTransaction queuedTransaction() {
        return SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
    }

    private static Date dateAt(Instant instant) {
        return Date.from(instant);
    }
}
