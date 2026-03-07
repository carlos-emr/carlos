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

import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
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
 * Integration tests for {@link MsgDemoMapDao} covering create,
 * findByDemographicNo, findByMessageId, and remove.
 *
 * <p>Migrated from legacy {@code MsgDemoMapDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MsgDemoMapDao
 */
@DisplayName("MsgDemoMap Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("messaging")
@Transactional
public class MsgDemoMapDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MsgDemoMapDao dao;

    private MsgDemoMap createMsgDemoMap(int messageId, int demographicNo) {
        MsgDemoMap entity = new MsgDemoMap();
        entity.setMessageID(messageId);
        entity.setDemographic_no(demographicNo);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist msg demo map with generated ID")
        void shouldPersistMsgDemoMap_whenValidDataProvided() {
            MsgDemoMap entity = createMsgDemoMap(1, 100);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByDemographicNo")
    class FindByDemographicNo {

        @Test
        @Tag("read")
        @DisplayName("should return maps for matching demographic number")
        void shouldReturnMaps_whenDemographicNoMatches() {
            createMsgDemoMap(10, 200);
            createMsgDemoMap(20, 200);
            createMsgDemoMap(30, 300);

            List<MsgDemoMap> results = dao.findByDemographicNo(200);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(m -> m.getDemographic_no().equals(200));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching demographic number")
        void shouldReturnEmptyList_whenNoMatchingDemographicNo() {
            createMsgDemoMap(10, 200);

            List<MsgDemoMap> results = dao.findByDemographicNo(99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByMessageId")
    class FindByMessageId {

        @Test
        @Tag("read")
        @DisplayName("should return maps for matching message ID")
        void shouldReturnMaps_whenMessageIdMatches() {
            createMsgDemoMap(50, 100);
            createMsgDemoMap(50, 200);
            createMsgDemoMap(60, 300);

            List<MsgDemoMap> results = dao.findByMessageId(50);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(m -> m.getMessageID().equals(50));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matching message ID")
        void shouldReturnEmptyList_whenNoMatchingMessageId() {
            List<MsgDemoMap> results = dao.findByMessageId(99999);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @Tag("delete")
        @DisplayName("should remove mapping for specific message ID and demographic number")
        void shouldRemoveMapping_whenMessageIdAndDemographicNoMatch() {
            createMsgDemoMap(70, 400);
            createMsgDemoMap(70, 500);

            dao.remove(70, 400);

            List<MsgDemoMap> remaining = dao.findByMessageId(70);
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getDemographic_no()).isEqualTo(500);
        }

        @Test
        @Tag("delete")
        @DisplayName("should not remove anything when no matching mapping exists")
        void shouldNotRemoveAnything_whenNoMatchingMapping() {
            createMsgDemoMap(80, 600);

            dao.remove(80, 99999);

            List<MsgDemoMap> results = dao.findByMessageId(80);
            assertThat(results).hasSize(1);
        }
    }
}
