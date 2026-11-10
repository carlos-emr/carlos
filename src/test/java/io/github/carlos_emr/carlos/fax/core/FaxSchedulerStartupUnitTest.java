/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.fax.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportConfigurationService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Real startup transitions; mocked timer/services guarantee zero fax work or network calls. */
@Tag("unit")
class FaxSchedulerStartupUnitTest extends CarlosUnitTestBase {
    private final FaxImporter importer = mock(FaxImporter.class);
    private final FaxSender sender = mock(FaxSender.class);
    private final FaxStatusUpdater status = mock(FaxStatusUpdater.class);
    private final FaxConfigDao configurations = mock(FaxConfigDao.class);
    private final Timer timer = mock(Timer.class);
    private final AtomicBoolean blocked = new AtomicBoolean(true);
    private final AtomicInteger timers = new AtomicInteger();
    private FaxSchedulerJob job;

    @BeforeEach void setup() {
        job = new FaxSchedulerJob(importer, sender, status, configurations, blocked::get,
                name -> { timers.incrementAndGet(); return timer; });
        var account = new FaxConfig(); account.setActive(true);
        doReturn(List.of(account)).when(configurations).findAll(null, null);
    }

    @AfterEach void cleanup() {
        job.shutdown();
        verify(importer, never()).poll();
        verify(sender, never()).send();
        verify(status, never()).updateStatus();
    }

    @Test void shouldDeferTheBlockedInitialRead_thenScheduleExistingActiveAccountsExactlyOnce() {
        job.initialize();
        assertThat(job.isRunning()).isFalse();
        verifyNoInteractions(configurations, timer);
        blocked.set(false);
        job.retryDeferredInitialization();
        job.retryDeferredInitialization();
        job.initialize();
        verify(configurations).findAll(null, null);
        verify(timer).schedule(any(TimerTask.class), eq(3000L), anyLong());
        assertThat(timers).hasValue(1);
        assertThat(job.isRunning()).isTrue();
        assertThat(job.getLastError()).isEmpty();
    }

    @Test void shouldStartNormallyWhenTheGuardIsDisabledOrOpen_withoutAnyDeferredStartup() {
        blocked.set(false);
        job.initialize();
        job.retryDeferredInitialization();
        assertThat(job.isRunning()).isTrue();
        verify(configurations).findAll(null, null);
        assertThat(timers).hasValue(1);
    }

    @Test void shouldPreserveNormalStartup_withActualDisabledMaintenanceService() throws Exception {
        var pool = mock(OscarTrackingBasicDataSource.class);
        var maintenance = new CdsImportMaintenanceService(pool,
                new CdsImportConfigurationService(new Properties()));
        job = new FaxSchedulerJob(importer, sender, status, configurations,
                maintenance::isClinicalAccessBlocked, name -> { timers.incrementAndGet(); return timer; });
        job.initialize(); job.retryDeferredInitialization();
        assertThat(job.isRunning()).isTrue(); assertThat(timers).hasValue(1);
        verify(pool, never()).getConnection();
        verify(configurations).findAll(null, null);
    }

