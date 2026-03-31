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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.FacilityManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.FacilityTransfer;

/**
 * SOAP endpoint tests for {@link FacilityWs} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("FacilityWs SOAP endpoint tests")
class FacilityWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private FacilityManager mockFacilityManager;

    @Override
    protected Object getServiceBean() {
        FacilityWs ws = new FacilityWs();
        injectDependency(ws, "facilityManager", mockFacilityManager);
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return FacilityWs.class;
    }

    @Test
    @DisplayName("should return default facility via SOAP")
    void shouldReturnDefaultFacility_viaSoap() {
        Facility facility = new Facility();
        facility.setId(1);
        facility.setName("Test Clinic");
        when(mockFacilityManager.getDefaultFacility(any(LoggedInInfo.class)))
            .thenReturn(facility);

        FacilityWs proxy = createClient(FacilityWs.class);
        FacilityTransfer result = proxy.getDefaultFacility();

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Clinic");
    }

    @Test
    @DisplayName("should return all active facilities via SOAP")
    void shouldReturnAllFacilities_whenActiveRequested() {
        Facility f1 = new Facility();
        f1.setId(1);
        f1.setName("Clinic A");
        Facility f2 = new Facility();
        f2.setId(2);
        f2.setName("Clinic B");
        when(mockFacilityManager.getAllFacilities(any(LoggedInInfo.class), eq(true)))
            .thenReturn(List.of(f1, f2));

        FacilityWs proxy = createClient(FacilityWs.class);
        FacilityTransfer[] results = proxy.getAllFacilities(true);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("should return null or empty array when no facilities exist (JAXB empty array serialization)")
    void shouldReturnEmptyArray_whenNoFacilitiesExist() {
        when(mockFacilityManager.getAllFacilities(any(LoggedInInfo.class), eq(true)))
            .thenReturn(Collections.emptyList());

        FacilityWs proxy = createClient(FacilityWs.class);
        FacilityTransfer[] results = proxy.getAllFacilities(true);

        assertThat(results).isNullOrEmpty();
    }
}
