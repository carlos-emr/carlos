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
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderFacility;
import io.github.carlos_emr.carlos.commn.model.ProviderFacilityPK;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderDao} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?0, ?1, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, name-based searches, active/inactive filtering,
 * team queries, OHIP credential lookups, facility joins, and native SQL methods.</p>
 *
 * @since 2026-02-26
 * @see ProviderDao
 */
@DisplayName("PMmodule ProviderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProviderDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Create test providers using HibernateTemplate
        persistProvider("T001", "John", "Smith", "1", "doctor");
        persistProvider("T002", "John", "Doe", "1", "doctor");
        persistProvider("T003", "Jane", "Smith", "1", "nurse");
        persistProvider("T004", "Bob", "Johnson", "0", "doctor");  // Inactive

        hibernateTemplate.flush();
    }

    /**
     * Creates a new Provider with the specified attributes and persists it via HibernateTemplate.
     *
     * @param providerNo String the provider number (VARCHAR(6) constraint)
     * @param firstName String the provider's first name
     * @param lastName String the provider's last name
     * @param status String the provider status ("1" for active, "0" for inactive)
     * @param providerType String the provider type (e.g., "doctor", "nurse")
     * @return Provider the persisted entity
     */
    private Provider persistProvider(String providerNo, String firstName,
                                     String lastName, String status, String providerType) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setProviderType(providerType);
        provider.setSex("M");
        provider.setSpecialty("");
        hibernateTemplate.save(provider);
        return provider;
    }

    /** Tests for CRUD read operations on Provider entities. */
    @Nested
    @DisplayName("CRUD read operations")
    class CrudReadOperations {

        @Test
        @Tag("read")
        @DisplayName("should get provider by provider number")
        void shouldGetProvider_whenValidProviderNoProvided() {
            // When
            Provider found = providerDao.getProvider("T001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("John");
            assertThat(found.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when provider number is empty")
        void shouldReturnNull_whenProviderNoIsEmpty() {
            // When
            Provider found = providerDao.getProvider("");

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return true when provider exists")
        void shouldReturnTrue_whenProviderExists() {
            // When/Then
            assertThat(providerDao.providerExists("T001")).isTrue();
        }

        @Test
        @Tag("read")
        @DisplayName("should return false when provider does not exist")
        void shouldReturnFalse_whenProviderDoesNotExist() {
            // When/Then
            assertThat(providerDao.providerExists("NONEXISTENT")).isFalse();
        }

        @Test
        @Tag("read")
        @DisplayName("should get provider name as first last")
        void shouldGetProviderName_whenValidProviderNoProvided() {
            // When
            String name = providerDao.getProviderName("T001");

            // Then
            assertThat(name).contains("John").contains("Smith");
        }
    }

    /** Tests for CRUD write operations on Provider entities. */
    @Nested
    @DisplayName("CRUD write operations")
    class CrudWriteOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist new provider with valid data")
        void shouldPersistProvider_whenValidDataProvided() {
            // Given
            Provider newProvider = new Provider();
            newProvider.setProviderNo("T099");
            newProvider.setFirstName("New");
            newProvider.setLastName("Provider");
            newProvider.setStatus("1");
            newProvider.setProviderType("doctor");
            newProvider.setSex("F");
            newProvider.setSpecialty("Cardiology");

            // When
            providerDao.saveProvider(newProvider);
            hibernateTemplate.flush();

            // Then
            Provider found = providerDao.getProvider("T099");
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("New");
            assertThat(found.getLastName()).isEqualTo("Provider");
            assertThat(found.getSpecialty()).isEqualTo("Cardiology");
        }

        @Test
        @Tag("update")
        @DisplayName("should update provider information when changes occur")
        void shouldUpdateProvider_whenChangesOccur() {
            // Given
            Provider existing = providerDao.getProvider("T001");
            assertThat(existing).isNotNull();
            existing.setSpecialty("Neurology");

            // When
            providerDao.updateProvider(existing);
            hibernateTemplate.flush();

            // Then
            Provider updated = providerDao.getProvider("T001");
            assertThat(updated.getSpecialty()).isEqualTo("Neurology");
        }
    }

    /** Tests for getProviderFromFirstLastName (2 params). */
    @Nested
    @DisplayName("getProviderFromFirstLastName (2 params)")
    class GetProviderFromFirstLastName {

        @Test
        @Tag("query")
        @DisplayName("should find provider when both first and last name match exactly")
        void shouldFindProvider_whenBothNamesMatchExactly() {
            // When
            List<Provider> results = providerDao.getProviderFromFirstLastName("John", "Smith");

            // Then - Only T001 should match (exact match)
            assertThat(results)
                .hasSize(1)
                .extracting(Provider::getProviderNo)
                .containsExactly("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when first name doesn't match")
        void shouldReturnEmpty_whenFirstNameDoesntMatch() {
            // When
            List<Provider> results = providerDao.getProviderFromFirstLastName("Nonexistent", "Smith");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when last name doesn't match")
        void shouldReturnEmpty_whenLastNameDoesntMatch() {
            // When
            List<Provider> results = providerDao.getProviderFromFirstLastName("John", "Nonexistent");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for getProviderLikeFirstLastName (2 params). */
    @Nested
    @DisplayName("getProviderLikeFirstLastName (2 params)")
    class GetProviderLikeFirstLastName {

        @Test
        @Tag("query")
        @DisplayName("should find providers matching partial first and last name")
        void shouldFindProviders_whenPartialNamesMatch() {
            // When - Search with partial names
            List<Provider> results = providerDao.getProviderLikeFirstLastName("Jo%", "Sm%");

            // Then - T001 should match (John Smith)
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return multiple providers when pattern matches many")
        void shouldReturnMultiple_whenPatternMatchesMany() {
            // When - Search for all Johns
            List<Provider> results = providerDao.getProviderLikeFirstLastName("John", "%");

            // Then - Should find T001 and T002
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002");
        }
    }

    /** Tests for getActiveProviderLikeFirstLastName (2 params). */
    @Nested
    @DisplayName("getActiveProviderLikeFirstLastName (2 params)")
    class GetActiveProviderLikeFirstLastName {

        @Test
        @Tag("query")
        @DisplayName("should find only active providers matching names")
        void shouldFindOnlyActiveProviders_whenNamesMatch() {
            // Given - T004 (Bob Johnson) is inactive

            // When
            List<Provider> results = providerDao.getActiveProviderLikeFirstLastName("Bob", "Johnson");

            // Then - Should not find T004 because inactive
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find active providers with matching names")
        void shouldFindActiveProviders_whenNamesMatch() {
            // When
            List<Provider> results = providerDao.getActiveProviderLikeFirstLastName("John", "%");

            // Then - Should find active Johns (T001, T002)
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002")
                .doesNotContain("T004");
        }
    }

    /** Tests for query methods with single parameters (baseline coverage). */
    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should retrieve all providers via query method")
        void shouldRetrieveAllProviders_fromDatabase() {
            // When
            List<Provider> results = providerDao.getProviders();

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003", "T004");
        }

        @Test
        @Tag("filter")
        @DisplayName("should filter active providers by status parameter")
        void shouldFilterActiveProviders_byStatusParameter() {
            // When
            List<Provider> activeProviders = providerDao.getActiveProviders();

            // Then - T004 (inactive) should be excluded
            assertThat(activeProviders)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003")
                .doesNotContain("T004");
        }

        @Test
        @Tag("read")
        @DisplayName("should get providers by type")
        void shouldGetProviders_byType() {
            // When
            List<Provider> doctors = providerDao.getProvidersByType("doctor");

            // Then
            assertThat(doctors)
                .extracting(Provider::getProviderType)
                .allMatch(type -> type.equals("doctor"));
        }

        @Test
        @Tag("filter")
        @DisplayName("should retrieve billable providers with billing filter")
        void shouldRetrieveBillableProviders_withBillingFilter() {
            // Given - Add a provider with an OHIP number
            Provider billable = persistProvider("T005", "Bill", "Able", "1", "doctor");
            billable.setOhipNo("12345");
            hibernateTemplate.update(billable);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getBillableProviders();

            // Then - Should contain the billable provider
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("filter")
        @DisplayName("should find doctors with OHIP certification applied")
        void shouldFindDoctors_withOhipCertification() {
            // Given - Add provider with OHIP
            Provider withOhip = persistProvider("T006", "Doc", "Ohip", "1", "doctor");
            withOhip.setOhipNo("OH123");
            hibernateTemplate.update(withOhip);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getDoctorsWithOhip();

            // Then
            assertThat(results).isNotEmpty();
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T006");
        }

        @Test
        @Tag("read")
        @DisplayName("should get provider name in last-first format")
        void shouldGetProviderName_inLastFirstFormat() {
            // When
            String name = providerDao.getProviderNameLastFirst("T001");

            // Then
            assertThat(name).isNotNull();
            assertThat(name).contains("Smith").contains("John");
        }

        @Test
        @Tag("filter")
        @DisplayName("should search providers using name pattern matching")
        void shouldSearchProviders_byNamePattern() {
            // When
            List<Provider> results = providerDao.search("John");

            // Then
            assertThat(results).isNotEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should get providers by boolean active filter")
        void shouldGetProviders_byActiveFilter() {
            // When - getProviders(true) returns active, getProviders(false) returns inactive
            List<Provider> activeOnly = providerDao.getProviders(true);
            List<Provider> inactiveOnly = providerDao.getProviders(false);

            // Then - active filter excludes inactive provider T004
            assertThat(activeOnly).isNotEmpty();
            assertThat(activeOnly)
                .extracting(Provider::getProviderNo)
                .doesNotContain("T004");

            // Inactive filter includes only inactive provider T004
            assertThat(inactiveOnly)
                .extracting(Provider::getProviderNo)
                .contains("T004");
        }
    }

    /**
     * Tests for getCurrentTeamProviders(String providerNo).
     *
     * <p>This method uses SQL injection-prone string concatenation to build a query that
     * finds active providers with non-empty OHIP numbers on the same team as the given
     * provider. The string-concatenated HQL is a known security and migration risk.</p>
     */
    @Nested
    @DisplayName("getCurrentTeamProviders (1 param: providerNo)")
    class GetCurrentTeamProviders {

        @Test
        @Tag("query")
        @DisplayName("should return team providers with non-empty OHIP numbers")
        void shouldReturnTeamProviders_withNonEmptyOhip() {
            // Given — set up two providers on same team with OHIP, one without, one on different team
            Provider withOhip1 = persistProvider("TM001", "Team", "One", "1", "doctor");
            withOhip1.setTeam("TeamA");
            withOhip1.setOhipNo("OH001");
            hibernateTemplate.update(withOhip1);

            Provider withOhip2 = persistProvider("TM002", "Team", "Two", "1", "doctor");
            withOhip2.setTeam("TeamA");
            withOhip2.setOhipNo("OH002");
            hibernateTemplate.update(withOhip2);

            Provider noOhip = persistProvider("TM003", "No", "Ohip", "1", "doctor");
            noOhip.setTeam("TeamA");
            // OhipNo is null/empty — should be excluded
            hibernateTemplate.update(noOhip);

            Provider diffTeam = persistProvider("TM004", "Diff", "Team", "1", "doctor");
            diffTeam.setTeam("TeamB");
            diffTeam.setOhipNo("OH004");
            hibernateTemplate.update(diffTeam);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getCurrentTeamProviders("TM001");

            // Then — same-team + OHIP providers returned; different team excluded
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("TM001", "TM002")
                .doesNotContain("TM003", "TM004");
        }

        @Test
        @Tag("query")
        @DisplayName("should include requesting provider when only team member")
        void shouldIncludeRequestingProvider_whenOnlyTeamMember() {
            // Given — solo provider on a unique team
            Provider solo = persistProvider("TM010", "Solo", "Provider", "1", "doctor");
            solo.setTeam("UniqTeam");
            solo.setOhipNo("OHSOLO");
            hibernateTemplate.update(solo);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getCurrentTeamProviders("TM010");

            // Then — the requesting provider should be found
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("TM010");
        }
    }

    /**
     * Tests for getProviderByPractitionerNo(String[] practitionerNoTypes, String practitionerNo).
     *
     * <p>PR #89 fixed the pre-existing bug where {@code IN (?0)} with a {@code String[]}
     * caused a ClassCastException. The fix converts to a named parameter with proper list
     * binding ({@code IN (:types)} + {@code setParameterList}), so the method now works
     * correctly.</p>
     */
    @Nested
    @DisplayName("getProviderByPractitionerNo (2 params: types[], practitionerNo)")
    class GetProviderByPractitionerNoArray {

        @Test
        @Tag("query")
        @DisplayName("should return provider when types array and practitioner number match")
        void shouldReturnProvider_whenTypesArrayAndPractitionerNoMatch() {
            // Given
            Provider prov = persistProvider("PN001", "Pract", "Test", "1", "doctor");
            prov.setPractitionerNo("PRAC12345");
            prov.setPractitionerNoType("MSP");
            hibernateTemplate.update(prov);
            hibernateTemplate.flush();

            // When — PR #89 fixed IN (?0) with String[] by switching to named param binding;
            // the method now returns the correct result instead of throwing ClassCastException.
            Provider result = providerDao.getProviderByPractitionerNo(new String[]{"MSP"}, "PRAC12345");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo("PN001");
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when types array is null")
        void shouldThrowIllegalArgumentException_whenTypesArrayIsNull() {
            // When/Then — null types array triggers the guard clause
            assertThatThrownBy(() ->
                providerDao.getProviderByPractitionerNo((String[]) null, "PRAC12345")
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for getActiveProviders(String facilityId, String programId).
     *
     * <p>This method has three branches: programId-based (ProgramProvider subquery),
     * facilityId-based (Program subquery), and null/null (all active). Uses positional
     * parameters that require renaming from ?0 to ?1 for Hibernate 6 compatibility.</p>
     */
    @Nested
    @DisplayName("getActiveProviders (2 params: facilityId, programId)")
    class GetActiveProvidersByFacilityAndProgram {

        @Test
        @Tag("query")
        @DisplayName("should return active providers for specific program")
        void shouldReturnActiveProviders_forSpecificProgram() {
            // Given — create Program first (FK constraint from program_provider)
            io.github.carlos_emr.carlos.PMmodule.model.Program program =
                new io.github.carlos_emr.carlos.PMmodule.model.Program();
            program.setName("Test Program");
            program.setType("community");
            program.setProgramStatus("active");
            hibernateTemplate.save(program);
            hibernateTemplate.flush();
            Integer programId = program.getId();

            // Create a provider and link via ProgramProvider
            Provider progProv = persistProvider("PP001", "Prog", "Prov", "1", "doctor");
            hibernateTemplate.flush();

            ProgramProvider pp = new ProgramProvider();
            pp.setProgramId(programId.longValue());
            pp.setProviderNo("PP001");
            hibernateTemplate.save(pp);
            hibernateTemplate.flush();

            // When — query by programId (triggers ProgramProvider subquery)
            List<Provider> results = providerDao.getActiveProviders(null, String.valueOf(programId));

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("PP001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return all active providers when both params are null")
        void shouldReturnAllActiveProviders_whenBothParamsAreNull() {
            // When — null/null triggers the all-active branch
            List<Provider> results = providerDao.getActiveProviders((String) null, (String) null);

            // Then — should include active providers from @BeforeEach (T001, T002, T003)
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003")
                .doesNotContain("T004"); // T004 is inactive
        }

        @Test
        @Tag("query")
        @DisplayName("should return active providers for specific facility")
        void shouldReturnActiveProviders_forSpecificFacility() {
            // Given - create a Facility, Program linked to it, and a ProgramProvider
            Facility facility = new Facility();
            facility.setName("Test Facility");
            facility.setDisabled(false);
            entityManager.persist(facility);
            entityManager.flush();
            Integer facilityId = facility.getId();

            io.github.carlos_emr.carlos.PMmodule.model.Program program =
                new io.github.carlos_emr.carlos.PMmodule.model.Program();
            program.setName("Facility Program");
            program.setType("community");
            program.setProgramStatus("active");
            program.setFacilityId(facilityId);
            hibernateTemplate.save(program);
            hibernateTemplate.flush();

            Provider facProv = persistProvider("FP001", "Fac", "Prov", "1", "doctor");
            hibernateTemplate.flush();

            ProgramProvider pp = new ProgramProvider();
            pp.setProgramId(program.getId().longValue());
            pp.setProviderNo("FP001");
            hibernateTemplate.save(pp);
            hibernateTemplate.flush();

            // When - query by facilityId (triggers Program subquery branch)
            List<Provider> results = providerDao.getActiveProviders(String.valueOf(facilityId), (String) null);

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("FP001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return all active providers when both params are zero strings")
        void shouldReturnAllActiveProviders_whenBothParamsAreZero() {
            // When - "0"/"0" also triggers the all-active branch
            List<Provider> results = providerDao.getActiveProviders("0", "0");

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003")
                .doesNotContain("T004");
        }
    }

    /** Tests for getProviderName and getProviderNameLastFirst edge cases. */
    @Nested
    @DisplayName("Provider name formatting edge cases")
    class ProviderNameFormattingEdgeCases {

        @Test
        @Tag("read")
        @DisplayName("should return empty string when provider not found for getProviderName")
        void shouldReturnEmptyString_whenProviderNotFoundForGetProviderName() {
            // When
            String name = providerDao.getProviderName("NONEXISTENT");

            // Then
            assertThat(name).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when provider number is null for getProvider")
        void shouldReturnNull_whenProviderNoIsNull() {
            // When
            Provider found = providerDao.getProvider(null);

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when provider number is null for getProviderNameLastFirst")
        void shouldThrowIllegalArgumentException_whenProviderNoIsNullForLastFirst() {
            // When/Then
            assertThatThrownBy(() -> providerDao.getProviderNameLastFirst(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException when provider number is empty for getProviderNameLastFirst")
        void shouldThrowIllegalArgumentException_whenProviderNoIsEmptyForLastFirst() {
            // When/Then
            assertThatThrownBy(() -> providerDao.getProviderNameLastFirst(""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty string when provider not found for getProviderNameLastFirst")
        void shouldReturnEmptyString_whenProviderNotFoundForLastFirst() {
            // When
            String name = providerDao.getProviderNameLastFirst("ZZZZZZ");

            // Then
            assertThat(name).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should format name as last comma first for getProviderNameLastFirst")
        void shouldFormatNameAsLastCommaFirst_forGetProviderNameLastFirst() {
            // When
            String name = providerDao.getProviderNameLastFirst("T001");

            // Then
            assertThat(name).isEqualTo("Smith, John");
        }

        @Test
        @Tag("read")
        @DisplayName("should format name as first space last for getProviderName")
        void shouldFormatNameAsFirstSpaceLast_forGetProviderName() {
            // When
            String name = providerDao.getProviderName("T001");

            // Then
            assertThat(name).isEqualTo("John Smith");
        }
    }

    /** Tests for getActiveProviders(boolean filterOutSystemAndImportedProviders). */
    @Nested
    @DisplayName("getActiveProviders with system/imported filter")
    class GetActiveProvidersWithSystemFilter {

        @Test
        @Tag("filter")
        @DisplayName("should include system providers when filter is false")
        void shouldIncludeSystemProviders_whenFilterIsFalse() {
            // Given - create a system provider (negative provider_no)
            Provider sysProvider = persistProvider("-1", "System", "Provider", "1", "doctor");
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getActiveProviders(false);

            // Then - system provider included
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("-1", "T001", "T002", "T003");
        }

        @Test
        @Tag("filter")
        @DisplayName("should exclude system providers when filter is true")
        void shouldExcludeSystemProviders_whenFilterIsTrue() {
            // Given - create a system provider (negative provider_no)
            Provider sysProvider = persistProvider("-1", "System", "Provider", "1", "doctor");
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getActiveProviders(true);

            // Then - system provider excluded (provider_no > -1)
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .doesNotContain("-1")
                .contains("T001", "T002", "T003");
        }
    }

    /** Tests for getActiveProvider(String providerNo) - single active provider lookup. */
    @Nested
    @DisplayName("getActiveProvider (single provider)")
    class GetActiveProviderSingle {

        @Test
        @Tag("read")
        @DisplayName("should return provider when active and matching provider number")
        void shouldReturnProvider_whenActiveAndMatching() {
            // When
            List<Provider> results = providerDao.getActiveProvider("T001");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo("T001");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when provider is inactive")
        void shouldReturnEmpty_whenProviderIsInactive() {
            // When - T004 is inactive
            List<Provider> results = providerDao.getActiveProvider("T004");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty when provider does not exist")
        void shouldReturnEmpty_whenProviderDoesNotExist() {
            // When
            List<Provider> results = providerDao.getActiveProvider("ZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for provider type pattern queries. */
    @Nested
    @DisplayName("Provider type query variants")
    class ProviderTypeQueryVariants {

        @Test
        @Tag("query")
        @DisplayName("should find providers by type pattern using LIKE")
        void shouldFindProviders_byTypePattern() {
            // When - "doc%" should match "doctor"
            List<Provider> results = providerDao.getProvidersByTypePattern("doc%");

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T004");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when type pattern matches nothing")
        void shouldReturnEmpty_whenTypePatternMatchesNothing() {
            // When
            List<Provider> results = providerDao.getProvidersByTypePattern("xyznonexistent%");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by type with non-empty OHIP number")
        void shouldFindProviders_byTypeWithNonEmptyOhip() {
            // Given - set OHIP on one doctor
            Provider doc = providerDao.getProvider("T001");
            doc.setOhipNo("OH999");
            hibernateTemplate.update(doc);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getProvidersByTypeWithNonEmptyOhipNo("doctor");

            // Then - only T001 has OHIP among doctors
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no provider of type has OHIP")
        void shouldReturnEmpty_whenNoProviderOfTypeHasOhip() {
            // When - nurses have no OHIP set
            List<Provider> results = providerDao.getProvidersByTypeWithNonEmptyOhipNo("nurse");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for team-related query methods. */
    @Nested
    @DisplayName("Team-related queries")
    class TeamRelatedQueries {

        @BeforeEach
        void setUpTeams() {
            // Assign teams to existing providers
            Provider t001 = providerDao.getProvider("T001");
            t001.setTeam("Alpha");
            t001.setOhipNo("OH001");
            hibernateTemplate.update(t001);

            Provider t002 = providerDao.getProvider("T002");
            t002.setTeam("Alpha");
            t002.setOhipNo("OH002");
            hibernateTemplate.update(t002);

            Provider t003 = providerDao.getProvider("T003");
            t003.setTeam("Beta");
            hibernateTemplate.update(t003);

            // T004 inactive, no team
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should return unique team names including null/empty")
        void shouldReturnUniqueTeamNames_includingNullOrEmpty() {
            // When
            List<String> teams = providerDao.getUniqueTeams();

            // Then - should include Alpha, Beta, and possibly null/empty from T004
            assertThat(teams).contains("Alpha", "Beta");
        }

        @Test
        @Tag("query")
        @DisplayName("should return active teams only excluding empty strings")
        void shouldReturnActiveTeams_excludingEmpty() {
            // When
            List<String> activeTeams = providerDao.getActiveTeams();

            // Then - only teams from active providers with non-empty team values
            assertThat(activeTeams).contains("Alpha");
            // Beta is from active T003
            assertThat(activeTeams).contains("Beta");
        }

        @Test
        @Tag("query")
        @DisplayName("should return provider numbers in a specific team")
        void shouldReturnProviderNumbers_inSpecificTeam() {
            // When
            List<String> providerNos = providerDao.getProvidersInTeam("Alpha");

            // Then
            assertThat(providerNos).contains("T001", "T002");
            assertThat(providerNos).doesNotContain("T003");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when team does not exist")
        void shouldReturnEmpty_whenTeamDoesNotExist() {
            // When
            List<String> providerNos = providerDao.getProvidersInTeam("NonexistentTeam");

            // Then
            assertThat(providerNos).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return billable providers on same team")
        void shouldReturnBillableProviders_onSameTeam() {
            // Given - provider on team Alpha with OHIP
            Provider p = providerDao.getProvider("T001");

            // When
            List<Provider> results = providerDao.getBillableProvidersOnTeam(p);

            // Then - T001 and T002 are on Alpha team with OHIP and active
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002");
        }
    }

    /** Tests for practitioner number lookup (single param). */
    @Nested
    @DisplayName("getProviderByPractitionerNo (single param)")
    class GetProviderByPractitionerNoSingle {

        @Test
        @Tag("query")
        @DisplayName("should find provider by practitioner number")
        void shouldFindProvider_byPractitionerNo() {
            // Given
            Provider prov = providerDao.getProvider("T001");
            prov.setPractitionerNo("MSP123");
            hibernateTemplate.update(prov);
            hibernateTemplate.flush();

            // When
            Provider found = providerDao.getProviderByPractitionerNo("MSP123");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when practitioner number not found")
        void shouldReturnNull_whenPractitionerNoNotFound() {
            // When
            Provider found = providerDao.getProviderByPractitionerNo("NONEXISTENT");

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when practitioner number is null")
        void shouldReturnNull_whenPractitionerNoIsNull() {
            // When
            Provider found = providerDao.getProviderByPractitionerNo((String) null);

            // Then
            assertThat(found).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when practitioner number is empty")
        void shouldReturnNull_whenPractitionerNoIsEmpty() {
            // When
            Provider found = providerDao.getProviderByPractitionerNo("");

            // Then
            assertThat(found).isNull();
        }
    }

    /**
     * Tests for getProviderByPractitionerNo(String type, String no).
     *
     * <p>This two-param overload delegates to the three-param array version, which
     * has a known ClassCastException bug. The test documents that the delegation
     * propagates the bug.</p>
     */
    @Nested
    @DisplayName("getProviderByPractitionerNo (2 params: type, practitionerNo)")
    class GetProviderByPractitionerNoTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should throw ClassCastException because it delegates to array version")
        void shouldThrowClassCastException_whenDelegatingToArrayVersion() {
            // Given
            Provider prov = persistProvider("PN002", "Prac", "Two", "1", "doctor");
            prov.setPractitionerNo("PRAC999");
            prov.setPractitionerNoType("MSP");
            hibernateTemplate.update(prov);
            hibernateTemplate.flush();

            // When/Then - two-param delegates to array version which has the IN (?0) bug
            assertThatThrownBy(() ->
                providerDao.getProviderByPractitionerNo("MSP", "PRAC999")
            ).isInstanceOf(ClassCastException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when practitioner number is empty")
        void shouldThrowIllegalArgumentException_whenPractitionerNoIsEmpty() {
            // When/Then - empty practitionerNo triggers guard clause in array version
            assertThatThrownBy(() ->
                providerDao.getProviderByPractitionerNo("MSP", "")
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Tests for OHIP and credential-based queries. */
    @Nested
    @DisplayName("OHIP and credential queries")
    class OhipAndCredentialQueries {

        @BeforeEach
        void setUpCredentials() {
            // Set OHIP numbers on some active providers
            Provider t001 = providerDao.getProvider("T001");
            t001.setOhipNo("OH001");
            t001.setProviderType("doctor");
            hibernateTemplate.update(t001);

            Provider t002 = providerDao.getProvider("T002");
            t002.setOhipNo("OH002");
            t002.setProviderType("doctor");
            hibernateTemplate.update(t002);

            // T003 is active nurse, no OHIP
            // T004 is inactive doctor, no OHIP
            hibernateTemplate.flush();
        }

        @Test
        @Tag("query")
        @DisplayName("should find billable providers by OHIP number")
        void shouldFindBillableProviders_byOhipNo() {
            // When
            List<Provider> results = providerDao.getBillableProvidersByOHIPNo("OH001");

            // Then
            assertThat(results).isNotNull();
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should find billable providers by partial OHIP with wildcard")
        void shouldFindBillableProviders_byPartialOhipWithWildcard() {
            // When - LIKE query requires explicit wildcards
            List<Provider> results = providerDao.getBillableProvidersByOHIPNo("OH%");

            // Then
            assertThat(results).isNotNull();
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no provider matches OHIP number")
        void shouldReturnNull_whenNoProviderMatchesOhip() {
            // When
            List<Provider> results = providerDao.getBillableProvidersByOHIPNo("ZZZZZ");

            // Then
            assertThat(results).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when OHIP number is null")
        void shouldThrowIllegalArgumentException_whenOhipNoIsNull() {
            // When/Then
            assertThatThrownBy(() -> providerDao.getBillableProvidersByOHIPNo(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should throw IllegalArgumentException when OHIP number is empty")
        void shouldThrowIllegalArgumentException_whenOhipNoIsEmpty() {
            // When/Then
            assertThatThrownBy(() -> providerDao.getBillableProvidersByOHIPNo(""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @Tag("query")
        @DisplayName("should return providers with non-empty OHIP ordered by name")
        void shouldReturnProviders_withNonEmptyOhip() {
            // When
            List<Provider> results = providerDao.getProvidersWithNonEmptyOhip();

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002");
        }

        @Test
        @Tag("query")
        @DisplayName("should find doctors with non-empty credentials")
        void shouldFindDoctors_withNonEmptyCredentials() {
            // When
            List<Provider> results = providerDao.getDoctorsWithNonEmptyCredentials();

            // Then - only active doctors with non-empty OHIP
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002")
                .doesNotContain("T003", "T004"); // T003=nurse, T004=inactive
        }

        @Test
        @Tag("query")
        @DisplayName("should find all providers with non-empty credentials")
        void shouldFindAllProviders_withNonEmptyCredentials() {
            // When
            List<Provider> results = providerDao.getProvidersWithNonEmptyCredentials();

            // Then - all active providers with non-empty OHIP regardless of type
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return billable providers in BC with no-arg variant")
        void shouldReturnBillableProviders_inBcNoArg() {
            // Given - also set RMA and billing numbers on a provider to test OR logic
            Provider t003 = providerDao.getProvider("T003");
            t003.setRmaNo("RMA1");
            hibernateTemplate.update(t003);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getBillableProvidersInBC();

            // Then - includes providers with OHIP, RMA, BillingNo, or HsoNo that are active
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003");
        }
    }

    /** Tests for search methods with pagination. */
    @Nested
    @DisplayName("Search with pagination")
    class SearchWithPagination {

        @Test
        @Tag("search")
        @DisplayName("should search providers by last name with pagination")
        void shouldSearchProviders_byLastNameWithPagination() {
            // When - search for "Smith" starting at 0, returning 10
            List<Provider> results = providerDao.searchProviderByNamesString("Smith", 0, 10);

            // Then - T001 and T003 have last name Smith
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T003");
        }

        @Test
        @Tag("search")
        @DisplayName("should search providers by last name and first name with comma")
        void shouldSearchProviders_byLastAndFirstNameWithComma() {
            // When - "Smith,Jo" should match last_name LIKE %Smith% AND first_name LIKE %Jo%
            List<Provider> results = providerDao.searchProviderByNamesString("Smith,Jo", 0, 10);

            // Then - Only John Smith (T001) matches
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001")
                .doesNotContain("T003"); // Jane Smith - first name doesn't match "Jo"
        }

        @Test
        @Tag("search")
        @DisplayName("should respect pagination start index")
        void shouldRespectPaginationStartIndex_forSearchProviderByNamesString() {
            // When - skip past first result
            List<Provider> results = providerDao.searchProviderByNamesString("Smith", 1, 10);

            // Then - should have one fewer result than starting at 0
            List<Provider> allResults = providerDao.searchProviderByNamesString("Smith", 0, 10);
            assertThat(results.size()).isEqualTo(allResults.size() - 1);
        }

        @Test
        @Tag("search")
        @DisplayName("should limit results via itemsToReturn parameter")
        void shouldLimitResults_viaItemsToReturn() {
            // When - limit to 1 result
            List<Provider> results = providerDao.searchProviderByNamesString("Smith", 0, 1);

            // Then
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("search")
        @DisplayName("should return all providers when search string is null")
        void shouldReturnAllProviders_whenSearchStringIsNull() {
            // When
            List<Provider> results = providerDao.searchProviderByNamesString(null, 0, 100);

            // Then - returns all providers (no WHERE clause)
            assertThat(results).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        @Tag("search")
        @DisplayName("should search active providers with term and pagination")
        void shouldSearchActiveProviders_withTermAndPagination() {
            // When
            List<Provider> results = providerDao.search("Smith", true, 0, 10);

            // Then - should find active Smiths
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T003")
                .doesNotContain("T004"); // inactive
        }

        @Test
        @Tag("search")
        @DisplayName("should search inactive providers with term and pagination")
        void shouldSearchInactiveProviders_withTermAndPagination() {
            // When
            List<Provider> results = providerDao.search("Johnson", false, 0, 10);

            // Then - T004 is inactive Johnson
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T004");
        }

        @Test
        @Tag("search")
        @DisplayName("should return all active providers when search term is empty")
        void shouldReturnAllActiveProviders_whenSearchTermIsEmpty() {
            // When
            List<Provider> results = providerDao.search("", true, 0, 100);

            // Then - all active providers
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003")
                .doesNotContain("T004");
        }

        @Test
        @Tag("search")
        @DisplayName("should return all active providers when search term is null")
        void shouldReturnAllActiveProviders_whenSearchTermIsNull() {
            // When
            List<Provider> results = providerDao.search(null, true, 0, 100);

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001", "T002", "T003")
                .doesNotContain("T004");
        }

        @Test
        @Tag("search")
        @DisplayName("should respect pagination for search with active filter")
        void shouldRespectPagination_forSearchWithActiveFilter() {
            // When - limit to 2 results
            List<Provider> results = providerDao.search(null, true, 0, 2);

            // Then
            assertThat(results).hasSize(2);
        }
    }

    /** Tests for distinct provider queries. */
    @Nested
    @DisplayName("Distinct provider queries")
    class DistinctProviderQueries {

        @Test
        @Tag("query")
        @DisplayName("should return distinct provider numbers and types")
        void shouldReturnDistinctProviderNumbersAndTypes_whenQueried() {
            // When
            List<Object[]> results = providerDao.getDistinctProviders();

            // Then - each result is [providerNo, providerType]
            assertThat(results).isNotEmpty();
            // Verify at least one entry has the expected structure
            assertThat(results.get(0)).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should include all providers in distinct query")
        void shouldIncludeAllProviders_inDistinctQuery() {
            // When
            List<Object[]> results = providerDao.getDistinctProviders();

            // Then - extract provider numbers from the Object[]
            assertThat(results)
                .extracting(row -> (String) row[0])
                .contains("T001", "T002", "T003", "T004");
        }
    }

    /** Tests for records updated since a given date. */
    @Nested
    @DisplayName("getRecordsAddedAndUpdatedSinceTime")
    class RecordsUpdatedSinceTime {

        @Test
        @Tag("query")
        @DisplayName("should return provider numbers updated after a date")
        void shouldReturnProviderNumbers_updatedAfterDate() {
            // Given - set lastUpdateDate on one provider to a known time
            Provider t001 = providerDao.getProvider("T001");
            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.JANUARY, 15);
            t001.setLastUpdateDate(cal.getTime());
            hibernateTemplate.update(t001);
            hibernateTemplate.flush();

            // When - search for records updated after Jan 1, 2026
            Calendar searchDate = Calendar.getInstance();
            searchDate.set(2026, Calendar.JANUARY, 1);
            List<String> results = providerDao.getRecordsAddedAndUpdatedSinceTime(searchDate.getTime());

            // Then
            assertThat(results).contains("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no records updated after date")
        void shouldReturnEmpty_whenNoRecordsUpdatedAfterDate() {
            // When - search with a future date
            Calendar future = Calendar.getInstance();
            future.set(2099, Calendar.DECEMBER, 31);
            List<String> results = providerDao.getRecordsAddedAndUpdatedSinceTime(future.getTime());

            // Then
            assertThat(results).isEmpty();
        }
    }

    /** Tests for getProvidersByIds and getProviderNamesByIdsAsMap. */
    @Nested
    @DisplayName("ID-based batch lookups")
    class IdBasedBatchLookups {

        @Test
        @Tag("query")
        @DisplayName("should return providers for given list of IDs")
        void shouldReturnProviders_forGivenIdList() {
            // When
            List<Provider> results = providerDao.getProvidersByIds(Arrays.asList("T001", "T003"));

            // Then
            assertThat(results)
                .hasSize(2)
                .extracting(Provider::getProviderNo)
                .containsExactlyInAnyOrder("T001", "T003");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no IDs match")
        void shouldReturnEmpty_whenNoIdsMatch() {
            // When
            List<Provider> results = providerDao.getProvidersByIds(Arrays.asList("ZZZ", "YYY"));

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return provider name map keyed by provider number")
        void shouldReturnProviderNameMap_keyedByProviderNo() {
            // When
            Map<String, String> nameMap = providerDao.getProviderNamesByIdsAsMap(
                Arrays.asList("T001", "T002", "T003"));

            // Then
            assertThat(nameMap).hasSize(3);
            assertThat(nameMap.get("T001")).isEqualTo("John Smith");
            assertThat(nameMap.get("T002")).isEqualTo("John Doe");
            assertThat(nameMap.get("T003")).isEqualTo("Jane Smith");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty map when no IDs match")
        void shouldReturnEmptyMap_whenNoIdsMatch() {
            // When
            Map<String, String> nameMap = providerDao.getProviderNamesByIdsAsMap(
                Arrays.asList("ZZZ"));

            // Then
            assertThat(nameMap).isEmpty();
        }
    }

    /** Tests for getProviderByPatientId (Demographic join). */
    @Nested
    @DisplayName("getProviderByPatientId (Demographic join)")
    class GetProviderByPatientId {

        @Test
        @Tag("query")
        @DisplayName("should find provider linked to patient demographic")
        void shouldFindProvider_linkedToPatientDemographic() {
            // Given - create a Demographic linked to provider T001
            Demographic demo = new Demographic();
            demo.setFirstName("Patient");
            demo.setLastName("Test");
            demo.setSex("M");
            demo.setProviderNo("T001");
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getProviderByPatientId(demo.getDemographicNo());

            // Then
            assertThat(results)
                .hasSize(1)
                .extracting(Provider::getProviderNo)
                .containsExactly("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when patient has no linked provider")
        void shouldReturnEmpty_whenPatientHasNoLinkedProvider() {
            // Given - create a Demographic with non-existent provider
            Demographic demo = new Demographic();
            demo.setFirstName("Orphan");
            demo.setLastName("Patient");
            demo.setSex("F");
            demo.setProviderNo("NONEXIST");
            hibernateTemplate.save(demo);
            hibernateTemplate.flush();

            // When
            List<Provider> results = providerDao.getProviderByPatientId(demo.getDemographicNo());

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when patient ID does not exist")
        void shouldReturnEmpty_whenPatientIdDoesNotExist() {
            // When
            List<Provider> results = providerDao.getProviderByPatientId(999999);

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for raw SQL methods that use native SQLQuery.
     *
     * <p>These methods bypass HQL and use direct SQL string concatenation,
     * making them high-risk for SQL injection and migration issues. The native
     * SQL references physical table names (provider, provider_facility, Facility,
     * appointment, providersite) that must exist in the test database.</p>
     */
    @Nested
    @DisplayName("Raw SQL query methods")
    class RawSqlQueryMethods {

        @Test
        @Tag("query")
        @DisplayName("should return facility IDs for a provider via native SQL")
        void shouldReturnFacilityIds_forProviderViaNativeSql() {
            // Given - create a Facility and a ProviderFacility link
            Facility facility = new Facility();
            facility.setName("SQL Test Facility");
            facility.setDisabled(false);
            entityManager.persist(facility);
            entityManager.flush();

            ProviderFacility pf = new ProviderFacility();
            ProviderFacilityPK pk = new ProviderFacilityPK();
            pk.setProviderNo("T001");
            pk.setFacilityId(facility.getId());
            pf.setId(pk);
            entityManager.persist(pf);
            entityManager.flush();

            // When
            List<Integer> facilityIds = providerDao.getFacilityIds("T001");

            // Then
            assertThat(facilityIds).contains(facility.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider has no facilities")
        void shouldReturnEmpty_whenProviderHasNoFacilities() {
            // When
            List<Integer> facilityIds = providerDao.getFacilityIds("T001");

            // Then
            assertThat(facilityIds).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should exclude disabled facilities from getFacilityIds")
        void shouldExcludeDisabledFacilities_fromGetFacilityIds() {
            // Given - create a disabled facility and link it
            Facility disabledFac = new Facility();
            disabledFac.setName("Disabled Facility");
            disabledFac.setDisabled(true);
            entityManager.persist(disabledFac);
            entityManager.flush();

            ProviderFacility pf = new ProviderFacility();
            ProviderFacilityPK pk = new ProviderFacilityPK();
            pk.setProviderNo("T001");
            pk.setFacilityId(disabledFac.getId());
            pf.setId(pk);
            entityManager.persist(pf);
            entityManager.flush();

            // When
            List<Integer> facilityIds = providerDao.getFacilityIds("T001");

            // Then - disabled facility should be excluded
            assertThat(facilityIds).doesNotContain(disabledFac.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return provider numbers for a facility via native SQL")
        void shouldReturnProviderNumbers_forFacilityViaNativeSql() {
            // Given - create a Facility and link two providers
            Facility facility = new Facility();
            facility.setName("Multi Provider Facility");
            facility.setDisabled(false);
            entityManager.persist(facility);
            entityManager.flush();

            ProviderFacility pf1 = new ProviderFacility();
            ProviderFacilityPK pk1 = new ProviderFacilityPK();
            pk1.setProviderNo("T001");
            pk1.setFacilityId(facility.getId());
            pf1.setId(pk1);
            entityManager.persist(pf1);

            ProviderFacility pf2 = new ProviderFacility();
            ProviderFacilityPK pk2 = new ProviderFacilityPK();
            pk2.setProviderNo("T002");
            pk2.setFacilityId(facility.getId());
            pf2.setId(pk2);
            entityManager.persist(pf2);
            entityManager.flush();

            // When
            List<String> providerIds = providerDao.getProviderIds(facility.getId());

            // Then
            assertThat(providerIds).contains("T001", "T002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when facility has no providers")
        void shouldReturnEmpty_whenFacilityHasNoProviders() {
            // When - using a non-existent facility ID
            List<String> providerIds = providerDao.getProviderIds(999999);

            // Then
            assertThat(providerIds).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return provider numbers with appointments on date via native SQL")
        void shouldReturnProviderNumbers_withAppointmentsOnDate() {
            // Given - create an appointment for today linked to an active provider
            Date today = new Date();

            Appointment appt = new Appointment();
            appt.setProviderNo("T001");
            appt.setAppointmentDate(today);
            appt.setDemographicNo(0);
            appt.setStartTime(today);
            appt.setEndTime(today);
            appt.setStatus("t");
            appt.setName("Test Patient");
            appt.setType("");
            appt.setNotes("");
            appt.setReason("");
            appt.setLocation("");
            appt.setResources("");
            appt.setCreator("test");
            appt.setLastUpdateUser("test");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<String> results = providerDao.getProviderNosWithAppointmentsOnDate(today);

            // Then - T001 is active and has an appointment today
            assertThat(results).contains("T001");
        }

        @Test
        @Tag("query")
        @DisplayName("should not return inactive providers with appointments on date")
        void shouldNotReturnInactiveProviders_withAppointmentsOnDate() {
            // Given - create appointment for inactive provider T004
            Date today = new Date();

            Appointment appt = new Appointment();
            appt.setProviderNo("T004");
            appt.setAppointmentDate(today);
            appt.setDemographicNo(0);
            appt.setStartTime(today);
            appt.setEndTime(today);
            appt.setStatus("t");
            appt.setName("Test Patient");
            appt.setType("");
            appt.setNotes("");
            appt.setReason("");
            appt.setLocation("");
            appt.setResources("");
            appt.setCreator("test");
            appt.setLastUpdateUser("test");
            entityManager.persist(appt);
            entityManager.flush();

            // When
            List<String> results = providerDao.getProviderNosWithAppointmentsOnDate(today);

            // Then - T004 is inactive, should be excluded even with an appointment
            assertThat(results).doesNotContain("T004");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no appointments exist on date")
        void shouldReturnEmpty_whenNoAppointmentsOnDate() {
            // Given - a date far in the future with no appointments
            Calendar cal = Calendar.getInstance();
            cal.set(2099, Calendar.DECEMBER, 31);

            // When
            List<String> results = providerDao.getProviderNosWithAppointmentsOnDate(cal.getTime());

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for getActiveTeamsViaSites (native SQL with providersite table).
     *
     * <p>This method uses a native SQL query referencing the providersite table,
     * which is not mapped as a Hibernate entity and does not exist in the H2 test
     * database. We verify the method fails gracefully rather than silently returning
     * incorrect results.</p>
     */
    @Nested
    @DisplayName("getActiveTeamsViaSites (native SQL - providersite)")
    class GetActiveTeamsViaSites {

        @Test
        @Tag("query")
        @DisplayName("should throw exception when providersite table does not exist")
        void shouldThrowException_whenProviderSiteTableDoesNotExist() {
            // When/Then - providersite table is not in the H2 test schema
            assertThatThrownBy(() -> providerDao.getActiveTeamsViaSites("T001"))
                .isInstanceOf(Exception.class);
        }
    }

    /** Tests for getActiveProviders() no-arg variant edge cases. */
    @Nested
    @DisplayName("getActiveProviders no-arg variant edge cases")
    class GetActiveProvidersNoArgEdgeCases {

        @Test
        @Tag("filter")
        @DisplayName("should exclude providers with negative provider numbers")
        void shouldExcludeProviders_withNegativeProviderNumbers() {
            // Given - provider with negative number (system provider)
            Provider sysProvider = persistProvider("-1", "Sys", "Prov", "1", "doctor");
            hibernateTemplate.flush();

            // When - getActiveProviders() no-arg excludes provider_no LIKE '-%'
            List<Provider> results = providerDao.getActiveProviders();

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .doesNotContain("-1");
        }
    }

    /** Tests for getActiveProvidersByRole. */
    @Nested
    @DisplayName("getActiveProvidersByRole")
    class GetActiveProvidersByRole {

        @Test
        @Tag("query")
        @DisplayName("should find active providers with matching security role")
        void shouldFindActiveProviders_withMatchingSecurityRole() {
            // Given - create SecUserRole entries linking providers to roles
            // SecUserRole is mapped via Secuserrole.hbm.xml with auto-increment id
            org.hibernate.Session session = hibernateTemplate.getSessionFactory().getCurrentSession();
            session.createSQLQuery(
                "INSERT INTO secUserRole (provider_no, role_name, orgcd) VALUES ('T001', 'doctor', 'D')")
                .executeUpdate();
            session.createSQLQuery(
                "INSERT INTO secUserRole (provider_no, role_name, orgcd) VALUES ('T002', 'admin', 'A')")
                .executeUpdate();
            session.flush();

            // When
            List<Provider> results = providerDao.getActiveProvidersByRole("doctor");

            // Then
            assertThat(results)
                .extracting(Provider::getProviderNo)
                .contains("T001")
                .doesNotContain("T002"); // T002 has role "admin", not "doctor"
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when no active providers have the role")
        void shouldReturnEmpty_whenNoActiveProvidersHaveRole() {
            // When
            List<Provider> results = providerDao.getActiveProvidersByRole("nonexistent_role");

            // Then
            assertThat(results).isEmpty();
        }
    }
}
