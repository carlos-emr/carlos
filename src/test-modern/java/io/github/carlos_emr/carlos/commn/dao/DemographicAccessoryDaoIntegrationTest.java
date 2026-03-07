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
import io.github.carlos_emr.carlos.commn.model.DemographicAccessory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicAccessoryDao} covering persist
 * and count-by-demographic queries.
 *
 * <p>Migrated from legacy {@code DemographicAccessoryDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicAccessoryDao
 */
@DisplayName("DemographicAccessoryDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicAccessoryDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicAccessoryDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        DemographicAccessory entity = new DemographicAccessory();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(1);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return count of one for demographic with single accessory")
    void shouldReturnCountOfOne_forDemographicWithSingleAccessory() {
        DemographicAccessory entity = new DemographicAccessory();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(100);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(dao.findCount(100)).isEqualTo(1);
    }

    @Test
    @Tag("read")
    @DisplayName("should return zero count for demographic with no accessories")
    void shouldReturnZeroCount_forDemographicWithNoAccessories() {
        assertThat(dao.findCount(99999)).isEqualTo(0);
    }
}
