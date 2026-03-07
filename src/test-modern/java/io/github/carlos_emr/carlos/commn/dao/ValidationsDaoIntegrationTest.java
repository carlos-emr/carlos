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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        @Test
        @Tag("create")
        @DisplayName("should persist and retrieve validation with all fields intact")
        void shouldPersistAndRetrieve_withAllFieldsIntact() throws Exception {
            Validations entity = new Validations();
            entity.setName("TestValidation");
            entity.setRegularExp("^[0-9]+$");
            entity.setMinValue(1.0);
            entity.setMaxValue(100.0);
            entity.setMinLength(1);
            entity.setMaxLength(10);
            entity.setNumeric(true);
            entity.setDate(false);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();

            List<Validations> found = dao.findByName("TestValidation");
            assertThat(found).hasSize(1);
            Validations retrieved = found.get(0);
            assertThat(retrieved.getName()).isEqualTo("TestValidation");
            assertThat(retrieved.getRegularExp()).isEqualTo("^[0-9]+$");
            assertThat(retrieved.getMinValue()).isEqualTo(1.0);
            assertThat(retrieved.getMaxValue()).isEqualTo(100.0);
            assertThat(retrieved.getMinLength()).isEqualTo(1);
            assertThat(retrieved.getMaxLength()).isEqualTo(10);
            assertThat(retrieved.isNumeric()).isTrue();
            assertThat(retrieved.isDate()).isFalse();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        private Validations numericValidation;
        private Validations dateValidation;

        @BeforeEach
        void setUpTestData() {
            numericValidation = new Validations();
            numericValidation.setName("NumericRange");
            numericValidation.setRegularExp("^[0-9]+$");
            numericValidation.setMinValue(0.0);
            numericValidation.setMaxValue(200.0);
            numericValidation.setMinLength(1);
            numericValidation.setMaxLength(5);
            numericValidation.setNumeric(true);
            numericValidation.setDate(false);
            dao.persist(numericValidation);

            dateValidation = new Validations();
            dateValidation.setName("DateFormat");
            dateValidation.setRegularExp("^\\d{4}-\\d{2}-\\d{2}$");
            dateValidation.setMinValue(null);
            dateValidation.setMaxValue(null);
            dateValidation.setMinLength(10);
            dateValidation.setMaxLength(10);
            dateValidation.setNumeric(false);
            dateValidation.setDate(true);
            dao.persist(dateValidation);
        }

        @Test
        @Tag("query")
        @DisplayName("should return all validations")
        void shouldReturnAllValidations() {
            List<Validations> all = dao.findAll();
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
            assertThat(all).extracting(Validations::getName)
                    .contains("NumericRange", "DateFormat");
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by regularExp parameter")
        void shouldFindValidations_byRegularExpParameter() {
            List<Validations> results = dao.findByAll("^[0-9]+$", null, null, null, null, null, null);
            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(v ->
                    assertThat(v.getRegularExp()).isEqualTo("^[0-9]+$"));
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by isNumeric parameter")
        void shouldFindValidations_byIsNumericParameter() {
            List<Validations> numericResults = dao.findByAll(null, null, null, null, null, true, null);
            assertThat(numericResults).isNotEmpty();
            assertThat(numericResults).allSatisfy(v ->
                    assertThat(v.isNumeric()).isTrue());

            List<Validations> nonNumericResults = dao.findByAll(null, null, null, null, null, false, null);
            assertThat(nonNumericResults).isNotEmpty();
            assertThat(nonNumericResults).allSatisfy(v ->
                    assertThat(v.isNumeric()).isFalse());
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by isDate parameter")
        void shouldFindValidations_byIsDateParameter() {
            List<Validations> dateResults = dao.findByAll(null, null, null, null, null, null, true);
            assertThat(dateResults).isNotEmpty();
            assertThat(dateResults).allSatisfy(v ->
                    assertThat(v.isDate()).isTrue());
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by multiple parameters combined")
        void shouldFindValidations_byMultipleParametersCombined() {
            List<Validations> results = dao.findByAll("^[0-9]+$", 0.0, 200.0, 1, 5, true, false);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("NumericRange");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching parameters")
        void shouldReturnEmptyList_whenNoMatchingParameters() {
            List<Validations> results = dao.findByAll("nonexistent_regex", null, null, null, null, null, null);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by name")
        void shouldFindValidations_byName() {
            List<Validations> found = dao.findByName("NumericRange");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getName()).isEqualTo("NumericRange");
            assertThat(found.get(0).getId()).isEqualTo(numericValidation.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent name")
        void shouldReturnEmptyList_forNonExistentName() {
            List<Validations> found = dao.findByName("NoSuchValidation");
            assertThat(found).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty results when no measurements match")
        void shouldReturnEmptyResults_whenNoMeasurementsMatch() {
            List<Object[]> results = dao.findValidationsBy(999999, "BP", numericValidation.getId());
            assertThat(results).isEmpty();
        }
    }
}
