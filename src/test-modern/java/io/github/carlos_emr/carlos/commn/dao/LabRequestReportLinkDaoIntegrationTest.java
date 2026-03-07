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
import io.github.carlos_emr.carlos.commn.model.LabRequestReportLink;
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
 * Integration tests for {@link LabRequestReportLinkDao} covering create,
 * findByReportTableAndReportId, and findByRequestTableAndRequestId.
 *
 * <p>Migrated from legacy {@code LabRequestReportLinkDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LabRequestReportLinkDao
 */
@DisplayName("LabRequestReportLink Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class LabRequestReportLinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LabRequestReportLinkDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist lab request report link with generated ID")
        void shouldPersistLabRequestReportLink_whenValidDataProvided() {
            LabRequestReportLink entity = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByReportTableAndReportId")
    class FindByReportTableAndReportId {

        @Test
        @Tag("search")
        @DisplayName("should return links matching report table and report ID")
        void shouldReturnLinks_whenReportTableAndIdMatch() {
            String reportTable1 = "alpha", reportTable2 = "bravo";
            int reportId1 = 101, reportId2 = 202;

            LabRequestReportLink link1 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link1);
            link1.setReportTable(reportTable2);
            link1.setReportId(reportId1);
            dao.persist(link1);

            LabRequestReportLink link2 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link2);
            link2.setReportTable(reportTable1);
            link2.setReportId(reportId1);
            dao.persist(link2);

            LabRequestReportLink link3 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link3);
            link3.setReportTable(reportTable1);
            link3.setReportId(reportId2);
            dao.persist(link3);

            LabRequestReportLink link4 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link4);
            link4.setReportTable(reportTable1);
            link4.setReportId(reportId1);
            dao.persist(link4);

            LabRequestReportLink link5 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link5);
            link5.setReportTable(reportTable2);
            link5.setReportId(reportId2);
            dao.persist(link5);

            LabRequestReportLink link6 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link6);
            link6.setReportTable(reportTable1);
            link6.setReportId(reportId1);
            dao.persist(link6);

            List<LabRequestReportLink> result = dao.findByReportTableAndReportId(reportTable1, reportId1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(link2, link4, link6);
        }
    }

    @Nested
    @DisplayName("findByRequestTableAndRequestId")
    class FindByRequestTableAndRequestId {

        @Test
        @Tag("search")
        @DisplayName("should return links matching request table and request ID")
        void shouldReturnLinks_whenRequestTableAndIdMatch() {
            String requestTable1 = "alpha", requestTable2 = "bravo";
            int requestId1 = 101, requestId2 = 202;

            LabRequestReportLink link1 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link1);
            link1.setRequestTable(requestTable2);
            link1.setRequestId(requestId1);
            dao.persist(link1);

            LabRequestReportLink link2 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link2);
            link2.setRequestTable(requestTable1);
            link2.setRequestId(requestId1);
            dao.persist(link2);

            LabRequestReportLink link3 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link3);
            link3.setRequestTable(requestTable1);
            link3.setRequestId(requestId2);
            dao.persist(link3);

            LabRequestReportLink link4 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link4);
            link4.setRequestTable(requestTable1);
            link4.setRequestId(requestId1);
            dao.persist(link4);

            LabRequestReportLink link5 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link5);
            link5.setRequestTable(requestTable2);
            link5.setRequestId(requestId2);
            dao.persist(link5);

            LabRequestReportLink link6 = new LabRequestReportLink();
            EntityDataGenerator.generateTestDataForModelClass(link6);
            link6.setRequestTable(requestTable1);
            link6.setRequestId(requestId1);
            dao.persist(link6);

            List<LabRequestReportLink> result = dao.findByRequestTableAndRequestId(requestTable1, requestId1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(link2, link4, link6);
        }
    }
}
