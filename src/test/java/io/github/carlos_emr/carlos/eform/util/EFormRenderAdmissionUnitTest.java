/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService.SlotAcquisition.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("eform")
class EFormRenderAdmissionUnitTest {
    private static void awaitWaiters(Semaphore slots, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (slots.getQueueLength() < expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertThat(slots.getQueueLength()).isEqualTo(expected);
    }

    @Test
    void fourQueuedSessionsBoundServletOccupancyAndOverflowRefusesBeforeTakingAnySlot() throws Exception {
        Semaphore slots = new Semaphore(0, true), waiting = new Semaphore(4);
        var workers = Executors.newFixedThreadPool(5);
        var queued = new ArrayList<java.util.concurrent.Future<EFormBrowserPdfService.SlotAcquisition>>();
        try {
            for (int i = 0; i < 4; i++) {
                queued.add(workers.submit(() -> EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofSeconds(10))));
            }
            awaitWaiters(slots, 4);
            assertThat(waiting.availablePermits()).isZero();
            // The extra caller must return now, rather than consume its ten-second wait.
            assertThat(workers.submit(() -> EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofSeconds(10)))
                    .get(2, TimeUnit.SECONDS)).isEqualTo(TIMED_OUT);
            assertThat(slots.getQueueLength()).isEqualTo(4);
            assertThat(slots.availablePermits()).isZero();
            // Model one browser completion followed by each admitted caller's completion.
            for (int recovered = 1; recovered <= 4; recovered++) {
                slots.release();
                // Submission order need not equal queue order; observe actual acquisitions.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (waiting.availablePermits() < recovered && System.nanoTime() < deadline) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
                assertThat(waiting.availablePermits()).isEqualTo(recovered);
            }
            for (var caller : queued) assertThat(caller.get(5, TimeUnit.SECONDS)).isEqualTo(ACQUIRED);
            assertThat(waiting.availablePermits()).isEqualTo(4);
            assertThat(slots.availablePermits()).isZero();
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(waiting.availablePermits()).isEqualTo(4);
    }

    @Test
    void admittedSessionsRemainFifoAndWaiterSlotsAreReleasedOnAcquisition() throws Exception {
        Semaphore slots = new Semaphore(0, true), waiting = new Semaphore(4);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofSeconds(10)));
            awaitWaiters(slots, 1);
            var second = workers.submit(() -> EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofSeconds(10)));
            awaitWaiters(slots, 2);
            slots.release();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(ACQUIRED);
            assertThat(second.isDone()).isFalse();
            assertThat(waiting.availablePermits()).isEqualTo(3);
            slots.release();
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(ACQUIRED);
            assertThat(waiting.availablePermits()).isEqualTo(4);
            assertThat(slots.availablePermits()).isZero();
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void timedOutWaiterReleasesItsAdmissionAndCannotCreateARenderPermit() {
        Semaphore slots = new Semaphore(0, true), waiting = new Semaphore(4);
        assertThat(EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofMillis(20))).isEqualTo(TIMED_OUT);
        assertThat(waiting.availablePermits()).isEqualTo(4);
        assertThat(slots.availablePermits()).isZero();
        assertThat(slots.getQueueLength()).isZero();
        slots.release();
        assertThat(EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ZERO)).isEqualTo(ACQUIRED);
        assertThat(slots.availablePermits()).isZero();
        assertThat(waiting.availablePermits()).isEqualTo(4);
    }

    @Test
    void interruptedQueuedCallerPreservesInterruptAndReleasesOnlyItsWaiterPermit() throws Exception {
        Semaphore slots = new Semaphore(0, true), waiting = new Semaphore(4);
        AtomicReference<EFormBrowserPdfService.SlotAcquisition> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            result.set(EFormBrowserPdfService.acquireRenderSlot(slots, waiting, Duration.ofSeconds(10)));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            caller.start(); awaitWaiters(slots, 1);
            caller.interrupt(); caller.join(5000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(result.get()).isEqualTo(INTERRUPTED);
            assertThat(interrupted).isTrue();
            assertThat(waiting.availablePermits()).isEqualTo(4);
            assertThat(slots.availablePermits()).isZero();
            assertThat(slots.getQueueLength()).isZero();
        } finally { caller.interrupt(); caller.join(5000); }
    }

    @Test
    void freeSlotNeedsNoWaitingTokenAndPreexistingInterruptAcquiresNothing() {
        Semaphore slots = new Semaphore(1, true), fullWaitingQueue = new Semaphore(0);
        assertThat(EFormBrowserPdfService.acquireRenderSlot(slots, fullWaitingQueue, Duration.ZERO)).isEqualTo(ACQUIRED);
        assertThat(fullWaitingQueue.availablePermits()).isZero();
        slots.release();
        Thread.currentThread().interrupt();
        try {
            assertThat(EFormBrowserPdfService.acquireRenderSlot(slots, fullWaitingQueue, Duration.ZERO)).isEqualTo(INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(slots.availablePermits()).isEqualTo(1);
            assertThat(fullWaitingQueue.availablePermits()).isZero();
        } finally { Thread.interrupted(); }
    }
}
