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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("start follows the property, and does not throw, when the saved settings cannot be read")
    void shouldFallBackToProperty_whenStoredSettingsCannotBeRead() {
        when(smsConfigService.storedSchedulerEnabled()).thenThrow(new IllegalStateException("sms_config missing"));
        when(carlosProperties.isPropertyActive("sms.queue.scheduler.enabled")).thenReturn(true, false);
        SmsQueueScheduler on = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        SmsQueueScheduler off = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            on.start();
            off.start();

            assertThat(on.isRunning()).isTrue();
            assertThat(off.isRunning()).isFalse();
        } finally {
            on.stop();
            off.stop();
        }
    }

    @Test
    @DisplayName("the scheduler starts again after a settings change stopped it, and a repeated start changes nothing")
    void shouldRestart_afterSettingsChangeStoppedIt() {
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));
            scheduler.onConfigChanged(new SmsConfigChangedEvent(false));
            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));
            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));

            assertThat(scheduler.isRunning()).isTrue();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("turning the scheduler off lets a run in progress finish, and shutdown interrupts it")
    void shouldLetRunFinish_whenTurnedOff_andInterruptIt_whenShutDown() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        // One stub for both runs: stubbing again from this thread would itself call the first answer.
        AtomicReference<CountDownLatch> gate = new AtomicReference<>(release);
        AtomicInteger interrupted = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        when(carlosProperties.getProperty(eq("sms.queue.scheduler.intervalSeconds"), anyString())).thenReturn("1");
        when(smsConfigService.sendingEnabled()).thenReturn(true);
        when(smsQueueWorker.processDueMessages(anyInt())).thenAnswer(invocation -> {
            CountDownLatch current = gate.get();
            started.countDown();
            try {
                current.await(10, TimeUnit.SECONDS);
                finished.incrementAndGet();
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
            }
            return 0;
        });
        SmsQueueScheduler scheduler = new SmsQueueScheduler(smsQueueWorker, smsConfigService);
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));
            await(started, 1);

            scheduler.onConfigChanged(new SmsConfigChangedEvent(false));
            assertThat(scheduler.isRunning()).isFalse();
            release.countDown();
            awaitValue(finished, 1);
            assertThat(interrupted).hasValue(0);

            // Back on: the next run blocks on a gate nobody opens, and shutdown does interrupt it.
            gate.set(new CountDownLatch(1));
            scheduler.onConfigChanged(new SmsConfigChangedEvent(true));
            await(started, 0);
            scheduler.stop();
            awaitValue(interrupted, 1);
            assertThat(finished).hasValue(1);
        } finally {
            release.countDown();
            scheduler.stop();
        }
    }

    private static void awaitValue(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (counter.get() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(counter).hasValue(expected);
    }

    /** Waits until {@code latch} has counted down to {@code remaining}. */
    private static void await(CountDownLatch latch, long remaining) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (latch.getCount() > remaining && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(latch.getCount()).isEqualTo(remaining);
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
