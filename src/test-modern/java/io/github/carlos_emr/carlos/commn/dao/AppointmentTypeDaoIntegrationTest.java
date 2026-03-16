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
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AppointmentTypeDao} covering persist,
 * list all, and find by name operations.
 *
 * <p>Migrated from legacy {@code AppointmentTypeDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see AppointmentTypeDao
 */
@DisplayName("AppointmentTypeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
public class AppointmentTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private AppointmentTypeDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist appointment type with generated ID")
        void shouldPersistAppointmentType_whenValidDataProvided() throws Exception {
            AppointmentType entity = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("listAll")
    class ListAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted appointment types")
        void shouldReturnAllAppointmentTypes_afterPersist() throws Exception {
            AppointmentType type1 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type1);
            type1.setName("A");

            AppointmentType type2 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type2);
            type2.setName("C");

            AppointmentType type3 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type3);
            type3.setName("B");

            dao.persist(type1);
            dao.persist(type2);
            dao.persist(type3);

            List<AppointmentType> result = dao.listAll();

            assertThat(result).hasSize(3);
            assertThat(result).contains(type1, type2, type3);
        }
    }

    @Nested
    @DisplayName("findByAppointmentTypeByName")
    class FindByName {

        @Test
        @Tag("query")
        @DisplayName("should find appointment type by name")
        void shouldReturnAppointmentType_whenNameMatches() throws Exception {
            AppointmentType type1 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type1);
            type1.setName("A");

            AppointmentType type2 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type2);
            type2.setName("C");

            AppointmentType type3 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type3);
            type3.setName("B");

            dao.persist(type1);
            dao.persist(type2);
            dao.persist(type3);

            AppointmentType result = dao.findByAppointmentTypeByName("A");

            assertThat(result).isEqualTo(type1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when name does not match")
        void shouldReturnNull_whenNameDoesNotMatch() throws Exception {
            AppointmentType type1 = new AppointmentType();
            EntityDataGenerator.generateTestDataForModelClass(type1);
            type1.setName("A");
            dao.persist(type1);

            AppointmentType result = dao.findByAppointmentTypeByName("NonExistent");

            assertThat(result).isNull();
        }
    }
}
