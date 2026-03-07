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
 * findByQuery, findByEverything, and findByProvider.
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

            assertThat(entity.getId()).isNotNull();
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
    @DisplayName("findByQuery")
    class FindByQuery {

        @Test
        @Tag("query")
        @DisplayName("should return favorites with matching query string using LIKE")
        void shouldReturnFavorites_whenQueryMatches() {
            createFavorite("200001", "Fav1", "SELECT demographics");
            createFavorite("200002", "Fav2", "SELECT appointments");
            createFavorite("200003", "Fav3", "INSERT something");

            List<ReportByExamplesFavorite> results = dao.findByQuery("SELECT%");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(f -> f.getQuery().startsWith("SELECT"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no query matches")
        void shouldReturnEmptyList_whenNoQueryMatches() {
            createFavorite("200001", "Fav1", "SELECT something");

            List<ReportByExamplesFavorite> results = dao.findByQuery("NO_MATCH%");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByEverything")
    class FindByEverything {

        @Test
        @Tag("query")
        @DisplayName("should return favorites matching provider and name")
        void shouldReturnFavorites_whenProviderAndNameMatch() {
            createFavorite("300001", "MatchFav", "some query");
            createFavorite("300001", "OtherFav", "other query");
            createFavorite("300002", "MatchFav", "diff query");

            List<ReportByExamplesFavorite> results = dao.findByEverything("300001", "MatchFav", "NO_MATCH");

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(f ->
                    f.getProviderNo().equals("300001") && f.getName().equals("MatchFav"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return favorites matching query string via OR clause")
        void shouldReturnFavorites_whenQueryStringMatchesViaOr() {
            createFavorite("300003", "SomeFav", "unique query string");

            // The findByEverything method uses OR for query: providerNo = ?1 AND name LIKE ?2 OR query LIKE ?3
            List<ReportByExamplesFavorite> results = dao.findByEverything("NOPROVIDER", "NONAME", "unique query string");

            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(f -> f.getQuery().equals("unique query string"));
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
