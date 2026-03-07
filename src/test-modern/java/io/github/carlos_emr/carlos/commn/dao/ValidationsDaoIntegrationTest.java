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
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ValidationsDao}.
 *
 * <p>Migrated from legacy {@code ValidationsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ValidationsDao
 */
@DisplayName("Validations Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class ValidationsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ValidationsDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist validations with generated ID")
        void shouldPersistValidations_whenValidDataProvided() throws Exception {
            Validations entity = new Validations();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find validations by all parameter combinations")
        void shouldFindValidations_byAllParameterCombinations() {
            assertThat(dao.findByAll(null, null, null, null, null, null, null)).isNotNull();
            assertThat(dao.findByAll("RE", null, null, null, null, null, null)).isNotNull();
            assertThat(dao.findByAll(null, 2.0, null, null, null, null, null)).isNotNull();
            assertThat(dao.findByAll(null, null, 1.0, null, null, null, null)).isNotNull();
            assertThat(dao.findByAll(null, null, null, 100, null, null, null)).isNotNull();
            assertThat(dao.findByAll(null, null, null, null, 200, null, null)).isNotNull();
            assertThat(dao.findByAll(null, null, null, null, null, true, null)).isNotNull();
            assertThat(dao.findByAll(null, null, null, null, null, null, false)).isNotNull();
            assertThat(dao.findByAll("BR", 1.0, 2.0, 199, 0, false, false)).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by demographic, type, and measurement ID")
        void shouldFindValidations_byDemographicTypeAndMeasurementId() {
            assertThat(dao.findValidationsBy(10, "type", 10)).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by name")
        void shouldFindValidations_byName() {
            assertThat(dao.findByName("NM")).isNotNull();
        }
    }
}
