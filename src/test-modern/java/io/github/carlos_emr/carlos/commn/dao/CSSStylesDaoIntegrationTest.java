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
import io.github.carlos_emr.carlos.commn.model.CssStyle;
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
 * Integration tests for {@link CSSStylesDAO} covering CRUD operations
 * and the findAll method which filters by active status.
 *
 * <p>Migrated from legacy {@code CSSStylesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see CSSStylesDAO
 */
@DisplayName("CSSStyles Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class CSSStylesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CSSStylesDAO cSSStylesDAO;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist CssStyle with generated ID")
        void shouldPersistCssStyle_whenValidDataProvided() {
            CssStyle entity = new CssStyle();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            cSSStylesDAO.persist(entity);
            assertThat(entity.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find CssStyle by ID with correct field values")
        void shouldFindCssStyle_whenValidIdProvided() {
            CssStyle saved = new CssStyle();
            saved.setName("TestStyle");
            saved.setStyle("body { color: red; }");
            saved.setStatus(CssStyle.ACTIVE);
            cSSStylesDAO.persist(saved);

            CssStyle found = cSSStylesDAO.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getName()).isEqualTo("TestStyle");
            assertThat(found.getStyle()).isEqualTo("body { color: red; }");
            assertThat(found.getStatus()).isEqualTo(CssStyle.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find only active styles, ordered by name")
        void shouldFindAllActiveStyles_orderedByName() {
            CssStyle activeB = new CssStyle();
            activeB.setName("BetaStyle");
            activeB.setStyle(".beta { }");
            activeB.setStatus(CssStyle.ACTIVE);
            cSSStylesDAO.persist(activeB);

            CssStyle activeA = new CssStyle();
            activeA.setName("AlphaStyle");
            activeA.setStyle(".alpha { }");
            activeA.setStatus(CssStyle.ACTIVE);
            cSSStylesDAO.persist(activeA);

            CssStyle deleted = new CssStyle();
            deleted.setName("DeletedStyle");
            deleted.setStyle(".deleted { }");
            deleted.setStatus(CssStyle.DELETED);
            cSSStylesDAO.persist(deleted);

            List<CssStyle> results = cSSStylesDAO.findAll();

            // Should exclude deleted, return only active, ordered by name
            assertThat(results).hasSize(2);
            assertThat(results).extracting(CssStyle::getStatus)
                    .containsOnly(CssStyle.ACTIVE);
            // Verify ordering by name
            assertThat(results.get(0).getName()).isEqualTo("AlphaStyle");
            assertThat(results.get(1).getName()).isEqualTo("BetaStyle");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no active styles exist")
        void shouldReturnEmptyList_whenNoActiveStylesExist() {
            CssStyle deleted = new CssStyle();
            deleted.setName("OnlyDeleted");
            deleted.setStyle(".d { }");
            deleted.setStatus(CssStyle.DELETED);
            cSSStylesDAO.persist(deleted);

            List<CssStyle> results = cSSStylesDAO.findAll();

            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should count all CssStyle records including deleted")
        void shouldCountAllCssStyles() {
            CssStyle active = new CssStyle();
            active.setName("Active");
            active.setStyle(".a { }");
            active.setStatus(CssStyle.ACTIVE);
            cSSStylesDAO.persist(active);

            CssStyle deleted = new CssStyle();
            deleted.setName("Deleted");
            deleted.setStyle(".d { }");
            deleted.setStatus(CssStyle.DELETED);
            cSSStylesDAO.persist(deleted);

            long count = cSSStylesDAO.getCountAll();

            // getCountAll counts all records regardless of status
            assertThat(count).isEqualTo(2);
        }
    }
}
