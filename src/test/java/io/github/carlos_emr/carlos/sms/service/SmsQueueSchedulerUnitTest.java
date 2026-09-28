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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
    private static final Instant RUN_STARTED = Instant.parse("2026-09-28T14:00:00Z");
    private static final Instant RUN_FINISHED = Instant.parse("2026-09-28T14:00:03Z");

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

    @Test
    @DisplayName("should report no run before the scheduler has run on this server")
    void shouldReportNoRun_beforeFirstRun() {
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);

        assertThat(scheduler.lastRunStartedAt()).isEmpty();
        assertThat(scheduler.lastCompletedRun()).isEmpty();
        assertThat(scheduler.isRunInProgress()).isFalse();
    }

    @Test
    @DisplayName("should record the start, finish and processed count of a completed run")
    void shouldRecordCompletedRun_whenWorkerProcessesMessages() {
        when(smsConfigService.sendingEnabled()).thenReturn(true);
        when(smsQueueWorker.processDueMessages(60)).thenReturn(3);
        SmsQueueScheduler scheduler = trackedScheduler();

        int processed;
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            processed = scheduler.runOnce();
        }

        assertThat(processed).isEqualTo(3);
        assertThat(scheduler.lastRunStartedAt()).contains(RUN_STARTED);
        assertThat(scheduler.lastCompletedRun()).contains(new SmsQueueScheduler.CompletedRun(
                RUN_STARTED, RUN_FINISHED, SmsQueueScheduler.RunOutcome.COMPLETED, 3));
        assertThat(scheduler.isRunInProgress()).isFalse();
    }

    @Test
    @DisplayName("should record a run that left the queue alone because sending is turned off")
    void shouldRecordSendingOff_whenSendingIsTurnedOff() {
        when(smsConfigService.sendingEnabled()).thenReturn(false);
        SmsQueueScheduler scheduler = trackedScheduler();

        scheduler.runOnce();

        assertThat(scheduler.lastCompletedRun()).contains(new SmsQueueScheduler.CompletedRun(
                RUN_STARTED, RUN_FINISHED, SmsQueueScheduler.RunOutcome.SENDING_OFF, 0));
        verifyNoInteractions(smsQueueWorker);
    }

    @Test
    @DisplayName("should record a failed run and still let the exception reach the caller")
    void shouldRecordFailedRun_whenWorkerThrows() {
        when(smsConfigService.sendingEnabled()).thenReturn(true);
        when(smsQueueWorker.processDueMessages(60)).thenThrow(new IllegalStateException("synthetic failure"));
        SmsQueueScheduler scheduler = trackedScheduler();

        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            assertThatThrownBy(scheduler::runOnce).isInstanceOf(IllegalStateException.class);
        }

        assertThat(scheduler.lastCompletedRun()).contains(new SmsQueueScheduler.CompletedRun(
                RUN_STARTED, RUN_FINISHED, SmsQueueScheduler.RunOutcome.FAILED, 0));
        assertThat(scheduler.isRunInProgress()).isFalse();
    }

    @Test
    @DisplayName("should show a run in progress, with its start time, while the worker is still draining")
    void shouldReportRunInProgress_whileWorkerIsDraining() {
        when(smsConfigService.sendingEnabled()).thenReturn(true);
        SmsQueueScheduler scheduler = trackedScheduler();
        AtomicBoolean inProgressDuringRun = new AtomicBoolean();
        AtomicReference<Optional<Instant>> startedDuringRun = new AtomicReference<>();
        AtomicReference<Optional<SmsQueueScheduler.CompletedRun>> completedDuringRun = new AtomicReference<>();
        when(smsQueueWorker.processDueMessages(60)).thenAnswer(invocation -> {
            inProgressDuringRun.set(scheduler.isRunInProgress());
            startedDuringRun.set(scheduler.lastRunStartedAt());
            completedDuringRun.set(scheduler.lastCompletedRun());
            return 0;
        });

        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            scheduler.runOnce();
        }

        assertThat(inProgressDuringRun.get()).isTrue();
        assertThat(startedDuringRun.get()).contains(RUN_STARTED);
        assertThat(completedDuringRun.get()).isEmpty();
        assertThat(scheduler.isRunInProgress()).isFalse();
    }

    /** A scheduler whose clock reads {@link #RUN_STARTED} then {@link #RUN_FINISHED}. */
    private SmsQueueScheduler trackedScheduler() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(RUN_STARTED, RUN_FINISHED);
        return new SmsQueueScheduler(smsQueueWorker, smsConfigService, clock);
    }

    private int runOnceWithProperties() {
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            return new SmsQueueScheduler(smsQueueWorker).runOnce();
        }
    }
}
