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
import io.github.carlos_emr.carlos.commn.model.DesAnnualReviewPlan;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DesAnnualReviewPlanDao} covering full method coverage
 * matching the legacy {@code DesAnnualReviewPlanDaoTest}.
 *
 * <p>Tests cover persist (create) and search operations.</p>
 *
 * @since 2026-03-07
 * @see DesAnnualReviewPlanDao
 */
@DisplayName("DesAnnualReviewPlan Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class DesAnnualReviewPlanDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DesAnnualReviewPlanDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() {
        DesAnnualReviewPlan entity = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by formNo and demographicNo")
    void shouldFindEntity_whenSearchingByFormNoAndDemographicNo() {
        DesAnnualReviewPlan entity = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(1);
        entity.setFormNo(1);
        dao.persist(entity);

        entity = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(1);
        entity.setFormNo(2);
        dao.persist(entity);

        DesAnnualReviewPlan darp = dao.search(2, 1);

        assertThat(darp).isNotNull();
    }
}
