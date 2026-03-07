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
import io.github.carlos_emr.carlos.commn.model.PageMonitor;
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
 * Integration tests for {@link PageMonitorDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code PageMonitorDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PageMonitorDao
 */
@DisplayName("PageMonitor Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class PageMonitorDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PageMonitorDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("read")
    @DisplayName("should return matching monitors ordered by date when filtered by page name and ID")
    void shouldReturnMatchingMonitors_whenFilteredByPageNameAndId() throws Exception {
        String pageId1 = "100";
        String pageId2 = "200";
        String pageName1 = "alpha";
        String pageName2 = "bravo";

        PageMonitor pageMonitor1 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor1);
        pageMonitor1.setPageId(pageId1);
        pageMonitor1.setPageName(pageName1);
        Date updateDate1 = new Date(dfm.parse("20010701").getTime());
        pageMonitor1.setUpdateDate(updateDate1);
        dao.persist(pageMonitor1);

        PageMonitor pageMonitor2 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor2);
        pageMonitor2.setPageId(pageId2);
        pageMonitor2.setPageName(pageName2);
        Date updateDate2 = new Date(dfm.parse("20100701").getTime());
        pageMonitor2.setUpdateDate(updateDate2);
        dao.persist(pageMonitor2);

        PageMonitor pageMonitor3 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor3);
        pageMonitor3.setPageId(pageId1);
        pageMonitor3.setPageName(pageName1);
        Date updateDate3 = new Date(dfm.parse("20110701").getTime());
        pageMonitor3.setUpdateDate(updateDate3);
        dao.persist(pageMonitor3);

        List<PageMonitor> expectedResult = Arrays.asList(pageMonitor3, pageMonitor1);
        List<PageMonitor> result = dao.findByPage(pageName1, pageId1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching monitors ordered by date when filtered by page name only")
    void shouldReturnMatchingMonitors_whenFilteredByPageNameOnly() throws Exception {
        String pageId1 = "100";
        String pageId2 = "200";
        String pageName1 = "alpha";
        String pageName2 = "bravo";

        PageMonitor pageMonitor1 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor1);
        pageMonitor1.setPageId(pageId1);
        pageMonitor1.setPageName(pageName1);
        Date updateDate1 = new Date(dfm.parse("20010701").getTime());
        pageMonitor1.setUpdateDate(updateDate1);
        dao.persist(pageMonitor1);

        PageMonitor pageMonitor2 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor2);
        pageMonitor2.setPageId(pageId2);
        pageMonitor2.setPageName(pageName2);
        Date updateDate2 = new Date(dfm.parse("20100701").getTime());
        pageMonitor2.setUpdateDate(updateDate2);
        dao.persist(pageMonitor2);

        PageMonitor pageMonitor3 = new PageMonitor();
        EntityDataGenerator.generateTestDataForModelClass(pageMonitor3);
        pageMonitor3.setPageId(pageId1);
        pageMonitor3.setPageName(pageName1);
        Date updateDate3 = new Date(dfm.parse("20110701").getTime());
        pageMonitor3.setUpdateDate(updateDate3);
        dao.persist(pageMonitor3);

        List<PageMonitor> expectedResult = Arrays.asList(pageMonitor3, pageMonitor1);
        List<PageMonitor> result = dao.findByPageName(pageName1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
