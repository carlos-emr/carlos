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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.VacancyTemplate;
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
 * Integration tests for {@link VacancyTemplateDao}.
 * Migrated from legacy JUnit 4 VacancyTemplateDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("VacancyTemplateDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class VacancyTemplateDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private VacancyTemplateDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist vacancy template with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        VacancyTemplate entity = new VacancyTemplate();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancy templates by wlProgramId")
    void shouldReturnTemplates_byWlProgramId() {
        List<VacancyTemplate> result = dao.getVacancyTemplateByWlProgramId(1);

        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return active vacancy templates by wlProgramId")
    void shouldReturnActiveTemplates_byWlProgramId() {
        List<VacancyTemplate> result = dao.getActiveVacancyTemplatesByWlProgramId(1);

        assertThat(result).isNotNull();
    }
}
