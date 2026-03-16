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
import io.github.carlos_emr.carlos.commn.model.MyGroupAccessRestriction;
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
 * Integration tests for {@link MyGroupAccessRestrictionDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code MyGroupAccessRestrictionDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MyGroupAccessRestrictionDao
 */
@DisplayName("MyGroupAccessRestriction Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class MyGroupAccessRestrictionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MyGroupAccessRestrictionDao myGroupAccessRestrictionDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist mygroupaccessrestriction with generated ID")
        void shouldPersistMyGroupAccessRestriction_whenValidDataProvided() throws Exception {
            MyGroupAccessRestriction entity = new MyGroupAccessRestriction();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            myGroupAccessRestrictionDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find mygroupaccessrestriction by ID")
        void shouldFindMyGroupAccessRestriction_whenValidIdProvided() throws Exception {
            MyGroupAccessRestriction saved = new MyGroupAccessRestriction();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            myGroupAccessRestrictionDao.persist(saved);
            MyGroupAccessRestriction found = myGroupAccessRestrictionDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all mygroupaccessrestriction records")
        void shouldCountAllMyGroupAccessRestrictions() throws Exception {
            MyGroupAccessRestriction entity = new MyGroupAccessRestriction();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            myGroupAccessRestrictionDao.persist(entity);
            long count = myGroupAccessRestrictionDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
