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
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramTeam;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.model.security.Secrole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramProviderDAO} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?1, ?2, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover simple queries, multi-parameter queries, subquery JOINs, LEFT JOINs,
 * 3-way JOINs, cache-evicting writes, loop deletes, and boolean domain checks.</p>
 *
 * @since 2026-02-26
 * @see ProgramProviderDAO
 * @see ProgramProviderDAOImpl
 */
@DisplayName("ProgramProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramProviderDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO programProviderDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Long testProgramId1;
    private Long testProgramId2;
    private Long testRoleId1;
    private Long testRoleId2;
    private String testProviderNo1;
    private String testProviderNo2;
    private Integer testFacilityId1;
    private Integer testFacilityId2;

    @BeforeEach
    void setUp() {
        // Generate short unique prefix from nanosecond timestamp to fit provider_no VARCHAR(6) constraint
        String uniquePrefix = String.valueOf(System.nanoTime() % 10000);

        // Create parent Provider records (provider_no is VARCHAR(6))
        testProviderNo1 = uniquePrefix + "1";
        testProviderNo2 = uniquePrefix + "2";
        createProvider(testProviderNo1, "Test", "Provider1");
        createProvider(testProviderNo2, "Test", "Provider2");

        // Create Facility records (needed for facility-based queries)
        Facility facility1 = createFacility("Test Facility 1");
        Facility facility2 = createFacility("Test Facility 2");
        testFacilityId1 = facility1.getId();
        testFacilityId2 = facility2.getId();

        // Create parent Program records with facilityId
        Program program1 = createProgram("Test Program 1", testFacilityId1);
        Program program2 = createProgram("Test Program 2", testFacilityId2);
        testProgramId1 = (long) program1.getId();
        testProgramId2 = (long) program2.getId();

        // Create parent Secrole records
        Secrole role1 = createSecrole("Doctor");
        Secrole role2 = createSecrole("Nurse");
        testRoleId1 = role1.getId();
        testRoleId2 = role2.getId();

        hibernateTemplate.flush();
    }

    /**
     * Creates a new Provider with the specified identifiers and persists it.
     *
     * @param providerNo String the provider number (VARCHAR(6) constraint)
     * @param firstName String the provider's first name
     * @param lastName String the provider's last name
     * @return Provider the persisted entity
     */
    private Provider createProvider(String providerNo, String firstName, String lastName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus("1");
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSpecialty("");
        hibernateTemplate.save(provider);
        return provider;
    }

    /**
     * Creates a new Program with the given name and default facilityId of 0, then persists it.
     *
     * @param name String the program name
     * @return Program the persisted entity with generated ID
     */
    private Program createProgram(String name) {
        return createProgram(name, 0);
    }

    /**
     * Creates a new Program with the given name and facility ID, then persists it.
     *
     * @param name String the program name
     * @param facilityId int the facility ID to associate with this program
     * @return Program the persisted entity with generated ID
     */
    private Program createProgram(String name, int facilityId) {
        Program program = new Program();
        program.setName(name);
        program.setType("community");
        program.setProgramStatus("active");
        program.setFacilityId(facilityId);
        hibernateTemplate.save(program);
        return program;
    }

    /**
     * Creates a new Facility with the given name and persists it.
     *
     * @param name String the facility name
     * @return Facility the persisted entity with generated ID
     */
    private Facility createFacility(String name) {
        Facility facility = new Facility();
        facility.setName(name);
        facility.setDescription("Test facility");
        hibernateTemplate.save(facility);
        return facility;
    }

    /**
     * Creates a new Secrole with the given name and persists it.
     *
     * @param roleName String the name of the security role
     * @return Secrole the persisted entity with generated ID
     */
    private Secrole createSecrole(String roleName) {
        Secrole role = new Secrole();
        role.setRoleName(roleName);
        role.setDescription("Test role: " + roleName);
        hibernateTemplate.save(role);
        return role;
    }

    /**
     * Creates a new ProgramProvider linking a provider to a program and persists it.
     *
     * @param providerNo String the provider number
     * @param programId Long the program ID to associate
     * @return ProgramProvider the persisted entity with generated ID
     */
    private ProgramProvider createProgramProvider(String providerNo, Long programId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        hibernateTemplate.save(pp);
        return pp;
    }

    /**
     * Creates a new ProgramProvider linking a provider to a program with a specific role and persists it.
     *
     * @param providerNo String the provider number
     * @param programId Long the program ID to associate
     * @param roleId Long the security role ID to assign
     * @return ProgramProvider the persisted entity with generated ID
     */
    private ProgramProvider createProgramProvider(String providerNo, Long programId, Long roleId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        pp.setRoleId(roleId);
        hibernateTemplate.save(pp);
        return pp;
    }

    /**
     * Creates a new ProgramTeam with the given name and program association, then persists it.
     *
     * @param name String the team name
     * @param programId Integer the program ID this team belongs to
     * @return ProgramTeam the persisted entity with generated ID
     */
    private ProgramTeam createProgramTeam(String name, Integer programId) {
        ProgramTeam team = new ProgramTeam();
        team.setName(name);
        team.setProgramId(programId);
        hibernateTemplate.save(team);
        return team;
    }

    // =========================================================================
    // Existing test classes (preserved as-is)
    // =========================================================================

    /**
     * Tests for {@code getProgramProviderByProviderProgramId(String providerNo, Long programId)} -
     * finds program providers matching both provider number and program ID.
     */
    @Nested
    @DisplayName("getProgramProviderByProviderProgramId (2 params)")
    class GetByProviderProgramId {

        @Test
        @Tag("query")
        @DisplayName("should find program provider when both params match")
        void shouldFind_whenBothParamsMatch() {
            ProgramProvider match = createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId(testProviderNo1, testProgramId1);

            assertThat(results)
                .hasSize(1)
                .extracting(ProgramProvider::getId)
                .containsExactly(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId("NONEXISTENT", testProgramId1);

            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@code getProgramProvider(String providerNo, Long programId)} - returns a single
     * ProgramProvider matching both provider number and program ID.
     */
    @Nested
    @DisplayName("getProgramProvider (2 params)")
    class GetProgramProviderTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should return single program provider when both params match")
        void shouldReturnSingle_whenBothParamsMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1);

            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo(testProviderNo1);
            assertThat(found.getProgramId()).isEqualTo(testProgramId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no match")
        void shouldReturnNull_whenNoMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, 999999L);

            assertThat(found).isNull();
        }
    }

    /**
     * Tests for {@code getProgramProvider(String providerNo, Long programId, Long roleId)} -
     * returns a single ProgramProvider matching provider, program, and role.
     */
    @Nested
    @DisplayName("getProgramProvider (3 params)")
    class GetProgramProviderThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should find when all three parameters match")
        void shouldFind_whenAllThreeParamsMatch() {
            ProgramProvider match = createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            createProgramProvider(testProviderNo1, testProgramId1, testRoleId2);
            createProgramProvider(testProviderNo1, testProgramId2, testRoleId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, testRoleId1);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when role doesn't match")
        void shouldReturnNull_whenRoleDoesntMatch() {
            createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, 999999L);

            assertThat(found).isNull();
        }
    }

    /**
     * Tests for single-parameter query methods as baseline coverage, including
     * {@code getProgramProviderByProviderNo(String)} and {@code getProgramProviders(Long)}.
     */
    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get program providers by provider number")
        void shouldGetProviders_byProviderNo() {
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO.getProgramProviderByProviderNo(testProviderNo1);

            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProviderNo().equals(testProviderNo1));
        }

        @Test
        @Tag("read")
        @DisplayName("should get program providers by program ID")
        void shouldGetProviders_byProgramId() {
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO.getProgramProviders(testProgramId1);

            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProgramId().equals(testProgramId1));
        }
    }

    // =========================================================================
    // New test classes for untested methods
    // =========================================================================

    /**
     * Tests for {@link ProgramProviderDAO#getAllProgramProviders()}.
     * Returns all ProgramProvider records with no filtering.
     */
    @Nested
    @DisplayName("getAllProgramProviders")
    class GetAllProgramProviders {

        @Test
        @Tag("read")
        @DisplayName("should return all program providers across all programs and providers")
        void shouldReturnAllProgramProviders_whenMultipleExist() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getAllProgramProviders();

            // Then - setUp creates no ProgramProviders; exactly 3 are created above
            assertThat(results).hasSize(3);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no program providers exist")
        void shouldReturnEmptyList_whenNoneExist() {
            // Given - no ProgramProvider records created

            // When
            List<ProgramProvider> results = programProviderDAO.getAllProgramProviders();

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramProvidersByProvider(String)}.
     * Similar to getProgramProviderByProviderNo but with different validation (throws on null).
     */
    @Nested
    @DisplayName("getProgramProvidersByProvider")
    class GetProgramProvidersByProvider {

        @Test
        @Tag("read")
        @DisplayName("should return all providers matching provider number")
        void shouldReturnAllProviders_whenProviderNoMatches() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramProvidersByProvider(testProviderNo1);

            // Then
            assertThat(results)
                .hasSize(2)
                .allSatisfy(pp -> assertThat(pp.getProviderNo()).isEqualTo(testProviderNo1));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when provider has no programs")
        void shouldReturnEmptyList_whenProviderHasNoPrograms() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramProvidersByProvider(testProviderNo2);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersByProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramProvidersByProviderAndFacility(String, Integer)}.
     * Uses named parameters: pp.ProviderNo = :providerNo and s.facilityId = :facilityId or s.facilityId is null.
     */
    @Nested
    @DisplayName("getProgramProvidersByProviderAndFacility (subquery JOIN)")
    class GetProgramProvidersByProviderAndFacility {

        @Test
        @Tag("query")
        @DisplayName("should return providers for programs in the specified facility")
        void shouldReturnProviders_whenProgramMatchesFacility() {
            // Given - program1 is in facility1, program2 is in facility2
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<ProgramProvider> results = programProviderDAO
                .getProgramProvidersByProviderAndFacility(testProviderNo1, testFacilityId1);

            // Then - should only return the one in facility1
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pp -> assertThat(pp.getProgramId()).isEqualTo(testProgramId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should include programs with null facilityId")
        void shouldIncludePrograms_withNullFacilityId() {
            // Given - create a program with facilityId explicitly set to null
            Program noFacilityProgram = createProgram("No Facility Program");
            noFacilityProgram.setFacilityId(null);
            hibernateTemplate.saveOrUpdate(noFacilityProgram);
            hibernateTemplate.flush();

            long noFacilityProgramId = (long) noFacilityProgram.getId();
            createProgramProvider(testProviderNo1, noFacilityProgramId);
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When - query with facility1 should also return programs with null facilityId
            @SuppressWarnings("unchecked")
            List<ProgramProvider> results = programProviderDAO
                .getProgramProvidersByProviderAndFacility(testProviderNo1, testFacilityId1);

            // Then - should include both facility1 program and null facility program
            assertThat(results).hasSizeGreaterThanOrEqualTo(2);
            assertThat(results)
                .extracting(ProgramProvider::getProgramId)
                .contains(testProgramId1, noFacilityProgramId);
        }

        @Test
        @Tag("query")
        @DisplayName("should not include programs from other providers")
        void shouldNotIncludePrograms_fromOtherProviders() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<ProgramProvider> results = programProviderDAO
                .getProgramProvidersByProviderAndFacility(testProviderNo1, testFacilityId1);

            // Then
            assertThat(results)
                .allSatisfy(pp -> assertThat(pp.getProviderNo()).isEqualTo(testProviderNo1));
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() ->
                programProviderDAO.getProgramProvidersByProviderAndFacility(null, testFacilityId1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramProvider(Long)}.
     * Simple get-by-ID using HibernateTemplate.get().
     */
    @Nested
    @DisplayName("getProgramProvider (by ID)")
    class GetProgramProviderById {

        @Test
        @Tag("read")
        @DisplayName("should return program provider when valid ID is provided")
        void shouldReturnProgramProvider_whenValidIdProvided() {
            // Given
            ProgramProvider saved = createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            hibernateTemplate.flush();

            // When
            ProgramProvider found = programProviderDAO.getProgramProvider(saved.getId());

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo(testProviderNo1);
            assertThat(found.getProgramId()).isEqualTo(testProgramId1);
            assertThat(found.getRoleId()).isEqualTo(testRoleId1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when ID does not exist")
        void shouldReturnNull_whenIdDoesNotExist() {
            // When
            ProgramProvider found = programProviderDAO.getProgramProvider(999999L);

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when ID is null")
        void shouldThrowIllegalArgumentException_whenIdIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider((Long) null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when ID is negative")
        void shouldThrowIllegalArgumentException_whenIdIsNegative() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#saveProgramProvider(ProgramProvider)}.
     * Saves or updates a ProgramProvider and evicts the cache entry.
     */
    @Nested
    @DisplayName("saveProgramProvider (cache-evicting write)")
    class SaveProgramProvider {

        @Test
        @Tag("create")
        @DisplayName("should persist new program provider")
        void shouldPersistNewProgramProvider_whenSaved() {
            // Given
            ProgramProvider pp = new ProgramProvider();
            pp.setProviderNo(testProviderNo1);
            pp.setProgramId(testProgramId1);
            pp.setRoleId(testRoleId1);

            // When
            programProviderDAO.saveProgramProvider(pp);
            hibernateTemplate.flush();

            // Then
            assertThat(pp.getId()).isPositive();
            assertThat(pp.getId()).isGreaterThan(0L);

            ProgramProvider found = programProviderDAO.getProgramProvider(pp.getId());
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo(testProviderNo1);
            assertThat(found.getProgramId()).isEqualTo(testProgramId1);
            assertThat(found.getRoleId()).isEqualTo(testRoleId1);
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing program provider")
        void shouldUpdateExistingProgramProvider_whenSavedAgain() {
            // Given
            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            hibernateTemplate.flush();
            Long savedId = pp.getId();

            // When
            pp.setRoleId(testRoleId2);
            programProviderDAO.saveProgramProvider(pp);
            hibernateTemplate.flush();

            // Then
            ProgramProvider found = programProviderDAO.getProgramProvider(savedId);
            assertThat(found).isNotNull();
            assertThat(found.getRoleId()).isEqualTo(testRoleId2);
        }

        @Test
        @Tag("create")
        @DisplayName("should throw IllegalArgumentException when program provider is null")
        void shouldThrowIllegalArgumentException_whenProgramProviderIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.saveProgramProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("create")
        @DisplayName("should evict cache so subsequent query returns fresh data")
        void shouldEvictCache_whenSaved() {
            // Given - pre-populate cache via a query
            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();
            programProviderDAO.getProgramProviderByProviderProgramId(testProviderNo1, testProgramId1);

            // When - save again to evict cache
            pp.setRoleId(testRoleId1);
            programProviderDAO.saveProgramProvider(pp);
            hibernateTemplate.flush();

            // Then - re-query should reflect updates (cache evicted)
            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId(testProviderNo1, testProgramId1);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRoleId()).isEqualTo(testRoleId1);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#deleteProgramProvider(Long)}.
     * Deletes a single ProgramProvider by ID and evicts cache.
     */
    @Nested
    @DisplayName("deleteProgramProvider (single delete with cache)")
    class DeleteProgramProvider {

        @Test
        @Tag("delete")
        @DisplayName("should delete program provider when valid ID is provided")
        void shouldDeleteProgramProvider_whenValidIdProvided() {
            // Given
            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();
            Long ppId = pp.getId();

            // When
            programProviderDAO.deleteProgramProvider(ppId);
            hibernateTemplate.flush();

            // Then
            ProgramProvider found = programProviderDAO.getProgramProvider(ppId);
            assertThat(found).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should not fail when ID does not exist")
        void shouldNotFail_whenIdDoesNotExist() {
            // Given - no record with this ID

            // When / Then - should not throw
            assertThatCode(() -> {
                programProviderDAO.deleteProgramProvider(999999L);
                hibernateTemplate.flush();
            }).doesNotThrowAnyException();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException when ID is null")
        void shouldThrowIllegalArgumentException_whenIdIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.deleteProgramProvider(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException when ID is negative")
        void shouldThrowIllegalArgumentException_whenIdIsNegative() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.deleteProgramProvider(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should only delete specified record and not affect others")
        void shouldOnlyDeleteSpecifiedRecord_whenMultipleExist() {
            // Given
            ProgramProvider pp1 = createProgramProvider(testProviderNo1, testProgramId1);
            ProgramProvider pp2 = createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            // When
            programProviderDAO.deleteProgramProvider(pp1.getId());
            hibernateTemplate.flush();

            // Then
            assertThat(programProviderDAO.getProgramProvider(pp1.getId())).isNull();
            assertThat(programProviderDAO.getProgramProvider(pp2.getId())).isNotNull();
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#deleteProgramProviderByProgramId(Long)}.
     * Loop-deletes all ProgramProviders for a given programId, evicting cache for each.
     */
    @Nested
    @DisplayName("deleteProgramProviderByProgramId (loop delete with cache eviction)")
    class DeleteProgramProviderByProgramId {

        @Test
        @Tag("delete")
        @DisplayName("should delete all program providers for the specified program")
        void shouldDeleteAll_whenProgramIdMatches() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            programProviderDAO.deleteProgramProviderByProgramId(testProgramId1);
            hibernateTemplate.flush();

            // Then - program1 providers deleted
            List<ProgramProvider> program1Providers = programProviderDAO.getProgramProviders(testProgramId1);
            assertThat(program1Providers).isEmpty();

            // And program2 providers still exist
            List<ProgramProvider> program2Providers = programProviderDAO.getProgramProviders(testProgramId2);
            assertThat(program2Providers).hasSize(1);
        }

        @Test
        @Tag("delete")
        @DisplayName("should not fail when program has no providers")
        void shouldNotFail_whenProgramHasNoProviders() {
            // Given - create a program with no providers
            Program emptyProgram = createProgram("Empty Program");
            hibernateTemplate.flush();
            Long emptyProgramId = (long) emptyProgram.getId();

            // When / Then - should not throw
            assertThatCode(() -> {
                programProviderDAO.deleteProgramProviderByProgramId(emptyProgramId);
                hibernateTemplate.flush();
            }).doesNotThrowAnyException();
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException when programId is null")
        void shouldThrowIllegalArgumentException_whenProgramIdIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.deleteProgramProviderByProgramId(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("delete")
        @DisplayName("should throw IllegalArgumentException when programId is zero or negative")
        void shouldThrowIllegalArgumentException_whenProgramIdIsZeroOrNegative() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.deleteProgramProviderByProgramId(0L))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programProviderDAO.deleteProgramProviderByProgramId(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramProvidersInTeam(Integer, Integer)}.
     * Uses LEFT JOIN with ProgramTeam via the pp.teams set mapping.
     */
    @Nested
    @DisplayName("getProgramProvidersInTeam (LEFT JOIN with ProgramTeam)")
    class GetProgramProvidersInTeam {

        @Test
        @Tag("query")
        @DisplayName("should return providers assigned to the specified team and program")
        void shouldReturnProviders_whenMatchingTeamAndProgram() {
            // Given
            ProgramTeam team = createProgramTeam("Team Alpha", testProgramId1.intValue());
            hibernateTemplate.flush();

            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1);
            Set<ProgramTeam> teams = new HashSet<>();
            teams.add(team);
            pp.setTeams(teams);
            hibernateTemplate.saveOrUpdate(pp);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO
                .getProgramProvidersInTeam(testProgramId1.intValue(), team.getId());

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(ProgramProvider::getId)
                .containsExactly(pp.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no providers in team")
        void shouldReturnEmpty_whenNoProvidersInTeam() {
            // Given
            ProgramTeam team = createProgramTeam("Empty Team", testProgramId1.intValue());
            hibernateTemplate.flush();

            // Create a provider NOT assigned to this team
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO
                .getProgramProvidersInTeam(testProgramId1.intValue(), team.getId());

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when programId is null or invalid")
        void shouldThrowIllegalArgumentException_whenProgramIdIsInvalid() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when teamId is null or invalid")
        void shouldThrowIllegalArgumentException_whenTeamIdIsInvalid() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(1, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> programProviderDAO.getProgramProvidersInTeam(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramDomain(String)}.
     * Returns all ProgramProviders for a given provider number (alias for getProgramProvidersByProvider).
     */
    @Nested
    @DisplayName("getProgramDomain")
    class GetProgramDomain {

        @Test
        @Tag("read")
        @DisplayName("should return all program providers for specified provider")
        void shouldReturnAllProgramProviders_forSpecifiedProvider() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramDomain(testProviderNo1);

            // Then
            assertThat(results)
                .hasSize(2)
                .allSatisfy(pp -> assertThat(pp.getProviderNo()).isEqualTo(testProviderNo1));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when provider has no domain")
        void shouldReturnEmptyList_whenProviderHasNoDomain() {
            // Given - no records for testProviderNo2

            // When
            List<ProgramProvider> results = programProviderDAO.getProgramDomain(testProviderNo2);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getProgramDomain(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getActiveProgramDomain(String)}.
     * Uses an implicit JOIN: pp.ProgramId=p.id and p.programStatus='active'.
     */
    @Nested
    @DisplayName("getActiveProgramDomain (implicit JOIN with Program)")
    class GetActiveProgramDomain {

        @Test
        @Tag("query")
        @DisplayName("should return only providers in active programs")
        void shouldReturnOnlyProviders_inActivePrograms() {
            // Given
            Program inactiveProgram = createProgram("Inactive Program");
            inactiveProgram.setProgramStatus("inactive");
            hibernateTemplate.saveOrUpdate(inactiveProgram);
            hibernateTemplate.flush();
            Long inactiveProgramId = (long) inactiveProgram.getId();

            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, inactiveProgramId);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getActiveProgramDomain(testProviderNo1);

            // Then - should only return the one in the active program
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pp -> assertThat(pp.getProgramId()).isEqualTo(testProgramId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return multiple providers when all programs are active")
        void shouldReturnMultipleProviders_whenAllProgramsAreActive() {
            // Given - both test programs are active by default
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getActiveProgramDomain(testProviderNo1);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when all programs are inactive")
        void shouldReturnEmptyList_whenAllProgramsAreInactive() {
            // Given
            Program inactiveProgram = createProgram("Inactive Only");
            inactiveProgram.setProgramStatus("inactive");
            hibernateTemplate.saveOrUpdate(inactiveProgram);
            hibernateTemplate.flush();

            createProgramProvider(testProviderNo1, (long) inactiveProgram.getId());
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO.getActiveProgramDomain(testProviderNo1);

            // Then - should not include the inactive program provider
            assertThat(results)
                .noneMatch(pp -> pp.getProgramId().equals((long) inactiveProgram.getId()));
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getActiveProgramDomain(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getProgramDomainByFacility(String, Integer)}.
     * Uses named parameters: pp.ProviderNo = :providerNo and s.facilityId = :facilityId or s.facilityId is null.
     */
    @Nested
    @DisplayName("getProgramDomainByFacility (subquery)")
    class GetProgramDomainByFacility {

        @Test
        @Tag("query")
        @DisplayName("should return providers for programs in specified facility")
        void shouldReturnProviders_forProgramsInSpecifiedFacility() {
            // Given - program1 is in facility1, program2 is in facility2
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            List<ProgramProvider> results = programProviderDAO
                .getProgramDomainByFacility(testProviderNo1, testFacilityId1);

            // Then - only program1 (facility1) should be returned
            assertThat(results)
                .hasSize(1)
                .allSatisfy(pp -> assertThat(pp.getProgramId()).isEqualTo(testProgramId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider has no programs in facility")
        void shouldReturnEmpty_whenProviderHasNoProgramsInFacility() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When - query for facility2, but provider1 is only in facility1's program
            List<ProgramProvider> results = programProviderDAO
                .getProgramDomainByFacility(testProviderNo1, testFacilityId2);

            // Then
            assertThat(results)
                .noneMatch(pp -> pp.getProgramId().equals(testProgramId1));
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() ->
                programProviderDAO.getProgramDomainByFacility(null, testFacilityId1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#isThisProgramInProgramDomain(String, Integer)}.
     * Returns boolean indicating whether a provider is associated with a specific program.
     */
    @Nested
    @DisplayName("isThisProgramInProgramDomain (boolean return)")
    class IsThisProgramInProgramDomain {

        @Test
        @Tag("query")
        @DisplayName("should return true when provider is associated with the program")
        void shouldReturnTrue_whenProviderIsAssociatedWithProgram() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When
            boolean result = programProviderDAO
                .isThisProgramInProgramDomain(testProviderNo1, testProgramId1.intValue());

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when provider is not associated with the program")
        void shouldReturnFalse_whenProviderIsNotAssociatedWithProgram() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            // When
            boolean result = programProviderDAO
                .isThisProgramInProgramDomain(testProviderNo1, testProgramId2.intValue());

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false when provider has no programs at all")
        void shouldReturnFalse_whenProviderHasNoPrograms() {
            // Given - no records

            // When
            boolean result = programProviderDAO
                .isThisProgramInProgramDomain(testProviderNo2, testProgramId1.intValue());

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() ->
                programProviderDAO.isThisProgramInProgramDomain(null, testProgramId1.intValue()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#getFacilitiesInProgramDomain(String)}.
     * 3-way JOIN: select distinct f from Facility f, Program p, ProgramProvider pp
     * where pp.ProgramId = p.id and f.id = p.facilityId and pp.ProviderNo = ?1.
     * Returns List of Facility entities.
     */
    @Nested
    @DisplayName("getFacilitiesInProgramDomain (3-way JOIN returning Facility)")
    class GetFacilitiesInProgramDomain {

        @Test
        @Tag("query")
        @DisplayName("should return distinct facilities for provider's programs")
        void shouldReturnDistinctFacilities_forProviderPrograms() {
            // Given - provider1 is in program1 (facility1) and program2 (facility2)
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            // When
            List<Facility> results = programProviderDAO.getFacilitiesInProgramDomain(testProviderNo1);

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(Facility::getId)
                .containsExactlyInAnyOrder(testFacilityId1, testFacilityId2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return distinct facilities even when provider has multiple programs in same facility")
        void shouldReturnDistinctFacilities_whenMultipleProgramsInSameFacility() {
            // Given - create another program in facility1
            Program anotherProgram = createProgram("Another Program in Fac1", testFacilityId1);
            hibernateTemplate.flush();
            Long anotherProgramId = (long) anotherProgram.getId();

            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, anotherProgramId);
            hibernateTemplate.flush();

            // When
            List<Facility> results = programProviderDAO.getFacilitiesInProgramDomain(testProviderNo1);

            // Then - should only return facility1 once (DISTINCT)
            assertThat(results)
                .hasSize(1)
                .extracting(Facility::getId)
                .containsExactly(testFacilityId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider has no programs")
        void shouldReturnEmpty_whenProviderHasNoPrograms() {
            // When
            List<Facility> results = programProviderDAO.getFacilitiesInProgramDomain(testProviderNo2);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should not include other providers' facilities")
        void shouldNotIncludeOtherProvidersFacilities_whenFilteringByProvider() {
            // Given
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId2);
            hibernateTemplate.flush();

            // When
            List<Facility> results = programProviderDAO.getFacilitiesInProgramDomain(testProviderNo1);

            // Then - should only include facility1 (provider1's program's facility)
            assertThat(results)
                .hasSize(1)
                .extracting(Facility::getId)
                .containsExactly(testFacilityId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when provider number is null")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() -> programProviderDAO.getFacilitiesInProgramDomain(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for {@link ProgramProviderDAO#updateProviderRole(ProgramProvider, Long)}.
     * Updates the roleId on an existing ProgramProvider.
     */
    @Nested
    @DisplayName("updateProviderRole")
    class UpdateProviderRole {

        @Test
        @Tag("update")
        @DisplayName("should update role ID on existing program provider")
        void shouldUpdateRoleId_onExistingProgramProvider() {
            // Given
            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            hibernateTemplate.flush();
            Long ppId = pp.getId();

            // When
            programProviderDAO.updateProviderRole(pp, testRoleId2);
            hibernateTemplate.flush();

            // Then
            ProgramProvider found = programProviderDAO.getProgramProvider(ppId);
            assertThat(found).isNotNull();
            assertThat(found.getRoleId()).isEqualTo(testRoleId2);
        }

        @Test
        @Tag("update")
        @DisplayName("should set role ID when it was previously null")
        void shouldSetRoleId_whenPreviouslyNull() {
            // Given
            ProgramProvider pp = createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();
            assertThat(pp.getRoleId()).isNull();
            Long ppId = pp.getId();

            // When
            programProviderDAO.updateProviderRole(pp, testRoleId1);
            hibernateTemplate.flush();

            // Then
            ProgramProvider found = programProviderDAO.getProgramProvider(ppId);
            assertThat(found).isNotNull();
            assertThat(found.getRoleId()).isEqualTo(testRoleId1);
        }
    }

    // =========================================================================
    // Input validation tests for methods with parameter guards
    // =========================================================================

    /**
     * Input validation tests for methods that throw IllegalArgumentException
     * on invalid input parameters.
     */
    @Nested
    @DisplayName("Input validation (parameter guard tests)")
    class InputValidation {

        @Test
        @Tag("read")
        @DisplayName("getProgramProviders should throw on null programId")
        void shouldThrowOnNullProgramId_forGetProgramProviders() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProviders(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("getProgramProviders should throw on negative programId")
        void shouldThrowOnNegativeProgramId_forGetProgramProviders() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProviders(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (2 params) should throw on null providerNo")
        void shouldThrowOnNullProviderNo_forGetProgramProviderTwoParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(null, testProgramId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (2 params) should throw on null programId")
        void shouldThrowOnNullProgramId_forGetProgramProviderTwoParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(testProviderNo1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (3 params) should throw on null providerNo")
        void shouldThrowOnNullProviderNo_forGetProgramProviderThreeParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(null, testProgramId1, testRoleId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (3 params) should throw on null programId")
        void shouldThrowOnNullProgramId_forGetProgramProviderThreeParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(testProviderNo1, null, testRoleId1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (3 params) should throw on null roleId")
        void shouldThrowOnNullRoleId_forGetProgramProviderThreeParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("getProgramProvider (3 params) should throw on negative roleId")
        void shouldThrowOnNegativeRoleId_forGetProgramProviderThreeParams() {
            assertThatThrownBy(() -> programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, -1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
