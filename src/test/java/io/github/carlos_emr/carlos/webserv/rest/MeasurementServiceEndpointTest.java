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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link MeasurementService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("MeasurementService REST endpoint tests")
class MeasurementServiceEndpointTest extends CarlosRestTestBase {
    private static final int BASELINE_GET_STATUS = 405; // observed at HEAD before the endpoint existed
    @Mock
    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private MeasurementManager mockMeasurementManager;

    @Test
    void shouldReturnConflictInsteadOfEmptySubset_whenStructuredCareCannotBeProjected() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class),eq("_measurement"),eq("r"),eq("123"))).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class),eq(123))).thenReturn(true);
        org.mockito.Mockito.doThrow(new jakarta.ws.rs.WebApplicationException(Response.status(409)
                .type("text/plain").entity("Structured projection requires review").build()))
                .when(projections).requireRestSupported(123,java.util.Set.of("CareElements"));
        Response response=request().path("/measurements/123").post(Entity.json("{\"types\":[\"BP\"]}"));
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.readEntity(String.class)).contains("requires review");
    }

    /**
     * Server-side capture of the Content-Type the endpoint actually received. A fresh instance is
     * built for every test (the base class rebuilds the providers in each {@code @BeforeEach}), so
     * no state is shared between tests.
     */
    private HeaderCaptureFilter headerCapture;

    /** Records, on the server side, whether a request arrived with a Content-Type header. */
    static final class HeaderCaptureFilter implements ContainerRequestFilter {
        final AtomicReference<String> contentType = new AtomicReference<>("not-called");

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            contentType.set(requestContext.getHeaderString(HttpHeaders.CONTENT_TYPE));
        }
    }

    @Override
    protected List<Object> additionalServerProviders() {
        headerCapture = new HeaderCaptureFilter();
        return List.of(headerCapture);
    }

    @Override
    protected Object getServiceBean() {
        MeasurementService service = new MeasurementService();
        injectDependency(service,"projections",projections);
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
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123))).thenReturn(true);

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
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/measurements/BP").isArray()).isTrue();
            assertThat(wireJson.at("/measurements/BP")).hasSize(1);
            assertThat(wireJson.at("/measurements/BP/0/dataField").textValue()).isEqualTo("120/80");
            assertThat(wireJson.at("/measurements/BP/0/type").textValue()).isEqualTo("BP");
            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123"));
            verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123));
        }

        @Test
        @DisplayName("should write the measurement entity's midnight timestamps as epoch milliseconds")
        void shouldWriteEntityTimestampsAsEpochMillis_whenTheyFallOnMidnight() {
            // MeasurementResponse carries Measurement entities, not MeasurementTo1, so the entity's
            // own DATETIME fields need the epoch shape too (issue #3877).
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123))).thenReturn(true);
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(2026, Calendar.MARCH, 11);
            Date midnight = calendar.getTime();
            Measurement m = new Measurement();
            m.setType("WT");
            m.setDataField("70");
            m.setDateObserved(midnight);
            m.setCreateDate(midnight);
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(123), any()))
                .thenReturn(List.of(m));

            Response response = request().path("/measurements/123")
                .post(Entity.json("{\"types\":[\"WT\"]}"));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            for (String field : List.of("dateObserved", "createDate")) {
                var value = wireJson.at("/measurements/WT/0/" + field);
                assertThat(value.isIntegralNumber()).as("%s on the wire: %s", field, value).isTrue();
                assertThat(value.longValue()).as(field).isEqualTo(midnight.getTime());
            }
        }

        @Test
        @DisplayName("should return 200 with empty response when no types match")
        void shouldReturn200WithEmptyResponse_whenNoTypesMatch() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("456")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(456))).thenReturn(true);
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(456), any()))
                .thenReturn(Collections.emptyList());

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            ArrayNode types = json.putArray("types");
            types.add("HT");

            Response response = request().path("/measurements/456")
                .post(Entity.json(json.toString()));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/measurements").isObject()).isTrue();
            assertThat(wireJson.at("/measurements")).isEmpty();
        }

        @Test
        @DisplayName("should return 200 with empty response when types array is empty")
        void shouldReturn200WithEmptyResponse_whenTypesArrayIsEmpty() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("789")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(789))).thenReturn(true);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            json.putArray("types");

            Response response = request().path("/measurements/789")
                .post(Entity.json(json.toString()));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/measurements").isObject()).isTrue();
            assertThat(wireJson.at("/measurements")).isEmpty();
        }

        @Test
        @DisplayName("should deny patient-specific measurement access before querying data")
        void shouldReturn403_whenPatientMeasurementAccessDenied() {
            Response response = request().path("/measurements/123")
                .post(Entity.json("{\"types\":[\"BP\"]}"));

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123"));
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should deny a blocked chart even when measurement privileges allow reading")
        void shouldReturn403_whenPatientRecordAccessDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123")))
                .thenReturn(true);

            Response response = request().path("/measurements/123")
                .post(Entity.json("{\"types\":[\"BP\"]}"));

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123));
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should reject nonpositive patient identifiers before querying data")
        void shouldReturn400_whenPatientIdentifierInvalid() {
            Response response = request().path("/measurements/0")
                .post(Entity.json("{\"types\":[\"BP\"]}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementManager);
        }
    }

    private MeasurementType sampleType(String type, String display, String description, String instruction) {
        MeasurementType t = new MeasurementType();
        t.setType(type);
        t.setTypeDisplayName(display);
        t.setTypeDescription(description);
        t.setMeasuringInstruction(instruction);
        t.setValidation("FAKE-VALIDATION-RULE");
        return t;
    }

    @Nested
    @DisplayName("GET /measurements/types")
    class GetMeasurementTypes {

        @Test
        @DisplayName("should return 200 with the four public fields in manager order")
        void shouldReturn200_withTypesInManagerOrder() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq(null)))
                .thenReturn(true);
            when(mockMeasurementManager.getMeasurementTypes(any(LoggedInInfo.class))).thenReturn(List.of(
                sampleType("FAKE-A", "FAKE Alpha", "FAKE alpha description", "FAKE alpha instruction"),
                sampleType("FAKE-B", "FAKE Beta", "FAKE beta description", "FAKE beta instruction")));

            Response response = request().path("/measurements/types").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/measurementTypes")).hasSize(2);
            assertThat(wireJson.at("/measurementTypes/0/type").textValue()).isEqualTo("FAKE-A");
            assertThat(wireJson.at("/measurementTypes/0/typeDisplayName").textValue()).isEqualTo("FAKE Alpha");
            assertThat(wireJson.at("/measurementTypes/0/typeDescription").textValue()).isEqualTo("FAKE alpha description");
            assertThat(wireJson.at("/measurementTypes/0/measuringInstruction").textValue()).isEqualTo("FAKE alpha instruction");
            assertThat(wireJson.at("/measurementTypes/1/type").textValue()).isEqualTo("FAKE-B");
            assertThat(wireJson.at("/measurementTypes/1/typeDisplayName").textValue()).isEqualTo("FAKE Beta");
            assertThat(wireJson.at("/measurementTypes/0").size()).isEqualTo(4);
        }

        @Test
        @DisplayName("should return 200 with an empty list when no types are configured")
        void shouldReturn200_withEmptyListWhenNoTypes() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq(null)))
                .thenReturn(true);
            when(mockMeasurementManager.getMeasurementTypes(any(LoggedInInfo.class))).thenReturn(Collections.emptyList());

            Response response = request().path("/measurements/types").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/measurementTypes").isArray()).isTrue();
            assertThat(wireJson.at("/measurementTypes")).isEmpty();
        }

        @Test
        @DisplayName("should deny without the measurement privilege and never touch the manager")
        void shouldReturn403_whenMeasurementPrivilegeMissing() {
            Response response = request().path("/measurements/types").get();

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq(null));
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should not expose id, validation or createDate")
        void shouldNotExposeInternalFields_inTypeJson() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq(null)))
                .thenReturn(true);
            MeasurementType t = sampleType("FAKE-A", "FAKE Alpha", "FAKE alpha description", "FAKE alpha instruction");
            when(mockMeasurementManager.getMeasurementTypes(any(LoggedInInfo.class))).thenReturn(List.of(t));

            Response response = request().path("/measurements/types").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var node = responseJson(response).at("/measurementTypes/0");
            assertThat(node.has("id")).isFalse();
            assertThat(node.has("validation")).isFalse();
            assertThat(node.has("createDate")).isFalse();
            assertThat(node.toString()).doesNotContain("FAKE-VALIDATION-RULE");
        }

        @Test
        @DisplayName("should keep GET and POST on /measurements/{demographicNo} behaving as before")
        void shouldKeepNumericPathRouting_unchanged() {
            Response get = request().path("/measurements/123").get();
            assertThat(get.getStatus()).as("GET baseline").isEqualTo(BASELINE_GET_STATUS);

            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq("123")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123))).thenReturn(true);
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(123), any()))
                .thenReturn(Collections.emptyList());
            Response post = request().path("/measurements/123").post(Entity.json("{\"types\":[\"BP\"]}"));
            assertThat(post.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should answer 200 when the request carries no Content-Type")
        void shouldReturn200_whenContentTypeAbsent() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("r"), eq(null)))
                .thenReturn(true);
            when(mockMeasurementManager.getMeasurementTypes(any(LoggedInInfo.class))).thenReturn(Collections.emptyList());

            Response response = request().path("/measurements/types")
                .replaceHeader(HttpHeaders.CONTENT_TYPE, null).get();

            assertThat(response.getStatus()).isEqualTo(200);
            // What the server received, not what the client meant to send.
            assertThat(headerCapture.contentType.get()).isNull();
        }

        @Test
        @DisplayName("should not route POST /measurements/types to any method")
        void shouldReturn404_whenPostingToTypes() {
            // 404 is CXF behaviour, not JAX-RS specification behaviour (the specification would
            // answer 405): CXF cannot convert "types" to the Integer path parameter of
            // POST /{demographicNo}, so that method is not selected. A CXF upgrade that changes
            // this should be read as a routing change, not as a regression here.
            Response response = request().path("/measurements/types")
                .post(Entity.json("{\"types\":[\"BP\"]}"));

            assertThat(response.getStatus()).isEqualTo(404);
            verifyNoInteractions(mockMeasurementManager, projections, mockSecurityInfoManager);
        }
    }
}
