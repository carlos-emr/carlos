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

import org.junit.jupiter.api.AfterEach;
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
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * Issue #5297: a schedule override replacement is all or nothing against a real database. When
 * the replacement insert fails, the current override stays active instead of being left retired
 * with nothing in its place.
 *
 * <p>The service runs through a proxy carrying its own {@code @Transactional} attributes, as
 * Spring wraps it in production, and commits for real; CarlosTestBase's per-test transaction is
 * suspended so the commit or rollback under test is the service's own.</p>
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

    @AfterEach
    void removeOwnedRows() {
        transaction().executeWithoutResult(status -> {
            for (ScheduleDate row : scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, DATE)) {
                scheduleDateDao.remove(row.getId());
            }
        });
    }

    @Test
    void shouldRetireCurrentAndInsertReplacement_inOneCommit() {
        Integer current = activeOverride("Clinic closed");

        service(scheduleDateDao).replaceOverride(PROVIDER, DATE, override("Half day"));

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

    private TransactionTemplate transaction() {
        TransactionTemplate result = new TransactionTemplate(transactionManager);
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return result;
    }
}
