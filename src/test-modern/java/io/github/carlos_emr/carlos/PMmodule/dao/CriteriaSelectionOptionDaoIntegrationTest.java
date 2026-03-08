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

import io.github.carlos_emr.carlos.PMmodule.model.CriteriaSelectionOption;
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
 * Integration tests for {@link CriteriaSelectionOptionDao}.
 * Migrated from legacy JUnit 4 CriteriaSelectionOptionDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("CriteriaSelectionOptionDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class CriteriaSelectionOptionDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CriteriaSelectionOptionDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist criteria selection option with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        CriteriaSelectionOption entity = new CriteriaSelectionOption();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return criteria selected options filtered by criteria ID")
    void shouldReturnSelectedOptions_byCriteriaId() throws Exception {
        int criteriaId1 = 101, criteriaId2 = 202;

        CriteriaSelectionOption cSO1 = new CriteriaSelectionOption();
        EntityDataGenerator.generateTestDataForModelClass(cSO1);
        cSO1.setCriteriaId(criteriaId1);
        dao.saveEntity(cSO1);

        CriteriaSelectionOption cSO2 = new CriteriaSelectionOption();
        EntityDataGenerator.generateTestDataForModelClass(cSO2);
        cSO2.setCriteriaId(criteriaId2);
        dao.saveEntity(cSO2);

        CriteriaSelectionOption cSO3 = new CriteriaSelectionOption();
        EntityDataGenerator.generateTestDataForModelClass(cSO3);
        cSO3.setCriteriaId(criteriaId1);
        dao.saveEntity(cSO3);

        CriteriaSelectionOption cSO4 = new CriteriaSelectionOption();
        EntityDataGenerator.generateTestDataForModelClass(cSO4);
        cSO4.setCriteriaId(criteriaId1);
        dao.saveEntity(cSO4);

        List<CriteriaSelectionOption> result = dao.getCriteriaSelectedOptionsByCriteriaId(criteriaId1);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(cSO1, cSO3, cSO4);
    }
}
