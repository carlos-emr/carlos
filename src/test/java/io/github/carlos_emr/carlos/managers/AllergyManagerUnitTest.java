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

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.log.LogAction;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AllergyManagerImpl} business logic.
 *
 * <p>This test class validates the allergy management service layer, covering
 * all five interface methods: single allergy retrieval, active allergy listing,
 * date-filtered queries, consent-gated demographic queries, and
 * program/provider/demographic/date composite queries.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>DAO delegation and result pass-through verification</li>
 *   <li>Patient consent gating (via PatientConsentManager)</li>
 *   <li>Consent-based result filtering for provider-specific access</li>
 *   <li>Audit logging verification (LogAction calls)</li>
 *   <li>Null and empty result edge cases</li>
 * </ul>
 *
 * <p><b>Note:</b> AllergyManagerImpl does not perform SecurityInfoManager
 * privilege checks. Security is enforced at the action/controller layer
 * for this manager. Therefore, no security failure tests are included.</p>
 *
 * @since 2026-02-09
 * @see AllergyManagerImpl
 * @see AllergyManager
 * @see AllergyUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Allergy Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("allergy")
public class AllergyManagerUnitTest extends AllergyUnitTestBase {

    @Mock
    private AllergyDao mockAllergyDao;

    @Mock
    private PartialDateDao mockPartialDateDao;

    @Mock
    private PatientConsentManager mockPatientConsentManager;

    private AllergyManagerImpl allergyManager;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mock DAOs with SpringUtils, creates a fresh {@link AllergyManagerImpl}
     * instance, and injects mock dependencies (AllergyDao, PatientConsentManager) via
     * reflection to isolate the manager from Spring context.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(AllergyDao.class, mockAllergyDao);
        registerMock(PatientConsentManager.class, mockPatientConsentManager);

