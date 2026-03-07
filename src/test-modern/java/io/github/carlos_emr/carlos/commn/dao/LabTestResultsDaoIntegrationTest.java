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
import io.github.carlos_emr.carlos.commn.model.LabTestResults;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LabTestResultsDao} covering
 * create, findByTitleAndLabInfoId, findByLabInfoId, findByAbnAndLabInfoId,
 * findUniqueTestNames, findByAbnAndPhysicianId, and findByLabPatientPhysicialInfoId.
 *
 * <p>Migrated from legacy {@code LabTestResultsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LabTestResultsDao
 */
@DisplayName("LabTestResultsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class LabTestResultsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LabTestResultsDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist lab test results with generated ID")
        void shouldPersistLabTestResults_whenValidDataProvided() {
            LabTestResults entity = new LabTestResults();
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
        @DisplayName("should return results for findByTitleAndLabInfoId")
        void shouldReturnResults_whenFindByTitleAndLabInfoId() {
            List<LabTestResults> results = dao.findByTitleAndLabInfoId(100);
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByLabInfoId")
        void shouldReturnResults_whenFindByLabInfoId() {
            List<LabTestResults> results = dao.findByLabInfoId(100);
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByAbnAndLabInfoId")
        void shouldReturnResults_whenFindByAbnAndLabInfoId() {
            List<LabTestResults> results = dao.findByAbnAndLabInfoId("A", 100);
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findUniqueTestNames")
        void shouldReturnResults_whenFindUniqueTestNames() {
            List<Object[]> results = dao.findUniqueTestNames(100, "CML");
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByAbnAndPhysicianId")
        void shouldReturnResults_whenFindByAbnAndPhysicianId() {
            List<LabTestResults> results = dao.findByAbnAndPhysicianId("ABN", 199);
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByLabPatientPhysicialInfoId")
        void shouldReturnResults_whenFindByLabPatientPhysicialInfoId() {
            List<LabTestResults> results = dao.findByLabPatientPhysicialInfoId(199);
            assertThat(results).isNotNull();
        }
    }
}
