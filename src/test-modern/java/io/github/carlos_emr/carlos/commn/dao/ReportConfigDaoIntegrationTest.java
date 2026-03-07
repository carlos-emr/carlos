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
import io.github.carlos_emr.carlos.commn.model.ReportConfig;
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
 * Integration tests for {@link ReportConfigDao} covering create,
 * findByReportIdAndNameAndCaptionAndTableNameAndSave, and findByReportIdAndSaveAndGtOrderNo.
 *
 * <p>Migrated from legacy {@code ReportConfigDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportConfigDao
 */
@DisplayName("ReportConfig Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("reporting")
@Transactional
public class ReportConfigDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportConfigDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist report config with generated ID")
        void shouldPersistReportConfig_whenValidDataProvided() {
            ReportConfig entity = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByReportIdAndNameAndCaptionAndTableNameAndSave")
    class FindByReportIdAndNameAndCaptionAndTableNameAndSave {

        @Test
        @Tag("search")
        @DisplayName("should return configs matching all five criteria")
        void shouldReturnMatchingConfigs_whenAllCriteriaMatch() {
            int reportId1 = 101, reportId2 = 202;
            String name1 = "alpha", name2 = "bravo";
            String caption1 = "charlie", caption2 = "delta";
            String tableName1 = "table1", tableName2 = "table2";
            String save1 = "sigma", save2 = "omega";

            ReportConfig rc1 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc1);
            rc1.setReportId(reportId1);
            rc1.setName(name2);
            rc1.setCaption(caption1);
            rc1.setTableName(tableName1);
            rc1.setSave(save1);
            dao.persist(rc1);

            ReportConfig rc2 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc2);
            rc2.setReportId(reportId1);
            rc2.setName(name1);
            rc2.setCaption(caption2);
            rc2.setTableName(tableName1);
            rc2.setSave(save1);
            dao.persist(rc2);

            ReportConfig rc3 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc3);
            rc3.setReportId(reportId1);
            rc3.setName(name1);
            rc3.setCaption(caption1);
            rc3.setTableName(tableName2);
            rc3.setSave(save1);
            dao.persist(rc3);

            ReportConfig rc4 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc4);
            rc4.setReportId(reportId1);
            rc4.setName(name1);
            rc4.setCaption(caption1);
            rc4.setTableName(tableName1);
            rc4.setSave(save1);
            dao.persist(rc4);

            ReportConfig rc5 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc5);
            rc5.setReportId(reportId1);
            rc5.setName(name1);
            rc5.setCaption(caption1);
            rc5.setTableName(tableName1);
            rc5.setSave(save2);
            dao.persist(rc5);

            ReportConfig rc6 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc6);
            rc6.setReportId(reportId1);
            rc6.setName(name1);
            rc6.setCaption(caption1);
            rc6.setTableName(tableName1);
            rc6.setSave(save1);
            dao.persist(rc6);

            ReportConfig rc7 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc7);
            rc7.setReportId(reportId2);
            rc7.setName(name1);
            rc7.setCaption(caption1);
            rc7.setTableName(tableName1);
            rc7.setSave(save1);
            dao.persist(rc7);

            ReportConfig rc8 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc8);
            rc8.setReportId(reportId1);
            rc8.setName(name1);
            rc8.setCaption(caption1);
            rc8.setTableName(tableName1);
            rc8.setSave(save1);
            dao.persist(rc8);

            List<ReportConfig> result = dao.findByReportIdAndNameAndCaptionAndTableNameAndSave(
                    reportId1, name1, caption1, tableName1, save1);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(rc4, rc6, rc8);
        }
    }

    @Nested
    @DisplayName("findByReportIdAndSaveAndGtOrderNo")
    class FindByReportIdAndSaveAndGtOrderNo {

        @Test
        @Tag("search")
        @DisplayName("should return configs matching reportId and save with orderNo >= threshold")
        void shouldReturnMatchingConfigs_withGtOrderNo() {
            int reportId1 = 101, reportId2 = 202;
            int orderNo1 = 111, orderNo2 = 222;
            String save1 = "alpha", save2 = "bravo";

            ReportConfig rc1 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc1);
            rc1.setReportId(reportId2);
            rc1.setSave(save1);
            rc1.setOrderNo(orderNo1);
            dao.persist(rc1);

            ReportConfig rc2 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc2);
            rc2.setReportId(reportId1);
            rc2.setSave(save1);
            rc2.setOrderNo(orderNo1);
            dao.persist(rc2);

            ReportConfig rc3 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc3);
            rc3.setReportId(reportId1);
            rc3.setSave(save2);
            rc3.setOrderNo(orderNo1);
            dao.persist(rc3);

            ReportConfig rc4 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc4);
            rc4.setReportId(reportId1);
            rc4.setSave(save1);
            rc4.setOrderNo(orderNo2);
            dao.persist(rc4);

            ReportConfig rc5 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc5);
            rc5.setReportId(reportId1);
            rc5.setSave(save1);
            rc5.setOrderNo(orderNo1);
            dao.persist(rc5);

            ReportConfig rc6 = new ReportConfig();
            EntityDataGenerator.generateTestDataForModelClass(rc6);
            rc6.setReportId(reportId1);
            rc6.setSave(save1);
            rc6.setOrderNo(orderNo1);
            dao.persist(rc6);

            List<ReportConfig> result = dao.findByReportIdAndSaveAndGtOrderNo(reportId1, save1, orderNo1);

            assertThat(result).hasSize(4);
            assertThat(result).containsExactly(rc4, rc2, rc5, rc6);
        }
    }
}
