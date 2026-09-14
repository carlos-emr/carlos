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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.NoteManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;

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
        DemographicService service = new DemographicService();
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

    @BeforeEach
    void setUpSecurityDefaults() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq(42)))
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

    /** Tests for GET /demographics (list all). */
    @Nested
    @DisplayName("GET /demographics")
    class GetAllDemographics {

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
}
