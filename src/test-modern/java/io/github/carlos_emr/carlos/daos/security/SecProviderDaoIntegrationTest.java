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
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.model.security.SecProvider;
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
 * Integration tests for SecProviderDao multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see SecProviderDao
 */
@DisplayName("SecProviderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecProviderDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private SecProviderDao secProviderDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 4);

        // Create test providers with unique IDs (providerNo must fit VARCHAR(6): 4-char prefix + 2-char suffix)
        createProvider(uniquePrefix + "01", "John", "Smith", "1");  // Active
        createProvider(uniquePrefix + "02", "Jane", "Doe", "1");    // Active
        createProvider(uniquePrefix + "03", "Bob", "Johnson", "0"); // Inactive
        hibernateTemplate.flush();
    }

    private SecProvider createProvider(String providerNo, String firstName, String lastName, String status) {
        SecProvider provider = new SecProvider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setSex("M");
        provider.setProviderType("doctor");
        provider.setSpecialty("");  // Required NOT NULL field from Provider.hbm.xml
        secProviderDao.save(provider);
        return provider;
    }

    @Nested
    @DisplayName("findById (2 params: id, status)")
    class FindByIdAndStatus {

        @Test
        @Tag("query")
        @DisplayName("should find provider when both id and status match")
        void shouldFind_whenBothIdAndStatusMatch() {
            // When
            SecProvider result = secProviderDao.findById(uniquePrefix + "01", "1");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(uniquePrefix + "01");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getStatus()).isEqualTo("1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when id doesn't match")
        void shouldReturnNull_whenIdDoesntMatch() {
            // When
            SecProvider result = secProviderDao.findById("999999", "1");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when status doesn't match")
        void shouldReturnNull_whenStatusDoesntMatch() {
            // When - Provider 01 is active (status=1), search for inactive (status=0)
            SecProvider result = secProviderDao.findById(uniquePrefix + "01", "0");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find inactive provider with inactive status")
        void shouldFindInactive_whenSearchingWithInactiveStatus() {
            // When - Provider 03 is inactive
            SecProvider result = secProviderDao.findById(uniquePrefix + "03", "0");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(uniquePrefix + "03");
            assertThat(result.getFirstName()).isEqualTo("Bob");
            assertThat(result.getStatus()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get provider by id only")
        void shouldGetById() {
            // When
            SecProvider result = secProviderDao.findById(uniquePrefix + "01");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("John");
        }

        @Test
        @Tag("read")
        @DisplayName("should find providers by last name via findByProperty path")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byLastName() {
            List<SecProvider> results = secProviderDao.findByLastName("Smith");

            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01")
                .doesNotContain(uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("read")
        @DisplayName("should return all providers via findAll")
        @SuppressWarnings("unchecked")
        void shouldReturnAllProviders_viaFindAll() {
            List<SecProvider> results = secProviderDao.findAll();

            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("read")
        @DisplayName("should find providers by status")
        void shouldFindByStatus() {
            // When
            List results = secProviderDao.findByStatus("1");

            // Then - Should include our active test providers
            assertThat(results)
                .isNotEmpty()
                .anyMatch(p -> ((SecProvider) p).getProviderNo().equals(uniquePrefix + "01"));
        }
    }
}
