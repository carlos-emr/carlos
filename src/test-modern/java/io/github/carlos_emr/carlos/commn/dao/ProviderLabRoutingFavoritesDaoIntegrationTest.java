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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProviderLabRoutingFavoritesDao} covering
 * favorite lab routing retrieval by provider.
 *
 * <p>Migrated from legacy {@code ProviderLabRoutingFavoritesDaoTest}
 * (JUnit 4 / DaoTestFixtures) with BDD-style naming and AssertJ assertions.</p>
 *
 * @since 2026-03-07
 * @see ProviderLabRoutingFavoritesDao
 */
@DisplayName("ProviderLabRoutingFavoritesDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class ProviderLabRoutingFavoritesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderLabRoutingFavoritesDao dao;

    private ProviderLabRoutingFavorite createFavorite(String providerNo) {
        ProviderLabRoutingFavorite fav = new ProviderLabRoutingFavorite();
        EntityDataGenerator.generateTestDataForModelClass(fav);
        fav.setProvider_no(providerNo);
        dao.persist(fav);
        hibernateTemplate.flush();
        return fav;
    }

    @Test
    @Tag("query")
    @DisplayName("should return favorites for specific provider only")
    void shouldReturnFavorites_forSpecificProviderOnly() {
        // Given
        String providerNo1 = "100";
        String providerNo2 = "200";

        ProviderLabRoutingFavorite fav1 = createFavorite(providerNo1);
        createFavorite(providerNo2);
        ProviderLabRoutingFavorite fav3 = createFavorite(providerNo1);

        // When
        List<ProviderLabRoutingFavorite> result = dao.findFavorites(providerNo1);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProviderLabRoutingFavorite::getId)
                .containsExactly(fav1.getId(), fav3.getId());
    }

    @Test
    @Tag("query")
    @DisplayName("should return empty list when provider has no favorites")
    void shouldReturnEmptyList_whenProviderHasNoFavorites() {
        // Given
        createFavorite("100");

        // When
        List<ProviderLabRoutingFavorite> result = dao.findFavorites("999");

        // Then
        assertThat(result).isEmpty();
    }
}
