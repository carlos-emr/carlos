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
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReportTemplatesDao} covering create, findAll, and findActive.
 *
 * <p>Migrated from legacy {@code ReportTemplatesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportTemplatesDao
 */
@DisplayName("ReportTemplates Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("reporting")
@Transactional
public class ReportTemplatesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportTemplatesDao dao;

    @Nested
    @DisplayName("Create operations")
    class CreateOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist report template with generated ID")
        void shouldPersistReportTemplate_whenValidDataProvided() throws Exception {
            ReportTemplates entity = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted report templates")
        void shouldReturnAllTemplates_whenMultipleExist() throws Exception {
            ReportTemplates rt1 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt1);
            dao.persist(rt1);

            ReportTemplates rt2 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt2);
            dao.persist(rt2);

            ReportTemplates rt3 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt3);
            dao.persist(rt3);

            ReportTemplates rt4 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt4);
            dao.persist(rt4);

            List<ReportTemplates> result = dao.findAll();

            assertThat(result).hasSize(4);
            assertThat(result).containsExactly(rt1, rt2, rt3, rt4);
        }
    }

    @Nested
    @DisplayName("findActive")
    class FindActive {

        @Test
        @Tag("read")
        @DisplayName("should return only templates with active status of 1")
        void shouldReturnActiveTemplates_whenMixedActiveStatusExists() throws Exception {
            int active = 1;
            int inactive = 2;

            ReportTemplates rt1 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt1);
            rt1.setActive(active);
            dao.persist(rt1);

            ReportTemplates rt2 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt2);
            rt2.setActive(inactive);
            dao.persist(rt2);

            ReportTemplates rt3 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt3);
            rt3.setActive(active);
            dao.persist(rt3);

            ReportTemplates rt4 = new ReportTemplates();
            EntityDataGenerator.generateTestDataForModelClass(rt4);
            rt4.setActive(active);
            dao.persist(rt4);

            List<ReportTemplates> result = dao.findActive();

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly(rt1, rt3, rt4);
        }
    }
}
