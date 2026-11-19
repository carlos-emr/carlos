/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * Issue #5297: a schedule override replacement is all or nothing against a real database. When
 * the replacement insert fails, the current override stays active instead of being left retired
 * with nothing in its place.
 *
 * <p>The service runs through a proxy carrying its own {@code @Transactional} attributes, as
 * Spring wraps it in production, and commits for real; CarlosTestBase's per-test transaction is
 * suspended so the commit or rollback under test is the service's own. Two Saves for one provider
 * run on two threads, each with its own connection and transaction, so their overlap is real.</p>
 *
 * @since 2026-11-19
 */
@Tag("integration")
@Tag("schedule")
@DisplayName("Schedule date override replacement")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleDateOverrideServiceIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER = "995297";
    private static final java.sql.Date DATE = java.sql.Date.valueOf("2031-05-14");

    @Autowired
    private ScheduleDateDao scheduleDateDao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** The provider row the service locks: it exists before the provider's first override. */
    @BeforeEach
    void createProvider() {
        transaction().executeWithoutResult(status -> {
            if (entityManager.find(Provider.class, PROVIDER) == null) {
                Provider provider = new Provider();
                provider.setProviderNo(PROVIDER);
                provider.setLastName("FAKE-Schedule");
                provider.setFirstName("Tester");
                provider.setProviderType("doctor");
                provider.setSex("F");
                provider.setSpecialty("");
                entityManager.persist(provider);
            }
        });
    }

    @AfterEach
    void removeOwnedRows() {
        transaction().executeWithoutResult(status -> {
            for (ScheduleDate row : scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, DATE)) {
                scheduleDateDao.remove(row.getId());
            }
            Provider provider = entityManager.find(Provider.class, PROVIDER);
            if (provider != null) {
                entityManager.remove(provider);
            }
        });
    }

    @Test
    void shouldRetireCurrentAndInsertReplacement_inOneCommit() {
        Integer current = activeOverride("Clinic closed");

        assertThat(service(scheduleDateDao).replaceOverride(PROVIDER, DATE, override("Half day"))).isTrue();

        ScheduleDate retired = transaction().execute(status -> scheduleDateDao.find(current));
        assertThat(retired.getStatus()).isEqualTo('D');
        ScheduleDate active = transaction().execute(status -> scheduleDateDao.findByProviderNoAndDate(PROVIDER, DATE));
        assertThat(active.getReason()).isEqualTo("Half day");
    }

    @Test
    void shouldKeepCurrentOverrideActive_whenReplacementInsertFails() {
        Integer current = activeOverride("Clinic closed");
        var overrideService = service(failingInsert(scheduleDateDao));
        ScheduleDate replacement = override("Half day");

        assertThatThrownBy(() -> overrideService.replaceOverride(PROVIDER, DATE, replacement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic replacement insert failure");

        ScheduleDate stillActive = transaction().execute(status -> scheduleDateDao.findByProviderNoAndDate(PROVIDER, DATE));
        assertThat(stillActive).isNotNull();
        assertThat(stillActive.getId()).isEqualTo(current);
        assertThat(stillActive.getStatus()).isEqualTo('A');
        List<ScheduleDate> owned = transaction().execute(status -> scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, DATE));
        assertThat(owned).hasSize(1);
    }

    @Test
    @DisplayName("should serialize two overlapping Saves on an empty date, then leave nothing active after Delete")
    void shouldSerializeOverlappingSaves_thenRetireBothOnDelete() throws Exception {
        CountDownLatch firstInserting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ScheduleDateOverrideService first = service(pausedInsert(scheduleDateDao, firstInserting, releaseFirst));
        ScheduleDateOverrideService second = service(scheduleDateDao);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstSave = threads.submit(() -> first.replaceOverride(PROVIDER, DATE, override("First")));
            assertThat(firstInserting.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> secondSave = threads.submit(() -> second.replaceOverride(PROVIDER, DATE, override("Second")));

            // The second Save waits on the provider row the first has locked. Unserialized, it found
            // no override, inserted its own and returned while the first was still inserting.
            assertThatThrownBy(() -> secondSave.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();

            assertThat(firstSave.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(secondSave.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            threads.shutdownNow();
        }
        assertThat(active()).extracting(ScheduleDate::getReason).containsExactly("Second");

        assertThat(service(scheduleDateDao).replaceOverride(PROVIDER, DATE, null)).isTrue();
        assertThat(active()).isEmpty();
    }

    @Test
    @DisplayName("should retire every active override when a date already holds more than one")
    void shouldRetireEveryActiveOverride_whenTheDateHoldsDuplicates() {
        activeOverride("Duplicate one");
        activeOverride("Duplicate two");

        assertThat(service(scheduleDateDao).replaceOverride(PROVIDER, DATE, override("Half day"))).isTrue();

        assertThat(active()).extracting(ScheduleDate::getReason).containsExactly("Half day");
    }

    @Test
    @DisplayName("should write nothing when the provider does not exist")
    void shouldWriteNothing_whenTheProviderDoesNotExist() {
        String missing = "995298";
        ScheduleDate replacement = override("Half day");
        replacement.setProviderNo(missing);

        assertThat(service(scheduleDateDao).replaceOverride(missing, DATE, replacement)).isFalse();

        List<ScheduleDate> written = transaction().execute(
                status -> scheduleDateDao.findByProviderAndDateRange(missing, DATE, DATE));
        assertThat(written).isEmpty();
    }

    /** The provider's active overrides for the date, read after every transaction has finished. */
    private List<ScheduleDate> active() {
        return transaction().execute(status -> scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, DATE)).stream()
                .filter(row -> row.getStatus() == 'A')
                .toList();
    }

    private Integer activeOverride(String reason) {
        return transaction().execute(status -> {
            ScheduleDate row = override(reason);
            scheduleDateDao.persist(row);
            return row.getId();
        });
    }

    private static ScheduleDate override(String reason) {
        ScheduleDate row = new ScheduleDate();
        row.setProviderNo(PROVIDER);
        row.setDate(DATE);
        row.setPriority('c');
        row.setAvailable('1');
        row.setHour("0800");
        row.setStatus('A');
        row.setReason(reason);
        row.setCreator("FAKE,Tester");
        return row;
    }

    /** The service behind the transaction proxy Spring gives it in production. */
    private ScheduleDateOverrideService service(ScheduleDateDao dao) {
        ProxyFactory factory = new ProxyFactory(new ScheduleDateOverrideService(dao));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (ScheduleDateOverrideService) factory.getProxy();
    }

    /** The real DAO, except that inserting the replacement fails after the retirement was written. */
    private static ScheduleDateDao failingInsert(ScheduleDateDao real) {
        return (ScheduleDateDao) Proxy.newProxyInstance(ScheduleDateDao.class.getClassLoader(),
                new Class<?>[]{ScheduleDateDao.class}, (proxy, method, args) -> {
                    if ("persist".equals(method.getName())) {
                        throw new IllegalStateException("synthetic replacement insert failure");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    /** The real DAO, except that the insert signals and then waits, holding the transaction open. */
    private static ScheduleDateDao pausedInsert(ScheduleDateDao real, CountDownLatch inserting, CountDownLatch release) {
        return (ScheduleDateDao) Proxy.newProxyInstance(ScheduleDateDao.class.getClassLoader(),
                new Class<?>[]{ScheduleDateDao.class}, (proxy, method, args) -> {
                    if ("persist".equals(method.getName())) {
                        inserting.countDown();
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("the paused insert was never released");
                        }
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private TransactionTemplate transaction() {
        TransactionTemplate result = new TransactionTemplate(transactionManager);
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return result;
    }
}
