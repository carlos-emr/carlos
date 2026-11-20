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
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link HRMDocumentToProviderDao} covering entity persistence.
 *
 * <p>Migrated from legacy {@code HRMDocumentToProviderDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see HRMDocumentToProviderDao
 */
@DisplayName("HRMDocumentToProviderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("hrm")
@Transactional
public class HRMDocumentToProviderDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private HRMDocumentToProviderDao hrmDocumentToProviderDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @Tag("create")
    @DisplayName("should persist HRM document to provider mapping with generated ID")
    void shouldPersistHrmDocumentToProvider_whenValidDataProvided() throws Exception {
        HRMDocument parentDoc = new HRMDocument();
        parentDoc.setReportType("test");
        parentDoc.setReportStatus("A");
        entityManager.persist(parentDoc);
        hibernateTemplate.flush();

        HRMDocumentToProvider entity = new HRMDocumentToProvider();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setHrmDocumentId(parentDoc.getId());
        hrmDocumentToProviderDao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
        assertThat(hrmDocumentToProviderDao.find(entity.getId())).isNotNull();
    }

    /**
     * The Inbox Hub's Acknowledged toggle lists the reports signed off today. The bound is the
     * sign-off time, not the report's own dates, so a report signed off last year stays out of
     * the toggled list instead of being paged in with the provider's whole history.
     */
    @Test
    @Tag("read")
    @Tag("filter")
    @DisplayName("should list only reports signed off since the bound")
    void shouldListOnlyReportsSignedOffSinceBound_whenSignedOffSinceGiven() {
        HRMDocumentToProvider signedOffThisMorning = routeSignedOffReport("2026-10-15 09:12:00");
        HRMDocumentToProvider signedOffAtMidnight = routeSignedOffReport("2026-10-15 00:00:00");
        HRMDocumentToProvider signedOffLastNight = routeSignedOffReport("2026-10-14 23:59:59");
        HRMDocumentToProvider signedOffLastYear = routeSignedOffReport("2025-01-10 16:40:00");

        List<HRMDocumentToProvider> unbounded = hrmDocumentToProviderDao.findByProviderNoLimit("778802",
                List.of(), false, null, null, 2, 1, false, 0, 100, null);
        List<HRMDocumentToProvider> bounded = hrmDocumentToProviderDao.findByProviderNoLimit("778802",
                List.of(), false, null, null, 2, 1, false, 0, 100, Timestamp.valueOf("2026-10-15 00:00:00"));

        assertThat(unbounded).as("every report is signed off, so an unbounded search lists them all")
                .extracting(HRMDocumentToProvider::getId)
                .containsExactlyInAnyOrder(signedOffThisMorning.getId(), signedOffAtMidnight.getId(),
                        signedOffLastNight.getId(), signedOffLastYear.getId());
        assertThat(bounded).as("the bound is inclusive: midnight is in, a second before is out")
                .extracting(HRMDocumentToProvider::getId)
                .containsExactlyInAnyOrder(signedOffThisMorning.getId(), signedOffAtMidnight.getId());
    }

    private HRMDocumentToProvider routeSignedOffReport(String signedOffAt) {
        HRMDocument report = new HRMDocument();
        report.setReportType("test");
        report.setReportStatus("S");
        entityManager.persist(report);
        HRMDocumentToProvider route = new HRMDocumentToProvider();
        route.setHrmDocumentId(report.getId());
        route.setProviderNo("778802");
        route.setSignedOff(1);
        route.setSignedOffTimestamp(Timestamp.valueOf(signedOffAt));
        route.setViewed(1);
        entityManager.persist(route);
        entityManager.flush();
        return route;
    }
}
