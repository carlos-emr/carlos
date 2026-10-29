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
package io.github.carlos_emr.carlos.test.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs the same check on several threads released together, for code that must be safe to share
 * between concurrent requests.
 *
 * <p>Each thread runs its rounds back to back so the calls overlap as much as the scheduler
 * allows. A check is given its thread and round numbers so it can work on values no other call
 * uses: a shared mutable formatter then shows up as one call returning another call's value or
 * failing outright, not as an identical answer that hides the race.</p>
 *
 * @since 2026-10-29
 */
public final class ConcurrentRounds {

    /** One round of a check. */
    @FunctionalInterface
    public interface Round {
        /**
         * Runs one round.
         *
         * @param thread int the thread's number, from 0
         * @param round int the round's number on that thread, from 0
         * @throws Exception any failure, an assertion error included, which is recorded and ends
         *     that thread's rounds
         */
        void run(int thread, int round) throws Exception;
    }

    private ConcurrentRounds() {
    }

    /**
     * Runs {@code rounds} rounds of {@code check} on each of {@code threads} threads.
     *
     * <p>A thread stops at its first failure, so the result holds at most one failure per
     * thread.</p>
     *
     * @param threads int the number of threads, released together
     * @param rounds int the rounds each thread runs
     * @param check Round the check to run
     * @return List&lt;String&gt; a description of each failure; empty when every round passed
     * @throws InterruptedException if interrupted while waiting for the threads
     * @throws IllegalStateException if the threads have not finished within a minute
     */
    public static List<String> run(int threads, int rounds, Round check) throws InterruptedException {
        Queue<String> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int round = 0; round < rounds; round++) {
                            try {
                                check.run(thread, round);
                            } catch (Throwable failure) {
                                failures.add("thread " + thread + ", round " + round + ": " + failure);
                                return;
                            }
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        failures.add("thread " + thread + " was interrupted");
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                throw new IllegalStateException("concurrent rounds did not finish within a minute");
            }
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(failures);
    }
}
