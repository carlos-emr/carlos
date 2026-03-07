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
import io.github.carlos_emr.carlos.commn.model.LookupList;
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
 * Integration tests for {@link LookupListDao} covering create, findAllActive, and findByName.
 *
 * <p>Migrated from legacy {@code LookupListDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LookupListDao
 */
@DisplayName("LookupList Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class LookupListDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LookupListDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist lookup list with generated ID")
        void shouldPersistLookupList_whenValidDataProvided() {
            LookupList entity = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findAllActive")
    class FindAllActive {

        @Test
        @Tag("read")
        @DisplayName("should return only active lookup lists ordered by name")
        void shouldReturnActiveLists_orderedByName() {
            LookupList ll1 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll1);
            ll1.setActive(true);
            ll1.setName("bravo");
            dao.persist(ll1);

            LookupList ll2 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll2);
            ll2.setActive(false);
            ll2.setName("bravo");
            dao.persist(ll2);

            LookupList ll3 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll3);
            ll3.setActive(true);
            ll3.setName("charlie");
            dao.persist(ll3);

            LookupList ll4 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll4);
            ll4.setActive(true);
            ll4.setName("alpha");
            dao.persist(ll4);

            List<LookupList> result = dao.findAllActive();

            assertThat(result).hasSizeGreaterThanOrEqualTo(3);
            List<LookupList> firstThree = result.subList(0, 3);
            assertThat(firstThree).containsExactly(ll4, ll1, ll3);
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        @Tag("read")
        @DisplayName("should return lookup list matching the given name")
        void shouldReturnLookupList_whenNameMatches() {
            LookupList ll1 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll1);
            ll1.setName("bravo");
            dao.persist(ll1);

            LookupList ll2 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll2);
            ll2.setName("alpha");
            dao.persist(ll2);

            LookupList ll3 = new LookupList();
            EntityDataGenerator.generateTestDataForModelClass(ll3);
            ll3.setName("charlie");
            dao.persist(ll3);

            LookupList result = dao.findByName("alpha");

            assertThat(result).isEqualTo(ll2);
        }
    }
}
