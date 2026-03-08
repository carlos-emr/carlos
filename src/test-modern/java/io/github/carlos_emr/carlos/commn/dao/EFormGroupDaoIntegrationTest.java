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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.EFormGroup;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link EFormGroupDao}.
 *
 * <p>Tests cover persist, find, getByGroupName, getGroupNames,
 * deleteByNameAndFormId, and deleteByName operations.</p>
 *
 * @since 2026-03-07
 * @see EFormGroupDao
 */
@DisplayName("EFormGroup Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("eform")
@Transactional
public class EFormGroupDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EFormGroupDao eFormGroupDao;

    /**
     * Helper to create and persist an EFormGroup with specific group name and form ID.
     */
    private EFormGroup createEFormGroup(String groupName, int formId) throws Exception {
        EFormGroup entity = new EFormGroup();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setGroupName(groupName);
        entity.setFormId(formId);
        eFormGroupDao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist eformgroup with generated ID")
        void shouldPersistEFormGroup_whenValidDataProvided() throws Exception {
            EFormGroup entity = new EFormGroup();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            eFormGroupDao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find eformgroup by ID with correct fields")
        void shouldFindEFormGroup_whenValidIdProvided() throws Exception {
            EFormGroup saved = createEFormGroup("TestGroup", 42);
            hibernateTemplate.flush();

            EFormGroup found = eFormGroupDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getGroupName()).isEqualTo("TestGroup");
            assertThat(found.getFormId()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all records accurately")
        void shouldCountAllRecords() throws Exception {
            int initialCount = eFormGroupDao.getCountAll();

            createEFormGroup("CountGroup", 1);
            createEFormGroup("CountGroup", 2);
            hibernateTemplate.flush();

            int newCount = eFormGroupDao.getCountAll();

            assertThat(newCount).isEqualTo(initialCount + 2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return groups filtered by group name")
        void shouldReturnGroups_byGroupName() throws Exception {
            createEFormGroup("AlphaGroup", 10);
            createEFormGroup("AlphaGroup", 20);
            createEFormGroup("BetaGroup", 30);
            hibernateTemplate.flush();

            List<EFormGroup> result = eFormGroupDao.getByGroupName("AlphaGroup");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(g -> g.getGroupName().equals("AlphaGroup"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when group name does not exist")
        void shouldReturnEmptyList_whenGroupNameNotFound() throws Exception {
            List<EFormGroup> result = eFormGroupDao.getByGroupName("NonExistentGroup");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return distinct group names")
        void shouldReturnDistinctGroupNames() throws Exception {
            createEFormGroup("UniqueGroupA", 1);
            createEFormGroup("UniqueGroupA", 2);
            createEFormGroup("UniqueGroupB", 3);
            hibernateTemplate.flush();

            List<String> groupNames = eFormGroupDao.getGroupNames();

            assertThat(groupNames).contains("UniqueGroupA", "UniqueGroupB");
            // UniqueGroupA should appear only once (distinct)
            assertThat(groupNames.stream().filter("UniqueGroupA"::equals).count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @Tag("delete")
        @DisplayName("should delete by group name and form ID")
        void shouldDeleteByNameAndFormId() throws Exception {
            createEFormGroup("DeleteGroup", 100);
            createEFormGroup("DeleteGroup", 200);
            hibernateTemplate.flush();

            int deleted = eFormGroupDao.deleteByNameAndFormId("DeleteGroup", 100);

            assertThat(deleted).isEqualTo(1);

            List<EFormGroup> remaining = eFormGroupDao.getByGroupName("DeleteGroup");
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getFormId()).isEqualTo(200);
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete all entries by group name")
        void shouldDeleteAllEntries_byGroupName() throws Exception {
            createEFormGroup("DeleteAllGroup", 10);
            createEFormGroup("DeleteAllGroup", 20);
            createEFormGroup("KeepGroup", 30);
            hibernateTemplate.flush();

            int deleted = eFormGroupDao.deleteByName("DeleteAllGroup");

            assertThat(deleted).isEqualTo(2);

            List<EFormGroup> remaining = eFormGroupDao.getByGroupName("DeleteAllGroup");
            assertThat(remaining).isEmpty();

            List<EFormGroup> kept = eFormGroupDao.getByGroupName("KeepGroup");
            assertThat(kept).hasSize(1);
        }
    }
}
