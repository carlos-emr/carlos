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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

/**
 * Paces the durable owner, fencing and cancellation reads that bounded import I/O issues between its
 * forced boundaries.
 *
 * <p>A scope's first read is due at once. After each successful read the wait before the next one
 * doubles, from 250ms up to a 2s cap, so a long file or a slow disk costs at most one SQL statement
 * every two seconds instead of four per second, while a cancellation or a lost owner is still seen
 * within about two seconds. Local interrupt, deadline and attempt-budget checks are not paced by this
 * class and keep running on every bounded read.</p>
 *
 * <p>Callers call {@link #expire()} before the read and {@link #succeeded(long)} only after it proved
 * the scope still owned, uncancelled and in budget, so a failed or refused read is never remembered
 * as a success and the next check reads again. Not thread-safe: each instance belongs to one scope
 * on one thread.</p>
 *
 * @since 2026-10-30
 */
final class CdsControlPollBackoff {
    /** Wait after a scope's first successful read. */
    static final long FIRST_WAIT_NANOS = 250_000_000L;
    /** Longest wait between two unforced reads. */
    static final long MAXIMUM_WAIT_NANOS = 2_000_000_000L;

    private long lastSuccess;
    private long waitNanos;
    private boolean current;

    /**
     * @param force a boundary that must read durable state now
     * @param now the caller's {@link System#nanoTime()}-style reading; only differences are compared,
     *            so negative values and wraparound are safe
     * @return whether the caller must issue the durable read now
     */
    boolean due(boolean force, long now) {
        return force || !current || now - lastSuccess >= waitNanos;
    }

    /** Forgets the remembered success, so the next {@link #due} check reads durable state again. */
    void expire() {
        current = false;
    }

    /**
     * Remembers a successful read that started at {@code now} and doubles the next wait, up to the cap.
     *
     * @param now the same clock reading that was passed to {@link #due} for this read
     */
    void succeeded(long now) {
        waitNanos = waitNanos == 0 ? FIRST_WAIT_NANOS : Math.min(MAXIMUM_WAIT_NANOS, waitNanos * 2);
        lastSuccess = now;
        current = true;
    }
}
