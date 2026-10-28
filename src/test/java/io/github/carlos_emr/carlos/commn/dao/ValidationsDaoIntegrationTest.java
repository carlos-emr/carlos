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
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

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

    private enum RuleField { REGEX, MINIMUM, MAXIMUM, MIN_LENGTH, MAX_LENGTH, NUMERIC, DATE }

    @Nested
    @DisplayName("Exact definition reuse")
    class DefinitionReuse {

        @Test
        @DisplayName("should create and reuse unbounded Neutrophils rule instead of an existing bounded rule")
        void shouldNotReuseUpperBound_whenDefinitionOmitsMaximum() {
            Validations bounded = new Validations();
            bounded.setName("Existing numeric 0 to 10");
            bounded.setMinValue(0.0);
            bounded.setMaxValue(10.0);
            bounded.setNumeric(true);
            dao.persist(bounded);

            // hepCFlowsheet.xml declares Neutrophils with numeric=1, minimum=0, no maximum.
            EctValidationsBean neutrophils = new EctValidationsBean();
            neutrophils.setName("Neutrophils");
            neutrophils.setIsNumeric("1");
            neutrophils.setMinValue("0");

            try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
                spring.when(() -> SpringUtils.getBean(ValidationsDao.class)).thenReturn(dao);
                EctValidationsBeanHandler handler = new EctValidationsBeanHandler();
                assertThat(handler.findValidation(neutrophils)).isEqualTo(-1);
                int id = handler.addValidation(neutrophils);
                Validations created = dao.find(id);
                assertThat(id).isNotEqualTo(bounded.getId());
                assertThat(created.getMinValue()).isEqualTo(0.0);
                assertThat(created.getMaxValue()).isNull();
                assertThat(created.isNumeric()).isTrue();
                assertThat(created.isDate()).isNull();
                assertThat(created.getMaxLength()).isZero();
                neutrophils.setName("A different descriptive name");
                assertThat(handler.findValidation(neutrophils)).isEqualTo(id);

                EctValidation validation = new EctValidation();
                assertThat(validation.isInRange(bounded.getMaxValue(), bounded.getMinValue(), "15")).isFalse();
                // Measurement actions translate the absent maximum to zero before validating.
                assertThat(validation.isInRange(0.0, created.getMinValue(), "15")).isTrue();
                assertThat(validation.isNumeric(created.isNumeric(), "15")).isTrue();
            }
        }

        @ParameterizedTest
        @EnumSource(RuleField.class)
        @DisplayName("should distinguish every absent rule field while preserving wildcard searches")
        void shouldMatchNullFieldsExactly_withoutChangingWildcardSearch(RuleField field) {
            Validations constrained = new Validations();
            constrained.setName("Constrained " + field);
            constrained.setMaxLength(null);
            switch (field) {
                case REGEX -> constrained.setRegularExp("^[0-9]+$");
                case MINIMUM -> constrained.setMinValue(2.0);
                case MAXIMUM -> constrained.setMaxValue(10.0);
                case MIN_LENGTH -> constrained.setMinLength(1);
                case MAX_LENGTH -> constrained.setMaxLength(10);
                case NUMERIC -> constrained.setNumeric(false);
                case DATE -> constrained.setDate(false);
            }
            dao.persist(constrained);
            Validations absent = new Validations();
            absent.setName("Absent " + field);
            absent.setMaxLength(null);

            assertThat(dao.findByDefinition(absent)).isEmpty();
            // Public search API retains its documented null-as-wildcard behavior.
            assertThat(dao.findByAll(null, null, null, null, null, null, null))
                    .extracting(Validations::getId).contains(constrained.getId());
            dao.persist(absent);
            assertThat(dao.findByDefinition(absent)).extracting(Validations::getId)
                    .containsExactly(absent.getId());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"0", "1", "false", "true"})
        @DisplayName("should reuse created rules with consistent absent, empty and explicit defaults")
        void shouldReuseCreatedRule_withConsistentDefaults(String flag) {
            EctValidationsBean rule = new EctValidationsBean();
            rule.setName("Default normalization " + flag);
            rule.setRegularExp("");
            rule.setMinValue("");
            rule.setMaxValue("");
            rule.setMinLength("");
            rule.setMaxLength("");
            rule.setIsNumeric(flag);
            rule.setIsDate(flag);
            try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
                spring.when(() -> SpringUtils.getBean(ValidationsDao.class)).thenReturn(dao);
                EctValidationsBeanHandler handler = new EctValidationsBeanHandler();
                int id = handler.addValidation(rule);
                Validations created = dao.find(id);
                Boolean expected = flag == null ? null : "1".equals(flag) || Boolean.parseBoolean(flag);
                assertThat(created.isNumeric()).isEqualTo(expected);
                assertThat(created.isDate()).isEqualTo(expected);
                assertThat(created.getMaxLength()).isZero();
                assertThat(handler.findValidation(rule)).isEqualTo(id);
                assertThat(dao.findByName(rule.getName())).hasSize(1);
            }
        }

        @Test
        @DisplayName("should preserve numeric and date checks when reusing and importing exported Boolean flags")
        void shouldPreserveBooleanConstraints_whenReimportingExportedDefinition() {
            Validations falseFlags = new Validations();
            falseFlags.setName("Unconstrained Boolean flags");
            falseFlags.setNumeric(false);
            falseFlags.setDate(false);
            dao.persist(falseFlags);
            Validations original = new Validations();
            original.setName("Numeric and date checks");
            original.setNumeric(true);
            original.setDate(true);
            dao.persist(original);
            EctValidationsBean exported = new EctValidationsBean(original);
            assertThat(exported.getIsNumeric()).isEqualTo("true");
            assertThat(exported.getIsDate()).isEqualTo("true");

            try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
                spring.when(() -> SpringUtils.getBean(ValidationsDao.class)).thenReturn(dao);
                EctValidationsBeanHandler handler = new EctValidationsBeanHandler();
                assertThat(handler.findValidation(exported)).isEqualTo(original.getId());
                Validations imported = dao.find(handler.addValidation(exported));
                assertThat(imported.isNumeric()).isTrue();
                assertThat(imported.isDate()).isTrue();
                assertThat(new EctValidation().isNumeric(imported.isNumeric(), "not-a-number")).isFalse();
            }
        }
    }

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

            assertThat(entity.getId()).isPositive();
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

            assertThat(entity.getId()).isPositive();

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
        void shouldReturnAllValidations() throws Exception {
            List<Validations> all = dao.findAll();
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
            assertThat(all).extracting(Validations::getName)
                    .contains("NumericRange", "DateFormat");
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by regularExp parameter")
        void shouldFindValidations_byRegularExpParameter() throws Exception {
            List<Validations> results = dao.findByAll("^[0-9]+$", null, null, null, null, null, null);
            assertThat(results).isNotEmpty();
            assertThat(results).allSatisfy(v ->
                    assertThat(v.getRegularExp()).isEqualTo("^[0-9]+$"));
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by isNumeric parameter")
        void shouldFindValidations_byIsNumericParameter() throws Exception {
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
        void shouldFindValidations_byIsDateParameter() throws Exception {
            List<Validations> dateResults = dao.findByAll(null, null, null, null, null, null, true);
            assertThat(dateResults).isNotEmpty();
            assertThat(dateResults).allSatisfy(v ->
                    assertThat(v.isDate()).isTrue());
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by multiple parameters combined")
        void shouldFindValidations_byMultipleParametersCombined() throws Exception {
            List<Validations> results = dao.findByAll("^[0-9]+$", 0.0, 200.0, 1, 5, true, false);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("NumericRange");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching parameters")
        void shouldReturnEmptyList_whenNoMatchingParameters() throws Exception {
            List<Validations> results = dao.findByAll("nonexistent_regex", null, null, null, null, null, null);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find validations by name")
        void shouldFindValidations_byName() throws Exception {
            List<Validations> found = dao.findByName("NumericRange");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getName()).isEqualTo("NumericRange");
            assertThat(found.get(0).getId()).isEqualTo(numericValidation.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent name")
        void shouldReturnEmptyList_forNonExistentName() throws Exception {
            List<Validations> found = dao.findByName("NoSuchValidation");
            assertThat(found).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty results when no measurements match")
        void shouldReturnEmptyResults_whenNoMeasurementsMatch() throws Exception {
            List<Object[]> results = dao.findValidationsBy(999999, "BP", numericValidation.getId());
            assertThat(results).isEmpty();
        }
    }
}
