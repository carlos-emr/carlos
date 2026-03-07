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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CSSStylesDAO} covering findAll with active
 * status filtering and ordering.
 *
 * <p>Migrated from legacy {@code CSSStylesDAOTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see CSSStylesDAO
 */
@DisplayName("CSSStylesDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CSSStylesDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private CSSStylesDAO dao;

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return only active styles and exclude deleted ones")
        void shouldReturnOnlyActiveStyles_excludingDeletedOnes() {
            CssStyle style1 = new CssStyle();
            style1.setName("Style1");
            style1.setStatus(CssStyle.ACTIVE);

            CssStyle style2 = new CssStyle();
            style2.setName("Style2");
            style2.setStatus(CssStyle.DELETED);

            CssStyle style3 = new CssStyle();
            style3.setName("Style3");
            style3.setStatus(CssStyle.ACTIVE);

            dao.persist(style1);
            dao.persist(style2);
            dao.persist(style3);

            List<CssStyle> result = dao.findAll();

            assertThat(result).contains(style1, style3);
            assertThat(result).doesNotContain(style2);
        }

        @Test
        @Tag("read")
        @DisplayName("should return styles ordered by name")
        void shouldReturnStyles_orderedByName() {
            CssStyle styleC = new CssStyle();
            styleC.setName("CStyle");
            styleC.setStatus(CssStyle.ACTIVE);

            CssStyle styleA = new CssStyle();
            styleA.setName("AStyle");
            styleA.setStatus(CssStyle.ACTIVE);

            CssStyle styleB = new CssStyle();
            styleB.setName("BStyle");
            styleB.setStatus(CssStyle.ACTIVE);

            dao.persist(styleC);
            dao.persist(styleA);
            dao.persist(styleB);

            List<CssStyle> result = dao.findAll();

            assertThat(result).extracting(CssStyle::getName).isSorted();
        }
    }
}
