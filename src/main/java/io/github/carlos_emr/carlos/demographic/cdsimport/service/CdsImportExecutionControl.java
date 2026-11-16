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

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * One run/epoch-bound I/O scope shares fresh ownership polling across all bounded reads. Every
 * bounded read is a checkpoint of the attempt's stall window ({@link CdsImportStallWindow}); the
 * scope keeps no clock of its own, so a long file is read to its end while it keeps moving.
 */
final class CdsImportExecutionControl implements AutoCloseable {
    private final String run;
    private final long epoch;
    private final CdsImportCoordinatorService.Owner coordinator;
    private final CdsImportMaintenanceService.Owner maintenance;
    private final LongSupplier nanoTime;
    private final Runnable attemptCheckpoint;
    private final Thread owner = Thread.currentThread();
    private final CdsFieldParser.ControlScope fields;
    private final CdsControlPollBackoff polls = new CdsControlPollBackoff();
    private boolean closed;

    CdsImportExecutionControl(String run, long epoch, CdsImportCoordinatorService.Owner coordinator,
            LongSupplier nanoTime, Runnable attemptCheckpoint, CdsFieldParser parser,
            CdsImportMaintenanceService.Owner maintenance) {
        this.run = Objects.requireNonNull(run);
        if (run.isBlank() || epoch < 1)
            throw new IllegalArgumentException("Invalid import I/O control binding");
        this.epoch = epoch;
        this.coordinator = Objects.requireNonNull(coordinator);
        this.maintenance = Objects.requireNonNull(maintenance);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.attemptCheckpoint = Objects.requireNonNull(attemptCheckpoint);
        Objects.requireNonNull(parser);
        // A failing initial probe cannot leave an installed ThreadLocal scope.
        check(true);
        fields = parser.controlEveryCheckpoint(() -> check(false));
    }

    void requireBinding(String expectedRun, long expectedEpoch) {
        requireOpen();
        if (!run.equals(expectedRun) || epoch != expectedEpoch)
            throw new IllegalArgumentException("Import I/O control binding differs");
    }

    void check(boolean force) {
        requireOpen();
        long now = localCheckpoint();
        if (polls.due(force, now)) {
            polls.expire();
            var current = coordinator.control(run);
            if (current == null || !current.coordinatorOwned())
                throw new CdsImportException("IMPORT_COORDINATOR_LOST");
            if (!current.runPresent() || current.ownerEpoch() != epoch || !current.admissionOwned())
                throw new CdsImportException("OWNER_FENCED");
            if (current.cancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
            maintenance.heartbeat();
            localCheckpoint();
            // Failure never advances this deadline or becomes a cached success.
            polls.succeeded(now);
        }
    }

    private long localCheckpoint() {
        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("IMPORT_CANCELLED");
        attemptCheckpoint.run();
        return nanoTime.getAsLong();
    }

    private void requireOpen() {
        if (closed || Thread.currentThread() != owner)
            throw new IllegalStateException("CDS_IO_CONTROL_SCOPE_ORDER_INVALID");
    }

    @Override public void close() {
        if (closed) return;
        requireOpen();
        fields.close();
        closed = true;
    }
}
