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
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.appointment.search.FilterDefinition;
import io.github.carlos_emr.carlos.appointment.search.Provider;
import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppointmentSearchManagerImpl}.
 *
 * <p>Verifies that malformed appointment-search filter keys fail fast instead of
 * being silently ignored at search time.</p>
 *
 * @since 2026-04-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentSearchManagerImpl unit tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("appointment")
class AppointmentSearchManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock
    private ScheduleManager scheduleManager;

    @Mock
    private DemographicManager demographicManager;

    @Mock
    private SearchConfig searchConfig;

    @Mock
    private Provider provider;

    @Mock
    private Demographic demographic;

    private AppointmentSearchManagerImpl appointmentSearchManager;

    @BeforeEach
    void setUp() {
        appointmentSearchManager = new AppointmentSearchManagerImpl();
        injectDependency(appointmentSearchManager, "scheduleManager", scheduleManager);
        injectDependency(appointmentSearchManager, "demographicManager", demographicManager);
    }

    @Test
    @DisplayName("should throw AppointmentSearchException when provider filter key is unknown")
    void shouldThrowAppointmentSearchException_whenProviderFilterKeyIsUnknown() {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Calendar startDate = Calendar.getInstance();

        DayWorkSchedule dayWorkSchedule = new DayWorkSchedule();
        TreeMap<Calendar, Character> timeSlots = new TreeMap<>();
        timeSlots.put((Calendar) startDate.clone(), 'A');
        dayWorkSchedule.setTimeSlots(timeSlots);

        FilterDefinition filterDefinition = new FilterDefinition();
        filterDefinition.setFilterClassName("UnknownFilterClass");

        when(demographicManager.getDemographic(loggedInInfo, 123)).thenReturn(demographic);
        when(demographic.getProviderNo()).thenReturn("111");
        when(searchConfig.getProvidersForAppointmentType(123, 456L, "111"))
                .thenReturn(Map.of(provider, new Character[]{'A'}));
        when(searchConfig.getDaysToSearchAheadLimit()).thenReturn(1);
        when(provider.getProviderNo()).thenReturn("200");
        when(provider.getFilter()).thenReturn(List.of(filterDefinition));
        when(scheduleManager.getDayWorkSchedule(eq("200"), any(Calendar.class))).thenReturn(dayWorkSchedule);

        assertThatThrownBy(() -> appointmentSearchManager.findAppointment(loggedInInfo, searchConfig, 123, 456L, startDate))
                .isInstanceOf(AppointmentSearchManager.AppointmentSearchException.class)
                .hasMessage("Unknown AvailableTimeSlotFilter key: UnknownFilterClass");
    }
}
