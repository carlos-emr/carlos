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
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
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
 * Integration tests for {@link GroupNoteDao} covering findLinksByDemographic,
 * findLinksByNoteId, and getNumberOfLinksByNoteId.
 *
 * <p>Migrated from legacy {@code GroupNoteDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see GroupNoteDao
 */
@DisplayName("GroupNote Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class GroupNoteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private GroupNoteDao dao;

    @Nested
    @DisplayName("findLinksByDemographic")
    class FindLinksByDemographic {

        @Test
        @Tag("read")
        @DisplayName("should return active links for the given demographic")
        void shouldReturnActiveLinks_whenDemographicNoMatches() throws Exception {
            int demographicNo1 = 101;
            int demographicNo2 = 202;

            GroupNoteLink gnl1 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl1);
            gnl1.setDemographicNo(demographicNo1);
            gnl1.setActive(true);
            dao.persist(gnl1);

            GroupNoteLink gnl2 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl2);
            gnl2.setDemographicNo(demographicNo2);
            gnl2.setActive(true);
            dao.persist(gnl2);

            GroupNoteLink gnl3 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl3);
            gnl3.setDemographicNo(demographicNo1);
            gnl3.setActive(true);
            dao.persist(gnl3);

            GroupNoteLink gnl4 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl4);
            gnl4.setDemographicNo(demographicNo1);
            gnl4.setActive(false);
            dao.persist(gnl4);

            List<GroupNoteLink> result = dao.findLinksByDemographic(demographicNo1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(gnl1, gnl3);
        }
    }

    @Nested
    @DisplayName("findLinksByNoteId")
    class FindLinksByNoteId {

        @Test
        @Tag("read")
        @DisplayName("should return active links for the given note ID")
        void shouldReturnActiveLinks_whenNoteIdMatches() throws Exception {
            int noteId1 = 101;
            int noteId2 = 202;

            GroupNoteLink gnl1 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl1);
            gnl1.setNoteId(noteId1);
            gnl1.setActive(true);
            dao.persist(gnl1);

            GroupNoteLink gnl2 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl2);
            gnl2.setNoteId(noteId2);
            gnl2.setActive(true);
            dao.persist(gnl2);

            GroupNoteLink gnl3 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl3);
            gnl3.setNoteId(noteId1);
            gnl3.setActive(true);
            dao.persist(gnl3);

            GroupNoteLink gnl4 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl4);
            gnl4.setNoteId(noteId1);
            gnl4.setActive(false);
            dao.persist(gnl4);

            List<GroupNoteLink> result = dao.findLinksByNoteId(noteId1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(gnl1, gnl3);
        }
    }

    @Nested
    @DisplayName("getNumberOfLinksByNoteId")
    class GetNumberOfLinksByNoteId {

        @Test
        @Tag("aggregate")
        @DisplayName("should return count of all links for the given note ID")
        void shouldReturnLinkCount_whenNoteIdMatches() throws Exception {
            int noteId1 = 101;
            int noteId2 = 202;

            GroupNoteLink gnl1 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl1);
            gnl1.setNoteId(noteId1);
            dao.persist(gnl1);

            GroupNoteLink gnl2 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl2);
            gnl2.setNoteId(noteId2);
            dao.persist(gnl2);

            GroupNoteLink gnl3 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl3);
            gnl3.setNoteId(noteId1);
            dao.persist(gnl3);

            GroupNoteLink gnl4 = new GroupNoteLink();
            EntityDataGenerator.generateTestDataForModelClass(gnl4);
            gnl4.setNoteId(noteId1);
            dao.persist(gnl4);

            int result = dao.getNumberOfLinksByNoteId(noteId1);

            assertThat(result).isEqualTo(3);
        }
    }
}