        // Create manager instance and inject dependencies via reflection
        allergyManager = new AllergyManagerImpl();
        injectDependency(allergyManager, "allergyDao", mockAllergyDao);
        injectDependency(allergyManager, "partialDateDao", mockPartialDateDao);
        injectDependency(allergyManager, "patientConsentManager", mockPatientConsentManager);
    }

    /**
     * Tests for {@link AllergyManager#getAllergy(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     *
     * <p>Verifies single allergy retrieval by ID, including successful lookup,
     * not-found scenarios, and conditional audit logging.</p>
     */
    @Nested
    @DisplayName("getAllergy")
    @Tag("read")
    class GetAllergy {

        @Test
        @DisplayName("should return allergy when valid ID is provided")
        void shouldReturnAllergy_whenValidIdProvided() {
            // Given
            Allergy expected = createTestAllergyWithId(TEST_ALLERGY_ID);
            when(mockAllergyDao.find(TEST_ALLERGY_ID)).thenReturn(expected);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, TEST_ALLERGY_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(expected);
            verify(mockAllergyDao).find(TEST_ALLERGY_ID);
        }

        @Test
        @DisplayName("should return null when allergy is not found")
        void shouldReturnNull_whenAllergyNotFound() {
            // Given
            Integer nonExistentId = 99999;
            when(mockAllergyDao.find(nonExistentId)).thenReturn(null);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, nonExistentId);

            // Then
            assertThat(result).isNull();
            verify(mockAllergyDao).find(nonExistentId);
        }

        @Test
        @DisplayName("should delegate to DAO with the exact ID provided")
        void shouldDelegateToDao_whenIdProvided() {
            // Given
            Integer specificId = 42;
            when(mockAllergyDao.find(specificId)).thenReturn(null);

            // When
            allergyManager.getAllergy(mockLoggedInInfo, specificId);

            // Then - cast to Object to match find(Object) overload since DAO has both find(Object) and find(int)
            verify(mockAllergyDao).find((Object) 42);
        }

        @Test
        @DisplayName("should handle null ID gracefully")
        void shouldHandleNullId_whenNullProvided() {
            // Given
            when(mockAllergyDao.find(isNull())).thenReturn(null);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, null);

            // Then
            assertThat(result).isNull();
            verify(mockAllergyDao).find(isNull());
        }

        @Test
        @DisplayName("should return allergy with correct clinical data intact")
        void shouldReturnAllergyWithClinicalData_whenFound() {
            // Given
            Allergy expected = createTestAllergyWithId(TEST_ALLERGY_ID);
            expected.setDescription("Amoxicillin");
            expected.setSeverityOfReaction("3"); // severe
            when(mockAllergyDao.find(TEST_ALLERGY_ID)).thenReturn(expected);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, TEST_ALLERGY_ID);

            // Then
            assertThat(result.getDescription()).isEqualTo("Amoxicillin");
            assertThat(result.getSeverityOfReaction()).isEqualTo("3");
        }
    }

    /**
     * Tests for {@link AllergyManager#getActiveAllergies(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     *
     * <p>Verifies retrieval of active (non-archived) allergies for a demographic,
     * including result ordering, empty results, and conditional logging.</p>
     */
    @Nested
    @DisplayName("getActiveAllergies")
    @Tag("read")
    class GetActiveAllergies {

        @Test
        @DisplayName("should return active allergies for demographic")
        void shouldReturnActiveAllergies_whenDemographicHasAllergies() {
            // Given
            List<Allergy> expected = List.of(
                    createTestAllergyWithId(1),
                    createTestAllergyWithId(2)
            );
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO)).thenReturn(expected);

            // When
            List<Allergy> result = allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isSameAs(expected);
            verify(mockAllergyDao).findActiveAllergiesOrderByDescription(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when no active allergies exist")
        void shouldReturnEmptyList_whenNoActiveAllergies() {
            // Given
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO))
                    .thenReturn(Collections.emptyList());

            // When
            List<Allergy> result = allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
            verify(mockAllergyDao).findActiveAllergiesOrderByDescription(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return null when DAO returns null")
        void shouldReturnNull_whenDaoReturnsNull() {
            // Given
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO)).thenReturn(null);

            // When
            List<Allergy> result = allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should delegate exact demographic number to DAO")
        void shouldDelegateExactDemographicNo_whenCalled() {
            // Given
            Integer specificDemo = 77777;
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(specificDemo))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getActiveAllergies(mockLoggedInInfo, specificDemo);

            // Then
            verify(mockAllergyDao).findActiveAllergiesOrderByDescription(eq(77777));
        }

        @Test
        @DisplayName("should return single allergy when only one active allergy exists")
        void shouldReturnSingleAllergy_whenOnlyOneActiveAllergyExists() {
            // Given
            List<Allergy> expected = List.of(createTestAllergyWithId(1));
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO)).thenReturn(expected);

            // When
            List<Allergy> result = allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    /**
     * Tests for {@link AllergyManager#getUpdatedAfterDate(io.github.carlos_emr.carlos.utility.LoggedInInfo, Date, int)}.
     *
     * <p>Verifies date-filtered allergy retrieval with provider consent filtering.
     * This method always applies consent filtering and always logs.</p>
     */
    @Nested
    @DisplayName("getUpdatedAfterDate")
    @Tag("read")
    @Tag("filter")
    class GetUpdatedAfterDate {

        @Test
        @DisplayName("should return allergies updated after the specified date")
        void shouldReturnAllergies_whenUpdatedAfterDate() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 10;
            List<Allergy> expected = List.of(createTestAllergyWithId(1), createTestAllergyWithId(2));
            when(mockAllergyDao.findByUpdateDate(cutoffDate, itemsToReturn)).thenReturn(expected);

            // When
            List<Allergy> result = allergyManager.getUpdatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isSameAs(expected);
            verify(mockAllergyDao).findByUpdateDate(cutoffDate, itemsToReturn);
        }

        @Test
        @DisplayName("should apply provider-specific consent filtering")
        void shouldApplyConsentFiltering_whenResultsReturned() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 5;
            List<Allergy> daoResults = new ArrayList<>(List.of(createTestAllergyWithId(1)));
            when(mockAllergyDao.findByUpdateDate(cutoffDate, itemsToReturn)).thenReturn(daoResults);

            // When
            allergyManager.getUpdatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            verify(mockPatientConsentManager).filterProviderSpecificConsent(mockLoggedInInfo, daoResults);
        }

        @Test
        @DisplayName("should apply consent filtering even when DAO returns empty list")
        void shouldApplyConsentFiltering_whenDaoReturnsEmptyList() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 10;
            List<Allergy> emptyResults = new ArrayList<>();
            when(mockAllergyDao.findByUpdateDate(cutoffDate, itemsToReturn)).thenReturn(emptyResults);

            // When
            allergyManager.getUpdatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            verify(mockPatientConsentManager).filterProviderSpecificConsent(mockLoggedInInfo, emptyResults);
        }

        @Test
        @DisplayName("should pass exact parameters to DAO")
        void shouldPassExactParameters_whenCalled() {
            // Given
            Date specificDate = new Date(1000000L);
            int specificCount = 25;
            when(mockAllergyDao.findByUpdateDate(specificDate, specificCount))
                    .thenReturn(new ArrayList<>());

            // When
            allergyManager.getUpdatedAfterDate(mockLoggedInInfo, specificDate, specificCount);

            // Then
            verify(mockAllergyDao).findByUpdateDate(eq(specificDate), eq(25));
        }

        @Test
        @DisplayName("should return filtered results after consent processing")
        void shouldReturnFilteredResults_whenConsentFilteringRemovesEntries() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 10;
            List<Allergy> mutableList = new ArrayList<>();
            mutableList.add(createTestAllergyWithId(1));
            mutableList.add(createTestAllergyWithId(2));
            when(mockAllergyDao.findByUpdateDate(cutoffDate, itemsToReturn)).thenReturn(mutableList);

            // Simulate consent filter removing one entry
            doAnswer(invocation -> {
                List<?> list = invocation.getArgument(1);
                list.remove(0);
                return null;
            }).when(mockPatientConsentManager).filterProviderSpecificConsent(eq(mockLoggedInInfo), anyList());

            // When
            List<Allergy> result = allergyManager.getUpdatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    /**
     * Tests for {@link AllergyManager#getByDemographicIdUpdatedAfterDate(
     * io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, Date)}.
     *
     * <p>Verifies consent-gated allergy retrieval by demographic and date.
     * The method only queries the DAO if the patient has consented; otherwise
     * it returns an empty list without contacting the database.</p>
     */
    @Nested
    @DisplayName("getByDemographicIdUpdatedAfterDate")
    @Tag("read")
    @Tag("filter")
    class GetByDemographicIdUpdatedAfterDate {

        private ConsentType mockConsentType;

        @BeforeEach
        void setUpConsent() {
            mockConsentType = mock(ConsentType.class);
            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo))
                    .thenReturn(mockConsentType);
        }

        @Test
        @DisplayName("should return allergies when patient has consented")
        void shouldReturnAllergies_whenPatientHasConsented() {
            // Given
            Date afterDate = new Date();
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, mockConsentType)).thenReturn(true);
            List<Allergy> expected = List.of(createTestAllergyWithId(1));
            when(mockAllergyDao.findByDemographicIdUpdatedAfterDate(TEST_DEMO_NO, afterDate)).thenReturn(expected);

            // When
            List<Allergy> result = allergyManager.getByDemographicIdUpdatedAfterDate(
                    mockLoggedInInfo, TEST_DEMO_NO, afterDate);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result).isSameAs(expected);
            verify(mockAllergyDao).findByDemographicIdUpdatedAfterDate(TEST_DEMO_NO, afterDate);
        }

        @Test
        @DisplayName("should return empty list when patient has not consented")
        void shouldReturnEmptyList_whenPatientHasNotConsented() {
            // Given
            Date afterDate = new Date();
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, mockConsentType)).thenReturn(false);

            // When
            List<Allergy> result = allergyManager.getByDemographicIdUpdatedAfterDate(
                    mockLoggedInInfo, TEST_DEMO_NO, afterDate);

            // Then
            assertThat(result).isEmpty();
            verify(mockAllergyDao, never()).findByDemographicIdUpdatedAfterDate(anyInt(), any(Date.class));
        }

        @Test
        @DisplayName("should not query DAO when consent check fails")
        void shouldNotQueryDao_whenConsentCheckFails() {
            // Given
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, mockConsentType)).thenReturn(false);

            // When
            allergyManager.getByDemographicIdUpdatedAfterDate(mockLoggedInInfo, TEST_DEMO_NO, new Date());

            // Then
            verifyNoInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("should check provider-specific consent type")
        void shouldCheckProviderSpecificConsentType_whenCalled() {
            // Given
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, mockConsentType)).thenReturn(false);

            // When
            allergyManager.getByDemographicIdUpdatedAfterDate(mockLoggedInInfo, TEST_DEMO_NO, new Date());

            // Then
            verify(mockPatientConsentManager).getProviderSpecificConsent(mockLoggedInInfo);
            verify(mockPatientConsentManager).hasPatientConsented(TEST_DEMO_NO, mockConsentType);
        }

        @Test
        @DisplayName("should return empty list when consented but DAO returns empty")
        void shouldReturnEmptyList_whenConsentedButNoResults() {
            // Given
            Date afterDate = new Date();
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, mockConsentType)).thenReturn(true);
            when(mockAllergyDao.findByDemographicIdUpdatedAfterDate(TEST_DEMO_NO, afterDate))
                    .thenReturn(Collections.emptyList());

            // When
            List<Allergy> result = allergyManager.getByDemographicIdUpdatedAfterDate(
                    mockLoggedInInfo, TEST_DEMO_NO, afterDate);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should pass exact demographic ID and date to DAO")
        void shouldPassExactParameters_whenPatientConsented() {
            // Given
            int specificDemo = 54321;
            Date specificDate = new Date(5000000L);
            when(mockPatientConsentManager.hasPatientConsented(specificDemo, mockConsentType)).thenReturn(true);
            when(mockAllergyDao.findByDemographicIdUpdatedAfterDate(specificDemo, specificDate))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getByDemographicIdUpdatedAfterDate(mockLoggedInInfo, specificDemo, specificDate);

            // Then
            verify(mockAllergyDao).findByDemographicIdUpdatedAfterDate(eq(54321), eq(specificDate));
        }
    }

    /**
     * Tests for {@link AllergyManager#getAllergiesByProgramProviderDemographicDate(
     * io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, String, Integer, Calendar, int)}.
     *
     * <p>Verifies the composite query that filters allergies by provider, demographic,
     * date, and item count. This method always delegates to the DAO and always logs.</p>
     */
    @Nested
    @DisplayName("getAllergiesByProgramProviderDemographicDate")
    @Tag("read")
    @Tag("query")
    class GetAllergiesByProgramProviderDemographicDate {

        private Calendar testCalendar;

        @BeforeEach
        void setUpCalendar() {
            testCalendar = Calendar.getInstance();
            testCalendar.set(2025, Calendar.JANUARY, 1);
        }

        @Test
        @DisplayName("should return allergies matching composite criteria")
        void shouldReturnAllergies_whenMatchingCriteriaFound() {
            // Given
            Integer programId = 100;
            int itemsToReturn = 10;
            List<Allergy> expected = List.of(createTestAllergyWithId(1), createTestAllergyWithId(2));
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, testCalendar.getTime(), itemsToReturn))
                    .thenReturn(expected);

            // When
            List<Allergy> result = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, testCalendar, itemsToReturn);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when no matching allergies exist")
        void shouldReturnEmptyList_whenNoMatchingAllergies() {
            // Given
            Integer programId = 100;
            int itemsToReturn = 10;
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, testCalendar.getTime(), itemsToReturn))
                    .thenReturn(Collections.emptyList());

            // When
            List<Allergy> result = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, testCalendar, itemsToReturn);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should delegate to DAO with provider, demographic, date, and limit")
        void shouldDelegateToDao_whenCalledWithAllParameters() {
            // Given
            Integer programId = 200;
            String providerNo = "111111";
            Integer demographicId = 55555;
            int itemsToReturn = 50;
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    providerNo, demographicId, testCalendar.getTime(), itemsToReturn))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, providerNo, demographicId, testCalendar, itemsToReturn);

            // Then
            verify(mockAllergyDao).findByProviderDemographicLastUpdateDate(
                    eq("111111"), eq(55555), eq(testCalendar.getTime()), eq(50));
        }

        @Test
        @DisplayName("should convert Calendar to Date when delegating to DAO")
        void shouldConvertCalendarToDate_whenDelegatingToDao() {
            // Given
            Integer programId = 100;
            int itemsToReturn = 10;
            Date expectedDate = testCalendar.getTime();
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, expectedDate, itemsToReturn))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, testCalendar, itemsToReturn);

            // Then
            verify(mockAllergyDao).findByProviderDemographicLastUpdateDate(
                    anyString(), anyInt(), eq(expectedDate), anyInt());
        }

        @Test
        @DisplayName("should not use programId in DAO query")
        void shouldNotUseProgramId_whenDelegatingToDao() {
            // Given - programId is ignored in the current implementation
            Integer programIdA = 100;
            Integer programIdB = 999;
            int itemsToReturn = 10;
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, testCalendar.getTime(), itemsToReturn))
                    .thenReturn(List.of(createTestAllergyWithId(1)));

            // When
            List<Allergy> resultA = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programIdA, TEST_PROVIDER, TEST_DEMO_NO, testCalendar, itemsToReturn);
            List<Allergy> resultB = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, programIdB, TEST_PROVIDER, TEST_DEMO_NO, testCalendar, itemsToReturn);

            // Then - both calls produce the same result regardless of programId
            assertThat(resultA).hasSameSizeAs(resultB);
        }
    }

    /**
     * Tests covering edge cases and boundary conditions across all methods.
     *
     * <p>Validates behavior with null parameters, empty collections, and
     * other boundary values that could occur in production.</p>
     */
    @Nested
    @DisplayName("Edge Cases")
    @Tag("read")
    class EdgeCases {

        @Test
        @DisplayName("getAllergy should handle zero ID")
        void shouldHandleZeroId_whenGettingAllergy() {
            // Given
            when(mockAllergyDao.find(0)).thenReturn(null);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, 0);

            // Then
            assertThat(result).isNull();
            verify(mockAllergyDao).find((Object) 0);
        }

        @Test
        @DisplayName("getAllergy should handle negative ID")
        void shouldHandleNegativeId_whenGettingAllergy() {
            // Given
            when(mockAllergyDao.find(-1)).thenReturn(null);

            // When
            Allergy result = allergyManager.getAllergy(mockLoggedInInfo, -1);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getActiveAllergies should handle null demographic number")
        void shouldHandleNullDemographicNo_whenGettingActiveAllergies() {
            // Given
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(isNull())).thenReturn(null);

            // When
            List<Allergy> result = allergyManager.getActiveAllergies(mockLoggedInInfo, null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getUpdatedAfterDate should handle zero items to return")
        void shouldHandleZeroItemsToReturn_whenGettingUpdatedAfterDate() {
            // Given
            Date cutoffDate = new Date();
            when(mockAllergyDao.findByUpdateDate(cutoffDate, 0)).thenReturn(new ArrayList<>());

            // When
            List<Allergy> result = allergyManager.getUpdatedAfterDate(mockLoggedInInfo, cutoffDate, 0);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getUpdatedAfterDate should handle null date")
        void shouldHandleNullDate_whenGettingUpdatedAfterDate() {
            // Given
            when(mockAllergyDao.findByUpdateDate(isNull(), eq(10))).thenReturn(new ArrayList<>());

            // When
            List<Allergy> result = allergyManager.getUpdatedAfterDate(mockLoggedInInfo, null, 10);

            // Then
            assertThat(result).isNotNull();
            verify(mockAllergyDao).findByUpdateDate(isNull(), eq(10));
        }

        @Test
        @DisplayName("getByDemographicIdUpdatedAfterDate should handle null consent type")
        void shouldHandleNullConsentType_whenCheckingConsent() {
            // Given - getProviderSpecificConsent returns null
            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(null);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, null)).thenReturn(false);

            // When
            List<Allergy> result = allergyManager.getByDemographicIdUpdatedAfterDate(
                    mockLoggedInInfo, TEST_DEMO_NO, new Date());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getAllergiesByProgramProviderDemographicDate should handle null provider")
        void shouldHandleNullProvider_whenGettingByCompositeQuery() {
            // Given
            Calendar cal = Calendar.getInstance();
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    isNull(), eq(TEST_DEMO_NO), eq(cal.getTime()), eq(10)))
                    .thenReturn(Collections.emptyList());

            // When
            List<Allergy> result = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, 100, null, TEST_DEMO_NO, cal, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getAllergiesByProgramProviderDemographicDate should handle null programId")
        void shouldHandleNullProgramId_whenGettingByCompositeQuery() {
            // Given
            Calendar cal = Calendar.getInstance();
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    eq(TEST_PROVIDER), eq(TEST_DEMO_NO), eq(cal.getTime()), eq(5)))
                    .thenReturn(Collections.emptyList());

            // When - programId is not passed to the DAO, so null should be fine
            List<Allergy> result = allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, null, TEST_PROVIDER, TEST_DEMO_NO, cal, 5);

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests verifying the interaction pattern between the manager and its DAO dependency.
     *
     * <p>Ensures the manager delegates correctly to the DAO without modifying
     * inputs or outputs, and does not call unexpected DAO methods.</p>
     */
    @Nested
    @DisplayName("DAO Interaction Verification")
    @Tag("read")
    class DaoInteraction {

        @Test
        @DisplayName("getAllergy should call find exactly once")
        void shouldCallFindExactlyOnce_whenGettingAllergy() {
            // Given
            when(mockAllergyDao.find(TEST_ALLERGY_ID)).thenReturn(createTestAllergyWithId(TEST_ALLERGY_ID));

            // When
            allergyManager.getAllergy(mockLoggedInInfo, TEST_ALLERGY_ID);

            // Then
            verify(mockAllergyDao, times(1)).find(TEST_ALLERGY_ID);
            verifyNoMoreInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("getActiveAllergies should call findActiveAllergiesOrderByDescription exactly once")
        void shouldCallFindActiveAllergiesOnce_whenGettingActiveAllergies() {
            // Given
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockAllergyDao, times(1)).findActiveAllergiesOrderByDescription(TEST_DEMO_NO);
            verifyNoMoreInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("getUpdatedAfterDate should call findByUpdateDate exactly once")
        void shouldCallFindByUpdateDateOnce_whenGettingUpdatedAfterDate() {
            // Given
            Date date = new Date();
            when(mockAllergyDao.findByUpdateDate(date, 5)).thenReturn(new ArrayList<>());

            // When
            allergyManager.getUpdatedAfterDate(mockLoggedInInfo, date, 5);

            // Then
            verify(mockAllergyDao, times(1)).findByUpdateDate(date, 5);
            verifyNoMoreInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("getByDemographicIdUpdatedAfterDate should not interact with DAO when consent denied")
        void shouldNotInteractWithDao_whenConsentDenied() {
            // Given
            ConsentType consentType = mock(ConsentType.class);
            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(false);

            // When
            allergyManager.getByDemographicIdUpdatedAfterDate(mockLoggedInInfo, TEST_DEMO_NO, new Date());

            // Then
            verifyNoInteractions(mockAllergyDao);
        }
    }

    /**
     * Tests verifying the consent manager interactions.
     *
     * <p>Ensures the manager properly consults the PatientConsentManager
     * for methods that require consent-based data filtering.</p>
     */
    @Nested
    @DisplayName("Consent Manager Interaction")
    @Tag("read")
    class ConsentManagerInteraction {

        @Test
        @DisplayName("getUpdatedAfterDate should always invoke consent filtering")
        void shouldAlwaysInvokeConsentFiltering_whenGettingUpdatedAfterDate() {
            // Given
            Date date = new Date();
            List<Allergy> results = new ArrayList<>();
            when(mockAllergyDao.findByUpdateDate(date, 10)).thenReturn(results);

            // When
            allergyManager.getUpdatedAfterDate(mockLoggedInInfo, date, 10);

            // Then
            verify(mockPatientConsentManager).filterProviderSpecificConsent(mockLoggedInInfo, results);
        }

        @Test
        @DisplayName("getAllergy should not consult consent manager")
        void shouldNotConsultConsentManager_whenGettingAllergyById() {
            // Given
            when(mockAllergyDao.find(TEST_ALLERGY_ID)).thenReturn(createTestAllergyWithId(TEST_ALLERGY_ID));

            // When
            allergyManager.getAllergy(mockLoggedInInfo, TEST_ALLERGY_ID);

            // Then
            verifyNoInteractions(mockPatientConsentManager);
        }

        @Test
        @DisplayName("getActiveAllergies should not consult consent manager")
        void shouldNotConsultConsentManager_whenGettingActiveAllergies() {
            // Given
            when(mockAllergyDao.findActiveAllergiesOrderByDescription(TEST_DEMO_NO))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getActiveAllergies(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verifyNoInteractions(mockPatientConsentManager);
        }

        @Test
        @DisplayName("getByDemographicIdUpdatedAfterDate should check consent before querying DAO")
        void shouldCheckConsentBeforeQueryingDao_whenGettingByDemographicDate() {
            // Given
            ConsentType consentType = mock(ConsentType.class);
            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);
            when(mockAllergyDao.findByDemographicIdUpdatedAfterDate(eq(TEST_DEMO_NO), any(Date.class)))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getByDemographicIdUpdatedAfterDate(mockLoggedInInfo, TEST_DEMO_NO, new Date());

            // Then - verify the consent calls happened in order before DAO
            var inOrder = inOrder(mockPatientConsentManager, mockAllergyDao);
            inOrder.verify(mockPatientConsentManager).getProviderSpecificConsent(mockLoggedInInfo);
            inOrder.verify(mockPatientConsentManager).hasPatientConsented(TEST_DEMO_NO, consentType);
            inOrder.verify(mockAllergyDao).findByDemographicIdUpdatedAfterDate(eq(TEST_DEMO_NO), any(Date.class));
        }

        @Test
        @DisplayName("getAllergiesByProgramProviderDemographicDate should not consult consent manager")
        void shouldNotConsultConsentManager_whenGettingByCompositeQuery() {
            // Given
            Calendar cal = Calendar.getInstance();
            when(mockAllergyDao.findByProviderDemographicLastUpdateDate(
                    eq(TEST_PROVIDER), eq(TEST_DEMO_NO), any(Date.class), eq(10)))
                    .thenReturn(Collections.emptyList());

            // When
            allergyManager.getAllergiesByProgramProviderDemographicDate(
                    mockLoggedInInfo, 100, TEST_PROVIDER, TEST_DEMO_NO, cal, 10);

            // Then
            verifyNoInteractions(mockPatientConsentManager);
        }
    }

    /**
     * Tests for {@link AllergyManager#saveAllergy(LoggedInInfo, Allergy)}.
     *
     * <p>Verifies new allergy persistence including providerNo defaulting,
     * entryDate defaulting, partial-date persistence, and audit logging.</p>
     */
    @Nested
    @DisplayName("saveAllergy")
    @Tag("create")
    class SaveAllergy {

        @Test
        @DisplayName("should persist allergy and return it")
        void shouldPersistAndReturnAllergy_whenValidAllergyProvided() {
            // Given
            Allergy allergy = createTestAllergy();

            // When
            Allergy result = allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(result).isSameAs(allergy);
            verify(mockAllergyDao).persist(allergy);
        }

        @Test
        @DisplayName("should default providerNo from loggedInInfo when not set")
        void shouldDefaultProviderNo_whenProviderNoIsNull() {
            // Given
            Allergy allergy = createTestAllergy();
            allergy.setProviderNo(null);

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getProviderNo()).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("should not overwrite providerNo when already set")
        void shouldNotOverwriteProviderNo_whenAlreadySet() {
            // Given
            Allergy allergy = createTestAllergy();
            allergy.setProviderNo("000001");

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getProviderNo()).isEqualTo("000001");
        }

        @Test
        @DisplayName("should default entryDate to now when not set")
        void shouldDefaultEntryDate_whenEntryDateIsNull() {
            // Given
            Allergy allergy = createTestAllergy();
            allergy.setEntryDate(null);
            Date before = new Date();

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getEntryDate()).isNotNull();
            assertThat(allergy.getEntryDate()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("should not overwrite entryDate when already set")
        void shouldNotOverwriteEntryDate_whenAlreadySet() {
            // Given
            Date existingDate = new Date(1000000L);
            Allergy allergy = createTestAllergy();
            allergy.setEntryDate(existingDate);

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getEntryDate()).isEqualTo(existingDate);
        }

        @Test
        @DisplayName("should persist partial start-date format after saving allergy")
        void shouldPersistPartialDateFormat_afterPersistingAllergy() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            allergy.setStartDateFormat("YYYY");

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then - verify persist happened before partial date
            var inOrder = inOrder(mockAllergyDao, mockPartialDateDao);
            inOrder.verify(mockAllergyDao).persist(allergy);
            inOrder.verify(mockPartialDateDao).setPartialDate(
                    PartialDate.ALLERGIES,
                    TEST_ALLERGY_ID,
                    PartialDate.ALLERGIES_STARTDATE,
                    "YYYY");
        }

        @Test
        @DisplayName("should persist partial-date with null format when startDateFormat not set")
        void shouldPersistPartialDate_withNullFormat_whenStartDateFormatNotSet() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            allergy.setStartDateFormat(null);

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            verify(mockPartialDateDao).setPartialDate(
                    PartialDate.ALLERGIES,
                    TEST_ALLERGY_ID,
                    PartialDate.ALLERGIES_STARTDATE,
                    null);
        }

        @Test
        @DisplayName("should throw when demographicNo is null")
        void shouldThrowIllegalArgumentException_whenDemographicNoIsNull() {
            // Given
            Allergy allergy = createTestAllergy();
            allergy.setDemographicNo(null);

            // When / Then
            assertThatThrownBy(() -> allergyManager.saveAllergy(mockLoggedInInfo, allergy))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("demographicNo");
            verifyNoInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("should write an audit log entry after persisting")
        void shouldWriteAuditLog_afterPersisting() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);

            // When
            allergyManager.saveAllergy(mockLoggedInInfo, allergy);

            // Then
            logActionMock.verify(() -> LogAction.addLogSynchronous(
                    eq(mockLoggedInInfo),
                    eq("AllergyManager.saveAllergy"),
                    contains("demographicNo=" + TEST_DEMO_NO)));
        }
    }

    /**
     * Tests for {@link AllergyManager#updateAllergy(LoggedInInfo, Allergy)}.
     *
     * <p>Verifies allergy update merging including providerNo defaulting, ID validation,
     * demographicNo validation, and audit logging.</p>
     */
    @Nested
    @DisplayName("updateAllergy")
    @Tag("update")
    class UpdateAllergy {

        @Test
        @DisplayName("should merge allergy and return result")
        void shouldMergeAllergyAndReturnResult_whenValidAllergyProvided() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            Allergy merged = createTestAllergyWithId(TEST_ALLERGY_ID);
            when(mockAllergyDao.merge(allergy)).thenReturn(merged);

            // When
            Allergy result = allergyManager.updateAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(result).isSameAs(merged);
            verify(mockAllergyDao).merge(allergy);
        }

        @Test
        @DisplayName("should default providerNo from loggedInInfo when not set")
        void shouldDefaultProviderNo_whenProviderNoIsNull() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            allergy.setProviderNo(null);
            when(mockAllergyDao.merge(allergy)).thenReturn(allergy);

            // When
            allergyManager.updateAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getProviderNo()).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("should not overwrite providerNo when already set")
        void shouldNotOverwriteProviderNo_whenAlreadySet() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            allergy.setProviderNo("000002");
            when(mockAllergyDao.merge(allergy)).thenReturn(allergy);

            // When
            allergyManager.updateAllergy(mockLoggedInInfo, allergy);

            // Then
            assertThat(allergy.getProviderNo()).isEqualTo("000002");
        }

        @Test
        @DisplayName("should throw when allergy ID is null")
        void shouldThrowIllegalArgumentException_whenAllergyIdIsNull() {
            // Given
            Allergy allergy = createTestAllergy(); // no ID set

            // When / Then
            assertThatThrownBy(() -> allergyManager.updateAllergy(mockLoggedInInfo, allergy))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ID");
            verifyNoInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("should throw when demographicNo is null")
        void shouldThrowIllegalArgumentException_whenDemographicNoIsNull() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            allergy.setDemographicNo(null);

            // When / Then
            assertThatThrownBy(() -> allergyManager.updateAllergy(mockLoggedInInfo, allergy))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("demographicNo");
            verifyNoInteractions(mockAllergyDao);
        }

        @Test
        @DisplayName("should write an audit log entry after merging")
        void shouldWriteAuditLog_afterMerging() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            when(mockAllergyDao.merge(allergy)).thenReturn(allergy);

            // When
            allergyManager.updateAllergy(mockLoggedInInfo, allergy);

            // Then
            logActionMock.verify(() -> LogAction.addLogSynchronous(
                    eq(mockLoggedInInfo),
                    eq("AllergyManager.updateAllergy"),
                    contains("id=" + TEST_ALLERGY_ID)));
        }

        @Test
        @DisplayName("should include demographicNo in audit log")
        void shouldIncludeDemographicNoInAuditLog_whenUpdating() {
            // Given
            Allergy allergy = createTestAllergyWithId(TEST_ALLERGY_ID);
            when(mockAllergyDao.merge(allergy)).thenReturn(allergy);

            // When
            allergyManager.updateAllergy(mockLoggedInInfo, allergy);

            // Then
            logActionMock.verify(() -> LogAction.addLogSynchronous(
                    eq(mockLoggedInInfo),
                    eq("AllergyManager.updateAllergy"),
                    contains("demographicNo=" + TEST_DEMO_NO)));
        }
    }
}
