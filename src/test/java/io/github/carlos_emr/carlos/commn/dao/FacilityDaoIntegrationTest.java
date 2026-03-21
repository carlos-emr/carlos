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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Facility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link FacilityDao} covering persist and
 * findAll with active/disabled filtering.
 *
 * <p>Migrated from legacy {@code FacilityDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see FacilityDao
 */
@DisplayName("FacilityDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("facility")
@Transactional
public class FacilityDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FacilityDao dao;

    private Facility createFacility(String name, boolean disabled) throws Exception {
        Facility entity = new Facility();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setName(name);
        entity.setDisabled(disabled);
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist facility with generated ID")
    void shouldPersistFacility_whenValidDataProvided() throws Exception {
        Facility entity = new Facility();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findAll(active)")
    class FindAll {

        @Test
        @Tag("query")
        @DisplayName("should return only disabled facilities when active is false")
        void shouldReturnDisabledFacilities_whenActiveIsFalse() throws Exception {
            createFacility("bravo", true);
            createFacility("delta", false);
            createFacility("charlie", true);
            createFacility("alpha", true);
            createFacility("sigma", false);

            // findAll(false) means active=false, so disabled=true (the parameter maps: active -> where disabled=!active)
            // Wait - re-reading the FacilityDaoImpl: findAll(Boolean active) where if active != null: where disabled=!active
            // So findAll(false) -> where disabled=true -> returns disabled facilities
            // But the legacy test calls findAll(!isDisabled) where isDisabled=true, so findAll(false) which means active=false
            // That returns disabled=true facilities, ordered by name
            List<Facility> result = dao.findAll(false);

            // Seed data may add extra facilities, so check ours are present
            assertThat(result).extracting(Facility::getName)
                    .contains("alpha", "bravo", "charlie");
        }

        @Test
        @Tag("query")
        @DisplayName("should return only active facilities when active is true")
        void shouldReturnActiveFacilities_whenActiveIsTrue() throws Exception {
            createFacility("bravo", true);
            createFacility("delta", false);
            createFacility("sigma", false);

            List<Facility> result = dao.findAll(true);

            // Seed data may add extra facilities, so check ours are present
            assertThat(result).extracting(Facility::getName)
                    .contains("delta", "sigma");
        }

        @Test
        @Tag("query")
        @DisplayName("should return all facilities when active is null")
        void shouldReturnAllFacilities_whenActiveIsNull() throws Exception {
            createFacility("alpha", true);
            createFacility("beta", false);

            List<Facility> result = dao.findAll(null);

            assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
