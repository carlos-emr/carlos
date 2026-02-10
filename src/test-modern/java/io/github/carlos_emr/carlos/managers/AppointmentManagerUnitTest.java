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

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link AppointmentManagerImpl} business logic.
 *
 * <p>Tests cover all 13 interface methods including security privilege checks,
 * appointment CRUD operations, history retrieval with deduplication, status/type/urgency
 * updates, lookup queries, monthly search, and next appointment date formatting.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Security privilege verification for protected methods</li>
 *   <li>Archive-before-update workflow validation</li>
 *   <li>Deduplication logic for appointment history with deleted records</li>
 *   <li>Edge cases for null returns and empty collections</li>
 *   <li>Manager-DAO interaction verification</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see AppointmentManagerImpl
 * @see AppointmentUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Appointment Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("appointment")
public class AppointmentManagerUnitTest extends AppointmentUnitTestBase {

    @Mock
    private OscarAppointmentDao mockAppointmentDao;

    @Mock
    private AppointmentStatusDao mockAppointmentStatusDao;

    @Mock
    private LookupListDao mockLookupListDao;

    @Mock
    private AppointmentArchiveDao mockAppointmentArchiveDao;

    private AppointmentManagerImpl appointmentManager;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mock DAOs with SpringUtils, stubs the security manager to grant all
     * privileges by default, creates a fresh {@link AppointmentManagerImpl} instance,
     * and injects mock dependencies (appointment DAOs, status DAO, lookup DAO, archive DAO,
     * SecurityInfoManager) via reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register DAO mocks for SpringUtils
        registerMock(OscarAppointmentDao.class, mockAppointmentDao);
        registerMock(AppointmentStatusDao.class, mockAppointmentStatusDao);
        registerMock(LookupListDao.class, mockLookupListDao);
        registerMock(AppointmentArchiveDao.class, mockAppointmentArchiveDao);

        // Security manager returns true for all privilege checks by default
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // Create manager and inject dependencies
        appointmentManager = new AppointmentManagerImpl();

