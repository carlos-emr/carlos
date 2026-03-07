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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecObjPrivilegeDao} covering
 * findByRoleUserGroupAndObjectName, findByObjectNames, findByRoleUserGroup,
 * findByObjectName, countObjectsByName, and findByFormNamePrivilegeAndProviderNo.
 *
 * <p>Migrated from legacy {@code SecObjPrivilegeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see SecObjPrivilegeDao
 */
@DisplayName("SecObjPrivilegeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecObjPrivilegeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecObjPrivilegeDao dao;

    private SecObjPrivilege createPrivilege(String objectName, String roleUserGroup) {
        SecObjPrivilege sop = new SecObjPrivilege();
        EntityDataGenerator.generateTestDataForModelClass(sop);
        SecObjPrivilegePrimaryKey pk = new SecObjPrivilegePrimaryKey();
        pk.setObjectName(objectName);
        pk.setRoleUserGroup(roleUserGroup);
        sop.setId(pk);
        dao.persist(sop);
        return sop;
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find privileges by role user group and object name")
        void shouldFindPrivileges_byRoleUserGroupAndObjectName() {
            SecObjPrivilege sop1 = createPrivilege("alphaName1", "sigmaGroup1");
            createPrivilege("alphaName2", "sigmaGroup2");
            createPrivilege("alphaName1", "sigmaGroup2");
            createPrivilege("alphaName2", "sigmaGroup1");

            List<SecObjPrivilege> result = dao.findByRoleUserGroupAndObjectName("sigmaGroup1", "alphaName1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(sop1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find privileges by object names collection")
        void shouldFindPrivileges_byObjectNames() {
            SecObjPrivilege sop1 = createPrivilege("alphaName1", "sigmaGroup1");
            SecObjPrivilege sop2 = createPrivilege("alphaName2", "sigmaGroup1");
            createPrivilege("alphaName3", "sigmaGroup2");

            Collection<String> objectNames = new ArrayList<>(Arrays.asList("alphaName1", "alphaName2"));

            List<SecObjPrivilege> result = dao.findByObjectNames(objectNames);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder(sop1, sop2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find privileges by role user group")
        void shouldFindPrivileges_byRoleUserGroup() {
            SecObjPrivilege sop1 = createPrivilege("alphaName1", "sigmaGroup1");
            createPrivilege("alphaName2", "sigmaGroup2");
            createPrivilege("alphaName1", "sigmaGroup2");
            SecObjPrivilege sop4 = createPrivilege("alphaName2", "sigmaGroup1");

            List<SecObjPrivilege> result = dao.findByRoleUserGroup("sigmaGroup1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(sop1);
            assertThat(result.get(1)).isEqualTo(sop4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find privileges by object name")
        void shouldFindPrivileges_byObjectName() {
            SecObjPrivilege sop1 = createPrivilege("alphaName1", "sigmaGroup1");
            createPrivilege("alphaName2", "sigmaGroup1");
            SecObjPrivilege sop3 = createPrivilege("alphaName1", "sigmaGroup2");
            createPrivilege("alphaName2", "sigmaGroup2");

            List<SecObjPrivilege> result = dao.findByObjectName("alphaName1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(sop1);
            assertThat(result.get(1)).isEqualTo(sop3);
        }

        @Test
        @Tag("query")
        @DisplayName("should count objects by name without error")
        void shouldCountObjects_byName() {
            dao.countObjectsByName("OBJ NAME");
            // Legacy test only verified no exception was thrown
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByFormNamePrivilegeAndProviderNo")
        void shouldReturnResults_whenFindByFormNamePrivilegeAndProviderNo() {
            List<Object[]> result = dao.findByFormNamePrivilegeAndProviderNo("frm", "priv", "prov");
            assertThat(result).isNotNull();
        }
    }
}
