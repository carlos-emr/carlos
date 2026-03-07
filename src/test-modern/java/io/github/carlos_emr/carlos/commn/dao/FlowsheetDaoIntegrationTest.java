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
import io.github.carlos_emr.carlos.commn.model.Flowsheet;
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
 * Integration tests for {@link FlowsheetDao} covering create, findAll, and findByName.
 *
 * <p>Migrated from legacy {@code FlowsheetDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FlowsheetDao
 */
@DisplayName("Flowsheet Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("flowsheet")
@Transactional
public class FlowsheetDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FlowsheetDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist flowsheet with generated ID")
        void shouldPersistFlowsheet_whenValidDataProvided() {
            Flowsheet entity = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted flowsheets")
        void shouldReturnAllFlowsheets_whenMultipleExist() {
            Flowsheet fs1 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs1);
            dao.persist(fs1);

            Flowsheet fs2 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs2);
            dao.persist(fs2);

            Flowsheet fs3 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs3);
            dao.persist(fs3);

            Flowsheet fs4 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs4);
            dao.persist(fs4);

            List<Flowsheet> result = dao.findAll();

            assertThat(result).hasSize(4);
            assertThat(result).containsExactly(fs1, fs2, fs3, fs4);
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @Tag("read")
        @DisplayName("should return flowsheet matching the given name")
        void shouldReturnFlowsheet_whenNameMatches() {
            Flowsheet fs1 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs1);
            fs1.setName("alpha");
            dao.persist(fs1);

            Flowsheet fs2 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs2);
            fs2.setName("bravo");
            dao.persist(fs2);

            Flowsheet fs3 = new Flowsheet();
            EntityDataGenerator.generateTestDataForModelClass(fs3);
            fs3.setName("charlie");
            dao.persist(fs3);

            Flowsheet result = dao.findByName("bravo");

            assertThat(result).isEqualTo(fs2);
        }
    }
}
