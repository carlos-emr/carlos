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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS25;
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
 * Integration tests for {@link TeleplanS25Dao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS25 Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS25DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS25Dao dao;

    private TeleplanS25 createEntity(Integer s21Id, String s25Type, String practitionerNo) throws Exception {
        TeleplanS25 entity = new TeleplanS25();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setS21Id(s21Id);
        entity.setS25Type(s25Type);
        entity.setPractitionerNo(practitionerNo);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        TeleplanS25 entity = new TeleplanS25();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() throws Exception {
        TeleplanS25 saved = createEntity(10, "TYPE1", "PR001");
        dao.persist(saved);

        TeleplanS25 found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getS21Id()).isEqualTo(10);
        assertThat(found.getS25Type()).isEqualTo("TYPE1");
        assertThat(found.getPractitionerNo()).isEqualTo("PR001");
    }

    @Test
    @Tag("read")
    @DisplayName("should search S25 records by s21Id, excluding type, matching practitioner")
    void shouldReturnFilteredRecords_byS21IdExcludingTypeAndPractitioner() throws Exception {
        TeleplanS25 match = createEntity(60, "MSG", "DR300");
        TeleplanS25 excludedType = createEntity(60, "EXCL", "DR300");
        TeleplanS25 wrongS21 = createEntity(99, "MSG", "DR300");
        TeleplanS25 wrongPrac = createEntity(60, "MSG", "DR999");
        dao.persist(match);
        dao.persist(excludedType);
        dao.persist(wrongS21);
        dao.persist(wrongPrac);

        // search_taS25: s21Id=60, s25Type<>"EXCL", practitionerNo like "DR300"
        List<TeleplanS25> results = dao.search_taS25(60, "EXCL", "DR300");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getS25Type()).isEqualTo("MSG");
        assertThat(results.get(0).getPractitionerNo()).isEqualTo("DR300");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no S25 records match criteria")
    void shouldReturnEmptyList_whenNoS25RecordsMatch() throws Exception {
        TeleplanS25 entity = createEntity(60, "EXCL", "DR300");
        dao.persist(entity);

        // The only record has the excluded type
        List<TeleplanS25> results = dao.search_taS25(60, "EXCL", "DR300");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return multiple matching S25 records ordered by ID then practitioner")
    void shouldReturnMultipleRecords_orderedByIdAndPractitioner() throws Exception {
        TeleplanS25 first = createEntity(35, "A", "DR100");
        TeleplanS25 second = createEntity(35, "B", "DR100");
        dao.persist(first);
        dao.persist(second);

        // Exclude type "X" which neither matches
        List<TeleplanS25> results = dao.search_taS25(35, "X", "DR100");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
    }
}
