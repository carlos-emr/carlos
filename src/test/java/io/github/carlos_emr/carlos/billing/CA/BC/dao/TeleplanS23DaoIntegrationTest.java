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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS23;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanS23Dao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS23 Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS23DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS23Dao dao;

    private TeleplanS23 createEntity(Integer s21Id, String s23Type, String aji) throws Exception {
        TeleplanS23 entity = new TeleplanS23();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setS21Id(s21Id);
        entity.setS23Type(s23Type);
        entity.setAji(aji);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        TeleplanS23 entity = new TeleplanS23();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() throws Exception {
        TeleplanS23 saved = createEntity(10, "TYPE1", "AJI001");
        dao.persist(saved);

        TeleplanS23 found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getS21Id()).isEqualTo(10);
        assertThat(found.getS23Type()).isEqualTo("TYPE1");
        assertThat(found.getAji()).isEqualTo("AJI001");
    }

    @Test
    @Tag("read")
    @DisplayName("should search S23 records by s21Id, excluding type, matching aji")
    void shouldReturnFilteredRecords_byS21IdExcludingTypeAndAji() throws Exception {
        TeleplanS23 match = createEntity(40, "KEEP", "ADJ01");
        TeleplanS23 excludedType = createEntity(40, "EXCL", "ADJ01");
        TeleplanS23 wrongS21 = createEntity(99, "KEEP", "ADJ01");
        TeleplanS23 wrongAji = createEntity(40, "KEEP", "ADJ99");
        dao.persist(match);
        dao.persist(excludedType);
        dao.persist(wrongS21);
        dao.persist(wrongAji);

        // search_taS23: s21Id=40, s23Type<>"EXCL", aji like "ADJ01"
        List<TeleplanS23> results = dao.search_taS23(40, "EXCL", "ADJ01");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getS23Type()).isEqualTo("KEEP");
        assertThat(results.get(0).getAji()).isEqualTo("ADJ01");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no S23 records match criteria")
    void shouldReturnEmptyList_whenNoS23RecordsMatch() throws Exception {
        TeleplanS23 entity = createEntity(40, "EXCL", "ADJ01");
        dao.persist(entity);

        // The only record has the excluded type
        List<TeleplanS23> results = dao.search_taS23(40, "EXCL", "ADJ01");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return multiple matching S23 records ordered by ID")
    void shouldReturnMultipleRecords_orderedById() throws Exception {
        TeleplanS23 first = createEntity(25, "A", "ADJ50");
        TeleplanS23 second = createEntity(25, "B", "ADJ50");
        dao.persist(first);
        dao.persist(second);

        // Exclude type "X" which neither matches
        List<TeleplanS23> results = dao.search_taS23(25, "X", "ADJ50");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
    }
}
