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
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
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
 * Integration tests for {@link DiagnosticCodeDao}.
 *
 * <p>Migrated from legacy {@code DiagnosticCodeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DiagnosticCodeDao
 */
@DisplayName("DiagnosticCode Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class DiagnosticCodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DiagnosticCodeDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist diagnostic code with generated ID")
        void shouldPersistDiagnosticCode_whenValidDataProvided() throws Exception {
            DiagnosticCode entity = new DiagnosticCode();
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
        @DisplayName("should find diagnostic codes by diagnostic code value")
        void shouldFindDiagnosticCodes_byDiagnosticCode() throws Exception {
            DiagnosticCode entity = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDiagnosticCode("a");
            dao.persist(entity);

            List<DiagnosticCode> results = dao.findByDiagnosticCode(entity.getDiagnosticCode());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(entity.getId());
            assertThat(results.get(0).getDiagnosticCode()).isEqualTo("a");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when diagnostic code does not match")
        void shouldReturnEmptyList_whenDiagnosticCodeNotFound() {
            DiagnosticCode entity = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDiagnosticCode("existing");
            dao.persist(entity);

            List<DiagnosticCode> results = dao.findByDiagnosticCode("nonexistent");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find diagnostic codes by diagnostic code and region")
        void shouldFindDiagnosticCodes_byDiagnosticCodeAndRegion() throws Exception {
            DiagnosticCode entity = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDiagnosticCode("a");
            entity.setRegion("b");
            dao.persist(entity);

            List<DiagnosticCode> results = dao.findByDiagnosticCodeAndRegion(
                    entity.getDiagnosticCode(), entity.getRegion());
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(entity.getId());
            assertThat(results.get(0).getDiagnosticCode()).isEqualTo("a");
            assertThat(results.get(0).getRegion()).isEqualTo("b");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when region does not match")
        void shouldReturnEmptyList_whenRegionDoesNotMatch() {
            DiagnosticCode entity = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDiagnosticCode("x");
            entity.setRegion("matchRegion");
            dao.persist(entity);

            List<DiagnosticCode> results = dao.findByDiagnosticCodeAndRegion("x", "noMatchRegion");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for findByRegionAndType with no matching data")
        void shouldReturnEmptyList_whenNoRegionAndTypeMatch() {
            // This query joins with CtlDiagCode table; with no data in that table, expect empty
            List<DiagnosticCode> codes = dao.findByRegionAndType("REG", "TYPE");
            assertThat(codes).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for findDiagnosticsAndCtlDiagCodes with no matching data")
        void shouldReturnEmptyList_whenNoCtlDiagCodesExist() {
            // This query joins with CtlDiagCode table; with no data in that table, expect empty
            List<Object[]> results = dao.findDiagnosictsAndCtlDiagCodesByServiceType("TYPE");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find only codes matching specific diagnostic code among multiple")
        void shouldReturnOnlyMatchingCodes_whenMultipleCodesExist() {
            DiagnosticCode code1 = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(code1);
            code1.setDiagnosticCode("ABC");
            code1.setRegion("ON");
            dao.persist(code1);

            DiagnosticCode code2 = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(code2);
            code2.setDiagnosticCode("DEF");
            code2.setRegion("ON");
            dao.persist(code2);

            DiagnosticCode code3 = new DiagnosticCode();
            EntityDataGenerator.generateTestDataForModelClass(code3);
            code3.setDiagnosticCode("ABC");
            code3.setRegion("BC");
            dao.persist(code3);

            hibernateTemplate.flush();

            List<DiagnosticCode> byCode = dao.findByDiagnosticCode("ABC");
            assertThat(byCode).hasSize(2);
            assertThat(byCode).extracting(DiagnosticCode::getDiagnosticCode)
                    .containsOnly("ABC");

            List<DiagnosticCode> byCodeAndRegion = dao.findByDiagnosticCodeAndRegion("ABC", "ON");
            assertThat(byCodeAndRegion).hasSize(1);
            assertThat(byCodeAndRegion.get(0).getId()).isEqualTo(code1.getId());
        }
    }
}
