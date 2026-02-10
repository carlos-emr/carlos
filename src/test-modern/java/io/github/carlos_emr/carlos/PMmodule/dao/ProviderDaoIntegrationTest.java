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

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.commn.model.Provider;
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
public class ProviderDaoIntegrationTest extends OpenOTestBase {

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
        void shouldRetrieveAllProviders() {
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
        void shouldGetProvidersByType() {
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
        void shouldGetProviderNameLastFirst() {
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
            // When
            List<Provider> activeOnly = providerDao.getProviders(true);
            List<Provider> all = providerDao.getProviders(false);

            // Then
            assertThat(activeOnly.size()).isLessThanOrEqualTo(all.size());
            assertThat(activeOnly)
                .extracting(Provider::getProviderNo)
                .doesNotContain("T004");
        }
    }
}
