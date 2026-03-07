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

import io.github.carlos_emr.carlos.commn.model.LabTestResults;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
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
 * findByAbnAndPhysicianId, and findByLabPatientPhysicialInfoId.
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

    private LabTestResults createLabTestResult(int labInfoId, String title, String abn, String testName) {
        LabTestResults entity = new LabTestResults();
        entity.setLabPatientPhysicianInfoId(labInfoId);
        entity.setTitle(title);
        entity.setAbn(abn);
        entity.setTestName(testName);
        entity.setResult("10.5");
        entity.setUnits("mg/dL");
        entity.setMinimum("5.0");
        entity.setMaximum("15.0");
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist lab test results with generated ID")
        void shouldPersistLabTestResults_whenValidDataProvided() {
            LabTestResults entity = createLabTestResult(100, "CBC", "N", "WBC");

            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find lab test results by ID after persist")
        void shouldFindLabTestResults_whenValidIdProvided() {
            LabTestResults saved = createLabTestResult(100, "CBC", "N", "WBC");

            LabTestResults found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getLabPatientPhysicianInfoId()).isEqualTo(100);
            assertThat(found.getTitle()).isEqualTo("CBC");
        }
    }

    @Nested
    @DisplayName("findByTitleAndLabInfoId")
    class FindByTitleAndLabInfoId {

        @Test
        @Tag("query")
        @DisplayName("should return results with non-empty title for matching lab ID")
        void shouldReturnResults_whenTitleNotEmptyAndLabIdMatches() {
            LabTestResults withTitle = createLabTestResult(200, "Chemistry", "N", "Glucose");
            createLabTestResult(200, "", "N", "Empty");
            createLabTestResult(300, "Other", "N", "Other");

            List<LabTestResults> results = dao.findByTitleAndLabInfoId(200);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(withTitle.getId());
            assertThat(results.get(0).getTitle()).isEqualTo("Chemistry");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching lab ID exists")
        void shouldReturnEmptyList_whenNoMatchingLabId() {
            createLabTestResult(200, "CBC", "N", "WBC");

            List<LabTestResults> results = dao.findByTitleAndLabInfoId(99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLabInfoId")
    class FindByLabInfoId {

        @Test
        @Tag("query")
        @DisplayName("should return all results for matching lab ID")
        void shouldReturnAllResults_whenLabIdMatches() {
            createLabTestResult(400, "CBC", "N", "WBC");
            createLabTestResult(400, "CBC", "A", "RBC");
            createLabTestResult(500, "Other", "N", "Other");

            List<LabTestResults> results = dao.findByLabInfoId(400);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getLabPatientPhysicianInfoId() == 400);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching lab ID")
        void shouldReturnEmptyList_whenNoMatchingLabId() {
            List<LabTestResults> results = dao.findByLabInfoId(99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAbnAndLabInfoId")
    class FindByAbnAndLabInfoId {

        @Test
        @Tag("query")
        @DisplayName("should return results matching both abn and lab ID")
        void shouldReturnResults_whenAbnAndLabIdMatch() {
            LabTestResults matching = createLabTestResult(600, "CBC", "A", "WBC");
            createLabTestResult(600, "CBC", "N", "RBC");
            createLabTestResult(700, "Other", "A", "Other");

            List<LabTestResults> results = dao.findByAbnAndLabInfoId("A", 600);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when abn does not match")
        void shouldReturnEmptyList_whenAbnDoesNotMatch() {
            createLabTestResult(600, "CBC", "N", "WBC");

            List<LabTestResults> results = dao.findByAbnAndLabInfoId("X", 600);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByAbnAndPhysicianId")
    class FindByAbnAndPhysicianId {

        @Test
        @Tag("query")
        @DisplayName("should return results matching both abn and physician info ID")
        void shouldReturnResults_whenAbnAndPhysicianIdMatch() {
            LabTestResults matching = createLabTestResult(800, "CBC", "A", "WBC");
            createLabTestResult(800, "CBC", "N", "RBC");
            createLabTestResult(900, "Other", "A", "Other");

            List<LabTestResults> results = dao.findByAbnAndPhysicianId("A", 800);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        }
    }

    @Nested
    @DisplayName("findByLabPatientPhysicialInfoId")
    class FindByLabPatientPhysicialInfoId {

        @Test
        @Tag("query")
        @DisplayName("should return all results for matching physician info ID")
        void shouldReturnAllResults_whenPhysicianInfoIdMatches() {
            createLabTestResult(1000, "CBC", "N", "WBC");
            createLabTestResult(1000, "CBC", "A", "RBC");
            createLabTestResult(1000, "Chem", "N", "Glucose");
            createLabTestResult(1100, "Other", "N", "Other");

            List<LabTestResults> results = dao.findByLabPatientPhysicialInfoId(1000);

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(r -> r.getLabPatientPhysicianInfoId() == 1000);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching physician info ID")
        void shouldReturnEmptyList_whenNoMatchingPhysicianInfoId() {
            List<LabTestResults> results = dao.findByLabPatientPhysicialInfoId(99999);

            assertThat(results).isEmpty();
        }
    }
}
