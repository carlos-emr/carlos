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
 * Integration tests for {@link DesAnnualReviewPlanDao} covering persist and search operations.
 *
 * <p>Tests verify that the DAO correctly persists entities and that the search method
 * returns the correct entity filtered by formNo and demographicNo with proper ordering.</p>
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
    @DisplayName("should find entity by formNo and demographicNo returning highest formNo match")
    void shouldFindEntity_whenSearchingByFormNoAndDemographicNo() {
        DesAnnualReviewPlan entity1 = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setDemographicNo(100);
        entity1.setFormNo(1);
        dao.persist(entity1);

        DesAnnualReviewPlan entity2 = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setDemographicNo(100);
        entity2.setFormNo(2);
        dao.persist(entity2);

        hibernateTemplate.flush();

        // search(formNo, demographicNo) finds entries where formNo <= param, ordered DESC
        DesAnnualReviewPlan result = dao.search(2, 100);

        assertThat(result).isNotNull();
        assertThat(result.getDemographicNo()).isEqualTo(100);
        assertThat(result.getFormNo()).isEqualTo(2);
        assertThat(result.getId()).isEqualTo(entity2.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no matching demographicNo exists")
    void shouldReturnNull_whenNonMatchingDemographicNo() {
        DesAnnualReviewPlan entity = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setDemographicNo(200);
        entity.setFormNo(1);
        dao.persist(entity);
        hibernateTemplate.flush();

        DesAnnualReviewPlan result = dao.search(1, 999);

        assertThat(result).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return entity with highest formNo not exceeding search parameter")
    void shouldReturnHighestFormNo_whenMultipleFormNosExist() {
        DesAnnualReviewPlan entity1 = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setDemographicNo(300);
        entity1.setFormNo(1);
        dao.persist(entity1);

        DesAnnualReviewPlan entity2 = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setDemographicNo(300);
        entity2.setFormNo(3);
        dao.persist(entity2);

        DesAnnualReviewPlan entity3 = new DesAnnualReviewPlan();
        EntityDataGenerator.generateTestDataForModelClass(entity3);
        entity3.setDemographicNo(300);
        entity3.setFormNo(5);
        dao.persist(entity3);

        hibernateTemplate.flush();

        // Search with formNo=4 should find entity2 (formNo=3), not entity3 (formNo=5)
        DesAnnualReviewPlan result = dao.search(4, 300);

        assertThat(result).isNotNull();
        assertThat(result.getFormNo()).isEqualTo(3);
        assertThat(result.getId()).isEqualTo(entity2.getId());
    }
}
