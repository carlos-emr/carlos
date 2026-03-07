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
import io.github.carlos_emr.carlos.commn.model.OtherId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link OtherIdDAO} covering CRUD operations,
 * getOtherId, searchTable, and save.
 *
 * <p>Migrated from legacy {@code OtherIdDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see OtherIdDAO
 */
@DisplayName("OtherId Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class OtherIdDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private OtherIdDAO otherIdDAO;

    private OtherId createOtherId(int tableName, String tableId, String otherKey, String otherIdValue) {
        OtherId entity = new OtherId(tableName, tableId, otherKey, otherIdValue);
        entity.setDeleted(false);
        otherIdDAO.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist otherid with generated ID")
        void shouldPersistOtherId_whenValidDataProvided() {
            OtherId entity = createOtherId(1, "100", "KEY1", "VAL1");

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find otherid by ID with correct field values")
        void shouldFindOtherId_whenValidIdProvided() {
            OtherId saved = createOtherId(2, "200", "KEY2", "VAL2");

            OtherId found = otherIdDAO.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getTableName()).isEqualTo(2);
            assertThat(found.getTableId()).isEqualTo("200");
            assertThat(found.getOtherKey()).isEqualTo("KEY2");
            assertThat(found.getOtherId()).isEqualTo("VAL2");
        }
    }

    @Nested
    @DisplayName("getOtherId")
    class GetOtherId {

        @Test
        @Tag("query")
        @DisplayName("should return OtherId matching tableName, tableId, and otherKey")
        void shouldReturnOtherId_whenAllParametersMatch() {
            createOtherId(3, "300", "MATCH_KEY", "MATCH_VAL");
            createOtherId(3, "300", "OTHER_KEY", "OTHER_VAL");

            OtherId result = otherIdDAO.getOtherId(3, "300", "MATCH_KEY");

            assertThat(result).isNotNull();
            assertThat(result.getOtherKey()).isEqualTo("MATCH_KEY");
            assertThat(result.getOtherId()).isEqualTo("MATCH_VAL");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching OtherId exists")
        void shouldReturnNull_whenNoMatchingOtherId() {
            OtherId result = otherIdDAO.getOtherId(999, "999", "NOPE");

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should not return deleted OtherId records")
        void shouldNotReturnDeleted_whenOtherIdIsDeleted() {
            OtherId entity = createOtherId(4, "400", "DEL_KEY", "DEL_VAL");
            entity.setDeleted(true);
            otherIdDAO.merge(entity);

            OtherId result = otherIdDAO.getOtherId(4, "400", "DEL_KEY");

            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should accept Integer tableId parameter")
        void shouldAcceptIntegerTableId_whenCalledWithIntegerOverload() {
            createOtherId(5, "500", "INT_KEY", "INT_VAL");

            OtherId result = otherIdDAO.getOtherId(5, 500, "INT_KEY");

            assertThat(result).isNotNull();
            assertThat(result.getOtherId()).isEqualTo("INT_VAL");
        }
    }

    @Nested
    @DisplayName("searchTable")
    class SearchTable {

        @Test
        @Tag("query")
        @DisplayName("should return OtherId matching tableName, otherKey, and otherValue")
        void shouldReturnOtherId_whenSearchCriteriaMatch() {
            createOtherId(6, "600", "SRCH_KEY", "SRCH_VAL");
            createOtherId(6, "601", "SRCH_KEY", "DIFF_VAL");

            OtherId result = otherIdDAO.searchTable(6, "SRCH_KEY", "SRCH_VAL");

            assertThat(result).isNotNull();
            assertThat(result.getTableId()).isEqualTo("600");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no matching search result")
        void shouldReturnNull_whenNoMatchingSearchResult() {
            OtherId result = otherIdDAO.searchTable(999, "NO_KEY", "NO_VAL");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @Tag("create")
        @DisplayName("should persist new OtherId when ID is null")
        void shouldPersistNewOtherId_whenIdIsNull() {
            OtherId entity = new OtherId(7, "700", "SAVE_KEY", "SAVE_VAL");
            entity.setDeleted(false);

            otherIdDAO.save(entity);

            assertThat(entity.getId()).isPositive();

            OtherId found = otherIdDAO.find(entity.getId());
            assertThat(found.getOtherKey()).isEqualTo("SAVE_KEY");
        }

        @Test
        @Tag("update")
        @DisplayName("should merge existing OtherId when ID is set")
        void shouldMergeOtherId_whenIdIsSet() {
            OtherId entity = createOtherId(8, "800", "UPD_KEY", "OLD_VAL");
            Integer savedId = entity.getId();

            entity.setOtherId("NEW_VAL");
            otherIdDAO.save(entity);

            OtherId found = otherIdDAO.find(savedId);
            assertThat(found.getOtherId()).isEqualTo("NEW_VAL");
        }
    }
}
