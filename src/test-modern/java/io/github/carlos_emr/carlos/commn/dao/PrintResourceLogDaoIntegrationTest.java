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
import io.github.carlos_emr.carlos.commn.model.PrintResourceLog;
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
 * Integration tests for {@link PrintResourceLogDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code PrintResourceLogDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PrintResourceLogDao
 */
@DisplayName("PrintResourceLog Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class PrintResourceLogDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PrintResourceLogDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("create")
    @DisplayName("should persist print resource log with generated ID")
    void shouldPersistPrintResourceLog_whenValidDataProvided() throws Exception {
        PrintResourceLog entity = new PrintResourceLog();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching logs ordered by date when filtered by resource name and ID")
    void shouldReturnMatchingLogs_whenFilteredByResourceNameAndId() throws Exception {
        String resourceId1 = "100";
        String resourceId2 = "200";
        String resourceName1 = "alpha";
        String resourceName2 = "bravo";

        PrintResourceLog printResourceLog1 = new PrintResourceLog();
        EntityDataGenerator.generateTestDataForModelClass(printResourceLog1);
        printResourceLog1.setResourceId(resourceId1);
        printResourceLog1.setResourceName(resourceName1);
        Date date1 = new Date(dfm.parse("20010101").getTime());
        printResourceLog1.setDateTime(date1);
        dao.persist(printResourceLog1);

        PrintResourceLog printResourceLog2 = new PrintResourceLog();
        EntityDataGenerator.generateTestDataForModelClass(printResourceLog2);
        printResourceLog2.setResourceId(resourceId2);
        printResourceLog2.setResourceName(resourceName2);
        Date date2 = new Date(dfm.parse("20100101").getTime());
        printResourceLog2.setDateTime(date2);
        dao.persist(printResourceLog2);

        PrintResourceLog printResourceLog3 = new PrintResourceLog();
        EntityDataGenerator.generateTestDataForModelClass(printResourceLog3);
        printResourceLog3.setResourceId(resourceId1);
        printResourceLog3.setResourceName(resourceName1);
        Date date3 = new Date(dfm.parse("20080101").getTime());
        printResourceLog3.setDateTime(date3);
        dao.persist(printResourceLog3);

        List<PrintResourceLog> expectedResult = Arrays.asList(printResourceLog3, printResourceLog1);
        List<PrintResourceLog> result = dao.findByResource(resourceName1, resourceId1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
