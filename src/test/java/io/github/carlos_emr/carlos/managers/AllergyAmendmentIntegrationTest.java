/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AllergyAmendmentIntegrationTest extends CarlosTestBase {
    private static final AtomicInteger DEMOGRAPHICS = new AtomicInteger(892000);
    @Autowired private AllergyDao allergies;
    @Autowired private PartialDateDao dates;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;
    private final LoggedInInfo login = mock(LoggedInInfo.class);

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private AllergyManager manager(PartialDateDao partialDates) {
        AllergyManagerImpl target = new AllergyManagerImpl();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), eq("_allergy"), eq("w"), anyInt())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        ReflectionTestUtils.setField(target, "allergyDao", allergies);
        ReflectionTestUtils.setField(target, "partialDateDao", partialDates);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(transactionManager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(advice);
        return (AllergyManager) factory.getProxy();
    }

    private Allergy allergy(int demographic, String severity) {
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographic);
        allergy.setDescription("Owned amendment fixture");
        allergy.setReaction("Fixture reaction");
        allergy.setTypeCode(0);
        allergy.setSeverityOfReaction(severity);
        allergy.setEntryDate(new Date());
        allergy.setStartDate(new Date());
        allergy.setStartDateFormat(PartialDate.YEARONLY);
        return allergy;
    }

    private Integer original(int demographic) {
        return transaction().execute(status -> {
            Allergy original = allergy(demographic, "2");
            entityManager.persist(original);
            return original.getId();
        });
    }

    private void cleanup(int demographic) {
        transaction().executeWithoutResult(status -> {
            entityManager.createQuery("delete from PartialDate p where p.tableName=:table and p.tableId in (select a.id from Allergy a where a.demographicNo=:demo)")
                    .setParameter("table", PartialDate.ALLERGIES).setParameter("demo", demographic).executeUpdate();
            entityManager.createQuery("delete from Allergy where demographicNo=:demo").setParameter("demo", demographic).executeUpdate();
        });
    }

    @Test
    void shouldKeepOneActiveReplacement_whenTwoTransactionsAmendTheSameOriginal() throws Exception {
        int demographic = DEMOGRAPHICS.incrementAndGet();
        Integer id = original(demographic);
        AllergyManager manager = manager(dates);
        CountDownLatch firstSaved = new CountDownLatch(1), secondRead = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> transaction().execute(status -> {
                boolean changed = manager.amendAllergy(login, id, allergy(demographic, "1"));
                firstSaved.countDown();
                await(releaseFirst);
                return changed;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> transaction().execute(status -> {
                // Model the action's earlier ownership read: this context can hold stale state.
                assertThat(entityManager.find(Allergy.class, id).getArchived()).isFalse();
                secondRead.countDown();
                return manager.amendAllergy(login, id, allergy(demographic, "3"));
            }));
            assertThat(secondRead.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
            transaction().executeWithoutResult(status -> {
                assertThat(entityManager.find(Allergy.class, id).getArchived()).isTrue();
                var active = allergies.findActiveAllergies(demographic);
                assertThat(active).hasSize(1);
                assertThat(active.getFirst().getSeverityOfReaction()).isEqualTo("1");
                assertThat(dates.getFormat(PartialDate.ALLERGIES, active.getFirst().getId(), PartialDate.ALLERGIES_STARTDATE)).isEqualTo(PartialDate.YEARONLY);
                assertThat(allergies.findAllergies(demographic)).hasSize(2);
            });
        } finally {
            releaseFirst.countDown();
            workers.shutdown();
            if (!workers.awaitTermination(15, TimeUnit.SECONDS)) workers.shutdownNow();
            cleanup(demographic);
        }
    }

    @Test
    void shouldRollBackArchiveAndReplacement_whenPartialDatePersistenceFails() {
        int demographic = DEMOGRAPHICS.incrementAndGet();
        Integer id = original(demographic);
        PartialDateDao failingDates = mock(PartialDateDao.class);
        doThrow(new IllegalStateException("injected partial-date failure")).when(failingDates)
                .setPartialDate(eq(PartialDate.ALLERGIES), anyInt(), eq(PartialDate.ALLERGIES_STARTDATE), anyString());
        try {
            assertThatThrownBy(() -> manager(failingDates).amendAllergy(login, id, allergy(demographic, "1")))
                    .isInstanceOf(IllegalStateException.class);
            transaction().executeWithoutResult(status -> {
                assertThat(entityManager.find(Allergy.class, id).getArchived()).isFalse();
                assertThat(allergies.findAllergies(demographic)).hasSize(1);
            });
        } finally { cleanup(demographic); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Transaction wait timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
