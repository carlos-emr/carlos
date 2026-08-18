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
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WaitingListNameDao} covering
 * countActiveWatingListNames, findCurrentByNameAndGroup, and findByMyGroups.
 *
 * <p>Migrated from legacy {@code WaitingListNameDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see WaitingListNameDao
 */
@DisplayName("WaitingListName Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class WaitingListNameDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WaitingListNameDao dao;

    @Autowired
    private MyGroupDao myGroupDao;

    @Nested
    @DisplayName("countActiveWatingListNames")
    class CountActiveWaitingListNames {

        @Test
        @Tag("aggregate")
        @DisplayName("should count only non-history waiting list names")
        void shouldCountActiveNames_whenMixOfHistoryAndNonHistory() throws Exception {
            WaitingListName wl1 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl1);
            wl1.setIsHistory("N");
            dao.persist(wl1);

            WaitingListName wl2 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl2);
            wl2.setIsHistory("Y");
            dao.persist(wl2);

            WaitingListName wl3 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl3);
            wl3.setIsHistory("N");
            dao.persist(wl3);

            WaitingListName wl4 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl4);
            wl4.setIsHistory("N");
            dao.persist(wl4);

            long result = dao.countActiveWatingListNames();

            assertThat(result).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findCurrentByNameAndGroup")
    class FindCurrentByNameAndGroup {

        @Test
        @Tag("search")
        @DisplayName("should return non-history entries matching name and group")
        void shouldReturnMatchingEntries_whenFilteredByNameAndGroup() throws Exception {
            String name1 = "alpha";
            String name2 = "bravo";
            String groupNo1 = "101";
            String groupNo2 = "202";

            WaitingListName wl1 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl1);
            wl1.setName(name1);
            wl1.setGroupNo(groupNo1);
            wl1.setIsHistory("N");
            dao.persist(wl1);

            WaitingListName wl2 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl2);
            wl2.setName(name2);
            wl2.setGroupNo(groupNo1);
            wl2.setIsHistory("Y");
            dao.persist(wl2);

            WaitingListName wl3 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl3);
            wl3.setName(name1);
            wl3.setGroupNo(groupNo2);
            wl3.setIsHistory("N");
            dao.persist(wl3);

            WaitingListName wl4 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl4);
            wl4.setName(name1);
            wl4.setGroupNo(groupNo1);
            wl4.setIsHistory("N");
            dao.persist(wl4);

            WaitingListName wl5 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl5);
            wl5.setName(name1);
            wl5.setGroupNo(groupNo1);
            wl5.setIsHistory("Y");
            dao.persist(wl5);

            List<WaitingListName> result = dao.findCurrentByNameAndGroup(name1, groupNo1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(wl1, wl4);
        }
    }

    @Nested
    @DisplayName("findByMyGroups")
    class FindByMyGroups {

        @Test
        @Tag("search")
        @DisplayName("should return active waiting lists for provider groups sorted by name")
        void shouldReturnActiveEntries_forProviderGroups() throws Exception {
            String name1 = "charlie";
            String name2 = "bravo";
            String name3 = "delta";
            String name4 = "alpha";

            String myGroupNo1 = "101";
            String myGroupNo2 = "202";
            String myGroupNo3 = "303";
            String myGroupNo4 = "404";

            String providerNo1 = "111";
            String providerNo2 = "222";
            String providerNo3 = "333";
            String providerNo4 = "444";

            MyGroupPrimaryKey pk1 = new MyGroupPrimaryKey();
            pk1.setMyGroupNo(myGroupNo1);
            pk1.setProviderNo(providerNo1);
            MyGroup myGroup1 = new MyGroup();
            EntityDataGenerator.generateTestDataForModelClass(myGroup1);
            myGroup1.setId(pk1);
            myGroupDao.merge(myGroup1);

            MyGroupPrimaryKey pk2 = new MyGroupPrimaryKey();
            pk2.setMyGroupNo(myGroupNo2);
            pk2.setProviderNo(providerNo2);
            MyGroup myGroup2 = new MyGroup();
            EntityDataGenerator.generateTestDataForModelClass(myGroup2);
            myGroup2.setId(pk2);
            myGroupDao.merge(myGroup2);

            MyGroupPrimaryKey pk3 = new MyGroupPrimaryKey();
            pk3.setMyGroupNo(myGroupNo3);
            pk3.setProviderNo(providerNo3);
            MyGroup myGroup3 = new MyGroup();
            EntityDataGenerator.generateTestDataForModelClass(myGroup3);
            myGroup3.setId(pk3);
            myGroupDao.merge(myGroup3);

            MyGroupPrimaryKey pk4 = new MyGroupPrimaryKey();
            pk4.setMyGroupNo(myGroupNo4);
            pk4.setProviderNo(providerNo4);
            MyGroup myGroup4 = new MyGroup();
            EntityDataGenerator.generateTestDataForModelClass(myGroup4);
            myGroup4.setId(pk4);
            myGroupDao.merge(myGroup4);

            WaitingListName wl1 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl1);
            wl1.setGroupNo("101");
            wl1.setIsHistory("N");
            wl1.setName(name1);
            dao.persist(wl1);

            WaitingListName wl2 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl2);
            wl2.setGroupNo("202");
            wl2.setIsHistory("Y");
            wl2.setName(name2);
            dao.persist(wl2);

            WaitingListName wl3 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl3);
            wl3.setGroupNo("303");
            wl3.setIsHistory("Y");
            wl3.setName(name3);
            dao.persist(wl3);

            WaitingListName wl4 = new WaitingListName();
            EntityDataGenerator.generateTestDataForModelClass(wl4);
            wl4.setGroupNo("404");
            wl4.setIsHistory("N");
            wl4.setName(name4);
            dao.persist(wl4);

            List<MyGroup> myGroups = Arrays.asList(myGroup1, myGroup2, myGroup3, myGroup4);

            List<WaitingListName> result = dao.findByMyGroups(providerNo1, myGroups);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(wl4, wl1);
        }
    }
}
