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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Real-transaction concurrency coverage for {@link MeasurementManagerImpl#addMeasurementUnlessStored}.
 *
 * <p>Each test method suspends the surrounding {@code CarlosTestBase} test transaction
 * ({@code Propagation.NOT_SUPPORTED}) so two concurrent worker threads run independent
 * {@code @Transactional} manager transactions through a Spring annotation-driven proxy.
 * If {@code @Transactional} is removed from {@code addMeasurementUnlessStored}, the pessimistic
 * lock and persist calls fail for lack of an active transaction.</p>
 *
 * @since 2026-11-28
 */
@DisplayName("MeasurementManagerImpl transactional concurrency integration tests")
@Tag("integration")
@Tag("manager")
@Tag("measurement")
@Tag("create")
@Isolated
class MeasurementManagerImplTransactionIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final long OBSERVED_MILLIS = 1_770_000_000_000L;

    @Autowired
    private MeasurementDao measurementDao;
    @Autowired
    private DemographicDao demographicDao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;
    private Integer patientNo;

    @BeforeEach
    void setUpCommittedPatient() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        patientNo = transactionTemplate.execute(status -> {
            Demographic demographic = new Demographic();
            demographic.setFirstName("FAKE-TxRetry");
            demographic.setLastName("FAKE-Measurement6177");
            demographic.setSex("U");
            demographic.setProviderNo(PROVIDER_NO);
            entityManager.persist(demographic);
            entityManager.flush();
            return demographic.getDemographicNo();
        });
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        if (patientNo == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("DELETE FROM measurements WHERE demographicNo = ?", patientNo);
            jdbc.update("DELETE FROM log WHERE demographic_no = ?", patientNo);
            jdbc.update("DELETE FROM demographic WHERE demographic_no = ?", patientNo);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should serialize concurrent identical retries through the transactional proxy and store one row")
    void shouldStoreOneReading_whenConcurrentTransactionsRetryIdenticalDatedMeasurement() throws Exception {
        CountDownLatch firstHoldingLock = new CountDownLatch(1);
        CountDownLatch secondEnteringLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        AtomicInteger lookupCalls = new AtomicInteger();

        DemographicDao coordinatedDemographicDao = (DemographicDao) Proxy.newProxyInstance(
                DemographicDao.class.getClassLoader(),
                new Class<?>[] { DemographicDao.class },
                (proxy, method, args) -> {
                    if ("findForUpdate".equals(method.getName()) && lockCalls.incrementAndGet() == 2) {
                        secondEnteringLock.countDown();
                    }
                    return invokeTarget(demographicDao, method, args);
                });

        MeasurementDao coordinatedMeasurementDao = (MeasurementDao) Proxy.newProxyInstance(
                MeasurementDao.class.getClassLoader(),
                new Class<?>[] { MeasurementDao.class },
                (proxy, method, args) -> {
                    if ("find".equals(method.getName()) && args != null && args.length == 1
                            && lookupCalls.incrementAndGet() == 1) {
                        // Worker 1 already holds the patient row lock; hold here so Worker 2 overlaps on findForUpdate.
                        firstHoldingLock.countDown();
                        if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out releasing first measurement worker");
                        }
                    }
                    return invokeTarget(measurementDao, method, args);
                });

        MeasurementManager manager = transactionalManager(coordinatedMeasurementDao, coordinatedDemographicDao);
        assertThat(AopUtils.isAopProxy(manager)).isTrue();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Measurement> first = executor.submit(
                    () -> manager.addMeasurementUnlessStored(loggedInProvider(), newBpReading("dose 1 mg")));
            assertThat(firstHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Measurement> second = executor.submit(
                    () -> manager.addMeasurementUnlessStored(loggedInProvider(), newBpReading("dose 1 mg")));
            assertThat(secondEnteringLock.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();

            releaseFirst.countDown();

            Measurement firstSaved = first.get(10, TimeUnit.SECONDS);
            Measurement secondSaved = second.get(10, TimeUnit.SECONDS);

            assertThat(firstSaved.getId()).isNotNull();
            assertThat(secondSaved.getId()).isEqualTo(firstSaved.getId());
        } finally {
            releaseFirst.countDown();
        }

        List<Measurement> stored = transactionTemplate.execute(
                status -> measurementDao.findByType(patientNo, List.of("BP")));
        assertThat(stored).singleElement().satisfies(reading -> {
            assertThat(reading.getDataField()).isEqualTo("122/80");
            assertThat(reading.getMeasuringInstruction()).isEqualTo("sitting position");
            assertThat(reading.getComments()).isEqualTo("dose 1 mg");
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should commit both readings when concurrent dated requests differ only by comment letter case")
    void shouldStoreBothReadings_whenConcurrentRequestsDifferOnlyByCase() throws Exception {
        MeasurementManager manager = transactionalManager(measurementDao, demographicDao);
        assertThat(AopUtils.isAopProxy(manager)).isTrue();

        CyclicBarrier startTogether = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Measurement> lower = executor.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return manager.addMeasurementUnlessStored(loggedInProvider(), newBpReading("dose 1 mg"));
            });
            Future<Measurement> upper = executor.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return manager.addMeasurementUnlessStored(loggedInProvider(), newBpReading("dose 1 Mg"));
            });

            Measurement firstSaved = lower.get(10, TimeUnit.SECONDS);
            Measurement secondSaved = upper.get(10, TimeUnit.SECONDS);

            assertThat(firstSaved.getId()).isNotNull();
            assertThat(secondSaved.getId()).isNotNull();
            assertThat(secondSaved.getId()).isNotEqualTo(firstSaved.getId());
        }

        List<Measurement> stored = transactionTemplate.execute(
                status -> measurementDao.findByType(patientNo, List.of("BP")));
        assertThat(stored)
                .hasSize(2)
                .extracting(Measurement::getComments)
                .containsExactlyInAnyOrder("dose 1 mg", "dose 1 Mg");
    }

    private MeasurementManager transactionalManager(MeasurementDao dao, DemographicDao demoDao) {
        MeasurementManagerImpl target = new MeasurementManagerImpl();
        ReflectionTestUtils.setField(target, "measurementDao", dao);
        target.setDemographicDao(demoDao);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (MeasurementManager) proxyFactory.getProxy();
    }

    private Measurement newBpReading(String comments) {
        Measurement measurement = new Measurement();
        measurement.setDemographicId(patientNo);
        measurement.setProviderNo(PROVIDER_NO);
        measurement.setType("BP");
        measurement.setDataField("122/80");
        measurement.setMeasuringInstruction("sitting position");
        measurement.setComments(comments);
        measurement.setDateObserved(new Date(OBSERVED_MILLIS));
        return measurement;
    }

    private static LoggedInInfo loggedInProvider() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);
        Security security = new Security();
        security.setProviderNo(PROVIDER_NO);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        return loggedInInfo;
    }

    private static Object invokeTarget(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
