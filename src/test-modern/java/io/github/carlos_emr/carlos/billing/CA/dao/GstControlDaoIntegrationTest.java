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
package io.github.carlos_emr.carlos.billing.CA.dao;

import io.github.carlos_emr.carlos.billing.CA.model.GstControl;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GstControlDao}.
 *
 * <p>Migrated from legacy {@code GstControlDaoTest} (JUnit 4 / DaoTestFixtures).
 * Tests persist and findAll methods.</p>
 *
 * @since 2026-03-07
 * @see GstControlDao
 */
@DisplayName("GstControlDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class GstControlDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private GstControlDao dao;

    private GstControl createEntity(BigDecimal gstPercent, Boolean gstFlag) {
        GstControl entity = new GstControl();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setGstPercent(gstPercent);
        entity.setGstFlag(gstFlag);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        GstControl entity = createEntity(new BigDecimal("13.00"), true);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct GST values")
    void shouldReturnEntity_whenValidIdProvided() {
        GstControl saved = createEntity(new BigDecimal("5.00"), true);
        dao.persist(saved);

        GstControl found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getGstPercent()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(found.getGstFlag()).isTrue();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all persisted GST control records")
    void shouldReturnAllRecords_whenFindAllCalled() {
        GstControl entity1 = createEntity(new BigDecimal("5.00"), true);
        GstControl entity2 = createEntity(new BigDecimal("7.00"), false);
        GstControl entity3 = createEntity(new BigDecimal("13.00"), true);
        dao.persist(entity1);
        dao.persist(entity2);
        dao.persist(entity3);

        List<GstControl> results = dao.findAll();
        assertThat(results).hasSize(3);
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no records exist")
    void shouldReturnEmptyList_whenNoRecordsExist() {
        List<GstControl> results = dao.findAll();
        assertThat(results).isEmpty();
    }
}
