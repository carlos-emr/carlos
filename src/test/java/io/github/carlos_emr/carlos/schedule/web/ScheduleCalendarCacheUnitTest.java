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
package io.github.carlos_emr.carlos.schedule.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.WebUtils;

import io.github.carlos_emr.HScheduleDate;
import io.github.carlos_emr.RscheduleBean;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * Tests the schedule month view's session copy of a provider's custom overrides: who it belongs
 * to, how a committed change refreshes one day, and how a failed refresh leaves the copy for the
 * month view to reload (#5297).
 *
 * @since 2026-11-19
 */
@DisplayName("Schedule calendar session cache")
@Tag("unit")
@Tag("schedule")
class ScheduleCalendarCacheUnitTest {

    private static final String PROVIDER = "999998";
    private static final String OTHER_PROVIDER = "999997";
    private static final java.sql.Date LEAP_DAY = java.sql.Date.valueOf("2032-02-29");

    private final MockHttpSession session = new MockHttpSession();

    private static ScheduleDate override(char priority, String reason) {
        ScheduleDate row = new ScheduleDate();
        row.setProviderNo(PROVIDER);
        row.setDate(LEAP_DAY);
        row.setPriority(priority);
        row.setAvailable('0');
        row.setHour("0800");
        row.setReason(reason);
        row.setCreator("FAKE,Tester");
        row.setStatus('A');
        return row;
    }

    @SuppressWarnings("unchecked")
    private Hashtable<String, Object> days() {
        return (Hashtable<String, Object>) session.getAttribute(ScheduleCalendarCache.DAYS);
    }

    /** A session whose copy was loaded for {@link #PROVIDER} and holds a stale entry for the leap day. */
    private Hashtable<String, Object> loadedWithStaleEntry() {
        ScheduleCalendarCache.load(session, PROVIDER, List.of());
        days().put("2032-02-29", new HScheduleDate("1", "c", "stale", "0900", "FAKE"));
        return days();
    }

    private static RscheduleBean rule(String provider, String hours) {
        return new RscheduleBean(provider, "2032-01-01", "2032-12-31", "1", "1", "", hours, "FAKE");
    }

    private MockHttpServletRequest form(String selection) {
        var request = new MockHttpServletRequest();
        request.setSession(session);
        if (selection != null) request.setParameter("templateSelection", selection);
        return request;
    }

    @Test
    void shouldReactivateAPopulatedCalendar_andKeepItsProviderRuleCustomDaysAndEditorSelection() throws Exception {
        var context = new StandardContext();
        context.setDistributable(true);
        var manager = new StandardManager();
        manager.setContext(context);
        var original = new StandardSession(manager);
        original.setValid(true);
        original.setCreationTime(System.currentTimeMillis());

        var dao = mock(ScheduleDateDao.class);
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "Leap day")));
        var monthRule = rule(PROVIDER, "<SUN>0900</SUN>");
        monthRule.active = "D";
        ScheduleCalendarCache.openMonth(original, monthRule, dao);
        var editor = new RscheduleBean(PROVIDER, "2032-05-01", "2032-06-30", "A", "1|2", "1300", "0800", "FAKE");
        String selection = ScheduleCalendarCache.selectTemplate(original, PROVIDER, editor);

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            original.writeObjectData(out);
        }
        var restored = new StandardSession(manager);
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObjectData(in);
        }

        var month = ScheduleCalendarCache.readMonth(restored, PROVIDER, dao);
        assertThat(month.rule().getDateAvailHour("2032-02-29")).isEqualTo("0900");
        assertThat(month.rule().active).isEqualTo("D");
        var day = (HScheduleDate) month.days().get("2032-02-29");
        assertThat(List.of(day.available, day.priority, day.reason, day.hour, day.creator))
                .containsExactly("0", "c", "Leap day", "0800", "FAKE,Tester");
        var request = new MockHttpServletRequest();
        request.setSession(restored);
        request.setParameter("templateSelection", selection);
        assertThat(ScheduleCalendarCache.templateForApply(request, PROVIDER))
                .isEqualTo(new ScheduleCalendarCache.TemplatePeriod(PROVIDER, "2032-05-01", "2032-06-30"));

        ScheduleCalendarCache.refreshDay(restored, PROVIDER, LEAP_DAY, () -> override('c', "Updated after activation"));
        var updated = ScheduleCalendarCache.readMonth(restored, PROVIDER, dao);
        assertThat(((HScheduleDate) updated.days().get("2032-02-29")).reason).isEqualTo("Updated after activation");
        assertThat(((HScheduleDate) ScheduleCalendarCache.readMonth(original, PROVIDER, dao).days().get("2032-02-29")).reason)
                .isEqualTo("Leap day");
        var restoredSelection = (RscheduleBean) restored.getAttribute("scheduleRscheduleBean");
        restoredSelection.setRscheduleBean(OTHER_PROVIDER, "2032-07-01", "2032-07-31", "1", "2", "", "1400", "OTHER");
        assertThat(restoredSelection.snapshotForProvider(OTHER_PROVIDER).avail_hour).isEqualTo("1400");
        assertThat(ScheduleCalendarCache.readMonth(restored, PROVIDER, dao)).isNull();
    }

    @Test
    void shouldNotifyTheSessionOfUpdatedDays_whenRefreshingOrReloadingAnExistingCalendar() {
        var replicated = org.mockito.Mockito.spy(new MockHttpSession());
        ScheduleCalendarCache.load(replicated, PROVIDER, List.of(override('c', "Original")));
        Object sameMap = replicated.getAttribute(ScheduleCalendarCache.DAYS);
        clearInvocations(replicated);
        ScheduleCalendarCache.refreshDay(replicated, PROVIDER, LEAP_DAY, () -> null);
        verify(replicated).setAttribute(ScheduleCalendarCache.DAYS, sameMap);
        clearInvocations(replicated);
        ScheduleCalendarCache.load(replicated, PROVIDER, List.of(override('c', "Reloaded")));
        verify(replicated).setAttribute(ScheduleCalendarCache.DAYS, sameMap);
    }

    @Test
    void shouldKeepEachRequestsRulesAndDaysTogetherWhenAnotherProviderOpensTheCalendar() {
        var dao = mock(ScheduleDateDao.class);
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "first provider")));
        var otherDay = override('c', "second provider");
        otherDay.setProviderNo(OTHER_PROVIDER);
        when(dao.search_scheduledate_c(OTHER_PROVIDER)).thenReturn(List.of(otherDay));
        var original = rule(PROVIDER, "<SUN>0900</SUN>");
        var appliedDays = ScheduleCalendarCache.openMonth(session, original, dao);
        var display = ScheduleCalendarCache.readMonth(session, PROVIDER, dao);
        assertThat(display.rule()).isNotSameAs(original);

        // A session created before request-local template selection may still expose a mutable bean.
        ((RscheduleBean) session.getAttribute("scheduleRscheduleBean")).clear();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        assertThat(original.getDateAvailHour("2032-02-29")).isEqualTo("0900");
        assertThat(display.rule().getDateAvailHour("2032-02-29")).isEqualTo("0900");
        ScheduleCalendarCache.openMonth(session, rule(OTHER_PROVIDER, "<SUN>1400</SUN>"), dao);

        assertThat(((HScheduleDate) appliedDays.get("2032-02-29")).reason).isEqualTo("first provider");
        assertThat(((HScheduleDate) display.days().get("2032-02-29")).reason).isEqualTo("first provider");
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        var current = ScheduleCalendarCache.readMonth(session, OTHER_PROVIDER, dao);
        assertThat(current.rule().getDateAvailHour("2032-02-29")).isEqualTo("1400");
        assertThat(((HScheduleDate) current.days().get("2032-02-29")).reason).isEqualTo("second provider");
    }

    @Test
    void shouldOpenNewTemplatesAndReloadOnlyTheCurrentProvidersStaleMonth() {
        var dao = mock(ScheduleDateDao.class);
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        session.setAttribute("scheduleRscheduleBean", new RscheduleBean());
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "opened")));
        ScheduleCalendarCache.openMonth(session, rule(PROVIDER, "<SUN>0800</SUN>"), dao);
        clearInvocations(dao);
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "updated")));
        session.setAttribute(ScheduleCalendarCache.STALE, Boolean.TRUE);

        var current = ScheduleCalendarCache.readMonth(session, PROVIDER, dao);
        assertThat(((HScheduleDate) current.days().get("2032-02-29")).reason).isEqualTo("updated");
        assertThat(current.rule().getDateAvailHour("2032-02-29")).isEqualTo("0800");
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao).days()).containsKeys("2032-02-29");
        verify(dao).search_scheduledate_c(PROVIDER);
    }

    @Test
    void shouldPublishTheLastCompletedTemplateWithoutChangingTheMonthsDisplayedRule() {
        var dao = mock(ScheduleDateDao.class);
        var first = rule(PROVIDER, "<SUN>0800</SUN>");
        var second = rule(OTHER_PROVIDER, "<SUN>1400</SUN>");
        ScheduleCalendarCache.openMonth(session, first, dao);

        var nextTemplate = rule(PROVIDER, "<SUN>1100</SUN>");
        nextTemplate.sdate = "2032-02-01";
        String nextForm = ScheduleCalendarCache.selectTemplate(session, PROVIDER, nextTemplate);
        assertThat(ScheduleCalendarCache.templateForApply(form(nextForm), PROVIDER).start()).isEqualTo(nextTemplate.sdate);
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao).rule().avail_hour).isEqualTo(first.avail_hour);
        assertThat(ScheduleCalendarCache.openRule(session, PROVIDER).avail_hour).isEqualTo(first.avail_hour);

        // Another editor started before Apply completed, then finished its own lookup.
        ScheduleCalendarCache.openMonth(session, nextTemplate, dao);
        String secondForm = ScheduleCalendarCache.selectTemplate(session, OTHER_PROVIDER, second);
        second.clear();
        assertThat(ScheduleCalendarCache.templateForApply(form(secondForm), OTHER_PROVIDER).start()).isEqualTo("2032-01-01");
        assertThat(ScheduleCalendarCache.templateForApply(form(nextForm), OTHER_PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form(nextForm), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();

        assertThat(ScheduleCalendarCache.selectTemplate(session, OTHER_PROVIDER, first)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form(secondForm), OTHER_PROVIDER).end()).isEqualTo("2032-12-31");
    }

    @Test
    void shouldRetainTheSelectedProviderForANewTemplateAndReadAnExistingSessionAfterUpgrade() {
        var dao = mock(ScheduleDateDao.class);
        ScheduleCalendarCache.load(session, PROVIDER, List.of());
        var oldRule = rule(PROVIDER, "<SUN>0900</SUN>");
        session.setAttribute("scheduleRscheduleBean", oldRule);
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao).rule().avail_hour).isEqualTo(oldRule.avail_hour);

        var newTemplate = new RscheduleBean();
        newTemplate.provider_no = OTHER_PROVIDER;
        String newForm = ScheduleCalendarCache.selectTemplate(session, OTHER_PROVIDER, newTemplate);
        assertThat(ScheduleCalendarCache.templateForApply(form(newForm), OTHER_PROVIDER))
                .isEqualTo(new ScheduleCalendarCache.TemplatePeriod(OTHER_PROVIDER, "", ""));
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.openRule(session, PROVIDER)).isNull();
        var applied = rule(OTHER_PROVIDER, "<SUN>1000</SUN>");
        ScheduleCalendarCache.openMonth(session, applied, dao);
        assertThat(ScheduleCalendarCache.readMonth(session, OTHER_PROVIDER, dao).rule().avail_hour).isEqualTo(applied.avail_hour);
    }

    @Test
    void shouldRecognizeAnOldFormOnlyWhenItsProviderAndPeriodAreCorroborated() {
        session.setAttribute("scheduleRscheduleBean", rule(PROVIDER, "<SUN>0800</SUN>"));
        var request = form(null);
        request.setParameter("syear", "2032");
        request.setParameter("smonth", "1");
        request.setParameter("sday", "1");
        request.setParameter("origeyear", "2032");
        request.setParameter("origemonth", "12");
        request.setParameter("origeday", "31");
        assertThat(ScheduleCalendarCache.templateForApply(request, PROVIDER))
                .isEqualTo(new ScheduleCalendarCache.TemplatePeriod(PROVIDER, "2032-01-01", "2032-12-31"));
        request.setParameter("origeday", "30");
        assertThat(ScheduleCalendarCache.templateForApply(request, PROVIDER)).isNull();
        request.setParameter("origeday", "32");
        assertThat(ScheduleCalendarCache.templateForApply(request, PROVIDER)).isNull();
        request.removeParameter("smonth");
        assertThat(ScheduleCalendarCache.templateForApply(request, PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form("unknown-selection"), PROVIDER)).isNull();
    }

    @Test
    void shouldRefuseStrippedUnknownAndExpiredSelectionsWithoutDiscardingOtherOpenTabs() {
        var template = rule(PROVIDER, "<SUN>0900</SUN>");
        String first = ScheduleCalendarCache.selectTemplate(session, PROVIDER, template);
        String second = ScheduleCalendarCache.selectTemplate(session, PROVIDER, template);
        for (int tab = 0; tab < 31; tab++) {
            ScheduleCalendarCache.selectTemplate(session, PROVIDER, template);
        }
        assertThat(ScheduleCalendarCache.templateForApply(form(first), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form(null), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form("unknown-selection"), PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.templateForApply(form(second), PROVIDER).start()).isEqualTo(template.sdate);
    }

    @Test
    @DisplayName("should load padded days, record the provider and clear a stale mark, in the JSP's own table")
    void shouldLoadPaddedDaysAndOwner_intoTheSessionTable() {
        Hashtable<String, Object> jspTable = new Hashtable<>();
        jspTable.put("2031-01-01", "left over");
        session.setAttribute(ScheduleCalendarCache.DAYS, jspTable);
        session.setAttribute(ScheduleCalendarCache.STALE, Boolean.TRUE);

        ScheduleCalendarCache.load(session, PROVIDER, List.of(override('c', "Leap day off")));

        assertThat(days()).isSameAs(jspTable).containsOnlyKeys("2032-02-29");
        HScheduleDate entry = (HScheduleDate) jspTable.get("2032-02-29");
        assertThat(entry.available).isEqualTo("0");
        assertThat(entry.priority).isEqualTo("c");
        assertThat(entry.reason).isEqualTo("Leap day off");
        assertThat(entry.hour).isEqualTo("0800");
        assertThat(session.getAttribute(ScheduleCalendarCache.PROVIDER)).isEqualTo(PROVIDER);
        assertThat(session.getAttribute(ScheduleCalendarCache.STALE)).isNull();
    }

    @Test
    @DisplayName("should create the table when the session has none")
    void shouldCreateTheTable_whenTheSessionHasNone() {
        ScheduleCalendarCache.load(session, PROVIDER, List.of(override('c', "Leap day off")));

        assertThat(days()).containsOnlyKeys("2032-02-29");
    }

    @Test
    @DisplayName("should recognize only the provider the copy was loaded for")
    void shouldRecognizeOnlyTheLoadedProvider_forOwnership() {
        assertThat(ScheduleCalendarCache.isFor(session, PROVIDER)).isFalse();

        ScheduleCalendarCache.load(session, PROVIDER, List.of());

        assertThat(ScheduleCalendarCache.isFor(session, PROVIDER)).isTrue();
        assertThat(ScheduleCalendarCache.isFor(session, OTHER_PROVIDER)).isFalse();
    }

    @Test
    @DisplayName("should store the committed custom override under the padded key")
    void shouldStoreTheCommittedCustomOverride_onRefresh() {
        Hashtable<String, Object> days = loadedWithStaleEntry();

        ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> override('c', "Leap day off"));

        assertThat(((HScheduleDate) days.get("2032-02-29")).reason).isEqualTo("Leap day off");
    }

    @Test
    @DisplayName("should clear the day when the committed override is rule-made")
    void shouldClearTheDay_whenTheCommittedOverrideIsRuleMade() {
        Hashtable<String, Object> days = loadedWithStaleEntry();

        ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> override('b', ""));

        assertThat(days).doesNotContainKey("2032-02-29");
    }

    @Test
    @DisplayName("should clear the day when no override remains")
    void shouldClearTheDay_whenNoOverrideRemains() {
        Hashtable<String, Object> days = loadedWithStaleEntry();

        ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> null);

        assertThat(days).doesNotContainKey("2032-02-29");
    }

    @Test
    @DisplayName("should leave another provider's calendar alone, without reading")
    void shouldLeaveAnotherProvidersCalendarAlone_onRefresh() {
        ScheduleCalendarCache.load(session, OTHER_PROVIDER, List.of(override('c', "Other provider's day")));
        List<String> reads = new ArrayList<>();

        ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> {
            reads.add("read");
            return null;
        });

        assertThat(reads).isEmpty();
        assertThat(((HScheduleDate) days().get("2032-02-29")).reason).isEqualTo("Other provider's day");
    }

    @Test
    @DisplayName("should keep the day, mark the copy stale and log no identifiers when the read fails")
    void shouldMarkStaleAndKeepTheDay_whenTheReadFails() {
        Hashtable<String, Object> days = loadedWithStaleEntry();

        try (LogCapture logs = LogCapture.forLogger(ScheduleCalendarCache.class)) {
            ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> {
                throw new IllegalStateException("synthetic read failure");
            });

            assertThat(logs.messages()).singleElement().satisfies(message -> {
                assertThat(message).contains("IllegalStateException").contains("calendar will reload");
                assertThat(message).doesNotContain(PROVIDER).doesNotContain("2032");
            });
        }

        assertThat(((HScheduleDate) days.get("2032-02-29")).reason).isEqualTo("stale");
        assertThat(session.getAttribute(ScheduleCalendarCache.STALE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("should read the committed override under the session mutex")
    void shouldReadUnderTheSessionMutex_onRefresh() {
        loadedWithStaleEntry();
        List<Boolean> lockHeld = new ArrayList<>();

        ScheduleCalendarCache.refreshDay(session, PROVIDER, LEAP_DAY, () -> {
            lockHeld.add(Thread.holdsLock(WebUtils.getSessionMutex(session)));
            return null;
        });

        assertThat(lockHeld).containsExactly(true);
    }

    @Test
    @DisplayName("should not query the database when the copy is current")
    void shouldNotReload_whenTheCopyIsCurrent() {
        Hashtable<String, Object> days = loadedWithStaleEntry();
        ScheduleDateDao dao = mock(ScheduleDateDao.class);

        ScheduleCalendarCache.reloadIfStale(session, PROVIDER, dao);

        verifyNoInteractions(dao);
        assertThat(((HScheduleDate) days.get("2032-02-29")).reason).isEqualTo("stale");
    }

    @Test
    void shouldLeaveTheCurrentProvidersStaleCacheIntact_whenAnotherTabRedisplaysItsMonth() {
        Hashtable<String, Object> days = loadedWithStaleEntry();
        session.setAttribute(ScheduleCalendarCache.STALE, Boolean.TRUE);
        ScheduleDateDao dao = mock(ScheduleDateDao.class);

        ScheduleCalendarCache.reloadIfStale(session, OTHER_PROVIDER, dao);

        verifyNoInteractions(dao);
        assertThat(ScheduleCalendarCache.isFor(session, PROVIDER)).isTrue();
        assertThat(session.getAttribute(ScheduleCalendarCache.STALE)).isEqualTo(Boolean.TRUE);
        assertThat(((HScheduleDate) days.get("2032-02-29")).reason).isEqualTo("stale");
    }

    @Test
    @DisplayName("should reload the provider's custom overrides and clear the mark when the copy is stale")
    void shouldReloadFromTheDatabase_whenTheCopyIsStale() {
        Hashtable<String, Object> days = loadedWithStaleEntry();
        session.setAttribute(ScheduleCalendarCache.STALE, Boolean.TRUE);
        ScheduleDateDao dao = mock(ScheduleDateDao.class);
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "Leap day off")));

        ScheduleCalendarCache.reloadIfStale(session, PROVIDER, dao);

        assertThat(days()).isSameAs(days);
        assertThat(((HScheduleDate) days.get("2032-02-29")).reason).isEqualTo("Leap day off");
        assertThat(session.getAttribute(ScheduleCalendarCache.STALE)).isNull();
        assertThat(ScheduleCalendarCache.isFor(session, PROVIDER)).isTrue();
    }
}
