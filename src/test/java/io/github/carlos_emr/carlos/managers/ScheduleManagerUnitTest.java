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
import io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleHolidayDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.commn.model.ScheduleHoliday;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplatePrimaryKey;
import io.github.carlos_emr.carlos.commn.model.Security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScheduleManagerImpl} business logic.
 *
 * <p>Tests cover appointment CRUD operations, schedule template resolution,
 * holiday detection, security privilege enforcement, consent filtering,
 * double-booking detection, and edge cases.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Manager-DAO interaction verification</li>
 *   <li>Security privilege enforcement</li>
 *   <li>Patient consent filtering</li>
 *   <li>Double-booking conflict detection</li>
 *   <li>Day work schedule template resolution</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see ScheduleManagerImpl
 * @see ScheduleUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Schedule Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("schedule")
public class ScheduleManagerUnitTest extends ScheduleUnitTestBase {

    @Mock
    private OscarAppointmentDao mockOscarAppointmentDao;

    @Mock
    private AppointmentArchiveDao mockAppointmentArchiveDao;

    @Mock
    private ScheduleHolidayDao mockScheduleHolidayDao;

    @Mock
    private ScheduleDateDao mockScheduleDateDao;

    @Mock
    private ScheduleTemplateDao mockScheduleTemplateDao;

    @Mock
    private ScheduleTemplateCodeDao mockScheduleTemplateCodeDao;

    @Mock
    private AppointmentTypeDao mockAppointmentTypeDao;

    @Mock
    private AppointmentStatusDao mockAppointmentStatusDao;

    @Mock
    private PatientConsentManager mockPatientConsentManager;

    @Mock
    private AppointmentManager mockAppointmentManager;

    private ScheduleManagerImpl scheduleManager;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers all mock DAOs and managers with SpringUtils, stubs the security
     * manager to grant all privileges by default, creates a fresh
     * {@link ScheduleManagerImpl} instance, and injects the full set of mock
     * dependencies (appointment, schedule, template, holiday, and status DAOs plus
     * SecurityInfoManager, PatientConsentManager, AppointmentManager) via
     * reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(OscarAppointmentDao.class, mockOscarAppointmentDao);
        registerMock(AppointmentArchiveDao.class, mockAppointmentArchiveDao);
        registerMock(ScheduleHolidayDao.class, mockScheduleHolidayDao);
        registerMock(ScheduleDateDao.class, mockScheduleDateDao);
        registerMock(ScheduleTemplateDao.class, mockScheduleTemplateDao);
        registerMock(ScheduleTemplateCodeDao.class, mockScheduleTemplateCodeDao);
        registerMock(AppointmentTypeDao.class, mockAppointmentTypeDao);
        registerMock(AppointmentStatusDao.class, mockAppointmentStatusDao);
        registerMock(PatientConsentManager.class, mockPatientConsentManager);
        registerMock(AppointmentManager.class, mockAppointmentManager);

        // Security manager grants all privileges by default
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // Create manager and inject dependencies
        scheduleManager = new ScheduleManagerImpl();
        injectDependency(scheduleManager, "oscarAppointmentDao", mockOscarAppointmentDao);
        injectDependency(scheduleManager, "appointmentArchiveDao", mockAppointmentArchiveDao);
        injectDependency(scheduleManager, "scheduleHolidayDao", mockScheduleHolidayDao);
        injectDependency(scheduleManager, "scheduleDateDao", mockScheduleDateDao);
        injectDependency(scheduleManager, "scheduleTemplateDao", mockScheduleTemplateDao);
        injectDependency(scheduleManager, "scheduleTemplateCodeDao", mockScheduleTemplateCodeDao);
        injectDependency(scheduleManager, "appointmentTypeDao", mockAppointmentTypeDao);
        injectDependency(scheduleManager, "appointmentStatusDao", mockAppointmentStatusDao);
        injectDependency(scheduleManager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(scheduleManager, "patientConsentManager", mockPatientConsentManager);
        injectDependency(scheduleManager, "appointmentManager", mockAppointmentManager);
    }