    @Test void shouldNotDuplicateTimers_whenReleaseCallbacksRace() throws Exception {
        job.initialize(); blocked.set(false);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); job.retryDeferredInitialization(); return null; });
            var second = pool.submit(() -> { start.await(); job.retryDeferredInitialization(); return null; });
            start.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        }
        assertThat(timers).hasValue(1);
        verify(configurations).findAll(null, null);
    }

    @Test void shouldKeepARealInactiveResultIdle_andPreserveExplicitAdminActivation() {
        blocked.set(false); when(configurations.findAll(null, null)).thenReturn(List.of());
        job.initialize(); job.retryDeferredInitialization();
        verify(configurations).findAll(null, null);
        assertThat(timers).hasValue(0);
        job.startIfNotRunning();
        assertThat(timers).hasValue(1);
    }

    @Test void shouldRetainAndPropagateAReadAdmissionRace_evenWhenTheGateAlreadyReopened() {
        job.initialize(); blocked.set(false);
        when(configurations.findAll(null, null)).thenThrow(new IllegalStateException(new SQLException(
                "CDS maintenance or an incomplete chart hold blocks clinical access", "45000")));
        assertThatThrownBy(job::retryDeferredInitialization).hasMessage("FAX_STARTUP_DEFERRED");
        assertThat(job.isRunning()).isFalse(); assertThat(timers).hasValue(0);
        var account = new FaxConfig(); account.setActive(true);
        doReturn(List.of(account)).when(configurations).findAll(null, null);
        job.retryDeferredInitialization();
        assertThat(job.isRunning()).isTrue(); assertThat(timers).hasValue(1);
    }

    @Test void shouldReportGenuineLookupErrorsAsOptionalFailures_withoutInventingAnInactiveResult() {
        blocked.set(false);
        when(configurations.findAll(null, null)).thenThrow(new IllegalStateException("db down"));
        assertThatCode(job::initialize).doesNotThrowAnyException();
        assertThatCode(job::retryDeferredInitialization).doesNotThrowAnyException();
        assertThat(job.getLastError()).contains("db down");
        assertThat(job.isRunning()).isFalse(); assertThat(timers).hasValue(0);
    }

    @Test void shouldNeverRestartAfterContextShutdown_evenFromLateRecoveryOrActivation() {
        job.initialize(); job.shutdown(); blocked.set(false);
        job.retryDeferredInitialization(); job.initialize(); job.startIfNotRunning(); job.restartTask(); job.runCycle();
        assertThat(job.isRunning()).isFalse(); assertThat(timers).hasValue(0);
        verifyNoInteractions(configurations, timer);
    }

    @Test void shouldNotRestartAFatalStoppedScheduler_fromTheDeferredStartupHook() {
        var failedImporter = mock(FaxImporter.class);
        doThrow(new OutOfMemoryError("synthetic test failure")).when(failedImporter).poll();
        var stopped = new FaxSchedulerJob(failedImporter, sender, status, configurations, () -> false,
                name -> {timers.incrementAndGet();return timer;});
        try {
            stopped.initialize(); stopped.runCycle(); stopped.retryDeferredInitialization();
            assertThat(stopped.isRunning()).isFalse(); assertThat(timers).hasValue(1);
            assertThat(stopped.getLastError()).contains("OUT OF MEMORY");
        } finally {stopped.shutdown();}
    }

    @Test void shouldPreserveAManualStop_untilExplicitAdminActivation() {
        blocked.set(false); job.initialize(); job.cancelTask(); job.retryDeferredInitialization();
        assertThat(job.isRunning()).isFalse(); assertThat(timers).hasValue(1);
        job.startIfNotRunning(); assertThat(job.isRunning()).isTrue(); assertThat(timers).hasValue(2);
    }

    @Test void shouldFenceTimerCreation_againstShutdownRacingInitialRead() throws Exception {
        blocked.set(false);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(configurations.findAll(null, null)).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            var account = new FaxConfig(); account.setActive(true); return List.of(account);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var initializing = pool.submit(job::initialize);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var stopping = pool.submit(job::shutdown); release.countDown();
            initializing.get(5, TimeUnit.SECONDS); stopping.get(5, TimeUnit.SECONDS);
        }
        job.retryDeferredInitialization();
        var task = ArgumentCaptor.forClass(TimerTask.class);
        verify(timer).schedule(task.capture(), eq(3000L), anyLong());
        verify(timer).cancel(); assertThat(timers).hasValue(1); assertThat(job.isRunning()).isFalse();
        task.getValue().run();
    }

    @Test void shouldUseTheActualSpringConstructorAndTerminalDestroyLifecycle_underRetainedMaintenance() {
        var maintenance = mock(CdsImportMaintenanceService.class);
        when(maintenance.isClinicalAccessBlocked()).thenReturn(true);
        FaxSchedulerJob nativeBean;
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FaxImporter.class, () -> importer);
            context.registerBean(FaxSender.class, () -> sender);
            context.registerBean(FaxStatusUpdater.class, () -> status);
            context.registerBean(FaxConfigDao.class, () -> configurations);
            context.registerBean(CdsImportMaintenanceService.class, () -> maintenance);
            context.registerBean(FaxSchedulerJob.class);
            context.refresh(); nativeBean = context.getBean(FaxSchedulerJob.class);
            assertThat(nativeBean.isRunning()).isFalse(); verifyNoInteractions(configurations);
        }
        when(maintenance.isClinicalAccessBlocked()).thenReturn(false);
        nativeBean.retryDeferredInitialization(); nativeBean.startIfNotRunning();
        verifyNoInteractions(configurations); assertThat(nativeBean.isRunning()).isFalse();
    }
}
