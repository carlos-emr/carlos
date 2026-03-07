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
import io.github.carlos_emr.carlos.commn.model.ReportLetters;
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
 * Integration tests for {@link ReportLettersDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code ReportLettersDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportLettersDao
 */
@DisplayName("ReportLetters Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ReportLettersDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportLettersDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("create")
    @DisplayName("should persist report letters with generated ID")
    void shouldPersistReportLetters_whenValidDataProvided() throws Exception {
        ReportLetters entity = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return only non-archived report letters when finding current")
    void shouldReturnNonArchivedReportLetters_whenFindingCurrent() throws Exception {
        String archive0 = "0", archive1 = "1";
        String reportName1 = "alpha", reportName2 = "bravo", reportName3 = "charlie",
                reportName4 = "delta", reportName5 = "epislon";

        Date date1 = new Date(dfm.parse("20020101").getTime());
        Date date2 = new Date(dfm.parse("20040101").getTime());
        Date date3 = new Date(dfm.parse("20060101").getTime());
        Date date4 = new Date(dfm.parse("20080101").getTime());
        Date date5 = new Date(dfm.parse("20110101").getTime());

        ReportLetters reportLetters1 = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(reportLetters1);
        reportLetters1.setArchive(archive0);
        reportLetters1.setDateTime(date1);
        reportLetters1.setReportName(reportName1);
        dao.persist(reportLetters1);

        ReportLetters reportLetters2 = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(reportLetters2);
        reportLetters2.setArchive(archive1);
        reportLetters2.setDateTime(date2);
        reportLetters2.setReportName(reportName2);
        dao.persist(reportLetters2);

        ReportLetters reportLetters3 = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(reportLetters3);
        reportLetters3.setArchive(archive1);
        reportLetters3.setDateTime(date3);
        reportLetters3.setReportName(reportName3);
        dao.persist(reportLetters3);

        ReportLetters reportLetters4 = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(reportLetters4);
        reportLetters4.setArchive(archive0);
        reportLetters4.setDateTime(date4);
        reportLetters4.setReportName(reportName4);
        dao.persist(reportLetters4);

        ReportLetters reportLetters5 = new ReportLetters();
        EntityDataGenerator.generateTestDataForModelClass(reportLetters5);
        reportLetters5.setArchive(archive0);
        reportLetters5.setDateTime(date5);
        reportLetters5.setReportName(reportName5);
        dao.persist(reportLetters5);

        List<ReportLetters> expectedResult = Arrays.asList(reportLetters1, reportLetters4, reportLetters5);
        List<ReportLetters> result = dao.findCurrent();

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
