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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestSearchResult;

/**
 * HTTP-level endpoint tests for {@link ConsultationWebService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for the consultation (referral) REST API. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ConsultationWebService REST endpoint tests")
class ConsultationWebServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ConsultationManager mockConsultationManager;
    @Mock
    private CaseManagementManager mockCaseManagementManager;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private DocumentManager mockDocumentManager;
    @Mock
    private ProviderDao mockProviderDao;
    @Mock
    private FaxConfigDao mockFaxConfigDao;
    @Mock
    private ClinicDAO mockClinicDAO;
    @Mock
    private UserPropertyDAO mockUserPropertyDAO;
    @Mock
    private ConsultationServiceDao mockConsultationServiceDao;

    @Override
    protected Object getServiceBean() {
        ConsultationWebService service = new ConsultationWebService();
        injectDependency(service, "consultationManager", mockConsultationManager);
        injectDependency(service, "caseManagementManager", mockCaseManagementManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "documentManager", mockDocumentManager);
        injectDependency(service, "providerDao", mockProviderDao);
        injectDependency(service, "faxConfigDao", mockFaxConfigDao);
        injectDependency(service, "clinicDAO", mockClinicDAO);
        injectDependency(service, "userPropertyDAO", mockUserPropertyDAO);
        injectDependency(service, "consultationServiceDao", mockConsultationServiceDao);
        return service;
    }

    /** Tests for POST /consults/searchRequests endpoint. */
    @Nested
    @DisplayName("POST /consults/searchRequests")
    class SearchRequests {

        @Test
        @DisplayName("should return 200 with results when consultations found")
        void shouldReturn200WithResults_whenConsultationsFound() {
            List<ConsultationRequestSearchResult> results = new ArrayList<>();
            ConsultationRequestSearchResult result = new ConsultationRequestSearchResult();
            results.add(result);

            when(mockConsultationManager.getConsultationCount(any(ConsultationRequestSearchFilter.class)))
                .thenReturn(1);
            when(mockConsultationManager.search(any(LoggedInInfo.class), any(ConsultationRequestSearchFilter.class)))
                .thenReturn(results);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            json.put("status", "1");
            json.put("team", "");
            json.put("demographicNo", "");
            json.put("numToReturn", 10);
            json.put("startIndex", 0);

            Response response = request().path("/consults/searchRequests").post(json);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty results when no consultations match")
        void shouldReturn200WithEmptyResults_whenNoConsultationsMatch() {
            when(mockConsultationManager.getConsultationCount(any(ConsultationRequestSearchFilter.class)))
                .thenReturn(0);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode json = mapper.createObjectNode();
            json.put("status", "1");
            json.put("numToReturn", 10);
            json.put("startIndex", 0);

            Response response = request().path("/consults/searchRequests").post(json);

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for GET /consults/getRequest endpoint. */
    @Nested
    @DisplayName("GET /consults/getRequest")
    class GetRequest {

        @Test
        @Disabled("TODO: Requires mock setup for EDocUtil static initializer calling SpringUtils.getBean() on CXF thread")
        @DisplayName("should return 200 with consultation request data when valid ID provided")
        void shouldReturn200WithRequest_whenValidIdProvided() {
            ConsultationRequest consultRequest = new ConsultationRequest();
            consultRequest.setDemographicId(1);

            when(mockConsultationManager.getRequest(any(LoggedInInfo.class), any()))
                .thenReturn(consultRequest);
            when(mockConsultationManager.getConsultationServices())
                .thenReturn(Collections.emptyList());
            when(mockProviderDao.getActiveTeams())
                .thenReturn(Collections.emptyList());
            when(mockLoggedInInfo.getLoggedInProviderNo())
                .thenReturn("999001");

            Response response = request().path("/consults/getRequest")
                .query("requestId", 10)
                .query("demographicId", 1)
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
