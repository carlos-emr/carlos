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
import io.github.carlos_emr.carlos.commn.model.Groups;
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
 * Integration tests for {@link GroupsDao} covering persist, find, and findByParentId.
 *
 * <p>Migrated from legacy {@code GroupsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see GroupsDao
 */
@DisplayName("Groups Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class GroupsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private GroupsDao dao;

    /**
     * Helper to create and persist a Groups entity with specific parent ID and description.
     */
    private Groups createGroup(int parentId, String description) throws Exception {
        Groups entity = new Groups();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setParentId(parentId);
        entity.setGroupDesc(description);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist groups entity with generated ID")
        void shouldPersistGroups_whenValidDataProvided() throws Exception {
            Groups entity = new Groups();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find group by ID with correct fields")
        void shouldFindGroup_whenValidIdProvided() throws Exception {
            Groups saved = createGroup(50, "TestDescription");
            hibernateTemplate.flush();

            Groups found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getParentId()).isEqualTo(50);
            assertThat(found.getGroupDesc()).isEqualTo("TestDescription");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find groups by parent ID")
        void shouldFindGroups_byParentId() throws Exception {
            createGroup(1000, "Child1");
            createGroup(1000, "Child2");
            createGroup(2000, "OtherChild");
            hibernateTemplate.flush();

            List<Groups> result = dao.findByParentId(1000);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(g -> g.getParentId() == 1000);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no groups match parent ID")
        void shouldReturnEmptyList_whenNoGroupsMatchParentId() throws Exception {
            List<Groups> result = dao.findByParentId(99999);

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should count all records accurately")
        void shouldCountAllRecords() throws Exception {
            int initialCount = dao.getCountAll();

            createGroup(3000, "New1");
            createGroup(3000, "New2");
            hibernateTemplate.flush();

            int newCount = dao.getCountAll();

            assertThat(newCount).isEqualTo(initialCount + 2);
        }
    }
}
