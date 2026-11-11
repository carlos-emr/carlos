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
    private EChart createEChart(int demographicNo, String subject, Date timestamp) throws Exception {
        EChart entity = new EChart();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(demographicNo);
        entity.setSubject(subject);
        entity.setTimestamp(timestamp);
        eChartDao.persist(entity);
        return entity;
    }

    @Test
    void shouldSelectHighestId_whenLegacyEncounterChartTimestampsDisagree() throws Exception {
        EChart newerTime = createEChart(5547101, "newer timestamp", new Date(200000L));
        EChart newerId = createEChart(5547101, "newer ID", new Date(100000L));
        createEChart(5547102, "other patient", new Date(300000L));
        assertThat(eChartDao.getLatestChartById(5547101).getId()).isEqualTo(newerId.getId());
        assertThat(eChartDao.getLatestChart(5547101).getId()).isEqualTo(newerTime.getId());
        assertThat(eChartDao.getLatestChartById(5547103)).isNull();
    }

    @Test
    void shouldReturnDistinctBoundedPages_whenHistoricalRevisionsExist() throws Exception {
        EChart first = createEChart(5547201, "first", new Date(100000L));
        EChart second = createEChart(5547201, "second", new Date(100000L));
        EChart third = createEChart(5547201, "third", new Date(100000L));
        createEChart(5547202, "other", new Date(100000L));
        assertThat(eChartDao.getChartsForDemographic(5547201, 0, 2))
                .extracting(EChart::getId).containsExactly(third.getId(), second.getId());
        assertThat(eChartDao.getChartsForDemographic(5547201, 2, 2))
                .extracting(EChart::getId).containsExactly(first.getId());
        assertThat(eChartDao.getChartsForDemographic(5547201, 3, 2)).isEmpty();
        assertThatThrownBy(() -> eChartDao.getChartsForDemographic(5547201, -1, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {"NULL,0,1", "0,0,1", "-1,0,1",
            "5547201,0,0", "5547201,0,102"}, nullValues = "NULL")
    void shouldRefuseInvalidHistoricalPageBeforeQuerying(Integer patient, int offset, int limit) {
        assertThatThrownBy(() -> eChartDao.getChartsForDemographic(patient, offset, limit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowMaximumHistoricalPageSize_withoutReturningAnotherPatient() throws Exception {
        EChart own = createEChart(5547301, "own", new Date());
        createEChart(5547302, "other", new Date());
        assertThat(eChartDao.getChartsForDemographic(5547301, 0, 101))
                .extracting(EChart::getId).containsExactly(own.getId());
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist echart with generated ID")
        void shouldPersistEChart_whenValidDataProvided() throws Exception {
            EChart entity = new EChart();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            eChartDao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find echart by ID with matching fields")
        void shouldFindEChart_whenValidIdProvided() throws Exception {
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
        void shouldCountAllRecords() throws Exception {
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
        void shouldReturnLatestChart_byTimestampForDemographic() throws Exception {
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
        void shouldReturnNull_whenNoChartExistsForDemographic() throws Exception {
            EChart result = eChartDao.getLatestChart(99999);

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return max ID for demographic")
        void shouldReturnMaxId_forDemographic() throws Exception {
            EChart first = createEChart(333, "Sub1", new Date());
            EChart second = createEChart(333, "Sub2", new Date());
            hibernateTemplate.flush();

            Integer maxId = eChartDao.getMaxIdForDemographic(333);

            assertThat(maxId).isEqualTo(second.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return all charts for demographic ordered by ID desc")
        void shouldReturnAllCharts_forDemographic() throws Exception {
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
        void shouldFindCharts_byDemoIdAndSubject() throws Exception {
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
