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
package io.github.carlos_emr.carlos.documentManager.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the properties that make {@link BoundedPdfTask} worth having.
 *
 * <p>Every one of these was a real defect at some point in this feature's history, and none of
 * them is visible by reading the class: an earlier version held the executor in
 * try-with-resources, so the deadline fired but {@code close()} then parked the caller until the
 * abandoned parse finished — the timeout was inert for exactly the case it existed to bound.
 * These tests measure behaviour rather than inspect structure for that reason.
 */
@Tag("unit")
@Tag("document")
@DisplayName("BoundedPdfTask")
class BoundedPdfTaskUnitTest {

    /** Burns CPU without ever checking the interrupt flag, like a PDFBox parse. */
    private static long spin(long millis) {
        long end = System.nanoTime() + millis * 1_000_000L;
        long x = 1;
        while (System.nanoTime() < end) {
            x += x * 31 + 7;
        }
        return x;
    }

    @Test
    @DisplayName("should release the caller at the deadline when the task ignores interruption")
    void shouldReleaseCaller_whenTaskIgnoresInterruption() {
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> BoundedPdfTask.runWithin(1, "test-deadline", () -> spin(4_000)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("too long");

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        // The worker runs for 4s regardless — it cannot be killed. What must not happen is the
        // CALLER waiting for it, which is what shutting the executor down with close() did.
        assertThat(elapsedMs)
                .as("the request thread must come back at the deadline, not when the parse ends")
                .isLessThan(2_500L);
    }

    @Test
    @DisplayName("should propagate an Error rather than reporting it as a document problem")
    void shouldPropagateError_whenTaskThrowsError() {
        // An OutOfMemoryError is not a bad PDF. Reporting it as one told the clinician to check
        // their document while the JVM was failing, and hid the real cause from the logs.
        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-error", () -> {
            throw new OutOfMemoryError("synthetic");
        })).isInstanceOf(OutOfMemoryError.class).hasMessage("synthetic");
    }

    @Test
    @DisplayName("should preserve the task's own IOException and RuntimeException")
    void shouldPreserveCause_whenTaskFails() {
        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-io", () -> {
            throw new IOException("unreadable page");
        })).isInstanceOf(IOException.class).hasMessage("unreadable page");

        assertThatThrownBy(() -> BoundedPdfTask.runWithin(5, "test-rt", () -> {
            throw new IllegalStateException("programming error");
        })).isInstanceOf(IllegalStateException.class).hasMessage("programming error");
    }

    @Test
    @DisplayName("should refuse a new parse when every permit is held by one already running")
    void shouldRefuseParse_whenAllPermitsHeld() throws Exception {
        int permits = BoundedPdfTask.maxConcurrentParses();
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch holding = new CountDownLatch(permits);
        ExecutorService callers = Executors.newFixedThreadPool(permits);

        try {
            // Fill the permits with tasks that block until released. Deliberately does NOT assert
            // how many are taken: a permit is held by the WORKER, so a spun-down parse from an
            // earlier test in this class can still hold one. What matters is only that once they
            // are exhausted, the next caller is refused rather than queued.
            for (int i = 0; i < permits; i++) {
                callers.submit(() -> {
                    // Retries until it actually holds a permit. The suite runs in parallel and
                    // the semaphore is global, so a caller refused because another test held a
                    // permit must come back for it — otherwise the permits drain again and this
                    // test observes no refusal at all, which is how it first went flaky.
                    long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while (System.nanoTime() < giveUp) {
                        try {
                            BoundedPdfTask.runWithin(60, "test-hold", () -> {
                                holding.countDown();
                                hold.await();
                                return 1;
                            });
                            return;
                        } catch (IOException busy) {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        } catch (Exception other) {
                            return;
                        }
                    }
                });
            }

            assertThat(holding.await(30, TimeUnit.SECONDS))
                    .as("every permit should be held before a refusal can be expected").isTrue();

            // Poll rather than sleep a fixed time: the refusal is the observable we want.
            IOException refusal = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline && refusal == null) {
                try {
                    BoundedPdfTask.runWithin(5, "test-overflow", () -> 1);
                    Thread.sleep(50);
                } catch (IOException e) {
                    refusal = e;
                }
            }

            assertThat(refusal)
                    .as("with every permit held, a further parse must be refused immediately")
                    .isNotNull();
            assertThat(refusal).hasMessageContaining("busy");
        } finally {
            hold.countDown();
            callers.shutdown();
            assertThat(callers.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        // Permits are returned once the workers finish, so the next parse succeeds.
        assertThat(BoundedPdfTask.runWithin(5, "test-after", () -> 42)).isEqualTo(42);
    }
}
