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
public class DefaultRoleAccessDAOIntegrationTest extends OpenOTestBase {

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
}
