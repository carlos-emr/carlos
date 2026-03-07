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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicMergedDao} covering persist,
 * findCurrentByMergedTo, findCurrentByDemographicNo, and findByDemographicNo.
 *
 * <p>Migrated from legacy {@code DemographicMergedDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicMergedDao
 */
@DisplayName("DemographicMergedDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicMergedDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicMergedDao dao;

    @Autowired
    private DemographicDao demographicDao;

    private int demoId1;
    private int demoId2;

    /**
     * Creates parent Demographic records to satisfy foreign key constraints
     * on the demographic_merged table.
     */
    @BeforeEach
    void setUp() {
        Demographic demo1 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo1);
        demo1.setDemographicNo(null);
        demographicDao.save(demo1);

        Demographic demo2 = new Demographic();
        EntityDataGenerator.generateTestDataForModelClass(demo2);
        demo2.setDemographicNo(null);
        demographicDao.save(demo2);

        hibernateTemplate.flush();

        demoId1 = demo1.getDemographicNo();
        demoId2 = demo2.getDemographicNo();
    }

    private DemographicMerged createMerged(int demoNo, int mergedTo, int deleted) {
        DemographicMerged entity = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(demoNo);
        entity.setMergedTo(mergedTo);
        entity.setDeleted(deleted);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist merged record with generated ID")
    void shouldPersistMergedRecord_whenValidDataProvided() {
        DemographicMerged entity = new DemographicMerged();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setMergedTo(demoId1);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findCurrentByMergedTo")
    class FindCurrentByMergedTo {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted records matching mergedTo")
        void shouldReturnNonDeletedRecords_forMergedTo() {
            DemographicMerged merged1 = createMerged(demoId1, demoId1, 0);
            createMerged(demoId2, demoId2, 0);
            DemographicMerged merged3 = createMerged(demoId1, demoId1, 0);
            // deleted record - should not be returned
            createMerged(demoId1, demoId1, 1);

            List<DemographicMerged> result = dao.findCurrentByMergedTo(demoId1);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DemographicMerged::getId)
                    .containsExactlyInAnyOrder(merged1.getId(), merged3.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when all records are deleted")
        void shouldReturnEmptyList_whenAllRecordsAreDeleted() {
            createMerged(demoId1, demoId1, 1);

            List<DemographicMerged> result = dao.findCurrentByMergedTo(demoId1);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findCurrentByDemographicNo")
    class FindCurrentByDemographicNo {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted records matching demographic number")
        void shouldReturnNonDeletedRecords_forDemographicNo() {
            DemographicMerged merged1 = createMerged(demoId1, demoId1, 0);
            createMerged(demoId2, demoId2, 0);
            DemographicMerged merged3 = createMerged(demoId1, demoId1, 0);
            // deleted record - should not be returned
            createMerged(demoId1, demoId1, 1);

            List<DemographicMerged> result = dao.findCurrentByDemographicNo(demoId1);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DemographicMerged::getId)
                    .containsExactlyInAnyOrder(merged1.getId(), merged3.getId());
        }
    }

    @Nested
    @DisplayName("findByDemographicNo")
    class FindByDemographicNo {

        @Test
        @Tag("query")
        @DisplayName("should return all records matching demographic number regardless of deleted status")
        void shouldReturnAllRecords_forDemographicNo() {
            DemographicMerged merged1 = createMerged(demoId1, demoId1, 0);
            createMerged(demoId2, demoId2, 0);
            createMerged(demoId2, demoId2, 0);

            List<DemographicMerged> result = dao.findByDemographicNo(demoId1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(merged1.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent demographic")
        void shouldReturnEmptyList_forNonExistentDemographic() {
            List<DemographicMerged> result = dao.findByDemographicNo(99999);

            assertThat(result).isEmpty();
        }
    }
}
