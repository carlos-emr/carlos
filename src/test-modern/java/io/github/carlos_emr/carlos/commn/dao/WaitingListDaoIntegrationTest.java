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

            assertThat(entity.getId()).isPositive();
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

            assertThat(lists).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no waiting lists match demographic")
        void shouldReturnEmptyList_whenNoDemographicMatch() {
            List<Object[]> lists = dao.findByDemographic(999999);
            assertThat(lists).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for waiting lists and demographics with no matching data")
        void shouldReturnEmptyList_whenNoWaitingListsAndDemographicsMatch() {
            List<Object[]> results = dao.findWaitingListsAndDemographics(999999);
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty appointments when no appointments match waiting list")
        void shouldReturnEmptyList_whenNoAppointmentsForWaitingList() {
            WaitingList w = new WaitingList();
            w.setDemographicNo(1);
            w.setOnListSince(new Date());

            List<Appointment> appts = dao.findAppointmentFor(w);
            assertThat(appts).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no entries match waiting list ID and demographic ID")
        void shouldReturnEmptyList_whenNoMatchByWaitingListIdAndDemographicId() {
            List<WaitingList> wls = dao.findByWaitingListIdAndDemographicId(999999, 999999);
            assertThat(wls).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find entries by waiting list ID and demographic ID")
        void shouldFindEntries_byWaitingListIdAndDemographicId() {
            WaitingList w1 = new WaitingList();
            w1.setDemographicNo(100);
            w1.setListId(50);
            w1.setOnListSince(new Date());
            w1.setPosition(1);
            w1.setIsHistory("N");
            dao.persist(w1);

            WaitingList w2 = new WaitingList();
            w2.setDemographicNo(200);
            w2.setListId(50);
            w2.setOnListSince(new Date());
            w2.setPosition(2);
            w2.setIsHistory("N");
            dao.persist(w2);

            hibernateTemplate.flush();

            List<WaitingList> results = dao.findByWaitingListIdAndDemographicId(50, 100);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDemographicNo()).isEqualTo(100);
            assertThat(results.get(0).getListId()).isEqualTo(50);
        }

        @Test
        @Tag("query")
        @DisplayName("should return zero max position when no entries exist for list")
        void shouldReturnZero_whenNoEntriesForMaxPosition() {
            Integer maxPos = dao.getMaxPosition(999999);
            assertThat(maxPos).isEqualTo(0);
        }

        @Test
        @Tag("query")
        @DisplayName("should return correct max position for waiting list")
        void shouldReturnCorrectMaxPosition_whenEntriesExist() {
            WaitingList w1 = new WaitingList();
            w1.setDemographicNo(10);
            w1.setListId(77);
            w1.setOnListSince(new Date());
            w1.setPosition(3);
            w1.setIsHistory("N");
            dao.persist(w1);

            WaitingList w2 = new WaitingList();
            w2.setDemographicNo(20);
            w2.setListId(77);
            w2.setOnListSince(new Date());
            w2.setPosition(7);
            w2.setIsHistory("N");
            dao.persist(w2);

            WaitingList w3 = new WaitingList();
            w3.setDemographicNo(30);
            w3.setListId(77);
            w3.setOnListSince(new Date());
            w3.setPosition(5);
            w3.setIsHistory("N");
            dao.persist(w3);

            // This entry is history, so should be excluded from max position
            WaitingList wHistory = new WaitingList();
            wHistory.setDemographicNo(40);
            wHistory.setListId(77);
            wHistory.setOnListSince(new Date());
            wHistory.setPosition(99);
            wHistory.setIsHistory("Y");
            dao.persist(wHistory);

            hibernateTemplate.flush();

            Integer maxPos = dao.getMaxPosition(77);
            assertThat(maxPos).isEqualTo(7);
        }
    }
}
