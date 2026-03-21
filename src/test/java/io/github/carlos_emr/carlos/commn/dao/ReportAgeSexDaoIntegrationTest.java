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
import io.github.carlos_emr.carlos.commn.model.ReportAgeSex;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportAgeSexDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code ReportAgeSexDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportAgeSexDao
 */
@DisplayName("ReportAgeSex Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ReportAgeSexDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportAgeSexDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("read")
    @DisplayName("should return records matching the report date when finding before report date")
    void shouldReturnMatchingRecords_whenFindingBeforeReportDate() throws Exception {
        Date date1 = new Date(dfm.parse("20110101").getTime());
        Date date2 = new Date(dfm.parse("20100101").getTime());

        ReportAgeSex reportAgeSex1 = new ReportAgeSex();
        EntityDataGenerator.generateTestDataForModelClass(reportAgeSex1);
        reportAgeSex1.setReportDate(date1);
        dao.persist(reportAgeSex1);

        ReportAgeSex reportAgeSex2 = new ReportAgeSex();
        EntityDataGenerator.generateTestDataForModelClass(reportAgeSex2);
        reportAgeSex2.setReportDate(date2);
        dao.persist(reportAgeSex2);

        ReportAgeSex reportAgeSex3 = new ReportAgeSex();
        EntityDataGenerator.generateTestDataForModelClass(reportAgeSex3);
        reportAgeSex3.setReportDate(date1);
        dao.persist(reportAgeSex3);

        List<ReportAgeSex> expectedResult = Arrays.asList(reportAgeSex1, reportAgeSex3);
        List<ReportAgeSex> result = dao.findBeforeReportDate(date1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    @Test
    @Tag("delete")
    @DisplayName("should remove all records for date when deleting all by date")
    void shouldRemoveAllRecords_whenDeletingAllByDate() throws Exception {
        Date date1 = new Date(dfm.parse("20110101").getTime());

        ReportAgeSex reportAgeSex1 = new ReportAgeSex();
        EntityDataGenerator.generateTestDataForModelClass(reportAgeSex1);
        reportAgeSex1.setReportDate(date1);
        dao.persist(reportAgeSex1);

        List<ReportAgeSex> expectedResult = Arrays.asList(reportAgeSex1);
        List<ReportAgeSex> result = dao.findBeforeReportDate(date1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }

        dao.deleteAllByDate(date1);

        List<ReportAgeSex> newResult = dao.findBeforeReportDate(date1);
        assertThat(newResult).isEmpty();
    }
}
