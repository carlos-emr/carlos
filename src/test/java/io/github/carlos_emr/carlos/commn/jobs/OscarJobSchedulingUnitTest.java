/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.jobs;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
class OscarJobSchedulingUnitTest {
    @Test
    void shouldKeepOneTimer_whenAnotherSaveArrivesBeforeTheSchedulerReturns() throws Exception {
        OscarJobType type = new OscarJobType();
        type.setEnabled(true);
        type.setClassName(TestJob.class.getName());
        OscarJob job = new OscarJob();
        job.setId(51024);
        job.setEnabled(true);
        job.setCronExpression("0 * * * * *");
        job.setOscarJobType(type);
        job.setProviderNo("123");
        ProviderDao providers = mock(ProviderDao.class);
        when(providers.getProvider("123")).thenReturn(new Provider());
        TaskScheduler scheduler = mock(TaskScheduler.class);

        var firstTimerRegistered = new CountDownLatch(1);
        var letFirstReturn = new CountDownLatch(1);
        var secondSaveStarted = new CountDownLatch(1);
        var secondTimerRegistered = new CountDownLatch(1);
        try (var timers = new ScheduledThreadPoolExecutor(1); var saves = Executors.newFixedThreadPool(2)) {
            timers.setRemoveOnCancelPolicy(true);
            when(scheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(call -> {
                var scheduled = timers.schedule(() -> null, 1, HOURS);
                if (firstTimerRegistered.getCount() != 0) {
                    firstTimerRegistered.countDown();
                    assertThat(letFirstReturn.await(5, SECONDS)).isTrue();
                } else {
                    secondTimerRegistered.countDown();
                }
                return scheduled;
            });

            var first = saves.submit(() -> schedule(job, scheduler, providers));
            try {
                assertThat(firstTimerRegistered.await(5, SECONDS)).isTrue();
                var second = saves.submit(() -> {
                    secondSaveStarted.countDown();
                    return schedule(job, scheduler, providers);
                });
                assertThat(secondSaveStarted.await(5, SECONDS)).isTrue();
                secondTimerRegistered.await(1, SECONDS);
                letFirstReturn.countDown();

                assertThat(first.get(5, SECONDS)).isTrue();
                assertThat(second.get(5, SECONDS)).isTrue();
                assertThat(timers.getQueue()).hasSize(1);
                var tracked = OscarJobExecutingManager.getFutures();
                assertThat(tracked).containsKey(job.getId());
                OscarJobUtils.cancelJob(job.getId(), false);
                assertThat(tracked).doesNotContainKey(job.getId());
                assertThat(timers.getQueue()).isEmpty();
            } finally {
                letFirstReturn.countDown();
                OscarJobExecutingManager.getFutures().remove(job.getId());
                timers.shutdownNow();
            }
        }
    }

    private static boolean schedule(OscarJob job, TaskScheduler scheduler, ProviderDao providers) throws Exception {
        try (var beans = mockStatic(SpringUtils.class)) {
            beans.when(() -> SpringUtils.getBean(TaskScheduler.class)).thenReturn(scheduler);
            beans.when(() -> SpringUtils.getBean(ProviderDao.class)).thenReturn(providers);
            return OscarJobUtils.scheduleJob(job);
        }
    }
}
