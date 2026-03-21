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
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
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
 * Integration tests for {@link EncounterTemplateDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code EncounterTemplateDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see EncounterTemplateDao
 */
@DisplayName("EncounterTemplate Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("encounter")
@Transactional
public class EncounterTemplateDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EncounterTemplateDao encounterTemplateDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist encountertemplate with generated ID")
        void shouldPersistEncounterTemplate_whenValidDataProvided() throws Exception {
            EncounterTemplate entity = new EncounterTemplate();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            encounterTemplateDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find encountertemplate by ID")
        void shouldFindEncounterTemplate_whenValidIdProvided() throws Exception {
            EncounterTemplate saved = new EncounterTemplate();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            encounterTemplateDao.persist(saved);
            EncounterTemplate found = encounterTemplateDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all encountertemplate records")
        void shouldCountAllEncounterTemplates() throws Exception {
            EncounterTemplate entity = new EncounterTemplate();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            encounterTemplateDao.persist(entity);
            long count = encounterTemplateDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
