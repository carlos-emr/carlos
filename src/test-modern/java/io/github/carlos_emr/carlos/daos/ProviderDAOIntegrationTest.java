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
package io.github.carlos_emr.carlos.daos;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
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
 * Integration tests for daos/ProviderDAO multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * <p>Note: This tests the daos/ProviderDAO, not PMmodule/ProviderDao.
 * These are different DAO classes with different methods.</p>
 *
 * @since 2026-02-03
 * @see ProviderDAO
 */
@DisplayName("daos/ProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ProviderDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderDAO providerDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 4);

        // Create test providers (providerNo must fit VARCHAR(6): 4-char prefix + 2-char suffix)
        createProvider(uniquePrefix + "01", "John", "Smith", "1", "doctor");
        createProvider(uniquePrefix + "02", "Jane", "Smith", "1", "doctor");
        createProvider(uniquePrefix + "03", "John", "Doe", "1", "nurse");
        createProvider(uniquePrefix + "04", "Bob", "Johnson", "0", "doctor");  // Inactive
        hibernateTemplate.flush();
    }

    private Provider createProvider(String providerNo, String firstName, String lastName,
                                    String status, String providerType) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setProviderType(providerType);
        provider.setSex("M");
        provider.setSpecialty("");  // Required not-null field
        hibernateTemplate.save(provider);
        return provider;
    }

    @Nested
    @DisplayName("getProviderByName (2 params: lastName, firstName)")
    class GetProviderByName {

        @Test
        @Tag("query")
        @DisplayName("should find provider when both last and first name match")
        void shouldFind_whenBothNamesMatch() {
            // When
            Provider result = providerDAO.getProviderByName("Smith", "John");

            // Then - Should find the John Smith provider
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when last name doesn't match")
        void shouldReturnNull_whenLastNameDoesntMatch() {
            // When
            Provider result = providerDAO.getProviderByName("Nonexistent", "John");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when first name doesn't match")
        void shouldReturnNull_whenFirstNameDoesntMatch() {
            // When
            Provider result = providerDAO.getProviderByName("Smith", "Nonexistent");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get provider by provider number")
        void shouldGetByProviderNo() {
            // When
            Provider result = providerDAO.getProvider(uniquePrefix + "01");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getLastName()).isEqualTo("Smith");
        }

        @Test
        @Tag("read")
        @DisplayName("should get all providers")
        void shouldGetAll() {
            // When
            List<Provider> results = providerDAO.getProviders();

            // Then - Should include our test providers
            assertThat(results)
                .isNotEmpty()
                .anyMatch(p -> p.getProviderNo().equals(uniquePrefix + "01"));
        }
    }
}
