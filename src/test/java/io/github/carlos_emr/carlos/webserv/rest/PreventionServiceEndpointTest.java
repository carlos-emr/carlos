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
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
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

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;

/**
 * CXF local-transport endpoint tests for {@link PreventionService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("PreventionService REST endpoint tests")
class PreventionServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private PreventionManager mockPreventionManager;
    @Mock
    private PartialDateDao partialDateDao;
    @Mock
    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;

    @Override
    protected Object getServiceBean() {
        return new PreventionService(mockPreventionManager, new PreventionDateService(partialDateDao), projections);
    }

    @Test
    void shouldReturnExplicitConflict_whenStructuredImmunizationsCannotBeProjected() {
        when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123))).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new jakarta.ws.rs.WebApplicationException(Response.status(409)
                .type("text/plain").entity("Structured projection requires review").build()))
                .when(projections).requireRestSupported(123,java.util.Set.of("Immunizations"));
        Response response=request().path("/preventions/active/precision").query("demographicNo",123).get();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.readEntity(String.class)).contains("requires review");
    }

    @Test
    void shouldKeepLegacyAnchorPayload_forNativeYearOnlyPrevention() {
        Prevention record = partialRecord();
        when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(record));
        Response response = request().path("/preventions/active").query("demographicNo", 123).get();
        assertThat(response.getStatus()).isEqualTo(200);
        var expected = new io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionTo1();
        expected.setId(41);
        expected.setPreventionType("Td");
        expected.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
        // Expected legacy fields/anchor follow the converter at merge-base aaaf4444.
        var actual = (com.fasterxml.jackson.databind.node.ObjectNode) responseJson(response).at("/preventions/0");
        var golden = (com.fasterxml.jackson.databind.node.ObjectNode) createTestObjectMapper().valueToTree(expected);
        // Both versions create this transfer timestamp at construction, independently of the clinical date.
        actual.remove("creationDate");
        golden.remove("creationDate");
        assertThat(actual).isEqualTo(golden);
    }

    @Test
    void shouldRefuseLegacyDateOnlyResponse_forExistingPartialHistory() {
        Prevention record = partialRecord();
        markImported(record);
        when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(record));
        Response response = request().path("/preventions/active").query("demographicNo", 123).get();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.readEntity(String.class)).contains("precision-aware", "/precision");
    }

    @Test
    void shouldRetainPartialAndUnknownRecords_inPrecisionResponse() {
        Prevention partial = partialRecord();
        Prevention undated = new Prevention();
        undated.setPreventionType("Td");
        when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(partial, undated));
        Response response = request().path("/preventions/active/precision").query("demographicNo", 123).get();
        assertThat(response.getStatus()).isEqualTo(200);
        var json = responseJson(response);
        assertThat(json.at("/preventions")).hasSize(2);
        assertThat(json.at("/preventions/0/preventionDate").isNull()).isTrue();
        assertThat(json.at("/preventions/0/preventionDateInfo/precision").asText()).isEqualTo("YEAR");
        assertThat(json.at("/preventions/0/preventionDateInfo/originalValue").asText()).isEqualTo("2019");
        assertThat(json.at("/preventions/0/preventionDateInfo/intervalEnd").asText()).isEqualTo("2019-12-31");
        assertThat(json.at("/preventions/1/preventionDateInfo/precision").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldRefuseLegacyResponse_forUndatedHistory() {
        Prevention record = new Prevention();
        markImported(record);
        when(mockPreventionManager.getImmunizationsByDemographic(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(record));
        Response response = request().path("/preventions/immunizations/123").get();
        assertThat(response.getStatus()).isEqualTo(409);
    }

    private static void markImported(Prevention prevention) {
        var provenance = new io.github.carlos_emr.carlos.commn.model.PreventionExt();
        provenance.setKeyval("cdsDatePrecision");
        prevention.addPreventionExt(provenance);
    }

    private Prevention partialRecord() {
        Prevention record = new Prevention();
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 41);
        record.setPreventionType("Td");
        record.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
        when(partialDateDao.getFormat(4, 41, 7)).thenReturn("YYYY");
        return record;
    }

    @Nested
    @DisplayName("GET /preventions/active")
    class GetActivePreventions {

        @Test
        @DisplayName("should return 200 with preventions")
        void shouldReturn200_whenPreventionsExist() {
            Prevention prevention = new Prevention();
            prevention.setPreventionType("Flu");
            prevention.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
            when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(123)))
                .thenReturn(List.of(prevention));

            Response response = request().path("/preventions/active")
                .query("demographicNo", 123)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/preventions").isArray()).isTrue();
            assertThat(wireJson.at("/preventions")).hasSize(1);
            assertThat(wireJson.at("/preventions/0/preventionType").textValue()).isEqualTo("Flu");
        }

        @Test
        @DisplayName("should return 200 with empty list when no preventions")
        void shouldReturn200WithEmptyList_whenNoPreventions() {
            when(mockPreventionManager.getPreventionsByDemographicNo(any(LoggedInInfo.class), eq(456)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/preventions/active")
                .query("demographicNo", 456)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            PreventionResponse body = response.readEntity(PreventionResponse.class);
            assertThat(body.getPreventions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /preventions/immunizations/{demographicNo}")
    class GetImmunizations {

        @Test
        @DisplayName("should return 200 with immunizations")
        void shouldReturn200_whenImmunizationsExist() {
            Prevention immunization = new Prevention();
            immunization.setPreventionType("Flu");
            immunization.setPreventionDate(java.sql.Date.valueOf("2019-01-01"));
            when(mockPreventionManager.getImmunizationsByDemographic(any(LoggedInInfo.class), eq(789)))
                .thenReturn(List.of(immunization));

            Response response = request().path("/preventions/immunizations/789").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/preventions").isArray()).isTrue();
            assertThat(wireJson.at("/preventions")).hasSize(1);
            assertThat(wireJson.at("/preventions/0/preventionType").textValue()).isEqualTo("Flu");
        }

        @Test
        @DisplayName("should return 200 with empty list when no immunizations")
        void shouldReturn200WithEmptyList_whenNoImmunizations() {
            when(mockPreventionManager.getImmunizationsByDemographic(any(LoggedInInfo.class), eq(100)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/preventions/immunizations/100").get();

            assertThat(response.getStatus()).isEqualTo(200);
            PreventionResponse body = response.readEntity(PreventionResponse.class);
            assertThat(body.getPreventions()).isEmpty();
        }
    }
}
