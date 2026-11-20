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
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link HRMDocumentToDemographicDao} covering entity persistence.
 *
 * <p>Migrated from legacy {@code HRMDocumentToDemographicDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see HRMDocumentToDemographicDao
 */
@DisplayName("HRMDocumentToDemographicDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("hrm")
@Transactional
public class HRMDocumentToDemographicDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @Tag("create")
    @DisplayName("should persist HRM document to demographic mapping with generated ID")
    void shouldPersistHrmDocumentToDemographic_whenValidDataProvided() throws Exception {
        HRMDocument parentDoc = new HRMDocument();
        parentDoc.setReportType("test");
        parentDoc.setReportStatus("A");
        entityManager.persist(parentDoc);
        hibernateTemplate.flush();

        HRMDocumentToDemographic entity = new HRMDocumentToDemographic();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setHrmDocumentId(parentDoc.getId());
        hrmDocumentToDemographicDao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
        assertThat(hrmDocumentToDemographicDao.find(entity.getId())).isNotNull();
    }

    @Test
    @Tag("query")
    @DisplayName("should return only HRM document ids linked to the patient")
    void shouldReturnOwnedHrmIds_forDemographic() {
        HRMDocument ownDoc = new HRMDocument();
        ownDoc.setReportType("test");
        ownDoc.setReportStatus("A");
        entityManager.persist(ownDoc);
        HRMDocument foreignDoc = new HRMDocument();
        foreignDoc.setReportType("test");
        foreignDoc.setReportStatus("A");
        entityManager.persist(foreignDoc);
        hibernateTemplate.flush();

        HRMDocumentToDemographic own = new HRMDocumentToDemographic();
        own.setDemographicNo(7001);
        own.setHrmDocumentId(ownDoc.getId());
        own.setTimeAssigned(new java.util.Date());
        hrmDocumentToDemographicDao.persist(own);
        HRMDocumentToDemographic foreign = new HRMDocumentToDemographic();
        foreign.setDemographicNo(7002);
        foreign.setHrmDocumentId(foreignDoc.getId());
        foreign.setTimeAssigned(new java.util.Date());
        hrmDocumentToDemographicDao.persist(foreign);
        hibernateTemplate.flush();

        assertThat(hrmDocumentToDemographicDao.findHrmIdsForDemographic(7001,
                java.util.List.of(ownDoc.getId(), foreignDoc.getId(), 99999)))
                .containsExactly(ownDoc.getId());
        assertThat(hrmDocumentToDemographicDao.findHrmIdsForDemographic(7001, java.util.List.of())).isEmpty();
    }
}
