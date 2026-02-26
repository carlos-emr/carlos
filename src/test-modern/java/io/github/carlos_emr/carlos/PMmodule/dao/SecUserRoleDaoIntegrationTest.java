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

import java.util.Calendar;
import java.util.Date;
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
public class SecUserRoleDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("pmSecUserRoleDao")
    private SecUserRoleDao secUserRoleDao;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 3);

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

        /**
         * Verifies that {@code getUserRoles()} throws IllegalArgumentException
         * when providerNo is null.
         */
        @Test
        @Tag("read")
        @DisplayName("should throw exception when getUserRoles called with null provider")
        void shouldThrowException_whenGetUserRolesCalledWithNullProvider() {
            // When / Then
            assertThatThrownBy(() -> secUserRoleDao.getUserRoles(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * Verifies that {@code getUserRoles()} returns an empty list when
         * the provider number does not match any records.
         */
        @Test
        @Tag("read")
        @DisplayName("should return empty list when provider has no roles")
        void shouldReturnEmptyList_whenProviderHasNoRoles() {
            // When
            List<SecUserRole> results = secUserRoleDao.getUserRoles("NOPVD");

            // Then
            assertThat(results).isEmpty();
        }

        /**
         * Verifies that {@code getSecUserRolesByRoleName()} returns an empty list
         * when the role name does not match any records.
         */
        @Test
        @Tag("read")
        @DisplayName("should return empty list when role name has no matches")
        void shouldReturnEmptyList_whenRoleNameHasNoMatches() {
            // When
            List<SecUserRole> results = secUserRoleDao.getSecUserRolesByRoleName("nonexistent_role_xyz");

            // Then
            assertThat(results).isEmpty();
        }
    }

    /**
     * Tests for the {@link SecUserRoleDao#hasAdminRole(String)} method.
     *
     * <p>The {@code hasAdminRole()} implementation queries for a {@code SecUserRole}
     * with the given providerNo and roleName = 'admin'. Returns true if any
     * matching records exist, false otherwise.</p>
     */
    @Nested
    @DisplayName("hasAdminRole() operations")
    class HasAdminRoleOperations {

        /**
         * Verifies that {@code hasAdminRole()} returns true when the provider
         * has an admin role assigned.
         */
        @Test
        @Tag("read")
        @Tag("security")
        @DisplayName("should return true when provider has admin role")
        void shouldReturnTrue_whenProviderHasAdminRole() {
            // Given - create a provider with admin role
            SecUserRole adminRole = new SecUserRole("admin", uniquePrefix + "009");
            hibernateTemplate.save(adminRole);
            hibernateTemplate.flush();

            // When
            boolean hasAdmin = secUserRoleDao.hasAdminRole(uniquePrefix + "009");

            // Then
            assertThat(hasAdmin).isTrue();
        }

        /**
         * Verifies that {@code hasAdminRole()} returns false when the provider
         * has roles but none of them is 'admin'.
         */
        @Test
        @Tag("read")
        @Tag("security")
        @DisplayName("should return false when provider has no admin role")
        void shouldReturnFalse_whenProviderHasNoAdminRole() {
            // Given - uniquePrefix + "001" has 'doctor' and 'nurse' roles from setUp,
            // but no 'admin' role

            // When
            boolean hasAdmin = secUserRoleDao.hasAdminRole(uniquePrefix + "001");

            // Then
            assertThat(hasAdmin).isFalse();
        }

        /**
         * Verifies that {@code hasAdminRole()} returns false when the provider
         * does not exist at all.
         */
        @Test
        @Tag("read")
        @Tag("security")
        @DisplayName("should return false when provider does not exist")
        void shouldReturnFalse_whenProviderDoesNotExist() {
            // When
            boolean hasAdmin = secUserRoleDao.hasAdminRole("NOPVD");

            // Then
            assertThat(hasAdmin).isFalse();
        }

        /**
         * Verifies that {@code hasAdminRole()} throws IllegalArgumentException
         * when providerNo is null.
         */
        @Test
        @Tag("read")
        @Tag("security")
        @DisplayName("should throw exception when providerNo is null")
        void shouldThrowException_whenProviderNoIsNull() {
            // When / Then
            assertThatThrownBy(() -> secUserRoleDao.hasAdminRole(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Tests for the {@link SecUserRoleDao#save(SecUserRole)} method.
     *
     * <p>The {@code save()} implementation sets {@code lastUpdateDate} to the
     * current time before delegating to {@code HibernateTemplate.save()}.
     * This auto-sets the date regardless of any value previously set on the entity.</p>
     */
    @Nested
    @DisplayName("save() operations")
    class SaveOperations {

        /**
         * Verifies that {@code save()} persists a new SecUserRole entity
         * and automatically sets the lastUpdateDate.
         */
        @Test
        @Tag("create")
        @DisplayName("should persist new user role and set lastUpdateDate")
        void shouldPersistNewUserRole_andSetLastUpdateDate() {
            // Given
            SecUserRole role = new SecUserRole("tester", uniquePrefix + "010");

            // When
            secUserRoleDao.save(role);
            hibernateTemplate.flush();

            // Then
            assertThat(role.getLastUpdateDate()).isNotNull();
            List<SecUserRole> found = secUserRoleDao.findByRoleNameAndProviderNo("tester", uniquePrefix + "010");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getLastUpdateDate()).isNotNull();
        }

        /**
         * Verifies that {@code save()} overrides any pre-existing lastUpdateDate
         * with the current time.
         */
        @Test
        @Tag("create")
        @DisplayName("should override pre-existing lastUpdateDate with current time")
        void shouldOverrideLastUpdateDate_withCurrentTime() {
            // Given - set an old date
            SecUserRole role = new SecUserRole("overrider", uniquePrefix + "011");
            Calendar cal = Calendar.getInstance();
            cal.set(2020, Calendar.JANUARY, 1);
            Date oldDate = cal.getTime();
            role.setLastUpdateDate(oldDate);

            // When
            Date beforeSave = new Date();
            secUserRoleDao.save(role);
            hibernateTemplate.flush();

            // Then - lastUpdateDate should be after the old date
            assertThat(role.getLastUpdateDate()).isAfterOrEqualTo(beforeSave);
        }
    }

    /**
     * Tests for the {@link SecUserRoleDao#getRecordsAddedAndUpdatedSinceTime(Date)} method.
     *
     * <p>The {@code getRecordsAddedAndUpdatedSinceTime()} implementation queries for
     * provider numbers from SecUserRole records whose {@code lastUpdateDate} is after
     * the given date. This is used for incremental synchronization.</p>
     */
    @Nested
    @DisplayName("getRecordsAddedAndUpdatedSinceTime() operations")
    class GetRecordsSinceTimeOperations {

        /**
         * Verifies that {@code getRecordsAddedAndUpdatedSinceTime()} returns
         * provider numbers for records updated after the given date.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return provider numbers for records updated after date")
        void shouldReturnProviderNumbers_forRecordsUpdatedAfterDate() {
            // Given - records created in @BeforeEach have lastUpdateDate set to now
            // by the DAO's save() method. Use a date in the past to find them.
            Calendar cal = Calendar.getInstance();
            cal.set(2020, Calendar.JANUARY, 1);
            Date pastDate = cal.getTime();

            // When
            List<String> providerNos = secUserRoleDao.getRecordsAddedAndUpdatedSinceTime(pastDate);

            // Then - should include providers from @BeforeEach setup
            assertThat(providerNos).isNotEmpty();
        }

        /**
         * Verifies that {@code getRecordsAddedAndUpdatedSinceTime()} returns an
         * empty list when no records were updated after the given future date.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when no records updated after future date")
        void shouldReturnEmptyList_whenNoRecordsUpdatedAfterFutureDate() {
            // Given - use a date far in the future
            Calendar cal = Calendar.getInstance();
            cal.set(2099, Calendar.DECEMBER, 31);
            Date futureDate = cal.getTime();

            // When
            List<String> providerNos = secUserRoleDao.getRecordsAddedAndUpdatedSinceTime(futureDate);

            // Then
            assertThat(providerNos).isEmpty();
        }

        /**
         * Verifies that {@code getRecordsAddedAndUpdatedSinceTime()} only returns
         * providers whose roles were updated after the threshold date, not before.
         */
        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should only return providers with roles updated after threshold")
        void shouldOnlyReturnProviders_withRolesUpdatedAfterThreshold() {
            // Given - create a new role after recording a timestamp
            hibernateTemplate.flush();
            Date threshold = new Date();

            // Create a new role that will have lastUpdateDate after the threshold
            SecUserRole newRole = new SecUserRole("therapist", uniquePrefix + "020");
            secUserRoleDao.save(newRole);
            hibernateTemplate.flush();

            // When
            List<String> providerNos = secUserRoleDao.getRecordsAddedAndUpdatedSinceTime(threshold);

            // Then - should include the newly created provider
            assertThat(providerNos).contains(uniquePrefix + "020");
        }
    }

    /**
     * Tests for the {@link SecUserRoleDao#findByRoleNameAndProviderNo(String, String)}
     * additional edge case scenarios.
     */
    @Nested
    @DisplayName("findByRoleNameAndProviderNo() additional scenarios")
    class FindByRoleNameAndProviderNoAdditional {

        /**
         * Verifies that {@code findByRoleNameAndProviderNo()} returns results
         * with all expected fields populated.
         */
        @Test
        @Tag("query")
        @DisplayName("should return result with all fields populated")
        void shouldReturnResult_withAllFieldsPopulated() {
            // Given - create a role with orgCd
            SecUserRole roleWithOrg = new SecUserRole("orgRole", uniquePrefix + "030");
            roleWithOrg.setOrgCd("TESTORG");
            hibernateTemplate.save(roleWithOrg);
            hibernateTemplate.flush();

            // When
            List<SecUserRole> results = secUserRoleDao.findByRoleNameAndProviderNo(
                "orgRole", uniquePrefix + "030");

            // Then
            assertThat(results).hasSize(1);
            SecUserRole found = results.get(0);
            assertThat(found.getRoleName()).isEqualTo("orgRole");
            assertThat(found.getProviderNo()).isEqualTo(uniquePrefix + "030");
            assertThat(found.getOrgCd()).isEqualTo("TESTORG");
        }
    }
}
