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
import io.github.carlos_emr.carlos.commn.model.ReportFilter;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;
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
 * Integration tests for {@link ReportFilterDao} covering basic CRUD operations.
 *
 * <p>Migrated from legacy {@code ReportFilterDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ReportFilterDao
 */
@DisplayName("ReportFilter Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("reporting")
@Transactional
public class ReportFilterDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ReportFilterDao reportFilterDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist reportfilter with generated ID")
        void shouldPersistReportFilter_whenValidDataProvided() throws Exception {
            ReportFilter entity = new ReportFilter();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            reportFilterDao.persist(entity);
            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find reportfilter by ID")
        void shouldFindReportFilter_whenValidIdProvided() throws Exception {
            ReportFilter saved = new ReportFilter();
            EntityDataGenerator.generateTestDataForModelClass(saved);
            reportFilterDao.persist(saved);
            ReportFilter found = reportFilterDao.find(saved.getId());
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should count all reportfilter records")
        void shouldCountAllReportFilters() throws Exception {
            ReportFilter entity = new ReportFilter();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            reportFilterDao.persist(entity);
            long count = reportFilterDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should filter by report and status in configured order")
        void shouldFilterReportAndStatusInOrder_forConfiguredFilters() {
            var later = reportFilter(55491, 1, 3);
            var earlier = reportFilter(55491, 1, 1);
            var deleted = reportFilter(55491, 0, 2);
            var other = reportFilter(55492, 1, 1);
            assertThat(reportFilterDao.findByReportIdAndStatus(55491, 1))
                    .extracting(ReportFilter::getId).containsExactly(earlier.getId(), later.getId());
            assertThat(reportFilterDao.findByReportIdAndStatus(55491, 0))
                    .extracting(ReportFilter::getId).containsExactly(deleted.getId());
            assertThat(reportFilterDao.findByReportIdAndStatus(null, 1))
                    .extracting(ReportFilter::getId).contains(earlier.getId(), later.getId(), other.getId()).doesNotContain(deleted.getId());
        }

        @Test
        @Tag("query")
        void shouldReadNullOrderAsZero_forLegacyReportFilters() throws Exception {
            var zeroOrder = reportFilter(55493, 1, 0);
            var nullOrder = reportFilter(55493, 1, 1);
            entityManager.flush();
            entityManager.createNativeQuery("update reportFilter set order_no=NULL where id=?1")
                    .setParameter(1, nullOrder.getId()).executeUpdate();
            entityManager.clear();

            assertThat(reportFilterDao.findByReportIdAndStatus(55493, 1))
                    .extracting(ReportFilter::getId).containsExactly(nullOrder.getId(), zeroOrder.getId());
            String[] row = (String[]) new RptReportFilter().getNameList("55493", 1).getFirst();
            assertThat(row[3]).isEqualTo("0");
            entityManager.flush();
            assertThat(entityManager.createNativeQuery("select order_no from reportFilter where id=?1")
                    .setParameter(1, nullOrder.getId()).getSingleResult()).isNull();
        }

        @Test
        @Tag("query")
        void shouldReadUnassignedFilters_forLegacyNullReportIds() throws Exception {
            var unassigned = reportFilter(55494, 1, 1);
            unassigned.setDescription("typed-report-null-report-id");
            entityManager.flush();
            entityManager.createNativeQuery("update reportFilter set report_id=NULL where id=?1")
                    .setParameter(1, unassigned.getId()).executeUpdate();
            entityManager.clear();

            assertThat(reportFilterDao.findByReportIdAndStatus(null, 1))
                    .filteredOn(row -> row.getId().equals(unassigned.getId()))
                    .singleElement().extracting(ReportFilter::getReportId).isEqualTo(0);
            java.util.Vector<?> filters = new RptReportFilter().getNameList(1);
            assertThat(filters).anySatisfy(row -> assertThat((String[]) row)
                    .startsWith("typed-report-null-report-id"));
            assertThat(reportFilterDao.findByReportIdAndStatus(0, 1))
                    .extracting(ReportFilter::getId).doesNotContain(unassigned.getId());
        }
    }

    private ReportFilter reportFilter(int report, int status, int order) {
        ReportFilter row = new ReportFilter();
        row.setReportId(report);
        row.setStatus(status);
        row.setOrderNo(order);
        row.setDescription("synthetic filter");
        row.setValue("1=1");
        row.setPosition("");
        row.setJavascript("");
        row.setDateFormat("");
        reportFilterDao.persist(row);
        return row;
    }

}
