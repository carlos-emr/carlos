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
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Create test providers
        createProvider("T001", "John", "Smith", "1", "doctor");
        createProvider("T002", "John", "Doe", "1", "doctor");
        createProvider("T003", "Jane", "Smith", "1", "nurse");
        createProvider("T004", "Bob", "Johnson", "0", "doctor");  // Inactive
        entityManager.flush();
    }

    private Provider createProvider(String providerNo, String firstName, String lastName,
                                   String status, String providerType) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setProviderType(providerType);
        provider.setSex("M");  // Required field
        entityManager.persist(provider);
        return provider;
    }

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

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get provider by provider number")
        void shouldGetProviderByProviderNo() {
            // When
            Provider found = providerDao.getProvider("T001");

            // Then
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("John");
            assertThat(found.getLastName()).isEqualTo("Smith");
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
        @Tag("read")
        @DisplayName("should check if provider exists")
        void shouldCheckProviderExists() {
            // When/Then
            assertThat(providerDao.providerExists("T001")).isTrue();
            assertThat(providerDao.providerExists("NONEXISTENT")).isFalse();
        }
    }
}
