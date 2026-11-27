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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * The chain locks on a real InnoDB (#6391): the statements are written for the index they walk, a
 * lock on one accession does not hold up an unrelated one, and a real lock wait timeout is recognised
 * as the lock failure the filing retries.
 *
 * <p>The name carries no "Dao" on purpose: the generated-schema lane selects every {@code *Dao*}
 * integration test and allows no skip, and this one needs the Flyway schema; the Flyway lane lists
 * its tests by name (the precedent is {@code CaseManagementNoteLegacyFlagsMariaDbIntegrationTest}).</p>
 *
 * <p>The tests that commit rows (a second connection cannot see an uncommitted one) delete them again.</p>
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

    /**
     * The {@code possible_keys} column of the first EXPLAIN row. On a near-empty table the optimizer
     * may choose a scan whatever the indexes are, so what is asserted is that the index is available
     * to the statement; that an unrelated lock is not held up is shown by the behavioural test below.
     */
    private String indexesAvailableFor(String explain) {
        Object[] row = (Object[]) entityManager.createNativeQuery(explain).getResultList().get(0);
        // EXPLAIN columns: id, select_type, table, type, possible_keys, key, ... (a partitions column
        // sits after table on some versions): find "possible_keys" by the row's width.
        int possibleKeys = row.length >= 12 ? 5 : 4;
        return String.valueOf(row[possibleKeys]);
    }

    @Test
    void shouldHaveTheAccessionIndexAvailable_whenLockingByAccession() {
        assertThat(indexesAvailableFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE accessionNum = 'ACC-EXPLAIN'"))
                .contains("accession_index");
    }

    @Test
    void shouldHaveTheFillerOrderNumberIndexAvailable_whenLockingByFillerOrderNumber() {
        assertThat(indexesAvailableFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE filler_order_num = 'FILL-EXPLAIN'"))
                .contains("idx_hl7TextInfo_filler_order_num");
    }

    @Test
    void shouldHaveTheLabNumberIndexAvailable_whenLockingTheReportsOwnRow() {
        assertThat(indexesAvailableFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE lab_no = 101"))
                .contains("labno_index");
    }

    @Test
    void shouldHaveTheKeyValueIndexAvailable_whenFindingAReportsMeasurements() {
        assertThat(indexesAvailableFor("EXPLAIN SELECT * FROM measurementsExt WHERE keyval = 'lab_no' AND val = '101'"))
                .contains("measurements_ext_keyval_val");
    }

    private void commit(String accession) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> stored(accession, null));
    }

    private void deleteCommitted(String... accessions) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String accession : accessions) {
                entityManager.createNativeQuery("DELETE FROM hl7TextInfo WHERE accessionNum = ?1")
                        .setParameter(1, accession).executeUpdate();
            }
        });
    }

    /**
     * Enough rows for the optimizer to prefer the accession index to a scan (on two rows it may
     * scan, and then every lock would touch every row whatever the statement is), committed so
     * that the statistics see them. Accessions and filler numbers share one prefix, so they can be
     * deleted by it. Their lab numbers are 8,000,001 to 8,002,000, well above the ones the other
     * tests of this class use.
     */
    private void commitFillerRows(String prefix) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "INSERT INTO hl7TextInfo (lab_no, report_status, accessionNum, filler_order_num) "
                            + "SELECT 8000000 + seq, 'F', CONCAT(?1, seq), CONCAT(?1, seq) FROM seq_1_to_2000")
                    .setParameter(1, prefix).executeUpdate();
        });
        // ANALYZE TABLE answers with a result set.
        new TransactionTemplate(transactions).executeWithoutResult(
                status -> entityManager.createNativeQuery("ANALYZE TABLE hl7TextInfo").getResultList());
    }

    private void deleteFillerRows(String prefix) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> entityManager
                .createNativeQuery("DELETE FROM hl7TextInfo WHERE accessionNum LIKE ?1")
                .setParameter(1, prefix + "%").executeUpdate());
    }

    /**
     * What the DAO's lock of one accession leaves free: with the chain's rows held through
     * {@link Hl7TextInfoChainLockDao#lockByAccession} by another transaction, locking an unrelated
     * accession must not wait. The held lock is taken by the DAO itself (a raw statement would only
     * show MariaDB's plan for that statement), on a second thread parked on a latch.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldNotHoldUpAnUnrelatedAccession_whenTheDaoHoldsAnotherOne() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String held = "ACC-HELD-" + suffix;
        String other = "ACC-OTHER-" + suffix;
        String fillers = "ACC-FILL-" + suffix + "-";
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            commit(held);
            commit(other);
            commitFillerRows(fillers);

            Future<?> holdsTheRow = holder.submit(() -> new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        locks.lockByAccession(held);
                        holding.countDown();
                        try {
                            release.await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }));
            assertThat(holding.await(60, TimeUnit.SECONDS)).as("the held lock was taken").isTrue();

            // One second: a lock that waited for the held rows would time out here.
            Throwable thrown = catchThrowable(() -> new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        entityManager.createNativeQuery(
                                "SET @carlos_previous_lock_wait = @@SESSION.innodb_lock_wait_timeout")
                                .executeUpdate();
                        try {
                            entityManager.createNativeQuery("SET SESSION innodb_lock_wait_timeout=1").executeUpdate();
                            assertThat(locks.lockByAccession(other)).hasSize(1);
                        } finally {
                            restoreLockWaitTimeout();
                        }
                    }));

            release.countDown();
            holdsTheRow.get(60, TimeUnit.SECONDS);
            assertThat(thrown).as("an unrelated accession must lock without waiting").isNull();
        } finally {
            release.countDown();
            holder.shutdownNow();
            try {
                // The holder's row locks are released when its transaction ends; a delete before that
                // would wait for them (up to the server's lock wait timeout).
                holder.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            try {
                deleteFillerRows(fillers);
            } finally {
                deleteCommitted(held, other);
            }
        }
    }

    /** Puts the session's lock wait setting back; never throws, so it cannot replace what a test asserts on. */
    private void restoreLockWaitTimeout() {
        try {
            entityManager.createNativeQuery("SET SESSION innodb_lock_wait_timeout = @carlos_previous_lock_wait")
                    .executeUpdate();
        } catch (RuntimeException ignored) {
            // The connection goes back to the pool with the test's own transaction; a failure here is
            // not what is being tested.
        }
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
        try {
            // Committed, so that a second connection sees the row.
            commit(accession);

            try (Connection holder = dataSource.getConnection()) {
                try {
                    holder.setAutoCommit(false);
                    try (PreparedStatement lock = holder.prepareStatement(
                            "SELECT lab_no FROM hl7TextInfo WHERE accessionNum = ? FOR UPDATE")) {
                        lock.setString(1, accession);
                        lock.executeQuery().close();
                    }

                    Throwable thrown = catchThrowable(() -> new TransactionTemplate(transactions)
                            .executeWithoutResult(status -> {
                                // The pooled connection is given back as it was: the setting is read first
                                // (into a session variable) and restored afterwards on the same connection.
                                entityManager.createNativeQuery(
                                        "SET @carlos_previous_lock_wait = @@SESSION.innodb_lock_wait_timeout")
                                        .executeUpdate();
                                try {
                                    // One second, not the server's fifty: a wrong lock fails in seconds.
                                    entityManager.createNativeQuery("SET SESSION innodb_lock_wait_timeout=1")
                                            .executeUpdate();
                                    locks.lockByAccession(accession);
                                } finally {
                                    // Its own try/catch: a failure here must not replace the lock exception.
                                    restoreLockWaitTimeout();
                                }
                            }));

                    assertThat(thrown).as("the second lock must time out while the row is held").isNotNull();
                    assertThat(LabMeasurementFilingService.isLockFailure(thrown))
                            .as(String.valueOf(thrown)).isTrue();
                } finally {
                    holder.rollback();
                }
            }
        } finally {
            deleteCommitted(accession);
        }
    }
}
