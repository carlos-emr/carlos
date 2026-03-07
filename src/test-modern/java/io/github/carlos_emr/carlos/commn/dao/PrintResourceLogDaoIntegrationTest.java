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
import io.github.carlos_emr.carlos.commn.model.PrintResourceLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PrintResourceLogDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code PrintResourceLogDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PrintResourceLogDao
 */
@DisplayName("PrintResourceLog Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class PrintResourceLogDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PrintResourceLogDao printResourceLogDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist printresourcelog with generated ID")
        void shouldPersistPrintResourceLog_whenValidDataProvided() {
            PrintResourceLog entity = new PrintResourceLog();
            printResourceLogDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find printresourcelog by ID")
        void shouldFindPrintResourceLog_whenValidIdProvided() {
            PrintResourceLog saved = new PrintResourceLog();
            printResourceLogDao.persist(saved);
            PrintResourceLog found = printResourceLogDao.find(saved.getId());
            assertThat(found).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all printresourcelog records")
        void shouldCountAllPrintResourceLogs() {
            PrintResourceLog entity = new PrintResourceLog();
            printResourceLogDao.persist(entity);
            long count = printResourceLogDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
