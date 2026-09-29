/*
 * Copyright (c) 2026. CARLOS EMR contributors and others.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package io.github.carlos_emr.carlos.fax.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FaxSchedulerJob} lifecycle and failure-classification behavior.
 *
 * <p>Exercises the package-private {@code runCycle()} test seam directly instead of waiting
 * on the {@link java.util.Timer}: cycle success clears {@code lastError} and marks the
 * scheduler running; a {@link RuntimeException} records the error, stops the scheduler and
 * schedules an automatic restart; an {@link OutOfMemoryError} (or any JVM {@link Error})
 * stops the scheduler WITHOUT scheduling a restart, to avoid crash loops.</p>
 *
 * <p>{@code cancelTask()} is always invoked in {@code @AfterEach} so no {@code Timer}
 * threads leak into other tests.</p>
 *
 * @since 2026-08-21
 * @see FaxSchedulerJob
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxSchedulerJob Unit Tests")
class FaxSchedulerJobUnitTest extends CarlosUnitTestBase {

    private FaxImporter faxImporter;
    private FaxSender faxSender;
    private FaxStatusUpdater faxStatusUpdater;
    private FaxConfigDao faxConfigDao;

    private FaxSchedulerJob schedulerJob;

    @BeforeEach
    void setUp() {
        faxImporter = mock(FaxImporter.class);
        faxSender = mock(FaxSender.class);
        faxStatusUpdater = mock(FaxStatusUpdater.class);
        faxConfigDao = mock(FaxConfigDao.class);

        schedulerJob = new FaxSchedulerJob(faxImporter, faxSender, faxStatusUpdater, faxConfigDao);
    }

    @AfterEach
    void tearDown() {
        // Always cancel timers - a leaked Timer thread would keep firing runCycle()
        // against garbage-collected mocks in later tests.
        schedulerJob.cancelTask();
    }

    @Test
    @DisplayName("should not start timer when no active configs exist at initialize")
    void shouldNotStartTimer_whenNoActiveConfigsAtInitialize() {
        // Given: only inactive fax accounts
        FaxConfig inactive1 = createConfig(false);
        FaxConfig inactive2 = createConfig(false);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(inactive1, inactive2));

        // When
        schedulerJob.initialize();

        // Then: scheduler stays idle until an account is activated via admin UI
        assertThat(schedulerJob.isRunning()).isFalse();
        assertThat((Object) readField("timerTask")).isNull();
    }

    @Test
    @DisplayName("should record last error when the startup config lookup fails")
    void shouldRecordLastError_whenStartupConfigLookupFails() {
        // Given: the configuration lookup itself fails (e.g. database outage at startup)
        when(faxConfigDao.findAll(null, null)).thenThrow(new RuntimeException("db down"));

        // When
        schedulerJob.initialize();

        // Then: not running, and the failure is recorded so the admin status page reports a
        // fatal stop instead of the benign "no active fax accounts" idle state
        assertThat(schedulerJob.isRunning()).isFalse();
        assertThat(schedulerJob.getLastError())
                .contains("Failed to check fax configurations at startup")
                .contains("db down");
    }

    @Test
    @DisplayName("should start timer when an active config exists at initialize")
    void shouldStartTimer_whenActiveConfigExistsAtInitialize() {
        // Given: one active fax account among inactive ones
        when(faxConfigDao.findAll(null, null))
                .thenReturn(Arrays.asList(createConfig(false), createConfig(true)));

        // When
        schedulerJob.initialize();

        // Then: startTask() flags the scheduler running as soon as the timer is scheduled
        assertThat(schedulerJob.isRunning()).isTrue();
        assertThat((Object) readField("timerTask")).isNotNull();
    }

    @Test
    @DisplayName("should record last error and stop running when cycle throws RuntimeException")
    void shouldRecordLastErrorAndStopRunning_whenCycleThrowsRuntimeException() {
        // Given
        doThrow(new RuntimeException("boom")).when(faxImporter).poll();

        // When
        schedulerJob.runCycle();

        // Then: error is classified as "<SimpleName>: <message>" and the cycle timer stops
        assertThat(schedulerJob.isRunning()).isFalse();
        assertThat(schedulerJob.getLastError())
                .contains("RuntimeException")
                .contains("boom");
        // RuntimeException is considered potentially transient - an automatic restart is scheduled
        assertThat((Object) readField("autoRestartTask"))
                .as("RuntimeException must schedule an automatic restart attempt")
                .isNotNull();
    }

    @Test
    @DisplayName("should not schedule restart when cycle throws OutOfMemoryError")
    void shouldNotScheduleRestart_whenCycleThrowsOutOfMemoryError() {
        // Given: the JVM is in a bad state - auto-restart would risk a crash loop
        doThrow(new OutOfMemoryError("heap exhausted")).when(faxImporter).poll();

        // When
        schedulerJob.runCycle();

        // Then: stopped, error recorded, and crucially NO restart task was scheduled
        assertThat(schedulerJob.isRunning()).isFalse();
        assertThat(schedulerJob.getLastError()).contains("OUT OF MEMORY");
        assertThat((Object) readField("autoRestartTask"))
                .as("OOM must not schedule an automatic restart")
                .isNull();
        assertThat((Object) readField("timer"))
                .as("cancelTask() nulls the timer and no restart re-created it")
                .isNull();
    }

    @Test
    @DisplayName("should clear last error when cycle succeeds after a failure")
    void shouldClearLastError_whenCycleSucceedsAfterFailure() {
        // Given: first cycle fails, second succeeds
        doThrow(new RuntimeException("transient db drop")).doNothing().when(faxImporter).poll();

        schedulerJob.runCycle();
        assertThat(schedulerJob.getLastError()).contains("transient db drop");
        assertThat(schedulerJob.isRunning()).isFalse();

        // When: the next cycle completes normally
        schedulerJob.runCycle();

        // Then: healthy state - empty lastError, running again, last success recorded
        assertThat(schedulerJob.getLastError()).isEmpty();
        assertThat(schedulerJob.isRunning()).isTrue();
        assertThat(schedulerJob.getLastSuccessfulRunEpochMs()).isPositive();
    }

    @Test
    @DisplayName("should start when startIfNotRunning is called while stopped")
    void shouldStart_whenStartIfNotRunningCalledWhileStopped() {
        // Given: freshly constructed scheduler, never started
        assertThat(schedulerJob.isRunning()).isFalse();

        // When
        schedulerJob.startIfNotRunning();

        // Then
        assertThat(schedulerJob.isRunning()).isTrue();
        assertThat((Object) readField("timerTask")).isNotNull();
    }

    @Test
    @DisplayName("should no-op when startIfNotRunning is called while running")
    void shouldNoOp_whenStartIfNotRunningCalledWhileRunning() {
        // Given: a successful cycle marked the scheduler running (without a scheduled timer)
        schedulerJob.runCycle();
        assertThat(schedulerJob.isRunning()).isTrue();
        assertThat((Object) readField("timerTask")).isNull();

        // When
        schedulerJob.startIfNotRunning();

        // Then: still running, and no new timer task was scheduled (no-op path taken)
        assertThat(schedulerJob.isRunning()).isTrue();
        assertThat((Object) readField("timerTask"))
                .as("startIfNotRunning must be a no-op while the scheduler reports running")
                .isNull();
    }

    // -- helper methods --

    private FaxConfig createConfig(boolean active) {
        FaxConfig config = new FaxConfig();
        config.setActive(active);
        config.setDownload(true);
        config.setFaxUser("test-access-id");
        return config;
    }

    /**
     * Reads a private field from the scheduler under test. Used to observe whether the
     * timer / auto-restart tasks were scheduled, which has no public accessor by design.
     */
    private Object readField(String fieldName) {
        try {
            Field field = FaxSchedulerJob.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(schedulerJob);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read field " + fieldName, e);
        }
    }
}
