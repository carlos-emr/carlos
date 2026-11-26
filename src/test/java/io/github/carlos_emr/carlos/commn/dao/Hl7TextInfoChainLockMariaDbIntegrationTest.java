/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.lab.ca.all.LabMeasurementFilingService;

/**
 * The chain locks on a real InnoDB (#6391): each statement walks the index it was written for and
 * not the whole table, because a locking scan under REPEATABLE READ locks every row it passes, and a
 * real lock wait timeout is recognised as the lock failure the filing retries.
 *
 * <p>The name carries no "Dao" on purpose: the generated-schema lane selects every {@code *Dao*}
 * integration test and allows no skip, and this one needs the Flyway schema; the Flyway lane lists
 * its tests by name (the precedent is {@code CaseManagementNoteLegacyFlagsMariaDbIntegrationTest}).</p>
 */
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
class Hl7TextInfoChainLockMariaDbIntegrationTest extends Hl7TextInfoChainLockDaoIntegrationTest {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactions;

    /** The {@code key} column of the first EXPLAIN row: the index the optimizer chose, or null. */
    private String indexChosenFor(String explain) {
        Object[] row = (Object[]) entityManager.createNativeQuery(explain).getResultList().get(0);
        // EXPLAIN columns: id, select_type, table, type, possible_keys, key, key_len, ref, rows, Extra
        // (a partitions column sits after table on some versions): find "key" by the row's width.
        int keyColumn = row.length >= 12 ? 6 : 5;
        return (String) row[keyColumn];
    }

    @Test
    void shouldUseTheAccessionIndex_whenLockingByAccession() {
        stored("ACC-EXPLAIN", null);

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE accessionNum = 'ACC-EXPLAIN'"))
                .isEqualTo("accession_index");
    }

    @Test
    void shouldUseTheFillerOrderNumberIndex_whenLockingByFillerOrderNumber() {
        stored("ACC-EXPLAIN-2", "FILL-EXPLAIN");

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE filler_order_num = 'FILL-EXPLAIN'"))
                .isEqualTo("idx_hl7TextInfo_filler_order_num");
    }

    @Test
    void shouldUseTheLabNumberIndex_whenLockingTheReportsOwnRow() {
        stored("ACC-EXPLAIN-3", null);

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE lab_no = 101"))
                .isEqualTo("labno_index");
    }

    @Test
    void shouldUseTheKeyValueIndex_whenFindingAReportsMeasurements() {
        assertThat(indexChosenFor("EXPLAIN SELECT * FROM measurementsExt WHERE keyval = 'lab_no' AND val = '101'"))
                .isEqualTo("measurements_ext_keyval_val");
    }

    /**
     * A real lock wait: one connection holds the chain's row, the lock of a second one waits out
     * {@code innodb_lock_wait_timeout} and fails. What the filing sees must be recognised as a lock
     * failure whatever exception type the persistence layer gives it (nothing here translates JPA
     * exceptions to Spring's).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRecogniseARealLockWaitTimeoutAsALockFailure_whenAnotherConnectionHoldsTheRow() throws Exception {
        String accession = "ACC-REAL-LOCK-" + System.nanoTime();
        // Committed, so that a second connection sees the row.
        new TransactionTemplate(transactions).executeWithoutResult(status -> stored(accession, null));

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT lab_no FROM hl7TextInfo WHERE accessionNum = ? FOR UPDATE")) {
                lock.setString(1, accession);
                lock.executeQuery().close();
            }

            Throwable thrown = catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                // One second, not the server's fifty: a wrong lock fails in seconds.
                entityManager.createNativeQuery("SET SESSION innodb_lock_wait_timeout=1").executeUpdate();
                locks.lockByAccession(accession);
            }));
            holder.rollback();

            assertThat(thrown).as("the second lock must time out while the row is held").isNotNull();
            assertThat(LabMeasurementFilingService.isLockFailure(thrown)).as(String.valueOf(thrown)).isTrue();
        }
    }
}
