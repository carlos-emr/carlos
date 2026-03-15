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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS22;
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
 * Integration tests for {@link TeleplanS22Dao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS22 Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS22DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS22Dao dao;

    private TeleplanS22 createEntity(Integer s21Id, String s22Type, String practitionerNo) throws Exception {
        TeleplanS22 entity = new TeleplanS22();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setS21Id(s21Id);
        entity.setS22Type(s22Type);
        entity.setPractitionerNo(practitionerNo);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        TeleplanS22 entity = new TeleplanS22();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() throws Exception {
        TeleplanS22 saved = createEntity(10, "TYPE1", "PR001");
        dao.persist(saved);

        TeleplanS22 found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getS21Id()).isEqualTo(10);
        assertThat(found.getS22Type()).isEqualTo("TYPE1");
        assertThat(found.getPractitionerNo()).isEqualTo("PR001");
    }

    @Test
    @Tag("read")
    @DisplayName("should search S22 records by s21Id, excluding type, matching practitioner")
    void shouldReturnFilteredRecords_byS21IdExcludingTypeAndPractitioner() throws Exception {
        TeleplanS22 match = createEntity(50, "KEEP", "DR100");
        TeleplanS22 excludedType = createEntity(50, "EXCL", "DR100");
        TeleplanS22 wrongS21 = createEntity(99, "KEEP", "DR100");
        TeleplanS22 wrongPrac = createEntity(50, "KEEP", "DR999");
        dao.persist(match);
        dao.persist(excludedType);
        dao.persist(wrongS21);
        dao.persist(wrongPrac);

        // search_taS22: s21Id=50, s22Type<>"EXCL", practitionerNo like "DR100"
        List<TeleplanS22> results = dao.search_taS22(50, "EXCL", "DR100");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getS22Type()).isEqualTo("KEEP");
        assertThat(results.get(0).getPractitionerNo()).isEqualTo("DR100");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no S22 records match criteria")
    void shouldReturnEmptyList_whenNoS22RecordsMatch() throws Exception {
        TeleplanS22 entity = createEntity(50, "EXCL", "DR100");
        dao.persist(entity);

        // Exclude "EXCL" type means this record is excluded
        List<TeleplanS22> results = dao.search_taS22(50, "EXCL", "DR100");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return multiple matching S22 records ordered by ID")
    void shouldReturnMultipleRecords_orderedById() throws Exception {
        TeleplanS22 first = createEntity(30, "A", "DR050");
        TeleplanS22 second = createEntity(30, "B", "DR050");
        dao.persist(first);
        dao.persist(second);

        // Exclude type "X" (neither matches), so both should be returned
        List<TeleplanS22> results = dao.search_taS22(30, "X", "DR050");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
    }
}
