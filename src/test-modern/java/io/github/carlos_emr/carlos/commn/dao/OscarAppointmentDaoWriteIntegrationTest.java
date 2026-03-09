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
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link OscarAppointmentDao} write/mutation methods.
 *
 * <p>Covers conflict checking, appointment archiving, and bulk status updates.
 * These methods modify database state and are important for verifying that
 * Hibernate 6 handles update queries and entity persistence correctly.</p>
 *
 * @since 2026-03-05
 * @see OscarAppointmentDao
 * @see OscarAppointmentDaoImpl
 */
@DisplayName("OscarAppointmentDao Write Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class OscarAppointmentDaoWriteIntegrationTest extends CarlosTestBase {

    @Autowired
    private OscarAppointmentDao oscarAppointmentDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int PROGRAM_ID = 10001;

    private Date today;
    private Date yesterday;
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

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");
        time0900 = timeFmt.parse("09:00:00");
        time1000 = timeFmt.parse("10:00:00");
        time1100 = timeFmt.parse("11:00:00");
        time1200 = timeFmt.parse("12:00:00");
    }

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
    // checkForConflict
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#checkForConflict(Appointment)}.
     * Returns true if an existing appointment overlaps with the given appointment's
     * date, time range, and provider (excluding status 'N' and 'C').
     */
    @Nested
    @DisplayName("checkForConflict")
    @Tag("read")
    class CheckForConflict {

        @Test
        @DisplayName("should return true when conflicting appointment exists")
        void shouldReturnTrue_whenConflictExists() {
            // Given - existing appointment at 09:00-10:00
            createAndPersist(today, PROVIDER_NO, 100, "A");

            // When - check for conflict with same time slot
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isTrue();
        }

        @Test
        @DisplayName("should return false when no conflicting appointment exists")
        void shouldReturnFalse_whenNoConflict() {
            // Given - existing appointment at 09:00-10:00
            createAndPersist(today, PROVIDER_NO, 100, "A");

            // When - check for conflict at different time
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            candidate.setStartTime(time1100);
            candidate.setEndTime(time1200);
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isFalse();
        }

        @Test
        @DisplayName("should ignore cancelled appointments in conflict check")
        void shouldIgnoreCancelled_whenCheckingConflict() {
            // Given - existing cancelled appointment
            createAndPersist(today, PROVIDER_NO, 100, "C");

            // When
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isFalse();
        }

        @Test
        @DisplayName("should ignore no-show appointments in conflict check")
        void shouldIgnoreNoShow_whenCheckingConflict() {
            // Given - existing no-show appointment
            createAndPersist(today, PROVIDER_NO, 100, "N");

            // When
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isFalse();
        }

        @Test
        @DisplayName("should not conflict with different provider")
        void shouldNotConflict_withDifferentProvider() {
            // Given - existing appointment for different provider
            createAndPersist(today, "999002", 100, "A");

            // When
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isFalse();
        }

        @Test
        @DisplayName("should not conflict on different date")
        void shouldNotConflict_onDifferentDate() {
            // Given - existing appointment on different date
            createAndPersist(yesterday, PROVIDER_NO, 100, "A");

            // When
            Appointment candidate = createTestAppointment(today, PROVIDER_NO, 200, "A");
            boolean conflict = oscarAppointmentDao.checkForConflict(candidate);

            // Then
            assertThat(conflict).isFalse();
        }
    }

    // ========================================================================
    // archiveAppointment
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#archiveAppointment(int)}.
     * Copies an appointment to AppointmentArchive table.
     */
    @Nested
    @DisplayName("archiveAppointment")
    @Tag("create")
    class ArchiveAppointment {

        @Test
        @DisplayName("should create archive record with appointment data")
        void shouldCreateArchiveRecord_whenAppointmentExists() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");
            int apptId = appt.getId();

            // When
            oscarAppointmentDao.archiveAppointment(apptId);
            entityManager.flush();

            // Then - verify archive was created
            List<AppointmentArchive> archives = entityManager
                    .createQuery("FROM AppointmentArchive a WHERE a.appointmentNo = :apptNo", AppointmentArchive.class)
                    .setParameter("apptNo", apptId)
                    .getResultList();
            assertThat(archives).hasSize(1);
            AppointmentArchive archive = archives.get(0);
            assertThat(archive.getAppointmentNo()).isEqualTo(apptId);
            assertThat(archive.getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(archive.getDemographicNo()).isEqualTo(100);
            assertThat(archive.getStatus()).isEqualTo("A");
        }

        @Test
        @DisplayName("should not throw when appointment does not exist")
        void shouldNotThrow_whenAppointmentDoesNotExist() {
            // When/Then - should complete without error
            assertThatCode(() -> oscarAppointmentDao.archiveAppointment(99999))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should preserve appointment times in archive")
        void shouldPreserveTimes_inArchive() {
            // Given
            Appointment appt = createAndPersistWithTimes(today, PROVIDER_NO, 100, "A", time0900, time1000);
            int apptId = appt.getId();

            // When
            oscarAppointmentDao.archiveAppointment(apptId);
            entityManager.flush();

            // Then
            List<AppointmentArchive> archives = entityManager
                    .createQuery("FROM AppointmentArchive a WHERE a.appointmentNo = :apptNo", AppointmentArchive.class)
                    .setParameter("apptNo", apptId)
                    .getResultList();
            assertThat(archives).hasSize(1);
            assertThat(archives.get(0).getStartTime()).isEqualTo(time0900);
            assertThat(archives.get(0).getEndTime()).isEqualTo(time1000);
        }
    }

    // ========================================================================
    // updateApptStatus
    // ========================================================================

    /**
     * Tests for {@link OscarAppointmentDao#updateApptStatus(String, String)}.
     * Bulk updates appointment status by comma-separated IDs.
     * Note: This method constructs a dynamic HQL query with sanitized IDs.
     */
    @Nested
    @DisplayName("updateApptStatus")
    @Tag("update")
    class UpdateApptStatus {

        @Test
        @DisplayName("should update status for single appointment")
        void shouldUpdateStatus_forSingleAppointment() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");

            // When
            int updated = oscarAppointmentDao.updateApptStatus(String.valueOf(appt.getId()), "B");

            // Then
            assertThat(updated).isEqualTo(1);
            entityManager.clear();
            Appointment refreshed = entityManager.find(Appointment.class, appt.getId());
            assertThat(refreshed.getStatus()).isEqualTo("B");
        }

        @Test
        @DisplayName("should update status for multiple appointments")
        void shouldUpdateStatus_forMultipleAppointments() {
            // Given
            Appointment appt1 = createAndPersist(today, PROVIDER_NO, 100, "A");
            Appointment appt2 = createAndPersist(today, PROVIDER_NO, 101, "A");
            String ids = appt1.getId() + "," + appt2.getId();

            // When
            int updated = oscarAppointmentDao.updateApptStatus(ids, "C");

            // Then
            assertThat(updated).isEqualTo(2);
            entityManager.clear();
            assertThat(entityManager.find(Appointment.class, appt1.getId()).getStatus()).isEqualTo("C");
            assertThat(entityManager.find(Appointment.class, appt2.getId()).getStatus()).isEqualTo("C");
        }

        @Test
        @DisplayName("should return 0 when no matching IDs")
        void shouldReturnZero_whenNoMatchingIds() {
            // When
            int updated = oscarAppointmentDao.updateApptStatus("99998,99999", "B");

            // Then
            assertThat(updated).isEqualTo(0);
        }

        @Test
        @DisplayName("should return 0 for empty ID string")
        void shouldReturnZero_forEmptyIdString() {
            // When
            int updated = oscarAppointmentDao.updateApptStatus("", "B");

            // Then
            assertThat(updated).isEqualTo(0);
        }

        @Test
        @DisplayName("should filter out non-numeric IDs")
        void shouldFilterOutNonNumericIds_whenMixedInput() {
            // Given
            Appointment appt = createAndPersist(today, PROVIDER_NO, 100, "A");

            // When - mix valid and invalid IDs
            int updated = oscarAppointmentDao.updateApptStatus(
                    appt.getId() + ",abc,xyz", "B");

            // Then - should only update the valid numeric ID
            assertThat(updated).isEqualTo(1);
            entityManager.clear();
            assertThat(entityManager.find(Appointment.class, appt.getId()).getStatus()).isEqualTo("B");
        }

        @Test
        @DisplayName("should return 0 when all IDs are non-numeric")
        void shouldReturnZero_whenAllIdsNonNumeric() {
            // When
            int updated = oscarAppointmentDao.updateApptStatus("abc,def,ghi", "B");

            // Then
            assertThat(updated).isEqualTo(0);
        }
    }
}
