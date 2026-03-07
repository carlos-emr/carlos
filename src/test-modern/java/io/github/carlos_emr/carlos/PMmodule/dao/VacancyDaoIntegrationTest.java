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

import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
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
 * Integration tests for {@link VacancyDao}.
 * Migrated from legacy JUnit 4 VacancyDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("VacancyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class VacancyDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private VacancyDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist vacancy entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        Vacancy entity = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancies filtered by wlProgramId")
    void shouldReturnVacancies_byWlProgramId() {
        int wlProgramId1 = 101, wlProgramId2 = 202;
        String name = "alpha";

        Vacancy vacancy1 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy1);
        vacancy1.setName(name);
        vacancy1.setWlProgramId(wlProgramId1);
        dao.persist(vacancy1);

        Vacancy vacancy2 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy2);
        vacancy2.setWlProgramId(wlProgramId2);
        vacancy2.setName(name);
        dao.persist(vacancy2);

        Vacancy vacancy3 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy3);
        vacancy3.setWlProgramId(wlProgramId1);
        vacancy3.setName(name);
        dao.persist(vacancy3);

        Vacancy vacancy4 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy4);
        vacancy4.setWlProgramId(wlProgramId2);
        vacancy4.setName(name);
        dao.persist(vacancy4);

        Vacancy vacancy5 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy5);
        vacancy5.setWlProgramId(wlProgramId1);
        vacancy5.setName(name);
        dao.persist(vacancy5);

        List<Vacancy> result = dao.getVacanciesByWlProgramId(wlProgramId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vacancy1, vacancy3, vacancy5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancies filtered by name")
    void shouldReturnVacancies_byName() {
        String name1 = "alpha", name2 = "charlie";

        Vacancy vacancy1 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy1);
        vacancy1.setName(name1);
        dao.persist(vacancy1);

        Vacancy vacancy2 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy2);
        vacancy2.setName(name2);
        dao.persist(vacancy2);

        Vacancy vacancy3 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy3);
        vacancy3.setName(name1);
        dao.persist(vacancy3);

        Vacancy vacancy4 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy4);
        vacancy4.setName(name2);
        dao.persist(vacancy4);

        Vacancy vacancy5 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy5);
        vacancy5.setName(name1);
        dao.persist(vacancy5);

        List<Vacancy> result = dao.getVacanciesByName(name1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vacancy1, vacancy3, vacancy5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancy by ID")
    void shouldReturnVacancy_byId() {
        Vacancy vacancy1 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy1);
        dao.saveEntity(vacancy1);

        Vacancy vacancy2 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy2);
        dao.saveEntity(vacancy2);

        Vacancy vacancy3 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy3);
        dao.saveEntity(vacancy3);

        Vacancy result = dao.getVacancyById(vacancy2.getId());

        assertThat(result).isEqualTo(vacancy2);
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancies filtered by wlProgramId and status")
    void shouldReturnVacancies_byWlProgramIdAndStatus() {
        int wlProgramId1 = 101, wlProgramId2 = 202;
        String status1 = "delta", status2 = "omega";

        Vacancy vacancy1 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy1);
        vacancy1.setWlProgramId(wlProgramId1);
        vacancy1.setStatus(status1);
        dao.persist(vacancy1);

        Vacancy vacancy2 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy2);
        vacancy2.setWlProgramId(wlProgramId2);
        vacancy2.setStatus(status2);
        dao.persist(vacancy2);

        Vacancy vacancy3 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy3);
        vacancy3.setWlProgramId(wlProgramId1);
        vacancy3.setStatus(status1);
        dao.persist(vacancy3);

        Vacancy vacancy4 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy4);
        vacancy4.setWlProgramId(wlProgramId2);
        vacancy4.setStatus(status1);
        dao.persist(vacancy4);

        Vacancy vacancy5 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy5);
        vacancy5.setWlProgramId(wlProgramId1);
        vacancy5.setStatus(status1);
        dao.persist(vacancy5);

        List<Vacancy> result = dao.getVacanciesByWlProgramId(wlProgramId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vacancy1, vacancy3, vacancy5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return current active vacancies")
    void shouldReturnCurrentVacancies_whenFindCurrentCalled() {
        String status1 = "ACTIVE", status2 = "NOTACTIVE";

        Vacancy vacancy1 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy1);
        vacancy1.setStatus(status1);
        dao.persist(vacancy1);

        Vacancy vacancy2 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy2);
        vacancy2.setStatus(status2);
        dao.persist(vacancy2);

        Vacancy vacancy3 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy3);
        vacancy3.setStatus(status1);
        dao.persist(vacancy3);

        Vacancy vacancy4 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy4);
        vacancy4.setStatus(status2);
        dao.persist(vacancy4);

        Vacancy vacancy5 = new Vacancy();
        EntityDataGenerator.generateTestDataForModelClass(vacancy5);
        vacancy5.setStatus(status1);
        dao.persist(vacancy5);

        List<Vacancy> result = dao.findCurrent();

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vacancy1, vacancy3, vacancy5);
    }
}
