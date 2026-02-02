/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package ca.openosp.openo.managers;

import ca.openosp.openo.commn.Gender;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.utility.DemographicContactCreator;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchRequest;
import ca.openosp.openo.webserv.rest.to.model.DemographicSearchResult;

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
 * Unit tests for DemographicManager business logic.
 *
 * <p>This test class provides comprehensive coverage of the DemographicManagerImpl
 * service layer, testing business logic, validation, security checks, and DAO
 * interactions without requiring a database or Spring context.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Validation logic testing</li>
 *   <li>Security privilege verification</li>
 *   <li>Business rule enforcement</li>
 *   <li>Archive-before-update patterns</li>
 *   <li>Manager-DAO interaction patterns</li>
 *   <li>MRP resolution chain testing</li>
 * </ul>
 *
 * @since 2026-01-24
 * @see DemographicManagerImpl
 * @see DemographicUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DemographicManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("demographic")
public class DemographicManagerUnitTest extends DemographicUnitTestBase {

    // 17 Mock dependencies
    @Mock private DemographicDao mockDemographicDao;
    @Mock private DemographicExtDao mockDemographicExtDao;
    @Mock private DemographicCustDao mockDemographicCustDao;
    @Mock private DemographicContactDao mockDemographicContactDao;
    @Mock private DemographicArchiveDao mockDemographicArchiveDao;
    @Mock private DemographicExtArchiveDao mockDemographicExtArchiveDao;
    @Mock private DemographicCustArchiveDao mockDemographicCustArchiveDao;
    @Mock private DemographicMergedDao mockDemographicMergedDao;
    @Mock private AdmissionDao mockAdmissionDao;
    @Mock private AppManager mockAppManager;
    @Mock private ContactSpecialtyDao mockContactSpecialtyDao;
    @Mock private PatientConsentManager mockPatientConsentManager;
    @Mock private ConsentDao mockConsentDao;
    @Mock private ProgramManager2 mockProgramManager2;
    @Mock private ProviderManager2 mockProviderManager;
    @Mock private AppointmentManager mockAppointmentManager;

    /** The DemographicManagerImpl instance under test. */
    private DemographicManagerImpl manager;

    /** Static mock for LogAction to prevent actual logging during tests. */
    private MockedStatic<LogAction> logActionMock;

    /** Static mock for DemographicContactCreator to avoid static initialization issues. */
    private MockedStatic<DemographicContactCreator> demographicContactCreatorMock;

    /**
     * Sets up the test environment before each test method.
     *
     * <p>This method performs the following setup:</p>
     * <ol>
     *   <li>Registers mocks with SpringUtils for static bean lookups</li>
     *   <li>Mocks static classes (LogAction, DemographicContactCreator) to prevent side effects</li>
     *   <li>Configures default security behavior (privileges granted)</li>
     *   <li>Creates the manager instance and injects all 17 dependencies via reflection</li>
     * </ol>
     *
     * <p><b>Important:</b> Mock registration order matters - SpringUtils mocks must be
     * registered BEFORE static class mocking to prevent NoClassDefFoundError during
     * static initializer execution.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils BEFORE mocking any static classes
        // This order is critical - static initializers may call SpringUtils.getBean()
        registerMock(DemographicDao.class, mockDemographicDao);
        registerMock(DemographicExtDao.class, mockDemographicExtDao);
        registerMock(DemographicCustDao.class, mockDemographicCustDao);
        registerMock(DemographicContactDao.class, mockDemographicContactDao);
        registerMock(ProviderManager2.class, mockProviderManager);

        // Register OscarLogDao mock to satisfy LogAction static initialization
        registerMock(ca.openosp.openo.commn.dao.OscarLogDao.class,
                    createAndRegisterMock(ca.openosp.openo.commn.dao.OscarLogDao.class));

        // Register mocks for DemographicContactCreator static initialization
        registerMock(ProfessionalSpecialistDao.class, createAndRegisterMock(ProfessionalSpecialistDao.class));

        // Mock DemographicContactCreator to prevent static initialization issues
        demographicContactCreatorMock = mockStatic(DemographicContactCreator.class);
        demographicContactCreatorMock.when(() -> DemographicContactCreator.addContactDetailsToDemographicContact(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        demographicContactCreatorMock.when(() -> DemographicContactCreator.addContactDetailsToDemographicContact(any(DemographicContact.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Now we can safely mock LogAction
        logActionMock = mockStatic(LogAction.class);

        // Security manager returns true for all privilege checks by default
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt()))
            .thenReturn(true);

        // Create manager instance
        manager = new DemographicManagerImpl();

        // Inject all 17 dependencies using reflection
        injectDependency(manager, "demographicDao", mockDemographicDao);
        injectDependency(manager, "demographicExtDao", mockDemographicExtDao);
        injectDependency(manager, "demographicCustDao", mockDemographicCustDao);
        injectDependency(manager, "demographicContactDao", mockDemographicContactDao);
        injectDependency(manager, "demographicArchiveDao", mockDemographicArchiveDao);
        injectDependency(manager, "demographicExtArchiveDao", mockDemographicExtArchiveDao);
        injectDependency(manager, "demographicCustArchiveDao", mockDemographicCustArchiveDao);
        injectDependency(manager, "demographicMergedDao", mockDemographicMergedDao);
        injectDependency(manager, "admissionDao", mockAdmissionDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(manager, "appManager", mockAppManager);
        injectDependency(manager, "contactSpecialtyDao", mockContactSpecialtyDao);
        injectDependency(manager, "patientConsentManager", mockPatientConsentManager);
        injectDependency(manager, "consentDao", mockConsentDao);
        injectDependency(manager, "programManager2", mockProgramManager2);
        injectDependency(manager, "providerManager", mockProviderManager);
        injectDependency(manager, "appointmentManager", mockAppointmentManager);
    }

    /**
     * Cleans up static mocks after each test to prevent test pollution.
     *
     * <p>MockedStatic instances must be closed to release the static mock
     * and restore original behavior. Failure to close can cause subsequent
     * tests to fail or behave unexpectedly.</p>
     */
    @AfterEach
    void tearDown() {
        if (demographicContactCreatorMock != null) {
            demographicContactCreatorMock.close();
        }
        if (logActionMock != null) {
            logActionMock.close();
        }
    }

    // ==================== SECURITY TESTS ====================

    /**
     * Tests for security and privilege checking functionality.
     *
     * <p>These tests verify that DemographicManager properly enforces security
     * constraints via SecurityInfoManager. Each operation should check for
     * appropriate privileges (read, write, update) before proceeding.</p>
     *
     * <p><b>Coverage includes:</b></p>
     * <ul>
     *   <li>Read privilege denial for getDemographic</li>
     *   <li>Write privilege denial for createDemographic, deleteDemographic</li>
     *   <li>Update privilege denial for updateDemographic</li>
     *   <li>Write privilege denial for createExtension, deleteExtension</li>
     *   <li>Update privilege denial for updateExtension</li>
     *   <li>Direct checkPrivilege method testing (both overloads)</li>
     * </ul>
     *
     * @see SecurityInfoManager#hasPrivilege
     */
    @Nested
    @DisplayName("Security and Privilege Checks")
    @Tag("security")
    class SecurityTests {

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied for getDemographic")
        void shouldThrowException_whenReadPrivilegeDeniedForGetDemographic() {
            // Reset and re-configure to return false for this specific test
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt()))
                .thenReturn(false);

