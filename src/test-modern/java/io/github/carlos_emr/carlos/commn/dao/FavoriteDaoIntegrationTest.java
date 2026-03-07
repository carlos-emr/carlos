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
import io.github.carlos_emr.carlos.commn.model.Favorite;
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
 * Integration tests for {@link FavoriteDao} covering persist, findByProviderNo,
 * and findByEverything operations.
 *
 * @since 2026-03-07
 * @see FavoriteDao
 */
@DisplayName("Favorite Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class FavoriteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FavoriteDao dao;

    /**
     * Helper to create a Favorite with specific provider number and name.
     */
    private Favorite createFavorite(String providerNo, String name) {
        Favorite entity = new Favorite();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setProviderNo(providerNo);
        entity.setName(name);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity and assign generated ID")
        void shouldPersistEntity_withGeneratedId() {
            Favorite entity = new Favorite();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should find favorites by provider number")
        void shouldFindFavorites_byProviderNo() {
            createFavorite("P001", "Aspirin");
            createFavorite("P001", "Ibuprofen");
            createFavorite("P002", "Tylenol");
            hibernateTemplate.flush();

            List<Favorite> result = dao.findByProviderNo("P001");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(f -> f.getProviderNo().equals("P001"));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when provider has no favorites")
        void shouldReturnEmptyList_whenProviderHasNoFavorites() {
            List<Favorite> result = dao.findByProviderNo("NONEXISTENT");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should find exact match with findByEverything")
        void shouldFindExactMatch_withFindByEverything() {
            Favorite fav = new Favorite();
            fav.setProviderNo("P100");
            fav.setName("TestFav");
            fav.setBn("BrandX");
            fav.setGcnSeqno("GCN001");
            fav.setCustomName("Custom1");
            fav.setTakeMin(1.0f);
            fav.setTakeMax(2.0f);
            fav.setFrequencyCode("BID");
            fav.setDuration("7");
            fav.setDurationUnit("days");
            fav.setQuantity("30");
            fav.setRepeat(3);
            fav.setNosubs(false);
            fav.setPrn(false);
            fav.setSpecial("Take with food");
            fav.setGn("GenericX");
            fav.setUnitName("mg");
            fav.setCustomInstructions(false);
            dao.persist(fav);
            hibernateTemplate.flush();

            Favorite found = dao.findByEverything(
                    "P100", "TestFav", "BrandX", "GCN001", "Custom1",
                    1.0f, 2.0f, "BID", "7", "days", "30", 3,
                    false, false, "Take with food", "GenericX", "mg", false);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(fav.getId());
            assertThat(found.getProviderNo()).isEqualTo("P100");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null from findByEverything when no exact match")
        void shouldReturnNull_whenNoExactMatchInFindByEverything() {
            Favorite found = dao.findByEverything(
                    "NONE", "NONE", "NONE", "NONE", "NONE",
                    0, 0, "NONE", "NONE", "NONE", "NONE", 0,
                    false, false, "NONE", "NONE", "NONE", false);

            assertThat(found).isNull();
        }
    }
}
