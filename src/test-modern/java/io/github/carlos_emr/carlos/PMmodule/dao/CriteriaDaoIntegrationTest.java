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

import io.github.carlos_emr.carlos.PMmodule.model.Criteria;
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
 * Integration tests for {@link CriteriaDao}.
 * Migrated from legacy JUnit 4 CriteriaDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("CriteriaDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class CriteriaDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CriteriaDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist criteria entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        Criteria entity = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria filtered by template ID")
    void shouldReturnCriteria_byTemplateId() {
        int templateId1 = 101;
        int templateId2 = 202;

        Criteria criteria1 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria1);
        criteria1.setTemplateId(templateId1);
        dao.persist(criteria1);

        Criteria criteria2 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria2);
        criteria2.setTemplateId(templateId2);
        dao.persist(criteria2);

        Criteria criteria3 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria3);
        criteria3.setTemplateId(templateId1);
        dao.persist(criteria3);

        Criteria criteria4 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria4);
        criteria4.setTemplateId(templateId2);
        dao.persist(criteria4);

        Criteria criteria5 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria5);
        criteria5.setTemplateId(templateId1);
        dao.persist(criteria5);

        List<Criteria> result = dao.getCriteriaByTemplateId(templateId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(criteria1, criteria3, criteria5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return single criteria by template ID, vacancy ID, and type ID")
    void shouldReturnCriteria_byTemplateIdVacancyIdAndTypeId() {
        int templateId1 = 101, templateId2 = 202;
        int vacancyId1 = 111, vacancyId2 = 222;
        int typeId1 = 333, typeId2 = 444;

        Criteria criteria1 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria1);
        criteria1.setTemplateId(templateId1);
        criteria1.setVacancyId(vacancyId1);
        criteria1.setCriteriaTypeId(typeId1);
        dao.persist(criteria1);

        Criteria criteria2 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria2);
        criteria2.setTemplateId(templateId2);
        criteria2.setVacancyId(vacancyId1);
        criteria2.setCriteriaTypeId(typeId2);
        dao.persist(criteria2);

        Criteria criteria3 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria3);
        criteria3.setTemplateId(templateId1);
        criteria3.setVacancyId(vacancyId2);
        criteria3.setCriteriaTypeId(typeId1);
        dao.persist(criteria3);

        Criteria criteria4 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria4);
        criteria4.setTemplateId(templateId2);
        criteria4.setVacancyId(vacancyId2);
        criteria4.setCriteriaTypeId(typeId1);
        dao.persist(criteria4);

        Criteria result = dao.getCriteriaByTemplateIdVacancyIdTypeId(templateId1, vacancyId1, typeId1);

        assertThat(result).isEqualTo(criteria1);
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria filtered by vacancy ID")
    void shouldReturnCriterias_byVacancyId() {
        int vacancyId1 = 101, vacancyId2 = 202;

        Criteria criteria1 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria1);
        criteria1.setVacancyId(vacancyId1);
        dao.persist(criteria1);

        Criteria criteria2 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria2);
        criteria2.setVacancyId(vacancyId2);
        dao.persist(criteria2);

        Criteria criteria3 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria3);
        criteria3.setVacancyId(vacancyId1);
        dao.persist(criteria3);

        Criteria criteria4 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria4);
        criteria4.setVacancyId(vacancyId2);
        dao.persist(criteria4);

        Criteria criteria5 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria5);
        criteria5.setVacancyId(vacancyId1);
        dao.persist(criteria5);

        List<Criteria> result = dao.getCriteriasByVacancyId(vacancyId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(criteria1, criteria3, criteria5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return refined criteria by vacancy ID excluding adhoc zero")
    void shouldReturnRefinedCriterias_byVacancyId() {
        int vacancyId1 = 101, vacancyId2 = 202;
        int canBeAdhoc1 = 0, canBeAdhoc2 = 11;

        Criteria criteria1 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria1);
        criteria1.setVacancyId(vacancyId1);
        criteria1.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria1);

        Criteria criteria2 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria2);
        criteria2.setVacancyId(vacancyId2);
        criteria2.setCanBeAdhoc(canBeAdhoc1);
        dao.persist(criteria2);

        Criteria criteria3 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria3);
        criteria3.setVacancyId(vacancyId1);
        criteria3.setCanBeAdhoc(canBeAdhoc1);
        dao.persist(criteria3);

        Criteria criteria4 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria4);
        criteria4.setVacancyId(vacancyId2);
        criteria4.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria4);

        Criteria criteria5 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria5);
        criteria5.setVacancyId(vacancyId1);
        criteria5.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria5);

        List<Criteria> result = dao.getRefinedCriteriasByVacancyId(vacancyId1);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(criteria1, criteria5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return refined criteria by template ID excluding adhoc zero")
    void shouldReturnRefinedCriterias_byTemplateId() {
        int templateId1 = 101, templateId2 = 202;
        int canBeAdhoc1 = 0, canBeAdhoc2 = 11;

        Criteria criteria1 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria1);
        criteria1.setTemplateId(templateId1);
        criteria1.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria1);

        Criteria criteria2 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria2);
        criteria2.setTemplateId(templateId2);
        criteria2.setCanBeAdhoc(canBeAdhoc1);
        dao.persist(criteria2);

        Criteria criteria3 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria3);
        criteria3.setTemplateId(templateId1);
        criteria3.setCanBeAdhoc(canBeAdhoc1);
        dao.persist(criteria3);

        Criteria criteria4 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria4);
        criteria4.setTemplateId(templateId2);
        criteria4.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria4);

        Criteria criteria5 = new Criteria();
        EntityDataGenerator.generateTestDataForModelClass(criteria5);
        criteria5.setTemplateId(templateId1);
        criteria5.setCanBeAdhoc(canBeAdhoc2);
        dao.persist(criteria5);

        List<Criteria> result = dao.getRefinedCriteriasByTemplateId(templateId1);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(criteria1, criteria5);
    }
}
