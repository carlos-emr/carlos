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
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DxresearchDAO}.
 *
 * <p>Migrated from legacy {@code DxresearchDAOTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DxresearchDAO
 */
@DisplayName("Dxresearch Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class DxresearchDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DxresearchDAO dao;

    /**
     * Helper to create and persist a Dxresearch entity with specific fields.
     */
    private Dxresearch createDxresearch(Integer demographicNo, String code, String codingSystem, Character status) {
        Dxresearch dr = new Dxresearch();
        EntityDataGenerator.generateTestDataForModelClass(dr);
        dr.setDemographicNo(demographicNo);
        dr.setDxresearchCode(code);
        dr.setCodingSystem(codingSystem);
        dr.setStatus(status);
        dr.setStartDate(new Date());
        dao.persist(dr);
        return dr;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist dxresearch with generated ID")
        void shouldPersistDxresearch_whenValidDataProvided() {
            Dxresearch dr = new Dxresearch();
            EntityDataGenerator.generateTestDataForModelClass(dr);
            dao.persist(dr);

            assertThat(dr.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find dxresearch by ID after persist")
        void shouldFindDxresearch_whenValidIdProvided() {
            Dxresearch dr = new Dxresearch();
            EntityDataGenerator.generateTestDataForModelClass(dr);
            dao.persist(dr);
            hibernateTemplate.flush();

            Dxresearch found = dao.find(dr.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(dr.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find by demographic no, research code and coding system with active status")
        void shouldFindDxresearch_byDemographicNoResearchCodeAndCodingSystem() {
            // The query filters for status 'A' or 'C' only
            Dxresearch active = createDxresearch(500, "CODE1", "ICD9", 'A');
            Dxresearch deleted = createDxresearch(500, "CODE1", "ICD9", 'D');
            Dxresearch differentCode = createDxresearch(500, "CODE2", "ICD9", 'A');
            hibernateTemplate.flush();

            List<Dxresearch> list = dao.findByDemographicNoResearchCodeAndCodingSystem(500, "CODE1", "ICD9");

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getId()).isEqualTo(active.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching entries exist")
        void shouldReturnEmptyList_whenNoMatchingEntriesExist() {
            createDxresearch(600, "ABC", "ICD9", 'A');
            hibernateTemplate.flush();

            List<Dxresearch> list = dao.findByDemographicNoResearchCodeAndCodingSystem(600, "NONEXISTENT", "ICD9");

            assertThat(list).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for INR report when no matching data exists")
        void shouldReturnEmptyList_whenNoInrReportDataExists() {
            Date now = new Date();
            List<Object[]> list = dao.getDataForInrReport(now, now);

            assertThat(list).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should count zero researches when no matching code exists")
        void shouldCountZeroResearches_whenNoMatchingCodeExists() {
            Date pastDate = new Date(0);
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L);

            Integer count = dao.countResearches("NONEXISTENT", pastDate, futureDate);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @Tag("query")
        @DisplayName("should count zero billing researches when no matching data exists")
        void shouldCountZeroBillingResearches_whenNoMatchingDataExists() {
            Date pastDate = new Date(0);
            Date futureDate = new Date(System.currentTimeMillis() + 86400000L);

            Integer count = dao.countBillingResearches("NONEXIST", "DIAG", "CREATOR", pastDate, futureDate);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @Tag("query")
        @DisplayName("should find entries by demographic number")
        void shouldFindEntries_byDemographicNo() {
            createDxresearch(700, "DX1", "ICD9", 'A');
            createDxresearch(700, "DX2", "ICD9", 'A');
            createDxresearch(701, "DX3", "ICD9", 'A');
            hibernateTemplate.flush();

            List<Dxresearch> result = dao.getByDemographicNo(700);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(dx -> dx.getDemographicNo().equals(700));
        }

        @Test
        @Tag("query")
        @DisplayName("should check entry existence correctly")
        void shouldCheckEntryExistence_whenEntryExistsOrNot() {
            createDxresearch(800, "EXISTS", "ICD9", 'A');
            hibernateTemplate.flush();

            boolean exists = dao.entryExists(800, "ICD9", "EXISTS");
            boolean notExists = dao.entryExists(800, "ICD9", "MISSING");

            assertThat(exists).isTrue();
            assertThat(notExists).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should find non-deleted entries by demographic number")
        void shouldFindNonDeletedEntries_byDemographicNo() {
            createDxresearch(900, "ND1", "ICD9", 'A');
            createDxresearch(900, "ND2", "ICD9", 'C');
            createDxresearch(900, "ND3", "ICD9", 'D');
            hibernateTemplate.flush();

            List<Dxresearch> result = dao.findNonDeletedByDemographicNo(900);

            assertThat(result).hasSize(2);
            assertThat(result).noneMatch(dx -> dx.getStatus().equals('D'));
        }
    }
}
