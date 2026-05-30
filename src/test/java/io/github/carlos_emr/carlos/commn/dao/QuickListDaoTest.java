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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link QuickListDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code QuickListDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see QuickListDao
 */
@DisplayName("QuickList Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class QuickListDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private QuickListDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist quick list with generated ID")
    void shouldPersistQuickList_whenValidDataProvided() throws Exception {
        QuickList ql = new QuickList();
        EntityDataGenerator.generateTestDataForModelClass(ql);
        dao.persist(ql);

        assertThat(ql.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching entries when filtered by name, research code, and coding system")
    void shouldReturnMatchingEntries_whenFilteredByNameResearchCodeAndCodingSystem() throws Exception {
        String quickListName1 = "alpha";
        String quickListName2 = "bravo";
        String dxResearchCode1 = "111";
        String dxResearchCode2 = "222";
        String codingSystem1 = "101";
        String codingSystem2 = "202";

        QuickList quickList1 = new QuickList();
        EntityDataGenerator.generateTestDataForModelClass(quickList1);
        quickList1.setQuickListName(quickListName1);
        quickList1.setDxResearchCode(dxResearchCode1);
        quickList1.setCodingSystem(codingSystem1);
        dao.persist(quickList1);

        QuickList quickList2 = new QuickList();
        EntityDataGenerator.generateTestDataForModelClass(quickList2);
        quickList2.setQuickListName(quickListName2);
        quickList2.setDxResearchCode(dxResearchCode2);
        quickList2.setCodingSystem(codingSystem2);
        dao.persist(quickList2);

        QuickList quickList3 = new QuickList();
        EntityDataGenerator.generateTestDataForModelClass(quickList3);
        quickList3.setQuickListName(quickListName1);
        quickList3.setDxResearchCode(dxResearchCode1);
        quickList3.setCodingSystem(codingSystem1);
        dao.persist(quickList3);

        List<QuickList> expectedResult = Arrays.asList(quickList1, quickList3);
        List<QuickList> result = dao.findByNameResearchCodeAndCodingSystem(quickListName1, dxResearchCode1, codingSystem1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }
}
