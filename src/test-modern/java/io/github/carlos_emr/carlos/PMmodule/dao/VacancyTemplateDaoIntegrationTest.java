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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VacancyTemplateDao}.
 * Tests persist, retrieve, merge, and query methods with meaningful assertions.
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

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    private VacancyTemplate createTemplate(String name, Integer wlProgramId, boolean active) {
        VacancyTemplate vt = new VacancyTemplate(wlProgramId, name, active);
        return vt;
    }

    @Nested
    @DisplayName("Persist and Retrieve Tests")
    class PersistAndRetrieveTests {

        @Test
        @Tag("create")
        @DisplayName("should persist vacancy template with generated ID")
        void shouldPersistEntity_whenValidDataProvided() throws Exception {
            VacancyTemplate entity = new VacancyTemplate();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("create")
        @DisplayName("should persist and retrieve vacancy template via saveVacancyTemplate")
        void shouldPersistAndRetrieve_viaSaveVacancyTemplate() {
            VacancyTemplate vt = createTemplate("Save Test Template", 100, true);
            dao.saveVacancyTemplate(vt);
            entityManager.flush();

            assertThat(vt.getId()).isPositive();

            VacancyTemplate found = dao.getVacancyTemplate(vt.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Save Test Template");
            assertThat(found.getWlProgramId()).isEqualTo(100);
            assertThat(found.getActive()).isTrue();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null for non-existent template ID")
        void shouldReturnNull_whenTemplateNotFound() {
            VacancyTemplate found = dao.getVacancyTemplate(99999);
            assertThat(found).isNull();
        }

        @Test
        @Tag("update")
        @DisplayName("should update vacancy template via mergeVacancyTemplate")
        void shouldUpdateTemplate_viaMerge() {
            VacancyTemplate vt = createTemplate("Before Merge", 200, true);
            dao.saveVacancyTemplate(vt);
            entityManager.flush();

            vt.setName("After Merge");
            dao.mergeVacancyTemplate(vt);
            entityManager.flush();

            VacancyTemplate found = dao.getVacancyTemplate(vt.getId());
            assertThat(found.getName()).isEqualTo("After Merge");
        }
    }

    @Nested
    @DisplayName("Query by wlProgramId Tests")
    class QueryByWlProgramIdTests {

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return templates matching wlProgramId and exclude others")
        void shouldReturnTemplates_byWlProgramId() {
            VacancyTemplate vt1 = createTemplate("Prog 300 Template A", 300, true);
            dao.saveVacancyTemplate(vt1);

            VacancyTemplate vt2 = createTemplate("Prog 300 Template B", 300, false);
            dao.saveVacancyTemplate(vt2);

            VacancyTemplate vt3 = createTemplate("Prog 301 Template", 301, true);
            dao.saveVacancyTemplate(vt3);
            entityManager.flush();

            List<VacancyTemplate> result = dao.getVacancyTemplateByWlProgramId(300);
            assertThat(result).hasSize(2);
            assertThat(result).extracting(VacancyTemplate::getId)
                .containsExactlyInAnyOrder(vt1.getId(), vt2.getId());
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when no templates match wlProgramId")
        void shouldReturnEmptyList_whenNoTemplatesMatchWlProgramId() {
            VacancyTemplate vt = createTemplate("Other Prog", 400, true);
            dao.saveVacancyTemplate(vt);
            entityManager.flush();

            List<VacancyTemplate> result = dao.getVacancyTemplateByWlProgramId(999);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return only active templates by wlProgramId")
        void shouldReturnOnlyActiveTemplates_byWlProgramId() {
            VacancyTemplate activeVt = createTemplate("Active Template", 500, true);
            dao.saveVacancyTemplate(activeVt);

            VacancyTemplate inactiveVt = createTemplate("Inactive Template", 500, false);
            dao.saveVacancyTemplate(inactiveVt);

            VacancyTemplate otherProgVt = createTemplate("Other Prog Active", 501, true);
            dao.saveVacancyTemplate(otherProgVt);
            entityManager.flush();

            List<VacancyTemplate> result = dao.getActiveVacancyTemplatesByWlProgramId(500);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(activeVt.getId());
            assertThat(result.get(0).getName()).isEqualTo("Active Template");
        }

        @Test
        @Tag("read")
        @Tag("query")
        @DisplayName("should return empty list when no active templates exist for wlProgramId")
        void shouldReturnEmptyList_whenNoActiveTemplatesExist() {
            VacancyTemplate inactiveVt = createTemplate("Inactive Only", 600, false);
            dao.saveVacancyTemplate(inactiveVt);
            entityManager.flush();

            List<VacancyTemplate> result = dao.getActiveVacancyTemplatesByWlProgramId(600);
            assertThat(result).isEmpty();
        }
    }
}
