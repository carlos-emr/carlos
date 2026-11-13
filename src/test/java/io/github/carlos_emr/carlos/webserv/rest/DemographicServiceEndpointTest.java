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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.NoteManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.SecurityExceptionMapper;

/**
 * CXF local-transport endpoint tests for {@link DemographicService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, query parameter binding,
 * and HTTP status codes for the demographics REST API. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("DemographicService REST endpoint tests")
class DemographicServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private DemographicManager mockDemographicManager;
    @Mock private io.github.carlos_emr.carlos.commn.dao.DemographicDao updateRecords;
    @Mock
    private ContactDao mockContactDao;
    @Mock
    private AllergyManager mockAllergyManager;
    @Mock
    private MeasurementManager mockMeasurementManager;
    @Mock
    private WaitingListDao mockWaitingListDao;
    @Mock
    private WaitingListNameDao mockWaitingListNameDao;
    @Mock
    private NoteManager mockNoteManager;
    @Mock
    private ProviderDao mockProviderDao;
    @Mock
    private RxManager mockRxManager;
    @Mock
    private SecUserRoleDao mockSecUserRoleDao;
    @Mock
    private ProfessionalSpecialistDao mockSpecialistDao;
    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private CdsLegacyProjectionDao mockProjectionRecords;

    @Test
    void shouldPreserveXml_whenDemographicListClientRequestsIt() throws Exception {
        var demo = createTestDemographic(17, "Synthetic", "XmlPatient");
        when(mockDemographicManager.getActiveDemographicCount(any(LoggedInInfo.class))).thenReturn(1L);
        when(mockDemographicManager.getActiveDemographics(any(LoggedInInfo.class), eq(0), eq(10)))
                .thenReturn(List.of(demo));
        try (Response response = request().path("/demographics").query("offset", 0).query("limit", 10)
                .replaceHeader("Accept", "application/xml").get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getMediaType().toString()).startsWith("application/xml");
            var xml = io.github.carlos_emr.carlos.utility.XmlUtils.toDocument(response.readEntity(String.class));
            assertThat(xml.getElementsByTagName("lastName").item(0).getTextContent()).isEqualTo("XmlPatient");
        }
    }

    @Override
    protected Object getServiceBean() {
        DemographicService service = new DemographicService(new CdsLegacyProjectionService(mockProjectionRecords), updateRecords);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "contactDao", mockContactDao);
        injectDependency(service, "allergyManager", mockAllergyManager);
        injectDependency(service, "measurementManager", mockMeasurementManager);
        injectDependency(service, "waitingListDao", mockWaitingListDao);
        injectDependency(service, "waitingListNameDao", mockWaitingListNameDao);
        injectDependency(service, "noteManager", mockNoteManager);
        injectDependency(service, "providerDao", mockProviderDao);
        injectDependency(service, "rxManager", mockRxManager);
        injectDependency(service, "secUserRoleDao", mockSecUserRoleDao);
        injectDependency(service, "specialistDao", mockSpecialistDao);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @Override
    protected List<Object> additionalServerProviders() {
        return List.of(new SecurityExceptionMapper(),
                new io.github.carlos_emr.carlos.webserv.rest.util.DemographicWriteProfileResponseFilter());
    }

    @BeforeEach
    void setUpSecurityDefaults() {
        var existing = createTestDemographic(42, "Existing", "Patient");
        existing.setSin("FAKE-SIN"); existing.setHin("FAKE-HIN");
        org.mockito.Mockito.lenient().when(updateRecords.findForUpdate(42)).thenReturn(existing);
        org.mockito.Mockito.lenient().when(updateRecords.findFreshForUpdate(42)).thenReturn(existing);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq(42)))
            .thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(42)))
            .thenReturn(true);
    }

    private Demographic createTestDemographic(int id, String firstName, String lastName) {
        Demographic demo = new Demographic();
        demo.setDemographicNo(id);
        demo.setFirstName(firstName);
        demo.setLastName(lastName);
        demo.setDateOfBirth("15");
        demo.setMonthOfBirth("06");
        demo.setYearOfBirth("1980");
        demo.setSex("M");
        return demo;
    }

    @Nested
    @DisplayName("Demographic write field lengths")
    class WriteFieldLengths {

        @BeforeEach
        void allowRecordUpdates() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("u"), eq(42)))
                    .thenReturn(true);
        }

        private DemographicTo1 payload(String method) {
            DemographicTo1 data = new DemographicTo1();
            if ("PUT".equals(method)) data.setDemographicNo(42);
            data.setFirstName("FAKE");
            data.setLastName("PATIENT");
            data.setDobYear("1980");
            data.setDobMonth("06");
            data.setDobDay("15");
            return data;
        }

        @ParameterizedTest
        @CsvSource({"POST,pronoun,A", "POST,gender,A", "PUT,pronoun,A", "PUT,gender,A",
                "POST,pronoun,😀", "POST,gender,😀", "PUT,pronoun,😀", "PUT,gender,😀"})
        void shouldRejectOverlongFields_beforeAnyWrite(String method, String field, String character) {
            DemographicTo1 data = payload(method);
            String invalid = character.repeat(26);
            if ("pronoun".equals(field)) data.setPronoun(invalid);
            else data.setGender(invalid);
            data.setNurse("999998");
            data.setNotes("FAKE-NOTES");
            data.setWaitingListID(7);

            try (Response response = request().path("/demographics").invoke(method, data)) {
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.readEntity(String.class))
                        .contains("exceeds maximum length of 25 characters.")
                        .doesNotContain(invalid, "FAKE-NOTES");
            }
            verifyNoInteractions(mockDemographicManager, mockWaitingListDao);
        }

        @ParameterizedTest
        @CsvSource({"POST,A", "PUT,A", "POST,😀", "PUT,😀"})
        void shouldPreserveFullColumnCapacity_whenFieldsAreAtTheLimit(String method, String character) {
            DemographicTo1 data = payload(method);
            String exact = character.repeat(25);
            data.setPronoun(exact);
            data.setGender(exact);

            try (Response response = request().path("/demographics").invoke(method, data)) {
                assertThat(response.getStatus()).isEqualTo(200);
                var json = responseJson(response);
                assertThat(json.get("pronoun").textValue()).isEqualTo(exact);
                assertThat(json.get("gender").textValue()).isEqualTo(exact);
            }
            ArgumentCaptor<Demographic> saved = ArgumentCaptor.forClass(Demographic.class);
            if ("POST".equals(method)) {
                verify(mockDemographicManager).createDemographic(any(LoggedInInfo.class), saved.capture(), eq(null));
            } else {
                verify(mockDemographicManager).updateDemographic(any(LoggedInInfo.class), saved.capture());
            }
            assertThat(saved.getValue().getPronoun()).isEqualTo(exact);
            assertThat(saved.getValue().getGender()).isEqualTo(exact);
        }

        @ParameterizedTest
        @CsvSource({"alert,Alert,A", "nurse,Nurse,A", "resident,Resident,A", "midwife,Midwife,A",
                "alert,Alert,😀", "waitingListNote,Waiting list note,A", "waitingListNote,Waiting list note,😀"})
        void shouldRejectOverlongCustomFieldsAndNote_beforeAnyWrite(String field, String label, String character) {
            // Issue #5869: these VARCHAR(255) fields were cut silently under an empty sql_mode.
            // WLWaitingListUtil reaches the DAO through SpringUtils, so register it there too: the
            // no-interaction check below then covers the waiting-list write, not only the service field.
            registerMock(WaitingListDao.class, mockWaitingListDao);
            DemographicTo1 data = payload("PUT");
            String invalid = character.repeat(256);
            switch (field) {
                case "alert" -> data.setAlert(invalid);
                case "nurse" -> data.setNurse(invalid);
                case "resident" -> data.setResident(invalid);
                case "midwife" -> data.setMidwife(invalid);
                default -> data.setWaitingListNote(invalid);
            }
            data.setWaitingListID(7);

            try (Response response = request().path("/demographics").invoke("PUT", data)) {
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.readEntity(String.class))
                        .isEqualTo(label + " exceeds maximum length of 255 characters.");
            }
            verifyNoInteractions(mockDemographicManager, mockWaitingListDao, updateRecords);
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"A", "😀"})
        void shouldKeepCustomFieldsAndNoteWhole_whenTheyAreAtTheLimit(String character) {
            registerMock(WaitingListDao.class, mockWaitingListDao);
            // The patient is on list 7, so the note is written and can be read back.
            io.github.carlos_emr.carlos.commn.model.WaitingList onTheList = new io.github.carlos_emr.carlos.commn.model.WaitingList();
            onTheList.setListId(7);
            onTheList.setDemographicNo(42);
            onTheList.setPosition(3);
            when(mockWaitingListDao.findByWaitingListIdAndDemographicId(7, 42)).thenReturn(List.of(onTheList));
            DemographicTo1 data = payload("PUT");
            String exact = character.repeat(255);
            data.setAlert(exact);
            data.setNurse(exact);
            data.setResident(exact);
            data.setMidwife(exact);
            data.setWaitingListID(7);
            data.setWaitingListNote(exact);

            try (Response response = request().path("/demographics").invoke("PUT", data)) {
                assertThat(response.getStatus()).isEqualTo(200);
            }
            ArgumentCaptor<io.github.carlos_emr.carlos.commn.model.DemographicCust> cust =
                    ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.DemographicCust.class);
            verify(mockDemographicManager).createUpdateDemographicCust(any(LoggedInInfo.class), cust.capture());
            assertThat(cust.getValue().getAlert()).isEqualTo(exact);
            assertThat(cust.getValue().getNurse()).isEqualTo(exact);
            assertThat(cust.getValue().getResident()).isEqualTo(exact);
            assertThat(cust.getValue().getMidwife()).isEqualTo(exact);
            ArgumentCaptor<io.github.carlos_emr.carlos.commn.model.WaitingList> note =
                    ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.WaitingList.class);
            verify(mockWaitingListDao).saveEntity(note.capture());
            assertThat(note.getValue().getNote()).isEqualTo(exact);
            assertThat(note.getValue().getListId()).isEqualTo(7);
        }

        @Test
        void shouldNotCheckTheNote_whenNoWaitingListIsNamed() {
            // The note is written only with a list, so without one a long note is not a reason to refuse.
            registerMock(WaitingListDao.class, mockWaitingListDao);
            DemographicTo1 data = payload("PUT");
            data.setWaitingListNote("A".repeat(256));

            try (Response response = request().path("/demographics").invoke("PUT", data)) {
                assertThat(response.getStatus()).isEqualTo(200);
            }
            verifyNoInteractions(mockWaitingListDao);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"default", "preserve-hidden-identifiers", "legacy-null-identifiers"})
    void shouldPreserveHiddenIdentifiersByDefault_andSupportExplicitLegacyNullClears(String profile) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("u"), eq(42)))
                .thenReturn(true);
        var data = new DemographicTo1(); data.setDemographicNo(42);
        data.setFirstName("Changed"); data.setLastName("Patient");
        data.setDobYear("1980"); data.setDobMonth("06"); data.setDobDay("15");
        if (profile.equals("legacy-null-identifiers")) {
            when(updateRecords.findFreshForUpdate(42)).thenThrow(new UnsupportedOperationException("Legacy DAO"));
        }
        var client = request().path("/demographics");
        if (!profile.equals("default")) client.header("X-Carlos-Demographic-Write-Profile", profile);
        try (Response response = client.invoke("PUT", data)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeaderString("X-Carlos-Demographic-Write-Profile"))
                    .isEqualTo(profile.equals("default") ? "preserve-hidden-identifiers" : profile);
            assertThat(response.readEntity(String.class)).doesNotContain("FAKE-SIN");
        }
        var saved = ArgumentCaptor.forClass(Demographic.class);
        verify(mockDemographicManager).updateDemographic(eq(mockLoggedInInfo), saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Changed");
        boolean preserve = !profile.equals("legacy-null-identifiers");
        assertThat(saved.getValue().getSin()).isEqualTo(preserve ? "FAKE-SIN" : null);
        assertThat(saved.getValue().getHin()).isEqualTo(preserve ? "FAKE-HIN" : null);
        if (preserve) verify(updateRecords).findFreshForUpdate(42);
        else verify(updateRecords).findForUpdate(42);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "unknown-private-value", "legacy-null-identifiers,preserve-hidden-identifiers"})
    void shouldRejectUnknownIdentifierWriteProfiles_beforeLockingOrWriting(String profile) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("u"), eq(42)))
                .thenReturn(true);
        var data = new DemographicTo1(); data.setDemographicNo(42);
        data.setDobYear("1980"); data.setDobMonth("06"); data.setDobDay("15");
        try (Response response = request().path("/demographics")
                .header("X-Carlos-Demographic-Write-Profile", profile).invoke("PUT", data)) {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.readEntity(String.class)).doesNotContain("unknown-private-value");
        }
        verifyNoInteractions(updateRecords, mockDemographicManager);
    }

    @ParameterizedTest
    @CsvSource({"HY000,1020", "HY000,1205", "40001,1213"})
    void shouldReportPreWriteDatabaseConflicts_withoutLeakingSqlOrChangingRelatedRecords(String state, int code) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("u"), eq(42)))
                .thenReturn(true);
        when(updateRecords.findFreshForUpdate(42)).thenThrow(new IllegalStateException(
                new java.sql.SQLException("FAKE-PRIVATE-SQL-VALUE", state, code)));
        var data = new DemographicTo1(); data.setDemographicNo(42);
        data.setDobYear("1980"); data.setDobMonth("06"); data.setDobDay("15");
        data.setNurse("41"); data.setWaitingListID(7);
        try (Response response = request().path("/demographics").invoke("PUT", data)) {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.readEntity(String.class)).contains("reload before retrying")
                    .doesNotContain("FAKE-PRIVATE-SQL-VALUE", "SQLException");
        }
        verifyNoInteractions(mockDemographicManager, mockWaitingListDao);
    }

    /** Tests for GET /demographics (list all). */
    @Nested
    @DisplayName("GET /demographics")
    class GetAllDemographics {

        @ParameterizedTest
        @CsvSource({"0,501", "0,2147483647", "0,-1", "-1,10"})
        @DisplayName("should return 403 before validating pagination when demographic read is denied")
        void shouldReturn403_whenInvalidListPaginationIsRequestedWithoutReadPrivilege(int offset, int limit) {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), any()))
                    .thenReturn(false);
            try (Response response = request().path("/demographics")
                    .query("offset", offset).query("limit", limit).get()) {
                assertThat(response.getStatus()).isEqualTo(403);
                verifyNoInteractions(mockDemographicManager);
            }
        }

        @ParameterizedTest
        @CsvSource({"0,501", "0,2147483647", "0,-1", "-1,10"})
        @DisplayName("should return 400 before reading demographics for invalid pagination")
        void shouldReturn400_whenListPaginationIsInvalid(int offset, int limit) {
            try (Response response = request().path("/demographics")
                    .query("offset", offset).query("limit", limit).get()) {
                assertThat(response.getStatus()).isEqualTo(400);
                verifyNoInteractions(mockDemographicManager);
            }
        }

        @ParameterizedTest
        @CsvSource({"0,0", "0,1", "0,500", "2147483647,10"})
        @DisplayName("should preserve supported demographic list pagination boundaries")
        void shouldReturn200_whenListPaginationIsWithinBounds(int offset, int limit) {
            when(mockDemographicManager.getActiveDemographicCount(any())).thenReturn(0L);
            when(mockDemographicManager.getActiveDemographics(any(), eq(offset), eq(limit)))
                    .thenReturn(Collections.emptyList());
            try (Response response = request().path("/demographics")
                    .query("offset", offset).query("limit", limit).get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                verify(mockDemographicManager).getActiveDemographics(any(), eq(offset), eq(limit));
            }
        }

        @Test
        @DisplayName("should preserve zero defaults for omitted list pagination")
        void shouldUseZeroDefaults_whenListPaginationIsOmitted() {
            when(mockDemographicManager.getActiveDemographicCount(any())).thenReturn(0L);
            when(mockDemographicManager.getActiveDemographics(any(), eq(0), eq(0)))
                    .thenReturn(Collections.emptyList());
            try (Response response = request().path("/demographics").get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                verify(mockDemographicManager).getActiveDemographics(any(), eq(0), eq(0));
            }
        }

        @Test
        @DisplayName("should return 200 with demographics list")
        void shouldReturn200WithDemographics_whenDemographicsExist() {
            Demographic demo = createTestDemographic(1, "John", "Smith");
            when(mockDemographicManager.getActiveDemographicCount(any(LoggedInInfo.class)))
                .thenReturn(1L);
            when(mockDemographicManager.getActiveDemographics(any(LoggedInInfo.class), eq(0), eq(10)))
                .thenReturn(List.of(demo));

            Response response = request().path("/demographics")
                .query("offset", 0)
                .query("limit", 10)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/Item").isArray()).isTrue();
            assertThat(wireJson.at("/Item")).hasSize(1);
            assertThat(wireJson.at("/Item/0/demographicNo").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/Item/0/dateOfBirth").textValue()).isEqualTo("1980-06-15");
            assertThat(wireJson.at("/Item/0/firstName").textValue()).isEqualTo("John");
            assertThat(wireJson.at("/Item/0/lastName").textValue()).isEqualTo("Smith");
            assertThat(wireJson.at("/Item/0/demoContacts").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/demoContacts")).hasSize(0);
            assertThat(wireJson.at("/Item/0/demoContactPros").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/demoContactPros")).hasSize(0);
            assertThat(wireJson.at("/Item/0/extras").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/extras")).hasSize(0);
            assertThat(wireJson.at("/Item/0/doctors").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/doctors")).hasSize(0);
            assertThat(wireJson.at("/Item/0/nurses").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/nurses")).hasSize(0);
            assertThat(wireJson.at("/Item/0/midwives").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/midwives")).hasSize(0);
            assertThat(wireJson.at("/Item/0/referralDoctors").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/referralDoctors")).hasSize(0);
            assertThat(wireJson.at("/Item/0/waitingListNames").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/waitingListNames")).hasSize(0);
            assertThat(wireJson.at("/Item/0/patientStatusList").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/patientStatusList")).hasSize(0);
            assertThat(wireJson.at("/Item/0/rosterStatusList").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/rosterStatusList")).hasSize(0);
            assertThat(wireJson.at("/Item/0/allergies").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/allergies")).hasSize(0);
            assertThat(wireJson.at("/Item/0/measurements").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/measurements")).hasSize(0);
            assertThat(wireJson.at("/Item/0/consultationRequests").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/consultationRequests")).hasSize(0);
            assertThat(wireJson.at("/Item/0/consultationResponses").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/consultationResponses")).hasSize(0);
            assertThat(wireJson.at("/Item/0/encounterNotes").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/encounterNotes")).hasSize(0);
            assertThat(wireJson.at("/Item/0/documents").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/documents")).hasSize(0);
            assertThat(wireJson.at("/Item/0/medicationSummary").isArray()).isTrue();
            assertThat(wireJson.at("/Item/0/medicationSummary")).hasSize(0);
        }

        @Test
        @DisplayName("should return 200 with empty content when no demographics")
        void shouldReturn200WithEmptyContent_whenNoDemographicsExist() {
            when(mockDemographicManager.getActiveDemographicCount(any(LoggedInInfo.class)))
                .thenReturn(0L);
            when(mockDemographicManager.getActiveDemographics(any(LoggedInInfo.class), eq(0), eq(0)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/demographics").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/Item").isArray()).isTrue();
            assertThat(wireJson.at("/Item")).hasSize(0);
        }
    }

    /** Tests for GET /demographics/{dataId}. */
    @Nested
    @DisplayName("GET /demographics/{dataId}")
    class GetDemographicById {

        private void assertDeniedIncludes(String include, String object, boolean imported) {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(object), eq("r"), eq("42")))
                    .thenReturn(false);
            if (imported) {
                when(mockProjectionRecords.hasSourceFields(eq(42), any())).thenReturn(true);
            }
            try (Response response = request().path("/demographics/42").query("includes[]", include).get()) {
                assertThat(response.getStatus()).isEqualTo(403);
                assertThat(response.hasEntity()).isFalse();
            }
            verifyNoInteractions(mockProjectionRecords, mockDemographicManager, mockAllergyManager,
                    mockMeasurementManager, mockNoteManager, mockRxManager);
        }

        @Test
        void shouldDenyUnauthorizedNativeClinicalIncludes_beforePatientReads() {
            assertDeniedIncludes("measurements", "_measurement", false);
        }

        @Test
        void shouldDenyUnauthorizedImportedClinicalIncludes_withoutDisclosingImportState() {
            assertDeniedIncludes("medications", "_rx", true);
        }

        @Test
        @DisplayName("should return 200 with demographic data when valid ID provided")
        void shouldReturn200WithDemographic_whenValidIdProvided() {
            Demographic demo = createTestDemographic(42, "Jane", "Doe");
            when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), eq(42)))
                .thenReturn(demo);
            when(mockDemographicManager.getDemographicExts(any(LoggedInInfo.class), eq(42)))
                .thenReturn(Collections.emptyList());
            when(mockDemographicManager.getDemographicCust(any(LoggedInInfo.class), eq(42)))
                .thenReturn(null);
            when(mockWaitingListDao.search_wlstatus(42))
                .thenReturn(Collections.emptyList());
            when(mockWaitingListNameDao.findAll(any(), any()))
                .thenReturn(Collections.emptyList());
            when(mockSpecialistDao.findAll())
                .thenReturn(Collections.emptyList());
            when(mockSecUserRoleDao.getSecUserRolesByRoleName(any()))
                .thenReturn(Collections.emptyList());
            when(mockDemographicManager.getDemographicContacts(any(LoggedInInfo.class), eq(42)))
                .thenReturn(Collections.emptyList());
            when(mockDemographicManager.getPatientStatusList())
                .thenReturn(Collections.emptyList());
            when(mockDemographicManager.getRosterStatusList())
                .thenReturn(Collections.emptyList());

            Response response = request().path("/demographics/42").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/demographicNo").intValue()).isEqualTo(42);
            assertThat(wireJson.at("/dateOfBirth").textValue()).isEqualTo("1980-06-15");
            assertThat(wireJson.at("/firstName").textValue()).isEqualTo("Jane");
            assertThat(wireJson.at("/lastName").textValue()).isEqualTo("Doe");
            assertThat(wireJson.at("/demoContacts").isArray()).isTrue();
            assertThat(wireJson.at("/demoContacts")).hasSize(0);
            assertThat(wireJson.at("/demoContactPros").isArray()).isTrue();
            assertThat(wireJson.at("/demoContactPros")).hasSize(0);
            assertThat(wireJson.at("/extras").isArray()).isTrue();
            assertThat(wireJson.at("/extras")).hasSize(0);
            assertThat(wireJson.at("/doctors").isArray()).isTrue();
            assertThat(wireJson.at("/doctors")).hasSize(0);
            assertThat(wireJson.at("/nurses").isArray()).isTrue();
            assertThat(wireJson.at("/nurses")).hasSize(0);
            assertThat(wireJson.at("/midwives").isArray()).isTrue();
            assertThat(wireJson.at("/midwives")).hasSize(0);
            assertThat(wireJson.at("/referralDoctors").isArray()).isTrue();
            assertThat(wireJson.at("/referralDoctors")).hasSize(0);
            assertThat(wireJson.at("/waitingListNames").isArray()).isTrue();
            assertThat(wireJson.at("/waitingListNames")).hasSize(0);
            assertThat(wireJson.at("/patientStatusList").isArray()).isTrue();
            assertThat(wireJson.at("/patientStatusList")).hasSize(0);
            assertThat(wireJson.at("/rosterStatusList").isArray()).isTrue();
            assertThat(wireJson.at("/rosterStatusList")).hasSize(0);
            assertThat(wireJson.at("/allergies").isArray()).isTrue();
            assertThat(wireJson.at("/allergies")).hasSize(0);
            assertThat(wireJson.at("/measurements").isArray()).isTrue();
            assertThat(wireJson.at("/measurements")).hasSize(0);
            assertThat(wireJson.at("/consultationRequests").isArray()).isTrue();
            assertThat(wireJson.at("/consultationRequests")).hasSize(0);
            assertThat(wireJson.at("/consultationResponses").isArray()).isTrue();
            assertThat(wireJson.at("/consultationResponses")).hasSize(0);
            assertThat(wireJson.at("/encounterNotes").isArray()).isTrue();
            assertThat(wireJson.at("/encounterNotes")).hasSize(0);
            assertThat(wireJson.at("/documents").isArray()).isTrue();
            assertThat(wireJson.at("/documents")).hasSize(0);
            assertThat(wireJson.at("/medicationSummary").isArray()).isTrue();
            assertThat(wireJson.at("/medicationSummary")).hasSize(0);
        }

        @Test
        @DisplayName("should write midnight timestamps as epoch milliseconds in the record and its includes")
        void shouldWriteMidnightTimestampsAsEpochMillis_whenRecordIncludesAllergiesAndMeasurements() {
            // Issue #3877: on /ws/services these timestamps were "yyyy-MM-dd" strings whenever they
            // fell on local midnight, and a number otherwise, which broke Ocean's sync.
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(2026, Calendar.MARCH, 11);
            Date midnight = calendar.getTime();
            Demographic demo = createTestDemographic(42, "Jane", "Doe");
            demo.setLastUpdateDate(midnight);
            Allergy allergy = new Allergy();
            allergy.setId(7);
            allergy.setDemographicNo(42);
            allergy.setDescription("FAKE-allergen");
            allergy.setArchived(false);
            allergy.setLastUpdateDate(midnight);
            Measurement weight = new Measurement();
            weight.setDemographicId(42);
            weight.setType("WT");
            weight.setDataField("70");
            weight.setDateObserved(midnight);
            weight.setCreateDate(midnight);
            when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), eq(42))).thenReturn(demo);
            when(mockDemographicManager.getDemographicExts(any(LoggedInInfo.class), eq(42)))
                .thenReturn(Collections.emptyList());
            when(mockWaitingListDao.search_wlstatus(42)).thenReturn(Collections.emptyList());
            when(mockWaitingListNameDao.findAll(any(), any())).thenReturn(Collections.emptyList());
            when(mockSpecialistDao.findAll()).thenReturn(Collections.emptyList());
            when(mockSecUserRoleDao.getSecUserRolesByRoleName(any())).thenReturn(Collections.emptyList());
            when(mockDemographicManager.getDemographicContacts(any(LoggedInInfo.class), eq(42)))
                .thenReturn(Collections.emptyList());
            when(mockAllergyManager.getActiveAllergies(any(LoggedInInfo.class), eq(42))).thenReturn(List.of(allergy));
            when(mockMeasurementManager.getMeasurementByType(any(LoggedInInfo.class), eq(42), any()))
                .thenReturn(Collections.emptyList());
            when(mockMeasurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    any(LoggedInInfo.class), eq(42), any(Date.class)))
                .thenReturn(new ArrayList<>(List.of(weight)));

            Response response = request().path("/demographics/42")
                .query("includes[]", "allergies").query("includes[]", "measurements").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            for (String pointer : List.of("/lastUpdateDate", "/allergies/0/lastUpdateDate",
                    "/measurements/0/createDate", "/measurements/0/dateObserved")) {
                assertThat(wireJson.at(pointer).isIntegralNumber()).as("%s on the wire: %s", pointer,
                        wireJson.at(pointer)).isTrue();
                assertThat(wireJson.at(pointer).longValue()).as(pointer).isEqualTo(midnight.getTime());
            }
            // A calendar date beside them keeps its string shape.
            assertThat(wireJson.at("/dateOfBirth").textValue()).isEqualTo("1980-06-15");
        }
    }

    /** Tests for GET /demographics/quickSearch. */
    @Nested
    @DisplayName("GET /demographics/quickSearch")
    class QuickSearch {

        @Test
        @DisplayName("should return 200 with search results when query matches")
        void shouldReturn200WithResults_whenQueryMatches() {
            List<DemographicSearchResult> results = new ArrayList<>();
            DemographicSearchResult result = new DemographicSearchResult();
            result.setLastName("Smith");
            results.add(result);

            when(mockDemographicManager.searchPatientsCount(any(LoggedInInfo.class), any()))
                .thenReturn(1);
            when(mockDemographicManager.searchPatients(any(LoggedInInfo.class), any(), eq(0), eq(10)))
                .thenReturn(results);

            Response response = request().path("/demographics/quickSearch")
                .query("query", "Smith")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(1);
            assertThat(wireJson.at("/content/0/lastName").textValue()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("should return 200 with empty results when no match")
        void shouldReturn200WithEmptyResults_whenNoMatch() {
            when(mockDemographicManager.searchPatientsCount(any(LoggedInInfo.class), any()))
                .thenReturn(0);

            Response response = request().path("/demographics/quickSearch")
                .query("query", "NonExistentName")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(0);
        }
    }
    @Nested
    @DisplayName("POST /demographics/search validation")
    class SearchValidation {
        @Test
        void shouldRejectMalformedOptions_beforeSearching() {
            for (String body : List.of(
                    "null", "{\"type\":\"Unknown\",\"term\":\"1980\"}",
                    "{\"type\":{},\"term\":\"1980\"}", "{\"type\":\"DOB\",\"term\":{}}",
                    "{\"type\":\"DOB\",\"term\":\"1980\",\"active\":\"maybe\"}",
                    "{\"type\":\"DOB\",\"term\":\"1980\",\"params\":[]}",
                    "{\"type\":\"DOB\",\"term\":\"1980\",\"params\":{\"sorting[Unknown]\":\"asc\"}}",
                    "{\"type\":\"DOB\",\"term\":\"1980\",\"params\":{\"sorting[DOB]\":\"sideways\"}}")) {
                try (Response response = request().path("/demographics/search").post(body)) {
                    assertThat(response.getStatus()).as("malformed request options").isEqualTo(400);
                }
            }
            org.mockito.Mockito.verifyNoInteractions(mockDemographicManager);
        }

        @Test
        void shouldRejectInvalidPagination_beforeSearching() {
            for (String[] query : List.of(new String[]{"startIndex", "-1"}, new String[]{"startIndex", "abc"},
                    new String[]{"startIndex", "2147483648"}, new String[]{"itemsToReturn", "-1"},
                    new String[]{"itemsToReturn", "0"}, new String[]{"itemsToReturn", "501"}, new String[]{"itemsToReturn", "abc"})) {
                try (Response response = request().path("/demographics/search").query(query[0], query[1])
                        .post("{\"type\":\"DOB\",\"term\":\"1980\"}")) {
                    assertThat(response.getStatus()).as("invalid pagination").isEqualTo(400);
                }
            }
            org.mockito.Mockito.verifyNoInteractions(mockDemographicManager);
        }

        @Test
        void shouldPreserveScalarCompatibility_whenKeywordIsNumericAndBooleanIsText() {
            try (Response response = request().path("/demographics/search")
                    .post("{\"type\":\"DOB\",\"term\":1980,\"active\":\"TRUE\",\"params\":{\"sorting[DOB]\":\"desc\"}}")) {
                assertThat(response.getStatus()).isEqualTo(200);
            }
            org.mockito.Mockito.verify(mockDemographicManager).searchPatientsCount(any(),
                    org.mockito.ArgumentMatchers.argThat(req -> "1980".equals(req.getKeyword()) && req.isActive()
                            && req.getSortDir() == io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SORTDIR.desc));
        }

        @Test
        void shouldDefaultPaginationAndNameMode_whenOptionsAreAbsent() {
            when(mockDemographicManager.searchPatientsCount(any(), any())).thenReturn(1);
            when(mockDemographicManager.searchPatients(any(), any(), eq(0), eq(10))).thenReturn(List.of());
            try (Response response = request().path("/demographics/search").post("{\"term\":\"Synthetic\"}")) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(responseJson(response).at("/total").intValue()).isEqualTo(1);
            }
            org.mockito.Mockito.verify(mockDemographicManager).searchPatients(any(),
                    org.mockito.ArgumentMatchers.argThat(req -> req.getMode() == io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest.SEARCHMODE.Name),
                    eq(0), eq(10));
        }
    }

}
