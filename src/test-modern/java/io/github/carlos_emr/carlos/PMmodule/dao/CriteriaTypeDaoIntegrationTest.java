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

import io.github.carlos_emr.carlos.PMmodule.model.CriteriaType;
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
 * Integration tests for {@link CriteriaTypeDao}.
 * Migrated from legacy JUnit 4 CriteriaTypeDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("CriteriaTypeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class CriteriaTypeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CriteriaTypeDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist criteria type entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        CriteriaType entity = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all criteria types")
    void shouldReturnAllCriteriaTypes_whenFindAllCalled() {
        CriteriaType cT1 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT1);
        dao.persist(cT1);

        CriteriaType cT2 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT2);
        dao.persist(cT2);

        CriteriaType cT3 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT3);
        dao.persist(cT3);

        CriteriaType cT4 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT4);
        dao.persist(cT4);

        List<CriteriaType> result = dao.findAll();

        assertThat(result).hasSize(4);
        assertThat(result).containsExactly(cT1, cT2, cT3, cT4);
    }

    @Test
    @Tag("read")
    @DisplayName("should find criteria type by field name")
    void shouldReturnCriteriaType_byFieldName() {
        String fieldName1 = "alpha", fieldName2 = "bravo", fieldName3 = "charlie";

        CriteriaType cT1 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT1);
        cT1.setFieldName(fieldName1);
        dao.persist(cT1);

        CriteriaType cT2 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT2);
        cT2.setFieldName(fieldName2);
        dao.persist(cT2);

        CriteriaType cT3 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT3);
        cT3.setFieldName(fieldName3);
        dao.persist(cT3);

        CriteriaType result = dao.findByName(fieldName2);

        assertThat(result).isEqualTo(cT2);
    }

    @Test
    @Tag("read")
    @DisplayName("should return all criteria types with wlProgramId of 1")
    void shouldReturnCriteriaTypes_whenGetAllCriteriaTypesCalled() {
        String fieldName1 = "alpha", fieldName2 = "bravo", fieldName3 = "charlie", fieldName4 = "delta", fieldName5 = "sigma";
        int wlProgramId1 = 1, wlProgramId2 = 222;

        CriteriaType cT1 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT1);
        cT1.setFieldType(fieldName2);
        cT1.setWlProgramId(wlProgramId1);
        dao.persist(cT1);

        CriteriaType cT2 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT2);
        cT2.setFieldType(fieldName4);
        cT2.setWlProgramId(wlProgramId2);
        dao.persist(cT2);

        CriteriaType cT3 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT3);
        cT3.setFieldType(fieldName5);
        cT3.setWlProgramId(wlProgramId1);
        dao.persist(cT3);

        CriteriaType cT4 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT4);
        cT4.setFieldType(fieldName1);
        cT4.setWlProgramId(wlProgramId1);
        dao.persist(cT4);

        CriteriaType cT5 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT5);
        cT5.setFieldType(fieldName3);
        cT5.setWlProgramId(wlProgramId1);
        dao.persist(cT5);

        List<CriteriaType> result = dao.getAllCriteriaTypes();

        assertThat(result).hasSize(4);
        assertThat(result).containsExactly(cT3, cT5, cT1, cT4);
    }

    @Test
    @Tag("read")
    @DisplayName("should return all criteria types filtered by wlProgramId")
    void shouldReturnCriteriaTypes_byWlProgramId() {
        String fieldName1 = "alpha", fieldName2 = "bravo", fieldName3 = "charlie", fieldName4 = "delta", fieldName5 = "sigma";
        int wlProgramId1 = 111, wlProgramId2 = 222;

        CriteriaType cT1 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT1);
        cT1.setFieldType(fieldName2);
        cT1.setWlProgramId(wlProgramId1);
        dao.persist(cT1);

        CriteriaType cT2 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT2);
        cT2.setFieldType(fieldName4);
        cT2.setWlProgramId(wlProgramId2);
        dao.persist(cT2);

        CriteriaType cT3 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT3);
        cT3.setFieldType(fieldName5);
        cT3.setWlProgramId(wlProgramId1);
        dao.persist(cT3);

        CriteriaType cT4 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT4);
        cT4.setFieldType(fieldName1);
        cT4.setWlProgramId(wlProgramId1);
        dao.persist(cT4);

        CriteriaType cT5 = new CriteriaType();
        EntityDataGenerator.generateTestDataForModelClass(cT5);
        cT5.setFieldName(fieldName3);
        cT5.setWlProgramId(wlProgramId1);
        dao.persist(cT5);

        List<CriteriaType> result = dao.getAllCriteriaTypesByWlProgramId(wlProgramId1);

        assertThat(result).hasSize(4);
        assertThat(result).containsExactly(cT3, cT5, cT1, cT4);
    }
}
