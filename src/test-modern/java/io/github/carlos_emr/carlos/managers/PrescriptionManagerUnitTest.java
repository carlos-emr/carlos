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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link PrescriptionManagerImpl} business logic.
 *
 * <p>Tests cover prescription retrieval, drug lookups, security privilege enforcement,
 * consent filtering, print tracking, and digital signature workflows without
 * requiring database access or Spring context.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Security privilege verification for healthcare data access</li>
 *   <li>Patient consent filtering for prescription data</li>
 *   <li>Prescription print and reprint date tracking</li>
 *   <li>Manager-DAO interaction patterns for drug and prescription lookups</li>
 *   <li>AccessDeniedException handling for unauthorized write operations</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see PrescriptionManagerImpl
 * @see PrescriptionUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Prescription Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("prescription")
public class PrescriptionManagerUnitTest extends PrescriptionUnitTestBase {

    @Mock
    private PrescriptionDao mockPrescriptionDao;

    @Mock
    private DrugDao mockDrugDao;

    @Mock
    private PatientConsentManager mockPatientConsentManager;

    private PrescriptionManagerImpl prescriptionManager;

    private static final Integer TEST_SCRIPT_NO = 100;
    private static final Integer TEST_PRESCRIPTION_ID = 200;
    private static final Integer TEST_PROGRAM_ID = 1;
    private static final Integer TEST_DIGITAL_SIG_ID = 500;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mock DAOs with SpringUtils, stubs both SecurityInfoManager
     * hasPrivilege overloads (String and int) to grant all privileges by default,
     * creates a fresh {@link PrescriptionManagerImpl} instance, and injects mock
     * dependencies (PrescriptionDao, DrugDao, SecurityInfoManager,
     * PatientConsentManager) via reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(PrescriptionDao.class, mockPrescriptionDao);
        registerMock(DrugDao.class, mockDrugDao);
        registerMock(PatientConsentManager.class, mockPatientConsentManager);

