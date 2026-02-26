/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for PMmodule ProviderDao multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
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

    @BeforeEach
    void setUp() {
        // Create test providers using HibernateTemplate
        persistProvider("T001", "John", "Smith", "1", "doctor");
        persistProvider("T002", "John", "Doe", "1", "doctor");
        persistProvider("T003", "Jane", "Smith", "1", "nurse");
        persistProvider("T004", "Bob", "Johnson", "0", "doctor");  // Inactive

        hibernateTemplate.flush();
    }

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
     * provider. PR #89 rewrites this to use named parameters.</p>
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
        void shouldThrowClassCastException_whenArrayPassedToPositionalParam() {
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
     * facilityId-based (Program subquery), and null/null (all active). PR #89 converts
     * the positional parameters from ?0 to ?1.</p>
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
    }
}
