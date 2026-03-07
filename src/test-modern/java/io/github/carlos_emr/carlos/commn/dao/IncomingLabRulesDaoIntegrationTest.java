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
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link IncomingLabRulesDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code IncomingLabRulesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see IncomingLabRulesDao
 */
@DisplayName("IncomingLabRules Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class IncomingLabRulesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private IncomingLabRulesDao incomingLabRulesDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist incominglabrules with generated ID")
        void shouldPersistIncomingLabRules_whenValidDataProvided() {
            IncomingLabRules entity = new IncomingLabRules();
            incomingLabRulesDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find incominglabrules by ID")
        void shouldFindIncomingLabRules_whenValidIdProvided() {
            IncomingLabRules saved = new IncomingLabRules();
            incomingLabRulesDao.persist(saved);
            IncomingLabRules found = incomingLabRulesDao.find(saved.getId());
            assertThat(found).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all incominglabrules records")
        void shouldCountAllIncomingLabRuless() {
            IncomingLabRules entity = new IncomingLabRules();
            incomingLabRulesDao.persist(entity);
            long count = incomingLabRulesDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
