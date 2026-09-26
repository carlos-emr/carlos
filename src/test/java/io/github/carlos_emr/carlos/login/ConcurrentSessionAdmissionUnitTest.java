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
package io.github.carlos_emr.carlos.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-user serialization that keeps two concurrent logins from both counting the same
 * sessions before either registers (issue #3980).
 */
@Tag("unit")
@Tag("security")
@DisplayName("ConcurrentSessionAdmission")
class ConcurrentSessionAdmissionUnitTest {

    @Test
    @DisplayName("should run a second admission for the same user only after the first finishes")
    void shouldSerializeAdmissions_forSameUser() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> ConcurrentSessionAdmission.serialize(3980, () -> {
                maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                firstEntered.countDown();
                awaitQuietly(releaseFirst);
                inside.decrementAndGet();
                return 1;
            }));
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = pool.submit(() -> ConcurrentSessionAdmission.serialize(3980, () -> {
                maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                inside.decrementAndGet();
                return 2;
            }));

            // Deterministic: wait until the second login is actually parked on the lock.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!ConcurrentSessionAdmission.lockFor(3980).hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(ConcurrentSessionAdmission.lockFor(3980).hasQueuedThreads())
                    .as("second login waits for the first").isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(maxInside.get()).isEqualTo(1);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("should not block a user whose lock stripe is free")
    void shouldNotBlockUser_onDifferentStripe() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ConcurrentSessionAdmission.serialize(1, () -> {
                // Stripe of 2 differs from stripe of 1, so another thread can admit user 2 now.
                Future<Integer> other = pool.submit(() -> ConcurrentSessionAdmission.serialize(2, () -> 2));
                try {
                    assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(2);
                } catch (Exception e) {
                    throw new AssertionError("a different user's admission was blocked", e);
                }
                return null;
            });
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("should allow re-entry on the same thread and run unlocked for a missing user")
    void shouldAllowReentry_andRunUnlockedForNullUser() throws Exception {
        Integer nested = ConcurrentSessionAdmission.serialize(7, () -> ConcurrentSessionAdmission.serialize(7, () -> 7));

        assertThat(nested).isEqualTo(7);
        assertThat(ConcurrentSessionAdmission.lockFor(7).isHeldByCurrentThread()).isFalse();
        assertThat(ConcurrentSessionAdmission.<String>serialize(null, () -> "ran")).isEqualTo("ran");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
