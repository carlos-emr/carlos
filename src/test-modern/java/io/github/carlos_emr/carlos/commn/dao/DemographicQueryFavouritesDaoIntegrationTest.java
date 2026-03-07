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
import io.github.carlos_emr.carlos.commn.model.DemographicQueryFavourite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicQueryFavouritesDao} covering persist
 * and findByArchived queries.
 *
 * <p>Migrated from legacy {@code DemographicQueryFavouritesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicQueryFavouritesDao
 */
@DisplayName("DemographicQueryFavouritesDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicQueryFavouritesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicQueryFavouritesDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist favourite with generated ID")
    void shouldPersistFavourite_whenValidDataProvided() {
        DemographicQueryFavourite entity = new DemographicQueryFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("query")
    @DisplayName("should return favourites matching archived status")
    void shouldReturnFavourites_forMatchingArchivedStatus() {
        DemographicQueryFavourite entity1 = new DemographicQueryFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setArchived("1");
        dao.persist(entity1);

        DemographicQueryFavourite entity2 = new DemographicQueryFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setArchived("1");
        dao.persist(entity2);

        DemographicQueryFavourite entity3 = new DemographicQueryFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity3);
        entity3.setArchived("0");
        dao.persist(entity3);

        hibernateTemplate.flush();

        List<DemographicQueryFavourite> result = dao.findByArchived("1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(f -> "1".equals(f.getArchived()));
    }

    @Test
    @Tag("query")
    @DisplayName("should return empty list when no favourites match archived status")
    void shouldReturnEmptyList_whenNoFavouritesMatchArchivedStatus() {
        DemographicQueryFavourite entity = new DemographicQueryFavourite();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setArchived("0");
        dao.persist(entity);
        hibernateTemplate.flush();

        List<DemographicQueryFavourite> result = dao.findByArchived("1");

        assertThat(result).isEmpty();
    }
}
