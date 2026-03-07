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

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist dxresearch with generated ID")
        void shouldPersistDxresearch_whenValidDataProvided() throws Exception {
            Dxresearch dr = new Dxresearch();
            EntityDataGenerator.generateTestDataForModelClass(dr);
            dao.persist(dr);

            assertThat(dr.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find by demographic no, research code and coding system")
        void shouldFindDxresearch_byDemographicNoResearchCodeAndCodingSystem() {
            List<Dxresearch> list = dao.findByDemographicNoResearchCodeAndCodingSystem(1, "CODE", "SYS");
            assertThat(list).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should get data for INR report")
        void shouldGetDataForInrReport_whenDateRangeProvided() {
            List<Object[]> list = dao.getDataForInrReport(new Date(), new Date());
            assertThat(list).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should count researches by code and date range")
        void shouldCountResearches_byCodeAndDateRange() {
            assertThat(dao.countResearches("CDE", new Date(), new Date())).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should count billing researches by code, diagnosis, creator and date range")
        void shouldCountBillingResearches_byCodeDiagnosisCreatorAndDateRange() {
            assertThat(dao.countBillingResearches("CDE", "DIAG", "CREATOR", new Date(), new Date())).isNotNull();
        }
    }
}
