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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportStatementWaits;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The import's stall window, {@code cds.import.maxDurationMillis}: how long the import worker may
 * go without reaching a checkpoint before it is treated as dead. It bounds the time since the last
 * checkpoint and nothing else; it is not a budget for the import.
 *
 * <p>Every checkpoint a scope's worker passes (a bounded read or write, a record, a unit, a report
 * row) is its proof of life, so an import that keeps making progress is never stopped for how long
 * it takes. A worker that was silent for longer than the window, because one read, lock or
 * statement never answered, is stalled for good, as the exporter treats a silent attempt
 * ({@code CDS_EXPORT_WORKER_STALLED}): the first checkpoint it reaches afterwards ends its scope
 * under {@link #WORKER_STALLED} (or {@link #REPORT_STALLED} for report generation), and every later
 * one does again, so a caught failure cannot let it carry on. The run stops like any operational
 * failure: committed units and holds are kept and the same run is resumed. A worker that never
 * returns is found by the database instead: its coordinator and maintenance sessions are silent for
 * the window plus the unwind allowance and are closed, which releases their locks exactly as for a
 * dead process ({@code CdsJdbcConnectionTimeouts.retainOwnerForDuration}).
 *
 * <p>Not thread-safe: an instance belongs to one scope on one thread.
 *
 * @since 2026-11-14
 */
final class CdsImportStallWindow {
    /** The import worker reached no checkpoint for the whole window. */
    static final String WORKER_STALLED = "IMPORT_WORKER_STALLED";

    /** Report generation reached no checkpoint for the whole window; clinical results stand. */
    static final String REPORT_STALLED = "IMPORT_REPORT_GENERATION_STALLED";

    private final long windowNanos;
    private final LongSupplier clock;
    private long last;
    private boolean stalled;

    CdsImportStallWindow(long windowMillis) {
        this(windowMillis, System::nanoTime);
    }

    /**
     * @param windowMillis the configured stall window; at least one millisecond
     * @param clock a {@link System#nanoTime()}-style clock; only differences are compared
     */
    CdsImportStallWindow(long windowMillis, LongSupplier clock) {
        if (windowMillis < 1) throw new IllegalArgumentException("Invalid CDS import stall window");
        windowNanos =
                windowMillis > Long.MAX_VALUE / 1_000_000L
                        ? Long.MAX_VALUE
                        : windowMillis * 1_000_000L;
        this.clock = Objects.requireNonNull(clock);
        last = clock.getAsLong();
    }

    /**
     * Records a checkpoint.
     *
     * @return true when the worker was silent for longer than the window before this checkpoint,
     *     or before any earlier one: a stall is sticky
     */
    boolean stalled() {
        long now = clock.getAsLong();
        if (stalled || now - last > windowNanos) {
            stalled = true;
            return true;
        }
        last = now;
        return false;
    }

    /** Records a checkpoint, ending the scope under {@link #WORKER_STALLED} after a stall. */
    void checkpoint() {
        if (stalled()) throw new CdsImportException(WORKER_STALLED);
    }

    /**
     * A database statement waited its deadline and is about to be sent again. A wait is not a
     * checkpoint and records nothing: once the window has passed since the last checkpoint the
     * wait, and the scope, end under {@code code}; until then the next wait is bounded by the
     * window, since no single wait needs to outlast it.
     *
     * @return the window in milliseconds
     */
    long waiting(String code) {
        long now = clock.getAsLong();
        if (stalled || now - last > windowNanos) {
            stalled = true;
            throw new CdsImportException(code);
        }
        return windowNanos / 1_000_000L;
    }

    /**
     * Makes this window the stall window of the calling thread's statement waits ({@link
     * CdsImportStatementWaits}) until the binding is closed; a wait past it ends under {@code code}.
     */
    CdsImportStatementWaits.Binding bindWaits(String code) {
        return CdsImportStatementWaits.observe(waited -> waiting(code));
    }
}
