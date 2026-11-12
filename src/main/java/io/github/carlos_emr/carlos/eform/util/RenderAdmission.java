/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** One bounded browser budget with FIFO queues and no repeated overtaking between work classes.
 *
 * <p>Charts may also have slots of their own (#5853). A chart takes one of those first, so it never
 * waits behind PDFs while one is free, and a PDF never takes one. Only when they are busy does a chart
 * compete for the shared slots, under the same rule as before. A chart holding its own slot counts as
 * holding a slot for that rule, so while PDFs wait, charts keep to their own slots and PDFs keep the
 * shared ones.
 * @since 2026-11-09
 */
final class RenderAdmission {
    enum Kind { CHART, PDF }
    private final Semaphore slots;
    private final Semaphore chartSlots;
    private final Object monitor = new Object();
    private final EnumMap<Kind, ArrayDeque<Object>> queues = new EnumMap<>(Kind.class);
    private final EnumMap<Kind, Integer> active = new EnumMap<>(Kind.class);

    /** Shared slots only: no chart has a slot of its own. */
    RenderAdmission(Semaphore slots) {
        this(slots, new Semaphore(0));
    }

    /**
     * @param slots      the browsers both classes share
     * @param chartSlots browsers only charts may use, tried before the shared ones
     */
    RenderAdmission(Semaphore slots, Semaphore chartSlots) {
        this.slots = slots;
        this.chartSlots = chartSlots;
        for (Kind kind : Kind.values()) {
            queues.put(kind, new ArrayDeque<>());
            active.put(kind, 0);
        }
    }

    Lease acquire(Kind kind, Duration timeout) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException("Render admission interrupted");
        synchronized (monitor) {
            var queue = queues.get(kind);
            if (queue.size() >= (kind == Kind.CHART ? 16 : 4)) return null;
            Object ticket = new Object();
            queue.addLast(ticket);
            long deadline = System.nanoTime() + timeout.toNanos();
            Kind other = kind == Kind.CHART ? Kind.PDF : Kind.CHART;
            try {
                while (true) {
                    if (queue.peekFirst() == ticket) {
                        // A chart's own slot costs the shared budget nothing, so it is tried first.
                        if (kind == Kind.CHART && chartSlots.tryAcquire()) return admit(kind, chartSlots);
                        // A running render cannot be preempted. Once the other class queues,
                        // this class cannot borrow another slot while already holding one.
                        boolean eligible = active.get(kind) == 0 || queues.get(other).isEmpty();
                        if (eligible && slots.tryAcquire()) return admit(kind, slots);
                    }
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) return null;
                    // Also observe releases from legacy callers holding the same semaphore.
                    TimeUnit.NANOSECONDS.timedWait(monitor, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
                }
            } finally {
                queue.remove(ticket);
                monitor.notifyAll();
            }
        }
    }

    /** Must hold {@link #monitor}. */
    private Lease admit(Kind kind, Semaphore taken) {
        active.put(kind, active.get(kind) + 1);
        return new Lease(kind, taken);
    }

    final class Lease implements AutoCloseable {
        private final Kind kind;
        private final Semaphore taken;
        private boolean closed;
        private Lease(Kind kind, Semaphore taken) {
            this.kind = kind;
            this.taken = taken;
        }
        @Override
        public void close() {
            synchronized (monitor) {
                if (closed) return;
                closed = true;
                active.put(kind, active.get(kind) - 1);
                taken.release();
                monitor.notifyAll();
            }
        }
    }
}