        // Security manager returns true for all privilege checks by default
        // Must stub BOTH overloads: hasPrivilege(..., String) and hasPrivilege(..., int)
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt()))
            .thenReturn(true);

        // Create manager instance and inject dependencies
        prescriptionManager = new PrescriptionManagerImpl();
        injectDependency(prescriptionManager, "prescriptionDao", mockPrescriptionDao);
        injectDependency(prescriptionManager, "drugDao", mockDrugDao);
        injectDependency(prescriptionManager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(prescriptionManager, "patientConsentManager", mockPatientConsentManager);
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /**
     * Injects a prescription ID via reflection since Prescription has no setId method.
     */
    private Prescription createTestPrescriptionWithId(Integer id) {
        Prescription prescription = createTestPrescription();
        try {
            java.lang.reflect.Field field = Prescription.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(prescription, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set prescription ID via reflection", e);
        }
        return prescription;
    }

    /**
     * Configures mock to deny a specific privilege check.
     */
    private void denyPrivilege(String objectName, String action) {
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq(objectName), eq(action), any()))
            .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(eq(mockLoggedInInfo), eq(objectName), eq(action), anyInt()))
            .thenReturn(false);
    }

    // -----------------------------------------------------------------------
    // Nested test classes
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link PrescriptionManagerImpl#getPrescription} and
     * {@link PrescriptionManagerImpl#getPrescriptions}.
     */
    @Nested
    @DisplayName("Get Prescription")
    @Tag("read")
    class GetPrescriptionTests {

        @Test
        @DisplayName("should return prescription when valid ID provided")
        void shouldReturnPrescription_whenValidIdProvided() {
            // Given
            Prescription expected = createTestPrescriptionWithId(TEST_PRESCRIPTION_ID);
            when(mockPrescriptionDao.find(TEST_PRESCRIPTION_ID)).thenReturn(expected);

            // When
            Prescription result = prescriptionManager.getPrescription(mockLoggedInInfo, TEST_PRESCRIPTION_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_PRESCRIPTION_ID);
            assertThat(result.getDemographicId()).isEqualTo(TEST_DEMO_NO);
            verify(mockPrescriptionDao).find(TEST_PRESCRIPTION_ID);
        }

        @Test
        @DisplayName("should delegate to DAO for prescription lookup")
        void shouldDelegateToDao_whenGetPrescriptionCalled() {
            // Given
            Prescription expected = createTestPrescriptionWithId(1);
            when(mockPrescriptionDao.find((Object) 1)).thenReturn(expected);

            // When
            prescriptionManager.getPrescription(mockLoggedInInfo, 1);

            // Then
            verify(mockPrescriptionDao, times(1)).find((Object) 1);
        }

        @Test
        @DisplayName("should return prescription list when demographic ID provided")
        void shouldReturnPrescriptionList_whenDemographicIdProvided() {
            // Given
            List<Prescription> expected = List.of(
                createTestPrescriptionWithId(1),
                createTestPrescriptionWithId(2)
            );
            when(mockPrescriptionDao.findByDemographicId(TEST_DEMO_NO)).thenReturn(expected);

            // When
            List<Prescription> result = prescriptionManager.getPrescriptions(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
            verify(mockPrescriptionDao).findByDemographicId(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when no prescriptions found for demographic")
        void shouldReturnEmptyList_whenNoPrescriptionsFound() {
            // Given
            when(mockPrescriptionDao.findByDemographicId(TEST_DEMO_NO)).thenReturn(Collections.emptyList());

            // When
            List<Prescription> result = prescriptionManager.getPrescriptions(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getDrugsByScriptNo}.
     */
    @Nested
    @DisplayName("Get Drugs By Script Number")
    @Tag("read")
    class GetDrugsByScriptNoTests {

        @Test
        @DisplayName("should return drugs when valid script number provided")
        void shouldReturnDrugs_whenValidScriptNoProvided() {
            // Given
            List<Drug> expected = List.of(createTestDrugWithId(1), createTestDrugWithId(2));
            when(mockDrugDao.findByScriptNo(TEST_SCRIPT_NO, false)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getDrugsByScriptNo(mockLoggedInInfo, TEST_SCRIPT_NO, false);

            // Then
            assertThat(result).hasSize(2);
            verify(mockDrugDao).findByScriptNo(TEST_SCRIPT_NO, false);
        }

        @Test
        @DisplayName("should return archived drugs when archived flag is true")
        void shouldReturnArchivedDrugs_whenArchivedFlagTrue() {
            // Given - drug must have an ID because getDrugsByScriptNo calls
            // Drug.getIdsAsStringList(results) which invokes getId().toString()
            Drug archivedDrug = createTestDrugWithId(TEST_DRUG_ID);
            archivedDrug.setArchived(true);
            when(mockDrugDao.findByScriptNo(TEST_SCRIPT_NO, true)).thenReturn(List.of(archivedDrug));

            // When
            List<Drug> result = prescriptionManager.getDrugsByScriptNo(mockLoggedInInfo, TEST_SCRIPT_NO, true);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isArchived()).isTrue();
        }

        @Test
        @DisplayName("should return empty list when no drugs found for script number")
        void shouldReturnEmptyList_whenNoDrugsFound() {
            // Given
            when(mockDrugDao.findByScriptNo(TEST_SCRIPT_NO, false)).thenReturn(Collections.emptyList());

            // When
            List<Drug> result = prescriptionManager.getDrugsByScriptNo(mockLoggedInInfo, TEST_SCRIPT_NO, false);

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#findDrugById}.
     */
    @Nested
    @DisplayName("Find Drug By ID")
    @Tag("read")
    class FindDrugByIdTests {

        @Test
        @DisplayName("should return drug when access granted and drug exists")
        void shouldReturnDrug_whenAccessGrantedAndDrugExists() {
            // Given
            Drug expected = createTestDrugWithId(TEST_DRUG_ID);
            when(mockDrugDao.find(TEST_DRUG_ID)).thenReturn(expected);

            // When
            Drug result = prescriptionManager.findDrugById(mockLoggedInInfo, TEST_DRUG_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_DRUG_ID);
            verify(mockDrugDao).find(TEST_DRUG_ID);
        }

        @Test
        @DisplayName("should return null when drug does not exist")
        void shouldReturnNull_whenDrugDoesNotExist() {
            // Given
            when(mockDrugDao.find(999)).thenReturn(null);

            // When
            Drug result = prescriptionManager.findDrugById(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when read access denied")
        void shouldReturnNull_whenReadAccessDenied() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            Drug result = prescriptionManager.findDrugById(mockLoggedInInfo, TEST_DRUG_ID);

            // Then
            assertThat(result).isNull();
            verify(mockDrugDao, never()).find(anyInt());
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getActiveMedications(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}
     * and {@link PrescriptionManagerImpl#getActiveMedications(io.github.carlos_emr.carlos.utility.LoggedInInfo, String)}.
     */
    @Nested
    @DisplayName("Get Active Medications")
    @Tag("read")
    class ActiveMedicationsTests {

        @Test
        @DisplayName("should return active medications when valid integer demographic ID provided")
        void shouldReturnActiveMedications_whenValidIntegerDemographicId() {
            // Given
            List<Drug> expected = List.of(createTestDrug(), createTestDrug());
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, false)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
            verify(mockDrugDao).findByDemographicId(TEST_DEMO_NO, false);
        }

        @Test
        @DisplayName("should return active medications when valid string demographic ID provided")
        void shouldReturnActiveMedications_whenValidStringDemographicId() {
            // Given
            List<Drug> expected = List.of(createTestDrug());
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, false)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, TEST_DEMO_NO.toString());

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return null when demographic number is null")
        void shouldReturnNull_whenDemographicNoIsNull() {
            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, (Integer) null);

            // Then
            assertThat(result).isNull();
            verify(mockDrugDao, never()).findByDemographicId(any(), anyBoolean());
        }

        @Test
        @DisplayName("should return null when read access denied for integer overload")
        void shouldReturnNull_whenAccessDeniedForIntegerOverload() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNull();
            verify(mockDrugDao, never()).findByDemographicId(any(), anyBoolean());
        }

        @Test
        @DisplayName("should return null when read access denied for string overload")
        void shouldReturnNull_whenAccessDeniedForStringOverload() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, TEST_DEMO_NO.toString());

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should parse string demographic ID with whitespace")
        void shouldParseStringDemographicId_whenContainsWhitespace() {
            // Given
            List<Drug> expected = List.of(createTestDrug());
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, false)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getActiveMedications(mockLoggedInInfo, " " + TEST_DEMO_NO + " ");

            // Then
            assertThat(result).hasSize(1);
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getMedicationsByDemographicNo}.
     */
    @Nested
    @DisplayName("Get Medications By Demographic Number")
    @Tag("read")
    class MedicationsByDemographicTests {

        @Test
        @DisplayName("should return medications when access granted")
        void shouldReturnMedications_whenAccessGranted() {
            // Given
            List<Drug> expected = List.of(createTestDrug(), createTestDrug());
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, false)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getMedicationsByDemographicNo(
                mockLoggedInInfo, TEST_DEMO_NO, false);

            // Then
            assertThat(result).hasSize(2);
            verify(mockDrugDao).findByDemographicId(TEST_DEMO_NO, false);
        }

        @Test
        @DisplayName("should return archived medications when archived flag is true")
        void shouldReturnArchivedMedications_whenArchivedFlagTrue() {
            // Given
            List<Drug> expected = List.of(createArchivedDrug());
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, true)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getMedicationsByDemographicNo(
                mockLoggedInInfo, TEST_DEMO_NO, true);

            // Then
            assertThat(result).hasSize(1);
            verify(mockDrugDao).findByDemographicId(TEST_DEMO_NO, true);
        }

        @Test
        @DisplayName("should return null when read access denied")
        void shouldReturnNull_whenReadAccessDenied() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            List<Drug> result = prescriptionManager.getMedicationsByDemographicNo(
                mockLoggedInInfo, TEST_DEMO_NO, false);

            // Then
            assertThat(result).isNull();
            verify(mockDrugDao, never()).findByDemographicId(any(), anyBoolean());
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getLongTermDrugs}.
     */
    @Nested
    @DisplayName("Get Long-Term Drugs")
    @Tag("read")
    class LongTermDrugsTests {

        @Test
        @DisplayName("should return long-term drugs when access granted")
        void shouldReturnLongTermDrugs_whenAccessGranted() {
            // Given
            List<Drug> expected = List.of(createLongTermDrug(), createLongTermDrug());
            when(mockDrugDao.findLongTermDrugsByDemographic(TEST_DEMO_NO)).thenReturn(expected);

            // When
            List<Drug> result = prescriptionManager.getLongTermDrugs(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(Drug::isLongTerm);
            verify(mockDrugDao).findLongTermDrugsByDemographic(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when no long-term drugs exist")
        void shouldReturnEmptyList_whenNoLongTermDrugsExist() {
            // Given
            when(mockDrugDao.findLongTermDrugsByDemographic(TEST_DEMO_NO)).thenReturn(Collections.emptyList());

            // When
            List<Drug> result = prescriptionManager.getLongTermDrugs(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return null when read access denied")
        void shouldReturnNull_whenReadAccessDenied() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            List<Drug> result = prescriptionManager.getLongTermDrugs(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNull();
            verify(mockDrugDao, never()).findLongTermDrugsByDemographic(anyInt());
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getUniqueDrugsByPatient}.
     */
    @Nested
    @DisplayName("Get Unique Drugs By Patient")
    @Tag("read")
    class UniqueDrugsByPatientTests {

        @Test
        @DisplayName("should return drugs from DAO when access granted")
        void shouldReturnDrugs_whenAccessGranted() {
            // Given
            Drug drug1 = createTestDrugWithId(1);
            drug1.setGcnSeqNo("12345");
            Drug drug2 = createTestDrugWithId(2);
            drug2.setGcnSeqNo("67890");
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO)).thenReturn(new ArrayList<>(List.of(drug1, drug2)));

            // When
            List<Drug> result = prescriptionManager.getUniqueDrugsByPatient(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isNotEmpty();
            verify(mockDrugDao).findByDemographicId(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when read access denied")
        void shouldReturnEmptyList_whenReadAccessDenied() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            List<Drug> result = prescriptionManager.getUniqueDrugsByPatient(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
            verify(mockDrugDao, never()).findByDemographicId(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return empty list when DAO returns no drugs")
        void shouldReturnEmptyList_whenDaoReturnsNoDrugs() {
            // Given
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO)).thenReturn(new ArrayList<>());

            // When
            List<Drug> result = prescriptionManager.getUniqueDrugsByPatient(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }

    }

    /**
     * Tests for {@link PrescriptionManagerImpl#createNewPrescription}.
     * <p>Only tests security enforcement since the success path depends on
     * legacy static utility classes (RxPatientData, RxProviderData, ProSignatureData)
     * that are not feasible to mock without PowerMock.</p>
     */
    @Nested
    @DisplayName("Create New Prescription")
    @Tag("create")
    class CreateNewPrescriptionTests {

        @Test
        @DisplayName("should throw AccessDeniedException when write privilege denied")
        void shouldThrowAccessDeniedException_whenWritePrivilegeDenied() {
            // Given
            denyPrivilege("_rx", "w");
            List<Drug> drugs = List.of(createTestDrug());

            // When / Then
            assertThatThrownBy(() ->
                prescriptionManager.createNewPrescription(mockLoggedInInfo, drugs, TEST_DEMO_NO)
            ).isInstanceOf(AccessDeniedException.class);

            verify(mockPrescriptionDao, never()).persist(any());
        }

        @Test
        @DisplayName("should check rx write privilege with demographic number")
        void shouldCheckRxWritePrivilege_withDemographicNumber() {
            // Given
            denyPrivilege("_rx", "w");
            List<Drug> drugs = List.of(createTestDrug());

            // When / Then
            assertThatThrownBy(() ->
                prescriptionManager.createNewPrescription(mockLoggedInInfo, drugs, TEST_DEMO_NO)
            ).isInstanceOf(AccessDeniedException.class);

            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "w", TEST_DEMO_NO);
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#print}.
     */
    @Nested
    @DisplayName("Print Prescription")
    @Tag("update")
    class PrintPrescriptionTests {

        @Test
        @DisplayName("should set date printed on first print")
        void shouldSetDatePrinted_whenFirstPrint() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            prescription.setDatePrinted(null);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            boolean result = prescriptionManager.print(mockLoggedInInfo, TEST_SCRIPT_NO);

            // Then
            assertThat(result).isTrue();
            assertThat(prescription.getDatePrinted()).isNotNull();
            verify(mockPrescriptionDao).merge(prescription);
        }

        @Test
        @DisplayName("should append reprint date when already printed")
        void shouldAppendReprintDate_whenAlreadyPrinted() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            prescription.setDatePrinted(new Date());
            prescription.setDatesReprinted(null);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            boolean result = prescriptionManager.print(mockLoggedInInfo, TEST_SCRIPT_NO);

            // Then
            assertThat(result).isTrue();
            assertThat(prescription.getDatesReprinted()).isNotNull();
            assertThat(prescription.getDatesReprinted()).contains(TEST_PROVIDER);
            verify(mockPrescriptionDao).merge(prescription);
        }

        @Test
        @DisplayName("should append to existing reprint dates when reprinted multiple times")
        void shouldAppendToExistingReprintDates_whenReprintedMultipleTimes() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            prescription.setDatePrinted(new Date());
            prescription.setDatesReprinted("2025-01-01 10:00:00;" + TEST_PROVIDER);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            boolean result = prescriptionManager.print(mockLoggedInInfo, TEST_SCRIPT_NO);

            // Then
            assertThat(result).isTrue();
            assertThat(prescription.getDatesReprinted()).contains(",");
            assertThat(prescription.getDatesReprinted()).startsWith("2025-01-01 10:00:00;" + TEST_PROVIDER);
            verify(mockPrescriptionDao).merge(prescription);
        }

        @Test
        @DisplayName("should return false when prescription not found")
        void shouldReturnFalse_whenPrescriptionNotFound() {
            // Given
            when(mockPrescriptionDao.find(999)).thenReturn(null);

            // When
            boolean result = prescriptionManager.print(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isFalse();
            verify(mockPrescriptionDao, never()).merge(any());
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#setPrescriptionSignature}.
     */
    @Nested
    @DisplayName("Set Prescription Signature")
    @Tag("update")
    class SetPrescriptionSignatureTests {

        @Test
        @DisplayName("should set digital signature on prescription")
        void shouldSetDigitalSignature_whenValidScriptNo() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            boolean result = prescriptionManager.setPrescriptionSignature(
                mockLoggedInInfo, TEST_SCRIPT_NO, TEST_DIGITAL_SIG_ID);

            // Then
            assertThat(result).isTrue();
            assertThat(prescription.getDigitalSignatureId()).isEqualTo(TEST_DIGITAL_SIG_ID);
            verify(mockPrescriptionDao).merge(prescription);
        }

        @Test
        @DisplayName("should persist updated prescription after setting signature")
        void shouldPersistUpdatedPrescription_afterSettingSignature() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            prescriptionManager.setPrescriptionSignature(mockLoggedInInfo, TEST_SCRIPT_NO, TEST_DIGITAL_SIG_ID);

            // Then
            verify(mockPrescriptionDao).find((int) TEST_SCRIPT_NO);
            verify(mockPrescriptionDao).merge(prescription);
        }

        @Test
        @DisplayName("should allow null digital signature ID")
        void shouldAllowNullDigitalSignatureId() {
            // Given
            Prescription prescription = createTestPrescriptionWithId(TEST_SCRIPT_NO);
            prescription.setDigitalSignatureId(TEST_DIGITAL_SIG_ID);
            when(mockPrescriptionDao.find((int) TEST_SCRIPT_NO)).thenReturn(prescription);

            // When
            boolean result = prescriptionManager.setPrescriptionSignature(
                mockLoggedInInfo, TEST_SCRIPT_NO, null);

            // Then
            assertThat(result).isTrue();
            assertThat(prescription.getDigitalSignatureId()).isNull();
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getPrescriptionUpdatedAfterDate} and
     * {@link PrescriptionManagerImpl#getPrescriptionByDemographicIdUpdatedAfterDate}.
     */
    @Nested
    @DisplayName("Prescription Updated After Date")
    @Tag("read")
    @Tag("query")
    class PrescriptionUpdatedAfterDateTests {

        @Test
        @DisplayName("should return prescriptions updated after date")
        void shouldReturnPrescriptions_whenUpdatedAfterDate() {
            // Given
            Date cutoffDate = new Date();
            List<Prescription> expected = List.of(createTestPrescriptionWithId(1));
            when(mockPrescriptionDao.findByUpdateDate(cutoffDate, 10)).thenReturn(expected);

            // When
            List<Prescription> result = prescriptionManager.getPrescriptionUpdatedAfterDate(
                mockLoggedInInfo, cutoffDate, 10);

            // Then
            assertThat(result).hasSize(1);
            verify(mockPrescriptionDao).findByUpdateDate(cutoffDate, 10);
            verify(mockPatientConsentManager).filterProviderSpecificConsent(mockLoggedInInfo, expected);
        }

        @Test
        @DisplayName("should filter results by provider-specific consent")
        void shouldFilterByConsent_whenGettingUpdatedPrescriptions() {
            // Given
            Date cutoffDate = new Date();
            List<Prescription> prescriptions = new ArrayList<>(List.of(createTestPrescriptionWithId(1)));
            when(mockPrescriptionDao.findByUpdateDate(cutoffDate, 50)).thenReturn(prescriptions);

            // When
            prescriptionManager.getPrescriptionUpdatedAfterDate(mockLoggedInInfo, cutoffDate, 50);

            // Then
            verify(mockPatientConsentManager).filterProviderSpecificConsent(mockLoggedInInfo, prescriptions);
        }

        @Test
        @DisplayName("should return prescriptions for consented demographic after date")
        void shouldReturnPrescriptions_whenPatientConsented() {
            // Given
            Date cutoffDate = new Date();
            ConsentType consentType = new ConsentType();
            List<Prescription> expected = List.of(createTestPrescriptionWithId(1));

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);
            when(mockPrescriptionDao.findByDemographicIdUpdatedAfterDateExclusive(TEST_DEMO_NO, cutoffDate))
                .thenReturn(expected);

            // When
            List<Prescription> result = prescriptionManager.getPrescriptionByDemographicIdUpdatedAfterDate(
                mockLoggedInInfo, TEST_DEMO_NO, cutoffDate);

            // Then
            assertThat(result).hasSize(1);
            verify(mockPrescriptionDao).findByDemographicIdUpdatedAfterDateExclusive(TEST_DEMO_NO, cutoffDate);
        }

        @Test
        @DisplayName("should return empty list when patient has not consented")
        void shouldReturnEmptyList_whenPatientNotConsented() {
            // Given
            Date cutoffDate = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo)).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(false);

            // When
            List<Prescription> result = prescriptionManager.getPrescriptionByDemographicIdUpdatedAfterDate(
                mockLoggedInInfo, TEST_DEMO_NO, cutoffDate);

            // Then
            assertThat(result).isEmpty();
            verify(mockPrescriptionDao, never()).findByDemographicIdUpdatedAfterDateExclusive(any(), any());
        }
    }

    /**
     * Tests for {@link PrescriptionManagerImpl#getPrescriptionsByProgramProviderDemographicDate}.
     */
    @Nested
    @DisplayName("Prescriptions By Program Provider and Demographic Date")
    @Tag("read")
    @Tag("query")
    class PrescriptionsByProgramProviderTests {

        @Test
        @DisplayName("should return prescriptions matching provider and demographic criteria")
        void shouldReturnPrescriptions_whenMatchingCriteria() {
            // Given
            Calendar cutoffDate = Calendar.getInstance();
            List<Prescription> expected = List.of(createTestPrescriptionWithId(1));
            when(mockPrescriptionDao.findByProviderDemographicLastUpdateDate(
                TEST_PROVIDER, TEST_DEMO_NO, cutoffDate.getTime(), 10))
                .thenReturn(expected);

            // When
            List<Prescription> result = prescriptionManager.getPrescriptionsByProgramProviderDemographicDate(
                mockLoggedInInfo, TEST_PROGRAM_ID, TEST_PROVIDER, TEST_DEMO_NO, cutoffDate, 10);

            // Then
            assertThat(result).hasSize(1);
            verify(mockPrescriptionDao).findByProviderDemographicLastUpdateDate(
                TEST_PROVIDER, TEST_DEMO_NO, cutoffDate.getTime(), 10);
        }

        @Test
        @DisplayName("should return empty list when no matching prescriptions found")
        void shouldReturnEmptyList_whenNoMatchingPrescriptions() {
            // Given
            Calendar cutoffDate = Calendar.getInstance();
            when(mockPrescriptionDao.findByProviderDemographicLastUpdateDate(
                TEST_PROVIDER, TEST_DEMO_NO, cutoffDate.getTime(), 10))
                .thenReturn(Collections.emptyList());

            // When
            List<Prescription> result = prescriptionManager.getPrescriptionsByProgramProviderDemographicDate(
                mockLoggedInInfo, TEST_PROGRAM_ID, TEST_PROVIDER, TEST_DEMO_NO, cutoffDate, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    /**
     * Cross-cutting security access control tests verifying that privilege checks
     * are enforced consistently across all secured methods.
     */
    @Nested
    @DisplayName("Security Access Control")
    @Tag("security")
    class SecurityAccessControlTests {

        @Test
        @DisplayName("should check demographic read privilege for findDrugById")
        void shouldCheckDemographicReadPrivilege_forFindDrugById() {
            // Given
            when(mockDrugDao.find(TEST_DRUG_ID)).thenReturn(createTestDrugWithId(TEST_DRUG_ID));

            // When
            prescriptionManager.findDrugById(mockLoggedInInfo, TEST_DRUG_ID);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_demographic", "r", null);
        }

        @Test
        @DisplayName("should check demographic read privilege for getLongTermDrugs")
        void shouldCheckDemographicReadPrivilege_forGetLongTermDrugs() {
            // Given
            when(mockDrugDao.findLongTermDrugsByDemographic(TEST_DEMO_NO)).thenReturn(Collections.emptyList());

            // When
            prescriptionManager.getLongTermDrugs(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_demographic", "r", null);
        }

        @Test
        @DisplayName("should check demographic read privilege for getUniqueDrugsByPatient")
        void shouldCheckDemographicReadPrivilege_forGetUniqueDrugsByPatient() {
            // Given
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO)).thenReturn(new ArrayList<>());

            // When
            prescriptionManager.getUniqueDrugsByPatient(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_demographic", "r", null);
        }

        @Test
        @DisplayName("should check demographic read privilege with demographic number for getMedicationsByDemographicNo")
        void shouldCheckDemographicReadPrivilege_forGetMedicationsByDemographicNo() {
            // Given
            when(mockDrugDao.findByDemographicId(TEST_DEMO_NO, false)).thenReturn(Collections.emptyList());

            // When
            prescriptionManager.getMedicationsByDemographicNo(mockLoggedInInfo, TEST_DEMO_NO, false);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_demographic", "r", TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should check rx write privilege for createNewPrescription")
        void shouldCheckRxWritePrivilege_forCreateNewPrescription() {
            // Given
            denyPrivilege("_rx", "w");

            // When / Then
            assertThatThrownBy(() ->
                prescriptionManager.createNewPrescription(mockLoggedInInfo, List.of(createTestDrug()), TEST_DEMO_NO)
            ).isInstanceOf(AccessDeniedException.class);

            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "w", TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should not invoke DAO when demographic read denied for getMedicationsByDemographicNo")
        void shouldNotInvokeDao_whenDemographicReadDeniedForMedications() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            prescriptionManager.getMedicationsByDemographicNo(mockLoggedInInfo, TEST_DEMO_NO, false);

            // Then
            verifyNoInteractions(mockDrugDao);
        }

        @Test
        @DisplayName("should not invoke DAO when demographic read denied for getActiveMedications")
        void shouldNotInvokeDao_whenDemographicReadDeniedForActiveMedications() {
            // Given
            denyPrivilege("_demographic", "r");

            // When
            prescriptionManager.getActiveMedications(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verifyNoInteractions(mockDrugDao);
        }
    }
}
