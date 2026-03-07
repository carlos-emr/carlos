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
import io.github.carlos_emr.carlos.commn.model.ReportTableFieldCaption;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportTableFieldCaptionDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code ReportTableFieldCaptionDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportTableFieldCaptionDao
 */
@DisplayName("ReportTableFieldCaption Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ReportTableFieldCaptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportTableFieldCaptionDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist report table field caption with generated ID")
    void shouldPersistReportTableFieldCaption_whenValidDataProvided() {
        ReportTableFieldCaption entity = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching captions when filtered by table name and name")
    void shouldReturnMatchingCaptions_whenFilteredByTableNameAndName() {
        String tableName1 = "table1", tableName2 = "table2";
        String name1 = "alpha", name2 = "bravo";

        ReportTableFieldCaption rTFC1 = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(rTFC1);
        rTFC1.setTableName(tableName1);
        rTFC1.setName(name2);
        dao.persist(rTFC1);

        ReportTableFieldCaption rTFC2 = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(rTFC2);
        rTFC2.setTableName(tableName1);
        rTFC2.setName(name1);
        dao.persist(rTFC2);

        ReportTableFieldCaption rTFC3 = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(rTFC3);
        rTFC3.setTableName(tableName1);
        rTFC3.setName(name1);
        dao.persist(rTFC3);

        ReportTableFieldCaption rTFC4 = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(rTFC4);
        rTFC4.setTableName(tableName2);
        rTFC4.setName(name1);
        dao.persist(rTFC4);

        ReportTableFieldCaption rTFC5 = new ReportTableFieldCaption();
        EntityDataGenerator.generateTestDataForModelClass(rTFC5);
        rTFC5.setTableName(tableName1);
        rTFC5.setName(name1);
        dao.persist(rTFC5);

        List<ReportTableFieldCaption> expectedResult = Arrays.asList(rTFC2, rTFC3, rTFC5);
        List<ReportTableFieldCaption> result = dao.findByTableNameAndName(tableName1, name1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
