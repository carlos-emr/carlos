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

import io.github.carlos_emr.carlos.commn.model.ReportConfig;
import io.github.carlos_emr.carlos.commn.service.ReportConfigurationService;
import io.github.carlos_emr.carlos.report.data.RptReportConfigData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Real database commit/rollback coverage for the report editor's multi-write operations. */
@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportConfigurationDaoIntegrationTest extends CarlosTestBase {
    @Autowired private ReportConfigDao configurations;
    @Autowired private ReportTableFieldCaptionDao captions;
    @Autowired private ReportConfigurationService service;
    @Autowired private PlatformTransactionManager transactionManager;

    private ReportConfig config(String name, int order, String save) {
        ReportConfig r = new ReportConfig();
        r.setReportId(5549);
        r.setName(name);
        r.setCaption("");
        r.setTableName("formBCAR");
        r.setOrderNo(order);
        r.setSave(save);
        return r;
    }

    private void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ReportConfig r : configurations.findByReportId(5549)) configurations.remove(r.getId());
        });
    }

    @Test
    void shouldPreserveNativeMetadataDatesDecimalsAndOrdering_forConfiguredReports() throws Exception {
        var result = io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.read(
                "select * from (select cast('2026-08-02' as date) as service_date, cast(12.30 as decimal(8,2)) as amount "
                + "union all select cast('2026-08-01' as date), cast(-1.20 as decimal(8,2))) report_values order by service_date");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).value("service_date")).isEqualTo("2026-08-01");
        assertThat(result.rows().get(0).value("amount")).isEqualTo("-1.20");
        assertThat(result.rows().get(1).value("amount")).isEqualTo("12.30");
    }

    @Test
    void shouldCommitGeneratedIdentifiersAndOrderedReads_forSavedReportVariants() throws Exception {
        cleanup();
        try {
            ReportConfig first = config("first", 9, "A");
            ReportConfig other = config("other", 0, "B");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> configurations.persist(first));
            service.insertWithOrder(other);
            assertThat(first.getId()).isPositive();
            assertThat(other.getId()).isPositive().isNotEqualTo(first.getId());
            assertThat(other.getOrderNo()).isEqualTo(10);
            assertThat(configurations.findByReportIdAndSave(5549, "A")).extracting(ReportConfig::getName).containsExactly("first");
            assertThat(configurations.findByReportId(5549)).extracting(ReportConfig::getOrderNo).containsExactly(9, 10);
            assertThat(configurations.findFormTableNames(5549)).containsExactly("formBCAR");
            assertThat(new RptReportConfigData().getConfigObj("B", "5549")).hasSize(1);
        } finally {
            cleanup();
        }
    }

    @Test
    void shouldRollbackEarlierDelete_whenLaterWriteFails() {
        cleanup();
        try {
            ReportConfig first = config("duplicate", 1, "A");
            ReportConfig second = config("duplicate", 2, "A");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                configurations.persist(first);
                configurations.persist(second);
            });
            ReportConfigDao failing = mock(ReportConfigDao.class, delegatesTo(configurations));
            AtomicInteger deletes = new AtomicInteger();
            doAnswer(call -> {
                if (deletes.incrementAndGet() == 2) throw new IllegalStateException("synthetic second-write failure");
                Object id = call.getArgument(0);
                boolean removed = configurations.remove(id);
                configurations.flush(); // Execute the earlier DELETE on the physical connection before failing.
                return removed;
            }).when(failing).remove(anyInt());
            ProxyFactory factory = new ProxyFactory(new ReportConfigurationService(failing, captions));
            factory.setProxyTargetClass(true);
            factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
            ReportConfigurationService failingService = (ReportConfigurationService) factory.getProxy();
            assertThatThrownBy(() -> failingService.deleteMatching(5549, "duplicate", "", "formBCAR", "A"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("synthetic second-write failure");
            assertThat(deletes.get()).isEqualTo(2);
            // This read opens a new transaction after the service transaction has ended.
            assertThat(configurations.findByReportId(5549)).hasSize(2);
        } finally {
            cleanup();
        }
    }

    @Test
    void shouldRemoveOriginalOnce_whenTargetRangeIsEmpty() {
        cleanup();
        try {
            ReportConfig first = config("first", 1, "A");
            service.insertWithOrder(first);
            service.shiftAndRemove(5549, "A", first.getId(), 100);
            assertThat(configurations.find(first.getId())).isNull();
        } finally {
            cleanup();
        }
    }

    @Test
    void shouldRejectCrossReportRemoval_forMismatchedVariant() {
        cleanup();
        try {
            ReportConfig first = config("first", 1, "A");
            service.insertWithOrder(first);
            assertThatThrownBy(() -> service.shiftAndRemove(5549, "B", first.getId(), 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(configurations.find(first.getId())).isNotNull();
        } finally {
            cleanup();
        }
    }
}
