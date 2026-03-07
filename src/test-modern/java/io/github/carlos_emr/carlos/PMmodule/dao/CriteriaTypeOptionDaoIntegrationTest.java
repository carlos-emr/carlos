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

import io.github.carlos_emr.carlos.PMmodule.model.CriteriaTypeOption;
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
 * Integration tests for {@link CriteriaTypeOptionDao}.
 * Migrated from legacy JUnit 4 CriteriaTypeOptionDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("CriteriaTypeOptionDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class CriteriaTypeOptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CriteriaTypeOptionDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist criteria type option with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        CriteriaTypeOption entity = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all criteria type options")
    void shouldReturnAllOptions_whenFindAllCalled() {
        CriteriaTypeOption cTO1 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO1);
        dao.persist(cTO1);

        CriteriaTypeOption cTO2 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO2);
        dao.persist(cTO2);

        CriteriaTypeOption cTO3 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO3);
        dao.persist(cTO3);

        CriteriaTypeOption cTO4 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO4);
        dao.persist(cTO4);

        List<CriteriaTypeOption> result = dao.findAll();

        assertThat(result).hasSize(4);
        assertThat(result).containsExactly(cTO1, cTO2, cTO3, cTO4);
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria type options filtered by type ID")
    void shouldReturnOptions_byCriteriaTypeId() {
        int criteriaTypeId1 = 101, criteriaTypeId2 = 202;

        CriteriaTypeOption cTO1 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO1);
        cTO1.setCriteriaTypeId(criteriaTypeId1);
        dao.persist(cTO1);

        CriteriaTypeOption cTO2 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO2);
        cTO2.setCriteriaTypeId(criteriaTypeId2);
        dao.persist(cTO2);

        CriteriaTypeOption cTO3 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO3);
        cTO3.setCriteriaTypeId(criteriaTypeId2);
        dao.persist(cTO3);

        CriteriaTypeOption cTO4 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO4);
        cTO4.setCriteriaTypeId(criteriaTypeId1);
        dao.persist(cTO4);

        CriteriaTypeOption cTO5 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO5);
        cTO5.setCriteriaTypeId(criteriaTypeId1);
        dao.persist(cTO5);

        List<CriteriaTypeOption> result = dao.getCriteriaTypeOptionByTypeId(criteriaTypeId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(cTO1, cTO4, cTO5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria type option by option value")
    void shouldReturnOption_byValue() {
        String optionValue1 = "alpha", optionValue2 = "bravo", optionValue3 = "charlie";

        CriteriaTypeOption cTO1 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO1);
        cTO1.setOptionValue(optionValue1);
        dao.persist(cTO1);

        CriteriaTypeOption cTO2 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO2);
        cTO2.setOptionValue(optionValue2);
        dao.persist(cTO2);

        CriteriaTypeOption cTO3 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO3);
        cTO3.setOptionValue(optionValue3);
        dao.persist(cTO3);

        CriteriaTypeOption result = dao.getByValue(optionValue2);

        assertThat(result).isEqualTo(cTO2);
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria type option by value and type ID")
    void shouldReturnOption_byValueAndTypeId() {
        int criteriaTypeId1 = 101, criteriaTypeId2 = 202;
        String optionValue1 = "alpha", optionValue2 = "bravo";

        CriteriaTypeOption cTO1 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO1);
        cTO1.setCriteriaTypeId(criteriaTypeId1);
        cTO1.setOptionValue(optionValue1);
        dao.persist(cTO1);

        CriteriaTypeOption cTO2 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO2);
        cTO2.setCriteriaTypeId(criteriaTypeId1);
        cTO2.setOptionValue(optionValue2);
        dao.persist(cTO2);

        CriteriaTypeOption cTO3 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO3);
        cTO3.setCriteriaTypeId(criteriaTypeId2);
        cTO3.setOptionValue(optionValue1);
        dao.persist(cTO3);

        CriteriaTypeOption cTO4 = new CriteriaTypeOption();
        EntityDataGenerator.generateTestDataForModelClass(cTO4);
        cTO4.setCriteriaTypeId(criteriaTypeId2);
        cTO4.setOptionValue(optionValue2);
        dao.persist(cTO4);

        CriteriaTypeOption result = dao.getByValueAndTypeId(optionValue2, criteriaTypeId2);

        assertThat(result).isEqualTo(cTO4);
    }
}
