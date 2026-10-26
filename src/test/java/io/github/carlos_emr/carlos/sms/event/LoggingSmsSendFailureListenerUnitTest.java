package io.github.carlos_emr.carlos.sms.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("unit")
@Tag("service")
class LoggingSmsSendFailureListenerUnitTest {

    @Test
    @DisplayName("handles a terminal failure event without error")
    void shouldHandleEvent_whenSendFailedTerminally() {
        LoggingSmsSendFailureListener listener = new LoggingSmsSendFailureListener();
        SmsSendFailedEvent event = new SmsSendFailedEvent(42L, 123, "999998", 7, "PROVIDER_EXHAUSTED");

        assertThatCode(() -> listener.onSmsSendFailed(event)).doesNotThrowAnyException();
    }
}
