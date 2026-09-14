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

import io.github.carlos_emr.carlos.commn.model.ReportByExamplesFavorite;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportByExamplesFavoriteDao} covering persist,
 * provider-scoped lookup, findByEverything, and findByProvider.
 *
 * <p>Migrated from legacy {@code ReportByExamplesFavoriteDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportByExamplesFavoriteDao
 */
@DisplayName("ReportByExamplesFavorite Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("reporting")
@Transactional
public class ReportByExamplesFavoriteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportByExamplesFavoriteDao dao;

    private ReportByExamplesFavorite createFavorite(String providerNo, String name, String query) {
        ReportByExamplesFavorite entity = new ReportByExamplesFavorite();
        entity.setProviderNo(providerNo);
        entity.setName(name);
        entity.setQuery(query);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist report by examples favorite with generated ID")
        void shouldPersistReportByExamplesFavorite_whenValidDataProvided() {
            ReportByExamplesFavorite entity = createFavorite("100001", "MyFav", "SELECT * FROM demographic");

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find favorite by ID with correct field values")
        void shouldFindFavorite_whenValidIdProvided() {
            ReportByExamplesFavorite saved = createFavorite("100002", "TestFav", "test query");

            ReportByExamplesFavorite found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getName()).isEqualTo("TestFav");
            assertThat(found.getQuery()).isEqualTo("test query");
        }
    }

    @Nested
    @DisplayName("findByProviderAndQuery")
    class FindByProviderAndQuery {

        @Test
        @Tag("query")
        @DisplayName("should not return another provider's matching query")
        void shouldReturnOnlyCurrentProviderFavorites_whenQueryMatches() {
            createFavorite("200010", "Mine", "SELECT appointments");
            createFavorite("200011", "Theirs", "SELECT appointments");

            List<ReportByExamplesFavorite> results =
                    dao.findByProviderAndQuery("200010", "SELECT appointments");

            assertThat(results).singleElement()
                    .satisfies(favorite -> assertThat(favorite.getName()).isEqualTo("Mine"));
        }
    }

    @Nested
    @DisplayName("findByEverything")
    class FindByEverything {

        @Test
        @Tag("query")
        @DisplayName("should require provider, name, and query to match")
        void shouldReturnFavorites_whenAllFieldsMatch() {
            createFavorite("300001", "MatchFav", "some query");
            createFavorite("300001", "OtherFav", "other query");
            createFavorite("300002", "MatchFav", "diff query");

            List<ReportByExamplesFavorite> results = dao.findByEverything("300001", "MatchFav", "some query");

            assertThat(results).singleElement()
                    .satisfies(favorite -> assertThat(favorite.getProviderNo()).isEqualTo("300001"));
        }

        @Test
        @Tag("query")
        @DisplayName("should not return another provider's matching query")
        void shouldReturnEmpty_whenOnlyQueryMatches() {
            createFavorite("300003", "SomeFav", "unique query string");

            List<ReportByExamplesFavorite> results = dao.findByEverything("NOPROVIDER", "NONAME", "unique query string");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProvider")
    class FindByProvider {

        @Test
        @Tag("query")
        @DisplayName("should return all favorites for matching provider")
        void shouldReturnAllFavorites_whenProviderMatches() {
            createFavorite("400001", "Fav1", "query1");
            createFavorite("400001", "Fav2", "query2");
            createFavorite("400002", "Fav3", "query3");

            List<ReportByExamplesFavorite> results = dao.findByProvider("400001");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(f -> f.getProviderNo().equals("400001"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no favorites for provider")
        void shouldReturnEmptyList_whenNoFavoritesForProvider() {
            List<ReportByExamplesFavorite> results = dao.findByProvider("999999");

            assertThat(results).isEmpty();
        }
    }
}
