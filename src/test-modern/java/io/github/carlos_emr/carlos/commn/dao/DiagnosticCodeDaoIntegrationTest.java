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

            assertThat(dao.findByDiagnosticCode(entity.getDiagnosticCode())).hasSize(1);
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

            assertThat(dao.findByDiagnosticCodeAndRegion(entity.getDiagnosticCode(), entity.getRegion())).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find diagnostic codes by region and type")
        void shouldFindDiagnosticCodes_byRegionAndType() {
            List<DiagnosticCode> codes = dao.findByRegionAndType("REG", "TYPE");
            assertThat(codes).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find diagnostics and ctl diag codes by service type")
        void shouldFindDiagnosticsAndCtlDiagCodes_byServiceType() {
            assertThat(dao.findDiagnosictsAndCtlDiagCodesByServiceType("TYPE")).isNotNull();
        }
    }
}
