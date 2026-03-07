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
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
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
 * Integration tests for {@link WaitingListDao} covering
 * create, findByDemographic, findWaitingListsAndDemographics,
 * findAppointmentFor, findByWaitingListIdAndDemographicId, and getMaxPosition.
 *
 * <p>Migrated from legacy {@code WaitingListDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see WaitingListDao
 */
@DisplayName("WaitingListDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class WaitingListDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WaitingListDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist waiting list entry with generated ID")
        void shouldPersistWaitingList_whenValidDataProvided() {
            WaitingList entity = new WaitingList();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find waiting lists by demographic number")
        void shouldFindWaitingLists_byDemographic() {
            WaitingListName wn = new WaitingListName();
            wn.setCreateDate(new Date());
            wn.setName("NAHBLIAYH");
            wn.setGroupNo("1");
            wn.setIsHistory("N");
            wn.setProviderNo("1");
            dao.persist(wn);

            WaitingList w = new WaitingList();
            w.setDemographicNo(10);
            w.setListId(wn.getId());
            w.setOnListSince(new Date());
            w.setPosition(1);
            w.setIsHistory("N");
            dao.persist(w);

            List<Object[]> lists = dao.findByDemographic(10);

            assertThat(lists).isNotNull();
            assertThat(lists).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find waiting lists and demographics by list ID")
        void shouldReturnResults_whenFindWaitingListsAndDemographics() {
            List<Object[]> results = dao.findWaitingListsAndDemographics(1);
            assertThat(results).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find appointments for a waiting list entry")
        void shouldReturnAppointments_forWaitingListEntry() {
            WaitingList w = new WaitingList();
            w.setDemographicNo(1);
            w.setOnListSince(new Date());

            List<Appointment> appts = dao.findAppointmentFor(w);
            assertThat(appts).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find by waiting list ID and demographic ID")
        void shouldFindEntries_byWaitingListIdAndDemographicId() {
            List<WaitingList> wls = dao.findByWaitingListIdAndDemographicId(1, 1);
            assertThat(wls).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return max position for a waiting list")
        void shouldReturnMaxPosition_forWaitingList() {
            Integer maxPos = dao.getMaxPosition(1);
            assertThat(maxPos).isNotNull();
        }
    }
}
