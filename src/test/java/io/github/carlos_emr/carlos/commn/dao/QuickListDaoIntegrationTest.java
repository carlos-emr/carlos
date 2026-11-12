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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** An ICHPPC code row, inserted natively because the entity's String id is manually assigned. */
    private void insertIchppcCode(String code, String description) {
        entityManager.createNativeQuery(
                "INSERT INTO ichppccode (ichppccode, diagnostic_code, description) VALUES (:id, :dc, :desc)")
                .setParameter("id", code)
                .setParameter("dc", code)
                .setParameter("desc", description)
                .executeUpdate();
        entityManager.flush();
    }

    private QuickList quickListRow(String quickListName, String code, String codingSystem) throws Exception {
        QuickList row = new QuickList();
        EntityDataGenerator.generateTestDataForModelClass(row);
        row.setQuickListName(quickListName);
        row.setDxResearchCode(code);
        row.setCodingSystem(codingSystem);
        dao.persist(row);
        return row;
    }

    @Test
    @Tag("read")
    @DisplayName("should list a seeded quick list's ICHPPC codes stored under the older name ichppc (#4989)")
    void shouldListCodesStoredAsIchppc_whenLookingUpTheIchppccodeList() throws Exception {
        insertIchppcCode("Z901", "FAKE seeded diagnosis");
        quickListRow("List1", "Z901", "ichppc");

        List<Object[]> items = dao.findResearchCodeAndCodingSystemDescriptionByCodingSystem("ichppccode", "List1");

        assertThat(items).hasSize(1);
        assertThat(items.get(0)[0]).isEqualTo("Z901");
        assertThat(items.get(0)[1]).isEqualTo("FAKE seeded diagnosis");
    }

    @Test
    @Tag("read")
    @DisplayName("should still list codes stored under ichppccode")
    void shouldListCodesStoredAsIchppccode_whenLookingUpTheIchppccodeList() throws Exception {
        insertIchppcCode("Z902", "FAKE added diagnosis");
        quickListRow("FAKE-made", "Z902", "ichppccode");

        List<Object[]> items = dao.findResearchCodeAndCodingSystemDescriptionByCodingSystem("ichppccode", "FAKE-made");

        assertThat(items).extracting(row -> row[0]).containsExactly("Z902");
    }

    @Test
    @Tag("read")
    @DisplayName("should offer a quick list stored under ichppc when the lists are filtered by ichppccode (#4989)")
    void shouldOfferListStoredAsIchppc_whenListsAreFilteredByIchppccode() throws Exception {
        quickListRow("List1", "Z903", "ichppc");

        List<QuickList> lists = dao.findByCodingSystem("ichppccode");

        assertThat(lists).extracting(QuickList::getQuickListName).contains("List1");
    }

    @Test
    @Tag("read")
    @DisplayName("should find a row stored under ichppc by name, code and ichppccode, so it can be removed (#4989)")
    void shouldFindRowStoredAsIchppc_whenFilteredByNameCodeAndIchppccode() throws Exception {
        QuickList stored = quickListRow("List1", "Z904", "ichppc");

        List<QuickList> found = dao.findByNameResearchCodeAndCodingSystem("List1", "Z904", "ichppccode");

        assertThat(found).containsExactly(stored);
    }

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
