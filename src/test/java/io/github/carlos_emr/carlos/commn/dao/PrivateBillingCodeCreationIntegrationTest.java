/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.SecObjectName;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real DAO writes and overlapping Spring JPA transactions in an owned, isolated database. */
@Tag("integration")
@Tag("billing")
class PrivateBillingCodeCreationIntegrationTest {
    private static EntityManagerFactory factory;
    private static JpaTransactionManager transactions;

    @BeforeAll
    static void openDatabase() {
        factory = new Configuration().addAnnotatedClass(BillingService.class)
                .addAnnotatedClass(BillingPercLimit.class).addAnnotatedClass(SecObjectName.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:private_codes_" + UUID.randomUUID()
                        + ";MODE=MySQL;LOCK_TIMEOUT=10000")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.connection.pool_size", "4")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        transactions = new JpaTransactionManager(factory);
    }

    @AfterAll
    static void closeDatabase() {
        if (factory != null) factory.close();
    }

    @BeforeEach
    void resetOwnedDatabase() {
        write(em -> {
            em.createQuery("DELETE FROM BillingService").executeUpdate();
            em.createQuery("DELETE FROM SecObjectName").executeUpdate();
            SecObjectName anchor = new SecObjectName();
            anchor.setId("_admin.billing");
            em.persist(anchor);
            return null;
        });
    }

    private static <T> T write(Function<EntityManager, T> body) {
        return new TransactionTemplate(transactions).execute(tx ->
                body.apply(EntityManagerFactoryUtils.getTransactionalEntityManager(factory)));
    }

    private static ServiceCodePersister service(EntityManager em) {
        var proxy = new ProxyFactory(new ServiceCodePersister(dao(em), null));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (ServiceCodePersister) proxy.getProxy();
    }

    private static BillingServiceDao dao(EntityManager em) {
        BillingServiceDaoImpl dao = new BillingServiceDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var proxy = new ProxyFactory(dao);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (BillingServiceDao) proxy.getProxy();
    }

    private static ServiceCodePersister.PrivateCodeMutationResult add(EntityManager em, String description) {
        return service(em).saveOrDeletePrivateCode(new ServiceCodePersister.PrivateCodeMutationRequest(
                "Save", "add_FAKE", "FAKE", description, "31.50", "2026-05-01", "0"));
    }

    private static List<BillingService> rows() {
        return write(em -> em.createQuery("SELECT b FROM BillingService b ORDER BY b.id", BillingService.class)
                .getResultList());
    }

    @Test
    void shouldKeepOneOriginalRow_whenTheAddSubmissionIsRetried() {
        assertThat(write(em -> add(em, "First save")).message()).contains("is added.");
        var retry = write(em -> add(em, "Retry must not replace this"));
        assertThat(retry.alert()).isEqualTo("error");
        assertThat(retry.action()).isEqualTo("search");
        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row.getServiceCode()).isEqualTo("_FAKE");
            assertThat(row.getDescription()).isEqualTo("First save");
            assertThat(row.getValue()).isEqualTo("31.50");
        });
    }

    @ParameterizedTest
    @MethodSource("overlappingAdds")
    void shouldSerializeOverlappingAdds_untilTheFirstTransactionCompletes(boolean rollBackFirst, boolean snapshot)
            throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
                // Hold only the anchor while the table is empty: the waiter cannot be blocked by a code row.
                em.find(SecObjectName.class, "_admin.billing", jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                firstLocked.countDown();
                await(releaseFirst);
                var result = add(em, "First save");
                if (rollBackFirst) tx.setRollbackOnly();
                return result;
            }));
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> write(em -> {
                // Establish an ordinary read before waiting for the creation lock.
                if (snapshot) em.createQuery("SELECT COUNT(b) FROM BillingService b").getSingleResult();
                secondStarted.countDown();
                return add(em, "Second save");
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).message()).contains("is added.");
            try {
                var secondResult = second.get(10, TimeUnit.SECONDS);
                assertThat(secondResult.alert()).isEqualTo(rollBackFirst ? "info" : "error");
            } catch (java.util.concurrent.ExecutionException conflict) {
                if (rollBackFirst || !snapshot) throw conflict;
                // MariaDB snapshot isolation can refuse the stale transaction instead of returning rows.
                // The web action maps this typed, rolled-back refusal to HTTP 409, never another insert.
                assertThat(conflict.getCause()).isInstanceOfAny(jakarta.persistence.OptimisticLockException.class,
                        org.springframework.dao.ConcurrencyFailureException.class);
            }
            assertThat(rows()).singleElement().extracting(BillingService::getDescription)
                    .isEqualTo(rollBackFirst ? "Second save" : "First save");
        } finally {
            releaseFirst.countDown();
            workers.shutdown();
            if (!workers.awaitTermination(15, TimeUnit.SECONDS)) workers.shutdownNow();
        }
    }

    static java.util.stream.Stream<Arguments> overlappingAdds() {
        return java.util.stream.Stream.of(Arguments.of(false, false), Arguments.of(false, true),
                Arguments.of(true, false), Arguments.of(true, true));
    }

    @Test
    void shouldRequireAnExistingTransaction_forTheAtomicDaoOperation() {
        try (EntityManager em = factory.createEntityManager()) {
            BillingService code = new BillingService();
            code.setServiceCode("_FAKE");
            assertThatThrownBy(() -> dao(em).persistPrivateCodeIfAbsent(code))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThat(rows()).isEmpty();
    }

    @Test
    void shouldRefuseCreationWithoutTheCoordinationRow_withoutWritingAnything() {
        write(em -> {
            em.remove(em.find(SecObjectName.class, "_admin.billing"));
            return null;
        });
        assertThatThrownBy(() -> write(em -> add(em, "Not saved")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("coordination row is missing");
        assertThat(rows()).isEmpty();
    }

    @Test
    void shouldLeaveNormalDatedHistoryAndPrivateUpdatesWorking() {
        write(em -> {
            ServiceCodePersister persister = service(em);
            persister.addCodeByStr("FAKE", "First fee", "10.00", "0.00", "2026-01-01", "0");
            persister.addCodeByStr("FAKE", "Later fee", "20.00", "0.00", "2026-05-01", "0");
            add(em, "Private fee");
            return null;
        });
        write(em -> service(em).saveOrDeletePrivateCode(new ServiceCodePersister.PrivateCodeMutationRequest(
                "Save", "edit_FAKE", "FAKE", "Updated private fee", "41.50", "2026-06-01", "0")));
        var historical = write(em -> service(em).saveOrAdd(new ServiceCodePersister.AddEditServiceCodeRequest(
                "Save", "add_FAKE", "_FAKE", null, "Historical private fee", "11.00", "0.00",
                "2026-01-01", "9999-12-31", false, "-1", null, null)));
        assertThat(historical.alert()).isEqualTo("success");
        assertThat(rows()).hasSize(4);
        assertThat(rows().stream().filter(row -> "FAKE".equals(row.getServiceCode())).toList())
                .extracting(BillingService::getDescription).containsExactly("First fee", "Later fee");
        assertThat(rows().stream().filter(row -> "_FAKE".equals(row.getServiceCode())).toList())
                .extracting(BillingService::getDescription)
                .containsExactly("Updated private fee", "Historical private fee");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test transaction timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
