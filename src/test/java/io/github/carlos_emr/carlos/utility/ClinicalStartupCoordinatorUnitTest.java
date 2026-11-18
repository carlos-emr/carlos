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
package io.github.carlos_emr.carlos.utility;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class ClinicalStartupCoordinatorUnitTest {
    @Test void shouldInitializeDeferredWorkOnDemand_whenAnotherNodeReleasesMaintenance() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(true);
        AtomicInteger work = new AtomicInteger();
        QueuedExecutor executor = new QueuedExecutor();
        var coordinator = new ClinicalStartupCoordinator(blocked::get, work::incrementAndGet, executor, () -> 0);
        coordinator.start();
        assertThat(work).hasValue(0);
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.PENDING);
        // No timer or another node's local event is needed: the next ordinary request triggers work.
        blocked.set(false);
        assertThat(coordinator.isReady()).isFalse();
        assertThat(coordinator.isReady()).isFalse();
        assertThat(executor.tasks).hasSize(1);
        executor.next();
        assertThat(coordinator.isReady()).isTrue();
        assertThat(work).hasValue(1);
        coordinator.close();
    }

    @Test void shouldKeepNormalStartupFailFast_whenInitializationFails() {
        var coordinator = new ClinicalStartupCoordinator(() -> false,
                () -> { throw new IllegalStateException("broken bootstrap"); }, new QueuedExecutor(), () -> 0);
        assertThatThrownBy(coordinator::start).isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.FAILED);
        coordinator.close();
    }

    @Test void shouldKeepInitializationPending_whenMaintenanceClosesDuringStartup() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(false);
        AtomicInteger work = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        QueuedExecutor executor = new QueuedExecutor();
        var coordinator = new ClinicalStartupCoordinator(blocked::get, () -> {
            if (work.incrementAndGet() == 1) { blocked.set(true); throw new IllegalStateException("admission closed"); }
        }, executor, clock::get);
        coordinator.start();
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.PENDING);
        blocked.set(false);
        assertThat(coordinator.isReady()).isFalse();
        assertThat(executor.tasks).isEmpty();
        clock.set(TimeUnit.SECONDS.toNanos(31));
        assertThat(coordinator.isReady()).isFalse();
        executor.next();
        assertThat(coordinator.isReady()).isTrue();
        assertThat(work).hasValue(2);
        coordinator.close();
    }

    @Test void shouldRetryAfterCooldown_whenAsyncInitializationFails() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(true);
        AtomicInteger work = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        QueuedExecutor executor = new QueuedExecutor();
        var coordinator = new ClinicalStartupCoordinator(blocked::get, () -> {
            if (work.incrementAndGet() == 1) throw new IllegalStateException("failed real cache hydration");
        }, executor, clock::get);
        coordinator.start(); blocked.set(false); coordinator.isReady(); executor.next();
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.FAILED);
        assertThat(coordinator.isReady()).isFalse(); assertThat(executor.tasks).isEmpty();
        clock.set(TimeUnit.SECONDS.toNanos(31)); coordinator.isReady(); executor.next();
        assertThat(coordinator.isReady()).isTrue(); coordinator.close();
    }

    @Test void shouldKeepFailureVisible_whenMaintenanceMetadataIsUnavailable() {
        AtomicInteger reads = new AtomicInteger();
        var coordinator = new ClinicalStartupCoordinator(() -> {
            if (reads.incrementAndGet() > 1) throw new IllegalStateException("metadata unavailable");
            return false;
        }, () -> { throw new IllegalArgumentException("bootstrap failed"); }, new QueuedExecutor(), () -> 0);
        assertThatThrownBy(coordinator::start).isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.FAILED);
        coordinator.close();
    }

    @Test void shouldCancelQueuedInitialization_whenContextIsDestroyed() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(true);
        AtomicInteger work = new AtomicInteger(); QueuedExecutor executor = new QueuedExecutor();
        var coordinator = new ClinicalStartupCoordinator(blocked::get, work::incrementAndGet, executor, () -> 0);
        coordinator.start(); blocked.set(false); coordinator.isReady(); coordinator.close();
        assertThat(executor.isShutdown()).isTrue(); assertThat(executor.tasks).isEmpty();
        assertThat(coordinator.isReady()).isFalse(); assertThat(work).hasValue(0);
    }

    @Test void shouldKeepInitializationPending_whenMaintenanceClosesAfterWork() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(false);
        var coordinator = new ClinicalStartupCoordinator(blocked::get, () -> blocked.set(true),
                new QueuedExecutor(), () -> 0);
        coordinator.start();
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.PENDING);
        coordinator.close();
    }

    @Test void shouldKeepClinicalAccessClosed_whenInitializerTerminatesFatally() {
        var coordinator = new ClinicalStartupCoordinator(() -> false,
                () -> { throw new AssertionError("fatal initialization"); }, new QueuedExecutor(), () -> 0);
        assertThatThrownBy(coordinator::start).isInstanceOf(AssertionError.class);
        assertThat(coordinator.state()).isEqualTo(ClinicalStartupCoordinator.State.FAILED);
        coordinator.close();
    }

    static final class QueuedExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>(); private boolean shutdown;
        void next() { tasks.remove().run(); }
        @Override public void execute(Runnable task) { if (shutdown) throw new IllegalStateException(); tasks.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; var prior = List.copyOf(tasks); tasks.clear(); return prior; }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
}
