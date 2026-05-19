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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link PatientDetailStatusService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("PatientDetailStatusService REST endpoint tests")
class PatientDetailStatusServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private CarlosProperties mockCarlosProperties;

    @Override
    protected Object getServiceBean() {
        PatientDetailStatusService service = new PatientDetailStatusService();
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "oscarProperties", mockCarlosProperties);
        return service;
    }

    @Nested
    @DisplayName("GET /patientDetailStatusService/getStatus")
    class GetStatus {

        @Test
        @DisplayName("should return 200 with patient detail status")
        void shouldReturn200_withStatus() {
            when(mockCarlosProperties.isPropertyActive("ENABLE_CONFORMANCE_ONLY_FEATURES")).thenReturn(false);
            when(mockCarlosProperties.isPropertyActive("workflow_enhance")).thenReturn(false);
            when(mockCarlosProperties.getProperty("billregion", "")).thenReturn("BC");
            when(mockCarlosProperties.getProperty("default_view", "")).thenReturn("summary");
            when(mockCarlosProperties.getProperty("hospital_view", "summary")).thenReturn("summary");
            when(mockCarlosProperties.isPropertyActive("showPrimaryCarePhysicianCheck")).thenReturn(false);
            when(mockCarlosProperties.isPropertyActive("showEmploymentStatus")).thenReturn(false);

            Response response = request().path("/patientDetailStatusService/getStatus")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /patientDetailStatusService/isUniqueHC")
    class IsUniqueHC {

        @Test
        @DisplayName("should return 200 success when HIN is unique")
        void shouldReturn200Success_whenHinIsUnique() {
            when(mockDemographicManager.searchByHealthCard(any(LoggedInInfo.class), eq("1234567890")))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/patientDetailStatusService/isUniqueHC")
                .query("hin", "1234567890")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 error when HIN is not unique")
        void shouldReturn200Error_whenHinNotUnique() {
            Demographic demo1 = new Demographic();
            demo1.setDemographicNo(456);
            when(mockDemographicManager.searchByHealthCard(any(LoggedInInfo.class), eq("1234567890")))
                .thenReturn(List.of(demo1));

            Response response = request().path("/patientDetailStatusService/isUniqueHC")
                .query("hin", "1234567890")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
