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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Validations;
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
    @Mock
    private io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private MeasurementManager mockMeasurementManager;

    @Mock
    private MeasurementTypeDao mockMeasurementTypeDao;

    @Mock
    private ValidationsDao mockValidationsDao;

    @Mock
    private DemographicDao mockDemographicDao;

    @Test
    void shouldReturnConflictInsteadOfEmptySubset_whenStructuredCareCannotBeProjected() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class),eq("_measurement"),eq("r"),eq("123"))).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class),eq(123))).thenReturn(true);
        doThrow(new jakarta.ws.rs.WebApplicationException(Response.status(409)
                .type("text/plain").entity("Structured projection requires review").build()))
                .when(projections).requireRestSupported(123,java.util.Set.of("CareElements"));
        Response response=request().path("/measurements/123").post(Entity.json("{\"types\":[\"BP\"]}"));
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.readEntity(String.class)).contains("requires review");
    }

    @Override
    protected Object getServiceBean() {
        MeasurementService service = new MeasurementService();
        service.setSecurityInfoManager(mockSecurityInfoManager);
        service.setMeasurementManager(mockMeasurementManager);
        service.setDemographicDao(mockDemographicDao);
        service.setMeasurementTypeDao(mockMeasurementTypeDao);
        service.setValidationsDao(mockValidationsDao);
        service.setCdsLegacyProjectionService(projections);
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

    @Nested
    @DisplayName("POST /measurements/{demographicNo}/new")
    class AddMeasurement {

        private void allowMeasurementWrite() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("w"), eq("123")))
                .thenReturn(true);
            when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123)))
                .thenReturn(true);
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("123456");
        }

        /** BP as the ON and BC seeds configure it: sitting position, validation rule 6. */
        private void allowConfiguredBloodPressure() {
            configureSeededBloodPressureRule();
        }

        @Test
        @DisplayName("should record a new measurement for an authorized patient")
        void shouldRecordMeasurement_whenAuthorizedForPatient() {
            allowMeasurementWrite();
            when(mockDemographicDao.getDemographicById(123)).thenReturn(new Demographic());
            allowConfiguredBloodPressure();
            when(mockMeasurementManager.addMeasurementUnlessStored(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\",\"comments\":\"New reading\","
                    + "\"measuringInstruction\":\"sitting position\",\"dateObserved\":1770000000000}"));

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/type").textValue()).isEqualTo("BP");
            assertThat(wireJson.at("/demographicId").intValue()).isEqualTo(123);
            assertThat(wireJson.at("/providerNo").textValue()).isEqualTo("123456");
            assertThat(wireJson.at("/dataField").textValue()).isEqualTo("122/80");
            assertThat(wireJson.at("/comments").textValue()).isEqualTo("New reading");
            assertThat(wireJson.at("/measuringInstruction").textValue()).isEqualTo("sitting position");
            assertThat(wireJson.at("/dateObserved").longValue()).isEqualTo(1770000000000L);
            verify(mockMeasurementManager).addMeasurementUnlessStored(any(LoggedInInfo.class), argThat(measurement ->
                measurement.getDemographicId().equals(123)
                    && measurement.getType().equals("BP")
                    && measurement.getDataField().equals("122/80")
                    && measurement.getProviderNo().equals("123456")
                    && measurement.getComments().equals("New reading")
                    && measurement.getMeasuringInstruction().equals("sitting position")));
            verify(projections).requireRestSupported(123, java.util.Set.of("CareElements"));
            verify(mockValidationsDao).find(6);
        }

        @Test
        @DisplayName("should reject a new measurement without a type")
        void shouldReturn400_whenTypeIsMissing() {
            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"dataField\":\"122/80\"}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementManager);
        }

        @Test
        @DisplayName("should reject a new measurement without a value")
        void shouldReturn400_whenDataFieldIsMissing() {
            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\"}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementManager);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "-30610224000001",       // one millisecond below the DATETIME minimum
            "253402300799001",       // one millisecond above the DATETIME maximum
            "9223372036854775808",   // Long.MAX_VALUE + 1
            "18446745843709551616",  // wraps to 1770000000000, a valid past date: only canConvertToLong refuses it
            "1770000000000.5",
            "\"1770000000000\""})
        @DisplayName("should refuse a dateObserved that is not a storable epoch before any access check or lookup")
        void shouldReturn400_whenDateObservedIsNotAStorableEpoch(String raw) {
            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\",\"dateObserved\":" + raw + "}");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("INVALID_DATE_OBSERVED");
            verifyNoInteractions(mockSecurityInfoManager, mockDemographicDao, mockMeasurementTypeDao,
                mockValidationsDao, mockMeasurementManager);
        }

        @Test
        @DisplayName("should refuse writes without patient-scoped measurement privilege")
        void shouldReturn403_whenWritePrivilegeIsMissing() {
            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\"}"));

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockSecurityInfoManager)
                .hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("w"), eq("123"));
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should refuse new measurements when patient record access is blocked")
        void shouldReturn403_whenPatientRecordAccessIsDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), eq("w"), eq("123")))
                .thenReturn(true);

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\"}"));

            assertThat(response.getStatus()).isEqualTo(403);
            verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(123));
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should not accept caller-controlled measurement identity fields")
        void shouldReturn400_whenRequestAttemptsToChangeOwnership() {
            allowMeasurementWrite();

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\",\"demographicId\":789}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should reject a measurement when the patient does not exist")
        void shouldReturn404_whenPatientDoesNotExist() {
            allowMeasurementWrite();

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\"}"));

            assertThat(response.getStatus()).isEqualTo(404);
            verify(mockDemographicDao).getDemographicById(123);
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should reject a measurement type that is not configured")
        void shouldReturn400_whenMeasurementTypeIsUnknown() {
            allowMeasurementWrite();
            when(mockDemographicDao.getDemographicById(123)).thenReturn(new Demographic());

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"UNKNOWN\",\"dataField\":\"123\"}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verify(mockMeasurementTypeDao).findByType("UNKNOWN");
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("UNKNOWN_TYPE");
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should reject a nonnumeric value when the configured type requires numeric input")
        void shouldReturn400_whenNumericValidationFails() {
            allowMeasurementWrite();
            when(mockDemographicDao.getDemographicById(123)).thenReturn(new Demographic());

            configureSeededWeight();

            Response response = request().path("/measurements/123/new")
                .post(Entity.json("{\"type\":\"WT\",\"dataField\":\"not-a-number\"}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verify(mockValidationsDao).find(5);
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0x1p3", "5d", "1e3", "1e999"})
        @DisplayName("should reject a value that is not a plain finite decimal when the type requires numeric input")
        void shouldReturn400_whenNumericValueIsNotFinite(String value) {
            allowWriteToExistingPatient();
            configureSeededAlkalinePhosphatase();

            Response response = postNew("{\"type\":\"ALP\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"2026/01/31", "2026-02-30", "2026-01-31x", "31-01-2026", "yesterday"})
        @DisplayName("should reject a value that is not a yyyy-MM-dd date when the rule is date-only")
        void shouldReturn400_whenDateRuleValueIsNotADate(String value) {
            allowWriteToExistingPatient();
            configureSeededExpectedDeliveryDate();

            Response response = postNew("{\"type\":\"EDC\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should accept a yyyy-MM-dd value when the rule is date-only")
        void shouldRecordMeasurement_whenDateRuleValueIsADate() {
            allowWriteToExistingPatient();
            configureSeededExpectedDeliveryDate();
            when(mockMeasurementManager.addMeasurement(any(), any())).thenAnswer(call -> call.getArgument(1));

            Response response = postNew("{\"type\":\"EDC\",\"dataField\":\"2026-01-31\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(), any());
        }

        private MeasurementType configureType(String typeCode, String instruction, String validationId) {
            MeasurementType type = new MeasurementType();
            type.setType(typeCode);
            type.setMeasuringInstruction(instruction);
            type.setValidation(validationId);
            when(mockMeasurementTypeDao.findByTypeAndMeasuringInstruction(typeCode, instruction))
                .thenReturn(List.of(type));
            when(mockMeasurementTypeDao.findByType(typeCode)).thenReturn(List.of(type));
            return type;
        }

        /** The seeded "Blood Pressure" rule (validations row 6 in the ON and BC data). */
        private void configureSeededBloodPressureRule() {
            configureType("BP", "sitting position", "6");
            Validations rule = new Validations();
            rule.setRegularExp("[0-9]{2,3}/{1}[0-9]{2,3}");
            when(mockValidationsDao.find(6)).thenReturn(rule);
        }

        private void allowWriteToExistingPatient() {
            allowMeasurementWrite();
            when(mockDemographicDao.getDemographicById(123)).thenReturn(new Demographic());
        }

        private Response postNew(String body) {
            return request().path("/measurements/123/new").post(Entity.json(body));
        }

        static Stream<Arguments> malformedMeasurementBodies() {
            String tooLong = "x".repeat(MeasurementService.API_DATA_FIELD_MAX_LENGTH + 1);
            return Stream.of(
                Arguments.of("empty object", "{}"),
                Arguments.of("blank type", "{\"type\":\"  \",\"dataField\":\"1\"}"),
                Arguments.of("type longer than 50 characters",
                    "{\"type\":\"" + "T".repeat(51) + "\",\"dataField\":\"1\"}"),
                Arguments.of("non-text type", "{\"type\":5,\"dataField\":\"1\"}"),
                Arguments.of("blank dataField", "{\"type\":\"BP\",\"dataField\":\" \"}"),
                Arguments.of("dataField longer than the API ceiling",
                    "{\"type\":\"BP\",\"dataField\":\"" + tooLong + "\"}"),
                Arguments.of("non-text dataField", "{\"type\":\"BP\",\"dataField\":120}"),
                Arguments.of("null type", "{\"type\":null,\"dataField\":\"1\"}"),
                Arguments.of("null dataField", "{\"type\":\"BP\",\"dataField\":null}"),
                Arguments.of("comments longer than 255 characters",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"comments\":\"" + "c".repeat(256) + "\"}"),
                Arguments.of("non-text comments", "{\"type\":\"BP\",\"dataField\":\"1\",\"comments\":5}"),
                Arguments.of("measuringInstruction longer than 255 characters",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"measuringInstruction\":\"" + "i".repeat(256) + "\"}"),
                Arguments.of("non-text measuringInstruction",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"measuringInstruction\":true}"),
                Arguments.of("text dateObserved",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"dateObserved\":\"2026-01-01\"}"),
                Arguments.of("fractional dateObserved",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"dateObserved\":1770000000000.5}"),
                Arguments.of("dateObserved before the DATETIME minimum",
                    "{\"type\":\"BP\",\"dataField\":\"1\",\"dateObserved\":-30610224000001}"),
                Arguments.of("unsupported server-set field", "{\"type\":\"BP\",\"dataField\":\"1\",\"id\":5}"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedMeasurementBodies")
        @DisplayName("should reject a malformed body before any access check or save")
        void shouldReturn400BeforeAccessCheck_whenBodyIsMalformed(String description, String body) {
            Response response = postNew(body);

            assertThat(response.getStatus()).as(description).isEqualTo(400);
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementTypeDao, mockMeasurementManager);
        }

        @ParameterizedTest(name = "demographicNo {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("should reject nonpositive patient identifiers before any access check")
        void shouldReturn400_whenPatientIdentifierIsNotPositive(int demographicNo) {
            Response response = request().path("/measurements/" + demographicNo + "/new")
                .post(Entity.json("{\"type\":\"BP\",\"dataField\":\"122/80\"}"));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementManager);
        }

        @Test
        @DisplayName("should accept dateObserved at the lower end of the database DATETIME range")
        void shouldRecordMeasurement_whenDateObservedIsAtLowerRangeBoundary() {
            long dateObserved = -30610224000000L;
            allowWriteToExistingPatient();
            allowConfiguredBloodPressure();
            when(mockMeasurementManager.addMeasurementUnlessStored(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\","
                + "\"measuringInstruction\":\"sitting position\",\"dateObserved\":" + dateObserved + "}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurementUnlessStored(any(LoggedInInfo.class), argThat(measurement ->
                measurement.getDateObserved().getTime() == dateObserved));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"upper DATETIME bound", "one day ahead", "just past the skew allowance"})
        @DisplayName("should refuse a dateObserved in the future, which would rank as the current reading")
        void shouldReturn400_whenDateObservedIsInFuture(String description) {
            long now = System.currentTimeMillis();
            long dateObserved = switch (description) {
                case "upper DATETIME bound" -> 253402300799000L;
                case "one day ahead" -> now + 24L * 60L * 60L * 1000L;
                default -> now + MeasurementService.MAX_FUTURE_SKEW_MILLIS + 60_000L;
            };

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\","
                + "\"measuringInstruction\":\"sitting position\",\"dateObserved\":" + dateObserved + "}");

            assertThat(response.getStatus()).as(description).isEqualTo(400);
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("DATE_OBSERVED_IN_FUTURE");
            verifyNoInteractions(mockSecurityInfoManager, mockMeasurementTypeDao, mockMeasurementManager);
        }

        /** A clinic text type for OCR'd ECG reports; validation "11" is the seeded "No Validations" rule. */
        private void configureEcgTextType(Integer maxLength) {
            configureType("ECGT", "", "11");
            Validations rule = new Validations();
            rule.setName("No Validations");
            rule.setMaxLength(maxLength);
            when(mockValidationsDao.find(11)).thenReturn(rule);
        }

        private static String ecgReport(int length) {
            String text = "FAKE sinus rhythm, rate 72 bpm.\nNo acute ST changes. ";
            return text.repeat(length / text.length() + 1).substring(0, length);
        }

        @ParameterizedTest(name = "{0} characters")
        @ValueSource(ints = {256, 511, MeasurementService.API_DATA_FIELD_MAX_LENGTH})
        @DisplayName("should record a free-text report longer than 255 characters up to the API ceiling")
        void shouldRecordFreeTextReport_upToApiCeiling(int length) throws Exception {
            allowWriteToExistingPatient();
            configureEcgTextType(null);
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            String report = ecgReport(length);

            Response response = postNew(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of("type", "ECGT", "dataField", report)));

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class),
                argThat(measurement -> report.equals(measurement.getDataField())));
        }

        @Test
        @DisplayName("should refuse a report longer than its type's own maxLength rule")
        void shouldRefuseReport_whenLongerThanTypeMaxLength() throws Exception {
            allowWriteToExistingPatient();
            configureEcgTextType(300);

            Response response = postNew(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                java.util.Map.of("type", "ECGT", "dataField", ecgReport(301))));

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should accept a dateObserved slightly ahead of the server clock, within the skew allowance")
        void shouldRecordMeasurement_whenDateObservedIsWithinClockSkew() {
            long dateObserved = System.currentTimeMillis() + 60_000L;
            allowWriteToExistingPatient();
            allowConfiguredBloodPressure();
            when(mockMeasurementManager.addMeasurementUnlessStored(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\","
                + "\"measuringInstruction\":\"sitting position\",\"dateObserved\":" + dateObserved + "}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurementUnlessStored(any(LoggedInInfo.class), any(Measurement.class));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"ab", "abcdefg"})
        @DisplayName("should reject a value shorter or longer than the configured length rule")
        void shouldReturn400_whenValueViolatesConfiguredLength(String value) {
            allowWriteToExistingPatient();
            configureType("NT", "", "3");
            Validations rule = new Validations();
            rule.setMinLength(3);
            rule.setMaxLength(6);
            when(mockValidationsDao.find(3)).thenReturn(rule);

            Response response = postNew("{\"type\":\"NT\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("VALUE_REJECTED");
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abc", "abcdef"})
        @DisplayName("should accept a value at either end of the configured length rule")
        void shouldRecordMeasurement_whenValueIsAtConfiguredLengthBoundary(String value) {
            allowWriteToExistingPatient();
            configureType("NT", "", "3");
            Validations rule = new Validations();
            rule.setMinLength(3);
            rule.setMaxLength(6);
            when(mockValidationsDao.find(3)).thenReturn(rule);
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"NT\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), any(Measurement.class));
        }

        @Test
        @DisplayName("should treat optional fields sent as JSON null as left out")
        void shouldTreatNullOptionalFieldsAsOmitted_whenSentExplicitly() {
            allowWriteToExistingPatient();
            configureType("HT", "", null);
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            long before = System.currentTimeMillis() - 1000;

            Response response = postNew("{\"type\":\"HT\",\"dataField\":\"170\","
                + "\"comments\":null,\"measuringInstruction\":null,\"dateObserved\":null}");

            long after = System.currentTimeMillis();
            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), argThat(measurement ->
                measurement.getComments().isEmpty()
                    && measurement.getMeasuringInstruction().isEmpty()
                    && measurement.getDateObserved().getTime() >= before
                    && measurement.getDateObserved().getTime() <= after));
            // A null dateObserved is a new reading at "now", not a retry of a stored one.
            verify(mockMeasurementManager, never()).addMeasurementUnlessStored(any(), any());
        }

        @Test
        @DisplayName("should store empty optional text and observe the reading now when only required fields are sent")
        void shouldStoreEmptyOptionalFields_whenOnlyRequiredFieldsSent() {
            allowWriteToExistingPatient();
            configureType("HT", "", null);
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
            long before = System.currentTimeMillis() - 1000;

            Response response = postNew("{\"type\":\"HT\",\"dataField\":\"170\"}");

            long after = System.currentTimeMillis();
            assertThat(response.getStatus()).isEqualTo(200);
            // Without a date the reading would sort below every dated one and never become current.
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), argThat(measurement ->
                measurement.getComments().isEmpty()
                    && measurement.getMeasuringInstruction().isEmpty()
                    && measurement.getDateObserved() != null
                    && measurement.getDateObserved().getTime() >= before
                    && measurement.getDateObserved().getTime() <= after
                    && measurement.getDateObserved().getTime() % 1000 == 0));
            verify(mockMeasurementManager, never()).addMeasurementUnlessStored(any(), any());
        }

        @Test
        @DisplayName("should record a blood pressure that satisfies the seeded blood pressure rule")
        void shouldRecordMeasurement_whenValueSatisfiesSeededBloodPressureRule() {
            allowWriteToExistingPatient();
            configureSeededBloodPressureRule();
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew(
                "{\"type\":\"BP\",\"dataField\":\"122/80\",\"measuringInstruction\":\"sitting position\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockValidationsDao).find(6);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), any(Measurement.class));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"80/122", "350/80", "high", "122-80", "1220/80"})
        @DisplayName("should reject a blood pressure the seeded blood pressure rule refuses")
        void shouldReturn400_whenValueViolatesSeededBloodPressureRule(String value) {
            allowWriteToExistingPatient();
            configureSeededBloodPressureRule();

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"" + value
                + "\",\"measuringInstruction\":\"sitting position\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"301", "-1"})
        @DisplayName("should reject a value outside the configured numeric range")
        void shouldReturn400_whenValueIsOutsideConfiguredRange(String value) {
            allowWriteToExistingPatient();
            configureSeededWeight();

            Response response = postNew("{\"type\":\"WT\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("VALUE_REJECTED");
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "300", "82.5"})
        @DisplayName("should accept a value at either end of the seeded 0 to 300 rule")
        void shouldRecordMeasurement_whenValueIsWithinConfiguredRange(String value) {
            allowWriteToExistingPatient();
            configureSeededWeight();
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"WT\",\"dataField\":\"" + value + "\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class),
                argThat(measurement -> value.equals(measurement.getDataField())
                    && "in kg".equals(measurement.getMeasuringInstruction())));
        }

        @Test
        @DisplayName("should accept a plain number on the seeded numeric rule that has no range")
        void shouldRecordMeasurement_whenValueSatisfiesUnboundedNumericRule() {
            allowWriteToExistingPatient();
            configureSeededAlkalinePhosphatase();
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"ALP\",\"dataField\":\"1250\"}");

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should reject text on the seeded numeric rule that has no range")
        void shouldReturn400_whenValueIsNotNumericForUnboundedNumericRule() {
            allowWriteToExistingPatient();
            configureSeededAlkalinePhosphatase();

            Response response = postNew("{\"type\":\"ALP\",\"dataField\":\"abc\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockMeasurementManager);
        }

        /** Seeded rule 5, "Numeric Value: 0 to 300", which WT (in kg) and HT (in cm) use. */
        private static Validations seededWeightRule() {
            Validations rule = new Validations();
            rule.setMaxValue(300.0);
            rule.setMinValue(0.0);
            rule.setNumeric(true);
            return rule;
        }

        private void configureSeededWeight() {
            configureType("WT", "in kg", "5");
            when(mockValidationsDao.find(5)).thenReturn(seededWeightRule());
        }

        /** Seeded rule 14, "Numeric Value greater than or equal to 0": numeric, 0/0 range, so no range applies. */
        private void configureSeededAlkalinePhosphatase() {
            configureType("ALP", "U/L Range 50-300", "14");
            Validations rule = new Validations();
            rule.setMaxValue(0.0);
            rule.setMinValue(0.0);
            rule.setMaxLength(0);
            rule.setMinLength(0);
            rule.setNumeric(true);
            rule.setDate(false);
            when(mockValidationsDao.find(14)).thenReturn(rule);
        }

        /** Seeded rule 13, "Date", which EDC, LMP and DOLE use. */
        private static Validations seededDateRule() {
            Validations rule = new Validations();
            rule.setDate(true);
            return rule;
        }

        private void configureSeededExpectedDeliveryDate() {
            configureType("EDC", "yyyy-mm-dd", "13");
            when(mockValidationsDao.find(13)).thenReturn(seededDateRule());
        }

        private static MeasurementType typeRow(String code, String instruction, String validation) {
            MeasurementType type = new MeasurementType();
            type.setType(code);
            type.setMeasuringInstruction(instruction);
            type.setValidation(validation);
            return type;
        }

        @Test
        @DisplayName("should reject a type and instruction that match configured types with different rules")
        void shouldReturn400_whenMeasurementTypeIsAmbiguous() {
            allowWriteToExistingPatient();
            when(mockMeasurementTypeDao.findByTypeAndMeasuringInstruction("BP", "sitting position"))
                .thenReturn(List.of(typeRow("BP", "sitting position", "6"), typeRow("BP", "sitting position", "11")));

            Response response = postNew(
                "{\"type\":\"BP\",\"dataField\":\"122/80\",\"measuringInstruction\":\"sitting position\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(responseJson(response).at("/code").textValue()).isEqualTo("AMBIGUOUS_TYPE");
            verifyNoInteractions(mockValidationsDao, mockMeasurementManager);
        }

        @Test
        @DisplayName("should ask for the instruction when the type is configured with several and none is sent")
        void shouldReturn400_whenInstructionOmittedForMultiInstructionType() {
            allowWriteToExistingPatient();
            // FEET is seeded with five instructions (pulses, reflexes, ...) sharing rule 7.
            when(mockMeasurementTypeDao.findByType("FEET")).thenReturn(List.of(
                typeRow("FEET", "pulses (Yes/No)", "7"), typeRow("FEET", "reflexes (Yes/No)", "7")));

            Response response = postNew("{\"type\":\"FEET\",\"dataField\":\"Yes\"}");

            assertThat(response.getStatus()).isEqualTo(400);
            var body = responseJson(response);
            assertThat(body.at("/code").textValue()).isEqualTo("INSTRUCTION_REQUIRED");
            assertThat(body.at("/message").textValue()).isEqualTo("measuringInstruction is required for this type");
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should use the type's only configured instruction when none is sent")
        void shouldUseConfiguredInstruction_whenTypeHasOnlyOne() {
            allowWriteToExistingPatient();
            when(mockMeasurementTypeDao.findByType("WT")).thenReturn(List.of(typeRow("WT", "in kg", "5")));
            when(mockValidationsDao.find(5)).thenReturn(seededWeightRule());
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"WT\",\"dataField\":\"82\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).at("/measuringInstruction").textValue()).isEqualTo("in kg");
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class),
                argThat(measurement -> "in kg".equals(measurement.getMeasuringInstruction())));
        }

        @Test
        @DisplayName("should treat a type configured twice with the same instruction and rule as one type")
        void shouldRecordMeasurement_whenTypeIsSeededTwiceIdentically() {
            allowWriteToExistingPatient();
            // LMP is seeded twice (ids 218 and 342) with the same instruction and rule.
            when(mockMeasurementTypeDao.findByType("LMP")).thenReturn(List.of(
                typeRow("LMP", "date", "13"), typeRow("LMP", "date", "13")));
            when(mockValidationsDao.find(13)).thenReturn(seededDateRule());
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew("{\"type\":\"LMP\",\"dataField\":\"2026-10-01\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), any(Measurement.class));
        }

        @Test
        @DisplayName("should store the configured spelling of the type and instruction, not the caller's")
        void shouldStoreConfiguredSpelling_whenCallerUsesDifferentCase() {
            allowWriteToExistingPatient();
            // The lookup collation is case-insensitive, so "bp" finds the BP row.
            when(mockMeasurementTypeDao.findByTypeAndMeasuringInstruction("bp", "SITTING POSITION"))
                .thenReturn(List.of(typeRow("BP", "sitting position", "6")));
            Validations bloodPressure = new Validations();
            bloodPressure.setRegularExp("[0-9]{2,3}/{1}[0-9]{2,3}");
            when(mockValidationsDao.find(6)).thenReturn(bloodPressure);
            when(mockMeasurementManager.addMeasurement(any(LoggedInInfo.class), any(Measurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

            Response response = postNew(
                "{\"type\":\"bp\",\"dataField\":\"122/80\",\"measuringInstruction\":\"SITTING POSITION\"}");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(mockMeasurementManager).addMeasurement(any(LoggedInInfo.class), argThat(measurement ->
                "BP".equals(measurement.getType())
                    && "sitting position".equals(measurement.getMeasuringInstruction())));
        }

        @Test
        @DisplayName("should return the stored reading without saving again when a dated request is repeated")
        void shouldReturnStoredReading_whenRequestIsRetried() {
            allowWriteToExistingPatient();
            allowConfiguredBloodPressure();
            Measurement stored = new Measurement();
            stored.setType("BP");
            stored.setDemographicId(123);
            stored.setDataField("122/80");
            stored.setMeasuringInstruction("sitting position");
            stored.setComments("");
            stored.setDateObserved(new java.util.Date(1770000000000L));
            when(mockMeasurementManager.addMeasurementUnlessStored(any(LoggedInInfo.class), any(Measurement.class)))
                .thenReturn(stored);

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\","
                + "\"measuringInstruction\":\"sitting position\",\"dateObserved\":1770000000456}");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(responseJson(response).at("/dataField").textValue()).isEqualTo("122/80");
            // A dated request goes to the manager's locked check-and-insert, with the identity it
            // matches on; the date is cut to the second, as the DATETIME column keeps it.
            verify(mockMeasurementManager).addMeasurementUnlessStored(any(LoggedInInfo.class), argThat(candidate ->
                Integer.valueOf(123).equals(candidate.getDemographicId())
                    && "BP".equals(candidate.getType())
                    && "122/80".equals(candidate.getDataField())
                    && "sitting position".equals(candidate.getMeasuringInstruction())
                    && "".equals(candidate.getComments())
                    && candidate.getDateObserved().getTime() == 1770000000000L));
            verify(mockMeasurementManager, never()).addMeasurement(any(), any());
        }

        @Test
        @DisplayName("should fail without saving when the type names a non-numeric validation id")
        void shouldReturn500WithoutSaving_whenValidationIdIsNotNumeric() {
            allowWriteToExistingPatient();
            configureType("BP", "sitting position", "six");

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\"}");

            assertThat(response.getStatus()).isEqualTo(500);
            verifyNoInteractions(mockValidationsDao, mockMeasurementManager);
        }

        @Test
        @DisplayName("should fail without saving when the type's validation rule does not exist")
        void shouldReturn500WithoutSaving_whenValidationRuleIsMissing() {
            allowWriteToExistingPatient();
            configureType("BP", "sitting position", "99");

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\"}");

            assertThat(response.getStatus()).isEqualTo(500);
            verify(mockValidationsDao).find(99);
            verifyNoInteractions(mockMeasurementManager);
        }

        @Test
        @DisplayName("should fail without saving when the validation rule's pattern is not a valid regex")
        void shouldReturn500WithoutSaving_whenValidationPatternIsInvalid() {
            allowWriteToExistingPatient();
            configureType("BP", "sitting position", "6");
            Validations rule = new Validations();
            rule.setRegularExp("[0-9");
            when(mockValidationsDao.find(6)).thenReturn(rule);

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\"}");

            assertThat(response.getStatus()).isEqualTo(500);
            verifyNoInteractions(mockMeasurementManager);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("should refuse a write when the session carries no provider identity")
        void shouldReturn403_whenProviderIdentityIsMissing(String providerNo) {
            allowWriteToExistingPatient();
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(providerNo);

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\"}");

            assertThat(response.getStatus()).isEqualTo(403);
            verifyNoInteractions(mockMeasurementTypeDao, mockMeasurementManager);
        }

        @Test
        @DisplayName("should answer 409 without saving when structured care cannot be projected")
        void shouldReturn409WithoutSaving_whenStructuredCareCannotBeProjected() {
            allowWriteToExistingPatient();
            doThrow(new jakarta.ws.rs.WebApplicationException(Response.status(409)
                    .type("text/plain").entity("Structured projection requires review").build()))
                .when(projections).requireRestSupported(123, java.util.Set.of("CareElements"));

            Response response = postNew("{\"type\":\"BP\",\"dataField\":\"122/80\"}");

            assertThat(response.getStatus()).isEqualTo(409);
            verifyNoInteractions(mockMeasurementTypeDao, mockMeasurementManager);
        }
    }
}
