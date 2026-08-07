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
import io.github.carlos_emr.carlos.commn.model.Diseases;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DiseasesDao} covering create,
 * findByDemographicNo, and findByIcd9.
 *
 * <p>Migrated from legacy {@code DiseasesDaoTest} (JUnit 4 / DaoTestFixtures)
 * with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see DiseasesDao
 */
@DisplayName("DiseasesDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class DiseasesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DiseasesDao dao;

    @Nested
    @DisplayName("create tests")
    @Tag("create")
    class Create {

        @Test
        @DisplayName("should persist entity with generated id")
        void shouldPersistEntity_withGeneratedId() throws Exception {
            Diseases entity = new Diseases();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findByDemographicNo tests")
    @Tag("read")
    class FindByDemographicNo {

        @Test
        @DisplayName("should return one disease when demographic number matches")
        void shouldReturnOnDisease_whenDemographicNoMatches() throws Exception {
            Diseases entity = new Diseases();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDemographicNo(1);
            dao.persist(entity);
            hibernateTemplate.flush();

            assertThat(dao.findByDemographicNo(1)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByIcd9 tests")
    @Tag("read")
    class FindByIcd9 {

        @Test
        @DisplayName("should return one disease when icd9 code matches")
        void shouldReturnOneDisease_whenIcd9CodeMatches() throws Exception {
            Diseases entity = new Diseases();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setDemographicNo(1);
            entity.setIcd9Entry("250");
            dao.persist(entity);
            hibernateTemplate.flush();

            assertThat(dao.findByIcd9("250")).hasSize(1);
        }
    }
}
