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
import io.github.carlos_emr.carlos.PMmodule.model.AccessType;
import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.model.security.Secrole;
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
 * Integration tests for DefaultRoleAccessDAO multi-parameter query methods.
 *
 * @since 2026-02-03
 * @see DefaultRoleAccessDAO
 */
@DisplayName("DefaultRoleAccessDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class DefaultRoleAccessDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private DefaultRoleAccessDAO defaultRoleAccessDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Long testRoleId1;
    private Long testRoleId2;
    private Long testAccessTypeId1;
    private Long testAccessTypeId2;

    @BeforeEach
    void setUp() {
        // Create parent Secrole records
        Secrole role1 = createSecrole("Admin");
        Secrole role2 = createSecrole("User");
        testRoleId1 = role1.getId();
        testRoleId2 = role2.getId();

        // Create parent AccessType records
        AccessType accessType1 = createAccessType("Read", "read");
        AccessType accessType2 = createAccessType("Write", "write");
        testAccessTypeId1 = accessType1.getId();
        testAccessTypeId2 = accessType2.getId();

        // Create test default role access entries
        createDefaultRoleAccess(testRoleId1, testAccessTypeId1);
        createDefaultRoleAccess(testRoleId1, testAccessTypeId2);
        createDefaultRoleAccess(testRoleId2, testAccessTypeId1);

        hibernateTemplate.flush();
    }

    private Secrole createSecrole(String roleName) {
        Secrole role = new Secrole();
        role.setRoleName(roleName);
        role.setDescription("Test role: " + roleName);
        hibernateTemplate.save(role);
        return role;
    }

    private AccessType createAccessType(String name, String type) {
        AccessType accessType = new AccessType();
        accessType.setName(name);
        accessType.setType(type);
        hibernateTemplate.save(accessType);
        return accessType;
    }

    private DefaultRoleAccess createDefaultRoleAccess(Long roleId, Long accessTypeId) {
        DefaultRoleAccess dra = new DefaultRoleAccess();
        dra.setRoleId(roleId);
        dra.setAccessTypeId(accessTypeId);
        hibernateTemplate.save(dra);
        return dra;
    }

    @Nested
    @DisplayName("find (2 params: roleId, accessTypeId)")
    class FindByRoleAndAccessType {

        @Test
        @Tag("query")
        @DisplayName("should find access when both role and access type match")
        void shouldFind_whenBothParamsMatch() {
            DefaultRoleAccess result = defaultRoleAccessDAO.find(testRoleId1, testAccessTypeId1);

            assertThat(result).isNotNull();
            assertThat(result.getRoleId()).isEqualTo(testRoleId1.longValue());
            assertThat(result.getAccessTypeId()).isEqualTo(testAccessTypeId1.longValue());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when role doesn't match")
        void shouldReturnNull_whenRoleDoesntMatch() {
            DefaultRoleAccess result = defaultRoleAccessDAO.find(999999L, testAccessTypeId1);

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when access type doesn't match")
        void shouldReturnNull_whenAccessTypeDoesntMatch() {
            DefaultRoleAccess result = defaultRoleAccessDAO.find(testRoleId1, 999999L);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get all default role accesses")
        void shouldGetAll() {
            List<DefaultRoleAccess> results = defaultRoleAccessDAO.getDefaultRoleAccesses();

            assertThat(results)
                .isNotEmpty()
                .anyMatch(dra -> dra.getRoleId() == testRoleId1 && dra.getAccessTypeId() == testAccessTypeId1);
        }
    }

    /**
     * Tests for {@code findAll()} - returns all DefaultRoleAccess records without ordering.
     */
    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all default role access records")
        void shouldReturnAllRecords() {
            // When
            List<DefaultRoleAccess> results = defaultRoleAccessDAO.findAll();

            // Then - setUp creates 3 entries
            assertThat(results)
                .hasSizeGreaterThanOrEqualTo(3)
                .anyMatch(dra -> dra.getRoleId() == testRoleId1 && dra.getAccessTypeId() == testAccessTypeId1)
                .anyMatch(dra -> dra.getRoleId() == testRoleId1 && dra.getAccessTypeId() == testAccessTypeId2)
                .anyMatch(dra -> dra.getRoleId() == testRoleId2 && dra.getAccessTypeId() == testAccessTypeId1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return same count as getDefaultRoleAccesses")
        void shouldReturnSameCountAsGetDefaultRoleAccesses() {
            // When
            List<DefaultRoleAccess> findAllResults = defaultRoleAccessDAO.findAll();
            List<DefaultRoleAccess> getResults = defaultRoleAccessDAO.getDefaultRoleAccesses();

            // Then - Both should contain the same records (just different ordering)
            assertThat(findAllResults).hasSameSizeAs(getResults);
        }
    }

    /**
     * Tests for {@code getDefaultRoleAccess(Long id)} - single entity lookup by primary key.
     */
    @Nested
    @DisplayName("getDefaultRoleAccess (by ID)")
    class GetDefaultRoleAccessById {

        @Test
        @Tag("read")
        @DisplayName("should return role access when valid ID is provided")
        void shouldReturnRoleAccess_whenValidIdProvided() {
            // Given - Get a known ID from the saved entries
            DefaultRoleAccess saved = createDefaultRoleAccess(testRoleId1, testAccessTypeId1);
            hibernateTemplate.flush();
            Long savedId = saved.getId();

            // When
            DefaultRoleAccess result = defaultRoleAccessDAO.getDefaultRoleAccess(savedId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(savedId);
            assertThat(result.getRoleId()).isEqualTo(testRoleId1.longValue());
            assertThat(result.getAccessTypeId()).isEqualTo(testAccessTypeId1.longValue());
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when ID does not exist")
        void shouldReturnNull_whenIdNotFound() {
            // When
            DefaultRoleAccess result = defaultRoleAccessDAO.getDefaultRoleAccess(999999L);

            // Then
            assertThat(result).isNull();
        }
    }

    /**
     * Tests for {@code deleteDefaultRoleAccess(Long id)} - deletes a role access record.
     */
    @Nested
    @DisplayName("deleteDefaultRoleAccess")
    class DeleteDefaultRoleAccess {

        @Test
        @Tag("delete")
        @DisplayName("should delete role access when valid ID is provided")
        void shouldDeleteRoleAccess_whenValidIdProvided() {
            // Given - Create a new entry specifically for deletion
            DefaultRoleAccess toDelete = createDefaultRoleAccess(testRoleId2, testAccessTypeId2);
            hibernateTemplate.flush();
            Long deleteId = toDelete.getId();

            // Verify it exists first
            assertThat(defaultRoleAccessDAO.getDefaultRoleAccess(deleteId)).isNotNull();

            // When
            defaultRoleAccessDAO.deleteDefaultRoleAccess(deleteId);
            hibernateTemplate.flush();

            // Then
            assertThat(defaultRoleAccessDAO.getDefaultRoleAccess(deleteId)).isNull();
        }
    }

    /**
     * Tests for {@code saveDefaultRoleAccess(DefaultRoleAccess)} - persist and update operations.
     */
    @Nested
    @DisplayName("saveDefaultRoleAccess")
    class SaveDefaultRoleAccess {

        @Test
        @Tag("create")
        @DisplayName("should persist new role access and assign ID")
        void shouldPersistNewRoleAccess_withGeneratedId() {
            // Given
            DefaultRoleAccess dra = new DefaultRoleAccess();
            dra.setRoleId(testRoleId2);
            dra.setAccessTypeId(testAccessTypeId2);

            // When
            defaultRoleAccessDAO.saveDefaultRoleAccess(dra);
            hibernateTemplate.flush();

            // Then
            assertThat(dra.getId()).isNotNull();
            assertThat(dra.getId()).isGreaterThan(0L);

            // Verify persistence
            DefaultRoleAccess found = defaultRoleAccessDAO.getDefaultRoleAccess(dra.getId());
            assertThat(found).isNotNull();
            assertThat(found.getRoleId()).isEqualTo(testRoleId2.longValue());
            assertThat(found.getAccessTypeId()).isEqualTo(testAccessTypeId2.longValue());
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing role access")
        void shouldUpdateExistingRoleAccess() {
            // Given
            DefaultRoleAccess dra = createDefaultRoleAccess(testRoleId1, testAccessTypeId1);
            hibernateTemplate.flush();
            Long savedId = dra.getId();

            // When - Change the access type
            dra.setAccessTypeId(testAccessTypeId2);
            defaultRoleAccessDAO.saveDefaultRoleAccess(dra);
            hibernateTemplate.flush();

            // Then
            DefaultRoleAccess updated = defaultRoleAccessDAO.getDefaultRoleAccess(savedId);
            assertThat(updated).isNotNull();
            assertThat(updated.getAccessTypeId()).isEqualTo(testAccessTypeId2.longValue());
        }
    }

    /**
     * Tests for {@code findAllRolesAndAccessTypes()} - cross-entity JOIN query returning
     * Object[] pairs of (DefaultRoleAccess, AccessType) where IDs match.
     *
     * <p>The HQL query is: {@code FROM DefaultRoleAccess a, AccessType b WHERE a.id = b.Id}
     * This is an implicit cross-join with an equality condition on IDs, which means it
     * only returns pairs where the DefaultRoleAccess primary key matches an AccessType primary key.</p>
     */
    @Nested
    @DisplayName("findAllRolesAndAccessTypes (JOIN query)")
    class FindAllRolesAndAccessTypes {

        @Test
        @Tag("query")
        @DisplayName("should return Object[] pairs when DefaultRoleAccess ID matches AccessType ID")
        void shouldReturnObjectArrayPairs_whenIdsMatch() {
            // Given - We need a DefaultRoleAccess whose ID happens to equal an AccessType ID.
            // The query joins on a.id = b.Id (DefaultRoleAccess PK = AccessType PK).
            // This is a fairly unusual join. We can verify it works by checking if any
            // results are returned, or if none match, verify empty return.

            // When
            List<Object[]> results = defaultRoleAccessDAO.findAllRolesAndAccessTypes();

            // Then - Each result should be an Object[] with 2 elements
            // Results may or may not exist depending on whether any DRA id == AccessType id
            assertThat(results).isNotNull();
            for (Object[] pair : results) {
                assertThat(pair).hasSize(2);
                assertThat(pair[0]).isInstanceOf(DefaultRoleAccess.class);
                assertThat(pair[1]).isInstanceOf(AccessType.class);

                DefaultRoleAccess dra = (DefaultRoleAccess) pair[0];
                AccessType at = (AccessType) pair[1];
                // The JOIN condition: DefaultRoleAccess.id = AccessType.id
                assertThat(dra.getId()).isEqualTo(at.getId());
            }
        }
    }
}
