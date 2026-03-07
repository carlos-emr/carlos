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
import io.github.carlos_emr.carlos.commn.model.Clinic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ClinicDAO} covering persist and getClinic operations.
 *
 * <p>Migrated from legacy {@code ClinicDAOTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see ClinicDAO
 */
@DisplayName("ClinicDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ClinicDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private ClinicDAO dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist clinic with generated ID")
        void shouldPersistClinic_whenValidDataProvided() throws Exception {
            Clinic entity = new Clinic();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getClinic")
    class GetClinic {

        @Test
        @Tag("read")
        @DisplayName("should return the first persisted clinic")
        void shouldReturnFirstClinic_whenMultipleClinicsExist() throws Exception {
            Clinic clinic1 = new Clinic();
            EntityDataGenerator.generateTestDataForModelClass(clinic1);
            dao.persist(clinic1);

            Clinic clinic2 = new Clinic();
            EntityDataGenerator.generateTestDataForModelClass(clinic2);
            dao.persist(clinic2);

            Clinic clinic3 = new Clinic();
            EntityDataGenerator.generateTestDataForModelClass(clinic3);
            dao.persist(clinic3);

            Clinic result = dao.getClinic();

            assertThat(result).isEqualTo(clinic1);
        }
    }
}
