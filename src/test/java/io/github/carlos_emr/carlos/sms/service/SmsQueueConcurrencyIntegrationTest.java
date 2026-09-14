package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDaoImpl;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("dao")
@Isolated
class SmsQueueConcurrencyIntegrationTest extends CarlosTestBase {
    @PersistenceUnit(unitName = "entityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldClaimOnlyOnce_whenIndependentTransactionsCompeteForOneMessage() throws Exception {
        Long id;
        try (EntityManager setup = entityManagerFactory.createEntityManager()) {
            setup.getTransaction().begin();
            SmsTransaction queued = SmsTransaction.outboundAttempt(
                    SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic concurrency test", "999998"),
                    SmsProviderType.STUB);
            setup.persist(queued);
            setup.getTransaction().commit();
            id = queued.getId();
        }
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claim(firstClaimed, releaseFirst));
            assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return claim(null, null);
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            releaseFirst.countDown();
            try (EntityManager cleanup = entityManagerFactory.createEntityManager()) {
                cleanup.getTransaction().begin();
                cleanup.createQuery("DELETE FROM SmsTransaction t WHERE t.id = :id")
                        .setParameter("id", id).executeUpdate();
                cleanup.getTransaction().commit();
            }
        }
    }

    private int claim(CountDownLatch claimed, CountDownLatch release) throws InterruptedException {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
                ReflectionTestUtils.setField(dao, "entityManager", entityManager);
                int count = dao.claimDueOutboundQueue(SmsProviderType.STUB,
                        new Date(System.currentTimeMillis() + 1_000), 1).size();
                if (claimed != null) {
                    claimed.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out releasing synthetic queue claim");
                    }
                }
                entityManager.getTransaction().commit();
                return count;
            } finally {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
            }
        }
    }
}
