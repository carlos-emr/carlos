/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.consultations.ConsultationRequestSearchFilter;
import io.github.carlos_emr.carlos.consultations.ConsultationResponseSearchFilter;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseSearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsultationManagerImpl} business logic.
 *
 * <p>Tests the core consultation management operations including request/response
 * CRUD, search functionality, security privilege enforcement, consultation services
 * lookup, outstanding consultation detection, and request archival.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Security privilege verification for read, write, and update operations</li>
 *   <li>New vs. existing entity save path routing (persist vs. merge)</li>
 *   <li>Consultation request search and result conversion</li>
 *   <li>Outstanding consultation time-based logic</li>
 *   <li>Property-based feature toggle (request/response enabled)</li>
 *   <li>Consultation request archival workflow</li>
 *   <li>Extension (extras) save-or-update batch logic</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see ConsultationManagerImpl
 * @see ConsultationManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Consultation Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("consultation")
public class ConsultationManagerUnitTest extends OpenOUnitTestBase {

    // --- DAO Mocks ---
    @Mock
    private ConsultRequestDao mockConsultRequestDao;

    @Mock
    private ConsultResponseDao mockConsultResponseDao;

    @Mock
    private ConsultationServiceDao mockServiceDao;

    @Mock
    private ProfessionalSpecialistDao mockProfessionalSpecialistDao;

    @Mock
    private ConsultDocsDao mockConsultDocsDao;

    @Mock
    private ConsultResponseDocDao mockResponseDocDao;

    @Mock
    private PropertyDao mockPropertyDao;

    @Mock
    private ConsultationRequestExtDao mockConsultationRequestExtDao;

    @Mock
    private ConsultationRequestArchiveDao mockConsultationRequestArchiveDao;

    @Mock
    private ConsultationRequestExtArchiveDao mockConsultationRequestExtArchiveDao;

    // --- Manager / Service Mocks ---
    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private DocumentManager mockDocumentManager;

    // --- Infrastructure Mocks ---
    @Mock
    private LoggedInInfo mockLoggedInInfo;

    /** The manager under test. */
    private ConsultationManagerImpl consultationManager;

