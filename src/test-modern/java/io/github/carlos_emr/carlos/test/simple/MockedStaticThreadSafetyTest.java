/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.test.simple;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Validates that Mockito 5.x's {@code MockedStatic} is thread-local and safe for concurrent use.
 *
 * <p>This test is a prerequisite for enabling parallel unit test execution in the CARLOS EMR
 * test suite. If this test fails, parallel unit tests that use {@code OpenOUnitTestBase}
 * (which creates {@code MockedStatic<SpringUtils>}) would collide and must remain sequential.
 *
 * <p>The test spawns multiple threads, each of which independently creates a
 * {@code MockedStatic<SpringUtils>} scope and configures it to return a thread-specific value.
 * If scoping is truly thread-local, each thread sees only its own mock behavior.
 *
 * @since 2026-02-11
 */
@Tag("unit")
@Tag("fast")
@Tag("infrastructure")
@DisplayName("MockedStatic Thread Safety Validation")
class MockedStaticThreadSafetyTest {

    private static final int THREAD_COUNT = 4;

    @Test
    @DisplayName("should isolate MockedStatic scopes across concurrent threads")
    void shouldIsolateMockedStaticScopes_acrossConcurrentThreads() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        Future<?>[] futures = new Future<?>[THREAD_COUNT];

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            futures[i] = executor.submit(() -> {
                try {
                    // All threads wait here, then proceed simultaneously
                    barrier.await(5, TimeUnit.SECONDS);

                    // Each thread creates its own MockedStatic scope
                    try (MockedStatic<SpringUtils> mocked = mockStatic(SpringUtils.class)) {
                        String expectedValue = "thread-" + threadId;

                        // Configure this thread's mock to return a unique value
                        mocked.when(() -> SpringUtils.getBean(any(Class.class)))
                            .thenReturn(expectedValue);

                        // Small delay to increase chance of interleaving
                        Thread.sleep(50);

                        // Verify this thread still sees its own mock value
                        Object result = SpringUtils.getBean(String.class);
                        if (expectedValue.equals(result)) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        // Wait for all threads to complete
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get())
            .as("All %d threads should see their own MockedStatic scope", THREAD_COUNT)
            .isEqualTo(THREAD_COUNT);
        assertThat(failureCount.get())
            .as("No threads should see another thread's mock")
            .isZero();
    }
}
