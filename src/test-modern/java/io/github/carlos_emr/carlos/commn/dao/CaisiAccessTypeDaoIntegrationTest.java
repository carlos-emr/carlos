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
import io.github.carlos_emr.carlos.commn.model.CaisiAccessType;
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
 * Integration tests for {@link CaisiAccessTypeDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code CaisiAccessTypeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CaisiAccessTypeDao
 */
@DisplayName("CaisiAccessType Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class CaisiAccessTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CaisiAccessTypeDao caisiAccessTypeDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist caisiaccesstype with generated ID")
        void shouldPersistCaisiAccessType_whenValidDataProvided() {
            CaisiAccessType entity = new CaisiAccessType();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            caisiAccessTypeDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find caisiaccesstype by ID")
        void shouldFindCaisiAccessType_whenValidIdProvided() {
            CaisiAccessType saved = new CaisiAccessType();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            caisiAccessTypeDao.persist(saved);
            CaisiAccessType found = caisiAccessTypeDao.find(saved.getId());
            assertThat(found).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all caisiaccesstype records")
        void shouldCountAllCaisiAccessTypes() {
            CaisiAccessType entity = new CaisiAccessType();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            caisiAccessTypeDao.persist(entity);
            long count = caisiAccessTypeDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
