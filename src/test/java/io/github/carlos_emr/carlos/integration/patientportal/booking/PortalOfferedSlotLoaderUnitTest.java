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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Only free time inside the clinic's bookable codes on the doctor's real template is offered (#3850). */
@Tag("unit")
@Tag("patient-portal")
class PortalOfferedSlotLoaderUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final ZonedDateTime NOW = MONDAY.minusDays(3).atTime(12, 0).atZone(ZONE);
    private static final PortalBookingSettings SETTINGS = new PortalBookingSettings(
            Set.of('B'), 24, "in_person", null, false, null, 60);

    private final ScheduleManager schedules = mock(ScheduleManager.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final ScheduleTemplateCodeDao codes = mock(ScheduleTemplateCodeDao.class);
    private final PortalOfferedSlotLoader loader = new PortalOfferedSlotLoader(schedules, appointments, codes);

    @BeforeEach
    void fifteenMinuteCodes() {
        ScheduleTemplateCode code = new ScheduleTemplateCode();
        code.setDuration("15");
        when(codes.getByCode(anyChar())).thenReturn(code);
        when(appointments.findByProviderAndDayandNotStatus(any(), any(), eq("C"))).thenReturn(List.of());
    }

    /** A day template from "HH:mm" to code, in 15-minute template slots. */
    private void template(LocalDate day, Map<String, Character> slots) {
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        slots.forEach((time, code) -> timeSlots.put(
                GregorianCalendar.from(day.atTime(LocalTime.parse(time)).atZone(ZONE)), code));
        DayWorkSchedule schedule = new DayWorkSchedule();
        schedule.setTimeSlotDurationMin(15);
        schedule.setTimeSlots(timeSlots);
        when(schedules.getDayWorkSchedule(eq("101"), eq(GregorianCalendar.from(day.atStartOfDay(ZONE)))))
                .thenReturn(schedule);
    }

    /** As Hibernate loads them: TIME columns arrive as java.sql.Time, whose toInstant() throws. */
    private static Appointment booked(LocalDate day, String start, String end) {
        Appointment appointment = new Appointment();
        appointment.setStartTime(java.sql.Time.valueOf(LocalTime.parse(start)));
        appointment.setEndTime(java.sql.Time.valueOf(LocalTime.parse(end)));
        return appointment;
    }

    @Test
    void shouldReadTimeColumns_asLoadedByHibernate() {
        assertThat(PortalOfferedSlotLoader.timeOf(java.sql.Time.valueOf("09:15:00"), ZONE)).isEqualTo(LocalTime.of(9, 15));
        assertThat(PortalOfferedSlotLoader.timeOf(Date.from(MONDAY.atTime(14, 30).atZone(ZONE).toInstant()), ZONE))
                .isEqualTo(LocalTime.of(14, 30));
    }

    private List<String> starts(List<PortalOfferedSlotLoader.OpenTime> times) {
        return times.stream().map(time -> time.start().toLocalTime().toString()).toList();
    }

    @Test
    void shouldOfferOnlyBookableCodes_whenTemplateMixesCodes() {
        template(MONDAY, Map.of("09:00", 'B', "09:15", 'A', "09:30", 'B', "09:45", '_'));
        var times = loader.load("101", MONDAY, MONDAY, 8, Set.of(), SETTINGS, NOW);
        assertThat(starts(times)).containsExactly("09:00", "09:30");
        assertThat(times).allSatisfy(time -> assertThat(time.durationMinutes()).isEqualTo(15));
    }

    @Test
    void shouldSkipTimes_whenAnAppointmentOverlaps() {
        template(MONDAY, Map.of("09:00", 'B', "09:15", 'B', "09:30", 'B'));
        // CARLOS ends a 15-minute 09:15 visit at 09:29.
        when(appointments.findByProviderAndDayandNotStatus(eq("101"), any(), eq("C")))
                .thenReturn(List.of(booked(MONDAY, "09:15", "09:29")));
        assertThat(starts(loader.load("101", MONDAY, MONDAY, 8, Set.of(), SETTINGS, NOW)))
                .containsExactly("09:00", "09:30");
    }

    @Test
    void shouldNeverOfferOffTemplate_whenAVisitRunsPastBookableCodes() {
        ScheduleTemplateCode half = new ScheduleTemplateCode();
        half.setDuration("30");
        when(codes.getByCode('B')).thenReturn(half);
        template(MONDAY, Map.of("09:00", 'B', "09:15", 'A', "09:30", 'B', "09:45", 'B'));
        // 09:00 would run into the 'A' slot; 09:45 would run off the end of the template.
        assertThat(starts(loader.load("101", MONDAY, MONDAY, 8, Set.of(), SETTINGS, NOW)))
                .containsExactly("09:30");
    }

    @Test
    void shouldOfferNothing_whenTheClinicChoseNoCodes() {
        template(MONDAY, Map.of("09:00", 'B'));
        var none = new PortalBookingSettings(Set.of(), 24, "in_person", null, false, null, 60);
        assertThat(loader.load("101", MONDAY, MONDAY, 8, Set.of(), none, NOW)).isEmpty();
    }

    @Test
    void shouldRespectLeadTime_andSkipHolidaysAndExcludedTimes() {
        template(MONDAY, Map.of("09:00", 'B', "10:00", 'B'));
        ZonedDateTime lateSunday = MONDAY.minusDays(1).atTime(9, 30).atZone(ZONE);
        // 24 hours of lead time from Sunday 09:30 leaves only 10:00.
        assertThat(starts(loader.load("101", MONDAY, MONDAY, 8, Set.of(), SETTINGS, lateSunday)))
                .containsExactly("10:00");
        Set<ZonedDateTime> taken = Set.of(MONDAY.atTime(9, 0).atZone(ZONE));
        assertThat(starts(loader.load("101", MONDAY, MONDAY, 8, taken, SETTINGS, NOW))).containsExactly("10:00");
        DayWorkSchedule holiday = new DayWorkSchedule();
        holiday.setHoliday(true);
        when(schedules.getDayWorkSchedule(eq("101"), any())).thenReturn(holiday);
        assertThat(loader.load("101", MONDAY, MONDAY, 8, Set.of(), SETTINGS, NOW)).isEmpty();
    }

    @Test
    void shouldSpreadOffers_acrossDaysAndHalves() {
        template(MONDAY, Map.of("09:00", 'B', "09:15", 'B', "09:30", 'B', "14:00", 'B'));
        template(MONDAY.plusDays(1), Map.of("09:00", 'B', "15:00", 'B'));
        var times = loader.load("101", MONDAY, MONDAY.plusDays(1), 4, Set.of(), SETTINGS, NOW);
        // Monday morning, Tuesday morning, Monday afternoon, Tuesday afternoon; listed in time order.
        assertThat(times).extracting(time -> time.start().toLocalDateTime().toString()).containsExactly(
                "2026-10-19T09:00", "2026-10-19T14:00", "2026-10-20T09:00", "2026-10-20T15:00");
    }

    @Test
    void shouldReportStillOpen_onlyForTheSameTimeAndLength() {
        template(MONDAY, Map.of("09:00", 'B', "09:15", 'B'));
        ZonedDateTime nine = MONDAY.atTime(9, 0).atZone(ZONE);
        assertThat(loader.isOpen("101", nine, 15, SETTINGS, NOW)).isTrue();
        assertThat(loader.isOpen("101", nine, 30, SETTINGS, NOW)).isFalse();
        assertThat(loader.isOpen("101", nine, 15, SETTINGS, nine)).isFalse();
        when(appointments.findByProviderAndDayandNotStatus(eq("101"), any(), eq("C")))
                .thenReturn(List.of(booked(MONDAY, "09:00", "09:14")));
        assertThat(loader.isOpen("101", nine, 15, SETTINGS, NOW)).isFalse();
    }
}
