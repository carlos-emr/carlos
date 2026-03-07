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
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
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
 * Integration tests for {@link FaxClientLogDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code FaxClientLogDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FaxClientLogDao
 */
@DisplayName("FaxClientLog Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("fax")
@Transactional
public class FaxClientLogDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FaxClientLogDao faxClientLogDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist faxclientlog with generated ID")
        void shouldPersistFaxClientLog_whenValidDataProvided() {
            FaxClientLog entity = new FaxClientLog();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            faxClientLogDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find faxclientlog by ID")
        void shouldFindFaxClientLog_whenValidIdProvided() {
            FaxClientLog saved = new FaxClientLog();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            faxClientLogDao.persist(saved);
            FaxClientLog found = dao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all faxclientlog records")
        void shouldCountAllFaxClientLogs() {
            FaxClientLog entity = new FaxClientLog();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            faxClientLogDao.persist(entity);
            long count = faxClientLogDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
