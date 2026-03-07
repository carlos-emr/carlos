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
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;
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
 * Integration tests for {@link ProviderLabRoutingFavoritesDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code ProviderLabRoutingFavoriteDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderLabRoutingFavoritesDao
 */
@DisplayName("ProviderLabRoutingFavorite Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderLabRoutingFavoriteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderLabRoutingFavoritesDao providerLabRoutingFavoritesDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist providerlabroutingfavorite with generated ID")
        void shouldPersistProviderLabRoutingFavorite_whenValidDataProvided() {
            ProviderLabRoutingFavorite entity = new ProviderLabRoutingFavorite();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            providerLabRoutingFavoritesDao.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find providerlabroutingfavorite by ID")
        void shouldFindProviderLabRoutingFavorite_whenValidIdProvided() {
            ProviderLabRoutingFavorite saved = new ProviderLabRoutingFavorite();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            providerLabRoutingFavoritesDao.persist(saved);
            ProviderLabRoutingFavorite found = providerLabRoutingFavoritesDao.find(saved.getId());
            assertThat(found).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find favorites by provider number")
        void shouldFindFavorites_byProviderNo() {
            String providerNo1 = "100";
            String providerNo2 = "200";

            ProviderLabRoutingFavorite fav1 = new ProviderLabRoutingFavorite();
            EntityDataGenerator.generateTestDataForModelClass(fav1);
            fav1.setProvider_no(providerNo1);
            providerLabRoutingFavoritesDao.persist(fav1);

            ProviderLabRoutingFavorite fav2 = new ProviderLabRoutingFavorite();
            EntityDataGenerator.generateTestDataForModelClass(fav2);
            fav2.setProvider_no(providerNo2);
            providerLabRoutingFavoritesDao.persist(fav2);

            ProviderLabRoutingFavorite fav3 = new ProviderLabRoutingFavorite();
            EntityDataGenerator.generateTestDataForModelClass(fav3);
            fav3.setProvider_no(providerNo1);
            providerLabRoutingFavoritesDao.persist(fav3);

            List<ProviderLabRoutingFavorite> result = providerLabRoutingFavoritesDao.findFavorites(providerNo1);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(fav1, fav3);
        }
    }
}
