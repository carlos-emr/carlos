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

/** Reporting keeps physical/epoch fencing after clinical admission or cancellation has ended. */
final class CdsImportReportControl implements AutoCloseable {
    private final String run;
    private final long epoch;
    private final CdsImportCoordinatorService.Owner coordinator;
    private final LongSupplier nanoTime;
    private final Runnable generationBudget;
    private final Thread owner=Thread.currentThread();
    private final CdsControlPollBackoff polls=new CdsControlPollBackoff();
    private boolean closed;

    CdsImportReportControl(String run, long epoch, CdsImportCoordinatorService.Owner coordinator,
            LongSupplier nanoTime, Runnable generationBudget) {
        this.run=Objects.requireNonNull(run);
        if (run.isBlank() || epoch<0) throw new IllegalArgumentException("Invalid import report control binding");
        this.epoch=epoch; this.coordinator=Objects.requireNonNull(coordinator);
        this.nanoTime=Objects.requireNonNull(nanoTime); this.generationBudget=Objects.requireNonNull(generationBudget);
        check(true);
    }

    void requireBinding(String expectedRun, long expectedEpoch) {
        requireOpen();
        if (!run.equals(expectedRun) || epoch!=expectedEpoch) throw new IllegalArgumentException("Import report control binding differs");
    }

    void checkBudget() {
        requireOpen();
        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("REPORT_CANCELLED");
        generationBudget.run();
    }

    void check(boolean force) {
        CdsImportLedgerService.requireNoTransaction();
        checkBudget(); long now=nanoTime.getAsLong();
        if (polls.due(force, now)) {
            polls.expire();
            var current=coordinator.control(run);
            if (current==null || !current.coordinatorOwned()) throw new CdsImportException("IMPORT_COORDINATOR_LOST");
            if (!current.runPresent() || current.ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
            // Cancellation and released clinical admission must not prevent required failure receipts.
            checkBudget(); polls.succeeded(now);
        }
    }

    private void requireOpen() {
        if (closed || Thread.currentThread()!=owner) throw new IllegalStateException("CDS_REPORT_CONTROL_SCOPE_ORDER_INVALID");
    }

    @Override public void close() {
        if (closed)return;
        requireOpen(); closed=true;
    }
}
