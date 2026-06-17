package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsQueueSchedulerUnitTest {
    private static final String BATCH_SIZE_PROPERTY = "sms.queue.scheduler.batchSize";
    private static final String DEFAULT_BATCH_SIZE = "60";

    @Mock
    private SmsQueueWorker smsQueueWorker;

    @Mock
    private CarlosProperties carlosProperties;

    @Test
    @DisplayName("runOnce clamps oversized batch sizes before processing")
    void shouldClampBatchSize_whenPropertyExceedsIntegerRange() {
        when(carlosProperties.getProperty(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE))
                .thenReturn(Long.toString((long) Integer.MAX_VALUE + 1L));
        when(smsQueueWorker.processDueMessages(Integer.MAX_VALUE)).thenReturn(7);

        int processed = runOnceWithProperties();

        assertThat(processed).isEqualTo(7);
        verify(smsQueueWorker).processDueMessages(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("runOnce uses minimum batch size for non-positive values")
    void shouldUseMinimumBatchSize_whenPropertyIsNonPositive() {
        when(carlosProperties.getProperty(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE)).thenReturn("0");
        when(smsQueueWorker.processDueMessages(1)).thenReturn(1);

        int processed = runOnceWithProperties();

        assertThat(processed).isEqualTo(1);
        verify(smsQueueWorker).processDueMessages(1);
    }

    private int runOnceWithProperties() {
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            return new SmsQueueScheduler(smsQueueWorker).runOnce();
        }
    }
}
