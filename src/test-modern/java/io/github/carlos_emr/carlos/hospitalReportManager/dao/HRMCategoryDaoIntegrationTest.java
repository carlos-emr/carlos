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
package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMCategory;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link HRMCategoryDao} covering entity persistence.
 *
 * <p>Migrated from legacy {@code HRMCategoryDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see HRMCategoryDao
 */
@DisplayName("HRMCategoryDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("hrm")
@Transactional
public class HRMCategoryDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private HRMCategoryDao hrmCategoryDao;

    @Test
    @Tag("create")
    @DisplayName("should persist HRM category with generated ID")
    void shouldPersistHrmCategory_whenValidDataProvided() throws Exception {
        HRMCategory entity = new HRMCategory();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        hrmCategoryDao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
        assertThat(hrmCategoryDao.find(entity.getId())).isNotNull();
    }
}
