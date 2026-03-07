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
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TicklerLinkDao} covering persist,
 * lookup by table name/ID, and lookup by tickler number.
 *
 * <p>Migrated from legacy {@code TicklerLinkDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see TicklerLinkDao
 */
@DisplayName("TicklerLinkDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("tickler")
@Transactional
public class TicklerLinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TicklerLinkDao ticklerLinkDao;

    private TicklerLink createTicklerLink(String tableName, long tableId, int ticklerNo) {
        TicklerLink entity = new TicklerLink();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setTableName(tableName);
        entity.setTableId(tableId);
        entity.setTicklerNo(ticklerNo);
        ticklerLinkDao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist tickler link with generated ID")
        void shouldPersistTicklerLink_whenValidDataProvided() {
            TicklerLink entity = new TicklerLink();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            ticklerLinkDao.persist(entity);
            hibernateTemplate.flush();

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getLinkByTableId queries")
    class GetLinkByTableId {

        @Test
        @Tag("query")
        @DisplayName("should return links matching table name and table ID")
        void shouldReturnLinks_forMatchingTableNameAndTableId() {
            String tableName1 = "alp";
            String tableName2 = "brv";
            long tableId1 = 101L;
            long tableId2 = 202L;

            TicklerLink link1 = createTicklerLink(tableName1, tableId1, 1);
            createTicklerLink(tableName2, tableId1, 2);
            TicklerLink link3 = createTicklerLink(tableName1, tableId1, 3);
            createTicklerLink(tableName1, tableId2, 4);
            hibernateTemplate.flush();

            List<TicklerLink> result = ticklerLinkDao.getLinkByTableId(tableName1, tableId1);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TicklerLink::getId)
                    .containsExactlyInAnyOrder(link1.getId(), link3.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no links match table name and ID")
        void shouldReturnEmptyList_whenNoLinksMatchTableNameAndId() {
            createTicklerLink("other", 999L, 1);
            hibernateTemplate.flush();

            List<TicklerLink> result = ticklerLinkDao.getLinkByTableId("nonexistent", 888L);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLinkByTickler queries")
    class GetLinkByTickler {

        @Test
        @Tag("query")
        @DisplayName("should return links matching tickler number")
        void shouldReturnLinks_forMatchingTicklerNo() {
            int ticklerNo1 = 101;
            int ticklerNo2 = 202;

            TicklerLink link1 = createTicklerLink("t1", 1L, ticklerNo1);
            createTicklerLink("t2", 2L, ticklerNo2);
            TicklerLink link3 = createTicklerLink("t3", 3L, ticklerNo1);
            TicklerLink link4 = createTicklerLink("t4", 4L, ticklerNo1);
            hibernateTemplate.flush();

            List<TicklerLink> result = ticklerLinkDao.getLinkByTickler(ticklerNo1);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(TicklerLink::getId)
                    .containsExactlyInAnyOrder(link1.getId(), link3.getId(), link4.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no links match tickler number")
        void shouldReturnEmptyList_whenNoLinksMatchTicklerNo() {
            createTicklerLink("t1", 1L, 999);
            hibernateTemplate.flush();

            List<TicklerLink> result = ticklerLinkDao.getLinkByTickler(888);
            assertThat(result).isEmpty();
        }
    }
}
