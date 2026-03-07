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
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SecRoleDao} covering findAll, findByName, and findAllOrderByRole.
 *
 * <p>Migrated from legacy {@code SecRoleDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see SecRoleDao
 */
@DisplayName("SecRole Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecRoleDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecRoleDao dao;

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted sec roles")
        void shouldReturnAllSecRoles_whenMultipleExist() {
            SecRole secRole1 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole1);
            secRole1.setName("alpha");
            dao.persist(secRole1);

            SecRole secRole2 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole2);
            secRole2.setName("bravo");
            dao.persist(secRole2);

            SecRole secRole3 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole3);
            secRole3.setName("charlie");
            dao.persist(secRole3);

            List<SecRole> result = dao.findAll();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(secRole1, secRole2, secRole3);
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @Tag("read")
        @DisplayName("should return sec role matching the given name")
        void shouldReturnSecRole_whenNameMatches() {
            SecRole secRole1 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole1);
            secRole1.setName("alpha");
            dao.persist(secRole1);

            SecRole secRole2 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole2);
            secRole2.setName("bravo");
            dao.persist(secRole2);

            SecRole result = dao.findByName("alpha");

            assertThat(result).isEqualTo(secRole1);
        }
    }

    @Nested
    @DisplayName("findAllOrderByRole")
    class FindAllOrderByRole {

        @Test
        @Tag("read")
        @DisplayName("should return all sec roles ordered by role name")
        void shouldReturnSecRoles_orderedByRoleName() {
            SecRole secRole1 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole1);
            secRole1.setName("charlie");
            dao.persist(secRole1);

            SecRole secRole2 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole2);
            secRole2.setName("alpha");
            dao.persist(secRole2);

            SecRole secRole3 = new SecRole();
            EntityDataGenerator.generateTestDataForModelClass(secRole3);
            secRole3.setName("bravo");
            dao.persist(secRole3);

            List<SecRole> result = dao.findAllOrderByRole();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(secRole2, secRole3, secRole1);
        }
    }
}
