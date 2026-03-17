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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.ConfigImmunization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConfigImmunizationDao} covering persist,
 * findAll (non-archived, ordered by name), and findByArchived.
 *
 * <p>Migrated from legacy {@code ConfigImmunizationDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see ConfigImmunizationDao
 */
@DisplayName("ConfigImmunizationDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("prevention")
@Transactional
public class ConfigImmunizationDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConfigImmunizationDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist config immunization with generated ID")
        void shouldPersistConfigImmunization_whenValidDataProvided() throws Exception {
            ConfigImmunization entity = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findAll (non-archived, ordered by name)")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return only non-archived immunizations ordered by name")
        void shouldReturnNonArchivedImmunizations_orderedByName() throws Exception {
            ConfigImmunization ci1 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci1);
            ci1.setArchived(0);
            ci1.setName("delta");
            dao.persist(ci1);

            ConfigImmunization ci2 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci2);
            ci2.setArchived(0);
            ci2.setName("alpha");
            dao.persist(ci2);

            ConfigImmunization ci3 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci3);
            ci3.setArchived(1);
            ci3.setName("bravo");
            dao.persist(ci3);

            ConfigImmunization ci4 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci4);
            ci4.setArchived(0);
            ci4.setName("charlie");
            dao.persist(ci4);

            List<ConfigImmunization> result = dao.findAll();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(ci2, ci4, ci1);
            assertThat(result).doesNotContain(ci3);
        }
    }

    @Nested
    @DisplayName("findByArchived")
    class FindByArchived {

        @Test
        @Tag("filter")
        @DisplayName("should return non-archived immunizations ordered by name when archived=0")
        void shouldReturnNonArchivedImmunizations_orderedByNameWhenArchivedIsZero() throws Exception {
            ConfigImmunization ci1 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci1);
            ci1.setArchived(0);
            ci1.setName("delta");
            dao.persist(ci1);

            ConfigImmunization ci2 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci2);
            ci2.setArchived(0);
            ci2.setName("alpha");
            dao.persist(ci2);

            ConfigImmunization ci3 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci3);
            ci3.setArchived(1);
            ci3.setName("bravo");
            dao.persist(ci3);

            ConfigImmunization ci4 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci4);
            ci4.setArchived(0);
            ci4.setName("charlie");
            dao.persist(ci4);

            List<ConfigImmunization> result = dao.findByArchived(0, true);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(ci2, ci4, ci1);
        }

        @Test
        @Tag("filter")
        @DisplayName("should return only archived immunizations when archived=1")
        void shouldReturnArchivedImmunizations_whenArchivedIsOne() throws Exception {
            ConfigImmunization ci1 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci1);
            ci1.setArchived(0);
            ci1.setName("alpha");
            dao.persist(ci1);

            ConfigImmunization ci2 = new ConfigImmunization();
            EntityDataGenerator.generateTestDataForModelClass(ci2);
            ci2.setArchived(1);
            ci2.setName("bravo");
            dao.persist(ci2);

            List<ConfigImmunization> result = dao.findByArchived(1, true);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(ci2);
        }
    }
}
