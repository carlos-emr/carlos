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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional boundary for DB-only finalization after OHIP disk files have
 * been written successfully by {@link BillingOnDiskService}.
 *
 * @since 2026-05-02
 */
@Service
public class BillingOnDiskTransactionService {

    /** Completion acknowledgement used to protect files when a database commit is uncertain. */
    public static final class Outcome {
        private boolean mayHaveCommitted;

        /** Whether rollback was not confirmed after finalization started. */
        public boolean mayHaveCommitted() { return mayHaveCommitted; }

        private void observe() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    mayHaveCommitted = status != STATUS_ROLLED_BACK;
                }
            });
            mayHaveCommitted = true;
        }
    }

    /**
     * Finalizes one generated disk writer in a single DB transaction.
     *
     * @param writer prototype claim-file writer that staged billed-header updates.
     * @param diskId generated disk-name id to update with final totals.
     */
    @Transactional
    public void finalizeGeneratedDisk(OhipClaimFileService writer, int diskId) {
        finalizeGeneratedDisk(writer, diskId, new Outcome());
    }

    /** Finalizes one writer and records the actual transaction completion outcome. */
    @Transactional
    public void finalizeGeneratedDisk(OhipClaimFileService writer, int diskId, Outcome outcome) {
        outcome.observe();
        writer.finalizeGeneratedDisk();
        writer.updateDisknameSum(diskId);
    }

    /**
     * Finalizes all generated disk writers atomically for a group disk.
     *
     * @param writers prototype claim-file writers that staged billed-header updates.
     * @param diskId generated disk-name id to update with final totals.
     */
    @Transactional
    public void finalizeGeneratedDisks(List<OhipClaimFileService> writers, int diskId) {
        finalizeGeneratedDisks(writers, diskId, new Outcome());
    }

    /** Finalizes a group and records whether the transaction committed, rolled back, or is uncertain. */
    @Transactional
    public void finalizeGeneratedDisks(List<OhipClaimFileService> writers, int diskId, Outcome outcome) {
        outcome.observe();
        for (OhipClaimFileService writer : writers) {
            writer.finalizeGeneratedDisk();
            writer.updateDisknameSum(diskId);
        }
    }
}
