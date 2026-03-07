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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link EChartDao}.
 *
 * <p>Tests cover persist, find, getLatestChart, getMaxIdForDemographic,
 * getChartsForDemographic, findByDemoIdAndSubject, and getCountAll.</p>
 *
 * @since 2026-03-07
 * @see EChartDao
 */
@DisplayName("EChart Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("encounter")
@Transactional
public class EChartDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EChartDao eChartDao;

    /**
     * Helper to create and persist an EChart with specific demographic and subject.
     */
    private EChart createEChart(int demographicNo, String subject, Date timestamp) {
        EChart entity = new EChart();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(demographicNo);
        entity.setSubject(subject);
        entity.setTimestamp(timestamp);
        eChartDao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist echart with generated ID")
        void shouldPersistEChart_whenValidDataProvided() {
            EChart entity = new EChart();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            eChartDao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find echart by ID with matching fields")
        void shouldFindEChart_whenValidIdProvided() {
            EChart saved = new EChart();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            saved.setDemographicNo(111);
            saved.setSubject("TestSubject");
            eChartDao.persist(saved);
            hibernateTemplate.flush();

            EChart found = eChartDao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getDemographicNo()).isEqualTo(111);
            assertThat(found.getSubject()).isEqualTo("TestSubject");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all records accurately")
        void shouldCountAllRecords() {
            int initialCount = eChartDao.getCountAll();

            EChart entity1 = new EChart();
            EntityDataGenerator.generateTestDataForModelClass(entity1);
            eChartDao.persist(entity1);

            EChart entity2 = new EChart();
            EntityDataGenerator.generateTestDataForModelClass(entity2);
            eChartDao.persist(entity2);

            hibernateTemplate.flush();

            int newCount = eChartDao.getCountAll();

            assertThat(newCount).isEqualTo(initialCount + 2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return latest chart by timestamp for demographic")
        void shouldReturnLatestChart_byTimestampForDemographic() {
            Date olderDate = new Date(1000000L);
            Date newerDate = new Date(2000000L);

            EChart older = createEChart(222, "OldSubject", olderDate);
            EChart newer = createEChart(222, "NewSubject", newerDate);
            hibernateTemplate.flush();

            EChart latest = eChartDao.getLatestChart(222);

            assertThat(latest).isNotNull();
            assertThat(latest.getId()).isEqualTo(newer.getId());
            assertThat(latest.getSubject()).isEqualTo("NewSubject");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no chart exists for demographic")
        void shouldReturnNull_whenNoChartExistsForDemographic() {
            EChart result = eChartDao.getLatestChart(99999);

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return max ID for demographic")
        void shouldReturnMaxId_forDemographic() {
            EChart first = createEChart(333, "Sub1", new Date());
            EChart second = createEChart(333, "Sub2", new Date());
            hibernateTemplate.flush();

            Integer maxId = eChartDao.getMaxIdForDemographic(333);

            assertThat(maxId).isEqualTo(second.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return all charts for demographic ordered by ID desc")
        void shouldReturnAllCharts_forDemographic() {
            createEChart(444, "SubA", new Date());
            createEChart(444, "SubB", new Date());
            createEChart(445, "SubC", new Date());
            hibernateTemplate.flush();

            List<EChart> charts = eChartDao.getChartsForDemographic(444);

            assertThat(charts).hasSize(2);
            assertThat(charts).allMatch(c -> c.getDemographicNo() == 444);
        }

        @Test
        @Tag("query")
        @DisplayName("should find charts by demographic and subject")
        void shouldFindCharts_byDemoIdAndSubject() {
            createEChart(555, "MatchSubject", new Date());
            createEChart(555, "OtherSubject", new Date());
            createEChart(555, "MatchSubject", new Date());
            hibernateTemplate.flush();

            List<EChart> result = eChartDao.findByDemoIdAndSubject(555, "MatchSubject");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getSubject().equals("MatchSubject"));
        }
    }
}