        injectDependency(appointmentManager, "appointmentDao", mockAppointmentDao);
        injectDependency(appointmentManager, "appointmentStatusDao", mockAppointmentStatusDao);
        injectDependency(appointmentManager, "lookupListDao", mockLookupListDao);
        injectDependency(appointmentManager, "appointmentArchiveDao", mockAppointmentArchiveDao);
        injectDependency(appointmentManager, "securityInfoManager", mockSecurityInfoManager);
    }

    // -------------------------------------------------------------------------
    // Security privilege checks
    // -------------------------------------------------------------------------

    /**
     * Tests that security privilege checks are enforced on all protected methods.
     * Methods that require privilege checks throw RuntimeException("Access Denied")
     * when the logged-in user lacks the necessary privilege.
     */
    @Nested
    @DisplayName("Security Privilege Checks")
    class SecurityPrivilegeChecks {

        @BeforeEach
        void denyAllPrivileges() {
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
        }

        @Test
        @DisplayName("should deny getAppointment without read privilege")
        void shouldThrowException_whenGetAppointmentWithoutReadPrivilege() {
            assertThatThrownBy(() ->
                appointmentManager.getAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny addAppointment without write privilege")
        void shouldThrowException_whenAddAppointmentWithoutWritePrivilege() {
            Appointment appointment = createTestAppointment();

            assertThatThrownBy(() ->
                appointmentManager.addAppointment(mockLoggedInInfo, appointment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny updateAppointment without write privilege")
        void shouldThrowException_whenUpdateAppointmentWithoutWritePrivilege() {
            Appointment appointment = createTestAppointmentWithId(TEST_APPOINTMENT_ID);

            assertThatThrownBy(() ->
                appointmentManager.updateAppointment(mockLoggedInInfo, appointment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny deleteAppointment without delete privilege")
        void shouldThrowException_whenDeleteAppointmentWithoutDeletePrivilege() {
            assertThatThrownBy(() ->
                appointmentManager.deleteAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny getAppointmentHistoryWithoutDeleted without read privilege")
        void shouldThrowException_whenGetHistoryWithoutDeletedWithoutReadPrivilege() {
            assertThatThrownBy(() ->
                appointmentManager.getAppointmentHistoryWithoutDeleted(mockLoggedInInfo, TEST_DEMO_NO, 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny getAppointmentHistoryWithDeleted without read privilege")
        void shouldThrowException_whenGetHistoryWithDeletedWithoutReadPrivilege() {
            assertThatThrownBy(() ->
                appointmentManager.getAppointmentHistoryWithDeleted(mockLoggedInInfo, TEST_DEMO_NO, 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should deny updateAppointmentStatus without write privilege")
        void shouldThrowException_whenUpdateStatusWithoutWritePrivilege() {
            assertThatThrownBy(() ->
                appointmentManager.updateAppointmentStatus(mockLoggedInInfo, TEST_APPOINTMENT_ID, "C"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should not call DAO when privilege is denied for getAppointment")
        void shouldNotCallDao_whenGetAppointmentDenied() {
            try {
                appointmentManager.getAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);
            } catch (RuntimeException ignored) {
                // expected
            }

            verify(mockAppointmentDao, never()).find(anyInt());
        }

        @Test
        @DisplayName("should not persist when privilege is denied for addAppointment")
        void shouldNotPersist_whenAddAppointmentDenied() {
            Appointment appointment = createTestAppointment();

            try {
                appointmentManager.addAppointment(mockLoggedInInfo, appointment);
            } catch (RuntimeException ignored) {
                // expected
            }

            verify(mockAppointmentDao, never()).persist(any(Appointment.class));
        }
    }

    // -------------------------------------------------------------------------
    // getAppointment
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getAppointment(io.github.carlos_emr.carlos.utility.LoggedInInfo, int)}.
     */
    @Nested
    @DisplayName("getAppointment")
    class GetAppointment {

        @Test
        @DisplayName("should return appointment when valid ID is provided")
        void shouldReturnAppointment_whenValidIdProvided() {
            // Given - cast to int to match find(int) overload since getAppointment takes primitive int
            Appointment expected = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(expected);

            // When
            Appointment result = appointmentManager.getAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TEST_APPOINTMENT_ID);
            assertThat(result.getDemographicNo()).isEqualTo(TEST_DEMO_NO);
        }

        @Test
        @DisplayName("should return null when appointment not found")
        void shouldReturnNull_whenAppointmentNotFound() {
            // Given
            when(mockAppointmentDao.find(999)).thenReturn(null);

            // When
            Appointment result = appointmentManager.getAppointment(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should call DAO find with correct appointment number")
        void shouldCallDaoFind_whenGettingAppointment() {
            // Given - cast to int to match find(int) overload
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(createTestAppointmentWithId(TEST_APPOINTMENT_ID));

            // When
            appointmentManager.getAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            verify(mockAppointmentDao).find((int) TEST_APPOINTMENT_ID);
        }

        @Test
        @DisplayName("should check read privilege on _appointment")
        void shouldCheckReadPrivilege_whenGettingAppointment() {
            // Given
            when(mockAppointmentDao.find(anyInt())).thenReturn(createTestAppointmentWithId(TEST_APPOINTMENT_ID));

            // When
            appointmentManager.getAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "r", null);
        }
    }

    // -------------------------------------------------------------------------
    // addAppointment
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#addAppointment(io.github.carlos_emr.carlos.utility.LoggedInInfo, Appointment)}.
     */
    @Nested
    @DisplayName("addAppointment")
    class AddAppointment {

        @Test
        @DisplayName("should persist appointment via DAO")
        void shouldPersistAppointment_whenAdding() {
            // Given
            Appointment appointment = createTestAppointment();

            // When
            appointmentManager.addAppointment(mockLoggedInInfo, appointment);

            // Then
            verify(mockAppointmentDao).persist(appointment);
        }

        @Test
        @DisplayName("should check write privilege on _appointment")
        void shouldCheckWritePrivilege_whenAddingAppointment() {
            // Given
            Appointment appointment = createTestAppointment();

            // When
            appointmentManager.addAppointment(mockLoggedInInfo, appointment);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "w", null);
        }

        @Test
        @DisplayName("should persist appointment with all fields intact")
        void shouldPersistAppointmentWithAllFields_whenAdding() {
            // Given
            Appointment appointment = createTestAppointment();
            appointment.setNotes("Test notes");
            appointment.setLocation("Room 1");

            // When
            appointmentManager.addAppointment(mockLoggedInInfo, appointment);

            // Then
            verify(mockAppointmentDao).persist(argThat(appt ->
                appt instanceof Appointment &&
                "Test notes".equals(((Appointment) appt).getNotes()) &&
                "Room 1".equals(((Appointment) appt).getLocation()) &&
                TEST_PROVIDER.equals(((Appointment) appt).getProviderNo())
            ));
        }
    }

    // -------------------------------------------------------------------------
    // updateAppointment
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#updateAppointment(io.github.carlos_emr.carlos.utility.LoggedInInfo, Appointment)}.
     */
    @Nested
    @DisplayName("updateAppointment")
    class UpdateAppointment {

        @Test
        @DisplayName("should archive existing and merge updated appointment")
        void shouldArchiveExistingAndMerge_whenUpdating() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            Appointment updated = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            updated.setReason("Updated reason");

            when(mockAppointmentDao.find((Object) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            appointmentManager.updateAppointment(mockLoggedInInfo, updated);

            // Then
            verify(mockAppointmentArchiveDao).archiveAppointment(existing);
            verify(mockAppointmentDao).merge(updated);
        }

        @Test
        @DisplayName("should skip archive when existing appointment not found")
        void shouldSkipArchive_whenExistingNotFound() {
            // Given
            Appointment updated = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((Object) TEST_APPOINTMENT_ID)).thenReturn(null);

            // When
            appointmentManager.updateAppointment(mockLoggedInInfo, updated);

            // Then
            verify(mockAppointmentArchiveDao, never()).archiveAppointment(any());
            verify(mockAppointmentDao).merge(updated);
        }

        @Test
        @DisplayName("should check write privilege on _appointment")
        void shouldCheckWritePrivilege_whenUpdating() {
            // Given
            Appointment appointment = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find(anyInt())).thenReturn(null);

            // When
            appointmentManager.updateAppointment(mockLoggedInInfo, appointment);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "w", null);
        }

        @Test
        @DisplayName("should look up existing appointment by ID before archiving")
        void shouldLookUpExistingById_beforeArchiving() {
            // Given
            Appointment updated = createTestAppointmentWithId(42);
            when(mockAppointmentDao.find((Object) 42)).thenReturn(createTestAppointmentWithId(42));

            // When
            appointmentManager.updateAppointment(mockLoggedInInfo, updated);

            // Then
            verify(mockAppointmentDao).find((Object) 42);
        }
    }

    // -------------------------------------------------------------------------
    // deleteAppointment
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#deleteAppointment(io.github.carlos_emr.carlos.utility.LoggedInInfo, int)}.
     */
    @Nested
    @DisplayName("deleteAppointment")
    class DeleteAppointment {

        @Test
        @DisplayName("should archive existing and remove appointment")
        void shouldArchiveAndRemove_whenDeleting() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            appointmentManager.deleteAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            verify(mockAppointmentArchiveDao).archiveAppointment(existing);
            verify(mockAppointmentDao).remove(TEST_APPOINTMENT_ID);
        }

        @Test
        @DisplayName("should skip archive when existing appointment not found")
        void shouldSkipArchive_whenExistingNotFound() {
            // Given
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(null);

            // When
            appointmentManager.deleteAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            verify(mockAppointmentArchiveDao, never()).archiveAppointment(any());
            verify(mockAppointmentDao).remove(TEST_APPOINTMENT_ID);
        }

        @Test
        @DisplayName("should check delete privilege on _appointment")
        void shouldCheckDeletePrivilege_whenDeleting() {
            // Given
            when(mockAppointmentDao.find(anyInt())).thenReturn(null);

            // When
            appointmentManager.deleteAppointment(mockLoggedInInfo, TEST_APPOINTMENT_ID);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "d", null);
        }

        @Test
        @DisplayName("should remove by appointment number even when existing is null")
        void shouldRemoveByNumber_whenExistingIsNull() {
            // Given
            when(mockAppointmentDao.find(77)).thenReturn(null);

            // When
            appointmentManager.deleteAppointment(mockLoggedInInfo, 77);

            // Then
            verify(mockAppointmentDao).remove(77);
        }
    }

    // -------------------------------------------------------------------------
    // getAppointmentHistoryWithoutDeleted
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getAppointmentHistoryWithoutDeleted}.
     */
    @Nested
    @DisplayName("getAppointmentHistoryWithoutDeleted")
    class GetAppointmentHistoryWithoutDeleted {

        @Test
        @DisplayName("should return non-deleted appointments from DAO")
        void shouldReturnNonDeletedAppointments_whenHistoryRequested() {
            // Given
            Appointment appt1 = createTestAppointmentWithId(10);
            Appointment appt2 = createTestAppointmentWithId(20);
            List<Appointment> appointments = Arrays.asList(appt1, appt2);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(appointments);

            // When
            List<Appointment> result = appointmentManager.getAppointmentHistoryWithoutDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(appt1, appt2);
        }

        @Test
        @DisplayName("should return empty list when no appointments found")
        void shouldReturnEmptyList_whenNoAppointments() {
            // Given
            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());

            // When
            List<Appointment> result = appointmentManager.getAppointmentHistoryWithoutDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should pass offset and limit to DAO")
        void shouldPassOffsetAndLimit_whenQuerying() {
            // Given
            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 5, 25))
                .thenReturn(Collections.emptyList());

            // When
            appointmentManager.getAppointmentHistoryWithoutDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 5, 25);

            // Then
            verify(mockAppointmentDao).getAppointmentHistory(TEST_DEMO_NO, 5, 25);
        }

        @Test
        @DisplayName("should check read privilege on _appointment")
        void shouldCheckReadPrivilege_whenGettingHistory() {
            // Given
            when(mockAppointmentDao.getAppointmentHistory(any(), any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            appointmentManager.getAppointmentHistoryWithoutDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "r", null);
        }
    }

    // -------------------------------------------------------------------------
    // getAppointmentHistoryWithDeleted
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getAppointmentHistoryWithDeleted}.
     * This method combines non-deleted appointments with deleted (archived) appointments,
     * deduplicating archives that match existing appointment IDs or share the same
     * appointmentNo with an archive already in the result set.
     */
    @Nested
    @DisplayName("getAppointmentHistoryWithDeleted")
    class GetAppointmentHistoryWithDeleted {

        @Test
        @DisplayName("should combine non-deleted and deleted appointments")
        void shouldCombineNonDeletedAndDeleted_whenHistoryRequested() {
            // Given
            Appointment appt1 = createTestAppointmentWithId(10);
            AppointmentArchive archive1 = createTestAppointmentArchive();
            archive1.setAppointmentNo(99);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(appt1));
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(archive1));

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(Appointment.class);
            assertThat(result.get(1)).isInstanceOf(AppointmentArchive.class);
        }

        @Test
        @DisplayName("should exclude archive when appointmentNo matches existing appointment ID")
        void shouldExcludeArchive_whenAppointmentNoMatchesExistingId() {
            // Given - appointment with id=10, archive with appointmentNo=10 (duplicate)
            Appointment appt1 = createTestAppointmentWithId(10);
            AppointmentArchive duplicateArchive = createTestAppointmentArchive();
            duplicateArchive.setAppointmentNo(10);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(appt1));
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(duplicateArchive));

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then - only the non-deleted appointment should be in the result
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(Appointment.class);
        }

        @Test
        @DisplayName("should exclude duplicate archives with same appointmentNo")
        void shouldExcludeDuplicateArchive_whenSameAppointmentNoAlreadyInList() {
            // Given - two archives with the same appointmentNo
            AppointmentArchive archive1 = createTestAppointmentArchive();
            archive1.setAppointmentNo(50);
            AppointmentArchive archive2 = createTestAppointmentArchive();
            archive2.setAppointmentNo(50);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Arrays.asList(archive1, archive2));

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then - only the first archive should be in the result
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(archive1);
        }

        @Test
        @DisplayName("should include archive with unique appointmentNo")
        void shouldIncludeArchive_whenAppointmentNoIsUnique() {
            // Given
            Appointment appt = createTestAppointmentWithId(10);
            AppointmentArchive archive = createTestAppointmentArchive();
            archive.setAppointmentNo(20);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(appt));
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.singletonList(archive));

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isSameAs(appt);
            assertThat(result.get(1)).isSameAs(archive);
        }

        @Test
        @DisplayName("should return empty list when no appointments or archives exist")
        void shouldReturnEmptyList_whenNoData() {
            // Given
            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return only non-deleted when all archives are duplicates")
        void shouldReturnOnlyNonDeleted_whenAllArchivesAreDuplicates() {
            // Given
            Appointment appt1 = createTestAppointmentWithId(10);
            Appointment appt2 = createTestAppointmentWithId(20);
            AppointmentArchive archiveDup1 = createTestAppointmentArchive();
            archiveDup1.setAppointmentNo(10);
            AppointmentArchive archiveDup2 = createTestAppointmentArchive();
            archiveDup2.setAppointmentNo(20);

            when(mockAppointmentDao.getAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Arrays.asList(appt1, appt2));
            when(mockAppointmentDao.getDeletedAppointmentHistory(TEST_DEMO_NO, 0, 10))
                .thenReturn(Arrays.asList(archiveDup1, archiveDup2));

            // When
            List<Object> result = appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(o -> o instanceof Appointment);
        }

        @Test
        @DisplayName("should check read privilege on _appointment")
        void shouldCheckReadPrivilege_whenGettingHistoryWithDeleted() {
            // Given
            when(mockAppointmentDao.getAppointmentHistory(any(), any(), any()))
                .thenReturn(Collections.emptyList());
            when(mockAppointmentDao.getDeletedAppointmentHistory(any(), any(), any()))
                .thenReturn(Collections.emptyList());

            // When
            appointmentManager.getAppointmentHistoryWithDeleted(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "r", null);
        }
    }

    // -------------------------------------------------------------------------
    // updateAppointmentStatus
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#updateAppointmentStatus}.
     */
    @Nested
    @DisplayName("updateAppointmentStatus")
    class UpdateAppointmentStatus {

        @Test
        @DisplayName("should archive and update status when appointment exists")
        void shouldArchiveAndUpdateStatus_whenAppointmentExists() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            existing.setStatus("t");
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentStatus(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "C");

            // Then
            verify(mockAppointmentArchiveDao).archiveAppointment(existing);
            assertThat(result.getStatus()).isEqualTo("C");
            verify(mockAppointmentDao).merge(existing);
        }

        @Test
        @DisplayName("should return appointment with new status")
        void shouldReturnAppointmentWithNewStatus_whenStatusChanged() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            existing.setStatus("t");
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentStatus(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "H");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("H");
            assertThat(result.getId()).isEqualTo(TEST_APPOINTMENT_ID);
        }

        @Test
        @DisplayName("should check write privilege on _appointment")
        void shouldCheckWritePrivilege_whenUpdatingStatus() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find(anyInt())).thenReturn(existing);

            // When
            appointmentManager.updateAppointmentStatus(mockLoggedInInfo, TEST_APPOINTMENT_ID, "C");

            // Then
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_appointment", "w", null);
        }

        @Test
        @DisplayName("should skip archive but still merge when appointment not found")
        void shouldSkipArchiveButStillMerge_whenAppointmentNotFound() {
            // Given - find returns null, causing appt to be null
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(null);

            // When/Then - the implementation calls merge(null) and getId() on null,
            // which would throw NPE. This verifies the current behavior.
            assertThatThrownBy(() ->
                appointmentManager.updateAppointmentStatus(mockLoggedInInfo, TEST_APPOINTMENT_ID, "C"))
                .isInstanceOf(NullPointerException.class);

            verify(mockAppointmentArchiveDao, never()).archiveAppointment(any());
        }
    }

    // -------------------------------------------------------------------------
    // updateAppointmentType
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#updateAppointmentType}.
     * Note: This method does NOT have a security privilege check in the implementation.
     */
    @Nested
    @DisplayName("updateAppointmentType")
    class UpdateAppointmentType {

        @Test
        @DisplayName("should archive and update type when appointment exists")
        void shouldArchiveAndUpdateType_whenAppointmentExists() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            existing.setType("");
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentType(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "telehealth");

            // Then
            verify(mockAppointmentArchiveDao).archiveAppointment(existing);
            assertThat(result.getType()).isEqualTo("telehealth");
            verify(mockAppointmentDao).merge(existing);
        }

        @Test
        @DisplayName("should return appointment with updated type")
        void shouldReturnAppointmentWithUpdatedType() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentType(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "in-person");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo("in-person");
        }

        @Test
        @DisplayName("should not enforce security privilege check")
        void shouldNotEnforceSecurityCheck() {
            // Given - deny all privileges
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When - should succeed despite denied privileges (no security check in implementation)
            Appointment result = appointmentManager.updateAppointmentType(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "telehealth");

            // Then
            assertThat(result).isNotNull();
            verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_appointment"), anyString(), any());
        }

        @Test
        @DisplayName("should throw NPE when appointment not found")
        void shouldThrowNpe_whenAppointmentNotFound() {
            // Given
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(null);

            // When/Then - implementation calls merge(null) then getId() on null
            assertThatThrownBy(() ->
                appointmentManager.updateAppointmentType(mockLoggedInInfo, TEST_APPOINTMENT_ID, "type"))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // -------------------------------------------------------------------------
    // updateAppointmentUrgency
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#updateAppointmentUrgency}.
     * Note: This method does NOT have a security privilege check in the implementation.
     */
    @Nested
    @DisplayName("updateAppointmentUrgency")
    class UpdateAppointmentUrgency {

        @Test
        @DisplayName("should archive and update urgency when appointment exists")
        void shouldArchiveAndUpdateUrgency_whenAppointmentExists() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            existing.setUrgency("low");
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentUrgency(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "critical");

            // Then
            verify(mockAppointmentArchiveDao).archiveAppointment(existing);
            assertThat(result.getUrgency()).isEqualTo("critical");
            verify(mockAppointmentDao).merge(existing);
        }

        @Test
        @DisplayName("should return appointment with updated urgency")
        void shouldReturnAppointmentWithUpdatedUrgency() {
            // Given
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When
            Appointment result = appointmentManager.updateAppointmentUrgency(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "high");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUrgency()).isEqualTo("high");
        }

        @Test
        @DisplayName("should not enforce security privilege check")
        void shouldNotEnforceSecurityCheck() {
            // Given - deny all privileges
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
            Appointment existing = createTestAppointmentWithId(TEST_APPOINTMENT_ID);
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(existing);

            // When - should succeed despite denied privileges
            Appointment result = appointmentManager.updateAppointmentUrgency(
                mockLoggedInInfo, TEST_APPOINTMENT_ID, "critical");

            // Then
            assertThat(result).isNotNull();
            verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_appointment"), anyString(), any());
        }

        @Test
        @DisplayName("should throw NPE when appointment not found")
        void shouldThrowNpe_whenAppointmentNotFound() {
            // Given
            when(mockAppointmentDao.find((int) TEST_APPOINTMENT_ID)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() ->
                appointmentManager.updateAppointmentUrgency(mockLoggedInInfo, TEST_APPOINTMENT_ID, "urgent"))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // -------------------------------------------------------------------------
    // getAppointmentStatuses
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getAppointmentStatuses()}.
     * This method requires no security check and no LoggedInInfo.
     */
    @Nested
    @DisplayName("getAppointmentStatuses")
    class GetAppointmentStatuses {

        @Test
        @DisplayName("should return all statuses from DAO")
        void shouldReturnAllStatuses_whenRequested() {
            // Given
            AppointmentStatus status1 = new AppointmentStatus();
            status1.setStatus("t");
            status1.setDescription("Confirmed");
            AppointmentStatus status2 = new AppointmentStatus();
            status2.setStatus("C");
            status2.setDescription("Cancelled");

            when(mockAppointmentStatusDao.findAll()).thenReturn(Arrays.asList(status1, status2));

            // When
            List<AppointmentStatus> result = appointmentManager.getAppointmentStatuses();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo("t");
            assertThat(result.get(1).getStatus()).isEqualTo("C");
        }

        @Test
        @DisplayName("should return empty list when no statuses exist")
        void shouldReturnEmptyList_whenNoStatuses() {
            // Given
            when(mockAppointmentStatusDao.findAll()).thenReturn(Collections.emptyList());

            // When
            List<AppointmentStatus> result = appointmentManager.getAppointmentStatuses();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should call appointmentStatusDao.findAll()")
        void shouldCallDaoFindAll() {
            // Given
            when(mockAppointmentStatusDao.findAll()).thenReturn(Collections.emptyList());

            // When
            appointmentManager.getAppointmentStatuses();

            // Then
            verify(mockAppointmentStatusDao).findAll();
        }
    }

    // -------------------------------------------------------------------------
    // getReasons
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getReasons()}.
     * Retrieves appointment reason codes from the LookupList named "reasonCode".
     */
    @Nested
    @DisplayName("getReasons")
    class GetReasons {

        @Test
        @DisplayName("should return lookup items when reasonCode list exists")
        void shouldReturnLookupItems_whenReasonCodeListExists() {
            // Given
            LookupList lookupList = new LookupList();
            lookupList.setName("reasonCode");

            LookupListItem item1 = new LookupListItem();
            item1.setLabel("Follow-up");
            item1.setValue("FU");
            LookupListItem item2 = new LookupListItem();
            item2.setLabel("New Patient");
            item2.setValue("NP");

            lookupList.setItems(Arrays.asList(item1, item2));

            when(mockLookupListDao.findByName("reasonCode")).thenReturn(lookupList);

            // When
            List<LookupListItem> result = appointmentManager.getReasons();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLabel()).isEqualTo("Follow-up");
            assertThat(result.get(1).getLabel()).isEqualTo("New Patient");
        }

        @Test
        @DisplayName("should return empty list when reasonCode list not found")
        void shouldReturnEmptyList_whenReasonCodeListNotFound() {
            // Given
            when(mockLookupListDao.findByName("reasonCode")).thenReturn(null);

            // When
            List<LookupListItem> result = appointmentManager.getReasons();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when reasonCode list has no items")
        void shouldReturnEmptyList_whenReasonCodeListHasNoItems() {
            // Given
            LookupList emptyList = new LookupList();
            emptyList.setName("reasonCode");
            emptyList.setItems(new ArrayList<>());

            when(mockLookupListDao.findByName("reasonCode")).thenReturn(emptyList);

            // When
            List<LookupListItem> result = appointmentManager.getReasons();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should query for the 'reasonCode' lookup list")
        void shouldQueryForReasonCodeList() {
            // Given
            when(mockLookupListDao.findByName("reasonCode")).thenReturn(null);

            // When
            appointmentManager.getReasons();

            // Then
            verify(mockLookupListDao).findByName("reasonCode");
        }
    }

    // -------------------------------------------------------------------------
    // findMonthlyAppointments
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#findMonthlyAppointments}.
     * Note: This method does NOT have a security privilege check in the implementation.
     */
    @Nested
    @DisplayName("findMonthlyAppointments")
    class FindMonthlyAppointments {

        @Test
        @DisplayName("should return appointments for the specified month and provider")
        void shouldReturnAppointments_whenFoundForMonth() {
            // Given
            Appointment appt1 = createTestAppointmentWithId(10);
            Appointment appt2 = createTestAppointmentWithId(20);

            when(mockAppointmentDao.findByDateRangeAndProvider(any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Arrays.asList(appt1, appt2));

            // When
            List<Appointment> result = appointmentManager.findMonthlyAppointments(
                mockLoggedInInfo, TEST_PROVIDER, 2026, 0);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(appt1, appt2);
        }

        @Test
        @DisplayName("should return empty list when no appointments found")
        void shouldReturnEmptyList_whenNoAppointments() {
            // Given
            when(mockAppointmentDao.findByDateRangeAndProvider(any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.emptyList());

            // When
            List<Appointment> result = appointmentManager.findMonthlyAppointments(
                mockLoggedInInfo, TEST_PROVIDER, 2026, 5);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should pass correct provider to DAO")
        void shouldPassCorrectProvider_whenSearching() {
            // Given
            String specificProvider = "100001";
            when(mockAppointmentDao.findByDateRangeAndProvider(any(Date.class), any(Date.class), eq(specificProvider)))
                .thenReturn(Collections.emptyList());

            // When
            appointmentManager.findMonthlyAppointments(mockLoggedInInfo, specificProvider, 2026, 1);

            // Then
            verify(mockAppointmentDao).findByDateRangeAndProvider(any(Date.class), any(Date.class), eq(specificProvider));
        }

        @Test
        @DisplayName("should call DAO with date range parameters")
        void shouldCallDaoWithDateRange() {
            // Given
            when(mockAppointmentDao.findByDateRangeAndProvider(any(Date.class), any(Date.class), anyString()))
                .thenReturn(Collections.emptyList());

            // When
            appointmentManager.findMonthlyAppointments(mockLoggedInInfo, TEST_PROVIDER, 2026, 0);

            // Then
            verify(mockAppointmentDao).findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER));
        }

        @Test
        @DisplayName("should not enforce security privilege check")
        void shouldNotEnforceSecurityCheck() {
            // Given - deny all privileges
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
            when(mockAppointmentDao.findByDateRangeAndProvider(any(Date.class), any(Date.class), anyString()))
                .thenReturn(Collections.emptyList());

            // When - should succeed despite denied privileges
            List<Appointment> result = appointmentManager.findMonthlyAppointments(
                mockLoggedInInfo, TEST_PROVIDER, 2026, 0);

            // Then
            assertThat(result).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // getNextAppointmentDate
    // -------------------------------------------------------------------------

    /**
     * Tests for {@link AppointmentManagerImpl#getNextAppointmentDate(Integer)}.
     * Returns the next appointment date as a formatted string (yyyy-MM-dd),
     * or an empty string if no upcoming appointment exists.
     */
    @Nested
    @DisplayName("getNextAppointmentDate")
    class GetNextAppointmentDate {

        @Test
        @DisplayName("should return formatted date when next appointment exists")
        void shouldReturnFormattedDate_whenNextAppointmentExists() {
            // Given
            Appointment nextAppt = createTestAppointment();
            Date appointmentDate = new Date();
            nextAppt.setAppointmentDate(appointmentDate);

            when(mockAppointmentDao.findNextAppointment(TEST_DEMO_NO)).thenReturn(nextAppt);

            String expectedDate = new SimpleDateFormat("yyyy-MM-dd").format(appointmentDate);

            // When
            String result = appointmentManager.getNextAppointmentDate(TEST_DEMO_NO);

            // Then
            assertThat(result).isEqualTo(expectedDate);
        }

        @Test
        @DisplayName("should return empty string when no next appointment")
        void shouldReturnEmptyString_whenNoNextAppointment() {
            // Given
            when(mockAppointmentDao.findNextAppointment(TEST_DEMO_NO)).thenReturn(null);

            // When
            String result = appointmentManager.getNextAppointmentDate(TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string when appointment date is null")
        void shouldReturnEmptyString_whenAppointmentDateIsNull() {
            // Given
            Appointment apptWithNullDate = createTestAppointment();
            apptWithNullDate.setAppointmentDate(null);
            when(mockAppointmentDao.findNextAppointment(TEST_DEMO_NO)).thenReturn(apptWithNullDate);

            // When
            String result = appointmentManager.getNextAppointmentDate(TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should call DAO with correct demographic number")
        void shouldCallDaoWithCorrectDemographicNo() {
            // Given
            Integer demoNo = 54321;
            when(mockAppointmentDao.findNextAppointment(demoNo)).thenReturn(null);

            // When
            appointmentManager.getNextAppointmentDate(demoNo);

            // Then
            verify(mockAppointmentDao).findNextAppointment(demoNo);
        }

        @Test
        @DisplayName("should not require security check")
        void shouldNotRequireSecurityCheck() {
            // Given - deny all privileges
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);
            when(mockAppointmentDao.findNextAppointment(TEST_DEMO_NO)).thenReturn(null);

            // When - should succeed despite denied privileges (no security check)
            String result = appointmentManager.getNextAppointmentDate(TEST_DEMO_NO);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return date in yyyy-MM-dd format")
        void shouldReturnDateInCorrectFormat() {
            // Given
            Appointment nextAppt = createTestAppointment();
            nextAppt.setAppointmentDate(new Date());
            when(mockAppointmentDao.findNextAppointment(TEST_DEMO_NO)).thenReturn(nextAppt);

            // When
            String result = appointmentManager.getNextAppointmentDate(TEST_DEMO_NO);

            // Then - verify format matches yyyy-MM-dd pattern
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }
}
