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

import java.util.Calendar;
import java.util.List;
import java.util.TreeMap;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.appointment.search.TimeSlot;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppointmentSearchManager}.
 *
 * <p>Verifies appointment type code filtering for generated schedule time slots.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("AppointmentSearchManager unit tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("appointment")
class AppointmentSearchManagerUnitTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    @DisplayName("getAllowedTimesByType should return empty list when schedule has no time slots")
    void shouldReturnEmptyList_whenScheduleHasNoTimeSlots() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        dayWorkSchedule.setTimeSlots(new TreeMap<>());

        List<TimeSlot> result = AppointmentSearchManager.getAllowedTimesByType(dayWorkSchedule, new Character[]{'A'}, "123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllowedTimesByType should return empty list when no codes match")
    void shouldReturnEmptyList_whenNoScheduleCodesMatchRequestedCodes() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        timeSlots.put(createAppointmentTime(0), 'B');
        dayWorkSchedule.setTimeSlots(timeSlots);

        List<TimeSlot> result = AppointmentSearchManager.getAllowedTimesByType(dayWorkSchedule, new Character[]{'A'}, "123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllowedTimesByType should return matching time slots")
    void shouldReturnMatchingTimeSlots_whenScheduleContainsRequestedCodes() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        Calendar cal1 = createAppointmentTime(0);
        Calendar cal2 = createAppointmentTime(1);
        Calendar cal3 = createAppointmentTime(2);

        timeSlots.put(cal1, 'A');
        timeSlots.put(cal2, 'B'); // Should not match
        timeSlots.put(cal3, 'A');

        dayWorkSchedule.setTimeSlots(timeSlots);

        List<TimeSlot> result = AppointmentSearchManager.getAllowedTimesByType(dayWorkSchedule, new Character[]{'A'}, "123");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TimeSlot::getCode).containsExactly('A', 'A');
        assertThat(result).extracting(TimeSlot::getProviderNo).containsExactly("123", "123");
        assertThat(result).extracting(TimeSlot::getAvailableApptTime).containsExactly(cal1, cal3);
    }

    private static Calendar createAppointmentTime(int hoursAfterStart) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, 15, 9, 0, 0);
        calendar.add(Calendar.HOUR_OF_DAY, hoursAfterStart);
        return calendar;
    }
}
