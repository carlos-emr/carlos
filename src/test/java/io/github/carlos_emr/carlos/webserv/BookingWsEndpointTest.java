/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.appointment.search.AppointmentType;
import io.github.carlos_emr.carlos.appointment.search.BookingType;
import io.github.carlos_emr.carlos.appointment.search.SearchConfig;
import io.github.carlos_emr.carlos.managers.AppointmentSearchManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * SOAP-level endpoint tests for {@link BookingWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for booking operations:
 * SOAP envelope marshalling/unmarshalling, WSDL processing, and response
 * serialization of {@link BookingType} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("BookingWs SOAP endpoint tests")
class BookingWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private ScheduleManager scheduleManager;

    @Mock
    private AppointmentSearchManager appointmentSearchManager;

    @Mock
    private DemographicManager demographicManager;

    @Mock
    private SearchConfig searchConfig;

    private BookingWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new BookingWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return BookingWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(ScheduleManager.class, scheduleManager);
        registerMock(AppointmentSearchManager.class, appointmentSearchManager);
        registerMock(DemographicManager.class, demographicManager);
        injectDependency(ws, "scheduleManager", scheduleManager);
        injectDependency(ws, "appointmentSearchManager", appointmentSearchManager);
        injectDependency(ws, "demographicManager", demographicManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999");
    }

    /** Tests for the getAppointmentTypesByProvider SOAP operation. */
    @Nested
    @DisplayName("getAppointmentTypesByProvider operation")
    class GetAppointmentTypesByProvider {

        @Test
        @DisplayName("should return empty booking types when no appointment types configured")
        void shouldReturnEmptyBookingTypes_whenNoTypesConfigured() {
            when(appointmentSearchManager.getProviderSearchConfig(anyString())).thenReturn(searchConfig);
            when(appointmentSearchManager.getAppointmentTypes(any(SearchConfig.class), anyString()))
                .thenReturn(new ArrayList<>());

            BookingWs proxy = createClient(BookingWs.class);
            BookingType[] result = proxy.getAppointmentTypesByProvider("001");

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("should return booking types for provider")
        void shouldReturnBookingTypes_forProvider() {
            List<AppointmentType> types = new ArrayList<>();
            AppointmentType apt = new AppointmentType();
            types.add(apt);

            BookingType bookingType = new BookingType();
            bookingType.setName("General Visit");

            when(appointmentSearchManager.getProviderSearchConfig(anyString())).thenReturn(searchConfig);
            when(appointmentSearchManager.getAppointmentTypes(any(SearchConfig.class), eq("001")))
                .thenReturn(types);
            when(searchConfig.getBookingType(any(AppointmentType.class), anyString()))
                .thenReturn(bookingType);

            BookingWs proxy = createClient(BookingWs.class);
            BookingType[] result = proxy.getAppointmentTypesByProvider("001");

            assertThat(result).isNotNull().hasSize(1);
        }
    }

    /** Tests for the getExternalAppointmentTypes SOAP operation. */
    @Nested
    @DisplayName("getExternalAppointmentTypes operation")
    class GetExternalAppointmentTypes {

        @Test
        @DisplayName("should return null when search config not found")
        void shouldReturnNull_whenSearchConfigNotFound() {
            when(appointmentSearchManager.getProviderSearchConfig(anyString())).thenReturn(null);

            BookingWs proxy = createClient(BookingWs.class);
            BookingType[] result = proxy.getExternalAppointmentTypes(100);

            assertThat(result).isNull();
        }
    }
}
