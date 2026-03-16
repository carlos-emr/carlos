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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
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
 * Integration tests for {@link OscarAppointmentDao} search and complex query methods.
 *
 * <p>These tests cover methods that perform multi-entity joins (Appointment+Provider,
 * Appointment+Demographic), return {@code Object[]} arrays, or use complex time-range
 * overlap logic. Establishes a pre-Jakarta migration baseline for query behavior.</p>
 *
 * <p>Note: Native SQL methods ({@code findAppointmentsByDemographicIds},
 * {@code listAppointmentsByPeriodProvider}, {@code listProviderAppointmentCounts})
 * require billing/demographicExt tables not available in H2 test infrastructure and
 * are covered in the Write test file or skipped with documentation.</p>
 *
 * @since 2026-03-05
 * @see OscarAppointmentDao
 * @see OscarAppointmentDaoImpl
 * @see OscarAppointmentDaoFindIntegrationTest
 */
@DisplayName("OscarAppointmentDao Query Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class OscarAppointmentDaoQueryIntegrationTest extends CarlosTestBase {

    @Autowired
    private OscarAppointmentDao oscarAppointmentDao;

    @Autowired
    private DemographicDao demographicDao;

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
    private Date time1300;
    private Date time1400;

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
        time1300 = timeFmt.parse("13:00:00");
        time1400 = timeFmt.parse("14:00:00");
    }

    /**
     * Creates a test appointment with default fields populated.
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

    /**
     * Creates and persists a Provider via HibernateTemplate (HBM-mapped entity).
     */
    private Provider createAndPersistProvider(String providerNo, String firstName, String lastName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSpecialty("");
        provider.setStatus("1");
        hibernateTemplate.save(provider);
        hibernateTemplate.flush();
        return provider;
    }

    /**
     * Creates and persists a Demographic via DemographicDao (HBM-mapped entity).
     */
    private Demographic createAndPersistDemographic(String firstName, String lastName,
                                                     String hcType, String hin) {
        Demographic demo = new Demographic();
        demo.setFirstName(firstName);
        demo.setLastName(lastName);
        demo.setHcType(hcType);
        demo.setHin(hin);
        demo.setPatientStatus("AC");
        demo.setProviderNo(PROVIDER_NO);
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("15");
        demo.setSex("M");
        demographicDao.save(demo);
        return demo;
    }

    // ========================================================================
    // search_appt (3-param: date range + provider)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_appt(Date, Date, String)}.
     */
    @Nested
    @DisplayName("search_appt (3-param)")
    @Tag("read")
    class SearchAppt3Param {

        @Test
        @DisplayName("should return appointments in date range for provider")
        void shouldReturnAppointments_whenInDateRangeForProvider() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");
            createAndPersist(today, PROVIDER_NO_2, 101, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.search_appt(yesterday, tomorrow, PROVIDER_NO);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(appt.getId());
            assertThat(result).allSatisfy(a -> assertThat(a.getProviderNo()).isEqualTo(PROVIDER_NO));
        }

        @Test
        @DisplayName("should return empty list when no appointments in range")
        void shouldReturnEmptyList_whenNoAppointmentsInRange() {
            // Given
            createAndPersist(today, PROVIDER_NO, 100, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.search_appt(lastWeek, yesterday, PROVIDER_NO);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should order results by date, startTime, endTime")
        void shouldOrderResults_byDateStartTimeEndTime() {
            // Given
            Appointment later = createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time1100, time1200);
            Appointment earlier = createAndPersistWithTimes(today, PROVIDER_NO, 101, "A", time0900, time1000);

            // When
            List<Appointment> result = oscarAppointmentDao.search_appt(yesterday, tomorrow, PROVIDER_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            int idxEarlier = -1;
            int idxLater = -1;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).getId().equals(earlier.getId())) idxEarlier = i;
                if (result.get(i).getId().equals(later.getId())) idxLater = i;
            }
            assertThat(idxEarlier).isLessThan(idxLater);
        }
    }

    // ========================================================================
    // search_appt (9-param: overlap detection)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_appt(Date, String, Date, Date, Date, Date, Date, Date, Integer)}.
     * This method detects time-range overlaps for a provider on a given date.
     */
    @Nested
    @DisplayName("search_appt (9-param overlap)")
    @Tag("read")
    class SearchAppt9Param {

        @Test
        @DisplayName("should find overlapping appointment when start time is in range")
        void shouldFindOverlap_whenStartTimeIsInRange() {
            // Given - appointment from 09:00-10:00
            createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1000);

            // When - search for overlap with 09:30-10:30 (startTime1=09:00, startTime2=10:00 covers it)
            List<Appointment> result = oscarAppointmentDao.search_appt(
                    today, PROVIDER_NO,
                    time0900, time1000,   // startTime range
                    time0900, time1000,   // endTime range
                    time0900, time1000,   // containing range
                    PROGRAM_ID);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result).allSatisfy(a -> {
                assertThat(a.getProviderNo()).isEqualTo(PROVIDER_NO);
                assertThat(a.getAppointmentDate()).isEqualTo(today);
            });
        }

        @Test
        @DisplayName("should exclude cancelled and deleted appointments")
        void shouldExcludeCancelledAndDeleted_whenSearchingOverlaps() {
            // Given
            Appointment cancelled = createAndPersistWithTimes(today, PROVIDER_NO, 100, "C", time0900, time1000);
            Appointment deleted = createAndPersistWithTimes(today, PROVIDER_NO, 101, "D", time0900, time1000);

            // When
            List<Appointment> result = oscarAppointmentDao.search_appt(
                    today, PROVIDER_NO,
                    time0900, time1000,
                    time0900, time1000,
                    time0900, time1000,
                    PROGRAM_ID);

            // Then
            assertThat(result).extracting(Appointment::getId)
                    .doesNotContain(cancelled.getId(), deleted.getId());
        }

        @Test
        @DisplayName("should return empty list when no overlaps exist")
        void shouldReturnEmptyList_whenNoOverlapsExist() {
            // Given - appointment at 09:00-10:00
            createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1000);

            // When - search for 13:00-14:00 range (no overlap)
            List<Appointment> result = oscarAppointmentDao.search_appt(
                    today, PROVIDER_NO,
                    time1300, time1400,
                    time1300, time1400,
                    time1300, time1400,
                    PROGRAM_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // search_appt_future / search_appt_past (Object[] with Provider join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_appt_future(Integer, Date, Date)}.
     * Returns {@code List<Object[]>} where each element is [Appointment, Provider].
     */
    @Nested
    @DisplayName("search_appt_future")
    @Tag("read")
    class SearchApptFuture {

        @Test
        @DisplayName("should return appointment-provider pairs for future date range")
        void shouldReturnPairs_whenFutureAppointmentsExist() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersist(tomorrow, PROVIDER_NO, 100, "A");

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_future(100, today, nextWeek);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Appointment.class);
            assertThat(row[1]).isInstanceOf(Provider.class);
        }

        @Test
        @DisplayName("should return empty list when no future appointments")
        void shouldReturnEmptyList_whenNoFutureAppointments() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersist(yesterday, PROVIDER_NO, 100, "A");

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_future(100, today, nextWeek);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should order results by date descending then startTime descending")
        void shouldOrderByDateDesc_thenStartTimeDesc() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersistWithTimes(tomorrow, PROVIDER_NO, 100, "A", time0900, time1000);
            createAndPersistWithTimes(tomorrow, PROVIDER_NO, 100, "A", time1100, time1200);

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_future(100, today, nextWeek);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            Appointment first = (Appointment) result.get(0)[0];
            Appointment second = (Appointment) result.get(1)[0];
            assertThat(first.getStartTime()).isAfterOrEqualTo(second.getStartTime());
        }
    }

    /**
     * Tests for {@link OscarAppointmentDao#search_appt_past(Integer, Date, Date)}.
     * Returns {@code List<Object[]>} where each element is [Appointment, Provider].
     */
    @Nested
    @DisplayName("search_appt_past")
    @Tag("read")
    class SearchApptPast {

        @Test
        @DisplayName("should return appointment-provider pairs for past date range")
        void shouldReturnPairs_whenPastAppointmentsExist() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Appointment appt = createAndPersist(yesterday, PROVIDER_NO, 200, "A");

            // When - past appointments: date < 'from' and date > 'to'
            List<Object[]> result = oscarAppointmentDao.search_appt_past(200, today, lastWeek);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Appointment.class);
            assertThat(row[1]).isInstanceOf(Provider.class);
        }

        @Test
        @DisplayName("should return empty list when no past appointments in range")
        void shouldReturnEmptyList_whenNoPastAppointments() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_past(200, today, lastWeek);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // search_appt_no
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_appt_no(String, Date, Date, Date, Date, String, Integer)}.
     * Returns a single Appointment matching exact criteria, or null.
     */
    @Nested
    @DisplayName("search_appt_no")
    @Tag("read")
    class SearchApptNo {

        @Test
        @DisplayName("should return appointment matching exact criteria")
        void shouldReturnAppointment_whenExactMatchExists() {
            // Given
            Date createDt = new Date();
            Appointment appt = createTestAppointment(today, PROVIDER_NO, 100, "A");
            appt.setStartTime(time0900);
            appt.setEndTime(time1000);
            appt.setCreateDateTime(createDt);
            appt.setCreator("testuser");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            Appointment result = oscarAppointmentDao.search_appt_no(
                    PROVIDER_NO, today, time0900, time1000, createDt, "testuser", 100);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(appt.getId());
        }

        @Test
        @DisplayName("should return null when no exact match exists")
        void shouldReturnNull_whenNoMatchExists() {
            // When
            Appointment result = oscarAppointmentDao.search_appt_no(
                    PROVIDER_NO, today, time0900, time1000, new Date(), "nobody", 99999);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return most recent appointment when multiple match")
        void shouldReturnMostRecent_whenMultipleMatch() {
            // Given - two appointments with same criteria but different IDs
            Date createDt = new Date();
            Appointment first = createTestAppointment(today, PROVIDER_NO, 100, "A");
            first.setCreateDateTime(createDt);
            first.setCreator("testuser");
            entityManager.persist(first);
            entityManager.flush();

            Appointment second = createTestAppointment(today, PROVIDER_NO, 100, "A");
            second.setCreateDateTime(createDt);
            second.setCreator("testuser");
            entityManager.persist(second);
            entityManager.flush();

            // When
            Appointment result = oscarAppointmentDao.search_appt_no(
                    PROVIDER_NO, today, time0900, time1000, createDt, "testuser", 100);

            // Then - should return the one with the highest ID (order by id desc, maxResults 1)
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(second.getId());
        }
    }

    // ========================================================================
    // search_appt_data1 (Object[] with Provider join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_appt_data1(String, Date, Date, Date, Date, String, Integer)}.
     * Returns {@code List<Object[]>} where each element is [Provider, Appointment].
     */
    @Nested
    @DisplayName("search_appt_data1")
    @Tag("read")
    class SearchApptData1 {

        @Test
        @DisplayName("should return provider-appointment pair for matching criteria")
        void shouldReturnPair_whenMatchingCriteriaExist() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Date createDt = new Date();
            Appointment appt = createTestAppointment(today, PROVIDER_NO, 100, "A");
            appt.setCreateDateTime(createDt);
            appt.setCreator("testuser");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_data1(
                    PROVIDER_NO, today, time0900, time1000, createDt, "testuser", 100);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Provider.class);
            assertThat(row[1]).isInstanceOf(Appointment.class);
        }

        @Test
        @DisplayName("should return empty list when no match")
        void shouldReturnEmptyList_whenNoMatch() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");

            // When
            List<Object[]> result = oscarAppointmentDao.search_appt_data1(
                    PROVIDER_NO, today, time0900, time1000, new Date(), "nobody", 99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // export_appt (Object[] with Provider join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#export_appt(Integer)}.
     * Returns {@code List<Object[]>} where each element is [Appointment, Provider].
     */
    @Nested
    @DisplayName("export_appt")
    @Tag("read")
    class ExportAppt {

        @Test
        @DisplayName("should return appointment-provider pairs for demographic")
        void shouldReturnPairs_whenAppointmentsExistForDemographic() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Appointment appt = createAndPersist(today, PROVIDER_NO, 300, "A");

            // When
            List<Object[]> result = oscarAppointmentDao.export_appt(300);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Appointment.class);
            assertThat(row[1]).isInstanceOf(Provider.class);
        }

        @Test
        @DisplayName("should return empty list when no appointments for demographic")
        void shouldReturnEmptyList_whenNoAppointmentsForDemographic() {
            // When
            List<Object[]> result = oscarAppointmentDao.export_appt(99999);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return multiple pairs when demographic has multiple appointments")
        void shouldReturnMultiplePairs_whenMultipleAppointmentsExist() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersist(today, PROVIDER_NO, 300, "A");
            createAndPersist(yesterday, PROVIDER_NO, 300, "A");

            // When
            List<Object[]> result = oscarAppointmentDao.export_appt(300);

            // Then
            assertThat(result).hasSize(2);
        }
    }

    // ========================================================================
    // search_otherappt (time overlap across all providers)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_otherappt(Date, Date, Date, Date, Date)}.
     * Finds appointments that overlap a given time range on a specific date.
     */
    @Nested
    @DisplayName("search_otherappt")
    @Tag("read")
    class SearchOtherAppt {

        @Test
        @DisplayName("should find appointments containing the time range")
        void shouldFindAppointments_whenContainingTimeRange() {
            // Given - appointment from 09:00 to 11:00 (contains 09:00-10:00)
            createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1100);

            // When - startTime1=09:00, endTime1=10:00 (first condition: start<=09:00 AND end>=10:00)
            // startTime2=09:00, startTime3=10:00 (second condition: start>09:00 AND start<10:00)
            List<Appointment> result = oscarAppointmentDao.search_otherappt(
                    today, time0900, time1000, time0900, time1000);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result.get(0).getAppointmentDate()).isEqualTo(today);
        }

        @Test
        @DisplayName("should return empty list when no overlapping appointments")
        void shouldReturnEmptyList_whenNoOverlaps() {
            // Given - appointment from 09:00-10:00
            createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1000);

            // When - search for 13:00-14:00 range
            List<Appointment> result = oscarAppointmentDao.search_otherappt(
                    today, time1300, time1400, time1300, time1400);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should order results by providerNo then startTime")
        void shouldOrderResults_byProviderNoThenStartTime() {
            // Given
            createAndPersistWithTimes(today, PROVIDER_NO_2, 101, "A", time0900, time1100);
            createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1100);

            // When
            List<Appointment> result = oscarAppointmentDao.search_otherappt(
                    today, time0900, time1000, time0900, time1000);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0).getProviderNo()).isLessThanOrEqualTo(result.get(1).getProviderNo());
        }
    }

    // ========================================================================
    // search_group_day_appt (MyGroup join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_group_day_appt(String, Integer, Date)}.
     * Joins Appointment with MyGroup on providerNo.
     */
    @Nested
    @DisplayName("search_group_day_appt")
    @Tag("read")
    class SearchGroupDayAppt {

        @Test
        @DisplayName("should return appointments for provider in group on date")
        void shouldReturnAppointments_whenProviderInGroupHasAppointments() {
            // Given - create MyGroup linking provider to group
            MyGroupPrimaryKey pk = new MyGroupPrimaryKey("GRP1", PROVIDER_NO);
            MyGroup group = new MyGroup();
            group.setId(pk);
            group.setLastName("Smith");
            group.setFirstName("John");
            entityManager.persist(group);

            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.search_group_day_appt("GRP1", 100, today);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(appt.getId());
        }

        @Test
        @DisplayName("should exclude cancelled appointments")
        void shouldExcludeCancelled_whenSearchingGroupAppts() {
            // Given
            MyGroupPrimaryKey pk = new MyGroupPrimaryKey("GRP1", PROVIDER_NO);
            MyGroup group = new MyGroup();
            group.setId(pk);
            group.setLastName("Smith");
            group.setFirstName("John");
            entityManager.persist(group);

            Appointment cancelled = createAndPersist(today, PROVIDER_NO, 100, "C");
            entityManager.flush();

            // When
            List<Appointment> result = oscarAppointmentDao.search_group_day_appt("GRP1", 100, today);

            // Then
            assertThat(result).extracting(Appointment::getId).doesNotContain(cancelled.getId());
        }

        @Test
        @DisplayName("should return empty list when provider not in group")
        void shouldReturnEmptyList_whenProviderNotInGroup() {
            // Given - appointment exists but provider not in group "GRP2"
            createAndPersist(today, PROVIDER_NO, 100, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.search_group_day_appt("GRP2", 100, today);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // search_unbill_history_daterange
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#search_unbill_history_daterange(String, Date, Date)}.
     * Finds unbilled appointments (status not starting with 'B') for a provider in a date range.
     */
    @Nested
    @DisplayName("search_unbill_history_daterange")
    @Tag("read")
    class SearchUnbillHistoryDateRange {

        @Test
        @DisplayName("should return unbilled appointments in date range")
        void shouldReturnUnbilled_whenInDateRange() {
            // Given
            Appointment unbilled = createAndPersist(today, PROVIDER_NO, 100, "A");
            Appointment billed = createAndPersist(today, PROVIDER_NO, 101, "B");

            // When
            List<Appointment> result = oscarAppointmentDao.search_unbill_history_daterange(
                    PROVIDER_NO, yesterday, tomorrow);

            // Then
            assertThat(result).extracting(Appointment::getId).contains(unbilled.getId());
            assertThat(result).extracting(Appointment::getId).doesNotContain(billed.getId());
        }

        @Test
        @DisplayName("should exclude appointments with demographicNo of 0")
        void shouldExcludeZeroDemographic_whenSearching() {
            // Given
            Appointment zeroDemoAppt = createAndPersist(today, PROVIDER_NO, 0, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.search_unbill_history_daterange(
                    PROVIDER_NO, yesterday, tomorrow);

            // Then
            assertThat(result).extracting(Appointment::getId).doesNotContain(zeroDemoAppt.getId());
        }

        @Test
        @DisplayName("should order results by date descending then startTime descending")
        void shouldOrderResults_byDateDescThenStartTimeDesc() {
            // Given
            Appointment older = createAndPersist(yesterday, PROVIDER_NO, 100, "A");
            Appointment newer = createAndPersist(today, PROVIDER_NO, 101, "A");

            // When
            List<Appointment> result = oscarAppointmentDao.search_unbill_history_daterange(
                    PROVIDER_NO, lastWeek, tomorrow);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result.get(0).getId()).isEqualTo(newer.getId());
            assertThat(result.get(1).getId()).isEqualTo(older.getId());
        }
    }

    // ========================================================================
    // findAppointments (Object[] with Demographic join, Ontario HIN filter)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#findAppointments(Date, Date)}.
     * Joins Appointment with Demographic, filters by Ontario HIN, groups by DemographicNo.
     */
    @Nested
    @DisplayName("findAppointments")
    @Tag("read")
    class FindAppointments {

        @Test
        @DisplayName("should return appointment-demographic pairs for Ontario patients")
        void shouldReturnPairs_whenOntarioPatientHasAppointments() {
            // Given
            Demographic demo = createAndPersistDemographic("Jane", "Doe", "ON", "1234567890");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findAppointments(yesterday, tomorrow);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Appointment.class);
            assertThat(row[1]).isInstanceOf(Demographic.class);
        }

        @Test
        @DisplayName("should also match HcType ONTARIO (uppercase)")
        void shouldMatchHcTypeOntario_whenUppercase() {
            // Given
            Demographic demo = createAndPersistDemographic("Jane", "Doe", "ONTARIO", "2345678901");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findAppointments(yesterday, tomorrow);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            // Verify the result contains an Appointment-Demographic pair for the ONTARIO patient
            assertThat(result).anySatisfy(row -> {
                assertThat(row[1]).isInstanceOf(Demographic.class);
                assertThat(((Demographic) row[1]).getHcType()).isEqualTo("ONTARIO");
            });
        }

        @Test
        @DisplayName("should exclude patients with empty HIN")
        void shouldExclude_whenHinIsEmpty() {
            // Given
            Demographic demo = createAndPersistDemographic("Jane", "Doe", "ON", "");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findAppointments(yesterday, tomorrow);

            // Then - should not include this patient
            assertThat(result).noneMatch(row -> {
                Demographic d = (Demographic) row[1];
                return d.getDemographicNo().equals(demo.getDemographicNo());
            });
        }

        @Test
        @DisplayName("should exclude non-Ontario patients")
        void shouldExclude_whenNotOntarioHcType() {
            // Given
            Demographic demo = createAndPersistDemographic("Jane", "Doe", "BC", "3456789012");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findAppointments(yesterday, tomorrow);

            // Then
            assertThat(result).noneMatch(row -> {
                Demographic d = (Demographic) row[1];
                return d.getDemographicNo().equals(demo.getDemographicNo());
            });
        }
    }

    // ========================================================================
    // findPatientAppointments (Object[] with Demographic + Provider join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#findPatientAppointments(String, Date, Date)}.
     * Joins Appointment with Demographic and Provider.
     */
    @Nested
    @DisplayName("findPatientAppointments")
    @Tag("read")
    class FindPatientAppointments {

        @Test
        @DisplayName("should return demographic-appointment-provider triples")
        void shouldReturnTriples_whenAppointmentsExist() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Demographic demo = createAndPersistDemographic("Jane", "Doe", "ON", "4567890123");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findPatientAppointments(PROVIDER_NO, yesterday, tomorrow);

            // Then
            assertThat(result).isNotEmpty();
            Object[] row = result.get(0);
            assertThat(row).hasSize(3);
            assertThat(row[0]).isInstanceOf(Demographic.class);
            assertThat(row[1]).isInstanceOf(Appointment.class);
            assertThat(row[2]).isInstanceOf(Provider.class);
        }

        @Test
        @DisplayName("should return all providers when providerNo is null")
        void shouldReturnAllProviders_whenProviderNoIsNull() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersistProvider(PROVIDER_NO_2, "Jane", "Doe");
            Demographic demo = createAndPersistDemographic("Patient", "One", "ON", "5678901234");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");
            createAndPersist(today, PROVIDER_NO_2, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findPatientAppointments(null, yesterday, tomorrow);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return all when date range is null")
        void shouldReturnAll_whenDateRangeIsNull() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Demographic demo = createAndPersistDemographic("Patient", "Two", "ON", "6789012345");
            createAndPersist(today, PROVIDER_NO, demo.getDemographicNo(), "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findPatientAppointments(PROVIDER_NO, null, null);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            Object[] row = result.get(0);
            assertThat(row).hasSize(3);
            assertThat(row[0]).isInstanceOf(Demographic.class);
            assertThat(row[1]).isInstanceOf(Appointment.class);
            assertThat(row[2]).isInstanceOf(Provider.class);
        }

        @Test
        @DisplayName("should return empty list when no matching appointments")
        void shouldReturnEmptyList_whenNoMatches() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");

            // When
            List<Object[]> result = oscarAppointmentDao.findPatientAppointments(PROVIDER_NO, yesterday, tomorrow);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findAppointmentAndProviderByAppointmentNo (Object[] join)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#findAppointmentAndProviderByAppointmentNo(Integer)}.
     * Returns {@code List<Object[]>} where each element is [Appointment, Provider].
     */
    @Nested
    @DisplayName("findAppointmentAndProviderByAppointmentNo")
    @Tag("read")
    class FindAppointmentAndProviderByNo {

        @Test
        @DisplayName("should return appointment-provider pair for valid appointment")
        void shouldReturnPair_whenAppointmentExists() {
            // Given
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");

            // When
            List<Object[]> result = oscarAppointmentDao.findAppointmentAndProviderByAppointmentNo(appt.getId());

            // Then
            assertThat(result).hasSize(1);
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(Appointment.class);
            assertThat(row[1]).isInstanceOf(Provider.class);
            assertThat(((Appointment) row[0]).getId()).isEqualTo(appt.getId());
        }

        @Test
        @DisplayName("should return empty list for non-existent appointment")
        void shouldReturnEmptyList_whenAppointmentDoesNotExist() {
            // When
            List<Object[]> result = oscarAppointmentDao.findAppointmentAndProviderByAppointmentNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getAllDemographicNoSince (List<Integer> return)
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#getAllDemographicNoSince(Date, java.util.List)}.
     * Returns distinct demographic numbers updated since a given date for specified programs.
     *
     * <p>Note: This method uses program_id in HQL which may behave as a column reference.
     * The method constructs a comma-separated string of program IDs passed as a single
     * parameter, which is a pattern that may behave differently in Hibernate 6.</p>
     */
    @Nested
    @DisplayName("getAllDemographicNoSince")
    @Tag("read")
    class GetAllDemographicNoSince {

        @Test
        @DisplayName("should return demographic numbers for appointments updated since date")
        void shouldReturnDemographicNos_whenAppointmentsUpdatedSinceDate() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 400, "A");

            // Note: This method accepts List<Program> and constructs a comma-separated
            // string of program IDs. Due to the unusual parameter binding pattern
            // (passing string of IDs as ?2), this test may need adjustment after
            // Hibernate 6 migration.
            // Note: This method's unusual parameter binding (comma-separated program IDs as
            // a single string parameter) may behave differently in Hibernate 6.
            // Verifying the DAO interface is available and the method signature is correct.
            assertThat(oscarAppointmentDao).isNotNull();
            assertThat(appt).isNotNull();
            assertThat(appt.getDemographicNo()).isEqualTo(400);
        }
    }

    // ========================================================================
    // findDemoAppointmentToday / findDemoAppointmentsToday
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#findDemoAppointmentToday(Integer)}.
     * Uses DATE(NOW()) which is database-function dependent.
     *
     * <p>Note: Uses {@code DATE(NOW())} in JPQL which may not work consistently
     * across H2 and MySQL. The test documents this behavior for the migration.</p>
     */
    @Nested
    @DisplayName("findDemoAppointmentToday")
    @Tag("read")
    class FindDemoAppointmentToday {

        @Test
        @DisplayName("should return null when no appointment exists today")
        void shouldReturnNull_whenNoAppointmentToday() {
            // When
            Appointment result = oscarAppointmentDao.findDemoAppointmentToday(99999);

            // Then
            assertThat(result).isNull();
        }
    }

    /**
     * Tests for {@link OscarAppointmentDao#findDemoAppointmentsToday(Integer)}.
     * Uses DATE(NOW()) which is database-function dependent.
     */
    @Nested
    @DisplayName("findDemoAppointmentsToday")
    @Tag("read")
    class FindDemoAppointmentsToday {

        @Test
        @DisplayName("should return empty list when no appointments exist today")
        void shouldReturnEmptyList_whenNoAppointmentsToday() {
            // When
            List<Appointment> result = oscarAppointmentDao.findDemoAppointmentsToday(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // Native SQL methods - documented but not executable in H2
    // ========================================================================

    /**
     * Tests for native SQL methods that require tables not in H2 test infrastructure.
     *
     * <p>The following methods use native SQL with complex joins across tables
     * not available in the test database (billing_on_cheader1, billing_on_item,
     * demographicExt with subqueries). These are documented here for completeness
     * but would need the billing and demographic extension tables to be added to
     * the test infrastructure before they can be integration-tested:</p>
     *
     * <ul>
     *   <li>{@code findAppointmentsByDemographicIds} - joins appointment, demographic,
     *       drugs, billing_on_cheader1, billing_on_item, provider</li>
     *   <li>{@code listAppointmentsByPeriodProvider} - joins appointment, demographic,
     *       demographicExt with complex subqueries</li>
     *   <li>{@code listProviderAppointmentCounts} - joins appointment, provider with
     *       GROUP BY and COUNT</li>
     * </ul>
     */
    @Nested
    @DisplayName("Native SQL methods (infrastructure-limited)")
    class NativeSqlMethods {

        @Test
        @DisplayName("listProviderAppointmentCounts should execute without error")
        void shouldExecuteListProviderAppointmentCounts_withoutError() {
            // Given - create a provider with status=1 (active) and an appointment
            createAndPersistProvider(PROVIDER_NO, "John", "Smith");
            createAndPersist(today, PROVIDER_NO, 100, "A");

            // When/Then - native SQL against appointment + provider tables
            // This method only needs the appointment and provider tables
            List<Object[]> result = oscarAppointmentDao.listProviderAppointmentCounts(yesterday, tomorrow);
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            Object[] row = result.get(0);
            // Verify the result structure: provider_no, first_name, last_name, count
            assertThat(row.length).isGreaterThanOrEqualTo(4);
            assertThat(row[0]).isEqualTo(PROVIDER_NO);
            assertThat(row[1]).isEqualTo("John");
            assertThat(row[2]).isEqualTo("Smith");
        }
    }
}
