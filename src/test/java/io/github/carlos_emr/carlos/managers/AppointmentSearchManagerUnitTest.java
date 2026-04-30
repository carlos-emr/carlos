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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.appointment.search.TimeSlot;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppointmentSearchManager unit tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("appointment")
class AppointmentSearchManagerUnitTest {

    @Test
    @DisplayName("getAllowedTimesByType should return empty list when schedule has no time slots")
    void getAllowedTimesByType_shouldReturnEmptyList_whenNoTimeSlots() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        dayWorkSchedule.setTimeSlots(new TreeMap<>());

        List<TimeSlot> result = AppointmentSearchManager.getAllowedTimesByType(dayWorkSchedule, new Character[]{'A'}, "123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllowedTimesByType should return empty list when no codes match")
    void getAllowedTimesByType_shouldReturnEmptyList_whenNoCodesMatch() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        timeSlots.put(Calendar.getInstance(), 'B');
        dayWorkSchedule.setTimeSlots(timeSlots);

        List<TimeSlot> result = AppointmentSearchManager.getAllowedTimesByType(dayWorkSchedule, new Character[]{'A'}, "123");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllowedTimesByType should return matching time slots")
    void getAllowedTimesByType_shouldReturnMatchingTimeSlots() {
        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal2.add(Calendar.HOUR, 1);
        Calendar cal3 = Calendar.getInstance();
        cal3.add(Calendar.HOUR, 2);

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
}
