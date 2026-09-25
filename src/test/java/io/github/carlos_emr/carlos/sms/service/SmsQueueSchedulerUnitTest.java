package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.event.SmsConfigChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsQueueSchedulerUnitTest {
    private static final String BATCH_SIZE_PROPERTY = "sms.queue.scheduler.batchSize";
    private static final String DEFAULT_BATCH_SIZE = "60";

    @Mock
    private SmsQueueProcessingService smsQueueWorker;

    @Mock
    private CarlosProperties carlosProperties;

    @Mock
    private SmsConfigService smsConfigService;

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

    @Test
    @DisplayName("start follows the setting saved in Administration over the property")
    void shouldStartFromStoredSetting_overProperty() {
        when(smsConfigService.storedSchedulerEnabled()).thenReturn(Optional.of(true));
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            scheduler.start();

            assertThat(scheduler.isRunning()).isTrue();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("start stays off when the saved setting is off, even if the property is on")
    void shouldStayOff_whenStoredSettingIsOff() {
        when(smsConfigService.storedSchedulerEnabled()).thenReturn(Optional.of(false));
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            scheduler.start();

            assertThat(scheduler.isRunning()).isFalse();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("start falls back to the property while nothing is saved")
    void shouldFallBackToProperty_whenNothingStored() {
        when(smsConfigService.storedSchedulerEnabled()).thenReturn(Optional.empty());
        when(carlosProperties.isPropertyActive("sms.queue.scheduler.enabled")).thenReturn(true);
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            scheduler.start();

            assertThat(scheduler.isRunning()).isTrue();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("a saved settings change starts or stops the scheduler without a restart")
    void shouldStartAndStop_whenSettingsChange() {
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));
            assertThat(scheduler.isRunning()).isTrue();

            scheduler.onConfigChanged(new SmsConfigChangedEvent(false));
            assertThat(scheduler.isRunning()).isFalse();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("runOnce leaves queued messages alone while SMS is turned off in Administration")
    void shouldSkipQueue_whenSendingIsTurnedOff() {
        when(smsConfigService.sendingEnabled()).thenReturn(false);

        int processed = new SmsQueueScheduler(smsQueueWorker, smsConfigService).runOnce();

        assertThat(processed).isZero();
        verifyNoInteractions(smsQueueWorker);
    }

    private int runOnceWithProperties() {
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            return new SmsQueueScheduler(smsQueueWorker).runOnce();
        }
    }
}
