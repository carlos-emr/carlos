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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link MeasurementService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("MeasurementService REST endpoint tests")
class MeasurementServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private MeasurementManager mockMeasurementManager;

    @Override
    protected Object getServiceBean() {
        MeasurementService service = new MeasurementService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "measurementManager", mockMeasurementManager);
        return service;
    }

    @Nested
    @DisplayName("POST /measurements/{demographicNo}")
    class GetMeasurements {

        @Test
        @DisplayName("should return 200 with measurements when types provided")
        void shouldReturn200_whenMeasurementsExist() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), any()))
                .thenReturn(true);

            Measurement m = new Measurement();
            m.setType("BP");
            m.setDataField("120/80");
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(123), any()))
                .thenReturn(List.of(m));

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            ArrayNode types = json.putArray("types");
            types.add("BP");

            Response response = request().path("/measurements/123")
                .post(Entity.json(json.toString()));

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty response when no types match")
        void shouldReturn200WithEmptyResponse_whenNoTypesMatch() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), any()))
                .thenReturn(true);
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(456), any()))
                .thenReturn(Collections.emptyList());

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            ArrayNode types = json.putArray("types");
            types.add("HT");

            Response response = request().path("/measurements/456")
                .post(Entity.json(json.toString()));

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty response when types array is empty")
        void shouldReturn200WithEmptyResponse_whenTypesArrayIsEmpty() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), any()))
                .thenReturn(true);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            json.putArray("types");

            Response response = request().path("/measurements/789")
                .post(Entity.json(json.toString()));

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
