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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Contract of {@code DemographicDaoImpl.runAfterCommitOrNow}, which keeps the HL7 A04 export of a
 * newly created patient from being written for a transaction that later rolls back.
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("fast")
@Tag("dao")
@DisplayName("Demographic DAO after-commit side effects")
class DemographicDaoImplAfterCommitUnitTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should run the action immediately when no transaction is active")
    void shouldRunActionImmediately_whenNoTransactionActive() {
        AtomicInteger runs = new AtomicInteger();

        DemographicDaoImpl.runAfterCommitOrNow(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("should hold the action until the transaction commits")
    void shouldHoldAction_untilTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        DemographicDaoImpl.runAfterCommitOrNow(runs::incrementAndGet);

        assertThat(runs).as("not run before commit").hasValue(0);
        registered().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).as("run once after commit").hasValue(1);
    }

    @Test
    @DisplayName("should drop the action when the transaction rolls back")
    void shouldDropAction_whenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        DemographicDaoImpl.runAfterCommitOrNow(runs::incrementAndGet);
        registered().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(runs).hasValue(0);
    }

    private static List<TransactionSynchronization> registered() {
        return new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
    }
}