    // Test data constants
    private static final Integer TEST_REQUEST_ID = 100;
    private static final Integer TEST_RESPONSE_ID = 200;
    private static final Integer TEST_DEMOGRAPHIC_NO = 1001;
    private static final Integer TEST_SERVICE_ID = 10;
    private static final Integer TEST_SPECIALIST_ID = 50;
    private static final String TEST_PROVIDER_NO = "999998";

    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(ConsultRequestDao.class, mockConsultRequestDao);
        registerMock(ConsultResponseDao.class, mockConsultResponseDao);
        registerMock(ConsultationServiceDao.class, mockServiceDao);
        registerMock(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
        registerMock(ConsultDocsDao.class, mockConsultDocsDao);
        registerMock(ConsultResponseDocDao.class, mockResponseDocDao);
        registerMock(PropertyDao.class, mockPropertyDao);
        registerMock(ConsultationRequestExtDao.class, mockConsultationRequestExtDao);
        registerMock(ConsultationRequestArchiveDao.class, mockConsultationRequestArchiveDao);
        registerMock(ConsultationRequestExtArchiveDao.class, mockConsultationRequestExtArchiveDao);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(DocumentManager.class, mockDocumentManager);

        // Security manager returns true for all privilege checks in unit tests
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // Create manager instance
        consultationManager = new ConsultationManagerImpl();

        // Inject dependencies using reflection
        injectDependency(consultationManager, "consultationRequestDao", mockConsultRequestDao);
        injectDependency(consultationManager, "consultationResponseDao", mockConsultResponseDao);
        injectDependency(consultationManager, "serviceDao", mockServiceDao);
        injectDependency(consultationManager, "professionalSpecialistDao", mockProfessionalSpecialistDao);
        injectDependency(consultationManager, "requestDocDao", mockConsultDocsDao);
        injectDependency(consultationManager, "responseDocDao", mockResponseDocDao);
        injectDependency(consultationManager, "propertyDao", mockPropertyDao);
        injectDependency(consultationManager, "consultationRequestExtDao", mockConsultationRequestExtDao);
        injectDependency(consultationManager, "consultationRequestArchiveDao", mockConsultationRequestArchiveDao);
        injectDependency(consultationManager, "consultationRequestExtArchiveDao", mockConsultationRequestExtArchiveDao);
        injectDependency(consultationManager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(consultationManager, "demographicManager", mockDemographicManager);
        injectDependency(consultationManager, "documentManager", mockDocumentManager);
        injectDependency(consultationManager, "consultDocsDao", mockConsultDocsDao);
    }

    // =========================================================================
    // Helper methods for building test data
    // =========================================================================

    /**
     * Creates a ConsultationRequest with an ID (simulates an existing record).
     */
    private ConsultationRequest createRequestWithId(Integer id) {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(TEST_DEMOGRAPHIC_NO);
        request.setProviderNo(TEST_PROVIDER_NO);
        request.setServiceId(TEST_SERVICE_ID);
        request.setReferralDate(new Date());
        request.setStatus("1");
        request.setUrgency("2");
        // Use reflection to set the ID since there is no public setter
        try {
            java.lang.reflect.Field idField = ConsultationRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set request ID", e);
        }
        return request;
    }

    /**
     * Creates a new ConsultationRequest with no ID (simulates a new record).
     */
    private ConsultationRequest createNewRequest() {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(TEST_DEMOGRAPHIC_NO);
        request.setProviderNo(TEST_PROVIDER_NO);
        request.setServiceId(TEST_SERVICE_ID);
        request.setReferralDate(new Date());
        request.setStatus("1");
        request.setUrgency("2");
        return request;
    }

    /**
     * Creates a ConsultationResponse with an ID (simulates an existing record).
     */
    private ConsultationResponse createResponseWithId(Integer id) {
        ConsultationResponse response = new ConsultationResponse();
        try {
            java.lang.reflect.Field idField = ConsultationResponse.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(response, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set response ID", e);
        }
        return response;
    }

    /**
     * Creates a new ConsultationResponse with no ID (simulates a new record).
     */
    private ConsultationResponse createNewResponse() {
        return new ConsultationResponse();
    }

    /**
     * Creates a ConsultDocs instance with optional ID.
     */
    private ConsultDocs createConsultDoc(Integer id, int requestId) {
        ConsultDocs doc = new ConsultDocs(requestId, 1, ConsultDocs.DOCTYPE_DOC, TEST_PROVIDER_NO);
        if (id != null) {
            doc.setId(id);
        }
        return doc;
    }

    /**
     * Creates a ConsultResponseDoc instance with optional ID.
     */
    private ConsultResponseDoc createConsultResponseDoc(Integer id, int responseId) {
        ConsultResponseDoc doc = new ConsultResponseDoc(responseId, 1, ConsultResponseDoc.DOCTYPE_DOC, TEST_PROVIDER_NO);
        if (id != null) {
            try {
                java.lang.reflect.Field idField = ConsultResponseDoc.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(doc, id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set response doc ID", e);
            }
        }
        return doc;
    }

    /**
     * Creates a ConsultationServices instance.
     */
    private ConsultationServices createService(Integer serviceId, String desc) {
        ConsultationServices service = new ConsultationServices(desc);
        try {
            java.lang.reflect.Field idField = ConsultationServices.class.getDeclaredField("serviceId");
            idField.setAccessible(true);
            idField.set(service, serviceId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set service ID", e);
        }
        service.setActive("1");
        return service;
    }

    /**
     * Builds a search result row (Object[]) as returned by ConsultRequestDao.search().
     */
    private Object[] buildRequestSearchRow(Integer requestId, Date referralDate) {
        ConsultationRequest request = createRequestWithId(requestId);
        request.setReferralDate(referralDate);

        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        ConsultationServices service = createService(TEST_SERVICE_ID, "Cardiology");
        Demographic demographic = new Demographic();
        Provider provider = new Provider();

        return new Object[]{request, specialist, service, demographic, provider};
    }

    /**
     * Builds a response search result row (Object[]) as returned by ConsultResponseDao.search().
     */
    private Object[] buildResponseSearchRow(Integer responseId) {
        ConsultationResponse response = createResponseWithId(responseId);

        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        Demographic demographic = new Demographic();
        Provider provider = new Provider();

        return new Object[]{response, specialist, demographic, provider};
    }

    /**
     * Creates a ConsultationRequestExt with key/value.
     */
    private ConsultationRequestExt createExt(Integer id, int requestId, String key, String value) {
        ConsultationRequestExt ext = new ConsultationRequestExt();
        try {
            java.lang.reflect.Field idField = ConsultationRequestExt.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ext, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ext ID", e);
        }
        ext.setRequestId(requestId);
        ext.setKey(key);
        ext.setValue(value);
        return ext;
    }

    // =========================================================================
    // Nested test classes
    // =========================================================================

    /**
     * Tests for getRequest() - retrieving a single consultation request by ID.
     */
    @Nested
    @DisplayName("Get Request")
    @Tag("read")
    class GetRequest {

        @Test
        @DisplayName("should return consultation request when valid ID provided")
        void shouldReturnConsultationRequest_whenValidIdProvided() {
            // Given
            ConsultationRequest expected = createRequestWithId(TEST_REQUEST_ID);
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(expected);

            // When
            ConsultationRequest result = consultationManager.getRequest(mockLoggedInInfo, TEST_REQUEST_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_REQUEST_ID);
            verify(mockConsultRequestDao).find(TEST_REQUEST_ID);
        }

        @Test
        @DisplayName("should check read privilege before retrieving request")
        void shouldCheckReadPrivilege_whenGettingRequest() {
            // Given
            ConsultationRequest request = createRequestWithId(TEST_REQUEST_ID);
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(request);

            // When
            consultationManager.getRequest(mockLoggedInInfo, TEST_REQUEST_ID);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), any());
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied")
        void shouldThrowRuntimeException_whenReadPrivilegeDenied() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.getRequest(mockLoggedInInfo, TEST_REQUEST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }

    /**
     * Tests for getResponse() - retrieving a single consultation response by ID.
     */
    @Nested
    @DisplayName("Get Response")
    @Tag("read")
    class GetResponse {

        @Test
        @DisplayName("should return consultation response when valid ID provided")
        void shouldReturnConsultationResponse_whenValidIdProvided() {
            // Given
            ConsultationResponse expected = createResponseWithId(TEST_RESPONSE_ID);
            when(mockConsultResponseDao.find(TEST_RESPONSE_ID)).thenReturn(expected);

            // When
            ConsultationResponse result = consultationManager.getResponse(mockLoggedInInfo, TEST_RESPONSE_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_RESPONSE_ID);
            verify(mockConsultResponseDao).find(TEST_RESPONSE_ID);
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied for response")
        void shouldThrowRuntimeException_whenReadPrivilegeDeniedForResponse() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.getResponse(mockLoggedInInfo, TEST_RESPONSE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }

    /**
     * Tests for saveConsultationRequest() - creating and updating consultation requests.
     */
    @Nested
    @DisplayName("Save Consultation Request")
    @Tag("create")
    @Tag("update")
    class SaveConsultationRequest {

        @Test
        @DisplayName("should persist new request when ID is null")
        void shouldPersistNewRequest_whenIdIsNull() {
            // Given
            ConsultationRequest newRequest = createNewRequest();

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, newRequest);

            // Then - persist is called for new requests, then merge for the specialist re-attach
            verify(mockConsultRequestDao).persist(newRequest);
            verify(mockConsultRequestDao).merge(newRequest);
        }

        @Test
        @DisplayName("should check write privilege when saving new request")
        void shouldCheckWritePrivilege_whenSavingNewRequest() {
            // Given
            ConsultationRequest newRequest = createNewRequest();

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, newRequest);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), any());
        }

        @Test
        @DisplayName("should merge existing request when ID is present")
        void shouldMergeExistingRequest_whenIdIsPresent() {
            // Given
            ConsultationRequest existingRequest = createRequestWithId(TEST_REQUEST_ID);

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, existingRequest);

            // Then - merge is called for existing requests (update path)
            verify(mockConsultRequestDao).merge(existingRequest);
            verify(mockConsultRequestDao, never()).persist(any());
        }

        @Test
        @DisplayName("should check update privilege when updating existing request")
        void shouldCheckUpdatePrivilege_whenUpdatingExistingRequest() {
            // Given
            ConsultationRequest existingRequest = createRequestWithId(TEST_REQUEST_ID);

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, existingRequest);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("u"), any());
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for new request")
        void shouldThrowRuntimeException_whenWritePrivilegeDeniedForNewRequest() {
            // Given
            ConsultationRequest newRequest = createNewRequest();
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("w"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.saveConsultationRequest(mockLoggedInInfo, newRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should clear specialist before persist and re-attach after for new request")
        void shouldClearSpecialistBeforePersist_whenNewRequest() {
            // Given
            ConsultationRequest newRequest = createNewRequest();
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            newRequest.setProfessionalSpecialist(specialist);

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, newRequest);

            // Then - verify persist and merge both called (specialist cleared then re-attached)
            verify(mockConsultRequestDao).persist(newRequest);
            verify(mockConsultRequestDao).merge(newRequest);
        }

        @Test
        @DisplayName("should save extras when request has non-empty extras list")
        void shouldSaveExtras_whenRequestHasExtras() {
            // Given
            ConsultationRequest existingRequest = createRequestWithId(TEST_REQUEST_ID);
            List<ConsultationRequestExt> extras = new ArrayList<>();
            ConsultationRequestExt ext = createExt(null, TEST_REQUEST_ID, "appointmentYear", "2026");
            extras.add(ext);
            existingRequest.setExtras(extras);

            // Stub the ext DAO calls
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(Collections.emptyList())
                .thenReturn(extras);

            // When
            consultationManager.saveConsultationRequest(mockLoggedInInfo, existingRequest);

            // Then - extras should be batch persisted since they are new
            verify(mockConsultationRequestExtDao).batchPersist(any());
        }
    }

    /**
     * Tests for saveConsultationResponse() - creating and updating consultation responses.
     */
    @Nested
    @DisplayName("Save Consultation Response")
    @Tag("create")
    @Tag("update")
    class SaveConsultationResponse {

        @Test
        @DisplayName("should persist new response when ID is null")
        void shouldPersistNewResponse_whenIdIsNull() {
            // Given
            ConsultationResponse newResponse = createNewResponse();

            // When
            consultationManager.saveConsultationResponse(mockLoggedInInfo, newResponse);

            // Then
            verify(mockConsultResponseDao).persist(newResponse);
        }

        @Test
        @DisplayName("should check write privilege when saving new response")
        void shouldCheckWritePrivilege_whenSavingNewResponse() {
            // Given
            ConsultationResponse newResponse = createNewResponse();

            // When
            consultationManager.saveConsultationResponse(mockLoggedInInfo, newResponse);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("w"), any());
        }

        @Test
        @DisplayName("should merge existing response when ID is present")
        void shouldMergeExistingResponse_whenIdIsPresent() {
            // Given
            ConsultationResponse existingResponse = createResponseWithId(TEST_RESPONSE_ID);

            // When
            consultationManager.saveConsultationResponse(mockLoggedInInfo, existingResponse);

            // Then
            verify(mockConsultResponseDao).merge(existingResponse);
            verify(mockConsultResponseDao, never()).persist(any());
        }

        @Test
        @DisplayName("should check update privilege when updating existing response")
        void shouldCheckUpdatePrivilege_whenUpdatingExistingResponse() {
            // Given
            ConsultationResponse existingResponse = createResponseWithId(TEST_RESPONSE_ID);

            // When
            consultationManager.saveConsultationResponse(mockLoggedInInfo, existingResponse);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("u"), any());
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for new response")
        void shouldThrowRuntimeException_whenWritePrivilegeDeniedForNewResponse() {
            // Given
            ConsultationResponse newResponse = createNewResponse();
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("w"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.saveConsultationResponse(mockLoggedInInfo, newResponse))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }

    /**
     * Tests for search() overloads - searching consultation requests and responses.
     */
    @Nested
    @DisplayName("Search Operations")
    @Tag("search")
    class SearchOperations {

        @Test
        @DisplayName("should return request search results when matching records exist")
        void shouldReturnRequestSearchResults_whenMatchingRecordsExist() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            List<Object[]> daoResults = new ArrayList<>();
            daoResults.add(buildRequestSearchRow(TEST_REQUEST_ID, new Date()));
            when(mockConsultRequestDao.search(filter)).thenReturn(daoResults);

            // When
            List<ConsultationRequestSearchResult> results = consultationManager.search(mockLoggedInInfo, filter);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(TEST_REQUEST_ID);
            assertThat(results.get(0).getServiceName()).isEqualTo("Cardiology");
        }

        @Test
        @DisplayName("should return empty list when no request search results found")
        void shouldReturnEmptyList_whenNoRequestSearchResults() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            when(mockConsultRequestDao.search(filter)).thenReturn(Collections.emptyList());

            // When
            List<ConsultationRequestSearchResult> results = consultationManager.search(mockLoggedInInfo, filter);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return response search results when matching records exist")
        void shouldReturnResponseSearchResults_whenMatchingRecordsExist() {
            // Given
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            List<Object[]> daoResults = new ArrayList<>();
            daoResults.add(buildResponseSearchRow(TEST_RESPONSE_ID));
            when(mockConsultResponseDao.search(filter)).thenReturn(daoResults);

            // When
            List<ConsultationResponseSearchResult> results = consultationManager.search(mockLoggedInInfo, filter);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(TEST_RESPONSE_ID);
        }

        @Test
        @DisplayName("should check read privilege before searching requests")
        void shouldCheckReadPrivilege_whenSearchingRequests() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            when(mockConsultRequestDao.search(filter)).thenReturn(Collections.emptyList());

            // When
            consultationManager.search(mockLoggedInInfo, filter);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("r"), any());
        }

