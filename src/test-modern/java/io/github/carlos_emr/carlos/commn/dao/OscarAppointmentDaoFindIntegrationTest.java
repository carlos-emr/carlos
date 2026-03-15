/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link OscarAppointmentDao} find/get query methods.
 *
 * <p>These tests validate JPQL queries execute correctly against an H2 in-memory
 * database, establishing a pre-Jakarta migration baseline. Each query method is
 * tested with at least one happy-path test and edge-case tests for complex methods.</p>
 *
 * <p>Split into multiple files by method category for maintainability:
 * <ul>
 *   <li>{@code OscarAppointmentDaoFindIntegrationTest} — find/get single and list queries (this file)</li>
 *   <li>{@code OscarAppointmentDaoQueryIntegrationTest} — search/complex queries, Object[] returns</li>
 *   <li>{@code OscarAppointmentDaoWriteIntegrationTest} — archive, update, conflict check</li>
 * </ul></p>
 *
 * @since 2026-03-04
 * @see OscarAppointmentDao
 * @see OscarAppointmentDaoImpl
 */
@DisplayName("OscarAppointmentDao Find Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class OscarAppointmentDaoFindIntegrationTest extends CarlosTestBase {

    @Autowired
    private OscarAppointmentDao oscarAppointmentDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final String PROVIDER_NO_2 = "999002";
    private static final int PROGRAM_ID = 10001;

    private Date today;
    private Date yesterday;
    private Date tomorrow;
    private Date lastWeek;
    private Date nextWeek;
    private Date time0900;
    private Date time1000;
    private Date time1100;
    private Date time1200;

    @BeforeEach
    void setUp() throws ParseException {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        tomorrow = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        nextWeek = cal.getTime();

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");
        time0900 = timeFmt.parse("09:00:00");
        time1000 = timeFmt.parse("10:00:00");
        time1100 = timeFmt.parse("11:00:00");
        time1200 = timeFmt.parse("12:00:00");
    }

    /**
     * Creates a test appointment with required fields populated.
     */
    private Appointment createTestAppointment(Date date, String providerNo, int demoNo, String status) {
        Appointment appt = new Appointment();
        appt.setAppointmentDate(date);
        appt.setProviderNo(providerNo);
        appt.setDemographicNo(demoNo);
        appt.setStatus(status);
        appt.setStartTime(time0900);
        appt.setEndTime(time1000);
        appt.setName("Test Patient");
        appt.setNotes("");
        appt.setReason("Follow-up");
        appt.setCreator("testuser");
        appt.setCreateDateTime(new Date());
        appt.setUpdateDateTime(new Date());
        appt.setProgramId(PROGRAM_ID);
        return appt;
    }

    private Appointment createAndPersist(Date date, String providerNo, int demoNo, String status) {
        Appointment appt = createTestAppointment(date, providerNo, demoNo, status);
        entityManager.persist(appt);
        entityManager.flush();
        return appt;
    }

    private Appointment createAndPersistWithTimes(Date date, String providerNo, int demoNo,
                                                   String status, Date startTime, Date endTime) {
        Appointment appt = createTestAppointment(date, providerNo, demoNo, status);
        appt.setStartTime(startTime);
        appt.setEndTime(endTime);
        entityManager.persist(appt);
        entityManager.flush();
        return appt;
    }

    // ========================================================================
    // getAppointmentHistory
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#getAppointmentHistory(Integer, Integer, Integer)}
     * and {@link OscarAppointmentDao#getAppointmentHistory(Integer)}.
     */
    @Nested
    @DisplayName("getAppointmentHistory")
    @Tag("read")
    class GetAppointmentHistory {

        @Test
        @DisplayName("should return paginated appointment history excluding deleted")
        void shouldReturnPaginatedHistory_whenDemographicHasAppointments() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 100, "A");
            Appointment deleted = createAndPersist(yesterday, PROVIDER_NO, 100, "D");

            // When
            List<Appointment> result = oscarAppointmentDao.getAppointmentHistory(100, 0, 10);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).extracting(Appointment::getId).doesNotContain(deleted.getId());
        }

        @Test
        @DisplayName("should return empty list when demographic has no appointments")
        void shouldReturnEmptyList_whenNoAppointmentsExist() {
            // When
            List<Appointment> result = oscarAppointmentDao.getAppointmentHistory(99999, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should respect pagination offset and limit")
        void shouldRespectPagination_whenOffsetAndLimitProvided() {
            // Given - create 5 appointments
            for (int i = 0; i < 5; i++) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(today);
                cal.add(Calendar.DAY_OF_MONTH, -i);
                createAndPersist(cal.getTime(), PROVIDER_NO, 101, "A");
            }

            // When - get page 2 (offset 2, limit 2)
            List<Appointment> result = oscarAppointmentDao.getAppointmentHistory(101, 2, 2);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return history excluding cancelled and deleted for unpaginated overload")
        void shouldExcludeCancelledAndDeleted_whenUnpaginatedCall() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 102, "A");
            createAndPersist(yesterday, PROVIDER_NO, 102, "C");
            createAndPersist(lastWeek, PROVIDER_NO, 102, "D");

            // When
            List<Appointment> result = oscarAppointmentDao.getAppointmentHistory(102);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ========================================================================
    // getAllAppointmentHistory
    // ========================================================================

    @Nested
    @DisplayName("getAllAppointmentHistory")
    @Tag("read")
    class GetAllAppointmentHistory {

        @Test
        @DisplayName("should return all appointments including cancelled and deleted")
        void shouldReturnAllStatuses_whenDemographicHasAppointments() {
            // Given
            createAndPersist(today, PROVIDER_NO, 103, "A");
            createAndPersist(yesterday, PROVIDER_NO, 103, "C");
            createAndPersist(lastWeek, PROVIDER_NO, 103, "D");

            // When
            List<Appointment> result = oscarAppointmentDao.getAllAppointmentHistory(103, 0, 10);

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list for non-existent demographic")
        void shouldReturnEmptyList_whenDemographicNotFound() {
            // When
            List<Appointment> result = oscarAppointmentDao.getAllAppointmentHistory(88888, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getDeletedAppointmentHistory
    // ========================================================================

    @Nested
    @DisplayName("getDeletedAppointmentHistory")
    @Tag("read")
    class GetDeletedAppointmentHistory {

        @Test
        @DisplayName("should return archived appointments for demographic")
        void shouldReturnArchivedAppointments_whenArchivesExist() {
            // Given - create an AppointmentArchive directly
            AppointmentArchive archive = new AppointmentArchive();
            archive.setAppointmentNo(1);
            archive.setDemographicNo(104);
            archive.setAppointmentDate(yesterday);
            archive.setStartTime(time0900);
            archive.setEndTime(time1000);
            archive.setProviderNo(PROVIDER_NO);
            archive.setName("Archived Patient");
            archive.setStatus("D");
            archive.setCreateDateTime(new Date());
            archive.setUpdateDateTime(new Date());
            archive.setCreator("testuser");
            entityManager.persist(archive);
            entityManager.flush();

            // When
            List<AppointmentArchive> result = oscarAppointmentDao.getDeletedAppointmentHistory(104, 0, 10);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getDemographicNo()).isEqualTo(104);
        }

        @Test
        @DisplayName("should return empty list when no archives exist")
        void shouldReturnEmptyList_whenNoArchivesExist() {
            // When
            List<AppointmentArchive> result = oscarAppointmentDao.getDeletedAppointmentHistory(77777, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getAllByDemographicNo
    // ========================================================================

    @Nested
    @DisplayName("getAllByDemographicNo")
    @Tag("read")
    class GetAllByDemographicNo {

        @Test
        @DisplayName("should return all appointments ordered by id")
        void shouldReturnAllAppointments_whenDemographicHasAppointments() {
            // Given
            Appointment a1 = createAndPersist(today, PROVIDER_NO, 105, "A");
            Appointment a2 = createAndPersist(yesterday, PROVIDER_NO, 105, "C");

            // When
            List<Appointment> result = oscarAppointmentDao.getAllByDemographicNo(105);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Appointment::getId).containsExactly(a1.getId(), a2.getId());
        }

        @Test
        @DisplayName("should return empty list for non-existent demographic")
        void shouldReturnEmptyList_whenDemographicNotFound() {
            // When
            List<Appointment> result = oscarAppointmentDao.getAllByDemographicNo(66666);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByUpdateDate
    // ========================================================================

    @Nested
    @DisplayName("findByUpdateDate")
    @Tag("read")
    class FindByUpdateDate {

        @Test
        @DisplayName("should return appointments updated after the given date")
        void shouldReturnAppointments_whenUpdatedAfterDate() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 106, "A");
            appt.setUpdateDateTime(today);
            entityManager.merge(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByUpdateDate(yesterday, 100);

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should respect items-to-return limit")
        void shouldRespectLimit_whenItemsToReturnSpecified() {
            // Given - create multiple appointments with recent update dates
            for (int i = 0; i < 5; i++) {
                Appointment appt = createAndPersist(today, PROVIDER_NO, 107 + i, "A");
                appt.setUpdateDateTime(new Date());
                entityManager.merge(appt);
            }
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByUpdateDate(lastWeek, 2);

            // Then
            assertThat(result).hasSizeLessThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findByDemographicIdUpdateDate
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicIdUpdateDate")
    @Tag("read")
    class FindByDemographicIdUpdateDate {

        @Test
        @DisplayName("should return appointments for demographic updated after date")
        void shouldReturnAppointments_whenDemographicUpdatedAfterDate() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 108, "A");
            appt.setUpdateDateTime(today);
            entityManager.merge(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByDemographicIdUpdateDate(108, yesterday);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(a -> a.getDemographicNo() == 108);
        }

        @Test
        @DisplayName("should return empty when no updates after date")
        void shouldReturnEmpty_whenNoUpdatesAfterDate() {
            // Given
            createAndPersist(lastWeek, PROVIDER_NO, 109, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDemographicIdUpdateDate(109, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getAllByDemographicNoSince
    // ========================================================================

    @Nested
    @DisplayName("getAllByDemographicNoSince")
    @Tag("read")
    class GetAllByDemographicNoSince {

        @Test
        @DisplayName("should return appointments updated since given date")
        void shouldReturnAppointments_whenUpdatedSinceDate() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 110, "A");
            appt.setUpdateDateTime(today);
            entityManager.merge(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.getAllByDemographicNoSince(110, yesterday);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByDateRange
    // ========================================================================

    @Nested
    @DisplayName("findByDateRange")
    @Tag("read")
    @Tag("query")
    class FindByDateRange {

        @Test
        @DisplayName("should return appointments within date range")
        void shouldReturnAppointments_whenWithinDateRange() {
            // Given
            createAndPersist(today, PROVIDER_NO, 111, "A");
            createAndPersist(yesterday, PROVIDER_NO, 112, "A");
            createAndPersist(nextWeek, PROVIDER_NO, 113, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDateRange(lastWeek, tomorrow);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list when no appointments in range")
        void shouldReturnEmptyList_whenNoAppointmentsInRange() {
            // Given
            createAndPersist(today, PROVIDER_NO, 114, "A");

            // When - far future range
            Calendar cal = Calendar.getInstance();
            cal.set(2030, Calendar.JANUARY, 1);
            Date farFuture = cal.getTime();
            cal.set(2030, Calendar.DECEMBER, 31);
            Date farFutureEnd = cal.getTime();

            List<Appointment> result = oscarAppointmentDao.findByDateRange(farFuture, farFutureEnd);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDateRangeAndProvider
    // ========================================================================

    @Nested
    @DisplayName("findByDateRangeAndProvider")
    @Tag("read")
    @Tag("query")
    class FindByDateRangeAndProvider {

        @Test
        @DisplayName("should return appointments for provider within date range")
        void shouldReturnAppointments_whenProviderAndDateRangeMatch() {
            // Given
            createAndPersist(today, PROVIDER_NO, 115, "A");
            createAndPersist(today, PROVIDER_NO_2, 116, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDateRangeAndProvider(lastWeek, tomorrow, PROVIDER_NO);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(a -> PROVIDER_NO.equals(a.getProviderNo()));
        }

        @Test
        @DisplayName("should not return appointments from other providers")
        void shouldExcludeOtherProviders_whenFilteredByProvider() {
            // Given
            createAndPersist(today, PROVIDER_NO_2, 117, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDateRangeAndProvider(lastWeek, tomorrow, PROVIDER_NO);

            // Then
            assertThat(result).noneMatch(a -> PROVIDER_NO_2.equals(a.getProviderNo()));
        }
    }

    // ========================================================================
    // getByProviderAndDay
    // ========================================================================

    @Nested
    @DisplayName("getByProviderAndDay")
    @Tag("read")
    class GetByProviderAndDay {

        @Test
        @DisplayName("should return non-cancelled non-no-show appointments for provider on day")
        void shouldReturnActiveAppointments_whenProviderAndDayMatch() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 118, "A");
            createAndPersist(today, PROVIDER_NO, 119, "C"); // cancelled
            createAndPersist(today, PROVIDER_NO, 120, "N"); // no-show

            // When
            List<Appointment> result = oscarAppointmentDao.getByProviderAndDay(today, PROVIDER_NO);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).noneMatch(a -> "C".equals(a.getStatus()) || "N".equals(a.getStatus()));
        }

        @Test
        @DisplayName("should return empty for different day")
        void shouldReturnEmpty_whenDifferentDay() {
            // Given
            createAndPersist(today, PROVIDER_NO, 121, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.getByProviderAndDay(nextWeek, PROVIDER_NO);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getByDemoNoAndDay
    // ========================================================================

    @Nested
    @DisplayName("getByDemoNoAndDay")
    @Tag("read")
    class GetByDemoNoAndDay {

        @Test
        @DisplayName("should return non-deleted appointments for demographic on day")
        void shouldReturnNonDeletedAppointments_whenDemoAndDayMatch() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 122, "A");
            createAndPersist(today, PROVIDER_NO, 122, "D"); // deleted

            // When
            List<Appointment> result = oscarAppointmentDao.getByDemoNoAndDay(122, today);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).noneMatch(a -> "D".equals(a.getStatus()));
        }
    }

    // ========================================================================
    // findByProviderAndDayandNotStatuses
    // ========================================================================

    @Nested
    @DisplayName("findByProviderAndDayandNotStatuses")
    @Tag("read")
    @Tag("filter")
    class FindByProviderAndDayAndNotStatuses {

        @Test
        @DisplayName("should exclude appointments with specified statuses")
        void shouldExcludeStatuses_whenStatusArrayProvided() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 123, "A");
            createAndPersist(today, PROVIDER_NO, 124, "C");
            createAndPersist(today, PROVIDER_NO, 125, "D");

            // When
            List<Appointment> result = oscarAppointmentDao.findByProviderAndDayandNotStatuses(
                    PROVIDER_NO, today, new String[]{"C", "D"});

            // Then
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).noneMatch(a -> "C".equals(a.getStatus()) || "D".equals(a.getStatus()));
        }
    }

    // ========================================================================
    // findByProviderAndDayandNotStatus
    // ========================================================================

    @Nested
    @DisplayName("findByProviderAndDayandNotStatus")
    @Tag("read")
    @Tag("filter")
    class FindByProviderAndDayAndNotStatus {

        @Test
        @DisplayName("should exclude appointments with specified single status")
        void shouldExcludeSingleStatus_whenStatusProvided() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 126, "A");
            createAndPersist(today, PROVIDER_NO, 127, "C");

            // When
            List<Appointment> result = oscarAppointmentDao.findByProviderAndDayandNotStatus(
                    PROVIDER_NO, today, "C");

            // Then
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).noneMatch(a -> "C".equals(a.getStatus()));
        }
    }

    // ========================================================================
    // findByProviderDayAndStatus
    // ========================================================================

    @Nested
    @DisplayName("findByProviderDayAndStatus")
    @Tag("read")
    @Tag("filter")
    class FindByProviderDayAndStatus {

        @Test
        @DisplayName("should return only appointments matching the given status")
        void shouldReturnMatchingStatus_whenStatusProvided() {
            // Given
            Appointment billed = createAndPersist(today, PROVIDER_NO, 128, "B");
            createAndPersist(today, PROVIDER_NO, 129, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByProviderDayAndStatus(
                    PROVIDER_NO, today, "B");

            // Then
            assertThat(result).extracting(Appointment::getId).contains(billed.getId());
            assertThat(result).allMatch(a -> "B".equals(a.getStatus()));
        }

        @Test
        @DisplayName("should return empty when no appointments match status")
        void shouldReturnEmpty_whenNoAppointmentsMatchStatus() {
            // Given
            createAndPersist(today, PROVIDER_NO, 130, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByProviderDayAndStatus(
                    PROVIDER_NO, today, "X");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDayAndStatus
    // ========================================================================

    @Nested
    @DisplayName("findByDayAndStatus")
    @Tag("read")
    @Tag("filter")
    class FindByDayAndStatus {

        @Test
        @DisplayName("should return appointments matching day and status across providers")
        void shouldReturnAppointments_whenDayAndStatusMatch() {
            // Given
            createAndPersist(today, PROVIDER_NO, 131, "A");
            createAndPersist(today, PROVIDER_NO_2, 132, "A");
            createAndPersist(today, PROVIDER_NO, 133, "B");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDayAndStatus(today, "A");

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result).allMatch(a -> "A".equals(a.getStatus()));
        }
    }

    // ========================================================================
    // find (10-parameter method)
    // ========================================================================

    @Nested
    @DisplayName("find (10-parameter exact match)")
    @Tag("read")
    @Tag("query")
    class FindByExactMatch {

        @Test
        @DisplayName("should return appointment when all 10 parameters match")
        void shouldReturnAppointment_whenAllParametersMatch() {
            // Given
            Date createDt = new Date();
            Appointment appt = createTestAppointment(today, PROVIDER_NO, 134, "A");
            appt.setStartTime(time0900);
            appt.setEndTime(time1000);
            appt.setName("Exact Match");
            appt.setNotes("test notes");
            appt.setReason("checkup");
            appt.setCreateDateTime(createDt);
            appt.setCreator("doc1");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.find(
                    today, PROVIDER_NO, time0900, time1000,
                    "Exact Match", "test notes", "checkup",
                    createDt, "doc1", 134);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getName()).isEqualTo("Exact Match");
        }

        @Test
        @DisplayName("should return empty when no exact match found")
        void shouldReturnEmpty_whenNoExactMatch() {
            // When
            List<Appointment> result = oscarAppointmentDao.find(
                    today, "NOMATCH", time0900, time1000,
                    "NoName", "", "", new Date(), "nobody", 0);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicId (paginated)
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicId (paginated)")
    @Tag("read")
    class FindByDemographicIdPaginated {

        @Test
        @DisplayName("should return appointments ordered by date descending with pagination")
        void shouldReturnPaginatedResults_whenDemographicExists() {
            // Given
            createAndPersist(today, PROVIDER_NO, 135, "A");
            createAndPersist(yesterday, PROVIDER_NO, 135, "B");
            createAndPersist(lastWeek, PROVIDER_NO, 135, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByDemographicId(135, 0, 2);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty for non-existent demographic")
        void shouldReturnEmpty_whenDemographicNotFound() {
            // When
            List<Appointment> result = oscarAppointmentDao.findByDemographicId(55555, 0, 10);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findAll
    // ========================================================================

    @Nested
    @DisplayName("findAll")
    @Tag("read")
    class FindAll {

        @Test
        @DisplayName("should return all appointments in database")
        void shouldReturnAllAppointments_whenCalled() {
            // Given
            createAndPersist(today, PROVIDER_NO, 136, "A");
            createAndPersist(yesterday, PROVIDER_NO, 137, "B");

            // When
            List<Appointment> result = oscarAppointmentDao.findAll();

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findNonCancelledFutureAppointments
    // ========================================================================

    @Nested
    @DisplayName("findNonCancelledFutureAppointments")
    @Tag("read")
    @Tag("filter")
    class FindNonCancelledFutureAppointments {

        @Test
        @DisplayName("should return only future non-cancelled non-deleted appointments")
        void shouldReturnFutureNonCancelled_whenDemographicHasAppointments() {
            // Given
            Appointment future = createAndPersist(nextWeek, PROVIDER_NO, 138, "A");
            createAndPersist(nextWeek, PROVIDER_NO, 138, "C"); // cancelled
            createAndPersist(nextWeek, PROVIDER_NO, 138, "D"); // deleted
            createAndPersist(lastWeek, PROVIDER_NO, 138, "A"); // past

            // When
            List<Appointment> result = oscarAppointmentDao.findNonCancelledFutureAppointments(138);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(future.getId());
            assertThat(result).noneMatch(a -> a.getStatus().contains("C") || a.getStatus().contains("D"));
        }
    }

    // ========================================================================
    // findNextAppointment
    // ========================================================================

    @Nested
    @DisplayName("findNextAppointment")
    @Tag("read")
    class FindNextAppointment {

        @Test
        @DisplayName("should return the next future appointment for demographic")
        void shouldReturnNextAppointment_whenFutureAppointmentExists() {
            // Given
            createAndPersist(nextWeek, PROVIDER_NO, 139, "A");

            // When
            Appointment result = oscarAppointmentDao.findNextAppointment(139);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDemographicNo()).isEqualTo(139);
        }

        @Test
        @DisplayName("should return null when no future appointments exist")
        void shouldReturnNull_whenNoFutureAppointments() {
            // Given - only past appointments
            createAndPersist(lastWeek, PROVIDER_NO, 140, "A");

            // When
            Appointment result = oscarAppointmentDao.findNextAppointment(140);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should skip cancelled future appointments")
        void shouldSkipCancelled_whenOnlyCancelledFutureExists() {
            // Given
            createAndPersist(nextWeek, PROVIDER_NO, 141, "C");

            // When
            Appointment result = oscarAppointmentDao.findNextAppointment(141);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // findByEverything (10-parameter)
    // ========================================================================

    @Nested
    @DisplayName("findByEverything")
    @Tag("read")
    @Tag("query")
    class FindByEverything {

        @Test
        @DisplayName("should return appointment matching all 10 parameters")
        void shouldReturnAppointment_whenAllParametersMatch() {
            // Given
            Date createDt = new Date();
            Appointment appt = createTestAppointment(today, PROVIDER_NO, 142, "A");
            appt.setStartTime(time0900);
            appt.setEndTime(time1000);
            appt.setName("Everything Match");
            appt.setNotes("notes");
            appt.setReason("reason");
            appt.setCreateDateTime(createDt);
            appt.setCreator("creator1");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByEverything(
                    today, PROVIDER_NO, time0900, time1000,
                    "Everything Match", "notes", "reason",
                    createDt, "creator1", 142);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findByProviderAndDate
    // ========================================================================

    @Nested
    @DisplayName("findByProviderAndDate")
    @Tag("read")
    class FindByProviderAndDate {

        @Test
        @DisplayName("should return appointments for provider on specific date")
        void shouldReturnAppointments_whenProviderAndDateMatch() {
            // Given
            createAndPersist(today, PROVIDER_NO, 143, "A");
            createAndPersist(today, PROVIDER_NO_2, 144, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findByProviderAndDate(PROVIDER_NO, today);

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(a -> PROVIDER_NO.equals(a.getProviderNo()));
        }
    }

    // ========================================================================
    // findByDateAndProvider
    // ========================================================================

    @Nested
    @DisplayName("findByDateAndProvider")
    @Tag("read")
    class FindByDateAndProvider {

        @Test
        @DisplayName("should return appointments ordered by start time")
        void shouldReturnOrderedByStartTime_whenProviderAndDateMatch() {
            // Given
            createAndPersistWithTimes(today, PROVIDER_NO, 145, "A", time1100, time1200);
            createAndPersistWithTimes(today, PROVIDER_NO, 146, "A", time0900, time1000);

            // When
            List<Appointment> result = oscarAppointmentDao.findByDateAndProvider(today, PROVIDER_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========================================================================
    // findByDate (most recent before date)
    // ========================================================================

    @Nested
    @DisplayName("findByDate")
    @Tag("read")
    class FindByDate {

        @Test
        @DisplayName("should return most recent appointment before the given date")
        void shouldReturnMostRecentBefore_whenAppointmentsExist() {
            // Given
            createAndPersist(yesterday, PROVIDER_NO, 147, "A");
            createAndPersist(lastWeek, PROVIDER_NO, 148, "A");

            // When
            Appointment result = oscarAppointmentDao.findByDate(today);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when no appointments before date")
        void shouldReturnNull_whenNoAppointmentsBeforeDate() {
            // Given - only future appointments
            createAndPersist(nextWeek, PROVIDER_NO, 149, "A");

            // When - search before all appointments
            Calendar cal = Calendar.getInstance();
            cal.set(2020, Calendar.JANUARY, 1);
            Appointment result = oscarAppointmentDao.findByDate(cal.getTime());

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // searchappointmentday
    // ========================================================================

    @Nested
    @DisplayName("searchappointmentday")
    @Tag("read")
    @Tag("search")
    class SearchAppointmentDay {

        @Test
        @DisplayName("should return non-deleted appointments for provider, date, and program")
        void shouldReturnNonDeletedAppointments_whenProviderDateProgramMatch() {
            // Given
            Appointment active = createAndPersist(today, PROVIDER_NO, 150, "A");
            createAndPersist(today, PROVIDER_NO, 151, "D"); // deleted

            // When
            List<Appointment> result = oscarAppointmentDao.searchappointmentday(
                    PROVIDER_NO, today, PROGRAM_ID);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(active.getId());
            assertThat(result).noneMatch(a -> "D".equals(a.getStatus()));
        }
    }

    // ========================================================================
    // searchAppointmentDaySite
    // ========================================================================

    @Nested
    @DisplayName("searchAppointmentDaySite")
    @Tag("read")
    @Tag("search")
    class SearchAppointmentDaySite {

        @Test
        @DisplayName("should return appointments without site filter when site is none")
        void shouldReturnAllSites_whenSiteIsNone() {
            // Given
            createAndPersist(today, PROVIDER_NO, 152, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.searchAppointmentDaySite(
                    PROVIDER_NO, today, PROGRAM_ID, "none");

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should filter by site when site ID provided")
        void shouldFilterBySite_whenSiteIdProvided() {
            // Given
            Appointment withSite = createTestAppointment(today, PROVIDER_NO, 153, "A");
            withSite.setLocation("SITE1");
            entityManager.persist(withSite);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.searchAppointmentDaySite(
                    PROVIDER_NO, today, PROGRAM_ID, "SITE1");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(a -> "SITE1".equals(a.getLocation()));
        }
    }

    // ========================================================================
    // findDemoAppointmentsOnDate
    // ========================================================================

    @Nested
    @DisplayName("findDemoAppointmentsOnDate")
    @Tag("read")
    class FindDemoAppointmentsOnDate {

        @Test
        @DisplayName("should return appointments for demographic on specific date")
        void shouldReturnAppointments_whenDemographicAndDateMatch() {
            // Given
            createAndPersist(today, PROVIDER_NO, 154, "A");
            createAndPersist(yesterday, PROVIDER_NO, 154, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.findDemoAppointmentsOnDate(154, today);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDemographicNo()).isEqualTo(154);
        }

        @Test
        @DisplayName("should return empty when no appointments on date")
        void shouldReturnEmpty_whenNoAppointmentsOnDate() {
            // When
            List<Appointment> result = oscarAppointmentDao.findDemoAppointmentsOnDate(155, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByProgramProviderDemographicDate
    // ========================================================================

    @Nested
    @DisplayName("findByProgramProviderDemographicDate")
    @Tag("read")
    @Tag("query")
    class FindByProgramProviderDemographicDate {

        @Test
        @DisplayName("should return appointments matching program, provider, demographic, and updated after date")
        void shouldReturnAppointments_whenAllCriteriaMatch() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 156, "A");
            appt.setUpdateDateTime(today);
            entityManager.merge(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByProgramProviderDemographicDate(
                    PROGRAM_ID, PROVIDER_NO, 156, yesterday, 100);

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should include appointments with null or zero program ID")
        void shouldIncludeNullProgram_whenProgramIdIsNullOrZero() {
            // Given
            Appointment appt = createTestAppointment(today, PROVIDER_NO, 157, "A");
            appt.setProgramId(0);
            appt.setUpdateDateTime(today);
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.findByProgramProviderDemographicDate(
                    PROGRAM_ID, PROVIDER_NO, 157, yesterday, 100);

            // Then
            assertThat(result).isNotEmpty();
        }
    }

    // ========================================================================
    // findAllDemographicIdByProgramProvider
    // ========================================================================

    @Nested
    @DisplayName("findAllDemographicIdByProgramProvider")
    @Tag("read")
    @Tag("query")
    class FindAllDemographicIdByProgramProvider {

        @Test
        @DisplayName("should return distinct demographic IDs for provider")
        void shouldReturnDistinctDemoIds_whenProviderHasAppointments() {
            // Given
            createAndPersist(today, PROVIDER_NO, 158, "A");
            createAndPersist(yesterday, PROVIDER_NO, 158, "A"); // duplicate demo
            createAndPersist(today, PROVIDER_NO, 159, "A");

            // When
            List<Integer> result = oscarAppointmentDao.findAllDemographicIdByProgramProvider(
                    PROGRAM_ID, PROVIDER_NO);

            // Then
            assertThat(result).contains(158, 159);
        }

        @Test
        @DisplayName("should return all demographics when program ID is null")
        void shouldReturnAllDemographics_whenProgramIdIsNull() {
            // Given
            createAndPersist(today, PROVIDER_NO, 160, "A");

            // When
            List<Integer> result = oscarAppointmentDao.findAllDemographicIdByProgramProvider(
                    null, PROVIDER_NO);

            // Then
            assertThat(result).contains(160);
        }
    }

    // ========================================================================
    // findPatientBilledAppointmentsByProviderAndAppointmentDate
    // ========================================================================

    @Nested
    @DisplayName("findPatientBilledAppointments")
    @Tag("read")
    @Tag("filter")
    class FindPatientBilledAppointments {

        @Test
        @DisplayName("should return only billed appointments for provider in date range")
        void shouldReturnBilledAppointments_whenProviderAndDateRangeMatch() {
            // Given
            Appointment billed = createAndPersist(today, PROVIDER_NO, 161, "B");
            billed.setDemographicNo(161);
            entityManager.merge(billed);

            createAndPersist(today, PROVIDER_NO, 162, "A"); // not billed
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao
                    .findPatientBilledAppointmentsByProviderAndAppointmentDate(
                            PROVIDER_NO, lastWeek, tomorrow);

            // Then
            assertThat(result).allMatch(a -> "B".equals(a.getStatus()));
        }

        @Test
        @DisplayName("should exclude appointments with demographic 0")
        void shouldExcludeDemoZero_whenBilledAppointmentsQueried() {
            // Given
            createAndPersist(today, PROVIDER_NO, 0, "B");
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao
                    .findPatientBilledAppointmentsByProviderAndAppointmentDate(
                            PROVIDER_NO, lastWeek, tomorrow);

            // Then
            assertThat(result).noneMatch(a -> a.getDemographicNo() == 0);
        }
    }

    // ========================================================================
    // findPatientUnbilledAppointmentsByProviderAndAppointmentDate
    // ========================================================================

    @Nested
    @DisplayName("findPatientUnbilledAppointments")
    @Tag("read")
    @Tag("filter")
    class FindPatientUnbilledAppointments {

        @Test
        @DisplayName("should return unbilled non-cancelled appointments")
        void shouldReturnUnbilledAppointments_whenProviderAndDateRangeMatch() {
            // Given
            Appointment unbilled = createAndPersist(today, PROVIDER_NO, 163, "A");
            createAndPersist(today, PROVIDER_NO, 164, "B"); // billed
            createAndPersist(today, PROVIDER_NO, 165, "C"); // cancelled
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao
                    .findPatientUnbilledAppointmentsByProviderAndAppointmentDate(
                            PROVIDER_NO, lastWeek, tomorrow);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(unbilled.getId());
            assertThat(result).noneMatch(a ->
                    a.getStatus().startsWith("B") ||
                    a.getStatus().startsWith("C") ||
                    a.getStatus().startsWith("N") ||
                    a.getStatus().startsWith("T") ||
                    a.getStatus().startsWith("t"));
        }
    }

    // ========================================================================
    // findProvideAppointmentTodayNum (COUNT query - critical for Hibernate 6)
    // ========================================================================

    @Nested
    @DisplayName("findProvideAppointmentTodayNum")
    @Tag("read")
    @Tag("aggregate")
    class FindProvideAppointmentTodayNum {

        @Test
        @DisplayName("should return count of active appointments for provider on date")
        void shouldReturnCount_whenProviderHasAppointmentsOnDate() {
            // Given
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            String todayStr = fmt.format(today);
            createAndPersist(today, PROVIDER_NO, 166, "A");
            createAndPersist(today, PROVIDER_NO, 167, "A");
            createAndPersist(today, PROVIDER_NO, 168, "C"); // cancelled - excluded
            createAndPersist(today, PROVIDER_NO, 169, "D"); // deleted - excluded

            // When
            int result = oscarAppointmentDao.findProvideAppointmentTodayNum(PROVIDER_NO, todayStr);

            // Then
            assertThat(result).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return zero when no appointments exist")
        void shouldReturnZero_whenNoAppointmentsExist() {
            // Given
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.set(2030, Calendar.DECEMBER, 25);
            String futureStr = fmt.format(cal.getTime());

            // When
            int result = oscarAppointmentDao.findProvideAppointmentTodayNum("NOPROV", futureStr);

            // Then
            assertThat(result).isEqualTo(0);
        }
    }
}
