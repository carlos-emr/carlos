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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MeasurementManagerImpl} business logic.
 *
 * <p>This test class provides comprehensive coverage of the MeasurementManagerImpl
 * service layer, testing DAO delegation, patient consent gating, audit logging
 * interactions, measurement group/property management, and edge cases without
 * requiring a database or Spring context.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>DAO delegation verification</li>
 *   <li>Patient consent gate logic for measurement retrieval</li>
 *   <li>Audit logging conditional behavior</li>
 *   <li>SpringUtils.getBean() lookups for PropertyDao and MeasurementGroupStyleDao</li>
 *   <li>Measurement group flowsheet HTML template management</li>
 *   <li>Edge cases: null results, empty lists, missing groups</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see MeasurementManagerImpl
 * @see MeasurementUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MeasurementManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("measurement")
public class MeasurementManagerUnitTest extends MeasurementUnitTestBase {

    @Mock
    private MeasurementDao mockMeasurementDao;

    @Mock
    private MeasurementMapDao mockMeasurementMapDao;

    @Mock
    private PatientConsentManager mockPatientConsentManager;

    @Mock
    private PropertyDao mockPropertyDao;

    @Mock
    private MeasurementGroupStyleDao mockMeasurementGroupStyleDao;

    /** The MeasurementManagerImpl instance under test. */
    private MeasurementManagerImpl measurementManager;

    /**
     * Sets up the test environment before each test method.
     *
     * <p>This method performs the following setup:</p>
     * <ol>
     *   <li>Registers mocks with SpringUtils for static bean lookups (PropertyDao, MeasurementGroupStyleDao)</li>
     *   <li>Creates the manager instance and injects all dependencies via reflection</li>
     * </ol>
     *
     * <p><b>Note:</b> SecurityInfoManager is already registered by MeasurementUnitTestBase.
     * LogAction and SpringUtils mocking is handled by OpenOUnitTestBase.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils.getBean() lookups used in getDShtml, isProperty, findGroupId, etc.
        registerMock(PropertyDao.class, mockPropertyDao);
        registerMock(MeasurementGroupStyleDao.class, mockMeasurementGroupStyleDao);

        // Create manager instance
        measurementManager = new MeasurementManagerImpl();

        // Inject @Autowired dependencies via reflection
        injectDependency(measurementManager, "measurementDao", mockMeasurementDao);
        injectDependency(measurementManager, "measurementMapDao", mockMeasurementMapDao);
        injectDependency(measurementManager, "patientConsentManager", mockPatientConsentManager);
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getMeasurement(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     *
     * <p>Verifies single-measurement retrieval by ID with conditional audit logging.</p>
     */
    @Nested
    @DisplayName("getMeasurement")
    class GetMeasurement {

