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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
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
import java.util.Collection;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramClientRestrictionDAO} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, multi-parameter searches, facility-based subqueries,
 * and the N+1 relationship enrichment pattern.</p>
 *
 * @since 2026-02-26
 * @see ProgramClientRestrictionDAO
 */
@DisplayName("ProgramClientRestrictionDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramClientRestrictionDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProgramClientRestrictionDAO programClientRestrictionDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private int testProgramId1;
    private int testProgramId2;
    private int testDemoNo1;
    private int testDemoNo2;

    @BeforeEach
    void setUp() {
        // Generate unique IDs from nanosecond timestamp to avoid conflicts across test runs
        int baseId = (int) (System.nanoTime() % 100000);
        testProgramId1 = 100 + baseId;
        testProgramId2 = 200 + baseId;
        testDemoNo1 = 1001 + baseId;
        testDemoNo2 = 1002 + baseId;

        // Create test restrictions - these are the entities the HQL queries will find
        // Restriction for program1, demo1 (enabled)
        createRestriction(testProgramId1, testDemoNo1, true);
        // Restriction for program1, demo2 (enabled)
        createRestriction(testProgramId1, testDemoNo2, true);
        // Restriction for program2, demo1 (enabled)
        createRestriction(testProgramId2, testDemoNo1, true);
        // Restriction for program1, demo1 (disabled - should NOT be found)
        createRestriction(testProgramId1, testDemoNo1, false);

        hibernateTemplate.flush();
    }

    /**
     * Creates a new ProgramClientRestriction with the specified parameters and persists it.
     *
     * @param programId int the program ID to associate the restriction with
     * @param demographicNo int the demographic (patient) number
     * @param enabled boolean whether the restriction is active
     * @return ProgramClientRestriction the persisted entity with generated ID
     */
    private ProgramClientRestriction createRestriction(int programId, int demographicNo, boolean enabled) {
        ProgramClientRestriction pcr = new ProgramClientRestriction();
        pcr.setProgramId(programId);
        pcr.setDemographicNo(demographicNo);
        pcr.setEnabled(enabled);
        pcr.setProviderNo("999");
        pcr.setStartDate(new Date());
        pcr.setEndDate(new Date(System.currentTimeMillis() + 86400000)); // +1 day
        pcr.setComments("Test restriction");
        pcr.setCommentId("test-" + System.nanoTime());  // Required non-null field
        programClientRestrictionDAO.save(pcr);
        return pcr;
    }

    /**
     * Tests for {@code find(int programId, int demographicNo)} - finds enabled restrictions
     * matching both program and demographic number.
     */
    @Nested
    @DisplayName("find (2 params: programId, demographicNo)")
    class FindByProgramAndDemo {

        @Test
        @Tag("query")
        @DisplayName("should find restriction when both program and demographic match")
        void shouldFind_whenBothParamsMatch() {
            // When - Query with both params that should match
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                testProgramId1, testDemoNo1);

            // Then - Should find exactly 1 enabled restriction
            assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(pcr -> {
                    assertThat(pcr.getProgramId()).isEqualTo(testProgramId1);
                    assertThat(pcr.getDemographicNo()).isEqualTo(testDemoNo1);
                    assertThat(pcr.isEnabled()).isTrue();
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when program doesn't match")
        void shouldReturnEmpty_whenProgramDoesntMatch() {
            // When - Program 999999 doesn't exist
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                999999, testDemoNo1);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when demographic doesn't match")
        void shouldReturnEmpty_whenDemoDoesntMatch() {
            // When - Demographic 999999 doesn't exist
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.find(
                testProgramId1, 999999);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should verify parameter order - swapping params should return different results")
        void shouldVerifyParameterOrder_swappingParamsShouldReturnDifferent() {
            // This test catches parameter binding errors where ?0 and ?1 are swapped
            // If parameters were incorrectly bound, we'd get unexpected results

            // When - Search for program2, demo1 (should find 1)
            Collection<ProgramClientRestriction> results1 = programClientRestrictionDAO.find(
                testProgramId2, testDemoNo1);

            // When - Search for program1, demo2 (should also find 1, but different)
            Collection<ProgramClientRestriction> results2 = programClientRestrictionDAO.find(
                testProgramId1, testDemoNo2);

            // Then - Both should have exactly 1 result
            assertThat(results1).hasSize(1);
            assertThat(results2).hasSize(1);

            // And the results should be different (different program/demo combos)
            ProgramClientRestriction pcr1 = results1.iterator().next();
            ProgramClientRestriction pcr2 = results2.iterator().next();

            assertThat(pcr1.getProgramId()).isEqualTo(testProgramId2);
            assertThat(pcr1.getDemographicNo()).isEqualTo(testDemoNo1);

            assertThat(pcr2.getProgramId()).isEqualTo(testProgramId1);
            assertThat(pcr2.getDemographicNo()).isEqualTo(testDemoNo2);
        }
    }

    /**
     * Tests for single-parameter query methods as baseline coverage, including
     * {@code findForClient(int)} and {@code findForProgram(int)}.
     */
    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should find all restrictions for client")
        void shouldFindAll_whenRestrictionsExist() {
            // When
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.findForClient(testDemoNo1);

            // Then - Demo1 has enabled restrictions in both programs
            assertThat(results)
                .hasSize(2)
                .allMatch(pcr -> pcr.getDemographicNo() == testDemoNo1)
                .allMatch(ProgramClientRestriction::isEnabled);
        }

        @Test
        @Tag("read")
        @DisplayName("should find restrictions for specific program")
        void shouldFindRestrictions_byProgram() {
            // When
            Collection<ProgramClientRestriction> results = programClientRestrictionDAO.findForProgram(testProgramId1);

            // Then - Program1 has 2 enabled restrictions (demo1 and demo2)
            assertThat(results)
                .hasSize(2)
                .allMatch(pcr -> pcr.getProgramId() == testProgramId1)
                .allMatch(ProgramClientRestriction::isEnabled);
        }
    }

    /**
     * Tests for {@code find(int restrictionId)} - single entity lookup by primary key.
     */
    @Nested
    @DisplayName("find (1 param: restrictionId)")
    class FindByRestrictionId {

        @Test
        @Tag("read")
        @DisplayName("should return restriction when valid ID is provided")
        void shouldReturnRestriction_whenValidIdProvided() {
            // Given - Create and save a restriction, capture its generated ID
            ProgramClientRestriction saved = createRestriction(testProgramId1, testDemoNo1, true);
            hibernateTemplate.flush();
            int savedId = saved.getId();

            // When
            ProgramClientRestriction result = programClientRestrictionDAO.find(savedId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(savedId);
            assertThat(result.getProgramId()).isEqualTo(testProgramId1);
            assertThat(result.getDemographicNo()).isEqualTo(testDemoNo1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when restriction ID does not exist")
        void shouldReturnNull_whenRestrictionIdNotFound() {
            // When
            ProgramClientRestriction result = programClientRestrictionDAO.find(999999);

            // Then
            assertThat(result).isNull();
        }
    }

    /**
     * Tests for {@code findDisabledForClient(int demographicNo)} - returns disabled
     * (enabled=false) restrictions for a given client.
     */
    @Nested
    @DisplayName("findDisabledForClient (1 param: demographicNo)")
    class FindDisabledForClient {

        @Test
        @Tag("read")
        @DisplayName("should return disabled restrictions for client")
        void shouldReturnDisabledRestrictions_forClient() {
            // Given - setUp already created one disabled restriction for (program1, demo1)

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForClient(testDemoNo1);

            // Then - Should find exactly the 1 disabled restriction
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pcr -> {
                    assertThat(pcr.getDemographicNo()).isEqualTo(testDemoNo1);
                    assertThat(pcr.isEnabled()).isFalse();
                });
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when client has no disabled restrictions")
        void shouldReturnEmpty_whenNoDisabledRestrictions() {
            // Given - demo2 has only enabled restrictions

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForClient(testDemoNo2);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when client does not exist")
        void shouldReturnEmpty_whenClientNotFound() {
            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForClient(999999);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code findDisabledForProgram(int programId)} - returns disabled
     * restrictions for a given program.
     */
    @Nested
    @DisplayName("findDisabledForProgram (1 param: programId)")
    class FindDisabledForProgram {

        @Test
        @Tag("read")
        @DisplayName("should return disabled restrictions for program")
        void shouldReturnDisabledRestrictions_forProgram() {
            // Given - setUp created one disabled restriction for (program1, demo1)

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForProgram(testProgramId1);

            // Then
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pcr -> {
                    assertThat(pcr.getProgramId()).isEqualTo(testProgramId1);
                    assertThat(pcr.isEnabled()).isFalse();
                });
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when program has no disabled restrictions")
        void shouldReturnEmpty_whenNoDisabledRestrictions() {
            // Given - program2 has only enabled restrictions

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForProgram(testProgramId2);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when program does not exist")
        void shouldReturnEmpty_whenProgramNotFound() {
            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findDisabledForProgram(999999);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code findForClient(int demographicNo, int facilityId)} - subquery with
     * OR condition matching programs by facilityId or null facilityId.
     */
    @Nested
    @DisplayName("findForClient (2 params: demographicNo, facilityId)")
    class FindForClientWithFacility {

        @Test
        @Tag("query")
        @DisplayName("should find restrictions for client in programs matching facility")
        void shouldFindRestrictions_whenProgramMatchesFacility() {
            // Given - Create programs with specific facility IDs using native SQL
            // because Program is HBM-mapped and hbm2ddl creates the table.
            // All primitive boolean fields must be provided (Hibernate cannot assign NULL to primitive).
            int facilityId = 42;
            entityManager.createNativeQuery(
                "INSERT INTO program (id, name, type, facilityId, userDefined, holdingTank, allowBatchAdmission, allowBatchDischarge, hic, transgender, firstNation, alcohol, physicalHealth, mentalHealth, housing) VALUES (?1, ?2, ?3, ?4, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)")
                .setParameter(1, testProgramId1)
                .setParameter(2, "TestProgram1")
                .setParameter(3, "community")
                .setParameter(4, facilityId)
                .executeUpdate();
            entityManager.flush();

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findForClient(testDemoNo1, facilityId);

            // Then - demo1 has enabled restrictions in program1 which matches facilityId
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pcr -> {
                    assertThat(pcr.getDemographicNo()).isEqualTo(testDemoNo1);
                    assertThat(pcr.isEnabled()).isTrue();
                    assertThat(pcr.getProgramId()).isEqualTo(testProgramId1); // verify facilityId filter
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should include restrictions for programs with null facilityId")
        void shouldIncludeRestrictions_whenProgramHasNullFacility() {
            // Given - Create a program with null facilityId.
            // facilityId is Integer (nullable) so NULL is valid.
            // All primitive boolean fields must be provided (Hibernate cannot assign NULL to primitive).
            entityManager.createNativeQuery(
                "INSERT INTO program (id, name, type, facilityId, userDefined, holdingTank, allowBatchAdmission, allowBatchDischarge, hic, transgender, firstNation, alcohol, physicalHealth, mentalHealth, housing) VALUES (?1, ?2, ?3, NULL, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE)")
                .setParameter(1, testProgramId1)
                .setParameter(2, "NullFacilityProg")
                .setParameter(3, "community")
                .executeUpdate();
            entityManager.flush();

            // When - Use a non-matching facilityId; the OR clause should still pick up
            // programs with null facilityId
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findForClient(testDemoNo1, 9999);

            // Then - Should find restrictions for programs with null facilityId
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pcr -> {
                    assertThat(pcr.getDemographicNo()).isEqualTo(testDemoNo1);
                    assertThat(pcr.isEnabled()).isTrue();
                    assertThat(pcr.getProgramId()).isEqualTo(testProgramId1);
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no programs match facility")
        void shouldReturnEmpty_whenNoProgramsMatchFacility() {
            // Given - No programs exist in the program table matching any facilityId
            // (setUp does not insert into program table)

            // When
            Collection<ProgramClientRestriction> results =
                programClientRestrictionDAO.findForClient(testDemoNo1, 12345);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code save(ProgramClientRestriction)} - persist and update operations.
     */
    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @Tag("create")
        @DisplayName("should persist new restriction and assign ID")
        void shouldPersistNewRestriction_withGeneratedId() {
            // Given
            ProgramClientRestriction pcr = new ProgramClientRestriction();
            pcr.setProgramId(testProgramId1);
            pcr.setDemographicNo(9999);
            pcr.setEnabled(true);
            pcr.setProviderNo("001");
            pcr.setStartDate(new Date());
            pcr.setEndDate(new Date(System.currentTimeMillis() + 86400000));
            pcr.setCommentId("save-test-" + System.nanoTime());

            // When
            programClientRestrictionDAO.save(pcr);
            hibernateTemplate.flush();

            // Then
            assertThat(pcr.getId()).isNotNull();
            assertThat(pcr.getId()).isGreaterThan(0);

            // Verify it can be retrieved
            ProgramClientRestriction found = programClientRestrictionDAO.find(pcr.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProgramId()).isEqualTo(testProgramId1);
            assertThat(found.getDemographicNo()).isEqualTo(9999);
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing restriction")
        void shouldUpdateExistingRestriction_whenChangesApplied() {
            // Given - Create a restriction
            ProgramClientRestriction pcr = createRestriction(testProgramId1, testDemoNo1, true);
            hibernateTemplate.flush();
            int savedId = pcr.getId();

            // When - Update the restriction
            pcr.setEnabled(false);
            pcr.setEarlyTerminationProvider("Dr. Test");
            programClientRestrictionDAO.save(pcr);
            hibernateTemplate.flush();

            // Then
            ProgramClientRestriction updated = programClientRestrictionDAO.find(savedId);
            assertThat(updated).isNotNull();
            assertThat(updated.isEnabled()).isFalse();
            assertThat(updated.getEarlyTerminationProvider()).isEqualTo("Dr. Test");
        }
    }

    /**
     * Tests verifying that the setRelationships() N+1 pattern executes without error
     * even when referenced entities (Demographic, Program, Provider) are not in the database.
     * This validates the post-processing enrichment pattern used by all query methods.
     */
    @Nested
    @DisplayName("setRelationships N+1 pattern")
    class SetRelationshipsN1 {

        @Test
        @Tag("read")
        @DisplayName("should return restriction with null relationships when referenced entities do not exist")
        void shouldReturnRestriction_withNullRelationships() {
            // Given - Restrictions reference non-existent demographic/program/provider IDs
            ProgramClientRestriction saved = createRestriction(testProgramId1, testDemoNo1, true);
            hibernateTemplate.flush();

            // When - find() calls setRelationships() internally
            ProgramClientRestriction result = programClientRestrictionDAO.find(saved.getId());

            // Then - The restriction is returned even though relationships resolve to null
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(saved.getId());
            // Client, Program, Provider set via setRelationships() - may be null when not in DB
            // The key assertion is that no exception is thrown during setRelationships()
        }
    }
}
