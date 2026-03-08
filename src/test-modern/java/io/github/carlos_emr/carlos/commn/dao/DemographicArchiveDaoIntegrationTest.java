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
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicArchiveDao} covering persist,
 * findByDemographicNo, and findRosterStatusHistoryByDemographicNo.
 *
 * <p>Migrated from legacy {@code DemographicArchiveDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicArchiveDao
 */
@DisplayName("DemographicArchiveDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicArchiveDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicArchiveDao dao;

    private DemographicArchive createArchive(int demoNo) throws Exception {
        DemographicArchive entity = new DemographicArchive();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(demoNo);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    private DemographicArchive createArchiveWithRosterStatus(int demoNo, String rosterStatus) throws Exception {
        DemographicArchive entity = new DemographicArchive();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(demoNo);
        entity.setRosterStatus(rosterStatus);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist archive with generated ID")
    void shouldPersistArchive_whenValidDataProvided() throws Exception {
        DemographicArchive entity = new DemographicArchive();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findByDemographicNo")
    class FindByDemographicNo {

        @Test
        @Tag("query")
        @DisplayName("should return archives matching demographic number")
        void shouldReturnArchives_forMatchingDemographicNo() throws Exception {
            int demoNo1 = 101;
            int demoNo2 = 202;

            createArchive(demoNo1);
            createArchive(demoNo1);
            createArchive(demoNo2);
            createArchive(demoNo1);

            List<DemographicArchive> result = dao.findByDemographicNo(demoNo1);

            assertThat(result).hasSize(3);
            assertThat(result).allMatch(a -> a.getDemographicNo() == demoNo1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent demographic")
        void shouldReturnEmptyList_forNonExistentDemographic() throws Exception {
            List<DemographicArchive> result = dao.findByDemographicNo(99999);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findRosterStatusHistoryByDemographicNo")
    class FindRosterStatusHistory {

        @Test
        @Tag("query")
        @DisplayName("should return deduplicated roster status history for demographic")
        void shouldReturnDeduplicatedHistory_forDemographic() throws Exception {
            int demoNo1 = 101;
            int demoNo2 = 202;

            createArchiveWithRosterStatus(demoNo1, "alpha");
            createArchiveWithRosterStatus(demoNo1, "bravo");
            createArchiveWithRosterStatus(demoNo2, "charlie");
            // Two consecutive "charlie" entries for demoNo1 - one should be deduplicated
            createArchiveWithRosterStatus(demoNo1, "charlie");
            createArchiveWithRosterStatus(demoNo1, "charlie");

            List<DemographicArchive> result = dao.findRosterStatusHistoryByDemographicNo(demoNo1);

            // The method deduplicates consecutive entries with same rosterStatus+rosterDate+rosterTerminationDate
            // Ordered by id desc, so: charlie(5), charlie(4), bravo(2), alpha(1)
            // After dedup of consecutive charlie: charlie, bravo, alpha = 3 entries
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(a -> a.getDemographicNo() == demoNo1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for demographic with no archives")
        void shouldReturnEmptyList_forDemographicWithNoArchives() throws Exception {
            List<DemographicArchive> result = dao.findRosterStatusHistoryByDemographicNo(99999);

            assertThat(result).isEmpty();
        }
    }
}
