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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAOImpl;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in production DAO/service concurrency proof on an owned throwaway MariaDB schema. */
@Tag("integration")
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EMAIL_TEST_DB_URL", matches = ".+")
class ClinicEmailFooterMariaDbIntegrationTest {
    private final String schema = "email_footer_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection admin;
    private SessionFactory factory;
    private JpaTransactionManager manager;
    private jakarta.persistence.EntityManager entities;
    private UserPropertyDAO dao;
    private ClinicEmailFooterService service;
    private boolean created;

    @BeforeAll
    void setup() throws Exception {
        String url = System.getenv("EMAIL_TEST_DB_URL");
        assertThat(url).matches("jdbc:mysql://[^/]+/");
        String user = System.getenv("EMAIL_TEST_DB_USER");
        String password = System.getenv("EMAIL_TEST_DB_PASSWORD");
        admin = DriverManager.getConnection(url, user, password);
        try (var statement = admin.createStatement(); var rows = statement.executeQuery("SELECT VERSION()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).containsIgnoringCase("MariaDB");
        }
        try (var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            created = true;
        }
        factory = new Configuration().addAnnotatedClass(Clinic.class).addAnnotatedClass(UserProperty.class)
                .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.url", url + schema)
                .setProperty("hibernate.connection.username", user)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.dialect", io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect.class.getName())
                // Match the connection handling supplied by production HibernateJpaVendorAdapter.
                .setProperty("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
                .setProperty("hibernate.hbm2ddl.auto", "create")
                .setProperty("hibernate.show_sql", Boolean.toString(Boolean.parseBoolean(System.getenv("EMAIL_TEST_SQL_TRACE"))))
                .buildSessionFactory();
        entities = SharedEntityManagerCreator.createSharedEntityManager(factory);
        manager = new JpaTransactionManager(factory);
        // A native Configuration factory has no Spring vendor adapter to supply this automatically.
        manager.setJpaDialect(new org.springframework.orm.jpa.vendor.HibernateJpaDialect());
        var target = new UserPropertyDAOImpl();
        ReflectionTestUtils.setField(target, "entityManager", entities);
        dao = proxy(target, UserPropertyDAO.class);
        service = proxy(new ClinicEmailFooterService(dao, mock(EmailFooterLogoService.class)), ClinicEmailFooterService.class);
    }

    private <T> T proxy(Object target, Class<T> type) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }

    @AfterAll
    void cleanup() throws Exception {
        if (factory != null) factory.close();
        if (admin != null) {
            try {
                if (created) try (var statement = admin.createStatement()) {
                    statement.execute("DROP DATABASE `" + schema + "`");
                }
            } finally { admin.close(); }
        }
    }

    @BeforeEach
    void reset() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            entities.createQuery("delete from UserProperty").executeUpdate();
            entities.createQuery("delete from Clinic").executeUpdate();
            Clinic clinic = new Clinic(); clinic.setClinicName("FAKE Clinic"); entities.persist(clinic);
        });
    }

    private TransactionTemplate transaction(int isolation) {
        var transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(isolation);
        return transaction;
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void shouldSerializeFirstSave_afterAmbientEmptyRead_andRespectWinnerRollback(boolean commitWinner, boolean strictSnapshot) throws Exception {
        var readerReady = new CountDownLatch(1);
        var startSave = new CountDownLatch(1);
        var writerSaved = new CountDownLatch(1);
        var finishWriter = new CountDownLatch(1);
        var readerConnection = new AtomicLong();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var reader = workers.submit(() -> transaction(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .execute(status -> {
                        setSnapshotIsolation(strictSnapshot);
                        assertThat(dao.findClinicEmailFooter()).isEmpty();
                        entities.unwrap(org.hibernate.Session.class).doWork(connection -> {
                            try (var statement = connection.createStatement(); var row = statement.executeQuery("SELECT CONNECTION_ID()")) {
                                row.next(); readerConnection.set(row.getLong(1));
                            }
                        });
                        readerReady.countDown(); await(startSave);
                        return service.save("Reader Clinic", ClinicEmailFooterService.fingerprint(""));
                    }));
            if (!readerReady.await(10, TimeUnit.SECONDS)) {
                startSave.countDown();
                reader.get(1, TimeUnit.SECONDS); // Surface a failed transaction before reporting a barrier timeout.
                fail("Reader did not reach the transaction barrier");
            }
            var writer = workers.submit(() -> transaction(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .execute(status -> {
                        var outcome = service.save("Writer Clinic", ClinicEmailFooterService.fingerprint(""));
                        writerSaved.countDown(); await(finishWriter);
                        if (!commitWinner) status.setRollbackOnly();
                        return outcome;
                    }));
            try {
                if (!writerSaved.await(10, TimeUnit.SECONDS)) {
                    writer.get(1, TimeUnit.SECONDS);
                    fail("Writer did not reach the transaction barrier");
                }
                startSave.countDown();
                assertThat(waitingForLock(readerConnection.get())).isTrue();
                assertThat(reader.isDone()).isFalse();
            } finally { finishWriter.countDown(); startSave.countDown(); }
            assertThat(writer.get(10, TimeUnit.SECONDS)).isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            if (commitWinner && strictSnapshot) {
                assertThatThrownBy(() -> reader.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
            } else {
                assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(commitWinner
                        ? ClinicEmailFooterService.SaveResult.STALE : ClinicEmailFooterService.SaveResult.SAVED);
            }
        }
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var rows = dao.findClinicEmailFooter(); assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getValue()).isEqualTo(commitWinner ? "Writer Clinic" : "Reader Clinic");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRefuseStaleEdit_afterEarlierManagedPropertyRead(boolean strictSnapshot) throws Exception {
        assertThat(service.save("Old Clinic", ClinicEmailFooterService.fingerprint("")))
                .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
        var cached = new CountDownLatch(1); var changed = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reader = worker.submit(() -> transaction(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .execute(status -> {
                        setSnapshotIsolation(strictSnapshot);
                        var old = dao.findClinicEmailFooter().get(0);
                        assertThat(old.getValue()).isEqualTo("Old Clinic"); cached.countDown(); await(changed);
                        return service.save("Stale Clinic", ClinicEmailFooterService.fingerprint("Old Clinic"));
                    }));
            assertThat(cached.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(service.save("Winner Clinic", ClinicEmailFooterService.fingerprint("Old Clinic")))
                        .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            } finally { changed.countDown(); }
            if (strictSnapshot) {
                assertThatThrownBy(() -> reader.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
            } else {
                assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(ClinicEmailFooterService.SaveResult.STALE);
            }
        }
        assertThat(service.clinicFooter()).isEqualTo("Winner Clinic");
    }

    @Test
    void shouldUpdateDetachedCurrentRow_andCleanLegacyDuplicates_withoutChangingOtherOwners() {
        int retained = seedClinicAndOwnerProperties(true);
        assertThat(service.save("Edited Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic")))
                .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
        assertSingleClinicAndOtherOwner(retained, "Edited Clinic");
    }

    @Test
    void shouldRollBackPermittedDetachedUpdate_andDuplicateCleanup_withCallerTransaction() {
        int retained = seedClinicAndOwnerProperties(true);
        transaction(TransactionDefinition.ISOLATION_READ_COMMITTED).executeWithoutResult(status -> {
            assertThat(service.save("Rolled Back Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic")))
                    .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            status.setRollbackOnly();
        });
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var rows = dao.findClinicEmailFooter();
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getId()).isEqualTo(retained);
            assertThat(rows.get(0).getValue()).isEqualTo("Existing Clinic");
            assertThat(rows.get(1).getValue()).isEqualTo("Legacy Duplicate");
            assertOtherOwnerProperties();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCleanDuplicateInsertedAfterAmbientRead_orRefuseStrictSnapshotThenPermitFreshRetry(
            boolean strictSnapshot) throws Exception {
        int retained = seedClinicAndOwnerProperties(false);
        var read = new CountDownLatch(1);
        var inserted = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reader = worker.submit(() -> transaction(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .execute(status -> {
                        setSnapshotIsolation(strictSnapshot);
                        var prior = dao.findClinicEmailFooter();
                        assertThat(prior).hasSize(1);
                        assertThat(prior.get(0).getValue()).isEqualTo("Existing Clinic");
                        read.countDown(); await(inserted);
                        return service.save("Edited Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic"));
                    }));
            try {
                if (!read.await(10, TimeUnit.SECONDS)) {
                    inserted.countDown();reader.get(1, TimeUnit.SECONDS);
                    fail("Reader did not reach the duplicate insertion barrier");
                }
                transaction(TransactionDefinition.ISOLATION_READ_COMMITTED).executeWithoutResult(status -> {
                    entities.persist(property("email_footer_clinic_default", "", "Legacy Duplicate"));
                });
            } finally { inserted.countDown(); }
            if (strictSnapshot) {
                assertThatThrownBy(() -> reader.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
                new TransactionTemplate(manager).executeWithoutResult(status -> {
                    assertThat(dao.findClinicEmailFooter()).hasSize(2);
                    assertThat(dao.findClinicEmailFooter().get(0).getValue()).isEqualTo("Existing Clinic");
                    assertOtherOwnerProperties();
                });
                assertThat(service.save("Edited Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic")))
                        .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            } else {
                assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            }
        }
        assertSingleClinicAndOtherOwner(retained, "Edited Clinic");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUpdateCanonicalInsertedAfterAmbientEmptyRead_whenShownFingerprintMatches_orRefuseStrictSnapshot(
            boolean strictSnapshot) throws Exception {
        var read = new CountDownLatch(1);
        var inserted = new CountDownLatch(1);
        var retained = new java.util.concurrent.atomic.AtomicInteger();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reader = worker.submit(() -> transaction(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                    .execute(status -> {
                        setSnapshotIsolation(strictSnapshot);
                        assertThat(dao.findClinicEmailFooter()).isEmpty();
                        read.countDown(); await(inserted);
                        // The compose/admin page can be loaded outside this ambient transaction.
                        return service.save("Edited Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic"));
                    }));
            try {
                if (!read.await(10, TimeUnit.SECONDS)) {
                    inserted.countDown();reader.get(1, TimeUnit.SECONDS);
                    fail("Reader did not reach the canonical insertion barrier");
                }
                retained.set(seedClinicAndOwnerProperties(false));
            } finally { inserted.countDown(); }
            if (strictSnapshot) {
                assertThatThrownBy(() -> reader.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
                assertSingleClinicAndOtherOwner(retained.get(), "Existing Clinic");
                assertThat(service.save("Edited Clinic", ClinicEmailFooterService.fingerprint("Existing Clinic")))
                        .isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            } else {
                assertThat(reader.get(10, TimeUnit.SECONDS)).isEqualTo(ClinicEmailFooterService.SaveResult.SAVED);
            }
        }
        assertSingleClinicAndOtherOwner(retained.get(), "Edited Clinic");
    }

    private int seedClinicAndOwnerProperties(boolean duplicate) {
        return new TransactionTemplate(manager).execute(status -> {
            var retained = property("email_footer_clinic_default", null, "Existing Clinic");
            entities.persist(retained);
            if (duplicate) entities.persist(property("email_footer_clinic_default", "", "Legacy Duplicate"));
            entities.persist(property("email_footer_clinic_default", "999998", "Other Owner Clinic Property"));
            entities.persist(property("email_footer", "999998", "Other Owner Personal"));
            return retained.getId();
        });
    }

    private UserProperty property(String name, String owner, String value) {
        var property = new UserProperty();property.setName(name);property.setProviderNo(owner);property.setValue(value);
        return property;
    }

    private void assertSingleClinicAndOtherOwner(int retained, String value) {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var rows = dao.findClinicEmailFooter();assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getId()).isEqualTo(retained);
            assertThat(rows.get(0).getValue()).isEqualTo(value);
            assertOtherOwnerProperties();
        });
    }

    private void assertOtherOwnerProperties() {
        assertThat(dao.getProp("999998", "email_footer_clinic_default").getValue())
                .isEqualTo("Other Owner Clinic Property");
        assertThat(dao.getProp("999998", "email_footer").getValue()).isEqualTo("Other Owner Personal");
    }

    private void setSnapshotIsolation(boolean strict) {
        entities.unwrap(org.hibernate.Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                // Only this owned fixture connection; never change the database server's global mode.
                statement.execute("SET SESSION innodb_snapshot_isolation=" + (strict ? "1" : "0"));
            }
        });
    }

    private boolean waitingForLock(long connection) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            try (var statement = admin.prepareStatement("SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w "
                    + "JOIN information_schema.INNODB_TRX t ON w.requesting_trx_id=t.trx_id WHERE t.trx_mysql_thread_id=?")) {
                statement.setLong(1, connection);
                try (var rows = statement.executeQuery()) { rows.next(); if (rows.getInt(1) > 0) return true; }
            }
            Thread.sleep(250); // InnoDB information-schema cache needs an idle interval to refresh.
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timed out at owned fixture barrier"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
