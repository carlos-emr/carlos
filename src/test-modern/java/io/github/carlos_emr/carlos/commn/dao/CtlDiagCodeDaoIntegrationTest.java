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
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
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
 * Integration tests for {@link CtlDiagCodeDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code CtlDiagCodeDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CtlDiagCodeDao
 */
@DisplayName("CtlDiagCode Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class CtlDiagCodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlDiagCodeDao ctlDiagCodeDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist ctldiagcode with generated ID")
        void shouldPersistCtlDiagCode_whenValidDataProvided() throws Exception {
            CtlDiagCode entity = new CtlDiagCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            ctlDiagCodeDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find ctldiagcode by ID")
        void shouldFindCtlDiagCode_whenValidIdProvided() throws Exception {
            CtlDiagCode saved = new CtlDiagCode();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            ctlDiagCodeDao.persist(saved);
            CtlDiagCode found = ctlDiagCodeDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all ctldiagcode records")
        void shouldCountAllCtlDiagCodes() throws Exception {
            CtlDiagCode entity = new CtlDiagCode();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            ctlDiagCodeDao.persist(entity);
            long count = ctlDiagCodeDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