            assertThatThrownBy(() -> manager.getDemographic(mockLoggedInInfo, TEST_DEMO_NO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for createDemographic")
        void shouldThrowException_whenWritePrivilegeDeniedForCreate() {
            // Reset and re-configure to return false for this specific test
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            Demographic demographic = createTestDemographic();

            assertThatThrownBy(() -> manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when update privilege denied for updateDemographic")
        void shouldThrowException_whenUpdatePrivilegeDeniedForUpdate() {
            // Reset and re-configure to return false for this specific test
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            Demographic demographic = createTestDemographicWithId(TEST_DEMO_NO);

            assertThatThrownBy(() -> manager.updateDemographic(mockLoggedInInfo, demographic))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should verify privilege check with demographicNo parameter")
        void shouldVerifyPrivilegeCheck_withDemographicNoParameter() {
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(createTestDemographic());

            manager.getDemographic(mockLoggedInInfo, TEST_DEMO_NO);

            verify(mockSecurityInfoManager).hasPrivilege(eq(mockLoggedInInfo), eq("_demographic"), eq("r"), anyInt());
        }

        @Test
        @DisplayName("should throw exception from checkPrivilege when privilege denied")
        void shouldThrowExceptionFromCheckPrivilege_whenPrivilegeDenied() {
            // Test the public checkPrivilege(LoggedInInfo, String) method directly
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            // checkPrivilege with write privilege should throw when denied
            assertThatThrownBy(() -> manager.checkPrivilege(mockLoggedInInfo, "w"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw exception from checkPrivilege with demographicNo when privilege denied")
        void shouldThrowExceptionFromCheckPrivilegeWithDemoNo_whenPrivilegeDenied() {
            // Test the overloaded checkPrivilege(LoggedInInfo, String, Integer) method
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt()))
                .thenReturn(false);

            // checkPrivilege with demographicNo parameter should throw when denied
            assertThatThrownBy(() -> manager.checkPrivilege(mockLoggedInInfo, "r", TEST_DEMO_NO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for deleteDemographic")
        void shouldThrowException_whenWritePrivilegeDeniedForDelete() {
            // deleteDemographic requires WRITE privilege (SecurityInfoManager.WRITE)
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            Demographic demographic = createTestDemographicWithId(TEST_DEMO_NO);

            // Should throw before any deletion logic executes
            assertThatThrownBy(() -> manager.deleteDemographic(mockLoggedInInfo, demographic))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for createExtension")
        void shouldThrowException_whenWritePrivilegeDeniedForCreateExtension() {
            // createExtension requires WRITE privilege (SecurityInfoManager.WRITE)
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            DemographicExt ext = createTestDemographicExt(TEST_DEMO_NO, "testKey", "testValue");

            // Should throw before any persistence occurs
            assertThatThrownBy(() -> manager.createExtension(mockLoggedInInfo, ext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when update privilege denied for updateExtension")
        void shouldThrowException_whenUpdatePrivilegeDeniedForUpdateExtension() {
            // updateExtension requires UPDATE privilege (SecurityInfoManager.UPDATE)
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            DemographicExt ext = createTestDemographicExtWithId(1, TEST_DEMO_NO, "testKey", "testValue");

            // Should throw before any archiving or update occurs
            assertThatThrownBy(() -> manager.updateExtension(mockLoggedInInfo, ext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied for deleteExtension")
        void shouldThrowException_whenWritePrivilegeDeniedForDeleteExtension() {
            // deleteExtension requires WRITE privilege (SecurityInfoManager.WRITE)
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenReturn(false);

            DemographicExt ext = createTestDemographicExtWithId(1, TEST_DEMO_NO, "testKey", "testValue");

            // Should throw before any archiving or deletion occurs
            assertThatThrownBy(() -> manager.deleteExtension(mockLoggedInInfo, ext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }
    }

    // ==================== CORE GET OPERATIONS ====================

    /**
     * Tests for core demographic retrieval operations.
     *
     * <p>These tests cover the fundamental get/retrieve operations including:</p>
     * <ul>
     *   <li>getDemographic by ID (Integer and String)</li>
     *   <li>getDemographicWithExt (demographic with extensions)</li>
     *   <li>getDemographicFormattedName</li>
     *   <li>getDemographicEmail</li>
     * </ul>
     */
    @Nested
    @DisplayName("Core Get Operations")
    @Tag("read")
    class CoreGetOperationsTests {

        @Test
        @DisplayName("should return demographic when valid ID provided")
        void shouldReturnDemographic_whenValidIdProvided() {
            Demographic expected = createTestDemographic();
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(expected);

            Demographic result = manager.getDemographic(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
            assertThat(result.getDemographicNo()).isEqualTo(TEST_DEMO_NO);
            verify(mockDemographicDao).getDemographicById(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return null when demographic not found")
        void shouldReturnNull_whenDemographicNotFound() {
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(null);

            Demographic result = manager.getDemographic(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return demographic when valid string ID provided")
        void shouldReturnDemographic_whenValidStringIdProvided() {
            Demographic expected = createTestDemographic();
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(expected);

            Demographic result = manager.getDemographic(mockLoggedInInfo, String.valueOf(TEST_DEMO_NO));

            assertThat(result).isNotNull();
            assertThat(result.getDemographicNo()).isEqualTo(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return null when non-numeric string ID provided")
        void shouldReturnNull_whenNonNumericStringIdProvided() {
            Demographic result = manager.getDemographic(mockLoggedInInfo, "invalid");

            assertThat(result).isNull();
            verify(mockDemographicDao, never()).getDemographicById(any());
        }

        @Test
        @DisplayName("should return demographic with extensions when requested")
        void shouldReturnDemographicWithExtensions_whenRequested() {
            Demographic expected = createTestDemographic();
            List<DemographicExt> extensions = List.of(
                createTestDemographicExt(TEST_DEMO_NO, "key1", "value1"),
                createTestDemographicExt(TEST_DEMO_NO, "key2", "value2")
            );
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(expected);
            when(mockDemographicExtDao.getDemographicExtByDemographicNo(TEST_DEMO_NO)).thenReturn(extensions);

            Demographic result = manager.getDemographicWithExt(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
            assertThat(result.getExtras()).hasSize(2);
        }

        @Test
        @DisplayName("should return formatted name when demographic exists")
        void shouldReturnFormattedName_whenDemographicExists() {
            Demographic expected = createTestDemographic();
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(expected);

            String result = manager.getDemographicFormattedName(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEqualTo(TEST_LAST_NAME + ", " + TEST_FIRST_NAME);
        }

        @Test
        @DisplayName("should return null formatted name when demographic not found")
        void shouldReturnNullFormattedName_whenDemographicNotFound() {
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(null);

            String result = manager.getDemographicFormattedName(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return email when demographic exists")
        void shouldReturnEmail_whenDemographicExists() {
            Demographic expected = createTestDemographic();
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(expected);

            String result = manager.getDemographicEmail(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("should return null email when demographic not found")
        void shouldReturnNullEmail_whenDemographicNotFound() {
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(null);

            String result = manager.getDemographicEmail(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNull();
        }
    }

    // ==================== SEARCH OPERATIONS ====================

    /**
     * Tests for demographic search functionality.
     *
     * <p>These tests cover various search operations including:</p>
     * <ul>
     *   <li>Search by name (partial matching)</li>
     *   <li>Search by health card number (HIN)</li>
     *   <li>Search by multiple attributes</li>
     *   <li>Search patients with pagination</li>
     *   <li>Search by chart number</li>
     * </ul>
     */
    @Nested
    @DisplayName("Search Operations")
    @Tag("search")
    class SearchOperationsTests {

        @Test
        @DisplayName("should return demographics when searching by name")
        void shouldReturnDemographics_whenSearchingByName() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.searchDemographicByNameString("Doe", 0, 10)).thenReturn(expected);

            List<Demographic> result = manager.searchDemographicByName(mockLoggedInInfo, "Doe", 0, 10);

            assertThat(result).hasSize(1);
            verify(mockDemographicDao).searchDemographicByNameString("Doe", 0, 10);
        }

        @Test
        @DisplayName("should return empty list when no matches found by name")
        void shouldReturnEmptyList_whenNoMatchesFoundByName() {
            when(mockDemographicDao.searchDemographicByNameString("NonExistent", 0, 10))
                .thenReturn(Collections.emptyList());

            List<Demographic> result = manager.searchDemographicByName(mockLoggedInInfo, "NonExistent", 0, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return demographics by attributes with all parameters")
        void shouldReturnDemographics_whenSearchingByAttributes() {
            List<Demographic> expected = List.of(createTestDemographic());
            Calendar dob = Calendar.getInstance();
            dob.set(1980, Calendar.JANUARY, 15);

            when(mockDemographicDao.findByAttributes(
                eq(TEST_HIN), eq(TEST_FIRST_NAME), eq(TEST_LAST_NAME),
                eq(Gender.M), any(Calendar.class), eq("Toronto"), eq("ON"),
                eq(TEST_PHONE), eq(TEST_EMAIL), isNull(), eq(0), eq(10)
            )).thenReturn(expected);

            List<Demographic> result = manager.searchDemographicsByAttributes(
                mockLoggedInInfo, TEST_HIN, TEST_FIRST_NAME, TEST_LAST_NAME,
                Gender.M, dob, "Toronto", "ON", TEST_PHONE, TEST_EMAIL, null, 0, 10
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return demographics when searching generic string")
        void shouldReturnDemographics_whenSearchingGenericString() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.searchDemographic("John Doe")).thenReturn(expected);

            List<Demographic> result = manager.searchDemographic(mockLoggedInInfo, "John Doe");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw SecurityException when searchDemographic called without login")
        void shouldThrowSecurityException_whenSearchDemographicCalledWithoutLogin() {
            assertThatThrownBy(() -> manager.searchDemographic(null, "test"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("user not logged in");
        }

        @Test
        @DisplayName("should return demographics by health card number")
        void shouldReturnDemographics_whenSearchingByHealthCard() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.searchByHealthCard(TEST_HIN)).thenReturn(expected);

            List<Demographic> result = manager.searchByHealthCard(mockLoggedInInfo, TEST_HIN);

            assertThat(result).hasSize(1);
            verify(mockDemographicDao).searchByHealthCard(TEST_HIN);
        }

        @Test
        @DisplayName("should return active demographics by health card and type")
        void shouldReturnActiveDemographics_whenSearchingByHealthCardAndType() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.getActiveDemosByHealthCardNo(TEST_HIN, "ON")).thenReturn(expected);

            List<Demographic> result = manager.getActiveDemosByHealthCardNo(mockLoggedInInfo, TEST_HIN, "ON");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return demographics by chart number")
        void shouldReturnDemographics_whenSearchingByChartNo() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.getClientsByChartNo("CHART001")).thenReturn(expected);

            List<Demographic> result = manager.getDemosByChartNo(mockLoggedInInfo, "CHART001");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return demographic by name phone and email")
        void shouldReturnDemographic_whenSearchingByNamePhoneEmail() {
            Demographic expected = createTestDemographic();
            when(mockDemographicDao.getDemographicByNamePhoneEmail(
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PHONE, null, TEST_EMAIL
            )).thenReturn(expected);

            Demographic result = manager.getDemographicByNamePhoneEmail(
                mockLoggedInInfo, TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PHONE, null, TEST_EMAIL
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return demographics by last first and DOB")
        void shouldReturnDemographics_whenSearchingByLastFirstDOB() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.getDemographicWithLastFirstDOB(
                TEST_LAST_NAME, TEST_FIRST_NAME, "1980", "01", "15"
            )).thenReturn(expected);

            List<Demographic> result = manager.getDemographicWithLastFirstDOB(
                mockLoggedInInfo, TEST_LAST_NAME, TEST_FIRST_NAME, "1980", "01", "15"
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return search results from searchPatients")
        void shouldReturnSearchResults_fromSearchPatients() {
            DemographicSearchRequest request = new DemographicSearchRequest();
            DemographicSearchResult searchResult = new DemographicSearchResult();
            searchResult.setDemographicNo(TEST_DEMO_NO);
            List<DemographicSearchResult> expected = List.of(searchResult);

            when(mockDemographicDao.searchPatients(mockLoggedInInfo, request, 0, 10)).thenReturn(expected);

            List<DemographicSearchResult> result = manager.searchPatients(mockLoggedInInfo, request, 0, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return count from searchPatientsCount")
        void shouldReturnCount_fromSearchPatientsCount() {
            DemographicSearchRequest request = new DemographicSearchRequest();
            when(mockDemographicDao.searchPatientCount(mockLoggedInInfo, request)).thenReturn(42);

            int result = manager.searchPatientsCount(mockLoggedInInfo, request);

            assertThat(result).isEqualTo(42);
        }
    }

    // ==================== EXTENSION OPERATIONS ====================

    /**
     * Tests for DemographicExt (extension) operations.
     *
     * <p>DemographicExt stores additional key-value pairs associated with demographics.
     * These tests cover:</p>
     * <ul>
     *   <li>Retrieving extensions by demographic number</li>
     *   <li>Getting specific extension by key</li>
     *   <li>Creating new extensions</li>
     *   <li>Updating extensions (with archive-before-update pattern)</li>
     *   <li>Deleting extensions</li>
     *   <li>Direct archiving of extensions</li>
     * </ul>
     */
    @Nested
    @DisplayName("Demographic Extension Operations")
    @Tag("extension")
    class ExtensionOperationsTests {

        @Test
        @DisplayName("should return all extensions for demographic")
        void shouldReturnAllExtensions_forDemographic() {
            List<DemographicExt> expected = List.of(
                createTestDemographicExt(TEST_DEMO_NO, "key1", "value1"),
                createTestDemographicExt(TEST_DEMO_NO, "key2", "value2")
            );
            when(mockDemographicExtDao.getDemographicExtByDemographicNo(TEST_DEMO_NO)).thenReturn(expected);

            List<DemographicExt> result = manager.getDemographicExts(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return specific extension by key string")
        void shouldReturnSpecificExtension_byKeyString() {
            DemographicExt expected = createTestDemographicExt(TEST_DEMO_NO, "wPhoneExt", "123");
            when(mockDemographicExtDao.getDemographicExt(TEST_DEMO_NO, "wPhoneExt")).thenReturn(expected);

            DemographicExt result = manager.getDemographicExt(mockLoggedInInfo, TEST_DEMO_NO, "wPhoneExt");

            assertThat(result).isNotNull();
            assertThat(result.getKey()).isEqualTo("wPhoneExt");
        }

        @Test
        @DisplayName("should return specific extension by enum key")
        void shouldReturnSpecificExtension_byEnumKey() {
            DemographicExt expected = createTestDemographicExt(TEST_DEMO_NO, "wPhoneExt", "456");
            when(mockDemographicExtDao.getDemographicExt(TEST_DEMO_NO, "wPhoneExt")).thenReturn(expected);

            DemographicExt result = manager.getDemographicExt(
                mockLoggedInInfo, TEST_DEMO_NO, DemographicExt.DemographicProperty.wPhoneExt
            );

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should create extension and persist")
        void shouldCreateExtension_andPersist() {
            DemographicExt ext = createTestDemographicExt(TEST_DEMO_NO, "newKey", "newValue");

            manager.createExtension(mockLoggedInInfo, ext);

            verify(mockDemographicExtDao).saveEntity(ext);
        }

        @Test
        @DisplayName("should update extension with archiving previous value")
        void shouldUpdateExtension_withArchivingPreviousValue() {
            DemographicExt existing = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "oldValue");
            DemographicExt updated = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "newValue");

            // Use any() matcher since find() can take Object or int
            when(mockDemographicExtDao.find(any())).thenReturn(existing);

            manager.updateExtension(mockLoggedInInfo, updated);

            verify(mockDemographicExtArchiveDao).archiveDemographicExt(existing);
            verify(mockDemographicExtDao).saveEntity(updated);
        }

        @Test
        @DisplayName("should not archive when extension value unchanged")
        void shouldNotArchive_whenExtensionValueUnchanged() {
            DemographicExt existing = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "sameValue");
            DemographicExt updated = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "sameValue");

            // Use any() matcher since find() can take Object or int
            when(mockDemographicExtDao.find(any())).thenReturn(existing);

            manager.updateExtension(mockLoggedInInfo, updated);

            verify(mockDemographicExtArchiveDao, never()).archiveDemographicExt(any());
            verify(mockDemographicExtDao).saveEntity(updated);
        }

        @Test
        @DisplayName("should delete extension with archiving")
        void shouldDeleteExtension_withArchiving() {
            // Create the ext to delete (with a different value than what's in DB)
            DemographicExt extToDelete = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "newValue");
            // Create the existing ext in DB with original value (different to trigger archiving)
            DemographicExt existingExt = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "originalValue");

            // Use any() matcher since find() can take Object or int
            when(mockDemographicExtDao.find(any())).thenReturn(existingExt);

            manager.deleteExtension(mockLoggedInInfo, extToDelete);

            verify(mockDemographicExtArchiveDao).archiveDemographicExt(existingExt);
            verify(mockDemographicExtDao).removeDemographicExt(1);
        }

        @Test
        @DisplayName("should archive extension directly when values differ")
        void shouldArchiveExtension_directly() {
            // Extension to archive with new value
            DemographicExt ext = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "newValue");
            // Previous extension in DB with old value
            DemographicExt prevExt = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "oldValue");

            when(mockDemographicExtDao.find(any())).thenReturn(prevExt);

            manager.archiveExtension(ext);

            verify(mockDemographicExtArchiveDao).archiveDemographicExt(prevExt);
        }
    }

    // ==================== CREATE/UPDATE/DELETE OPERATIONS ====================

    /**
     * Tests for demographic CRUD (Create, Read, Update, Delete) operations.
     *
     * <p>These tests verify the core lifecycle operations for demographics:</p>
     * <ul>
     *   <li>Creating demographics with automatic admission creation</li>
     *   <li>Setting default patient status on creation</li>
     *   <li>Updating demographics with archive-before-update pattern</li>
     *   <li>Deleting demographics (soft delete via status change)</li>
     *   <li>Adding demographics without admission</li>
     * </ul>
     *
     * <p><b>Archive Pattern:</b> The manager archives the previous record before
     * updates to maintain audit history for regulatory compliance.</p>
     */
    @Nested
    @DisplayName("Create, Update, and Delete Operations")
    @Tag("crud")
    class CrudOperationsTests {

        @Test
        @DisplayName("should create demographic with admission")
        void shouldCreateDemographic_withAdmission() {
            Demographic demographic = createTestDemographic();
            demographic.setDemographicNo(null);

            manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID);

            verify(mockDemographicDao).save(demographic);
            verify(mockAdmissionDao).saveAdmission(any(Admission.class));
        }

        @Test
        @DisplayName("should set patient status to AC when creating")
        void shouldSetPatientStatusToAC_whenCreating() {
            Demographic demographic = createTestDemographic();
            demographic.setPatientStatus(null);

            manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID);

            assertThat(demographic.getPatientStatus()).isEqualTo(Demographic.PatientStatus.AC.name());
        }

        @Test
        @DisplayName("should set family doctor default when creating")
        void shouldSetFamilyDoctorDefault_whenCreating() {
            Demographic demographic = createTestDemographic();

            manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID);

            assertThat(demographic.getFamilyDoctor()).isEqualTo("<rdohip></rdohip><rd></rd>");
        }

        @Test
        @DisplayName("should create extensions when demographic has extras")
        void shouldCreateExtensions_whenDemographicHasExtras() {
            Demographic demographic = createTestDemographic();
            DemographicExt[] extras = {
                createTestDemographicExt(null, "key1", "value1"),
                createTestDemographicExt(null, "key2", "value2")
            };
            demographic.setExtras(extras);

            manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID);

            verify(mockDemographicExtDao, times(2)).saveEntity(any(DemographicExt.class));
        }

        @Test
        @DisplayName("should throw exception when birth date invalid")
        void shouldThrowException_whenBirthDateInvalid() {
            Demographic demographic = createTestDemographic();
            demographic.setYearOfBirth("invalid");
            demographic.setMonthOfBirth("00");
            demographic.setDateOfBirth("00");

            assertThatThrownBy(() -> manager.createDemographic(mockLoggedInInfo, demographic, TEST_PROGRAM_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should update demographic with archiving previous record")
        void shouldUpdateDemographic_withArchivingPreviousRecord() {
            Demographic previous = createTestDemographicWithId(TEST_DEMO_NO);
            Demographic updated = createTestDemographicWithId(TEST_DEMO_NO);
            updated.setFirstName("UpdatedName");

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(previous);

            manager.updateDemographic(mockLoggedInInfo, updated);

            verify(mockDemographicArchiveDao).archiveRecord(previous);
            verify(mockDemographicDao).save(updated);
        }

        @Test
        @DisplayName("should retain subRecord when updating demographic")
        void shouldRetainSubRecord_whenUpdatingDemographic() {
            Demographic previous = createTestDemographicWithId(TEST_DEMO_NO);
            Set<Integer> subRecords = new HashSet<>(Set.of(100, 101));
            previous.setSubRecord(subRecords);

            Demographic updated = createTestDemographicWithId(TEST_DEMO_NO);

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(previous);

            manager.updateDemographic(mockLoggedInInfo, updated);

            assertThat(updated.getSubRecord()).isEqualTo(subRecords);
        }

        @Test
        @DisplayName("should set lastUpdateUser when updating demographic")
        void shouldSetLastUpdateUser_whenUpdatingDemographic() {
            Demographic previous = createTestDemographicWithId(TEST_DEMO_NO);
            Demographic updated = createTestDemographicWithId(TEST_DEMO_NO);

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(previous);

            manager.updateDemographic(mockLoggedInInfo, updated);

            assertThat(updated.getLastUpdateUser()).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("should add demographic without admission")
        void shouldAddDemographic_withoutAdmission() {
            Demographic demographic = createTestDemographic();

            manager.addDemographic(mockLoggedInInfo, demographic);

            verify(mockDemographicDao).save(demographic);
            verify(mockAdmissionDao, never()).saveAdmission(any());
        }

        @Test
        @DisplayName("should delete demographic by setting status to DE")
        void shouldDeleteDemographic_bySettingStatusToDE() {
            Demographic demographic = createTestDemographicWithId(TEST_DEMO_NO);
            when(mockDemographicExtDao.getDemographicExtByDemographicNo(TEST_DEMO_NO))
                .thenReturn(Collections.emptyList());

            manager.deleteDemographic(mockLoggedInInfo, demographic);

            verify(mockDemographicArchiveDao).archiveRecord(demographic);
            assertThat(demographic.getPatientStatus()).isEqualTo(Demographic.PatientStatus.DE.name());
            verify(mockDemographicDao).save(demographic);
        }

        @Test
        @DisplayName("should delete all extensions when deleting demographic")
        void shouldDeleteAllExtensions_whenDeletingDemographic() {
            Demographic demographic = createTestDemographicWithId(TEST_DEMO_NO);
            DemographicExt ext1 = createTestDemographicExtWithId(1, TEST_DEMO_NO, "key1", "value1");
            DemographicExt ext2 = createTestDemographicExtWithId(2, TEST_DEMO_NO, "key2", "value2");
            List<DemographicExt> extensions = List.of(ext1, ext2);

            when(mockDemographicExtDao.getDemographicExtByDemographicNo(TEST_DEMO_NO)).thenReturn(extensions);
            // Use any() matcher since find() can take Object or int
            when(mockDemographicExtDao.find(any()))
                .thenAnswer(invocation -> {
                    Object id = invocation.getArgument(0);
                    if (Integer.valueOf(1).equals(id)) return ext1;
                    if (Integer.valueOf(2).equals(id)) return ext2;
                    return null;
                });

            manager.deleteDemographic(mockLoggedInInfo, demographic);

            verify(mockDemographicExtDao, times(2)).removeDemographicExt(anyInt());
        }

        @Test
        @DisplayName("should throw NullPointerException when updating non-existent demographic")
        void shouldThrowNullPointerException_whenUpdatingNonExistentDemographic() {
            Demographic demographic = createTestDemographicWithId(TEST_DEMO_NO);

            // Return null to simulate demographic not found
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(null);

            // The implementation calls prevDemo.getSubRecord() which will NPE if prevDemo is null
            assertThatThrownBy(() -> manager.updateDemographic(mockLoggedInInfo, demographic))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== DEMOGRAPHIC CUST OPERATIONS ====================

    /**
     * Tests for DemographicCust operations.
     *
     * <p>DemographicCust stores additional custom fields for a demographic such as
     * nurse, resident, midwife assignments, alerts, and notes. These tests cover:</p>
     * <ul>
     *   <li>Retrieving DemographicCust by demographic number</li>
     *   <li>Creating new DemographicCust records</li>
     *   <li>Updating with archive-before-update pattern</li>
     *   <li>Handling cases where no changes are made (no archiving)</li>
     * </ul>
     */
    @Nested
    @DisplayName("Demographic Cust Operations")
    @Tag("cust")
    class DemographicCustOperationsTests {

        @Test
        @DisplayName("should return DemographicCust when found")
        void shouldReturnDemographicCust_whenFound() {
            DemographicCust expected = createTestDemographicCust(TEST_DEMO_NO);
            when(mockDemographicCustDao.find(TEST_DEMO_NO)).thenReturn(expected);

            DemographicCust result = manager.getDemographicCust(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when DemographicCust not found")
        void shouldReturnNull_whenDemographicCustNotFound() {
            when(mockDemographicCustDao.find(TEST_DEMO_NO)).thenReturn(null);

            DemographicCust result = manager.getDemographicCust(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should create new DemographicCust when none exists")
        void shouldCreateNewDemographicCust_whenNoneExists() {
            DemographicCust newCust = createTestDemographicCust(TEST_DEMO_NO);
            when(mockDemographicCustDao.find(TEST_DEMO_NO)).thenReturn(null);

            manager.createUpdateDemographicCust(mockLoggedInInfo, newCust);

            verify(mockDemographicCustDao).merge(newCust);
            verify(mockDemographicCustArchiveDao, never()).archiveDemographicCust(any());
        }

        @Test
        @DisplayName("should archive previous DemographicCust when updating with changes")
        void shouldArchivePreviousDemographicCust_whenUpdatingWithChanges() {
            DemographicCust existing = createTestDemographicCust(TEST_DEMO_NO);
            DemographicCust updated = createTestDemographicCust(TEST_DEMO_NO);
            updated.setAlert("new alert");

            when(mockDemographicCustDao.find(TEST_DEMO_NO)).thenReturn(existing);

            manager.createUpdateDemographicCust(mockLoggedInInfo, updated);

            verify(mockDemographicCustArchiveDao).archiveDemographicCust(existing);
            verify(mockDemographicCustDao).merge(updated);
        }

        @Test
        @DisplayName("should not archive when DemographicCust unchanged")
        void shouldNotArchive_whenDemographicCustUnchanged() {
            DemographicCust existing = createTestDemographicCust(TEST_DEMO_NO);
            DemographicCust updated = createTestDemographicCust(TEST_DEMO_NO);

            when(mockDemographicCustDao.find(TEST_DEMO_NO)).thenReturn(existing);

            manager.createUpdateDemographicCust(mockLoggedInInfo, updated);

            verify(mockDemographicCustArchiveDao, never()).archiveDemographicCust(any());
        }
    }

    // ==================== CONTACT OPERATIONS ====================

    /**
     * Tests for DemographicContact operations.
     *
     * <p>DemographicContact represents relationships between demographics and various
     * contact types (providers, other demographics, specialists). Tests cover:</p>
     * <ul>
     *   <li>Retrieving contacts by category (personal, professional)</li>
     *   <li>Finding active contacts</li>
     *   <li>Creating and updating contacts</li>
     *   <li>Finding Substitute Decision Makers (SDM)</li>
     *   <li>Emergency contact retrieval</li>
     * </ul>
     */
    @Nested
    @DisplayName("Demographic Contact Operations")
    @Tag("contact")
    class ContactOperationsTests {

        @Test
        @DisplayName("should return contacts by demographic and category")
        void shouldReturnContacts_byDemographicAndCategory() {
            List<DemographicContact> expected = List.of(
                createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0)
            );
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(expected);

            List<DemographicContact> result = manager.getDemographicContacts(
                mockLoggedInInfo, TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return active contacts by demographic only")
        void shouldReturnActiveContacts_byDemographicOnly() {
            List<DemographicContact> expected = List.of(
                createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0)
            );
            when(mockDemographicContactDao.findActiveByDemographicNo(TEST_DEMO_NO)).thenReturn(expected);

            List<DemographicContact> result = manager.getDemographicContacts(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should create or update demographic contact")
        void shouldCreateOrUpdateDemographicContact() {
            DemographicContact contact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);

            manager.createUpdateDemographicContact(mockLoggedInInfo, contact);

            verify(mockDemographicContactDao).merge(contact);
        }

        @Test
        @DisplayName("should return SDM contacts by demographic")
        void shouldReturnSDMContacts_byDemographic() {
            DemographicContact sdmContact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL, 1);
            sdmContact.setSdm("true");
            List<DemographicContact> expected = List.of(sdmContact);

            when(mockDemographicContactDao.findSDMByDemographicNo(TEST_DEMO_NO)).thenReturn(expected);

            List<DemographicContact> result = manager.findSDMByDemographicNo(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return null when demographicNo is null for getHealthCareTeam")
        void shouldReturnNull_whenDemographicNoIsNullForGetHealthCareTeam() {
            List<DemographicContact> result = manager.getHealthCareTeam(mockLoggedInInfo, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return health care team contacts")
        void shouldReturnHealthCareTeamContacts() {
            List<DemographicContact> expected = List.of(
                createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0)
            );
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(expected);

            List<DemographicContact> result = manager.getHealthCareTeam(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return null when demographicNo is null for getPersonalEmergencyContacts")
        void shouldReturnNull_whenDemographicNoIsNullForGetPersonalEmergencyContacts() {
            List<DemographicContact> result = manager.getPersonalEmergencyContacts(mockLoggedInInfo, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return personal emergency contacts")
        void shouldReturnPersonalEmergencyContacts() {
            List<DemographicContact> expected = List.of(
                createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL, 1)
            );
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL))
                .thenReturn(expected);

            List<DemographicContact> result = manager.getPersonalEmergencyContacts(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return emergency contacts only when marked as EC")
        void shouldReturnEmergencyContacts_onlyWhenMarkedAsEC() {
            DemographicContact ecContact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL, 1);
            ecContact.setEc("true");
            DemographicContact nonEcContact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL, 1);
            nonEcContact.setEc("false");

            List<DemographicContact> contacts = List.of(ecContact, nonEcContact);
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL))
                .thenReturn(contacts);

            List<DemographicContact> result = manager.getEmergencyContacts(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }
    }

    // ==================== PROVIDER/MRP OPERATIONS ====================

    /**
     * Tests for Provider and Most Responsible Provider (MRP) operations.
     *
     * <p>The MRP is the primary healthcare provider responsible for a patient.
     * The resolution chain for finding MRP is:</p>
     * <ol>
     *   <li>demographic.providerNo (primary)</li>
     *   <li>Health care team contact marked as MRP</li>
     *   <li>Most responsible provider from health care team</li>
     * </ol>
     *
     * <p>Tests also cover listing providers for a demographic and provider caseload queries.</p>
     */
    @Nested
    @DisplayName("Provider and MRP Operations")
    @Tag("provider")
    class ProviderMrpOperationsTests {

        @Test
        @DisplayName("should return MRP from demographic providerNo")
        void shouldReturnMRP_fromDemographicProviderNo() {
            Demographic demographic = createTestDemographic();
            Provider expectedMrp = createTestProvider();

            when(mockProviderManager.getProvider(mockLoggedInInfo, TEST_PROVIDER)).thenReturn(expectedMrp);

            Provider result = manager.getMRP(mockLoggedInInfo, demographic);

            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(TEST_PROVIDER);
            assertThat(demographic.getMrp()).isEqualTo(expectedMrp);
        }

        @Test
        @DisplayName("should fallback to health care team when providerNo is null")
        void shouldFallbackToHealthCareTeam_whenProviderNoIsNull() {
            Demographic demographic = createTestDemographic();
            demographic.setProviderNo(null);

            DemographicContact contact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            contact.setMrp(true);
            contact.setContactId("999991");
            List<DemographicContact> contacts = List.of(contact);

            Provider expectedMrp = createTestProviderWithNo("999991");

            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(contacts);
            when(mockProviderManager.getProvider(mockLoggedInInfo, "999991")).thenReturn(expectedMrp);

            Provider result = manager.getMRP(mockLoggedInInfo, demographic);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return MRP by demographic ID")
        void shouldReturnMRP_byDemographicId() {
            Demographic demographic = createTestDemographic();
            Provider expectedMrp = createTestProvider();

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(demographic);
            when(mockProviderManager.getProvider(mockLoggedInInfo, TEST_PROVIDER)).thenReturn(expectedMrp);

            Provider result = manager.getMRP(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return most responsible providers from health care team")
        void shouldReturnMostResponsibleProviders_fromHealthCareTeam() {
            DemographicContact contact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            contact.setContactId(TEST_PROVIDER);
            List<DemographicContact> contacts = List.of(contact);

            Provider provider = createTestProvider();

            when(mockDemographicContactDao.findAllByDemographicNoAndCategoryAndType(TEST_DEMO_NO, "professional", 0))
                .thenReturn(contacts);
            when(mockProviderManager.getProvider(mockLoggedInInfo, TEST_PROVIDER)).thenReturn(provider);

            List<Provider> result = manager.getDemographicMostResponsibleProviders(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no providers found")
        void shouldReturnEmptyList_whenNoProvidersFound() {
            when(mockDemographicContactDao.findAllByDemographicNoAndCategoryAndType(TEST_DEMO_NO, "professional", 0))
                .thenReturn(Collections.emptyList());

            List<Provider> result = manager.getDemographicMostResponsibleProviders(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return demographics by provider")
        void shouldReturnDemographics_byProvider() {
            Provider provider = createTestProvider();
            List<Demographic> expected = List.of(createTestDemographic());

            when(mockDemographicDao.getDemographicByProvider(TEST_PROVIDER, true)).thenReturn(expected);

            List<Demographic> result = manager.getDemographicsByProvider(mockLoggedInInfo, provider);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when provider is null for getDemographicsNameRangeByProvider")
        void shouldReturnEmptyList_whenProviderIsNull() {
            List<Demographic> result = manager.getDemographicsNameRangeByProvider(mockLoggedInInfo, null, "^[A-M]");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter demographics by lastname regex")
        void shouldFilterDemographics_byLastnameRegex() {
            Provider provider = createTestProvider();
            Demographic matchingDemo = createTestDemographic();
            matchingDemo.setLastName("ADAMS");
            Demographic nonMatchingDemo = createTestDemographic();
            nonMatchingDemo.setLastName("SMITH");

            List<Demographic> allDemos = List.of(matchingDemo, nonMatchingDemo);
            when(mockDemographicDao.getDemographicByProvider(TEST_PROVIDER)).thenReturn(allDemos);

            List<Demographic> result = manager.getDemographicsNameRangeByProvider(mockLoggedInInfo, provider, "^[A-M]");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLastName()).isEqualTo("ADAMS");
        }
    }

    // ==================== MERGE OPERATIONS ====================

    /**
     * Tests for demographic merge and unmerge operations.
     *
     * <p>Merging allows combining duplicate patient records while maintaining
     * data integrity. The parent record becomes the primary, and children
     * are marked as merged (soft-linked). Tests cover:</p>
     * <ul>
     *   <li>Merging multiple children to a parent</li>
     *   <li>Unmerging (restoring) children from parent</li>
     *   <li>Retrieving merged demographic information</li>
     *   <li>Getting merged demographic IDs</li>
     * </ul>
     */
    @Nested
    @DisplayName("Merge Operations")
    @Tag("merge")
    class MergeOperationsTests {

        @Test
        @DisplayName("should merge multiple children to parent")
        void shouldMergeMultipleChildren_toParent() {
            Integer parentId = 1000;
            List<Integer> children = List.of(1001, 1002, 1003);

            manager.mergeDemographics(mockLoggedInInfo, parentId, children);

            verify(mockDemographicMergedDao, times(3)).persist(any(DemographicMerged.class));
        }

        @Test
        @DisplayName("should unmerge children from parent")
        void shouldUnmergeChildren_fromParent() {
            Integer parentId = 1000;
            Integer childId = 1001;
            List<Integer> children = List.of(childId);

            DemographicMerged mergeRecord = createTestDemographicMergedWithId(1, childId, parentId);
            when(mockDemographicMergedDao.findByParentAndChildIds(parentId, childId))
                .thenReturn(List.of(mergeRecord));

            manager.unmergeDemographics(mockLoggedInInfo, parentId, children);

            assertThat(mergeRecord.getDeleted()).isEqualTo(1);
            verify(mockDemographicMergedDao).merge(mergeRecord);
        }

        @Test
        @DisplayName("should throw exception when unmerging non-existent merge")
        void shouldThrowException_whenUnmergingNonExistentMerge() {
            Integer parentId = 1000;
            List<Integer> children = List.of(1001);

            when(mockDemographicMergedDao.findByParentAndChildIds(parentId, 1001))
                .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> manager.unmergeDemographics(mockLoggedInInfo, parentId, children))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to find merge record");
        }

        @Test
        @DisplayName("should return merged demographics for parent")
        void shouldReturnMergedDemographics_forParent() {
            int parentId = 1000;
            List<DemographicMerged> expected = List.of(
                createTestDemographicMerged(1001, parentId),
                createTestDemographicMerged(1002, parentId)
            );

            when(mockDemographicMergedDao.findCurrentByMergedTo(parentId)).thenReturn(expected);

            List<DemographicMerged> result = manager.getMergedDemographics(mockLoggedInInfo, parentId);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return merged demographic IDs")
        void shouldReturnMergedDemographicIds() {
            List<Integer> expected = List.of(1001, 1002);
            when(mockDemographicDao.getMergedDemographics(TEST_DEMO_NO)).thenReturn(expected);

            List<Integer> result = manager.getMergedDemographicIds(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should do nothing when merging with empty children list")
        void shouldDoNothing_whenMergingWithEmptyChildrenList() {
            // Edge case: empty children list should be handled gracefully
            Integer parentId = 1000;
            List<Integer> emptyChildren = Collections.emptyList();

            // The implementation iterates over children, so empty list means no iterations
            manager.mergeDemographics(mockLoggedInInfo, parentId, emptyChildren);

            // Verify no merge records were persisted and no exceptions thrown
            verify(mockDemographicMergedDao, never()).persist(any(DemographicMerged.class));
        }
    }

    // ==================== CONSENT OPERATIONS ====================

    /**
     * Tests for patient consent management.
     *
     * <p>Consent tracking is required for privacy compliance and data sharing.
     * Tests cover:</p>
     * <ul>
     *   <li>Updating patient consent status</li>
     *   <li>Checking if a patient has consented to a specific consent type</li>
     * </ul>
     */
    @Nested
    @DisplayName("Consent Operations")
    @Tag("consent")
    class ConsentOperationsTests {

        @Test
        @DisplayName("should update patient consent")
        void shouldUpdatePatientConsent() {
            ConsentType consentType = new ConsentType();
            consentType.setId(1);

            when(mockPatientConsentManager.getConsentType("INTEGRATOR_PATIENT_CONSENT")).thenReturn(consentType);

            manager.updatePatientConsent(mockLoggedInInfo, TEST_DEMO_NO, "INTEGRATOR_PATIENT_CONSENT", true);

            verify(mockPatientConsentManager).setConsent(mockLoggedInInfo, TEST_DEMO_NO, 1, true);
        }

        @Test
        @DisplayName("should check if patient is consented")
        void shouldCheckIfPatientIsConsented() {
            ConsentType consentType = new ConsentType();
            consentType.setId(1);

            when(mockPatientConsentManager.getConsentType("INTEGRATOR_PATIENT_CONSENT")).thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType)).thenReturn(true);

            boolean result = manager.isPatientConsented(mockLoggedInInfo, TEST_DEMO_NO, "INTEGRATOR_PATIENT_CONSENT");

            assertThat(result).isTrue();
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Tests for utility methods and simple queries.
     *
     * <p>These tests cover miscellaneous helper methods:</p>
     * <ul>
     *   <li>Getting patient status list (AC, IN, DE, etc.)</li>
     *   <li>Getting roster status list</li>
     *   <li>Getting work phone and extension</li>
     *   <li>Getting next appointment date</li>
     *   <li>Retrieving archive metadata</li>
     * </ul>
     */
    @Nested
    @DisplayName("Utility Methods")
    @Tag("utility")
    class UtilityMethodsTests {

        @Test
        @DisplayName("should return patient status list")
        void shouldReturnPatientStatusList() {
            List<String> expected = List.of("AC", "IN", "DE");
            when(mockDemographicDao.search_ptstatus()).thenReturn(expected);

            List<String> result = manager.getPatientStatusList();

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return roster status list")
        void shouldReturnRosterStatusList() {
            List<String> expected = List.of("RO", "NR", "TE");
            when(mockDemographicDao.getRosterStatuses()).thenReturn(expected);

            List<String> result = manager.getRosterStatusList();

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return work phone with extension")
        void shouldReturnWorkPhoneWithExtension() {
            Demographic demographic = createTestDemographic();
            demographic.setPhone2("416-555-9999");

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(demographic);
            when(mockDemographicExtDao.getValueForDemoKey(TEST_DEMO_NO, "wPhoneExt")).thenReturn("123");

            String result = manager.getDemographicWorkPhoneAndExtension(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEqualTo("416-555-9999x123");
        }

        @Test
        @DisplayName("should return work phone without extension when none exists")
        void shouldReturnWorkPhoneWithoutExtension_whenNoneExists() {
            Demographic demographic = createTestDemographic();
            demographic.setPhone2("416-555-9999");

            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(demographic);
            when(mockDemographicExtDao.getValueForDemoKey(TEST_DEMO_NO, "wPhoneExt")).thenReturn(null);

            String result = manager.getDemographicWorkPhoneAndExtension(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEqualTo("416-555-9999");
        }

        @Test
        @DisplayName("should return archive meta for demographic")
        void shouldReturnArchiveMeta_forDemographic() {
            List<Object[]> expected = List.of(new Object[]{"data1"}, new Object[]{"data2"});
            when(mockDemographicArchiveDao.findMetaByDemographicNo(TEST_DEMO_NO)).thenReturn(expected);

            List<Object[]> result = manager.getArchiveMeta(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return next appointment date")
        void shouldReturnNextAppointmentDate() {
            when(mockAppointmentManager.getNextAppointmentDate(TEST_DEMO_NO)).thenReturn("2025-02-15 10:00");

            String result = manager.getNextAppointmentDate(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEqualTo("2025-02-15 10:00");
        }

        @Test
        @DisplayName("should set next appointment on demographic object")
        void shouldSetNextAppointment_onDemographicObject() {
            Demographic demographic = createTestDemographic();
            when(mockAppointmentManager.getNextAppointmentDate(TEST_DEMO_NO)).thenReturn("2025-02-15 10:00");

            String result = manager.getNextAppointmentDate(mockLoggedInInfo, demographic);

            assertThat(result).isEqualTo("2025-02-15 10:00");
            assertThat(demographic.getNextAppointment()).isEqualTo("2025-02-15 10:00");
        }
    }

    // ==================== ACTIVE DEMOGRAPHICS ====================

    /**
     * Tests for active demographics queries.
     *
     * <p>Active demographics are patients with status 'AC' (Active).
     * These tests cover counting and listing active patients for
     * reporting and administrative purposes.</p>
     */
    @Nested
    @DisplayName("Active Demographics Operations")
    @Tag("active")
    class ActiveDemographicsTests {

        @Test
        @DisplayName("should return active demographic count")
        void shouldReturnActiveDemographicCount() {
            when(mockDemographicDao.getActiveDemographicCount()).thenReturn(1000L);

            Long result = manager.getActiveDemographicCount(mockLoggedInInfo);

            assertThat(result).isEqualTo(1000L);
        }

        @Test
        @DisplayName("should return active demographics with pagination")
        void shouldReturnActiveDemographics_withPagination() {
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.getActiveDemographics(0, 10)).thenReturn(expected);

            List<Demographic> result = manager.getActiveDemographics(mockLoggedInInfo, 0, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return active demographics after date")
        void shouldReturnActiveDemographics_afterDate() {
            Date afterDate = new Date();
            List<Demographic> expected = List.of(createTestDemographic());
            when(mockDemographicDao.getActiveDemographicAfter(afterDate)).thenReturn(expected);

            List<Demographic> result = manager.getActiveDemographicAfter(mockLoggedInInfo, afterDate);

            assertThat(result).hasSize(1);
        }
    }

    // ==================== PROVIDER CASELOAD OPERATIONS ====================

    /**
     * Tests for provider caseload queries.
     *
     * <p>Caseload queries support filtering demographics assigned to specific
     * provider types (midwife, nurse, resident) with optional lastname regex
     * filtering for alphabetical navigation.</p>
     */
    @Nested
    @DisplayName("Provider Caseload Operations")
    @Tag("caseload")
    class ProviderCaseloadOperationsTests {

        @Test
        @DisplayName("should return demographic numbers by midwife and lastname regex")
        void shouldReturnDemographicNumbers_byMidwifeAndLastnameRegex() {
            List<Integer> expected = List.of(1001, 1002);
            when(mockDemographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                any(), eq("MW001"), eq("^[A-M]")
            )).thenReturn(expected);

            List<Integer> result = manager.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(
                mockLoggedInInfo, "MW001", "^[A-M]"
            );

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return demographic numbers by nurse and lastname regex")
        void shouldReturnDemographicNumbers_byNurseAndLastnameRegex() {
            List<Integer> expected = List.of(1003, 1004);
            when(mockDemographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                any(), eq("NR001"), eq("^[N-Z]")
            )).thenReturn(expected);

            List<Integer> result = manager.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(
                mockLoggedInInfo, "NR001", "^[N-Z]"
            );

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return demographic numbers by resident and lastname regex")
        void shouldReturnDemographicNumbers_byResidentAndLastnameRegex() {
            List<Integer> expected = List.of(1005);
            when(mockDemographicExtDao.getDemographicNumbersByDemographicExtKeyAndProviderNumberAndDemographicLastNameRegex(
                any(), eq("RS001"), eq("^DOE")
            )).thenReturn(expected);

            List<Integer> result = manager.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(
                mockLoggedInInfo, "RS001", "^DOE"
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return multiple midwife extensions for demographic numbers")
        void shouldReturnMultipleMidwifeExtensions_forDemographicNumbers() {
            Collection<Integer> demoNos = List.of(1001, 1002);
            List<DemographicExt> expected = List.of(
                createTestDemographicExt(1001, "midwife", "MW001")
            );
            when(mockDemographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                any(), eq(demoNos), eq("MW001")
            )).thenReturn(expected);

            List<DemographicExt> result = manager.getMultipleMidwifeForDemographicNumbersByProviderNumber(
                mockLoggedInInfo, demoNos, "MW001"
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return multiple nurse extensions for demographic numbers")
        void shouldReturnMultipleNurseExtensions_forDemographicNumbers() {
            Collection<Integer> demoNos = List.of(1001, 1002);
            List<DemographicExt> expected = List.of(
                createTestDemographicExt(1001, "nurse", "NR001")
            );
            when(mockDemographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                any(), eq(demoNos), eq("NR001")
            )).thenReturn(expected);

            List<DemographicExt> result = manager.getMultipleNurseForDemographicNumbersByProviderNumber(
                mockLoggedInInfo, demoNos, "NR001"
            );

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return multiple resident extensions for demographic numbers")
        void shouldReturnMultipleResidentExtensions_forDemographicNumbers() {
            Collection<Integer> demoNos = List.of(1001, 1002);
            List<DemographicExt> expected = List.of(
                createTestDemographicExt(1002, "resident", "RS001")
            );
            when(mockDemographicExtDao.getMultipleDemographicExtKeyForDemographicNumbersByProviderNumber(
                any(), eq(demoNos), eq("RS001")
            )).thenReturn(expected);

            List<DemographicExt> result = manager.getMultipleResidentForDemographicNumbersByProviderNumber(
                mockLoggedInInfo, demoNos, "RS001"
            );

            assertThat(result).hasSize(1);
        }
    }

    // ==================== PROGRAM AND ADMISSION OPERATIONS ====================

    /**
     * Tests for program admission related operations.
     *
     * <p>Program admissions link demographics to specific programs (e.g., mental health,
     * chronic disease management). Tests cover querying admitted demographics by
     * program and provider.</p>
     */
    @Nested
    @DisplayName("Program and Admission Operations")
    @Tag("admission")
    class ProgramAdmissionOperationsTests {

        @Test
        @DisplayName("should throw SecurityException when loggedInInfo is null for getAdmittedDemographicIds")
        void shouldThrowSecurityException_whenLoggedInInfoIsNullForAdmittedDemos() {
            assertThatThrownBy(() ->
                manager.getAdmittedDemographicIdsByProgramAndProvider(null, TEST_PROGRAM_ID, TEST_PROVIDER)
            ).isInstanceOf(SecurityException.class)
                .hasMessageContaining("user not logged in");
        }

        @Test
        @DisplayName("should return admitted demographic IDs by program and provider")
        void shouldReturnAdmittedDemographicIds_byProgramAndProvider() {
            List<Integer> expected = List.of(1001, 1002, 1003);
            when(mockAdmissionDao.getAdmittedDemographicIdByProgramAndProvider(TEST_PROGRAM_ID, TEST_PROVIDER))
                .thenReturn(expected);

            List<Integer> result = manager.getAdmittedDemographicIdsByProgramAndProvider(
                mockLoggedInInfo, TEST_PROGRAM_ID, TEST_PROVIDER
            );

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return demographics by list of IDs")
        void shouldReturnDemographics_byListOfIds() {
            List<Integer> ids = List.of(1001, 1002);
            List<Demographic> expected = List.of(
                createTestDemographicWithId(1001),
                createTestDemographicWithId(1002)
            );
            when(mockDemographicDao.getDemographics(ids)).thenReturn(expected);

            List<Demographic> result = manager.getDemographics(mockLoggedInInfo, ids);

            assertThat(result).hasSize(2);
        }
    }

    // ==================== MATCHING OPERATIONS ====================

    /**
     * Tests for demographic matching and duplicate detection.
     *
     * <p>Matching operations help identify potential duplicate patient records.
     * Two types of matching are supported:</p>
     * <ul>
     *   <li><b>Exact match:</b> Returns single demographic if exactly one match found</li>
     *   <li><b>Fuzzy match:</b> Returns list of potential matches for review</li>
     * </ul>
     */
    @Nested
    @DisplayName("Demographic Matching Operations")
    @Tag("matching")
    class MatchingOperationsTests {

        @Test
        @DisplayName("should return exact match when one match found")
        void shouldReturnExactMatch_whenOneMatchFound() {
            Demographic searchDemo = createTestDemographic();
            List<Demographic> matches = List.of(searchDemo);

            when(mockDemographicDao.findByAttributes(
                eq(TEST_HIN), eq(TEST_FIRST_NAME), eq(TEST_LAST_NAME),
                any(Gender.class), any(Calendar.class), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq(0), eq(2)
            )).thenReturn(matches);

            Demographic result = manager.findExactMatchToDemographic(mockLoggedInInfo, searchDemo);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when multiple matches found")
        void shouldReturnNull_whenMultipleMatchesFound() {
            Demographic searchDemo = createTestDemographic();
            List<Demographic> matches = List.of(
                createTestDemographicWithId(1001),
                createTestDemographicWithId(1002)
            );

            when(mockDemographicDao.findByAttributes(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
            )).thenReturn(matches);

            Demographic result = manager.findExactMatchToDemographic(mockLoggedInInfo, searchDemo);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when no matches found")
        void shouldReturnNull_whenNoMatchesFound() {
            Demographic searchDemo = createTestDemographic();

            when(mockDemographicDao.findByAttributes(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
            )).thenReturn(Collections.emptyList());

            Demographic result = manager.findExactMatchToDemographic(mockLoggedInInfo, searchDemo);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return fuzzy matches by lastname and DOB")
        void shouldReturnFuzzyMatches_byLastnameAndDOB() {
            Demographic searchDemo = createTestDemographic();
            List<Demographic> expected = List.of(
                createTestDemographicWithId(1001),
                createTestDemographicWithId(1002)
            );

            when(mockDemographicDao.getDemographicWithLastFirstDOB(
                TEST_LAST_NAME, TEST_FIRST_NAME, "1980", "01", "15"
            )).thenReturn(expected);

            List<Demographic> result = manager.findFuzzyMatchToDemographic(mockLoggedInInfo, searchDemo);

            assertThat(result).hasSize(2);
        }
    }

    // ==================== HEALTH CARE MEMBER OPERATIONS ====================

    /**
     * Tests for health care member retrieval operations.
     *
     * <p>Health care members are providers or specialists associated with a patient
     * through the health care team. Tests cover:</p>
     * <ul>
     *   <li>Finding MRP (Most Responsible Provider) from health care team</li>
     *   <li>Finding health care members by role (numeric or string)</li>
     *   <li>Finding health care members by ID</li>
     *   <li>Finding personal emergency contacts by ID</li>
     * </ul>
     */
    @Nested
    @DisplayName("Health Care Member Operations")
    @Tag("healthcare")
    class HealthCareMemberOperationsTests {

        @Test
        @DisplayName("should return null when demographicNo is null for getMostResponsibleProvider")
        void shouldReturnNull_whenDemographicNoIsNullForMRP() {
            DemographicContact result = manager.getMostResponsibleProviderFromHealthCareTeam(mockLoggedInInfo, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return MRP from health care team when marked as MRP")
        void shouldReturnMRP_fromHealthCareTeamWhenMarkedAsMRP() {
            DemographicContact mrpContact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            mrpContact.setMrp(true);

            List<DemographicContact> contacts = List.of(mrpContact);
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(contacts);

            DemographicContact result = manager.getMostResponsibleProviderFromHealthCareTeam(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
            assertThat(result.isMrp()).isTrue();
        }

        @Test
        @DisplayName("should fallback to first internal provider when no MRP marked")
        void shouldFallbackToFirstInternalProvider_whenNoMRPMarked() {
            DemographicContact providerContact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            providerContact.setMrp(false);

            List<DemographicContact> contacts = List.of(providerContact);
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(contacts);

            DemographicContact result = manager.getMostResponsibleProviderFromHealthCareTeam(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when demographicNo is null for getHealthCareMemberbyRole")
        void shouldReturnNull_whenDemographicNoIsNullForGetHealthCareMemberbyRole() {
            DemographicContact result = manager.getHealthCareMemberbyRole(mockLoggedInInfo, null, "1");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should find health care member by numeric role")
        void shouldFindHealthCareMember_byNumericRole() {
            ContactSpecialty specialty = new ContactSpecialty();
            specialty.setId(1);
            specialty.setSpecialty("Cardiologist");

            DemographicContact contact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            // The implementation converts numeric role ID to specialty name, then compares
            // So contact's role should be the specialty name ("Cardiologist") not the ID ("1")
            contact.setRole("Cardiologist");

            // Use any() matcher since find() can take Object or int
            when(mockContactSpecialtyDao.find(anyInt())).thenReturn(specialty);
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(List.of(contact));

            DemographicContact result = manager.getHealthCareMemberbyRole(mockLoggedInInfo, TEST_DEMO_NO, "1");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should find health care member by string role")
        void shouldFindHealthCareMember_byStringRole() {
            ContactSpecialty specialty = new ContactSpecialty();
            specialty.setId(1);
            specialty.setSpecialty("Cardiologist");

            DemographicContact contact = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            contact.setRole("Cardiologist");

            when(mockContactSpecialtyDao.findBySpecialty("Cardiologist")).thenReturn(specialty);
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(List.of(contact));

            DemographicContact result = manager.getHealthCareMemberbyRole(mockLoggedInInfo, TEST_DEMO_NO, "Cardiologist");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when demographicContactId is null for getHealthCareMemberbyId")
        void shouldReturnNull_whenDemographicContactIdIsNull() {
            DemographicContact result = manager.getHealthCareMemberbyId(mockLoggedInInfo, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return health care member by ID")
        void shouldReturnHealthCareMember_byId() {
            DemographicContact expected = createTestDemographicContactWithId(123, TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            // Use any() matcher since find() can take Object or int
            when(mockDemographicContactDao.find(any())).thenReturn(expected);

            DemographicContact result = manager.getHealthCareMemberbyId(mockLoggedInInfo, 123);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when demographicContactId is null for getPersonalEmergencyContactById")
        void shouldReturnNull_whenDemographicContactIdIsNullForPersonalContact() {
            DemographicContact result = manager.getPersonalEmergencyContactById(mockLoggedInInfo, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return personal emergency contact by ID")
        void shouldReturnPersonalEmergencyContact_byId() {
            DemographicContact expected = createTestDemographicContactWithId(456, TEST_DEMO_NO, DemographicContact.CATEGORY_PERSONAL, 1);
            // Use any() matcher since find() can take Object or int
            when(mockDemographicContactDao.find(any())).thenReturn(expected);

            DemographicContact result = manager.getPersonalEmergencyContactById(mockLoggedInInfo, 456);

            assertThat(result).isNotNull();
        }
    }

    // ==================== INTEGRATOR OPERATIONS ====================

    /**
     * Tests for CAISI Integrator operations.
     *
     * <p>The Integrator allows data sharing between multiple EMR installations.
     * These tests verify behavior when integrator is disabled (the default in
     * unit tests). Integration-enabled scenarios require more complex setup
     * and are covered separately.</p>
     *
     * <p>Methods tested:</p>
     * <ul>
     *   <li>getRemoteDemographic - fetching demographics from remote facilities</li>
     *   <li>linkDemographicToRemoteDemographic - linking local to remote records</li>
     *   <li>getLinkedDemographics - retrieving linked demographic pairs</li>
     *   <li>getLinkedDemographicIds - retrieving IDs of linked demographics</li>
     * </ul>
     */
    @Nested
    @DisplayName("Integrator Operations")
    @Tag("integrator")
    class IntegratorOperationsTests {

        @Test
        @DisplayName("should return null when integrator disabled for getRemoteDemographic")
        void shouldReturnNull_whenIntegratorDisabledForGetRemoteDemographic() {
            when(mockFacility.isIntegratorEnabled()).thenReturn(false);

            Demographic result = manager.getRemoteDemographic(mockLoggedInInfo, 1, 1001);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return false when integrator disabled for linkDemographicToRemoteDemographic")
        void shouldReturnFalse_whenIntegratorDisabledForLinkDemographic() {
            when(mockFacility.isIntegratorEnabled()).thenReturn(false);

            boolean result = manager.linkDemographicToRemoteDemographic(mockLoggedInInfo, 1001, 2, 2001);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return empty list when integrator disabled for getLinkedDemographics")
        void shouldReturnEmptyList_whenIntegratorDisabledForGetLinkedDemographics() {
            when(mockFacility.isIntegratorEnabled()).thenReturn(false);

            var result = manager.getLinkedDemographics(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when integrator disabled for getLinkedDemographicIds")
        void shouldReturnEmptyList_whenIntegratorDisabledForGetLinkedDemographicIds() {
            when(mockFacility.isIntegratorEnabled()).thenReturn(false);

            List<Integer> result = manager.getLinkedDemographicIds(mockLoggedInInfo, TEST_DEMO_NO, 1);

            assertThat(result).isEmpty();
        }
    }

    // ==================== EDGE CASES AND NULL HANDLING ====================

    /**
     * Tests for edge cases and null input handling.
     *
     * <p>These tests verify defensive programming practices and graceful
     * degradation when encountering unexpected inputs:</p>
     * <ul>
     *   <li>Null list parameters returning null or empty collections</li>
     *   <li>Empty extension lists resulting in null extras on demographic</li>
     *   <li>Missing demographics returning null</li>
     *   <li>Null role parameters in health care member searches</li>
     *   <li>Skipping null providers when building provider lists</li>
     * </ul>
     *
     * <p>These tests ensure the manager layer doesn't throw unexpected exceptions
     * and provides predictable behavior for edge cases.</p>
     */
    @Nested
    @DisplayName("Edge Cases and Null Handling")
    @Tag("edge-cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null list gracefully for getDemographics")
        void shouldHandleNullListGracefully_forGetDemographics() {
            when(mockDemographicDao.getDemographics(anyList())).thenReturn(null);

            List<Demographic> result = manager.getDemographics(mockLoggedInInfo, List.of(1, 2, 3));

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle empty extension list when getDemographicWithExt returns demographic")
        void shouldHandleEmptyExtensionList_whenGetDemographicWithExtReturnsDemographic() {
            Demographic demographic = createTestDemographic();
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(demographic);
            when(mockDemographicExtDao.getDemographicExtByDemographicNo(TEST_DEMO_NO))
                .thenReturn(Collections.emptyList());

            Demographic result = manager.getDemographicWithExt(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNotNull();
            assertThat(result.getExtras()).isNull();
        }

        @Test
        @DisplayName("should return null when getDemographicWithExt finds no demographic")
        void shouldReturnNull_whenGetDemographicWithExtFindsNoDemographic() {
            when(mockDemographicDao.getDemographicById(TEST_DEMO_NO)).thenReturn(null);

            Demographic result = manager.getDemographicWithExt(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle null role gracefully for getHealthCareMemberbyRole")
        void shouldHandleNullRoleGracefully_forGetHealthCareMemberbyRole() {
            when(mockDemographicContactDao.findByDemographicNoAndCategory(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL))
                .thenReturn(Collections.emptyList());

            DemographicContact result = manager.getHealthCareMemberbyRole(mockLoggedInInfo, TEST_DEMO_NO, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should skip null provider when building most responsible providers list")
        void shouldSkipNullProvider_whenBuildingMostResponsibleProvidersList() {
            DemographicContact contact1 = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            contact1.setContactId("999990");
            DemographicContact contact2 = createTestDemographicContact(TEST_DEMO_NO, DemographicContact.CATEGORY_PROFESSIONAL, 0);
            contact2.setContactId("999991");

            List<DemographicContact> contacts = List.of(contact1, contact2);

            when(mockDemographicContactDao.findAllByDemographicNoAndCategoryAndType(TEST_DEMO_NO, "professional", 0))
                .thenReturn(contacts);
            when(mockProviderManager.getProvider(mockLoggedInInfo, "999990")).thenReturn(createTestProvider());
            when(mockProviderManager.getProvider(mockLoggedInInfo, "999991")).thenReturn(null);

            List<Provider> result = manager.getDemographicMostResponsibleProviders(mockLoggedInInfo, TEST_DEMO_NO);

            assertThat(result).hasSize(1);
        }
    }
}
