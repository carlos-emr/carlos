/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** One bounded browser budget with FIFO queues and no repeated overtaking between work classes.
 * @since 2026-11-09
 */
final class RenderAdmission {
    enum Kind { CHART, PDF }
    private final Semaphore slots;
    private final Object monitor = new Object();
    private final EnumMap<Kind, ArrayDeque<Object>> queues = new EnumMap<>(Kind.class);
    private final EnumMap<Kind, Integer> active = new EnumMap<>(Kind.class);

    RenderAdmission(Semaphore slots) {
        this.slots = slots;
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
            // A running render cannot be preempted. Once the other class queues,
            // this class cannot borrow another slot while already holding one.
            boolean eligible = queue.peekFirst() == ticket
                    && (active.get(kind) == 0 || queues.get(other).isEmpty());
            if (eligible && slots.tryAcquire()) {
                active.put(kind, active.get(kind) + 1);
                return new Lease(kind);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            // Also observe releases from legacy callers holding the same semaphore.
            TimeUnit.NANOSECONDS.timedWait(monitor, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
        }
    }

    final class Lease implements AutoCloseable {
        private final Kind kind;
        private boolean closed;
        private Lease(Kind kind) { this.kind = kind; }
        @Override
        public void close() {
            synchronized (monitor) {
                if (closed) return;
                closed = true;
                active.put(kind, active.get(kind) - 1);
                slots.release();
                monitor.notifyAll();
            }
        }
    }
}
