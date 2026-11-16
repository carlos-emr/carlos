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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

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
    @Transactional
    void shouldReadNullOrderAsZero_forLegacySavedConfigurations() throws Exception {
        ReportConfig zeroOrder = config("zeroOrder", 0, "nullable-order");
        ReportConfig nullOrder = config("nullOrder", 1, "nullable-order");
        ReportConfig later = config("later", 2, "nullable-order");
        configurations.persist(zeroOrder);
        configurations.persist(nullOrder);
        configurations.persist(later);
        entityManager.flush();
        entityManager.createNativeQuery("update reportConfig set order_no=NULL where id=?1")
                .setParameter(1, nullOrder.getId()).executeUpdate();
        entityManager.clear();

        assertThat(configurations.findByReportIdAndSave(5549, "nullable-order"))
                .extracting(ReportConfig::getId)
                .containsExactly(zeroOrder.getId(), nullOrder.getId(), later.getId());
        var row = (java.util.Properties) new RptReportConfigData()
                .getConfigObj("nullable-order", "5549").get(1);
        assertThat(row).containsEntry("name", "nullOrder").containsEntry("order_no", "0");
        assertThat(configurations.nextOrder(5549)).isEqualTo(3);
        entityManager.flush();
        assertThat(entityManager.createNativeQuery("select order_no from reportConfig where id=?1")
                .setParameter(1, nullOrder.getId()).getSingleResult()).isNull();
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
    void shouldPreserveTheMovedField_whenTargetRangeIsEmpty() {
        cleanup();
        try {
            ReportConfig first = config("first", 1, "A");
            service.insertWithOrder(first);
            service.shiftAndRemove(5549, "A", first.getId(), 100);
            assertThat(configurations.find(first.getId())).isNotNull();
            assertThat(configurations.find(first.getId()).getOrderNo()).isEqualTo(100);
        } finally {
            cleanup();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "third,first,third:first:second",
            "first,third,second:first:third"
    })
    @Tag("update")
    void shouldMoveTheSelectedField_beforeTheTargetWithoutDroppingRows(
            String selected, String target, String expectedNames) {
        cleanup();
        try {
            for (int index = 0; index < 3; index++) {
                ReportConfig row = config(java.util.List.of("first", "second", "third").get(index), index + 1, "A");
                row.setCaption("FAKE-5926 & Ω");
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> configurations.persist(row));
            }
            ReportConfig otherVariant = config("other", 42, "B");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> configurations.persist(otherVariant));
            var before = configurations.findByReportIdAndSave(5549, "A");
            var selectedRow = before.stream().filter(row -> row.getName().equals(selected)).findFirst().orElseThrow();
            int position = before.stream().filter(row -> row.getName().equals(target)).findFirst().orElseThrow().getOrderNo();
            var originalIds = before.stream().map(ReportConfig::getId).toList();

            service.shiftAndRemove(5549, "A", selectedRow.getId(), position);

            var after = configurations.findByReportIdAndSave(5549, "A");
            assertThat(after).extracting(ReportConfig::getName).containsExactly(expectedNames.split(":"));
            assertThat(after).extracting(ReportConfig::getId).containsExactlyInAnyOrderElementsOf(originalIds);
            assertThat(after).allSatisfy(row -> {
                assertThat(row.getCaption()).isEqualTo("FAKE-5926 & Ω");
                assertThat(row.getTableName()).isEqualTo("formBCAR");
            });
            assertThat(configurations.find(selectedRow.getId()).getOrderNo()).isEqualTo(position);
            assertThat(configurations.find(otherVariant.getId()).getOrderNo()).isEqualTo(42);
        } finally { cleanup(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"third,second,first:third:second", "first,third,second:first:third"})
    @Tag("update")
    void shouldMoveToTheSpecificTarget_whenLegacyOrderNumbersAreTied(String selected, String target, String expected) {
        cleanup();
        try {
            for (String name : java.util.List.of("first", "second", "third")) {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> configurations.persist(config(name, 0, "A")));
            }
            var before = configurations.findByReportIdAndSave(5549, "A");
            var selectedRow = before.stream().filter(row -> row.getName().equals(selected)).findFirst().orElseThrow();
            var targetRow = before.stream().filter(row -> row.getName().equals(target)).findFirst().orElseThrow();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager.createNativeQuery(
                    "update reportConfig set order_no=NULL where id=?1").setParameter(1, targetRow.getId()).executeUpdate());
            new RptReportConfigData().updateRecordOrder("A", "5549", selectedRow.getId().toString(), "0", targetRow.getId().toString());
            var after = configurations.findByReportIdAndSave(5549, "A");
            assertThat(after).extracting(ReportConfig::getName).containsExactly(expected.split(":"));
            assertThat(after).extracting(ReportConfig::getId).containsExactlyInAnyOrderElementsOf(before.stream().map(ReportConfig::getId).toList());
        } finally { cleanup(); }
    }

    @Test
    @Tag("update")
    void shouldLeaveTiedOrdersUnchanged_whenTheSpecificTargetIsTheSelectedRow() {
        cleanup();
        try {
            ReportConfig first = config("first", 0, "A");
            ReportConfig second = config("second", 0, "A");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {configurations.persist(first); configurations.persist(second);});
            service.moveBefore(5549, "A", second.getId(), second.getId());
            assertThat(configurations.findByReportIdAndSave(5549, "A"))
                    .extracting(ReportConfig::getId, ReportConfig::getOrderNo)
                    .containsExactly(tuple(first.getId(), 0), tuple(second.getId(), 0));
            assertThatThrownBy(() -> service.moveBefore(5549, "B", first.getId(), second.getId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.moveBefore(5549, "A", first.getId(), -1)).isInstanceOf(IllegalArgumentException.class);
        } finally { cleanup(); }
    }

    @Test
    @Tag("update")
    void shouldRecomputeFromTheCommittedOrder_whenTwoMovesOverlap() throws Exception {
        cleanup();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var firstMoved = new java.util.concurrent.CountDownLatch(1);
        var commitFirst = new java.util.concurrent.CountDownLatch(1);
        var secondEntered = new java.util.concurrent.CountDownLatch(1);
        try {
            for (int index = 0; index < 3; index++) {
                var row = config(java.util.List.of("first", "second", "third").get(index), index + 1, "A");
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> configurations.persist(row));
            }
            var rows = configurations.findByReportIdAndSave(5549, "A");
            var first = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                service.moveBefore(5549, "A", rows.get(2).getId(), rows.get(0).getId());
                firstMoved.countDown();
                try { if (!commitFirst.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("First move was never released"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                return true;
            }));
            assertThat(firstMoved.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                secondEntered.countDown();
                service.moveBefore(5549, "A", rows.get(0).getId(), rows.get(2).getId());
                return true;
            });
            assertThat(secondEntered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            commitFirst.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(configurations.findByReportIdAndSave(5549, "A"))
                    .extracting(ReportConfig::getName).containsExactly("first", "third", "second");
        } finally {
            commitFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            cleanup();
        }
    }

    @Test
    @Tag("update")
    void shouldKeepEveryOrderNumber_whenMovingAFieldToItsCurrentPosition() {
        cleanup();
        try {
            ReportConfig first = config("first", 1, "A");
            ReportConfig second = config("second", 2, "A");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                configurations.persist(first);
                configurations.persist(second);
            });
            service.shiftAndRemove(5549, "A", first.getId(), 1);
            assertThat(configurations.findByReportIdAndSave(5549, "A"))
                    .extracting(ReportConfig::getId, ReportConfig::getOrderNo)
                    .containsExactly(tuple(first.getId(), 1), tuple(second.getId(), 2));
        } finally { cleanup(); }
    }

    @Test
    @Tag("update")
    void shouldMoveBeforeALegacyNullOrder_whenTheTargetIsZero() {
        cleanup();
        try {
            ReportConfig legacy = config("legacy", 0, "A");
            ReportConfig selected = config("selected", 2, "A");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                configurations.persist(legacy);
                configurations.persist(selected);
                configurations.flush();
                entityManager.createNativeQuery("update reportConfig set order_no=null where id=?1")
                        .setParameter(1, legacy.getId()).executeUpdate();
                entityManager.clear();
            });
            assertThat(configurations.find(legacy.getId()).getOrderNo()).isZero();
            service.shiftAndRemove(5549, "A", selected.getId(), 0);
            assertThat(configurations.findByReportIdAndSave(5549, "A"))
                    .extracting(ReportConfig::getId).containsExactly(selected.getId(), legacy.getId());
            assertThat(configurations.find(legacy.getId()).getOrderNo()).isEqualTo(1);
        } finally { cleanup(); }
    }

    @Test
    @Tag("read")
    void shouldUseIdentifierOrder_forNullAndZeroOrderTies() {
        cleanup();
        try {
            ReportConfig zero = config("zero", 0, "A");
            ReportConfig legacy = config("legacy", 0, "A");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                configurations.persist(zero);
                configurations.persist(legacy);
                configurations.flush();
                entityManager.createNativeQuery("update reportConfig set order_no=null where id=?1")
                        .setParameter(1, legacy.getId()).executeUpdate();
                entityManager.clear();
            });
            assertThat(configurations.findByReportIdAndSave(5549, "A"))
                    .extracting(ReportConfig::getId).containsExactly(zero.getId(), legacy.getId());
            assertThat(configurations.findByReportId(5549))
                    .extracting(ReportConfig::getId).containsExactly(zero.getId(), legacy.getId());
        } finally { cleanup(); }
    }

    @Test
    void shouldRejectRemoval_forMismatchedReportOrSavedVariant() {
        cleanup();
        try {
            ReportConfig first = config("first", 1, "A");
            service.insertWithOrder(first);
            int configurationId = first.getId();
            assertThatThrownBy(() -> service.shiftAndRemove(5549, "B", configurationId, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.shiftAndRemove(5550, "A", configurationId, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(configurations.find(configurationId)).isNotNull();
        } finally {
            cleanup();
        }
    }
}