        @Test
        @DisplayName("should throw RuntimeException when search privilege denied")
        void shouldThrowRuntimeException_whenSearchPrivilegeDenied() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.search(mockLoggedInInfo, filter))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }

    /**
     * Tests for getConsultationCount() overloads.
     */
    @Nested
    @DisplayName("Consultation Count")
    @Tag("read")
    @Tag("aggregate")
    class ConsultationCount {

        @Test
        @DisplayName("should return request count from DAO")
        void shouldReturnRequestCount_fromDao() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            when(mockConsultRequestDao.getConsultationCount2(filter)).thenReturn(42);

            // When
            int count = consultationManager.getConsultationCount(filter);

            // Then
            assertThat(count).isEqualTo(42);
        }

        @Test
        @DisplayName("should return response count from DAO")
        void shouldReturnResponseCount_fromDao() {
            // Given
            ConsultationResponseSearchFilter filter = new ConsultationResponseSearchFilter();
            when(mockConsultResponseDao.getConsultationCount(filter)).thenReturn(17);

            // When
            int count = consultationManager.getConsultationCount(filter);

            // Then
            assertThat(count).isEqualTo(17);
        }

        @Test
        @DisplayName("should return zero when no consultations exist")
        void shouldReturnZero_whenNoConsultationsExist() {
            // Given
            ConsultationRequestSearchFilter filter = new ConsultationRequestSearchFilter();
            when(mockConsultRequestDao.getConsultationCount2(filter)).thenReturn(0);

            // When
            int count = consultationManager.getConsultationCount(filter);

            // Then
            assertThat(count).isEqualTo(0);
        }
    }

    /**
     * Tests for hasOutstandingConsultations() - detecting consultations older than one month.
     */
    @Nested
    @DisplayName("Outstanding Consultations")
    @Tag("read")
    class OutstandingConsultations {

        @Test
        @DisplayName("should return true when consultation referral date is more than one month old")
        void shouldReturnTrue_whenReferralDateMoreThanOneMonthOld() {
            // Given - a referral from 2 months ago
            Calendar twoMonthsAgo = Calendar.getInstance();
            twoMonthsAgo.add(Calendar.MONTH, -2);
            Date oldReferralDate = twoMonthsAgo.getTime();

            List<Object[]> daoResults = new ArrayList<>();
            daoResults.add(buildRequestSearchRow(TEST_REQUEST_ID, oldReferralDate));
            when(mockConsultRequestDao.search(any(ConsultationRequestSearchFilter.class)))
                .thenReturn(daoResults);

            // When
            boolean outstanding = consultationManager.hasOutstandingConsultations(mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            // Then
            assertThat(outstanding).isTrue();
        }

        @Test
        @DisplayName("should return false when all consultation referral dates are recent")
        void shouldReturnFalse_whenAllReferralDatesAreRecent() {
            // Given - a referral from today (within 1 month)
            List<Object[]> daoResults = new ArrayList<>();
            daoResults.add(buildRequestSearchRow(TEST_REQUEST_ID, new Date()));
            when(mockConsultRequestDao.search(any(ConsultationRequestSearchFilter.class)))
                .thenReturn(daoResults);

            // When
            boolean outstanding = consultationManager.hasOutstandingConsultations(mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            // Then
            assertThat(outstanding).isFalse();
        }

        @Test
        @DisplayName("should return false when no consultations exist for demographic")
        void shouldReturnFalse_whenNoConsultationsExist() {
            // Given
            when(mockConsultRequestDao.search(any(ConsultationRequestSearchFilter.class)))
                .thenReturn(Collections.emptyList());

            // When
            boolean outstanding = consultationManager.hasOutstandingConsultations(mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            // Then
            assertThat(outstanding).isFalse();
        }
    }

    /**
     * Tests for getConsultationServices() - listing active consultation services.
     */
    @Nested
    @DisplayName("Consultation Services")
    @Tag("read")
    class ConsultationServicesTests {

        @Test
        @DisplayName("should return active services excluding referring doctor")
        void shouldReturnActiveServices_excludingReferringDoctor() {
            // Given
            List<ConsultationServices> services = new ArrayList<>();
            services.add(createService(1, "Cardiology"));
            services.add(createService(2, "Referring Doctor"));
            services.add(createService(3, "Neurology"));
            when(mockServiceDao.findActive()).thenReturn(services);

            // When
            List<ConsultationServices> result = consultationManager.getConsultationServices();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).noneMatch(s -> s.getServiceDesc().equals("Referring Doctor"));
        }

        @Test
        @DisplayName("should return all active services when no referring doctor entry exists")
        void shouldReturnAllActiveServices_whenNoReferringDoctorExists() {
            // Given
            List<ConsultationServices> services = new ArrayList<>();
            services.add(createService(1, "Cardiology"));
            services.add(createService(2, "Neurology"));
            when(mockServiceDao.findActive()).thenReturn(services);

            // When
            List<ConsultationServices> result = consultationManager.getConsultationServices();

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no active services exist")
        void shouldReturnEmptyList_whenNoActiveServicesExist() {
            // Given
            when(mockServiceDao.findActive()).thenReturn(new ArrayList<>());

            // When
            List<ConsultationServices> result = consultationManager.getConsultationServices();

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests for getReferringDoctorList() and getProfessionalSpecialist().
     */
    @Nested
    @DisplayName("Professional Specialist Lookup")
    @Tag("read")
    class ProfessionalSpecialistLookup {

        @Test
        @DisplayName("should return specialists from referring doctor service")
        void shouldReturnSpecialists_fromReferringDoctorService() {
            // Given
            ConsultationServices service = createService(1, "Referring Doctor");
            List<ProfessionalSpecialist> specialists = new ArrayList<>();
            specialists.add(new ProfessionalSpecialist());
            service.setSpecialists(specialists);
            when(mockServiceDao.findReferringDoctorService(true)).thenReturn(service);

            // When
            List<ProfessionalSpecialist> result = consultationManager.getReferringDoctorList();

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return null when no referring doctor service exists")
        void shouldReturnNull_whenNoReferringDoctorServiceExists() {
            // Given
            when(mockServiceDao.findReferringDoctorService(true)).thenReturn(null);

            // When
            List<ProfessionalSpecialist> result = consultationManager.getReferringDoctorList();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return professional specialist by ID")
        void shouldReturnProfessionalSpecialist_byId() {
            // Given
            ProfessionalSpecialist expected = new ProfessionalSpecialist();
            when(mockProfessionalSpecialistDao.find(TEST_SPECIALIST_ID)).thenReturn(expected);

            // When
            ProfessionalSpecialist result = consultationManager.getProfessionalSpecialist(TEST_SPECIALIST_ID);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return specialists by service name")
        void shouldReturnSpecialists_byServiceName() {
            // Given
            List<ProfessionalSpecialist> expected = List.of(new ProfessionalSpecialist());
            when(mockProfessionalSpecialistDao.findByService("Cardiology")).thenReturn(expected);

            // When
            List<ProfessionalSpecialist> result = consultationManager.findByService(mockLoggedInInfo, "Cardiology");

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return specialists by service ID")
        void shouldReturnSpecialists_byServiceId() {
            // Given
            List<ProfessionalSpecialist> expected = List.of(new ProfessionalSpecialist());
            when(mockProfessionalSpecialistDao.findByServiceId(TEST_SERVICE_ID)).thenReturn(expected);

            // When
            List<ProfessionalSpecialist> result = consultationManager.findByServiceId(mockLoggedInInfo, TEST_SERVICE_ID);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw RuntimeException when privilege denied for findByService")
        void shouldThrowRuntimeException_whenPrivilegeDeniedForFindByService() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> consultationManager.findByService(mockLoggedInInfo, "Cardiology"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }

    /**
     * Tests for consultation document attachment operations.
     */
    @Nested
    @DisplayName("Consultation Document Attachments")
    @Tag("read")
    @Tag("update")
    class ConsultationDocuments {

        @Test
        @DisplayName("should return request documents by request ID")
        void shouldReturnRequestDocuments_byRequestId() {
            // Given
            List<ConsultDocs> expected = List.of(createConsultDoc(1, TEST_REQUEST_ID));
            when(mockConsultDocsDao.findByRequestId(TEST_REQUEST_ID)).thenReturn(expected);

            // When
            List<ConsultDocs> result = consultationManager.getConsultRequestDocs(mockLoggedInInfo, TEST_REQUEST_ID);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return response documents by response ID")
        void shouldReturnResponseDocuments_byResponseId() {
            // Given
            List<ConsultResponseDoc> expected = List.of(createConsultResponseDoc(1, TEST_RESPONSE_ID));
            when(mockResponseDocDao.findByResponseId(TEST_RESPONSE_ID)).thenReturn(expected);

            // When
            List<ConsultResponseDoc> result = consultationManager.getConsultResponseDocs(mockLoggedInInfo, TEST_RESPONSE_ID);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should persist new request doc when ID is null")
        void shouldPersistNewRequestDoc_whenIdIsNull() {
            // Given
            ConsultDocs newDoc = createConsultDoc(null, TEST_REQUEST_ID);

            // When
            consultationManager.saveConsultRequestDoc(mockLoggedInInfo, newDoc);

            // Then
            verify(mockConsultDocsDao).persist(newDoc);
        }

        @Test
        @DisplayName("should merge existing request doc when ID is present")
        void shouldMergeExistingRequestDoc_whenIdIsPresent() {
            // Given
            ConsultDocs existingDoc = createConsultDoc(1, TEST_REQUEST_ID);

            // When
            consultationManager.saveConsultRequestDoc(mockLoggedInInfo, existingDoc);

            // Then
            verify(mockConsultDocsDao).merge(existingDoc);
        }

        @Test
        @DisplayName("should persist new response doc when ID is null")
        void shouldPersistNewResponseDoc_whenIdIsNull() {
            // Given
            ConsultResponseDoc newDoc = createConsultResponseDoc(null, TEST_RESPONSE_ID);

            // When
            consultationManager.saveConsultResponseDoc(mockLoggedInInfo, newDoc);

            // Then
            verify(mockResponseDocDao).persist(newDoc);
        }

        @Test
        @DisplayName("should merge existing response doc when ID is present")
        void shouldMergeExistingResponseDoc_whenIdIsPresent() {
            // Given
            ConsultResponseDoc existingDoc = createConsultResponseDoc(1, TEST_RESPONSE_ID);

            // When
            consultationManager.saveConsultResponseDoc(mockLoggedInInfo, existingDoc);

            // Then
            verify(mockResponseDocDao).merge(existingDoc);
        }

        @Test
        @DisplayName("should return attached documents by type")
        void shouldReturnAttachedDocuments_byType() {
            // Given
            List<ConsultDocs> expected = List.of(createConsultDoc(1, TEST_REQUEST_ID));
            when(mockConsultDocsDao.findByRequestIdDocType(TEST_REQUEST_ID, ConsultDocs.DOCTYPE_DOC))
                .thenReturn(expected);

            // When
            List<ConsultDocs> result = consultationManager.getAttachedDocumentsByType(
                mockLoggedInInfo, TEST_REQUEST_ID, ConsultDocs.DOCTYPE_DOC);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should check update privilege when saving request doc")
        void shouldCheckUpdatePrivilege_whenSavingRequestDoc() {
            // Given
            ConsultDocs newDoc = createConsultDoc(null, TEST_REQUEST_ID);

            // When
            consultationManager.saveConsultRequestDoc(mockLoggedInInfo, newDoc);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_con"), eq("u"), any());
        }
    }

    /**
     * Tests for enableConsultRequestResponse(), isConsultRequestEnabled(), isConsultResponseEnabled().
     */
    @Nested
    @DisplayName("Feature Toggle - Request/Response Enabled")
    @Tag("update")
    class FeatureToggle {

        @Test
        @DisplayName("should return true when consultation request is enabled")
        void shouldReturnTrue_whenConsultRequestEnabled() {
            // Given
            Property enabledProp = new Property("consultRequestEnabled");
            enabledProp.setValue("Y");
            when(mockPropertyDao.findByName("consultRequestEnabled")).thenReturn(List.of(enabledProp));

            // When
            boolean result = consultationManager.isConsultRequestEnabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when consultation request is not enabled")
        void shouldReturnFalse_whenConsultRequestNotEnabled() {
            // Given
            when(mockPropertyDao.findByName("consultRequestEnabled")).thenReturn(Collections.emptyList());

            // When
            boolean result = consultationManager.isConsultRequestEnabled();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when consultation response is enabled")
        void shouldReturnTrue_whenConsultResponseEnabled() {
            // Given
            Property enabledProp = new Property("consultResponseEnabled");
            enabledProp.setValue("Y");
            when(mockPropertyDao.findByName("consultResponseEnabled")).thenReturn(List.of(enabledProp));

            // When
            boolean result = consultationManager.isConsultResponseEnabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when consultation response property value is null")
        void shouldReturnFalse_whenConsultResponsePropertyValueIsNull() {
            // Given
            Property disabledProp = new Property("consultResponseEnabled");
            disabledProp.setValue(null);
            when(mockPropertyDao.findByName("consultResponseEnabled")).thenReturn(List.of(disabledProp));

            // When
            boolean result = consultationManager.isConsultResponseEnabled();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should enable request and response and activate referring doctor service")
        void shouldEnableRequestAndResponse_andActivateReferringDoctorService() {
            // Given
            when(mockPropertyDao.findByName("consultRequestEnabled")).thenReturn(Collections.emptyList());
            when(mockPropertyDao.findByName("consultResponseEnabled")).thenReturn(Collections.emptyList());

            ConsultationServices referringService = createService(1, "Referring Doctor");
            when(mockServiceDao.findReferringDoctorService(false)).thenReturn(referringService);

            // When
            consultationManager.enableConsultRequestResponse(true, true);

            // Then - both properties merged, referring doctor service activated
            verify(mockPropertyDao, Mockito.times(2)).merge(any());
            verify(mockServiceDao).merge(referringService);
            assertThat(referringService.getActive()).isEqualTo("1");
        }

        @Test
        @DisplayName("should disable response and deactivate referring doctor service")
        void shouldDisableResponse_andDeactivateReferringDoctorService() {
            // Given
            when(mockPropertyDao.findByName("consultRequestEnabled")).thenReturn(Collections.emptyList());
            when(mockPropertyDao.findByName("consultResponseEnabled")).thenReturn(Collections.emptyList());

            ConsultationServices referringService = createService(1, "Referring Doctor");
            when(mockServiceDao.findReferringDoctorService(false)).thenReturn(referringService);

            // When
            consultationManager.enableConsultRequestResponse(false, false);

            // Then - referring doctor service deactivated
            verify(mockServiceDao).merge(referringService);
            assertThat(referringService.getActive()).isEqualTo("02");
        }

        @Test
        @DisplayName("should create new referring doctor service when none exists")
        void shouldCreateNewReferringDoctorService_whenNoneExists() {
            // Given
            when(mockPropertyDao.findByName("consultRequestEnabled")).thenReturn(Collections.emptyList());
            when(mockPropertyDao.findByName("consultResponseEnabled")).thenReturn(Collections.emptyList());
            when(mockServiceDao.findReferringDoctorService(false)).thenReturn(null);

            // When
            consultationManager.enableConsultRequestResponse(true, true);

            // Then - a new service should be merged with "Referring Doctor" description
            verify(mockServiceDao).merge(any(ConsultationServices.class));
        }
    }

    /**
     * Tests for archiveConsultationRequest() - consultation request archival workflow.
     */
    @Nested
    @DisplayName("Archive Consultation Request")
    @Tag("update")
    class ArchiveConsultationRequest {

        @Test
        @DisplayName("should persist archive copy when request exists")
        void shouldPersistArchiveCopy_whenRequestExists() {
            // Given
            ConsultationRequest request = createRequestWithId(TEST_REQUEST_ID);
            request.setReasonForReferral("Heart palpitations");
            request.setClinicalInfo("Patient reports irregular heartbeat");
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(request);
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(Collections.emptyList());

            // When
            consultationManager.archiveConsultationRequest(TEST_REQUEST_ID);

            // Then
            verify(mockConsultationRequestArchiveDao).persist(any());
        }

        @Test
        @DisplayName("should archive extension records along with request")
        void shouldArchiveExtensionRecords_alongWithRequest() {
            // Given
            ConsultationRequest request = createRequestWithId(TEST_REQUEST_ID);
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(request);

            List<ConsultationRequestExt> exts = new ArrayList<>();
            exts.add(createExt(1, TEST_REQUEST_ID, "appointmentYear", "2026"));
            exts.add(createExt(2, TEST_REQUEST_ID, "appointmentMonth", "02"));
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(exts);

            // When
            consultationManager.archiveConsultationRequest(TEST_REQUEST_ID);

            // Then - archive DAO should persist one archive + two ext archives
            verify(mockConsultationRequestArchiveDao).persist(any());
            verify(mockConsultationRequestExtArchiveDao, Mockito.times(2)).persist(any());
        }

        @Test
        @DisplayName("should do nothing when request does not exist")
        void shouldDoNothing_whenRequestDoesNotExist() {
            // Given
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(null);

            // When
            consultationManager.archiveConsultationRequest(TEST_REQUEST_ID);

            // Then
            verify(mockConsultationRequestArchiveDao, never()).persist(any());
        }

        @Test
        @DisplayName("should merge archive with specialist when request has specialist")
        void shouldMergeArchiveWithSpecialist_whenRequestHasSpecialist() {
            // Given
            ConsultationRequest request = createRequestWithId(TEST_REQUEST_ID);
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            try {
                java.lang.reflect.Field idField = ProfessionalSpecialist.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(specialist, TEST_SPECIALIST_ID);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set specialist ID", e);
            }
            request.setProfessionalSpecialist(specialist);
            when(mockConsultRequestDao.find(TEST_REQUEST_ID)).thenReturn(request);
            when(mockProfessionalSpecialistDao.find(TEST_SPECIALIST_ID)).thenReturn(specialist);
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(Collections.emptyList());

            // When
            consultationManager.archiveConsultationRequest(TEST_REQUEST_ID);

            // Then - persist the archive, then merge with specialist
            verify(mockConsultationRequestArchiveDao).persist(any());
            verify(mockConsultationRequestArchiveDao).merge(any());
        }
    }

    /**
     * Tests for saveOrUpdateExts() - batch save/update of consultation request extras.
     */
    @Nested
    @DisplayName("Save or Update Extensions")
    @Tag("create")
    @Tag("update")
    class SaveOrUpdateExts {

        @Test
        @DisplayName("should batch persist new extras when no existing extras found")
        void shouldBatchPersistNewExtras_whenNoExistingExtras() {
            // Given
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(Collections.emptyList());

            List<ConsultationRequestExt> newExtras = new ArrayList<>();
            newExtras.add(createExt(null, 0, "appointmentYear", "2026"));
            newExtras.add(createExt(null, 0, "appointmentMonth", "02"));

            // When
            consultationManager.saveOrUpdateExts(TEST_REQUEST_ID, newExtras);

            // Then
            verify(mockConsultationRequestExtDao).batchPersist(any());
        }

        @Test
        @DisplayName("should merge existing extra when key matches and value differs")
        void shouldMergeExistingExtra_whenKeyMatchesAndValueDiffers() {
            // Given
            ConsultationRequestExt existing = createExt(1, TEST_REQUEST_ID, "appointmentYear", "2025");
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(List.of(existing));

            List<ConsultationRequestExt> updatedExtras = new ArrayList<>();
            updatedExtras.add(createExt(null, 0, "appointmentYear", "2026"));

            // When
            consultationManager.saveOrUpdateExts(TEST_REQUEST_ID, updatedExtras);

            // Then - existing extra should be merged with updated value
            verify(mockConsultationRequestExtDao).merge(existing);
            assertThat(existing.getValue()).isEqualTo("2026");
        }

        @Test
        @DisplayName("should not merge existing extra when key matches and value is same")
        void shouldNotMergeExistingExtra_whenKeyMatchesAndValueIsSame() {
            // Given
            ConsultationRequestExt existing = createExt(1, TEST_REQUEST_ID, "appointmentYear", "2026");
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(List.of(existing));

            List<ConsultationRequestExt> sameExtras = new ArrayList<>();
            sameExtras.add(createExt(null, 0, "appointmentYear", "2026"));

            // When
            consultationManager.saveOrUpdateExts(TEST_REQUEST_ID, sameExtras);

            // Then - no merge needed since value unchanged
            verify(mockConsultationRequestExtDao, never()).merge(any());
            verify(mockConsultationRequestExtDao, never()).batchPersist(any());
        }

        @Test
        @DisplayName("should handle mix of new and existing extras")
        void shouldHandleMixOfNewAndExistingExtras() {
            // Given
            ConsultationRequestExt existing = createExt(1, TEST_REQUEST_ID, "appointmentYear", "2025");
            when(mockConsultationRequestExtDao.getConsultationRequestExts(TEST_REQUEST_ID))
                .thenReturn(List.of(existing));

            List<ConsultationRequestExt> mixedExtras = new ArrayList<>();
            mixedExtras.add(createExt(null, 0, "appointmentYear", "2026"));
            mixedExtras.add(createExt(null, 0, "newKey", "newValue"));

            // When
            consultationManager.saveOrUpdateExts(TEST_REQUEST_ID, mixedExtras);

            // Then - existing updated via merge, new persisted via batch
            verify(mockConsultationRequestExtDao).merge(existing);
            verify(mockConsultationRequestExtDao).batchPersist(any());
        }
    }

    /**
     * Tests for getExtsAsMap() and getExtValuesAsMap() - utility conversion methods.
     */
    @Nested
    @DisplayName("Extension Map Conversion")
    @Tag("read")
    class ExtensionMapConversion {

        @Test
        @DisplayName("should convert extension list to map keyed by extension key")
        void shouldConvertExtensionList_toMapKeyedByExtKey() {
            // Given
            List<ConsultationRequestExt> extras = new ArrayList<>();
            extras.add(createExt(1, TEST_REQUEST_ID, "appointmentYear", "2026"));
            extras.add(createExt(2, TEST_REQUEST_ID, "appointmentMonth", "02"));

            // When
            Map<String, ConsultationRequestExt> result = consultationManager.getExtsAsMap(extras);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsKey("appointmentYear");
            assertThat(result).containsKey("appointmentMonth");
            assertThat(result.get("appointmentYear").getValue()).isEqualTo("2026");
        }

        @Test
        @DisplayName("should return empty map when extension list is empty")
        void shouldReturnEmptyMap_whenExtensionListIsEmpty() {
            // When
            Map<String, ConsultationRequestExt> result = consultationManager.getExtsAsMap(Collections.emptyList());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should convert extension list to values map")
        void shouldConvertExtensionList_toValuesMap() {
            // Given
            List<ConsultationRequestExt> extras = new ArrayList<>();
            extras.add(createExt(1, TEST_REQUEST_ID, "appointmentYear", "2026"));
            extras.add(createExt(2, TEST_REQUEST_ID, "appointmentMonth", "02"));

            // When
            Map<String, String> result = consultationManager.getExtValuesAsMap(extras);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsEntry("appointmentYear", "2026");
            assertThat(result).containsEntry("appointmentMonth", "02");
        }

        @Test
        @DisplayName("should return empty values map when extension list is empty")
        void shouldReturnEmptyValuesMap_whenExtensionListIsEmpty() {
            // When
            Map<String, String> result = consultationManager.getExtValuesAsMap(Collections.emptyList());

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests verifying that security privilege checks are enforced across all protected methods.
     */
    @Nested
    @DisplayName("Security Privilege Enforcement")
    @Tag("security")
    class SecurityPrivilegeEnforcement {

        @BeforeEach
        void denyAllPrivileges() {
            // Override the lenient default to deny all privileges
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
        }

        @Test
        @DisplayName("should deny getRequest when read privilege missing")
        void shouldDenyGetRequest_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.getRequest(mockLoggedInInfo, TEST_REQUEST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny getResponse when read privilege missing")
        void shouldDenyGetResponse_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.getResponse(mockLoggedInInfo, TEST_RESPONSE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny search for requests when read privilege missing")
        void shouldDenySearchRequests_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.search(mockLoggedInInfo, new ConsultationRequestSearchFilter()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny search for responses when read privilege missing")
        void shouldDenySearchResponses_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.search(mockLoggedInInfo, new ConsultationResponseSearchFilter()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny saveConsultationRequest for new request when write privilege missing")
        void shouldDenySaveNewRequest_whenWritePrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.saveConsultationRequest(mockLoggedInInfo, createNewRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny saveConsultationRequest for existing request when update privilege missing")
        void shouldDenySaveExistingRequest_whenUpdatePrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.saveConsultationRequest(mockLoggedInInfo, createRequestWithId(TEST_REQUEST_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny saveConsultationResponse for new response when write privilege missing")
        void shouldDenySaveNewResponse_whenWritePrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.saveConsultationResponse(mockLoggedInInfo, createNewResponse()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny getConsultRequestDocs when read privilege missing")
        void shouldDenyGetConsultRequestDocs_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.getConsultRequestDocs(mockLoggedInInfo, TEST_REQUEST_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny getConsultResponseDocs when read privilege missing")
        void shouldDenyGetConsultResponseDocs_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.getConsultResponseDocs(mockLoggedInInfo, TEST_RESPONSE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny saveConsultRequestDoc when update privilege missing")
        void shouldDenySaveConsultRequestDoc_whenUpdatePrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.saveConsultRequestDoc(mockLoggedInInfo, createConsultDoc(null, TEST_REQUEST_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny saveConsultResponseDoc when update privilege missing")
        void shouldDenySaveConsultResponseDoc_whenUpdatePrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.saveConsultResponseDoc(mockLoggedInInfo, createConsultResponseDoc(null, TEST_RESPONSE_ID)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny findByService when read privilege missing")
        void shouldDenyFindByService_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.findByService(mockLoggedInInfo, "Cardiology"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }

        @Test
        @DisplayName("should deny findByServiceId when read privilege missing")
        void shouldDenyFindByServiceId_whenReadPrivilegeMissing() {
            assertThatThrownBy(() -> consultationManager.findByServiceId(mockLoggedInInfo, TEST_SERVICE_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access Denied");
        }
    }
}