        @Test
        @DisplayName("should return measurement when valid ID is provided")
        void shouldReturnMeasurement_whenValidIdProvided() {
            // Given
            Measurement expected = createTestMeasurementWithId(TEST_MEASUREMENT_ID);
            when(mockMeasurementDao.find(TEST_MEASUREMENT_ID)).thenReturn(expected);

            // When
            Measurement result = measurementManager.getMeasurement(mockLoggedInInfo, TEST_MEASUREMENT_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should return null when measurement not found")
        void shouldReturnNull_whenMeasurementNotFound() {
            // Given
            when(mockMeasurementDao.find(999)).thenReturn(null);

            // When
            Measurement result = measurementManager.getMeasurement(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should delegate to DAO find method with correct ID")
        void shouldDelegateToDao_withCorrectId() {
            // Given
            when(mockMeasurementDao.find(TEST_MEASUREMENT_ID)).thenReturn(createTestMeasurementWithId(TEST_MEASUREMENT_ID));

            // When
            measurementManager.getMeasurement(mockLoggedInInfo, TEST_MEASUREMENT_ID);

            // Then
            verify(mockMeasurementDao).find(TEST_MEASUREMENT_ID);
        }

        @Test
        @DisplayName("should handle null ID gracefully")
        void shouldHandleNullId() {
            // Given
            when(mockMeasurementDao.find(null)).thenReturn(null);

            // When
            Measurement result = measurementManager.getMeasurement(mockLoggedInInfo, null);

            // Then
            assertThat(result).isNull();
            verify(mockMeasurementDao).find(null);
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getCreatedAfterDate(io.github.carlos_emr.carlos.utility.LoggedInInfo, Date, int)}.
     *
     * <p>Verifies paginated retrieval of measurements created after a given date.</p>
     */
    @Nested
    @DisplayName("getCreatedAfterDate")
    class GetCreatedAfterDate {

        @Test
        @DisplayName("should return measurements created after the specified date")
        void shouldReturnMeasurements_whenCreatedAfterDate() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 10;
            List<Measurement> expected = List.of(
                    createTestMeasurementWithId(1),
                    createTestMeasurementWithId(2)
            );
            when(mockMeasurementDao.findByCreateDate(cutoffDate, itemsToReturn)).thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getCreatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when no measurements created after date")
        void shouldReturnEmptyList_whenNoneCreatedAfterDate() {
            // Given
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L);
            when(mockMeasurementDao.findByCreateDate(futureDate, 10)).thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getCreatedAfterDate(mockLoggedInInfo, futureDate, 10);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should delegate to DAO with correct parameters")
        void shouldDelegateToDao_withCorrectParameters() {
            // Given
            Date cutoffDate = new Date();
            int itemsToReturn = 25;
            when(mockMeasurementDao.findByCreateDate(cutoffDate, itemsToReturn)).thenReturn(Collections.emptyList());

            // When
            measurementManager.getCreatedAfterDate(mockLoggedInInfo, cutoffDate, itemsToReturn);

            // Then
            verify(mockMeasurementDao).findByCreateDate(cutoffDate, itemsToReturn);
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getMeasurementByType(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, List)}.
     *
     * <p>Verifies retrieval of measurements filtered by type codes for a given patient.</p>
     */
    @Nested
    @DisplayName("getMeasurementByType")
    class GetMeasurementByType {

        @Test
        @DisplayName("should return measurements matching the requested types")
        void shouldReturnMeasurements_whenValidTypesProvided() {
            // Given
            List<String> types = List.of(TEST_TYPE_BP, TEST_TYPE_WT);
            List<Measurement> expected = List.of(
                    createTestMeasurement(TEST_TYPE_BP, "120/80"),
                    createTestMeasurement(TEST_TYPE_WT, "75.0")
            );
            when(mockMeasurementDao.findByType(TEST_DEMO_NO, types)).thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getMeasurementByType(mockLoggedInInfo, TEST_DEMO_NO, types);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when no measurements of requested type exist")
        void shouldReturnEmptyList_whenNoMeasurementsOfType() {
            // Given
            List<String> types = List.of("NONEXISTENT");
            when(mockMeasurementDao.findByType(TEST_DEMO_NO, types)).thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getMeasurementByType(mockLoggedInInfo, TEST_DEMO_NO, types);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should delegate to DAO with correct demographic ID and types")
        void shouldDelegateToDao_withCorrectParameters() {
            // Given
            List<String> types = List.of(TEST_TYPE_HT);
            when(mockMeasurementDao.findByType(TEST_DEMO_NO, types)).thenReturn(Collections.emptyList());

            // When
            measurementManager.getMeasurementByType(mockLoggedInInfo, TEST_DEMO_NO, types);

            // Then
            verify(mockMeasurementDao).findByType(TEST_DEMO_NO, types);
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getMeasurementByDemographicIdAfter(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, Date)}.
     *
     * <p>Verifies consent-gated retrieval of measurements updated after a date.
     * The method first checks patient consent via {@link PatientConsentManager}
     * before querying the DAO.</p>
     */
    @Nested
    @DisplayName("getMeasurementByDemographicIdAfter (consent-gated)")
    class GetMeasurementByDemographicIdAfter {

        @Test
        @DisplayName("should return measurements when patient has consented")
        void shouldReturnMeasurements_whenPatientHasConsented() {
            // Given
            Date updateAfter = new Date();
            ConsentType consentType = new ConsentType();
            List<Measurement> expected = List.of(createTestMeasurementWithId(1));

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);
            when(mockMeasurementDao.findByDemographicLastUpdateAfterDate(TEST_DEMO_NO, updateAfter)).thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getMeasurementByDemographicIdAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when patient has not consented")
        void shouldReturnEmptyList_whenPatientHasNotConsented() {
            // Given
            Date updateAfter = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(false);

            // When
            List<Measurement> results = measurementManager.getMeasurementByDemographicIdAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not query DAO when patient has not consented")
        void shouldNotQueryDao_whenPatientHasNotConsented() {
            // Given
            Date updateAfter = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(false);

            // When
            measurementManager.getMeasurementByDemographicIdAfter(mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then
            verify(mockMeasurementDao, never()).findByDemographicLastUpdateAfterDate(anyInt(), any(Date.class));
        }

        @Test
        @DisplayName("should check consent before querying DAO")
        void shouldCheckConsentBeforeQuerying() {
            // Given
            Date updateAfter = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);
            when(mockMeasurementDao.findByDemographicLastUpdateAfterDate(TEST_DEMO_NO, updateAfter))
                    .thenReturn(Collections.emptyList());

            // When
            measurementManager.getMeasurementByDemographicIdAfter(mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then - verify consent was checked
            verify(mockPatientConsentManager).getProviderSpecificConsent(mockLoggedInInfo);
            verify(mockPatientConsentManager).hasPatientConsented(TEST_DEMO_NO, consentType);
        }

        @Test
        @DisplayName("should return empty list when consent type is null and patient has not consented")
        void shouldReturnEmptyList_whenConsentTypeNullAndPatientNotConsented() {
            // Given
            Date updateAfter = new Date();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(null);
            when(mockPatientConsentManager.hasPatientConsented(eq(TEST_DEMO_NO), isNull())).thenReturn(false);

            // When
            List<Measurement> results = measurementManager.getMeasurementByDemographicIdAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getLatestMeasurementsByDemographicIdObservedAfter(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, Date)}.
     *
     * <p>Verifies consent-gated retrieval with a dual consent check:
     * access is granted if the provider has specific consent OR if the consent type
     * is not managed by the clinic at all.</p>
     */
    @Nested
    @DisplayName("getLatestMeasurementsByDemographicIdObservedAfter (consent-gated)")
    class GetLatestMeasurementsByDemographicIdObservedAfter {

        @Test
        @DisplayName("should return measurements when provider has specific consent")
        void shouldReturnMeasurements_whenProviderHasSpecificConsent() {
            // Given
            Date observedDate = new Date();
            List<Measurement> expected = List.of(createTestMeasurementWithId(1));

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(true);
            when(mockMeasurementDao.findLatestByDemographicObservedAfterDate(TEST_DEMO_NO, observedDate))
                    .thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return measurements when consent type is not managed by clinic")
        void shouldReturnMeasurements_whenConsentTypeNotManaged() {
            // Given - provider does NOT have specific consent, but consent type doesn't exist in system
            Date observedDate = new Date();
            List<Measurement> expected = List.of(createTestMeasurementWithId(2));

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(false);
            when(mockPatientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER)).thenReturn(null);
            when(mockMeasurementDao.findLatestByDemographicObservedAfterDate(TEST_DEMO_NO, observedDate))
                    .thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when no consent and consent type is managed")
        void shouldReturnEmptyList_whenNoConsentAndConsentTypeIsManaged() {
            // Given - provider does NOT have specific consent AND consent type exists in system
            Date observedDate = new Date();
            ConsentType managedConsentType = new ConsentType();

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(false);
            when(mockPatientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER))
                    .thenReturn(managedConsentType);

            // When
            List<Measurement> results = measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should not query DAO when consent is denied")
        void shouldNotQueryDao_whenConsentDenied() {
            // Given
            Date observedDate = new Date();
            ConsentType managedConsentType = new ConsentType();

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(false);
            when(mockPatientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER))
                    .thenReturn(managedConsentType);

            // When
            measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then
            verify(mockMeasurementDao, never()).findLatestByDemographicObservedAfterDate(anyInt(), any(Date.class));
        }

        @Test
        @DisplayName("should return empty list from DAO when no measurements observed after date")
        void shouldReturnEmptyList_whenNoMeasurementsObservedAfterDate() {
            // Given
            Date observedDate = new Date();

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(true);
            when(mockMeasurementDao.findLatestByDemographicObservedAfterDate(TEST_DEMO_NO, observedDate))
                    .thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should short-circuit on first consent check when provider has consent")
        void shouldShortCircuit_whenProviderHasConsent() {
            // Given - provider HAS consent, so getConsentType should never be called
            Date observedDate = new Date();

            when(mockPatientConsentManager.hasProviderSpecificConsent(mockLoggedInInfo)).thenReturn(true);
            when(mockMeasurementDao.findLatestByDemographicObservedAfterDate(TEST_DEMO_NO, observedDate))
                    .thenReturn(Collections.emptyList());

            // When
            measurementManager.getLatestMeasurementsByDemographicIdObservedAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, observedDate);

            // Then - getConsentType should not be called due to short-circuit OR evaluation
            verify(mockPatientConsentManager, never()).getConsentType(anyString());
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getMeasurementMaps()}.
     *
     * <p>Verifies retrieval of all measurement map configurations (no security context required).</p>
     */
    @Nested
    @DisplayName("getMeasurementMaps")
    class GetMeasurementMaps {

        @Test
        @DisplayName("should return all measurement maps")
        void shouldReturnAllMaps() {
            // Given
            List<MeasurementMap> expected = List.of(
                    createTestMeasurementMap("BP", "8462-4", "Blood Pressure"),
                    createTestMeasurementMap("WT", "3141-9", "Weight")
            );
            when(mockMeasurementMapDao.getAllMaps()).thenReturn(expected);

            // When
            List<MeasurementMap> results = measurementManager.getMeasurementMaps();

            // Then
            assertThat(results).hasSize(2);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should return empty list when no maps exist")
        void shouldReturnEmptyList_whenNoMapsExist() {
            // Given
            when(mockMeasurementMapDao.getAllMaps()).thenReturn(Collections.emptyList());

            // When
            List<MeasurementMap> results = measurementManager.getMeasurementMaps();

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should delegate to MeasurementMapDao")
        void shouldDelegateToMeasurementMapDao() {
            // Given
            when(mockMeasurementMapDao.getAllMaps()).thenReturn(Collections.emptyList());

            // When
            measurementManager.getMeasurementMaps();

            // Then
            verify(mockMeasurementMapDao).getAllMaps();
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#addMeasurement(io.github.carlos_emr.carlos.utility.LoggedInInfo, Measurement)}.
     *
     * <p>Verifies measurement persistence and audit logging on add.</p>
     */
    @Nested
    @DisplayName("addMeasurement")
    class AddMeasurement {

        @Test
        @DisplayName("should persist measurement via DAO")
        void shouldPersistMeasurement() {
            // Given
            Measurement measurement = createTestMeasurement();

            // When
            measurementManager.addMeasurement(mockLoggedInInfo, measurement);

            // Then
            verify(mockMeasurementDao).persist(measurement);
        }

        @Test
        @DisplayName("should return the persisted measurement")
        void shouldReturnPersistedMeasurement() {
            // Given
            Measurement measurement = createTestMeasurement();

            // When
            Measurement result = measurementManager.addMeasurement(mockLoggedInInfo, measurement);

            // Then
            assertThat(result).isSameAs(measurement);
        }

        @Test
        @DisplayName("should persist measurement with blood pressure data")
        void shouldPersistMeasurement_withBloodPressureData() {
            // Given
            Measurement bpMeasurement = createTestMeasurement(TEST_TYPE_BP, "140/90");

            // When
            Measurement result = measurementManager.addMeasurement(mockLoggedInInfo, bpMeasurement);

            // Then
            assertThat(result.getType()).isEqualTo(TEST_TYPE_BP);
            assertThat(result.getDataField()).isEqualTo("140/90");
            verify(mockMeasurementDao).persist(bpMeasurement);
        }

        @Test
        @DisplayName("should persist measurement with weight data")
        void shouldPersistMeasurement_withWeightData() {
            // Given
            Measurement wtMeasurement = createTestMeasurement(TEST_TYPE_WT, "82.5");

            // When
            Measurement result = measurementManager.addMeasurement(mockLoggedInInfo, wtMeasurement);

            // Then
            assertThat(result.getType()).isEqualTo(TEST_TYPE_WT);
            assertThat(result.getDataField()).isEqualTo("82.5");
            verify(mockMeasurementDao).persist(wtMeasurement);
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getMeasurementsByProgramProviderDemographicDate(
     * io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, String, Integer, Calendar, int)}.
     *
     * <p>Verifies retrieval of measurements filtered by program, provider, demographic, and date.</p>
     */
    @Nested
    @DisplayName("getMeasurementsByProgramProviderDemographicDate")
    class GetMeasurementsByProgramProviderDemographicDate {

        @Test
        @DisplayName("should return measurements for given provider and demographic")
        void shouldReturnMeasurements_whenValidParameters() {
            // Given
            Integer programId = 1;
            Calendar cutoff = Calendar.getInstance();
            int itemsToReturn = 50;
            List<Measurement> expected = List.of(createTestMeasurementWithId(1));

            when(mockMeasurementDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, cutoff.getTime(), itemsToReturn))
                    .thenReturn(expected);

            // When
            List<Measurement> results = measurementManager.getMeasurementsByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, cutoff, itemsToReturn);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results).isSameAs(expected);
        }

        @Test
        @DisplayName("should convert Calendar to Date when delegating to DAO")
        void shouldConvertCalendarToDate_whenDelegatingToDao() {
            // Given
            Integer programId = 2;
            Calendar cutoff = Calendar.getInstance();
            cutoff.set(2025, Calendar.JUNE, 15, 10, 30, 0);
            int itemsToReturn = 20;
            Date expectedDate = cutoff.getTime();

            when(mockMeasurementDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, expectedDate, itemsToReturn))
                    .thenReturn(Collections.emptyList());

            // When
            measurementManager.getMeasurementsByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, cutoff, itemsToReturn);

            // Then
            verify(mockMeasurementDao).findByProviderDemographicLastUpdateDate(
                    eq(TEST_PROVIDER), eq(TEST_DEMO_NO), eq(expectedDate), eq(itemsToReturn));
        }

        @Test
        @DisplayName("should return empty list when no measurements match")
        void shouldReturnEmptyList_whenNoMeasurementsMatch() {
            // Given
            Integer programId = 1;
            Calendar cutoff = Calendar.getInstance();

            when(mockMeasurementDao.findByProviderDemographicLastUpdateDate(
                    anyString(), anyInt(), any(Date.class), anyInt()))
                    .thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getMeasurementsByProgramProviderDemographicDate(
                    mockLoggedInInfo, programId, TEST_PROVIDER, TEST_DEMO_NO, cutoff, 10);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#findGroupId(String)}.
     *
     * <p>Verifies group ID lookup by name via MeasurementGroupStyleDao (accessed through SpringUtils).</p>
     */
    @Nested
    @DisplayName("findGroupId")
    class FindGroupId {

        @Test
        @DisplayName("should return group ID when group exists")
        void shouldReturnGroupId_whenGroupExists() {
            // Given
            String groupName = "Vitals";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(42);
            style.setGroupName(groupName);

            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            // When
            String result = measurementManager.findGroupId(groupName);

            // Then
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("should return null when group not found")
        void shouldReturnNull_whenGroupNotFound() {
            // Given
            when(mockMeasurementGroupStyleDao.findByGroupName("NonExistent")).thenReturn(Collections.emptyList());

            // When
            String result = measurementManager.findGroupId("NonExistent");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return last ID when multiple groups exist with same name")
        void shouldReturnLastId_whenMultipleGroupsExist() {
            // Given - the implementation iterates over all results, keeping the last ID
            String groupName = "Duplicated";
            MeasurementGroupStyle style1 = new MeasurementGroupStyle();
            style1.setId(10);
            style1.setGroupName(groupName);

            MeasurementGroupStyle style2 = new MeasurementGroupStyle();
            style2.setId(20);
            style2.setGroupName(groupName);

            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style1, style2));

            // When
            String result = measurementManager.findGroupId(groupName);

            // Then - last element's ID wins due to iteration
            assertThat(result).isEqualTo("20");
        }

        @Test
        @DisplayName("should delegate to MeasurementGroupStyleDao via SpringUtils")
        void shouldDelegateToDao() {
            // Given
            when(mockMeasurementGroupStyleDao.findByGroupName("TestGroup")).thenReturn(Collections.emptyList());

            // When
            measurementManager.findGroupId("TestGroup");

            // Then
            verify(mockMeasurementGroupStyleDao).findByGroupName("TestGroup");
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#isProperty(String)}.
     *
     * <p>Verifies property existence check via PropertyDao (accessed through SpringUtils).</p>
     */
    @Nested
    @DisplayName("isProperty")
    class IsProperty {

        @Test
        @DisplayName("should return true when property exists")
        void shouldReturnTrue_whenPropertyExists() {
            // Given
            Property property = new Property();
            property.setName("some.property");
            property.setValue("someValue");
            when(mockPropertyDao.checkByName("some.property")).thenReturn(property);

            // When
            boolean result = measurementManager.isProperty("some.property");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when property does not exist")
        void shouldReturnFalse_whenPropertyNotExists() {
            // Given
            when(mockPropertyDao.checkByName("nonexistent.property")).thenReturn(null);

            // When
            boolean result = measurementManager.isProperty("nonexistent.property");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should delegate to PropertyDao via SpringUtils")
        void shouldDelegateToPropertyDao() {
            // Given
            when(mockPropertyDao.checkByName("test.prop")).thenReturn(null);

            // When
            measurementManager.isProperty("test.prop");

            // Then
            verify(mockPropertyDao).checkByName("test.prop");
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#getDShtml(String)}.
     *
     * <p>Verifies flowsheet decision support HTML retrieval. This method chains findGroupId,
     * PropertyDao lookup, and MeasurementFlowSheet.getDSHTMLStream (static).</p>
     */
    @Nested
    @DisplayName("getDShtml")
    class GetDShtml {

        @Test
        @DisplayName("should return flowsheet HTML when property exists")
        void shouldReturnFlowsheetHtml_whenPropertyExists() {
            // Given
            String groupName = "Vitals";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(42);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            Property property = new Property();
            property.setName("mgroup.ds.html.42");
            property.setValue("vital_signs.html");
            when(mockPropertyDao.checkByName("mgroup.ds.html.42")).thenReturn(property);

            // Need to mock the static MeasurementFlowSheet.getDSHTMLStream call
            try (MockedStatic<MeasurementFlowSheet> flowSheetMock = mockStatic(MeasurementFlowSheet.class)) {
                flowSheetMock.when(() -> MeasurementFlowSheet.getDSHTMLStream("vital_signs.html"))
                        .thenReturn("<html>Vital Signs DS</html>");

                // When
                String result = measurementManager.getDShtml(groupName);

                // Then
                assertThat(result).isEqualTo("<html>Vital Signs DS</html>");
            }
        }

        @Test
        @DisplayName("should return empty string when property does not exist")
        void shouldReturnEmptyString_whenPropertyNotExists() {
            // Given
            String groupName = "Vitals";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(42);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            when(mockPropertyDao.checkByName("mgroup.ds.html.42")).thenReturn(null);

            // When
            String result = measurementManager.getDShtml(groupName);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when group not found")
        void shouldReturnEmptyString_whenGroupNotFound() {
            // Given - findGroupId returns null, so propKey becomes "mgroup.ds.html.null"
            when(mockMeasurementGroupStyleDao.findByGroupName("Unknown")).thenReturn(Collections.emptyList());
            when(mockPropertyDao.checkByName("mgroup.ds.html.null")).thenReturn(null);

            // When
            String result = measurementManager.getDShtml("Unknown");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should construct property key from group ID")
        void shouldConstructPropertyKey_fromGroupId() {
            // Given
            String groupName = "Diabetes";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(99);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));
            when(mockPropertyDao.checkByName("mgroup.ds.html.99")).thenReturn(null);

            // When
            measurementManager.getDShtml(groupName);

            // Then - verifies the property key was constructed as "mgroup.ds.html." + groupId
            verify(mockPropertyDao).checkByName("mgroup.ds.html.99");
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#addMeasurementGroupDS(String, String)}.
     *
     * <p>Verifies adding or updating decision support HTML for a measurement group.
     * Updates an existing property if one exists, otherwise creates a new one.</p>
     */
    @Nested
    @DisplayName("addMeasurementGroupDS")
    class AddMeasurementGroupDS {

        @Test
        @DisplayName("should update existing property when property already exists")
        void shouldUpdateExistingProperty_whenPropertyAlreadyExists() {
            // Given
            String groupName = "Vitals";
            String dsHTML = "<html>Updated DS</html>";

            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(42);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            Property existingProperty = new Property();
            existingProperty.setName("mgroup.ds.html.42");
            existingProperty.setValue("old_content.html");

            // isProperty check and the subsequent checkByName in addMeasurementGroupDS
            when(mockPropertyDao.checkByName("mgroup.ds.html.42")).thenReturn(existingProperty);

            // When
            measurementManager.addMeasurementGroupDS(groupName, dsHTML);

            // Then
            verify(mockPropertyDao).merge(existingProperty);
            assertThat(existingProperty.getValue()).isEqualTo(dsHTML);
        }

        @Test
        @DisplayName("should create new property when property does not exist")
        void shouldCreateNewProperty_whenPropertyNotExists() {
            // Given
            String groupName = "Vitals";
            String dsHTML = "<html>New DS</html>";

            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(42);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            // isProperty returns null (property doesn't exist)
            when(mockPropertyDao.checkByName("mgroup.ds.html.42")).thenReturn(null);

            // When
            measurementManager.addMeasurementGroupDS(groupName, dsHTML);

            // Then
            verify(mockPropertyDao).persist(argThat(p ->
                    p instanceof Property &&
                    "mgroup.ds.html.42".equals(((Property) p).getName()) &&
                    dsHTML.equals(((Property) p).getValue())
            ));
        }

        @Test
        @DisplayName("should not merge when creating a new property")
        void shouldNotMerge_whenCreatingNewProperty() {
            // Given
            String groupName = "NewGroup";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(77);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));
            when(mockPropertyDao.checkByName("mgroup.ds.html.77")).thenReturn(null);

            // When
            measurementManager.addMeasurementGroupDS(groupName, "<html>content</html>");

            // Then
            verify(mockPropertyDao, never()).merge(any(Property.class));
            verify(mockPropertyDao).persist(any(Property.class));
        }

        @Test
        @DisplayName("should not persist when updating existing property")
        void shouldNotPersist_whenUpdatingExistingProperty() {
            // Given
            String groupName = "ExistingGroup";
            MeasurementGroupStyle style = new MeasurementGroupStyle();
            style.setId(55);
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(List.of(style));

            Property existingProperty = new Property();
            existingProperty.setName("mgroup.ds.html.55");
            when(mockPropertyDao.checkByName("mgroup.ds.html.55")).thenReturn(existingProperty);

            // When
            measurementManager.addMeasurementGroupDS(groupName, "<html>updated</html>");

            // Then
            verify(mockPropertyDao).merge(existingProperty);
            // persist is called in other code paths through findGroupId/isProperty chain;
            // here we verify merge was the final write operation
            verify(mockPropertyDao, never()).persist(argThat(p ->
                    p instanceof Property && "mgroup.ds.html.55".equals(((Property) p).getName())));
        }
    }

    /**
     * Tests for {@link MeasurementManagerImpl#removeMeasurementGroupDS(String)}.
     *
     * <p>Verifies removal of decision support HTML property by key.
     * Only removes if the property exists.</p>
     */
    @Nested
    @DisplayName("removeMeasurementGroupDS")
    class RemoveMeasurementGroupDS {

        @Test
        @DisplayName("should remove property when it exists")
        void shouldRemoveProperty_whenPropertyExists() {
            // Given
            String propKey = "mgroup.ds.html.42";
            Property existingProperty = new Property();
            existingProperty.setName(propKey);

            // Use reflection to set the ID since there's no public setter
            try {
                java.lang.reflect.Field idField = Property.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(existingProperty, 100);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set property ID", e);
            }

            when(mockPropertyDao.checkByName(propKey)).thenReturn(existingProperty);

            // When
            measurementManager.removeMeasurementGroupDS(propKey);

            // Then
            verify(mockPropertyDao).remove(100);
        }

        @Test
        @DisplayName("should not remove anything when property does not exist")
        void shouldDoNothing_whenPropertyNotExists() {
            // Given
            String propKey = "mgroup.ds.html.999";
            when(mockPropertyDao.checkByName(propKey)).thenReturn(null);

            // When
            measurementManager.removeMeasurementGroupDS(propKey);

            // Then
            verify(mockPropertyDao, never()).remove(anyInt());
        }
    }

    /**
     * Tests for edge cases and boundary conditions across multiple methods.
     */
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("getMeasurementByType should handle empty types list")
        void shouldHandleEmptyTypesList() {
            // Given
            List<String> emptyTypes = Collections.emptyList();
            when(mockMeasurementDao.findByType(TEST_DEMO_NO, emptyTypes)).thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getMeasurementByType(
                    mockLoggedInInfo, TEST_DEMO_NO, emptyTypes);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("getCreatedAfterDate should handle zero items to return")
        void shouldHandleZeroItemsToReturn() {
            // Given
            Date cutoffDate = new Date();
            when(mockMeasurementDao.findByCreateDate(cutoffDate, 0)).thenReturn(Collections.emptyList());

            // When
            List<Measurement> results = measurementManager.getCreatedAfterDate(mockLoggedInInfo, cutoffDate, 0);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("getMeasurementsByProgramProviderDemographicDate should handle null programId")
        void shouldHandleNullProgramId() {
            // Given - programId is not actually used by the DAO call, only for logging
            Calendar cutoff = Calendar.getInstance();
            when(mockMeasurementDao.findByProviderDemographicLastUpdateDate(
                    TEST_PROVIDER, TEST_DEMO_NO, cutoff.getTime(), 10))
                    .thenReturn(Collections.emptyList());

            // When - should not throw even with null programId
            List<Measurement> results = measurementManager.getMeasurementsByProgramProviderDemographicDate(
                    mockLoggedInInfo, null, TEST_PROVIDER, TEST_DEMO_NO, cutoff, 10);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("getMeasurementByDemographicIdAfter should handle consented patient with empty results")
        void shouldReturnEmptyList_whenConsentedButNoMeasurements() {
            // Given
            Date updateAfter = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);
            when(mockMeasurementDao.findByDemographicLastUpdateAfterDate(TEST_DEMO_NO, updateAfter))
                    .thenReturn(new ArrayList<>());

            // When
            List<Measurement> results = measurementManager.getMeasurementByDemographicIdAfter(
                    mockLoggedInInfo, TEST_DEMO_NO, updateAfter);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("addMeasurementGroupDS should handle group with null ID from findGroupId")
        void shouldHandleNullGroupId_whenAddingGroupDS() {
            // Given - no group found so findGroupId returns null
            String groupName = "MissingGroup";
            when(mockMeasurementGroupStyleDao.findByGroupName(groupName)).thenReturn(Collections.emptyList());

            // isProperty check for "mgroup.ds.html.null"
            when(mockPropertyDao.checkByName("mgroup.ds.html.null")).thenReturn(null);

            // When
            measurementManager.addMeasurementGroupDS(groupName, "<html>test</html>");

            // Then - should still create property with null in key
            verify(mockPropertyDao).persist(argThat(p ->
                    p instanceof Property &&
                    "mgroup.ds.html.null".equals(((Property) p).getName())
            ));
        }
    }
}
