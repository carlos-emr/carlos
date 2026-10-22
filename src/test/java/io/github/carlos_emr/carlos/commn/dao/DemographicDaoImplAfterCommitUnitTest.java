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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit coverage for {@link DemographicDaoImpl#runAfterCommitOrNow(Runnable)}, which keeps the HL7 A04
 * registration file from being written for a patient whose creating transaction later rolls back.
 */
@DisplayName("DemographicDaoImpl after-commit hook")
@Tag("unit")
@Tag("fast")
@Tag("dao")
class DemographicDaoImplAfterCommitUnitTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should run the action immediately when no transaction is active")
    void shouldRunImmediately_whenNoTransactionActive() {
        List<String> ran = new ArrayList<>();

        DemographicDaoImpl.runAfterCommitOrNow(() -> ran.add("a04"));

        assertThat(ran).containsExactly("a04");
    }

    @Test
    @DisplayName("should hold the action until the transaction commits")
    void shouldRunOnlyAfterCommit_whenTransactionActive() {
        List<String> ran = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        DemographicDaoImpl.runAfterCommitOrNow(() -> ran.add("a04"));
        assertThat(ran).as("not run while the transaction is open").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(ran).containsExactly("a04");
    }

    @Test
    @DisplayName("should never run the action when the transaction rolls back")
    void shouldNeverRun_whenTransactionRollsBack() {
        List<String> ran = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        DemographicDaoImpl.runAfterCommitOrNow(() -> ran.add("a04"));
        // A rollback completes the synchronization with STATUS_ROLLED_BACK and never calls afterCommit.
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(ran).isEmpty();
    }
}
