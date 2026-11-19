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
 * shared ones. A PDF therefore yields a shared slot to a waiting chart only when no chart holds a slot:
 * a chart that already holds one could not take it, and the slot would sit idle while both wait.
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

    /** How often a caller that waits as long as it takes is asked whether it still waits. */
    static final Duration CHECK_INTERVAL = Duration.ofSeconds(1);

    Lease acquire(Kind kind, Duration timeout) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException("Render admission interrupted");
        synchronized (monitor) {
            var queue = queues.get(kind);
            if (queue.size() >= capacity(kind)) return null;
            Object ticket = new Object();
            queue.addLast(ticket);
            try {
                return admit(kind, ticket, System.nanoTime() + timeout.toNanos());
            } finally {
                queue.remove(ticket);
                monitor.notifyAll();
            }
        }
    }

    /**
     * {@link #acquire(Kind, Duration)} with no deadline, for a caller that waits for a slot as
     * long as it takes (the CDS export, which renders one form at a time and so never holds more
     * than one slot). It keeps its place in the queue; about once a second, outside the lock and
     * with its place kept, {@code waiting} is run, and it ends the wait by throwing: the place is
     * then given up and the exception passes through. A full queue is refused at once, as always.
     *
     * @return the lease, or null only when the queue was already full
     */
    Lease acquire(Kind kind, Runnable waiting) throws InterruptedException {
        java.util.Objects.requireNonNull(waiting);
        if (Thread.interrupted()) throw new InterruptedException("Render admission interrupted");
        var queue = queues.get(kind);
        Object ticket = new Object();
        synchronized (monitor) {
            if (queue.size() >= capacity(kind)) return null;
            queue.addLast(ticket);
        }
        try {
            while (true) {
                synchronized (monitor) {
                    var lease = admit(kind, ticket, System.nanoTime() + CHECK_INTERVAL.toNanos());
                    if (lease != null) return lease;
                }
                // Outside the lock, so no other caller waits on it: a short check (the export's
                // cancellation and lease probe).
                waiting.run();
            }
        } finally {
            synchronized (monitor) {
                queue.remove(ticket);
                monitor.notifyAll();
            }
        }
    }

    private static int capacity(Kind kind) {
        return kind == Kind.CHART ? 16 : 4;
    }

    /** With the monitor held: the lease once {@code ticket} may take a slot, or null at {@code deadline}. */
    private Lease admit(Kind kind, Object ticket, long deadline) throws InterruptedException {
        var queue = queues.get(kind);
        Kind other = kind == Kind.CHART ? Kind.PDF : Kind.CHART;
        while (true) {
            if (queue.peekFirst() == ticket) {
                // A chart's own slot costs the shared budget nothing, so it is tried first.
                if (kind == Kind.CHART && chartSlots.tryAcquire()) return grant(kind, chartSlots);
                // A running render cannot be preempted. Once the other class queues,
                // this class cannot borrow another slot while already holding one. A PDF need
                // not yield to a chart that is itself barred (a chart already holds a slot),
                // or a free shared slot would sit idle while both wait.
                boolean eligible = active.get(kind) == 0 || queues.get(other).isEmpty()
                        || (kind == Kind.PDF && active.get(Kind.CHART) > 0);
                if (eligible && slots.tryAcquire()) return grant(kind, slots);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            // Also observe releases from legacy callers holding the same semaphore.
            TimeUnit.NANOSECONDS.timedWait(monitor, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
        }
    }

    /** Must hold {@link #monitor}. */
    private Lease grant(Kind kind, Semaphore taken) {
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