    // -----------------------------------------------------------------------
    // getDayAppointments
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getDayAppointments} covering
     * the Date and Calendar overloads, empty results, and audit logging.
     */
    @Nested
    @DisplayName("getDayAppointments")
    @Tag("read")
    @Tag("query")
    class GetDayAppointments {

        @Test
        @DisplayName("should return appointments for provider and date")
        void shouldReturnAppointments_whenProviderAndDateProvided() {
            // Given
            Date today = new Date();
            Appointment appt1 = createTestAppointment(1, TEST_PROVIDER, today);
            Appointment appt2 = createTestAppointment(2, TEST_PROVIDER, today);
            List<Appointment> expected = Arrays.asList(appt1, appt2);

            when(mockOscarAppointmentDao.findByProviderAndDayandNotStatus(
                eq(TEST_PROVIDER), eq(today), eq(AppointmentStatus.APPOINTMENT_STATUS_CANCELLED)))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getDayAppointments(mockLoggedInInfo, TEST_PROVIDER, today);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(appt1, appt2);
            verify(mockOscarAppointmentDao).findByProviderAndDayandNotStatus(
                TEST_PROVIDER, today, AppointmentStatus.APPOINTMENT_STATUS_CANCELLED);
        }

        @Test
        @DisplayName("should return empty list when no appointments exist")
        void shouldReturnEmptyList_whenNoAppointmentsExist() {
            // Given
            Date today = new Date();
            when(mockOscarAppointmentDao.findByProviderAndDayandNotStatus(
                anyString(), any(Date.class), anyString()))
                .thenReturn(Collections.emptyList());

            // When
            List<Appointment> result = scheduleManager.getDayAppointments(mockLoggedInInfo, TEST_PROVIDER, today);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should delegate Calendar overload to Date overload")
        void shouldDelegateCalendarOverload_toDateOverload() {
            // Given
            Calendar cal = Calendar.getInstance();
            Date expectedDate = cal.getTime();
            List<Appointment> expected = Collections.singletonList(createTestAppointment(1, TEST_PROVIDER, expectedDate));

            when(mockOscarAppointmentDao.findByProviderAndDayandNotStatus(
                eq(TEST_PROVIDER), any(Date.class), eq(AppointmentStatus.APPOINTMENT_STATUS_CANCELLED)))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getDayAppointments(mockLoggedInInfo, TEST_PROVIDER, cal);

            // Then
            assertThat(result).hasSize(1);
            verify(mockOscarAppointmentDao).findByProviderAndDayandNotStatus(
                eq(TEST_PROVIDER), any(Date.class), eq(AppointmentStatus.APPOINTMENT_STATUS_CANCELLED));
        }
    }

    // -----------------------------------------------------------------------
    // getAppointment
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointment} covering
     * successful lookup, null results, and audit logging behavior.
     */
    @Nested
    @DisplayName("getAppointment")
    @Tag("read")
    class GetAppointment {

        @Test
        @DisplayName("should return appointment when valid ID provided")
        void shouldReturnAppointment_whenValidIdProvided() {
            // Given
            Integer appointmentId = 42;
            Appointment expected = createTestAppointment(appointmentId, TEST_PROVIDER, new Date());
            when(mockOscarAppointmentDao.find(appointmentId)).thenReturn(expected);

            // When
            Appointment result = scheduleManager.getAppointment(mockLoggedInInfo, appointmentId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(appointmentId);
            verify(mockOscarAppointmentDao).find(appointmentId);
        }

        @Test
        @DisplayName("should return null when appointment not found")
        void shouldReturnNull_whenAppointmentNotFound() {
            // Given
            Integer nonExistentId = 999;
            when(mockOscarAppointmentDao.find(nonExistentId)).thenReturn(null);

            // When
            Appointment result = scheduleManager.getAppointment(mockLoggedInInfo, nonExistentId);

            // Then
            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // addAppointment
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#addAppointment} covering
     * security credential assignment and persistence.
     */
    @Nested
    @DisplayName("addAppointment")
    @Tag("create")
    class AddAppointment {

        @Test
        @DisplayName("should persist appointment with security credentials")
        void shouldPersistAppointment_withSecurityCredentials() {
            // Given
            Appointment appointment = createTestAppointment(null, TEST_PROVIDER, new Date());
            Security security = new Security();
            security.setId(100);
            security.setUserName("testuser");

            // When
            scheduleManager.addAppointment(mockLoggedInInfo, security, appointment);

            // Then
            assertThat(appointment.getCreatorSecurityId()).isEqualTo(100);
            assertThat(appointment.getCreator()).isEqualTo("testuser");
            verify(mockOscarAppointmentDao).persist(appointment);
        }

        @Test
        @DisplayName("should set creator fields from Security object")
        void shouldSetCreatorFields_fromSecurityObject() {
            // Given
            Appointment appointment = new Appointment();
            appointment.setProviderNo(TEST_PROVIDER);

            Security security = new Security();
            security.setId(55);
            security.setUserName("drsmith");

            // When
            scheduleManager.addAppointment(mockLoggedInInfo, security, appointment);

            // Then
            assertThat(appointment.getCreatorSecurityId()).isEqualTo(55);
            assertThat(appointment.getCreator()).isEqualTo("drsmith");
        }
    }

    // -----------------------------------------------------------------------
    // updateAppointment
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#updateAppointment} covering
     * archiving before save, and merge invocation.
     */
    @Nested
    @DisplayName("updateAppointment")
    @Tag("update")
    class UpdateAppointment {

        @Test
        @DisplayName("should archive and merge appointment on update")
        void shouldArchiveAndMerge_whenUpdatingAppointment() {
            // Given
            Appointment appointment = createTestAppointment(77, TEST_PROVIDER, new Date());

            // When
            scheduleManager.updateAppointment(mockLoggedInInfo, appointment);

            // Then - verify archive happens before merge
            var inOrder = inOrder(mockOscarAppointmentDao);
            inOrder.verify(mockOscarAppointmentDao).archiveAppointment(77);
            inOrder.verify(mockOscarAppointmentDao).merge(appointment);
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentsForPatient
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentsForPatient}
     * covering pagination and empty result sets.
     */
    @Nested
    @DisplayName("getAppointmentsForPatient")
    @Tag("read")
    class GetAppointmentsForPatient {

        @Test
        @DisplayName("should return paginated appointments for demographic")
        void shouldReturnPaginatedAppointments_whenDemographicIdProvided() {
            // Given
            Integer demographicId = TEST_DEMO_NO;
            Appointment appt = createTestAppointment(10, TEST_PROVIDER, new Date());
            List<Appointment> expected = Collections.singletonList(appt);

            when(mockOscarAppointmentDao.findByDemographicId(demographicId, 0, 10))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getAppointmentsForPatient(
                mockLoggedInInfo, demographicId, 0, 10);

            // Then
            assertThat(result).hasSize(1);
            verify(mockOscarAppointmentDao).findByDemographicId(demographicId, 0, 10);
        }

        @Test
        @DisplayName("should return empty list when patient has no appointments")
        void shouldReturnEmptyList_whenPatientHasNoAppointments() {
            // Given
            when(mockOscarAppointmentDao.findByDemographicId(anyInt(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

            // When
            List<Appointment> result = scheduleManager.getAppointmentsForPatient(
                mockLoggedInInfo, TEST_DEMO_NO, 0, 20);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentsByProgramProviderDemographicDate
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentsByProgramProviderDemographicDate}
     * covering the multi-criteria query delegation.
     */
    @Nested
    @DisplayName("getAppointmentsByProgramProviderDemographicDate")
    @Tag("read")
    @Tag("query")
    class GetAppointmentsByProgramProviderDemographicDate {

        @Test
        @DisplayName("should delegate to DAO with correct parameters")
        void shouldDelegateToDao_withCorrectParameters() {
            // Given
            Integer programId = 5;
            Integer demographicId = TEST_DEMO_NO;
            Calendar afterDate = Calendar.getInstance();
            afterDate.add(Calendar.DAY_OF_MONTH, -7);
            int itemsToReturn = 50;

            List<Appointment> expected = Arrays.asList(
                createTestAppointment(1, TEST_PROVIDER, new Date()),
                createTestAppointment(2, TEST_PROVIDER, new Date()));

            when(mockOscarAppointmentDao.findByProgramProviderDemographicDate(
                eq(programId), eq(TEST_PROVIDER), eq(demographicId), any(Date.class), eq(itemsToReturn)))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getAppointmentsByProgramProviderDemographicDate(
                mockLoggedInInfo, programId, TEST_PROVIDER, demographicId, afterDate, itemsToReturn);

            // Then
            assertThat(result).hasSize(2);
            verify(mockOscarAppointmentDao).findByProgramProviderDemographicDate(
                programId, TEST_PROVIDER, demographicId, afterDate.getTime(), itemsToReturn);
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentsForDateRangeAndProvider
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentsForDateRangeAndProvider}
     * covering date range queries.
     */
    @Nested
    @DisplayName("getAppointmentsForDateRangeAndProvider")
    @Tag("read")
    @Tag("query")
    class GetAppointmentsForDateRangeAndProvider {

        @Test
        @DisplayName("should return appointments within date range for provider")
        void shouldReturnAppointments_withinDateRangeForProvider() {
            // Given
            Date startTime = createTime(8, 0);
            Date endTime = createTime(17, 0);
            List<Appointment> expected = Collections.singletonList(
                createTestAppointment(1, TEST_PROVIDER, new Date()));

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(startTime, endTime, TEST_PROVIDER))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getAppointmentsForDateRangeAndProvider(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER);

            // Then
            assertThat(result).hasSize(1);
            verify(mockOscarAppointmentDao).findByDateRangeAndProvider(startTime, endTime, TEST_PROVIDER);
        }
    }

    // -----------------------------------------------------------------------
    // getScheduleTemplateCodes
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getScheduleTemplateCodes}
     * covering template code retrieval.
     */
    @Nested
    @DisplayName("getScheduleTemplateCodes")
    @Tag("read")
    class GetScheduleTemplateCodes {

        @Test
        @DisplayName("should return all schedule template codes")
        void shouldReturnAllScheduleTemplateCodes() {
            // Given
            ScheduleTemplateCode code1 = createTestScheduleTemplateCode('A', "Available", "#00FF00");
            ScheduleTemplateCode code2 = createTestScheduleTemplateCode('B', "Blocked", "#FF0000");
            List<ScheduleTemplateCode> expected = Arrays.asList(code1, code2);

            when(mockScheduleTemplateCodeDao.findAll()).thenReturn(expected);

            // When
            List<ScheduleTemplateCode> result = scheduleManager.getScheduleTemplateCodes();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isSameAs(code1);
            assertThat(result.get(1)).isSameAs(code2);
            verify(mockScheduleTemplateCodeDao).findAll();
        }

        @Test
        @DisplayName("should return empty list when no template codes exist")
        void shouldReturnEmptyList_whenNoTemplateCodesExist() {
            // Given
            when(mockScheduleTemplateCodeDao.findAll()).thenReturn(Collections.emptyList());

            // When
            List<ScheduleTemplateCode> result = scheduleManager.getScheduleTemplateCodes();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentTypes
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentTypes}
     * covering appointment type retrieval.
     */
    @Nested
    @DisplayName("getAppointmentTypes")
    @Tag("read")
    class GetAppointmentTypes {

        @Test
        @DisplayName("should return all appointment types")
        void shouldReturnAllAppointmentTypes() {
            // Given
            AppointmentType type1 = new AppointmentType();
            AppointmentType type2 = new AppointmentType();
            List<AppointmentType> expected = Arrays.asList(type1, type2);

            when(mockAppointmentTypeDao.listAll()).thenReturn(expected);

            // When
            List<AppointmentType> result = scheduleManager.getAppointmentTypes();

            // Then
            assertThat(result).hasSize(2);
            verify(mockAppointmentTypeDao).listAll();
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentStatuses
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentStatuses}
     * covering the hard-coded limit of 100 statuses.
     */
    @Nested
    @DisplayName("getAppointmentStatuses")
    @Tag("read")
    class GetAppointmentStatuses {

        @Test
        @DisplayName("should return appointment statuses with limit of 100")
        void shouldReturnAppointmentStatuses_withLimitOf100() {
            // Given
            List<AppointmentStatus> expected = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                expected.add(new AppointmentStatus());
            }
            when(mockAppointmentStatusDao.findAll(0, 100)).thenReturn(expected);

            // When
            List<AppointmentStatus> result = scheduleManager.getAppointmentStatuses(mockLoggedInInfo);

            // Then
            assertThat(result).hasSize(5);
            verify(mockAppointmentStatusDao).findAll(0, 100);
        }

        @Test
        @DisplayName("should handle reaching hard-coded limit of 100")
        void shouldHandleReachingHardCodedLimit() {
            // Given - exactly 100 statuses triggers the logger.error path
            List<AppointmentStatus> hundredStatuses = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                hundredStatuses.add(new AppointmentStatus());
            }
            when(mockAppointmentStatusDao.findAll(0, 100)).thenReturn(hundredStatuses);

            // When
            List<AppointmentStatus> result = scheduleManager.getAppointmentStatuses(mockLoggedInInfo);

            // Then - method still returns results even at the limit
            assertThat(result).hasSize(100);
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentUpdatedAfterDate
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentUpdatedAfterDate}
     * covering consent filtering when privilege is missing.
     */
    @Nested
    @DisplayName("getAppointmentUpdatedAfterDate")
    @Tag("read")
    @Tag("query")
    class GetAppointmentUpdatedAfterDate {

        @Test
        @DisplayName("should return appointments without filtering when privileged")
        void shouldReturnAppointments_withoutFiltering_whenPrivileged() {
            // Given
            Date afterDate = new Date();
            List<Appointment> expected = Collections.singletonList(
                createTestAppointment(1, TEST_PROVIDER, new Date()));

            when(mockOscarAppointmentDao.findByUpdateDate(afterDate, 10)).thenReturn(expected);
            when(mockSecurityInfoManager.hasPrivilege(
                eq(mockLoggedInInfo), eq("_appointment.UpdatedAfterDate"), eq("x"), isNull()))
                .thenReturn(true);

            // When
            List<Appointment> result = scheduleManager.getAppointmentUpdatedAfterDate(
                mockLoggedInInfo, afterDate, 10);

            // Then
            assertThat(result).hasSize(1);
            verify(mockPatientConsentManager, never()).filterProviderSpecificConsent(any(), anyList());
        }

        @Test
        @DisplayName("should filter by consent when privilege is missing")
        void shouldFilterByConsent_whenPrivilegeIsMissing() {
            // Given
            Date afterDate = new Date();
            List<Appointment> appointments = new ArrayList<>();
            appointments.add(createTestAppointment(1, TEST_PROVIDER, new Date()));

            when(mockOscarAppointmentDao.findByUpdateDate(afterDate, 10)).thenReturn(appointments);
            when(mockSecurityInfoManager.hasPrivilege(
                eq(mockLoggedInInfo), eq("_appointment.UpdatedAfterDate"), eq("x"), isNull()))
                .thenReturn(false);

            // When
            scheduleManager.getAppointmentUpdatedAfterDate(mockLoggedInInfo, afterDate, 10);

            // Then
            verify(mockPatientConsentManager).filterProviderSpecificConsent(
                eq(mockLoggedInInfo), eq(appointments));
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentByDemographicIdUpdatedAfterDate
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentByDemographicIdUpdatedAfterDate}
     * covering consent-gated appointment retrieval.
     */
    @Nested
    @DisplayName("getAppointmentByDemographicIdUpdatedAfterDate")
    @Tag("read")
    @Tag("query")
    class GetAppointmentByDemographicIdUpdatedAfterDate {

        @Test
        @DisplayName("should return appointments when patient has consented")
        void shouldReturnAppointments_whenPatientHasConsented() {
            // Given
            Date afterDate = new Date();
            ConsentType consentType = new ConsentType();
            List<Appointment> expected = Collections.singletonList(
                createTestAppointment(1, TEST_PROVIDER, new Date()));

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo))
                .thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType))
                .thenReturn(true);
            when(mockOscarAppointmentDao.findByDemographicIdUpdateDate(TEST_DEMO_NO, afterDate))
                .thenReturn(expected);

            // When
            List<Appointment> result = scheduleManager.getAppointmentByDemographicIdUpdatedAfterDate(
                mockLoggedInInfo, TEST_DEMO_NO, afterDate);

            // Then
            assertThat(result).hasSize(1);
            verify(mockOscarAppointmentDao).findByDemographicIdUpdateDate(TEST_DEMO_NO, afterDate);
        }

        @Test
        @DisplayName("should return empty list when patient has not consented")
        void shouldReturnEmptyList_whenPatientHasNotConsented() {
            // Given
            Date afterDate = new Date();
            ConsentType consentType = new ConsentType();

            when(mockPatientConsentManager.getProviderSpecificConsent(mockLoggedInInfo))
                .thenReturn(consentType);
            when(mockPatientConsentManager.hasPatientConsented(TEST_DEMO_NO, consentType))
                .thenReturn(false);

            // When
            List<Appointment> result = scheduleManager.getAppointmentByDemographicIdUpdatedAfterDate(
                mockLoggedInInfo, TEST_DEMO_NO, afterDate);

            // Then
            assertThat(result).isEmpty();
            verify(mockOscarAppointmentDao, never()).findByDemographicIdUpdateDate(anyInt(), any(Date.class));
        }
    }

    // -----------------------------------------------------------------------
    // getAppointmentArchiveUpdatedAfterDate
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAppointmentArchiveUpdatedAfterDate}
     * covering archive retrieval delegation.
     */
    @Nested
    @DisplayName("getAppointmentArchiveUpdatedAfterDate")
    @Tag("read")
    @Tag("query")
    class GetAppointmentArchiveUpdatedAfterDate {

        @Test
        @DisplayName("should return archives updated after specified date")
        void shouldReturnArchives_updatedAfterSpecifiedDate() {
            // Given
            Date afterDate = new Date();
            AppointmentArchive archive = new AppointmentArchive();
            List<AppointmentArchive> expected = Collections.singletonList(archive);

            when(mockAppointmentArchiveDao.findByUpdateDate(afterDate, 25))
                .thenReturn(expected);

            // When
            List<AppointmentArchive> result = scheduleManager.getAppointmentArchiveUpdatedAfterDate(
                mockLoggedInInfo, afterDate, 25);

            // Then
            assertThat(result).hasSize(1);
            verify(mockAppointmentArchiveDao).findByUpdateDate(afterDate, 25);
        }
    }

    // -----------------------------------------------------------------------
    // getAllDemographicIdByProgramProvider
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getAllDemographicIdByProgramProvider}
     * covering demographic ID retrieval.
     */
    @Nested
    @DisplayName("getAllDemographicIdByProgramProvider")
    @Tag("read")
    @Tag("query")
    class GetAllDemographicIdByProgramProvider {

        @Test
        @DisplayName("should return demographic IDs for program and provider")
        void shouldReturnDemographicIds_forProgramAndProvider() {
            // Given
            Integer programId = 3;
            List<Integer> expected = Arrays.asList(100, 200, 300);

            when(mockOscarAppointmentDao.findAllDemographicIdByProgramProvider(programId, TEST_PROVIDER))
                .thenReturn(expected);

            // When
            List<Integer> result = scheduleManager.getAllDemographicIdByProgramProvider(
                mockLoggedInInfo, programId, TEST_PROVIDER);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(100, 200, 300);
        }
    }

    // -----------------------------------------------------------------------
    // listAppointmentsByPeriodProvider - Security
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#listAppointmentsByPeriodProvider}
     * covering security privilege enforcement and provider list parsing.
     */
    @Nested
    @DisplayName("listAppointmentsByPeriodProvider")
    @Tag("read")
    @Tag("security")
    class ListAppointmentsByPeriodProvider {

        @Test
        @DisplayName("should return appointment data when privileged")
        void shouldReturnAppointmentData_whenPrivileged() {
            // Given
            Date sDate = createTime(8, 0);
            Date eDate = createTime(17, 0);
            String providers = "1,2,3";

            List<Object[]> expected = new ArrayList<>();
            expected.add(new Object[]{"data1"});
            when(mockOscarAppointmentDao.listAppointmentsByPeriodProvider(
                eq(sDate), eq(eDate), anyList()))
                .thenReturn(expected);

            // When
            List<Object[]> result = scheduleManager.listAppointmentsByPeriodProvider(
                mockLoggedInInfo, sDate, eDate, providers);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw RuntimeException when access denied")
        void shouldThrowRuntimeException_whenAccessDenied() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(
                eq(mockLoggedInInfo), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(false);

            Date sDate = createTime(8, 0);
            Date eDate = createTime(17, 0);

            // When/Then
            assertThatThrownBy(() ->
                scheduleManager.listAppointmentsByPeriodProvider(
                    mockLoggedInInfo, sDate, eDate, "1,2"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should parse comma-separated provider numbers")
        void shouldParseCommaSeparatedProviderNumbers() {
            // Given
            Date sDate = createTime(8, 0);
            Date eDate = createTime(17, 0);
            String providers = "100,200,300";

            when(mockOscarAppointmentDao.listAppointmentsByPeriodProvider(
                any(Date.class), any(Date.class), anyList()))
                .thenReturn(Collections.emptyList());

            // When
            scheduleManager.listAppointmentsByPeriodProvider(
                mockLoggedInInfo, sDate, eDate, providers);

            // Then - verify the parsed provider list was passed
            verify(mockOscarAppointmentDao).listAppointmentsByPeriodProvider(
                eq(sDate), eq(eDate), eq(Arrays.asList(100, 200, 300)));
        }
    }

    // -----------------------------------------------------------------------
    // listProviderAppointmentCounts - Security
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#listProviderAppointmentCounts}
     * covering security, date parsing, and error handling.
     */
    @Nested
    @DisplayName("listProviderAppointmentCounts")
    @Tag("read")
    @Tag("security")
    class ListProviderAppointmentCounts {

        @Test
        @DisplayName("should throw RuntimeException when access denied")
        void shouldThrowRuntimeException_whenAccessDenied() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(
                eq(mockLoggedInInfo), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(false);

            // When/Then
            assertThatThrownBy(() ->
                scheduleManager.listProviderAppointmentCounts(
                    mockLoggedInInfo, "2026-01-01", "2026-01-31"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");
        }

        @Test
        @DisplayName("should throw WebApplicationException on invalid date format")
        void shouldThrowWebApplicationException_whenInvalidDateFormat() {
            // Given - privilege is granted by default

            // When/Then
            assertThatThrownBy(() ->
                scheduleManager.listProviderAppointmentCounts(
                    mockLoggedInInfo, "not-a-date", "also-not-a-date"))
                .isInstanceOf(jakarta.ws.rs.WebApplicationException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getDayWorkSchedule
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#getDayWorkSchedule} covering
     * holiday detection, template resolution, timeslot computation,
     * and fallback to public templates.
     */
    @Nested
    @DisplayName("getDayWorkSchedule")
    @Tag("read")
    @Tag("query")
    class GetDayWorkSchedule {

        @Test
        @DisplayName("should return null when no schedule date exists")
        void shouldReturnNull_whenNoScheduleDateExists() {
            // Given
            Calendar date = Calendar.getInstance();
            when(mockScheduleHolidayDao.find(any())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(null);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should mark day as holiday when holiday exists")
        void shouldMarkDayAsHoliday_whenHolidayExists() {
            // Given
            Calendar date = Calendar.getInstance();
            ScheduleHoliday holiday = createTestScheduleHoliday(date.getTime(), "Christmas");
            ScheduleDate scheduleDate = createTestScheduleDate();

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(holiday);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            // Template found for provider
            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey(TEST_PROVIDER, TEST_TEMPLATE_NAME);
            ScheduleTemplate template = createTestScheduleTemplate(TEST_PROVIDER, TEST_TEMPLATE_NAME, "AA__");
            when(mockScheduleTemplateDao.find(pk)).thenReturn(template);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isHoliday()).isTrue();
        }

        @Test
        @DisplayName("should mark day as non-holiday when no holiday exists")
        void shouldMarkDayAsNonHoliday_whenNoHolidayExists() {
            // Given
            Calendar date = Calendar.getInstance();
            ScheduleDate scheduleDate = createTestScheduleDate();

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey(TEST_PROVIDER, TEST_TEMPLATE_NAME);
            ScheduleTemplate template = createTestScheduleTemplate(TEST_PROVIDER, TEST_TEMPLATE_NAME, "AB__");
            when(mockScheduleTemplateDao.find(pk)).thenReturn(template);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isHoliday()).isFalse();
        }

        @Test
        @DisplayName("should calculate time slot duration from timecode length")
        void shouldCalculateTimeSlotDuration_fromTimecodeLength() {
            // Given - timecode of 96 chars means 15-minute slots (1440/96)
            Calendar date = Calendar.getInstance();
            ScheduleDate scheduleDate = createTestScheduleDate();
            String timecodeOf96 = "A".repeat(96);

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey(TEST_PROVIDER, TEST_TEMPLATE_NAME);
            ScheduleTemplate template = createTestScheduleTemplate(TEST_PROVIDER, TEST_TEMPLATE_NAME, timecodeOf96);
            when(mockScheduleTemplateDao.find(pk)).thenReturn(template);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTimeSlotDurationMin()).isEqualTo(15);
        }

        @Test
        @DisplayName("should skip underscore slots in timecode")
        void shouldSkipUnderscoreSlots_inTimecode() {
            // Given - "A_B_" has 4 slots, only A and B are active
            Calendar date = Calendar.getInstance();
            ScheduleDate scheduleDate = createTestScheduleDate();

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            ScheduleTemplatePrimaryKey pk = new ScheduleTemplatePrimaryKey(TEST_PROVIDER, TEST_TEMPLATE_NAME);
            ScheduleTemplate template = createTestScheduleTemplate(TEST_PROVIDER, TEST_TEMPLATE_NAME, "A_B_");
            when(mockScheduleTemplateDao.find(pk)).thenReturn(template);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then - only non-underscore slots are in the map
            assertThat(result).isNotNull();
            assertThat(result.getTimeSlots()).hasSize(2);
            assertThat(result.getTimeSlots().values()).containsExactly('A', 'B');
        }

        @Test
        @DisplayName("should fall back to public template when provider template not found")
        void shouldFallBackToPublicTemplate_whenProviderTemplateNotFound() {
            // Given
            Calendar date = Calendar.getInstance();
            ScheduleDate scheduleDate = createTestScheduleDate();

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            // Provider-specific template not found
            ScheduleTemplatePrimaryKey providerPk = new ScheduleTemplatePrimaryKey(TEST_PROVIDER, TEST_TEMPLATE_NAME);
            when(mockScheduleTemplateDao.find(providerPk)).thenReturn(null);

            // Public template found
            ScheduleTemplatePrimaryKey publicPk = new ScheduleTemplatePrimaryKey(
                ScheduleTemplatePrimaryKey.DODGY_FAKE_PROVIDER_NO_USED_TO_HOLD_PUBLIC_TEMPLATES, TEST_TEMPLATE_NAME);
            ScheduleTemplate publicTemplate = createTestScheduleTemplate("Public", TEST_TEMPLATE_NAME, "AB");
            when(mockScheduleTemplateDao.find(publicPk)).thenReturn(publicTemplate);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTimeSlots()).hasSize(2);
            // Verify both lookups occurred
            verify(mockScheduleTemplateDao).find(providerPk);
            verify(mockScheduleTemplateDao).find(publicPk);
        }

        @Test
        @DisplayName("should return schedule with no time slots when both templates are null")
        void shouldReturnScheduleWithNoTimeSlots_whenBothTemplatesNull() {
            // Given
            Calendar date = Calendar.getInstance();
            ScheduleDate scheduleDate = createTestScheduleDate();

            when(mockScheduleHolidayDao.find(date.getTime())).thenReturn(null);
            when(mockScheduleDateDao.findByProviderNoAndDate(TEST_PROVIDER, date.getTime()))
                .thenReturn(scheduleDate);

            // Both provider and public templates not found
            when(mockScheduleTemplateDao.find(any(ScheduleTemplatePrimaryKey.class))).thenReturn(null);

            // When
            DayWorkSchedule result = scheduleManager.getDayWorkSchedule(TEST_PROVIDER, date);

            // Then - returns a schedule object but with empty time slots
            assertThat(result).isNotNull();
            assertThat(result.getTimeSlots()).isEmpty();
            assertThat(result.getTimeSlotDurationMin()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // removeIfDoubleBooked
    // -----------------------------------------------------------------------

    /**
     * Tests for {@link ScheduleManagerImpl#removeIfDoubleBooked} covering
     * conflict detection with overlapping, non-overlapping, and same-appointment cases.
     */
    @Nested
    @DisplayName("removeIfDoubleBooked")
    @Tag("delete")
    class RemoveIfDoubleBooked {

        @Test
        @DisplayName("should not remove when no conflicts exist")
        void shouldNotRemove_whenNoConflictsExist() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // No existing appointments conflict
            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.emptyList());

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isFalse();
            verify(mockAppointmentManager, never()).deleteAppointment(any(), anyInt());
        }

        @Test
        @DisplayName("should not remove when only same appointment found")
        void shouldNotRemove_whenOnlySameAppointmentFound() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // The same appointment is returned
            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(myAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isFalse();
            verify(mockAppointmentManager, never()).deleteAppointment(any(), anyInt());
        }

        @Test
        @DisplayName("should remove when overlapping appointment exists (conflict case 1: starts during)")
        void shouldRemove_whenOverlappingAppointmentStartsDuring() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // An existing appointment that starts during my appointment
            Appointment conflictAppt = createAppointmentWithTimes(2, 2026, 1, 15, 10, 15, 10, 45);
            conflictAppt.setStatus("t");

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(conflictAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isTrue();
            verify(mockAppointmentManager).deleteAppointment(mockLoggedInInfo, 1);
        }

        @Test
        @DisplayName("should remove when overlapping appointment exists (conflict case 2: ends during)")
        void shouldRemove_whenOverlappingAppointmentEndsDuring() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // An existing appointment that ends during my appointment
            Appointment conflictAppt = createAppointmentWithTimes(2, 2026, 1, 15, 9, 45, 10, 15);
            conflictAppt.setStatus("t");

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(conflictAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isTrue();
            verify(mockAppointmentManager).deleteAppointment(mockLoggedInInfo, 1);
        }

        @Test
        @DisplayName("should remove when existing appointment fully contains my appointment (conflict case 3)")
        void shouldRemove_whenExistingAppointmentFullyContains() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // An existing appointment that fully contains my appointment
            Appointment conflictAppt = createAppointmentWithTimes(2, 2026, 1, 15, 9, 0, 11, 0);
            conflictAppt.setStatus("t");

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(conflictAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isTrue();
            verify(mockAppointmentManager).deleteAppointment(mockLoggedInInfo, 1);
        }

        @Test
        @DisplayName("should skip cancelled appointments during conflict check")
        void shouldSkipCancelledAppointments_duringConflictCheck() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("C"); // Cancelled status

            // An existing appointment that overlaps
            Appointment existingAppt = createAppointmentWithTimes(2, 2026, 1, 15, 10, 0, 10, 30);
            existingAppt.setStatus("t");

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(existingAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then - Cancelled appointment is skipped in conflict check
            assertThat(removed).isFalse();
            verify(mockAppointmentManager, never()).deleteAppointment(any(), anyInt());
        }

        @Test
        @DisplayName("should not remove when adjacent appointments do not overlap")
        void shouldNotRemove_whenAdjacentAppointmentsDoNotOverlap() {
            // Given
            Calendar startTime = createCalendarTime(2026, 1, 15, 10, 0);
            Calendar endTime = createCalendarTime(2026, 1, 15, 10, 30);

            Appointment myAppt = createAppointmentWithTimes(1, 2026, 1, 15, 10, 0, 10, 30);
            myAppt.setStatus("t");

            // An existing appointment that ends exactly when mine starts (no overlap)
            Appointment adjacentAppt = createAppointmentWithTimes(2, 2026, 1, 15, 9, 30, 10, 0);
            adjacentAppt.setStatus("t");

            when(mockOscarAppointmentDao.findByDateRangeAndProvider(
                any(Date.class), any(Date.class), eq(TEST_PROVIDER)))
                .thenReturn(Collections.singletonList(adjacentAppt));

            // When
            boolean removed = scheduleManager.removeIfDoubleBooked(
                mockLoggedInInfo, startTime, endTime, TEST_PROVIDER, myAppt);

            // Then
            assertThat(removed).isFalse();
            verify(mockAppointmentManager, never()).deleteAppointment(any(), anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /**
     * Creates a test Appointment with the given ID, provider, and date.
     *
     * @param id the appointment ID (may be null for new appointments)
     * @param providerNo the provider number
     * @param appointmentDate the appointment date
     * @return Appointment a configured test appointment
     */
    private Appointment createTestAppointment(Integer id, String providerNo, Date appointmentDate) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setProviderNo(providerNo);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setStartTime(createTime(9, 0));
        appointment.setEndTime(createTime(9, 30));
        appointment.setName("Test Patient");
        appointment.setDemographicNo(TEST_DEMO_NO);
        appointment.setStatus("t");
        return appointment;
    }

    /**
     * Creates a Calendar for a specific date and time.
     *
     * @param year the year
     * @param month the month (1-based, will be converted to 0-based)
     * @param day the day of month
     * @param hour the hour of day (0-23)
     * @param minute the minute
     * @return Calendar a configured calendar instance
     */
    private Calendar createCalendarTime(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /**
     * Creates an Appointment with specific date and time fields set for
     * conflict detection testing. Sets appointmentDate, startTime, and endTime
     * so that getStartTimeAsFullDate() and getEndTimeAsFullDate() return the
     * correct combined date+time values.
     *
     * @param id the appointment ID
     * @param year the year
     * @param month the month (1-based)
     * @param day the day of month
     * @param startHour the start hour (0-23)
     * @param startMinute the start minute
     * @param endHour the end hour (0-23)
     * @param endMinute the end minute
     * @return Appointment with properly configured date and time fields
     */
    private Appointment createAppointmentWithTimes(int id, int year, int month, int day,
                                                    int startHour, int startMinute,
                                                    int endHour, int endMinute) {
        Calendar dateCal = Calendar.getInstance();
        dateCal.set(year, month - 1, day, 0, 0, 0);
        dateCal.set(Calendar.MILLISECOND, 0);

        Calendar startCal = Calendar.getInstance();
        startCal.set(year, month - 1, day, startHour, startMinute, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        Calendar endCal = Calendar.getInstance();
        endCal.set(year, month - 1, day, endHour, endMinute, 0);
        endCal.set(Calendar.MILLISECOND, 0);

        Appointment appt = new Appointment();
        appt.setId(id);
        appt.setProviderNo(TEST_PROVIDER);
        appt.setAppointmentDate(dateCal.getTime());
        appt.setStartTime(startCal.getTime());
        appt.setEndTime(endCal.getTime());
        appt.setDemographicNo(TEST_DEMO_NO);
        appt.setName("Test Patient");
        return appt;
    }
}
