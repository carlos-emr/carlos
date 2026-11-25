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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

        // A template editor can change the session bean while the first request generates days.
        ((RscheduleBean) session.getAttribute("scheduleRscheduleBean")).clear();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        assertThat(original.getDateAvailHour("2032-02-29")).isEqualTo("0900");
        assertThat(display.rule().getDateAvailHour("2032-02-29")).isEqualTo("0900");
        ScheduleCalendarCache.openMonth(session, rule(OTHER_PROVIDER, "<SUN>1400</SUN>"), dao);

        assertThat(((HScheduleDate) appliedDays.get("2032-02-29")).reason).isEqualTo("first provider");
        assertThat(((HScheduleDate) display.days().get("2032-02-29")).reason).isEqualTo("first provider");
        assertThat(ScheduleCalendarCache.ruleForTemplate(session, PROVIDER)).isNull();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        var current = ScheduleCalendarCache.readMonth(session, OTHER_PROVIDER, dao);
        assertThat(current.rule().getDateAvailHour("2032-02-29")).isEqualTo("1400");
        assertThat(((HScheduleDate) current.days().get("2032-02-29")).reason).isEqualTo("second provider");
    }

    @Test
    void shouldOpenNewTemplatesAndReloadOnlyTheCurrentProvidersStaleMonth() {
        var dao = mock(ScheduleDateDao.class);
        assertThat(ScheduleCalendarCache.ruleForTemplate(session, PROVIDER).sdate).isEmpty();
        session.setAttribute("scheduleRscheduleBean", new RscheduleBean());
        assertThat(ScheduleCalendarCache.ruleForTemplate(session, PROVIDER).sdate).isEmpty();
        assertThat(ScheduleCalendarCache.readMonth(session, PROVIDER, dao)).isNull();
        when(dao.search_scheduledate_c(PROVIDER)).thenReturn(List.of(override('c', "opened")));
        ScheduleCalendarCache.openMonth(session, rule(PROVIDER, "<SUN>0800</SUN>"), dao);
        var selected = ScheduleCalendarCache.ruleForTemplate(session, PROVIDER);
        assertThat(selected.sdate).isEqualTo("2032-01-01");
        selected.clear();
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
