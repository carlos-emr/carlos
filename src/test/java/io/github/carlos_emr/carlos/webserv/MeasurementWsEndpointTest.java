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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.MeasurementMapTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.MeasurementTransfer;

/**
 * SOAP-level endpoint tests for {@link MeasurementWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for measurement operations:
 * SOAP envelope marshalling/unmarshalling and response serialization of
 * {@link MeasurementTransfer} and {@link MeasurementMapTransfer} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("MeasurementWs SOAP endpoint tests")
class MeasurementWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private MeasurementManager measurementManager;

    private MeasurementWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new MeasurementWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return MeasurementWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(MeasurementManager.class, measurementManager);
        injectDependency(ws, "measurementManager", measurementManager);
    }

    /** Tests for the getMeasurement SOAP operation. */
    @Nested
    @DisplayName("getMeasurement operation")
    class GetMeasurement {

        @Test
        @DisplayName("should return measurement transfer when found")
        void shouldReturnMeasurementTransfer_whenFound() {
            Measurement measurement = new Measurement();
            measurement.setType("BP");
            when(measurementManager.getMeasurement(any(LoggedInInfo.class), eq(25))).thenReturn(measurement);

            MeasurementWs proxy = createClient(MeasurementWs.class);
            MeasurementTransfer result = proxy.getMeasurement(25);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when measurement not found")
        void shouldReturnNull_whenMeasurementNotFound() {
            when(measurementManager.getMeasurement(any(LoggedInInfo.class), eq(999))).thenReturn(null);

            MeasurementWs proxy = createClient(MeasurementWs.class);
            MeasurementTransfer result = proxy.getMeasurement(999);

            assertThat(result).isNull();
        }
    }

    /** Tests for the getMeasurementMaps SOAP operation. */
    @Nested
    @DisplayName("getMeasurementMaps operation")
    class GetMeasurementMaps {

        @Test
        @DisplayName("should return measurement maps via SOAP")
        void shouldReturnMeasurementMaps_viaSoap() {
            List<MeasurementMap> maps = new ArrayList<>();
            MeasurementMap map = new MeasurementMap();
            map.setId(1);
            maps.add(map);
            when(measurementManager.getMeasurementMaps()).thenReturn(maps);

            MeasurementWs proxy = createClient(MeasurementWs.class);
            MeasurementMapTransfer[] result = proxy.getMeasurementMaps();

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should return null or empty array when no maps exist (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoMapsExist() {
            when(measurementManager.getMeasurementMaps()).thenReturn(Collections.emptyList());

            MeasurementWs proxy = createClient(MeasurementWs.class);
            MeasurementMapTransfer[] result = proxy.getMeasurementMaps();

            assertThat(result).isNullOrEmpty();
        }
    }

    /** Tests for the getMeasurementsCreatedAfterDate SOAP operation. */
    @Nested
    @DisplayName("getMeasurementsCreatedAfterDate operation")
    class GetMeasurementsCreatedAfterDate {

        @Test
        @DisplayName("should return measurements created after date")
        void shouldReturnMeasurements_createdAfterDate() {
            List<Measurement> measurements = new ArrayList<>();
            Measurement m = new Measurement();
            measurements.add(m);
            when(measurementManager.getCreatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(measurements);

            MeasurementWs proxy = createClient(MeasurementWs.class);
            MeasurementTransfer[] result = proxy.getMeasurementsCreatedAfterDate(new Date(), 10);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
