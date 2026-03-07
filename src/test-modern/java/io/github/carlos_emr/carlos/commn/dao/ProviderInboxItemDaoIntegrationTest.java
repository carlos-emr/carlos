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
import io.github.carlos_emr.carlos.commn.model.ProviderInboxItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderInboxRoutingDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code ProviderInboxItemDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderInboxRoutingDao
 */
@DisplayName("ProviderInboxItem Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderInboxItemDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist providerinboxitem with generated ID")
        void shouldPersistProviderInboxItem_whenValidDataProvided() {
            ProviderInboxItem entity = new ProviderInboxItem();
            providerInboxRoutingDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find providerinboxitem by ID")
        void shouldFindProviderInboxItem_whenValidIdProvided() {
            ProviderInboxItem saved = new ProviderInboxItem();
            providerInboxRoutingDao.persist(saved);
            ProviderInboxItem found = providerInboxRoutingDao.find(saved.getId());
            assertThat(found).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all providerinboxitem records")
        void shouldCountAllProviderInboxItems() {
            ProviderInboxItem entity = new ProviderInboxItem();
            providerInboxRoutingDao.persist(entity);
            long count = providerInboxRoutingDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }
}
