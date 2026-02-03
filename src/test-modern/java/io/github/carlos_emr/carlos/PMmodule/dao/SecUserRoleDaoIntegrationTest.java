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
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for PMmodule SecUserRoleDao multi-parameter query methods.
 *
 * <p>These tests validate that HQL queries with multiple positional parameters
 * bind parameters correctly. Tests are designed to catch parameter index errors
 * during Hibernate migration.</p>
 *
 * @since 2026-02-03
 * @see SecUserRoleDao
 */
@DisplayName("PMmodule SecUserRoleDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Tag("security")
@Transactional
public class SecUserRoleDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("pmSecUserRoleDao")
    private SecUserRoleDao secUserRoleDao;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 8);

        // Create test user roles using HibernateTemplate (same as DAO uses)
        hibernateTemplate.save(new SecUserRole("doctor", uniquePrefix + "001"));
        hibernateTemplate.save(new SecUserRole("nurse", uniquePrefix + "001"));
        hibernateTemplate.save(new SecUserRole("doctor", uniquePrefix + "002"));

        SecUserRole inactiveRole = new SecUserRole("admin", uniquePrefix + "003");
        inactiveRole.setActive(false);
        hibernateTemplate.save(inactiveRole);

        hibernateTemplate.flush();
    }

    @Nested
    @DisplayName("findByRoleNameAndProviderNo (2 params)")
    class FindByRoleNameAndProviderNo {

        @Test
        @Tag("query")
        @DisplayName("should find role when both role name and provider match")
        void shouldFind_whenBothParamsMatch() {
            // When
            List<SecUserRole> results = secUserRoleDao.findByRoleNameAndProviderNo(
                "doctor", uniquePrefix + "001");

            // Then - Should find exactly one match
            assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(r -> {
                    assertThat(r.getRoleName()).isEqualTo("doctor");
                    assertThat(r.getProviderNo()).isEqualTo(uniquePrefix + "001");
                });
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when role name doesn't match")
        void shouldReturnEmpty_whenRoleDoesntMatch() {
            // When - Search with non-existent role
            List<SecUserRole> results = secUserRoleDao.findByRoleNameAndProviderNo(
                "superadmin", uniquePrefix + "001");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            // When - Search with non-existent provider
            List<SecUserRole> results = secUserRoleDao.findByRoleNameAndProviderNo(
                "doctor", "NONEXISTENT");

            // Then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get roles by provider number")
        void shouldGetRolesByProviderNo() {
            // When
            List<SecUserRole> results = secUserRoleDao.getUserRoles(uniquePrefix + "001");

            // Then - Provider 001 has two roles (doctor and nurse)
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getProviderNo().equals(uniquePrefix + "001"));
        }

        @Test
        @Tag("read")
        @DisplayName("should get roles by role name")
        void shouldGetRolesByRoleName() {
            // When
            List<SecUserRole> results = secUserRoleDao.getSecUserRolesByRoleName("doctor");

            // Then - Two providers have doctor role
            assertThat(results)
                .hasSizeGreaterThanOrEqualTo(2)
                .allMatch(r -> r.getRoleName().equals("doctor"));
        }
    }
}
