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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Web-context lifecycle for work that cannot run inside a retained clinical maintenance window.
 * There is no timer/polling loop: the first ordinary request after release requests initialization.
 * Import/login routes remain independent of this node-local readiness barrier.
 */
public final class ClinicalStartupCoordinator implements AutoCloseable {
    public static final String ATTRIBUTE = ClinicalStartupCoordinator.class.getName();
    public enum State { PENDING, RUNNING, READY, FAILED, CLOSED }
    @FunctionalInterface public interface Initialization { void run() throws Exception; }
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final BooleanSupplier blocked;
    private final Initialization initialization;
    private final ExecutorService executor;
    private final LongSupplier clock;
    private State state = State.PENDING;
    private long retryAt;

    public ClinicalStartupCoordinator(BooleanSupplier blocked, Initialization initialization) {
        this(blocked, initialization, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "carlos-clinical-startup");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, failure) -> MiscUtils.getLogger().error(
                    "Clinical startup worker terminated ({}); clinical access remains unavailable", failure.getClass().getName()));
            return thread;
        }), System::nanoTime);
    }

    ClinicalStartupCoordinator(BooleanSupplier blocked, Initialization initialization,
            ExecutorService executor, LongSupplier clock) {
        this.blocked = Objects.requireNonNull(blocked);
        this.initialization = Objects.requireNonNull(initialization);
        this.executor = Objects.requireNonNull(executor);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Normal startup remains fail-fast; an actual closed database gate explicitly defers work. */
    public void start() throws Exception {
        if (blocked.getAsBoolean()) {
            MiscUtils.getLogger().info("Clinical startup deferred while CDS maintenance or chart holds remain active");
            return;
        }
        synchronized (this) { if (state == State.CLOSED) return; state = State.RUNNING; }
        initialize(true);
    }

    /** Nonblocking HTTP admission check. Only one initialization may execute on this node. */
    public synchronized boolean isReady() {
        if (state == State.READY) return true;
        if (state == State.CLOSED || state == State.RUNNING || clock.getAsLong() < retryAt) return false;
        state = State.RUNNING;
        try {
            executor.execute(() -> {
                try { initialize(false); }
                catch (Exception impossible) { throw new IllegalStateException(impossible); }
            });
        } catch (RuntimeException rejected) {
            state = State.FAILED;
            retryAt = clock.getAsLong() + RETRY_NANOS;
            MiscUtils.getLogger().error("Clinical startup scheduling failed; clinical access remains unavailable");
        }
        return false;
    }

    private void initialize(boolean failFast) throws Exception {
        try {
            if (blocked.getAsBoolean()) { deferred(); return; }
            initialization.run();
            if (blocked.getAsBoolean()) { deferred(); return; }
            synchronized (this) { if (state != State.CLOSED) state = State.READY; }
            MiscUtils.getLogger().info("Clinical startup initialization completed");
        } catch (Exception failure) {
            // Only a successful read of the actual durable gate can classify failure as maintenance.
            boolean maintenance = false;
            try { maintenance = blocked.getAsBoolean(); }
            catch (RuntimeException unavailable) { failure.addSuppressed(unavailable); }
            if (maintenance) { deferred(); return; }
            synchronized (this) {
                if (state != State.CLOSED) { state = State.FAILED; retryAt = clock.getAsLong() + RETRY_NANOS; }
            }
            MiscUtils.getLogger().error("Clinical startup failed ({}); clinical access remains unavailable",
                    failure.getClass().getName());
            if (failFast) throw failure;
        } catch (Error fatal) {
            synchronized (this) {
                if (state != State.CLOSED) { state = State.FAILED; retryAt = clock.getAsLong() + RETRY_NANOS; }
            }
            MiscUtils.getLogger().error("Clinical startup terminated ({}); clinical access remains unavailable",
                    fatal.getClass().getName());
            throw fatal;
        }
    }

    private synchronized void deferred() {
        if (state != State.CLOSED) { state = State.PENDING; retryAt = clock.getAsLong() + RETRY_NANOS; }
    }
    public synchronized State state() { return state; }

    @Override public void close() {
        synchronized (this) { state = State.CLOSED; }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                MiscUtils.getLogger().warn("Clinical startup task has not yet stopped after context shutdown");
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
