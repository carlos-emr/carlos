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
            } finally {
                queue.remove(ticket);
                monitor.notifyAll();
            }
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
