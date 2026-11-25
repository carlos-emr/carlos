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

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import io.github.carlos_emr.carlos.commn.dao.RScheduleDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RSchedule;
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
    private static final java.sql.Date FORMER_END = java.sql.Date.valueOf("2031-05-15");
    private static final java.sql.Date FOLLOWING = java.sql.Date.valueOf("2031-05-16");

    @Autowired
    private ScheduleDateDao scheduleDateDao;
    @Autowired
    private RScheduleDao rScheduleDao;
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
            entityManager.createQuery("delete from RSchedule r where r.providerNo = :provider")
                    .setParameter("provider", PROVIDER).executeUpdate();
            for (ScheduleDate row : scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, FOLLOWING)) {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldGenerateFromCommittedDays_whenAnOverlappingPopupSavesOrDeletes(boolean save) throws Exception {
        ScheduleDate candidate = ruleDay("New weekly rule");
        ScheduleDate popupDay;
        if (save) {
            popupDay = override("Closed");
            popupDay.setAvailable('0');
        } else {
            activeOverride("Old custom day");
            popupDay = ruleDay("Fallback before template generation");
        }

        var popupInserting = new CountDownLatch(1);
        var releasePopup = new CountDownLatch(1);
        var bulkStarting = new CountDownLatch(1);
        var popup = service(pausedInsert(scheduleDateDao, popupInserting, releasePopup));
        var bulk = service(scheduleDateDao);
        var threads = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = threads.submit(() -> popup.replaceOverride(PROVIDER, DATE, popupDay));
            assertThat(popupInserting.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = threads.submit(() -> {
                bulkStarting.countDown();
                return bulk.replaceRuleDays(PROVIDER, DATE, DATE, List.of(candidate));
            });
            assertThat(bulkStarting.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releasePopup.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releasePopup.countDown();
            threads.shutdownNow();
        }

        ScheduleDate expected = save ? popupDay : candidate;
        assertThat(active()).singleElement().satisfies(row -> {
            assertThat(row.getReason()).isEqualTo(expected.getReason());
            assertThat(row.getPriority()).isEqualTo(expected.getPriority());
            assertThat(row.getAvailable()).isEqualTo(expected.getAvailable());
        });
    }

    @Test
    void shouldRetireOnlyTheOldRuleRange_andAllowRegenerationAfterACustomDayWasRetired() {
        var bulk = service(scheduleDateDao);
        activeOverride("Previous custom day");
        assertThat(bulk.replaceOverride(PROVIDER, DATE, null)).isTrue();
        for (var date : List.of(DATE, FORMER_END, FOLLOWING)) {
            ScheduleDate old = ruleDay("Old rule");
            old.setDate(date);
            assertThat(bulk.replaceOverride(PROVIDER, date, old)).isTrue();
        }

        assertThat(bulk.replaceRuleDays(PROVIDER, DATE, FORMER_END, List.of(ruleDay("Shorter rule")))).isTrue();
        assertThat(bulk.currentOverride(PROVIDER, DATE).getReason()).isEqualTo("Shorter rule");
        assertThat(bulk.currentOverride(PROVIDER, FORMER_END)).isNull();
        assertThat(bulk.currentOverride(PROVIDER, FOLLOWING).getReason()).isEqualTo("Old rule");
    }

    @ParameterizedTest
    @ValueSource(strings = {"other provider", "custom priority", "before range", "after range"})
    void shouldRollBackTheEntireGeneration_whenACandidateDoesNotBelongToTheRule(String invalid) {
        var bulk = service(scheduleDateDao);
        assertThat(bulk.replaceOverride(PROVIDER, DATE, ruleDay("Before generation"))).isTrue();
        ScheduleDate candidate = ruleDay("Invalid candidate");
        switch (invalid) {
            case "other provider" -> candidate.setProviderNo("995298");
            case "custom priority" -> candidate.setPriority('c');
            case "before range" -> candidate.setDate(java.sql.Date.valueOf("2031-05-13"));
            case "after range" -> candidate.setDate(FOLLOWING);
            default -> throw new AssertionError(invalid);
        }
        assertThatThrownBy(() -> bulk.replaceRuleDays(PROVIDER, DATE, DATE,
                List.of(ruleDay("Would have been inserted"), candidate))).isInstanceOf(IllegalArgumentException.class);
        assertThat(active()).extracting(ScheduleDate::getReason).containsExactly("Before generation");
    }

    @Test
    void shouldRefuseTheWholeTemplate_whenTheProviderNoLongerExists() {
        String missing = "995298";
        RSchedule submitted = header("New rule");
        submitted.setProviderNo(missing);
        ScheduleDate day = ruleDay("Would have been generated");
        day.setProviderNo(missing);

        var result = templates(rScheduleDao).apply(submitted, false, FORMER_END, List.of(day));

        assertThat(result.providerFound()).isFalse();
        assertThat(result.overlaps()).isFalse();
        assertThat(transaction().execute(status -> rScheduleDao.findByProviderAvailableAndDate(missing, "1", DATE)).size()).isZero();
        assertThat(transaction().execute(status -> scheduleDateDao.findByProviderAndDateRange(missing, DATE, FORMER_END)).size()).isZero();
    }

    @Test
    void shouldRollBackBothRuleAndDates_whenTheFinalHeaderWriteFails() {
        RSchedule original = header("Old rule");
        assertThat(templates(rScheduleDao).apply(original, false, FORMER_END,
                List.of(ruleDay("Original day"))).providerFound()).isTrue();
        var failingHeader = new ProxyFactory(rScheduleDao);
        failingHeader.addAdvice((MethodInterceptor) call -> {
            Object value = call.proceed();
            if ("persist".equals(call.getMethod().getName())) {
                entityManager.flush();
                throw new IllegalStateException("synthetic failure after the final header insert");
            }
            return value;
        });
        var templates = templates((RScheduleDao) failingHeader.getProxy());

        assertThatThrownBy(() -> templates.apply(header("New rule"), true, FORMER_END, List.of(ruleDay("New day"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after the final header insert");

        List<RSchedule> rules = transaction().execute(status -> rScheduleDao.findByProviderAvailableAndDate(PROVIDER, "1", DATE));
        assertThat(rules).singleElement().satisfies(rule -> {
            assertThat(rule.getId()).isEqualTo(original.getId());
            assertThat(rule.getStatus()).isEqualTo("A");
            assertThat(rule.getAvailHour()).isEqualTo("Old rule");
        });
        List<ScheduleDate> rows = transaction().execute(status -> scheduleDateDao.findByProviderAndDateRange(PROVIDER, DATE, DATE));
        assertThat(rows).singleElement().satisfies(day -> {
            assertThat(day.getStatus()).isEqualTo('A');
            assertThat(day.getReason()).isEqualTo("Original day");
        });
    }

    @Test
    void shouldPreserveSameStartReplacement_editsAndTheExistingOverlapWarning() {
        var templates = templates(rScheduleDao);
        RSchedule first = header("Initial hours");
        var newRule = templates.apply(first, false, FORMER_END, List.of());
        assertThat(newRule.providerFound()).isTrue();
        assertThat(newRule.overlaps()).isFalse();
        RSchedule edit = header("Edited hours");
        edit.setAvailHourB("Alternate hours");
        edit.setDayOfWeek("3 4");
        edit.setCreator("Second editor");
        assertThat(templates.apply(edit, false, FORMER_END, List.of()).providerFound()).isTrue();
        RSchedule stored = transaction().execute(status -> rScheduleDao.find(first.getId()));
        assertThat(stored.getAvailHour()).isEqualTo("Edited hours");
        assertThat(stored.getAvailHourB()).isEqualTo("Alternate hours");
        assertThat(stored.getDayOfWeek()).isEqualTo("3 4");
        assertThat(stored.getCreator()).isEqualTo("Second editor");

        RSchedule alternate = header("Old alternate");
        alternate.setAvailable("A");
        RSchedule overlapping = header("Overlapping range");
        overlapping.setsDate(java.sql.Date.valueOf("2031-05-13"));
        overlapping.seteDate(FOLLOWING);
        transaction().executeWithoutResult(status -> {
            rScheduleDao.persist(alternate);
            rScheduleDao.persist(overlapping);
        });
        RSchedule replacement = header("Replacement");
        assertThat(templates.apply(replacement, true, FORMER_END, List.of()).overlaps()).isTrue();
        List<String> statuses = transaction().execute(status -> List.of(first, alternate, overlapping, replacement).stream()
                .map(rule -> rScheduleDao.find(rule.getId()).getStatus()).toList());
        assertThat(statuses).containsExactly("D", "D", "A", "A");
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
        assertThat(service(scheduleDateDao).replaceRuleDays(missing, DATE, DATE, List.of())).isFalse();

        List<ScheduleDate> written = transaction().execute(
                status -> scheduleDateDao.findByProviderAndDateRange(missing, DATE, DATE));
        assertThat(written).isEmpty();
    }

    @Test
    @DisplayName("should read the override that committed last, and none once it is deleted")
    void shouldReadTheLatestCommittedOverride_afterEachReplacement() {
        ScheduleDateOverrideService overrideService = service(scheduleDateDao);
        ScheduleDate ruleDay = override("Rule day");
        ruleDay.setPriority('b');
        assertThat(overrideService.replaceOverride(PROVIDER, DATE, ruleDay)).isTrue();
        assertThat(overrideService.currentOverride(PROVIDER, DATE).getPriority()).isEqualTo('b');

        assertThat(overrideService.replaceOverride(PROVIDER, DATE, override("First save"))).isTrue();
        assertThat(overrideService.replaceOverride(PROVIDER, DATE, override("Second save"))).isTrue();
        ScheduleDate current = overrideService.currentOverride(PROVIDER, DATE);
        assertThat(current.getReason()).isEqualTo("Second save");
        assertThat(current.getPriority()).isEqualTo('c');

        assertThat(overrideService.replaceOverride(PROVIDER, DATE, null)).isTrue();
        assertThat(overrideService.currentOverride(PROVIDER, DATE)).isNull();
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

    private static ScheduleDate ruleDay(String reason) {
        ScheduleDate row = override(reason);
        row.setPriority('b');
        return row;
    }

    private static RSchedule header(String hours) {
        RSchedule rule = new RSchedule();
        rule.setProviderNo(PROVIDER);
        rule.setsDate(DATE);
        rule.seteDate(FORMER_END);
        rule.setAvailable("1");
        rule.setDayOfWeek("1 2");
        rule.setAvailHour(hours);
        rule.setCreator("FAKE,Tester");
        rule.setStatus("A");
        return rule;
    }

    /** The service behind the transaction proxy Spring gives it in production. */
    private ScheduleDateOverrideService service(ScheduleDateDao dao) {
        return transactional(new ScheduleDateOverrideService(dao), ScheduleDateOverrideService.class);
    }

    private ScheduleTemplateService templates(RScheduleDao rules) {
        return transactional(new ScheduleTemplateService(rules, service(scheduleDateDao)), ScheduleTemplateService.class);
    }

    private <T> T transactional(T target, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
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
