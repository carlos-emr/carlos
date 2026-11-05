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
import io.github.carlos_emr.carlos.commn.model.ReportItem;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.report.data.RptReportItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReportItemDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code ReportItemDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportItemDao
 */
@DisplayName("ReportItem Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("reporting")
@Transactional
public class ReportItemDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportItemDao reportItemDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist reportitem with generated ID")
        void shouldPersistReportItem_whenValidDataProvided() throws Exception {
            ReportItem entity = new ReportItem();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            reportItemDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find reportitem by ID")
        void shouldFindReportItem_whenValidIdProvided() throws Exception {
            ReportItem saved = new ReportItem();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            reportItemDao.persist(saved);
            ReportItem found = reportItemDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all reportitem records")
        void shouldCountAllReportItems() throws Exception {
            ReportItem entity = new ReportItem();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            reportItemDao.persist(entity);
            long count = reportItemDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    void shouldFilterStatusesAndOrderIdentifiers_forReportLists() {
        ReportItem first = new ReportItem();
        first.setReportName("typed-report-first"); first.setStatus(1); reportItemDao.persist(first);
        ReportItem deleted = new ReportItem();
        deleted.setReportName("typed-report-deleted"); deleted.setStatus(0); reportItemDao.persist(deleted);
        ReportItem last = new ReportItem();
        last.setReportName("typed-report-last"); last.setStatus(1); reportItemDao.persist(last);
        assertThat(reportItemDao.findByStatus(1)).extracting(ReportItem::getId)
                .containsSubsequence(first.getId(), last.getId()).doesNotContain(deleted.getId());
        assertThat(reportItemDao.findByStatus(0)).extracting(ReportItem::getId)
                .contains(deleted.getId()).doesNotContain(first.getId(), last.getId());
    }

    @Test
    void shouldReadReportName_forLegacyNullStatus() throws Exception {
        ReportItem item = new ReportItem();
        item.setReportName("typed-report-null-status");
        item.setStatus(1);
        reportItemDao.persist(item);
        entityManager.flush();
        entityManager.createNativeQuery("update reportItem set status=NULL where id=?1")
                .setParameter(1, item.getId()).executeUpdate();
        entityManager.clear();

        assertThat(new RptReportItem().getReportName(item.getId().toString()))
                .isEqualTo("typed-report-null-status");
        assertThat(reportItemDao.find(item.getId()).getStatus()).isZero();
        assertThat(reportItemDao.findByStatus(0)).extracting(ReportItem::getId).doesNotContain(item.getId());
        assertThat(reportItemDao.findByStatus(1)).extracting(ReportItem::getId).doesNotContain(item.getId());
        entityManager.flush();
        assertThat(entityManager.createNativeQuery("select status from reportItem where id=?1")
                .setParameter(1, item.getId()).getSingleResult()).isNull();
    }

}
