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
import io.github.carlos_emr.carlos.commn.model.LabReportInformation;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link LabReportInformationDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code LabReportInformationDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see LabReportInformationDao
 */
@DisplayName("LabReportInformation Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lab")
@Transactional
public class LabReportInformationDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private LabReportInformationDao labReportInformationDao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist labreportinformation with generated ID")
        void shouldPersistLabReportInformation_whenValidDataProvided() throws Exception {
            LabReportInformation entity = new LabReportInformation();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labReportInformationDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find labreportinformation by ID")
        void shouldFindLabReportInformation_whenValidIdProvided() throws Exception {
            LabReportInformation saved = new LabReportInformation();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            labReportInformationDao.persist(saved);
            LabReportInformation found = labReportInformationDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all labreportinformation records")
        void shouldCountAllLabReportInformations() throws Exception {
            LabReportInformation entity = new LabReportInformation();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            labReportInformationDao.persist(entity);
            long count = labReportInformationDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
