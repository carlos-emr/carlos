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
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CustomFilterDao} covering save and find with
 * program and assignee associations.
 *
 * <p>Migrated from legacy {@code CustomFilterDaoTest} (JUnit 4 / DaoTestFixtures)
 * with expanded coverage and BDD-style naming.</p>
 *
 * @since 2026-03-07
 * @see CustomFilterDao
 */
@DisplayName("CustomFilterDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CustomFilterDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CustomFilterDao dao;

    @Autowired
    private ProviderDao providerDao;

    @Nested
    @DisplayName("save and find")
    class SaveAndFind {

        @Test
        @Tag("create")
        @DisplayName("should persist custom filter with program and assignees")
        void shouldPersistCustomFilter_withProgramAndAssignees() throws Exception {
            // Create a provider first (legacy test relied on pre-loaded data)
            Provider p = new Provider();
            p.setProviderNo("999998");
            p.setFirstName("Test");
            p.setLastName("Provider");
            p.setProviderType("doctor");
            p.setSex("M");
            p.setSpecialty("");
            p.setStatus("1");
            p.setSignedConfidentiality(new Date());
            providerDao.saveProvider(p);

            CustomFilter entity = new CustomFilter();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            entity.setProviderNo("999998");
            entity.setProgramId("10015");

            entity.getAssignees().add(p);

            dao.persist(entity);

            CustomFilter cf = dao.find(entity.getId());

            assertThat(cf).isNotNull();
            assertThat(cf.getAssignees()).hasSize(1);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when filter does not exist")
        void shouldReturnNull_whenFilterDoesNotExist() {
            CustomFilter cf = dao.find(99999);

            assertThat(cf).isNull();
        }
    }
}
