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

import io.github.carlos_emr.carlos.commn.model.Admission;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AdmissionDao} query methods.
 *
 * <p>Validates JPQL queries for program admission data against H2. Covers
 * current/discharged admission lookups, date range queries, and program-based
 * filtering critical for Hibernate 6 migration.</p>
 *
 * @since 2026-03-05
 * @see AdmissionDao
 * @see AdmissionDaoImpl
 */
@DisplayName("AdmissionDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admission")
@Transactional
public class AdmissionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private AdmissionDao admissionDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final int DEMO_NO = 100;
    private static final int PROGRAM_ID = 10001;
    private static final int PROGRAM_ID_2 = 10002;

    private Date today;
    private Date yesterday;
    private Date lastWeek;
    private Date nextWeek;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 7);
        nextWeek = cal.getTime();

        // Create parent records required by FK constraints on admission table
        entityManager.createNativeQuery(
                "INSERT INTO demographic (demographic_no, first_name, last_name, sex) VALUES (:id, 'Test', 'Patient', 'M')")
                .setParameter("id", DEMO_NO)
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO demographic (demographic_no, first_name, last_name, sex) VALUES (:id, 'Test', 'Patient2', 'F')")
                .setParameter("id", DEMO_NO + 1)
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO program (id) VALUES (:id)")
                .setParameter("id", PROGRAM_ID)
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO program (id) VALUES (:id)")
                .setParameter("id", PROGRAM_ID_2)
                .executeUpdate();
        entityManager.flush();
    }

    private Admission createAdmission(int clientId, int programId, String status, Date admissionDate) {
        Admission adm = new Admission();
        adm.setClientId(clientId);
        adm.setProgramId(programId);
        adm.setAdmissionStatus(status);
        adm.setAdmissionDate(admissionDate);
        adm.setProviderNo(PROVIDER_NO);
        adm.setAdmissionNotes("");
        adm.setAutomaticDischarge(false);
        adm.setTemporaryAdmissionFlag(false);
        return adm;
    }

    private Admission createAndPersist(int clientId, int programId, String status, Date admissionDate) {
        Admission adm = createAdmission(clientId, programId, status, admissionDate);
        entityManager.persist(adm);
        entityManager.flush();
        return adm;
    }

    // ========================================================================
    // getCurrentAdmission
    // ========================================================================

    @Nested
    @DisplayName("getCurrentAdmission")
    @Tag("read")
    class GetCurrentAdmission {

        @Test
        @DisplayName("should return current admission for program and client")
        void shouldReturnCurrentAdmission_whenExists() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            Admission result = admissionDao.getCurrentAdmission(PROGRAM_ID, DEMO_NO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAdmissionStatus()).isEqualTo(Admission.STATUS_CURRENT);
        }

        @Test
        @DisplayName("should return null when no current admission")
        void shouldReturnNull_whenNoCurrent() {
            // Given
            Admission discharged = createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_DISCHARGED, yesterday);
            discharged.setDischargeDate(today);
            entityManager.flush();

            // When
            Admission result = admissionDao.getCurrentAdmission(PROGRAM_ID, DEMO_NO);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // getAdmissions (by demographicNo)
    // ========================================================================

    @Nested
    @DisplayName("getAdmissions by demographic")
    @Tag("read")
    class GetAdmissionsByDemographic {

        @Test
        @DisplayName("should return all admissions for demographic")
        void shouldReturnAllAdmissions_forDemographic() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);
            createAndPersist(DEMO_NO, PROGRAM_ID_2, Admission.STATUS_DISCHARGED, yesterday);

            // When
            List<Admission> result = admissionDao.getAdmissions(DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return empty list for demographic with no admissions")
        void shouldReturnEmptyList_whenNoAdmissions() {
            // When
            List<Admission> result = admissionDao.getAdmissions(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getAdmissionsASC
    // ========================================================================

    @Nested
    @DisplayName("getAdmissionsASC")
    @Tag("read")
    class GetAdmissionsASC {

        @Test
        @DisplayName("should return admissions ordered by date ascending")
        void shouldReturnAdmissions_orderedByDateASC() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, yesterday);
            createAndPersist(DEMO_NO, PROGRAM_ID_2, Admission.STATUS_CURRENT, today);

            // When
            List<Admission> result = admissionDao.getAdmissionsASC(DEMO_NO);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            if (result.size() >= 2) {
                assertThat(result.get(0).getAdmissionDate())
                        .isBeforeOrEqualTo(result.get(1).getAdmissionDate());
            }
        }
    }

    // ========================================================================
    // getCurrentAdmissions
    // ========================================================================

    @Nested
    @DisplayName("getCurrentAdmissions")
    @Tag("read")
    class GetCurrentAdmissions {

        @Test
        @DisplayName("should return only current admissions")
        void shouldReturnOnlyCurrentAdmissions() {
            // Given
            Admission current = createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);
            Admission discharged = createAndPersist(DEMO_NO, PROGRAM_ID_2, Admission.STATUS_DISCHARGED, yesterday);

            // When
            List<Admission> result = admissionDao.getCurrentAdmissions(DEMO_NO);

            // Then
            assertThat(result).extracting(Admission::getId).contains(current.getId());
            assertThat(result).extracting(Admission::getId).doesNotContain(discharged.getId());
        }
    }

    // ========================================================================
    // getCurrentAdmissionsByProgramId
    // ========================================================================

    @Nested
    @DisplayName("getCurrentAdmissionsByProgramId")
    @Tag("read")
    class GetCurrentAdmissionsByProgramId {

        @Test
        @DisplayName("should return current admissions for program")
        void shouldReturnCurrentAdmissions_forProgram() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);
            createAndPersist(DEMO_NO + 1, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            List<Admission> result = admissionDao.getCurrentAdmissionsByProgramId(PROGRAM_ID);

            // Then
            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
            assertThat(result).allSatisfy(a ->
                    assertThat(a.getAdmissionStatus()).isEqualTo(Admission.STATUS_CURRENT));
        }
    }

    // ========================================================================
    // getAdmissionsByProgramAndDate
    // ========================================================================

    @Nested
    @DisplayName("getAdmissionsByProgramAndDate")
    @Tag("read")
    class GetAdmissionsByProgramAndDate {

        @Test
        @DisplayName("should return admissions for program in date range")
        void shouldReturnAdmissions_forProgramInDateRange() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);
            createAndPersist(DEMO_NO + 1, PROGRAM_ID, Admission.STATUS_CURRENT, lastWeek);

            // When
            List<Admission> result = admissionDao.getAdmissionsByProgramAndDate(
                    PROGRAM_ID, yesterday, nextWeek);

            // Then — today's admission should be in range (yesterday..nextWeek)
            assertThat(result).isNotEmpty();
            assertThat(result).extracting(Admission::getProgramId).contains(PROGRAM_ID);
            assertThat(result).extracting(Admission::getClientId).contains(DEMO_NO);
        }
    }

    // ========================================================================
    // getAdmission (by ID)
    // ========================================================================

    @Nested
    @DisplayName("getAdmission by ID")
    @Tag("read")
    class GetAdmissionById {

        @Test
        @DisplayName("should return admission by integer ID")
        void shouldReturnAdmission_byIntId() {
            // Given
            Admission adm = createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            Admission result = admissionDao.getAdmission(adm.getId().intValue());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(adm.getId());
        }

        @Test
        @DisplayName("should return admission by Long ID")
        void shouldReturnAdmission_byLongId() {
            // Given
            Admission adm = createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            Admission result = admissionDao.getAdmission(adm.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(adm.getId());
            assertThat(result.getClientId()).isEqualTo(DEMO_NO);
            assertThat(result.getProgramId()).isEqualTo(PROGRAM_ID);
            assertThat(result.getAdmissionStatus()).isEqualTo(Admission.STATUS_CURRENT);
        }

        @Test
        @DisplayName("should return null for non-existent ID")
        void shouldReturnNull_forNonExistentId() {
            // When
            Admission result = admissionDao.getAdmission(99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // saveAdmission
    // ========================================================================

    @Nested
    @DisplayName("saveAdmission")
    @Tag("create")
    class SaveAdmission {

        @Test
        @DisplayName("should persist new admission")
        void shouldPersistNewAdmission() {
            // Given
            Admission adm = createAdmission(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            admissionDao.saveAdmission(adm);
            entityManager.flush();

            // Then
            assertThat(adm.getId()).isPositive();
        }
    }

    // ========================================================================
    // wasInProgram
    // ========================================================================

    @Nested
    @DisplayName("wasInProgram")
    @Tag("read")
    class WasInProgram {

        @Test
        @DisplayName("should return true when client was in program")
        void shouldReturnTrue_whenClientWasInProgram() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_DISCHARGED, yesterday);

            // When
            boolean result = admissionDao.wasInProgram(PROGRAM_ID, DEMO_NO);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when client was never in program")
        void shouldReturnFalse_whenNeverInProgram() {
            // When
            boolean result = admissionDao.wasInProgram(PROGRAM_ID, 99999);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // getAdmittedDemographicIdByProgramAndProvider
    // ========================================================================

    @Nested
    @DisplayName("getAdmittedDemographicIdByProgramAndProvider")
    @Tag("read")
    class GetAdmittedDemographicIdByProgramAndProvider {

        @Test
        @DisplayName("should return demographic IDs for program and provider")
        void shouldReturnDemoIds_forProgramAndProvider() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            List<Integer> result = admissionDao.getAdmittedDemographicIdByProgramAndProvider(
                    PROGRAM_ID, PROVIDER_NO);

            // Then
            assertThat(result).contains(DEMO_NO);
        }
    }

    // ========================================================================
    // getTemporaryAdmission
    // ========================================================================

    @Nested
    @DisplayName("getTemporaryAdmission")
    @Tag("read")
    class GetTemporaryAdmission {

        @Test
        @DisplayName("should return temporary admission when exists")
        void shouldReturnTemporaryAdmission_whenExists() {
            // Given
            Admission temp = createAdmission(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);
            temp.setTemporaryAdmissionFlag(true);
            entityManager.persist(temp);
            entityManager.flush();

            // When
            Admission result = admissionDao.getTemporaryAdmission(DEMO_NO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isTemporaryAdmissionFlag()).isTrue();
        }

        @Test
        @DisplayName("should return null when no temporary admission")
        void shouldReturnNull_whenNoTemporaryAdmission() {
            // When
            Admission result = admissionDao.getTemporaryAdmission(99999);

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // getClientIdByProgramDate
    // ========================================================================

    @Nested
    @DisplayName("getClientIdByProgramDate")
    @Tag("read")
    class GetClientIdByProgramDate {

        @Test
        @DisplayName("should return admissions for program on specific date")
        void shouldReturnAdmissions_forProgramOnDate() {
            // Given
            createAndPersist(DEMO_NO, PROGRAM_ID, Admission.STATUS_CURRENT, today);

            // When
            List<Admission> result = admissionDao.getClientIdByProgramDate(PROGRAM_ID, today);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProgramId()).isEqualTo(PROGRAM_ID);
            assertThat(result.get(0).getClientId()).isEqualTo(DEMO_NO);
        }
    }
}
