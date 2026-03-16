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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientStatus;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramTeam;
import io.github.carlos_emr.carlos.commn.model.Admission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramClientStatusDAO} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, status name existence checks, Admission-based
 * queries, and input validation edge cases.</p>
 *
 * @since 2026-02-26
 * @see ProgramClientStatusDAO
 */
@DisplayName("ProgramClientStatusDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramClientStatusDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProgramClientStatusDAO programClientStatusDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Integer testProgramId1;
    private Integer testProgramId2;
    private Integer testStatusId1;
    private Integer testStatusId2;

    @BeforeEach
    void setUp() {
        // Generate unique IDs from nanosecond timestamp to avoid conflicts across test runs
        int baseId = (int) (System.nanoTime() % 100000);
        testProgramId1 = 1000 + baseId;
        testProgramId2 = 2000 + baseId;

        // Create test client statuses
        ProgramClientStatus status1 = createClientStatus(testProgramId1, "Active");
        ProgramClientStatus status2 = createClientStatus(testProgramId1, "Pending");
        createClientStatus(testProgramId2, "Active");

        testStatusId1 = status1.getId();
        testStatusId2 = status2.getId();

        hibernateTemplate.flush();
    }

    /**
     * Creates a new ProgramClientStatus with the specified program ID and name, then persists it.
     *
     * @param programId Integer the program ID to associate the status with
     * @param name String the display name for the client status
     * @return ProgramClientStatus the persisted entity with generated ID
     */
    private ProgramClientStatus createClientStatus(Integer programId, String name) {
        ProgramClientStatus status = new ProgramClientStatus();
        status.setProgramId(programId);
        status.setName(name);
        hibernateTemplate.save(status);
        return status;
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get all statuses by program")
        void shouldGetStatuses_byProgram() {
            List<ProgramClientStatus> results = programClientStatusDAO.getProgramClientStatuses(testProgramId1);
            assertThat(results)
                .hasSize(2)
                .allMatch(s -> s.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("read")
        @DisplayName("should get status by ID")
        void shouldGetStatus_byId() {
            ProgramClientStatus result = programClientStatusDAO.getProgramClientStatus(String.valueOf(testStatusId1));
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Active");
        }
    }

    /**
     * Tests for {@code saveProgramClientStatus(ProgramClientStatus)} - persist and update operations.
     */
    @Nested
    @DisplayName("saveProgramClientStatus")
    class SaveProgramClientStatus {

        @Test
        @Tag("create")
        @DisplayName("should persist new client status and assign ID")
        void shouldPersistNewClientStatus_withGeneratedId() {
            // Given
            ProgramClientStatus status = new ProgramClientStatus();
            status.setProgramId(testProgramId2);
            status.setName("Discharged");

            // When
            programClientStatusDAO.saveProgramClientStatus(status);
            hibernateTemplate.flush();

            // Then
            assertThat(status.getId()).isNotNull();
            assertThat(status.getId()).isGreaterThan(0);

            ProgramClientStatus found = programClientStatusDAO.getProgramClientStatus(
                String.valueOf(status.getId()));
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Discharged");
            assertThat(found.getProgramId()).isEqualTo(testProgramId2);
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing client status name")
        void shouldUpdateExistingClientStatusName_whenChangesApplied() {
            // Given
            ProgramClientStatus status = createClientStatus(testProgramId1, "Temporary");
            hibernateTemplate.flush();
            Integer savedId = status.getId();

            // When
            status.setName("Updated");
            programClientStatusDAO.saveProgramClientStatus(status);
            hibernateTemplate.flush();

            // Then
            ProgramClientStatus updated = programClientStatusDAO.getProgramClientStatus(
                String.valueOf(savedId));
            assertThat(updated).isNotNull();
            assertThat(updated.getName()).isEqualTo("Updated");
        }
    }

    /**
     * Tests for {@code deleteProgramClientStatus(String id)} - deletes a client status record.
     */
    @Nested
    @DisplayName("deleteProgramClientStatus")
    class DeleteProgramClientStatus {

        @Test
        @Tag("delete")
        @DisplayName("should delete client status when valid ID is provided")
        void shouldDeleteClientStatus_whenValidIdProvided() {
            // Given
            ProgramClientStatus toDelete = createClientStatus(testProgramId1, "ToDelete");
            hibernateTemplate.flush();
            String deleteId = String.valueOf(toDelete.getId());

            // Verify it exists
            assertThat(programClientStatusDAO.getProgramClientStatus(deleteId)).isNotNull();

            // When
            programClientStatusDAO.deleteProgramClientStatus(deleteId);
            hibernateTemplate.flush();

            // Then
            assertThat(programClientStatusDAO.getProgramClientStatus(deleteId)).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw exception for null ID")
        void shouldThrow_whenIdIsNull() {
            assertThatThrownBy(() -> programClientStatusDAO.deleteProgramClientStatus(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code clientStatusNameExists(Integer programId, String statusName)} - checks
     * whether a status name already exists for a given program.
     */
    @Nested
    @DisplayName("clientStatusNameExists")
    class ClientStatusNameExists {

        @Test
        @Tag("query")
        @DisplayName("should return true when status name exists for program")
        void shouldReturnTrue_whenStatusNameExists() {
            // Given - setUp already created "Active" for testProgramId1
            hibernateTemplate.flush();

            // When
            boolean exists = programClientStatusDAO.clientStatusNameExists(
                testProgramId1, "Active");

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when status name does not exist for program")
        void shouldReturnFalse_whenStatusNameDoesNotExist() {
            // Given
            hibernateTemplate.flush();

            // When
            boolean exists = programClientStatusDAO.clientStatusNameExists(
                testProgramId1, "NonExistent");

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when program ID does not match")
        void shouldReturnFalse_whenProgramIdDoesNotMatch() {
            // Given
            hibernateTemplate.flush();

            // When - Use a programId that has no statuses
            boolean exists = programClientStatusDAO.clientStatusNameExists(
                99999, "Active");

            // Then
            assertThat(exists).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for null program ID")
        void shouldThrow_whenProgramIdIsNull() {
            assertThatThrownBy(() -> programClientStatusDAO.clientStatusNameExists(
                null, "Active"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for null status name")
        void shouldThrow_whenStatusNameIsNull() {
            assertThatThrownBy(() -> programClientStatusDAO.clientStatusNameExists(
                testProgramId1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for empty status name")
        void shouldThrow_whenStatusNameIsEmpty() {
            assertThatThrownBy(() -> programClientStatusDAO.clientStatusNameExists(
                testProgramId1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for zero or negative program ID")
        void shouldThrow_whenProgramIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programClientStatusDAO.clientStatusNameExists(
                0, "Active"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@code getAllClientsInStatus(Integer programId, Integer statusId)} - queries
     * Admission entities filtered by programId, teamId (mapped from statusId), and
     * admissionStatus='current'.
     *
     * <p>Parent records (Program, Demographic, ProgramTeam) required by Admission's eager
     * relationships are created in {@link #setUpAdmissionFixtures()}, which runs after the
     * outer {@code setUp()} to ensure proper FK ordering.</p>
     */
    @Nested
    @DisplayName("getAllClientsInStatus (enabled)")
    class GetAllClientsInStatusEnabled {

        private Integer sharedTeamId1;
        private Integer sharedTeamId2;
        private int sharedDemoNo1;
        private int sharedDemoNo2;

        /**
         * Creates required parent records for Admission's eager relationships before each test.
         *
         * <p>The Admission entity has {@code @ManyToOne(fetch = EAGER)} relationships to
         * Program, ProgramTeam, ProgramClientStatus, and Demographic. A {@code @PostLoad}
         * callback calls {@code program.getName()} and {@code program.getType()}, requiring
         * a valid Program parent record.</p>
         *
         * <p>This method runs after the outer {@code setUp()}, which creates ProgramClientStatus
         * records, ensuring Program rows exist before any child Admission entities are persisted.</p>
         */
        @BeforeEach
        void setUpAdmissionFixtures() {
            // Derive unique demographic IDs from program IDs to avoid conflicts across test runs
            sharedDemoNo1 = testProgramId1 + 5000;
            sharedDemoNo2 = testProgramId2 + 5000;

            // Insert Program records via native SQL since Program is HBM-mapped.
            // All primitive boolean fields must be provided (Hibernate cannot assign NULL to primitive).
            entityManager.createNativeQuery(
                "INSERT INTO program (id, name, type, facilityId, userDefined, holdingTank, allowBatchAdmission, allowBatchDischarge, hic, transgender, firstNation, alcohol, physicalHealth, mentalHealth, housing) VALUES (?1, ?2, ?3, 0, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)")
                .setParameter(1, testProgramId1)
                .setParameter(2, "TestProg")
                .setParameter(3, "community")
                .executeUpdate();

            entityManager.createNativeQuery(
                "INSERT INTO program (id, name, type, facilityId, userDefined, holdingTank, allowBatchAdmission, allowBatchDischarge, hic, transgender, firstNation, alcohol, physicalHealth, mentalHealth, housing) VALUES (?1, ?2, ?3, 0, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)")
                .setParameter(1, testProgramId2)
                .setParameter(2, "TestProg2")
                .setParameter(3, "community")
                .executeUpdate();

            // Insert Demographic records for the client_id FK
            entityManager.createNativeQuery(
                "INSERT INTO demographic (demographic_no, last_name, first_name, sex, year_of_birth, month_of_birth, date_of_birth, patient_status) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                .setParameter(1, sharedDemoNo1)
                .setParameter(2, "TestLast")
                .setParameter(3, "TestFirst")
                .setParameter(4, "M")
                .setParameter(5, "1990")
                .setParameter(6, "01")
                .setParameter(7, "15")
                .setParameter(8, "AC")
                .executeUpdate();

            entityManager.createNativeQuery(
                "INSERT INTO demographic (demographic_no, last_name, first_name, sex, year_of_birth, month_of_birth, date_of_birth, patient_status) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
                .setParameter(1, sharedDemoNo2)
                .setParameter(2, "Discharged")
                .setParameter(3, "Patient")
                .setParameter(4, "F")
                .setParameter(5, "1985")
                .setParameter(6, "06")
                .setParameter(7, "20")
                .setParameter(8, "AC")
                .executeUpdate();

            // Create ProgramTeam instances to satisfy the FK constraint: admission.team_id → program_team.team_id.
            // In H2 (with hbm2ddl), this FK is enforced; MySQL may not enforce it in older schemas.
            ProgramTeam team1 = new ProgramTeam();
            team1.setProgramId(testProgramId1);
            team1.setName("TestTeam");
            hibernateTemplate.save(team1);

            ProgramTeam team2 = new ProgramTeam();
            team2.setProgramId(testProgramId2);
            team2.setName("TestTeam2");
            hibernateTemplate.save(team2);

            hibernateTemplate.flush();
            sharedTeamId1 = team1.getId();
            sharedTeamId2 = team2.getId();

            entityManager.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no admissions exist")
        void shouldReturnEmpty_whenNoAdmissionsExist() {
            // Given - setUpAdmissionFixtures created Program/Demographic/ProgramTeam, but no Admissions
            hibernateTemplate.flush();

            // When
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(
                testProgramId1, sharedTeamId1);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return admissions matching program and status")
        void shouldReturnAdmissions_whenMatchingProgramAndStatus() {
            // Given - Create an Admission with status='current' matching our program and team
            Admission admission = new Admission();
            admission.setProgramId(testProgramId1);
            admission.setTeamId(sharedTeamId1);
            admission.setAdmissionStatus("current");
            admission.setProviderNo("999");
            admission.setClientId(sharedDemoNo1);
            admission.setAdmissionDate(new Date());
            entityManager.persist(admission);
            entityManager.flush();

            // When - getAllClientsInStatus filters by teamId (the ProgramClientStatus-like concept)
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(
                testProgramId1, sharedTeamId1);

            // Then
            assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getProgramId()).isEqualTo(testProgramId1);
                    assertThat(a.getTeamId()).isEqualTo(sharedTeamId1);
                    assertThat(a.getAdmissionStatus()).isEqualTo("current");
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should not return discharged admissions")
        void shouldNotReturnDischarged_whenStatusIsDischarged() {
            // Given - Create a discharged admission (should NOT be returned)
            Admission discharged = new Admission();
            discharged.setProgramId(testProgramId2);
            discharged.setTeamId(sharedTeamId2);
            discharged.setAdmissionStatus("discharged");
            discharged.setProviderNo("999");
            discharged.setClientId(sharedDemoNo2);
            discharged.setAdmissionDate(new Date());
            entityManager.persist(discharged);
            entityManager.flush();

            // When
            List<Admission> results = programClientStatusDAO.getAllClientsInStatus(
                testProgramId2, sharedTeamId2);

            // Then - discharged admissions should not be included
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for null program ID")
        void shouldThrow_whenProgramIdIsNull() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(
                null, testStatusId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for null status ID")
        void shouldThrow_whenStatusIdIsNull() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(
                testProgramId1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for zero or negative program ID")
        void shouldThrow_whenProgramIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(
                0, testStatusId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw exception for zero or negative status ID")
        void shouldThrow_whenStatusIdIsZeroOrNegative() {
            assertThatThrownBy(() -> programClientStatusDAO.getAllClientsInStatus(
                testProgramId1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
