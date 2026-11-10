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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.commn.dao.CdsExportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.CdsExportRun;
import io.github.carlos_emr.carlos.commn.model.CdsExportEvent;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import jakarta.persistence.EntityManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real commits, row locks and audit rollback; optional explicit properties run the same cases on
 * fresh MariaDB DDL.
 */
@Tag("integration")
@Tag("cds-export")
class CdsExportLedgerIntegrationTest {
    private static final String COMPATIBLE = "a".repeat(64);

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    static class Audit extends CdsExportEventDao {
        private boolean fail;

        public void failNext() {
            fail = true;
        }

        @Override
        public void persist(AbstractModel<?> model) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("SYNTHETIC_AUDIT_FAILED");
            }
            super.persist(model);
        }
    }

    static class CommitFault implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;
        boolean loseAcknowledgement;

        CommitFault(PlatformTransactionManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return delegate.getTransaction(definition);
        }

        @Override
        public void commit(TransactionStatus status) {
            boolean own = status.isNewTransaction();
            delegate.commit(status);
            if (own && loseAcknowledgement) {
                loseAcknowledgement = false;
                throw new org.springframework.transaction.TransactionSystemException(
                        "SYNTHETIC_LOST_ACKNOWLEDGEMENT");
            }
        }

        @Override
        public void rollback(TransactionStatus status) {
            delegate.rollback(status);
        }
    }

    private AnnotationConfigApplicationContext context;
    private CdsExportLedgerService ledger;
    private Audit audit;
    private CommitFault commits;
    private JdbcTemplate sql;
    private String operator;

    @BeforeEach
    void initializeOwnedDatabase() throws Exception {
        Properties properties = new Properties();
        String selected = System.getProperty("cds.export.test.properties");
        boolean nativeDatabase = selected != null && !selected.isBlank();
        if (nativeDatabase) {
            try (InputStream input = Files.newInputStream(Path.of(selected))) {
                properties.load(input);
            }
            if (!properties
                            .getProperty("jdbcUrl", "")
                            .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                    || !"true".equals(properties.getProperty("ownedDisposableDatabase"))) {
                throw new IllegalArgumentException(
                        "A fresh disposable cds_export_test database is required");
            }
        }
        String url =
                nativeDatabase
                        ? properties.getProperty("jdbcUrl")
                        : "jdbc:h2:mem:cds_export_"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1";
        DataSource source =
                new DriverManagerDataSource(
                        url,
                        nativeDatabase ? properties.getProperty("jdbcUser") : "sa",
                        nativeDatabase ? properties.getProperty("jdbcPassword") : "");
        sql = new JdbcTemplate(source);
        if (nativeDatabase) {
            assertThat(sql.queryForObject("SELECT VERSION()", String.class)).contains("MariaDB");
            assertThat(
                            sql.queryForObject(
                                    "SELECT COUNT(*) FROM secObjectName WHERE"
                                            + " objectName='_cds_export'",
                                    Long.class))
                    .isEqualTo(1);
            assertThat(
                            sql.queryForObject(
                                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE"
                                        + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cds_export_run'"
                                        + " AND ENGINE='InnoDB'",
                                    Long.class))
                    .isEqualTo(1);
        }
        operator = "fixture-" + UUID.randomUUID();
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setDataSource(source);
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    CdsExportRun.class.getName(),
                                    CdsExportEvent.class.getName(),
                                    io.github.carlos_emr.carlos.commn.model.CdsExportAttempt.class
                                            .getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    nativeDatabase ? "validate" : "create-drop",
                                    "hibernate.dialect",
                                    nativeDatabase
                                            ? "org.hibernate.dialect.MariaDBDialect"
                                            : "org.hibernate.dialect.H2Dialect",
                                    "hibernate.jdbc.time_zone",
                                    "UTC",
                                    "hibernate.show_sql",
                                    "false"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () ->
                        new CommitFault(
                                new JpaTransactionManager(
                                        context.getBean(EntityManagerFactory.class))));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(
                CdsExportRunDao.class,
                io.github.carlos_emr.carlos.commn.dao.CdsExportAttemptDao.class,
                CdsExportLedgerService.class);
        context.registerBean(CdsExportEventDao.class, Audit::new);
        context.refresh();
        ledger = context.getBean(CdsExportLedgerService.class);
        audit = (Audit) context.getBean(CdsExportEventDao.class);
        commits = (CommitFault) context.getBean(PlatformTransactionManager.class);
    }

    @AfterEach
    void closeOwnedResources() {
        try {
            if (context != null && context.isActive() && sql != null && operator != null) {
                sql.update(
                        "delete from cds_export_attempt where run_id in (select id from"
                                + " cds_export_run where operator=?)",
                        operatorKey());
                sql.update(
                        "delete from cds_export_event where run_id in (select id from"
                                + " cds_export_run where operator=?)",
                        operatorKey());
                sql.update("delete from cds_export_run where operator=?", operatorKey());
            }
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void shouldRetainSeparateAttemptOwnership_whenTheSameRunIsResumed() {
        var job = ledger.accept(document("recipient"));
        var first = ledger.claim(job.id(), COMPATIBLE);
        ledger.release(first);
        var second = ledger.claim(job.id(), COMPATIBLE);
        assertThat(second.epoch()).isEqualTo(first.epoch() + 1);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_attempt where run_id=?",
                                Long.class,
                                job.id()))
                .isEqualTo(2);
        assertThat(
                        sql.queryForList(
                                "select epoch from cds_export_attempt where run_id=? order by"
                                        + " epoch",
                                Long.class,
                                job.id()))
                .containsExactly(first.epoch(), second.epoch());
    }

    @Test
    void shouldRollbackAttemptCreation_whenItsClaimAuditFails() {
        var job = ledger.accept(document("recipient"));
        audit.failNext();
        assertThatThrownBy(() -> ledger.claim(job.id(), COMPATIBLE))
                .hasMessage("SYNTHETIC_AUDIT_FAILED");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_attempt where run_id=?",
                                Long.class,
                                job.id()))
                .isZero();
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.ACCEPTED);
    }

    @Test
    void shouldRecoverTheSameRunAndUnknownTotals_forAnIdenticalSubmission() {
        var document = document("recipient");
        var first = ledger.accept(document);
        var second = ledger.accept(document);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.totalPatients()).isNull();
        assertThat(second.artifactAvailable()).isFalse();
        assertThat(second.state()).isEqualTo(CdsExportState.ACCEPTED);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=?",
                                Long.class,
                                first.id()))
                .isEqualTo(1);
    }

    @Test
    void shouldRefuseChangedIntent_insteadOfReusingAnotherSubmission() {
        ledger.accept(document("recipient"));
        assertThatThrownBy(() -> ledger.accept(document("another")))
                .hasMessage("CDS_EXPORT_SUBMISSION_CONFLICT");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_run where operator=?",
                                Long.class,
                                operatorKey()))
                .isEqualTo(1);
    }

    @Test
    void shouldRetainVersionTwoStorageBindings_andRefuseInconsistentProtocolDocuments() {
        var original = document("recipient");
        var binding =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                        .CdsExportStorageBindingDto("primary", "active", "e".repeat(64));
        var current =
                new CdsExportJobDocumentDto(
                        2,
                        original.actor(),
                        original.command(),
                        original.compatibilityDigest(),
                        original.senderNamespace(),
                        original.recipientFingerprint(),
                        original.auditPlanDigest(),
                        binding);
        var accepted = ledger.accept(current);
        assertThat(ledger.document(accepted.id())).isEqualTo(current);
        assertThat(ledger.claim(accepted.id(), COMPATIBLE).document().storage()).isEqualTo(binding);
        assertThatThrownBy(
                        () ->
                                ledger.accept(
                                        new CdsExportJobDocumentDto(
                                                1,
                                                original.actor(),
                                                original.command(),
                                                original.compatibilityDigest(),
                                                original.senderNamespace(),
                                                original.recipientFingerprint(),
                                                original.auditPlanDigest(),
                                                binding)))
                .hasMessage("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        assertThatThrownBy(
                        () ->
                                ledger.accept(
                                        new CdsExportJobDocumentDto(
                                                2,
                                                original.actor(),
                                                original.command(),
                                                original.compatibilityDigest(),
                                                original.senderNamespace(),
                                                original.recipientFingerprint(),
                                                original.auditPlanDigest())))
                .hasMessage("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_run where operator=?",
                                Long.class,
                                operatorKey()))
                .isEqualTo(1);
    }

    @Test
    void shouldPreserveTheObservedCancelRevision_acrossACommittedLeaseHeartbeat() throws Exception {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        var observed = ledger.status(job.id());
        var before =
                sql.queryForObject(
                        "select lease_until from cds_export_run where id=?",
                        java.sql.Timestamp.class,
                        job.id());
        Thread.sleep(1100);
        ledger.checkpoint(lease);
        var renewed = ledger.status(job.id());
        assertThat(renewed.revision()).isEqualTo(observed.revision());
        assertThat(renewed.updatedAt()).isAfter(observed.updatedAt());
        assertThat(
                        sql.queryForObject(
                                "select lease_until from cds_export_run where id=?",
                                java.sql.Timestamp.class,
                                job.id()))
                .isAfter(before);
        ledger.cancel(job.id(), operatorKey(), observed.revision());
        assertThat(ledger.status(job.id()).cancelRequested()).isTrue();
        assertThatThrownBy(() -> ledger.checkpoint(lease))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
    }

    @Test
    void shouldRejectAStaleBrowserControl_withoutChangingTheNewerAttempt() {
        var job = ledger.accept(document("recipient"));
        ledger.cancel(job.id(), operatorKey(), job.revision());
        var cancelled = ledger.status(job.id());
        ledger.resume(job.id(), operatorKey(), COMPATIBLE, cancelled.revision());
        var resumed = ledger.status(job.id());
        assertThatThrownBy(() -> ledger.cancel(job.id(), operatorKey(), job.revision()))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportStaleControlException.class);
        assertThatThrownBy(
                        () ->
                                ledger.resume(
                                        job.id(), operatorKey(), COMPATIBLE, cancelled.revision()))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportStaleControlException.class);
        assertThat(ledger.status(job.id())).isEqualTo(resumed);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=?",
                                Long.class,
                                job.id()))
                .isEqualTo(4);
    }

    @Test
    void shouldRefreshAnOpenViewEntity_beforeCheckingTheLockedControlRevision() throws Exception {
        var job = ledger.accept(document("recipient"));
        var factory = context.getBean(EntityManagerFactory.class);
        var view = factory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(
                factory, new org.springframework.orm.jpa.EntityManagerHolder(view));
        try (var external = Executors.newSingleThreadExecutor()) {
            var cached = view.find(CdsExportRun.class, job.id());
            assertThat(cached.getRevision()).isEqualTo(job.revision());
            external.submit(() -> ledger.cancel(job.id(), operatorKey()))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(cached.getRevision()).isEqualTo(job.revision());
            assertThatThrownBy(
                            () ->
                                    ledger.resume(
                                            job.id(), operatorKey(), COMPATIBLE, job.revision()))
                    .isInstanceOf(
                            io.github.carlos_emr.carlos.demographic.cdsexport.support
                                    .CdsExportStaleControlException.class);
            assertThat(
                            sql.queryForObject(
                                    "select state from cds_export_run where id=?",
                                    String.class,
                                    job.id()))
                    .isEqualTo("CANCELLED");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .unbindResource(factory);
            view.close();
        }
    }

    @Test
    void shouldRetireOnlyTheExpiredOwnGeneration_withoutReplacingALiveOrNewerWorker() {
        var job = ledger.accept(document("recipient"));
        var first = ledger.claim(job.id(), COMPATIBLE);
        assertThat(ledger.retireExpiredLease(first)).isFalse();
        sql.update(
                "update cds_export_run set lease_until=? where id=?",
                Timestamp.from(Instant.EPOCH),
                job.id());
        var second = ledger.claim(job.id(), COMPATIBLE);
        assertThat(ledger.retireExpiredLease(first)).isFalse();
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.DISCOVERING);
        sql.update(
                "update cds_export_run set lease_until=? where id=?",
                Timestamp.from(Instant.EPOCH),
                job.id());
        assertThat(ledger.retireExpiredLease(second)).isTrue();
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.REVIEW_REQUIRED);
        assertThat(ledger.status(job.id()).safeCode())
                .isEqualTo("CDS_EXPORT_EXECUTION_LEASE_EXPIRED");
        assertThat(ledger.pending(100)).doesNotContain(job.id());
    }

    @Test
    void shouldPageNewestSavedJobs_byCreationTimeAndIdentifierWithinTheOwner() {
        var older = ledger.accept(document("recipient", "older"));
        var first = ledger.accept(document("recipient", "first"));
        var second = ledger.accept(document("recipient", "second"));
        sql.update(
                "update cds_export_run set created_at=? where operator=?",
                Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")),
                operatorKey());
        sql.update(
                "update cds_export_run set created_at=? where id=?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                older.id());
        var tied =
                java.util.stream.Stream.of(first.id(), second.id())
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList();
        assertThat(ledger.history(operatorKey(), "", 1))
                .extracting(row -> row.id())
                .containsExactly(tied.get(0));
        assertThat(ledger.history(operatorKey(), tied.get(0), 10))
                .extracting(row -> row.id())
                .containsExactly(tied.get(1), older.id());
        assertThatThrownBy(() -> ledger.history("unix:foreign", first.id(), 10))
                .hasMessage("CDS_EXPORT_HISTORY_CURSOR_INVALID");
        assertThatThrownBy(() -> ledger.history(operatorKey(), UUID.randomUUID().toString(), 10))
                .hasMessage("CDS_EXPORT_HISTORY_CURSOR_INVALID");
    }

    @Test
    void shouldRecordUnconfirmedCompletion_whenCiphertextWasWrittenButFinalConfirmationFailed()
            throws Exception {
        var ready = readyArtifact();
        var download = ledger.beginDownload(ready.lease().runId(), operatorKey());
        ledger.finishDownload(download, false, true);
        assertThat(
                        sql.queryForList(
                                "select event_type from cds_export_event where run_id=?",
                                String.class,
                                ready.lease().runId()))
                .contains("DOWNLOAD_COMPLETION_UNCONFIRMED")
                .doesNotContain("DOWNLOAD_COMPLETE", "DOWNLOAD_STOPPED");
    }

    @Test
    void shouldFenceExpiredWorkers_whenASecondNodeClaimsTheJob() {
        var job = ledger.accept(document("recipient"));
        var first = ledger.claim(job.id(), COMPATIBLE);
        assertThat(ledger.claim(job.id(), COMPATIBLE)).isNull();
        sql.update(
                "update cds_export_run set lease_until=? where id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                job.id());
        var second = ledger.claim(job.id(), COMPATIBLE);
        assertThat(second.epoch()).isEqualTo(first.epoch() + 1);
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThatThrownBy(() -> ledger.checkpoint(first)).hasMessage("CDS_EXPORT_OWNER_FENCED");
        assertThatThrownBy(() -> ledger.stop(first, CdsExportState.FAILED, "CDS_EXPORT_FAILURE"))
                .hasMessage("CDS_EXPORT_OWNER_FENCED");
        ledger.checkpoint(second);
    }

    @Test
    void shouldPersistCancellation_andRequireExplicitResumeBeforeAnotherAttempt() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        ledger.cancel(job.id(), operatorKey());
        assertThatThrownBy(() -> ledger.checkpoint(lease)).hasMessage("CDS_EXPORT_CANCELLED");
        ledger.stop(lease, CdsExportState.FAILED, "CDS_EXPORT_FAILURE");
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.CANCELLED);
        assertThat(ledger.pending(100)).doesNotContain(job.id());
        ledger.resume(job.id(), operatorKey(), COMPATIBLE);
        var resumed = ledger.claim(job.id(), COMPATIBLE);
        assertThat(resumed.epoch()).isEqualTo(2);
        assertThat(ledger.status(job.id()).cancelRequested()).isFalse();
    }

    @Test
    void shouldRollbackTheRun_whenStrictAuditInsertionFails() {
        audit.failNext();
        assertThatThrownBy(() -> ledger.accept(document("recipient")))
                .hasMessage("SYNTHETIC_AUDIT_FAILED");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_run where operator=?",
                                Long.class,
                                operatorKey()))
                .isZero();
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        audit.failNext();
        assertThatThrownBy(() -> ledger.stop(lease, CdsExportState.FAILED, "CDS_EXPORT_FAILURE"))
                .hasMessage("SYNTHETIC_AUDIT_FAILED");
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.DISCOVERING);
    }

    @Test
    void shouldRecoverDurableAcceptance_afterARealCommitLosesItsAcknowledgement() {
        commits.loseAcknowledgement = true;
        assertThatThrownBy(() -> ledger.accept(document("recipient")))
                .hasMessage("SYNTHETIC_LOST_ACKNOWLEDGEMENT");
        var recovered = ledger.submission(operatorKey(), "fixture");
        assertThat(ledger.accept(document("recipient")).id()).isEqualTo(recovered.id());
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=?",
                                Long.class,
                                recovered.id()))
                .isEqualTo(1);
    }

    @Test
    void shouldRefuseUnvalidatedReadyTransitions_andIncompatibleRuntimeRecovery() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        assertThatThrownBy(() -> ledger.phase(lease, CdsExportState.READY))
                .hasMessage("CDS_EXPORT_PHASE_INVALID");
        assertThatThrownBy(() -> ledger.phase(lease, CdsExportState.CAPTURING))
                .hasMessage("CDS_EXPORT_SELECTION_NOT_SEALED");
        assertThatThrownBy(() -> ledger.phase(lease, CdsExportState.SERIALIZING))
                .hasMessage("CDS_EXPORT_PHASE_INVALID");
        ledger.stop(lease, CdsExportState.FAILED, "CDS_EXPORT_FAILURE");
        assertThatThrownBy(() -> ledger.resume(job.id(), operatorKey(), "b".repeat(64)))
                .hasMessage("CDS_EXPORT_RUNTIME_CHANGED");
    }

    @Test
    void shouldAdmitOneOwner_underConcurrentPhysicalTransactions() throws Exception {
        var job = ledger.accept(document("recipient"));
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first =
                    workers.submit(
                            () -> {
                                barrier.await();
                                return ledger.claim(job.id(), COMPATIBLE);
                            });
            var second =
                    workers.submit(
                            () -> {
                                barrier.await();
                                return ledger.claim(job.id(), COMPATIBLE);
                            });
            assertThat(
                            java.util.stream.Stream.of(first.get(), second.get())
                                    .filter(java.util.Objects::nonNull)
                                    .count())
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldRejectCorruptedJobBindings_withoutStartingAnAttempt() {
        var job = ledger.accept(document("recipient"));
        sql.update(
                "update cds_export_run set request_digest=? where id=?", "b".repeat(64), job.id());
        assertThatThrownBy(() -> ledger.claim(job.id(), COMPATIBLE))
                .hasMessage("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        assertThat(ledger.status(job.id()).workerOwned()).isFalse();
    }

    @Test
    void shouldPreserveAVerifiedUnixMachineAccount_acrossAcceptanceObservationAndControl() {
        operator = "cds-fixture$";
        var job = ledger.accept(document("recipient"));
        assertThat(ledger.submission(operatorKey(), "fixture").id()).isEqualTo(job.id());
        assertThat(ledger.history(operatorKey(), "", 10))
                .extracting(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto::id)
                .contains(job.id());
        ledger.cancel(job.id(), operatorKey());
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.CANCELLED);
    }

    @Test
    void shouldPreserveTheRecordedFailure_whenCancellationArrivesAfterTheAttemptStopped() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        ledger.stop(lease, CdsExportState.FAILED, "CDS_EXPORT_FAILURE");
        assertThatThrownBy(() -> ledger.cancel(job.id(), operatorKey()))
                .hasMessage("CDS_EXPORT_CANCEL_INVALID");
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.FAILED);
        assertThat(ledger.status(job.id()).safeCode()).isEqualTo("CDS_EXPORT_FAILURE");
    }

    @Test
    void shouldKeepIdenticallyNamedUnixAndWebOperators_inDistinctSubmissionNamespaces() {
        operator = "web-7";
        var unixDocument = document("recipient");
        var unix = ledger.accept(unixDocument);
        var webDocument =
                new CdsExportJobDocumentDto(
                        1,
                        new CdsExportActorDto("web-7", "999998", 7, null),
                        unixDocument.command(),
                        COMPATIBLE,
                        unixDocument.senderNamespace(),
                        unixDocument.recipientFingerprint(),
                        unixDocument.auditPlanDigest());
        var web = ledger.accept(webDocument);
        try {
            assertThat(web.id()).isNotEqualTo(unix.id());
            assertThat(ledger.submission("web:7", "fixture").id()).isEqualTo(web.id());
            assertThat(ledger.submission("unix:web-7", "fixture").id()).isEqualTo(unix.id());
            assertThat(ledger.history("unix:web-7", "", 10))
                    .extracting(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto
                                    ::id)
                    .containsExactly(unix.id());
        } finally {
            sql.update("delete from cds_export_event where run_id=?", web.id());
            sql.update("delete from cds_export_run where id=?", web.id());
        }
    }

    @Test
    void shouldReleaseAnInterruptedAttempt_withoutCancellingIntentOrRetainingStaleProgress() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        sql.update(
                "update cds_export_run set"
                    + " expected_patients=2,processed_patients=1,cohort_digest=?,selected_at=CURRENT_TIMESTAMP"
                    + " where id=?",
                "a".repeat(64),
                job.id());
        ledger.release(lease);
        var waiting = ledger.status(job.id());
        assertThat(waiting.state()).isEqualTo(CdsExportState.ACCEPTED);
        assertThat(waiting.cancelRequested()).isFalse();
        assertThat(waiting.totalPatients()).isNull();
        assertThat(waiting.processedPatients()).isZero();
        assertThatThrownBy(() -> ledger.checkpoint(lease)).hasMessage("CDS_EXPORT_OWNER_FENCED");
        assertThat(ledger.claim(job.id(), COMPATIBLE).epoch()).isEqualTo(lease.epoch() + 1);
    }

    @Test
    void shouldHonorDurableCancellation_whenAnInterruptedWorkerRelinquishesOwnership() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        ledger.cancel(job.id(), operatorKey());
        ledger.release(lease);
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.CANCELLED);
    }

    @Test
    void shouldRejectAMismatchedAcceptedActorScope_beforeRecordingAnyJob() {
        var valid = document("recipient");
        var changed =
                new CdsExportJobDocumentDto(
                        1,
                        new CdsExportActorDto(operator, "100", null, null),
                        valid.command(),
                        valid.compatibilityDigest(),
                        valid.senderNamespace(),
                        valid.recipientFingerprint(),
                        valid.auditPlanDigest());
        assertThatThrownBy(() -> ledger.accept(changed))
                .hasMessage("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        assertThat(ledger.history(operatorKey(), "", 10)).isEmpty();
    }

    @Test
    void shouldRequireADurableCancelRequest_beforeAnEngineCanMarkIntentCancelled() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        assertThatThrownBy(
                        () -> ledger.stop(lease, CdsExportState.CANCELLED, "CDS_EXPORT_CANCELLED"))
                .hasMessage("CDS_EXPORT_DURABLE_CANCEL_REQUIRED");
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.DISCOVERING);
    }

    @Test
    void shouldRetainMetadataOwnershipAndCancellation_whenClinicalIntentCannotBeDeserialized() {
        var job = ledger.accept(document("recipient"));
        sql.update(
                "update cds_export_run set request_document=? where id=?",
                "not-valid-intent",
                job.id());
        var owner = ledger.owner(job.id());
        assertThat(owner.operatorKey()).isEqualTo(operatorKey());
        assertThat(owner.providerNo()).isEqualTo("999998");
        assertThat(owner.facilityHint()).isNull();
        assertThatThrownBy(() -> ledger.document(job.id()))
                .hasMessage("CDS_EXPORT_JOB_DOCUMENT_INVALID");
        ledger.cancel(job.id(), operatorKey());
        assertThat(ledger.status(job.id()).state()).isEqualTo(CdsExportState.CANCELLED);
    }

    @Test
    void shouldRejectAmbientTransactions_beforePerformingMetadataWork() {
        var transaction = new TransactionTemplate(commits);
        assertThatThrownBy(
                        () -> transaction.execute(status -> ledger.accept(document("recipient"))))
                .hasMessage("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
    }

    @Test
    void shouldObserveDurableCancellation_fromInsideAStableSourceReadSnapshot() throws Exception {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        var sourceRead = new TransactionTemplate(commits);
        sourceRead.setReadOnly(true);
        sourceRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try (var external = Executors.newSingleThreadExecutor()) {
            sourceRead.execute(
                    status -> {
                        assertThat(
                                        sql.queryForObject(
                                                "select cancel_requested from cds_export_run where"
                                                        + " id=?",
                                                Boolean.class,
                                                job.id()))
                                .isFalse();
                        ledger.captureCheckpoint(lease);
                        try {
                            external.submit(() -> ledger.cancel(job.id(), operatorKey()))
                                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception failed) {
                            throw new IllegalStateException(failed);
                        }
                        assertThat(
                                        sql.queryForObject(
                                                "select cancel_requested from cds_export_run where"
                                                        + " id=?",
                                                Boolean.class,
                                                job.id()))
                                .isFalse();
                        assertThatThrownBy(() -> ledger.captureCheckpoint(lease))
                                .hasMessage("CDS_EXPORT_CANCELLED");
                        return null;
                    });
        }
        assertThat(ledger.status(job.id()).cancelRequested()).isTrue();
    }

    @Test
    void shouldRestrictTheCaptureProbe_toAnExplicitReadOnlyTransaction() {
        var job = ledger.accept(document("recipient"));
        var lease = ledger.claim(job.id(), COMPATIBLE);
        assertThatThrownBy(() -> ledger.captureCheckpoint(lease))
                .hasMessage("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
        var ordinaryWrite = new TransactionTemplate(commits);
        assertThatThrownBy(
                        () ->
                                ordinaryWrite.execute(
                                        status -> {
                                            ledger.captureCheckpoint(lease);
                                            return null;
                                        }))
                .hasMessage("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path cohortRoot;

    @Test
    void shouldDurablySealTheExactVerifiedCohort_andRecoverItsPointerWithoutClinicalPublication()
            throws Exception {
        var lease = boundLease();
        var fixture = preparedCohort(lease);
        assertThatThrownBy(() -> ledger.cohort(lease))
                .hasMessage("CDS_EXPORT_SELECTION_NOT_SEALED");
        ledger.sealSelection(lease, fixture.sealed());
        var status = ledger.status(lease.runId());
        assertThat(status.state()).isEqualTo(CdsExportState.CAPTURING);
        assertThat(status.totalPatients()).isEqualTo(1L);
        assertThat(status.processedPatients()).isZero();
        assertThat(status.artifactAvailable()).isFalse();
        assertThat(status.complete()).isFalse();
        assertThat(ledger.cohort(lease)).isEqualTo(fixture.sealed().member());
        try (var cursor = fixture.service().open(lease, cohortControl(lease))) {
            assertThat(cursor.next().patient()).isEqualTo(27);
            assertThat(cursor.next()).isNull();
            assertThat(cursor.complete()).isTrue();
        }
        ledger.sealSelection(lease, fixture.sealed());
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='SELECTION_SEALED'",
                                Long.class,
                                lease.runId()))
                .isEqualTo(1);
    }

    @Test
    void shouldRecoverTheSameCohort_afterACommittedSealLosesItsAcknowledgement() throws Exception {
        var lease = boundLease();
        var fixture = preparedCohort(lease);
        commits.loseAcknowledgement = true;
        assertThatThrownBy(() -> ledger.sealSelection(lease, fixture.sealed()))
                .hasMessage("SYNTHETIC_LOST_ACKNOWLEDGEMENT");
        assertThat(ledger.status(lease.runId()).state()).isEqualTo(CdsExportState.CAPTURING);
        ledger.sealSelection(lease, fixture.sealed());
        assertThat(ledger.cohort(lease)).isEqualTo(fixture.sealed().member());
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='SELECTION_SEALED'",
                                Long.class,
                                lease.runId()))
                .isEqualTo(1);
    }

    @Test
    void shouldRollbackASeal_whenItsAuditFailsAndRefuseReplacingAnAlreadySealedSelection()
            throws Exception {
        var lease = boundLease();
        var fixture = preparedCohort(lease);
        audit.failNext();
        assertThatThrownBy(() -> ledger.sealSelection(lease, fixture.sealed()))
                .hasMessage("SYNTHETIC_AUDIT_FAILED");
        assertThat(ledger.status(lease.runId()).state()).isEqualTo(CdsExportState.DISCOVERING);
        assertThat(ledger.status(lease.runId()).totalPatients()).isNull();
        ledger.sealSelection(lease, fixture.sealed());
        var replacement = preparedCohort(lease);
        assertThatThrownBy(() -> ledger.sealSelection(lease, replacement.sealed()))
                .hasMessage("CDS_EXPORT_SELECTION_ALREADY_SEALED");
        assertThat(ledger.cohort(lease)).isEqualTo(fixture.sealed().member());
    }

    @Test
    void shouldFenceASealedCohort_afterTakeoverAndResetItsDurableEvidence() throws Exception {
        var first = boundLease();
        var fixture = preparedCohort(first);
        ledger.sealSelection(first, fixture.sealed());
        sql.update(
                "update cds_export_run set lease_until=? where id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                first.runId());
        var successor = ledger.claim(first.runId(), COMPATIBLE);
        assertThatThrownBy(() -> ledger.sealSelection(first, fixture.sealed()))
                .hasMessage("CDS_EXPORT_OWNER_FENCED");
        assertThatThrownBy(() -> ledger.sealSelection(successor, fixture.sealed()))
                .hasMessage("CDS_EXPORT_COHORT_SCOPE_MISMATCH");
        assertThatThrownBy(() -> ledger.cohort(successor))
                .hasMessage("CDS_EXPORT_SELECTION_NOT_SEALED");
        assertThat(ledger.status(first.runId()).totalPatients()).isNull();
    }

    @Test
    void shouldRetainPreparedAndEncryptedEvidence_withoutMakingTheArtifactAvailable()
            throws Exception {
        var f = artifactFixture();
        var context = ledger.retainPrepared(f.lease(), f.prepared());
        assertThat(context.encrypted()).isFalse();
        ledger.retainPrepared(f.lease(), f.prepared());
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='PACKAGE_RETAINED'",
                                Long.class,
                                f.lease().runId()))
                .isEqualTo(1);
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        var encrypted = ledger.retainEncrypted(f.lease(), f.encrypted());
        assertThat(encrypted.encrypted()).isTrue();
        assertThat(ledger.status(f.lease().runId()).artifactAvailable()).isFalse();
        assertThat(
                        sql.queryForObject(
                                "select state from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isEqualTo("ENCRYPTED");
    }

    @Test
    void shouldRequirePreparedEvidence_beforeRetainingCiphertext() throws Exception {
        var f = artifactFixture();
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        assertThatThrownBy(() -> ledger.retainEncrypted(f.lease(), f.encrypted()))
                .hasMessage("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID");
        assertThat(
                        sql.queryForObject(
                                "select context_document from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isNull();
    }

    @Test
    void shouldAtomicallyPublish_andRecoverALostCommitAcknowledgement() throws Exception {
        var f = artifactFixture();
        ledger.retainPrepared(f.lease(), f.prepared());
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        var service = publication(f, () -> commits.loseAcknowledgement = true);
        var ready =
                service.publish(
                        f.lease(),
                        f.encrypted(),
                        3600,
                        org.mockito.Mockito.mock(
                                io.github.carlos_emr.carlos.demographic.cdsexport.engine
                                        .CdsExportRunControl.class));
        assertThat(ready.state()).isEqualTo(CdsExportState.READY);
        assertThat(ready.complete()).isTrue();
        assertThat(ready.artifactAvailable()).isTrue();
        assertThat(ready.workerOwned()).isFalse();
        assertThat(
                        sql.queryForObject(
                                "select state from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isEqualTo("PUBLISHED");
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='READY'",
                                Long.class,
                                f.lease().runId()))
                .isEqualTo(1);
    }

    @Test
    void shouldRefusePublication_whenTheFinalScopeCheckCancelsTheRun() throws Exception {
        var f = artifactFixture();
        ledger.retainPrepared(f.lease(), f.prepared());
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        var service = publication(f, () -> ledger.cancel(f.lease().runId(), operatorKey()));
        assertThatThrownBy(
                        () ->
                                service.publish(
                                        f.lease(), f.encrypted(), 3600, cohortControl(f.lease())))
                .hasMessage("CDS_EXPORT_CANCELLED");
        assertThat(ledger.status(f.lease().runId()).artifactAvailable()).isFalse();
        assertThat(
                        sql.queryForObject(
                                "select artifact_member from cds_export_run where id=?",
                                String.class,
                                f.lease().runId()))
                .isNull();
    }

    @Test
    void shouldLeaveThePackagePrivate_whenNoClinicalEngineIsQualified() throws Exception {
        var f = artifactFixture();
        ledger.retainPrepared(f.lease(), f.prepared());
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        var service =
                new CdsExportPublicationService(
                        new CdsExportEngineRegistry(List.of()),
                        ledger,
                        org.mockito.Mockito.mock(CdsExportDisclosureScopeService.class),
                        org.mockito.Mockito.mock(CdsExportDisclosurePolicyService.class));
        assertThatThrownBy(
                        () ->
                                service.publish(
                                        f.lease(), f.encrypted(), 3600, cohortControl(f.lease())))
                .hasMessage("CDS_EXPORT_PROFILE_NOT_QUALIFIED");
        assertThat(ledger.status(f.lease().runId()).artifactAvailable()).isFalse();
    }

    @Test
    void shouldRejectTamperedRetainedContext_beforeReplacingItWithEncryptedEvidence()
            throws Exception {
        var f = artifactFixture();
        ledger.retainPrepared(f.lease(), f.prepared());
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        sql.update(
                "update cds_export_attempt set context_document=? where run_id=?",
                "{}",
                f.lease().runId());
        assertThatThrownBy(() -> ledger.retainEncrypted(f.lease(), f.encrypted()))
                .hasMessage("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID");
        assertThat(ledger.status(f.lease().runId()).artifactAvailable()).isFalse();
    }

    private record ArtifactFixture(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto lease,
            CdsExportClinicalPackageService.Prepared prepared,
            CdsExportDeliveryService.Encrypted encrypted) {}

    private ArtifactFixture artifactFixture() throws Exception {
        var lease = boundLease();
        var cohort = preparedCohort(lease);
        ledger.sealSelection(lease, cohort.sealed());
        for (var phase :
                List.of(
                        CdsExportState.RECONCILING,
                        CdsExportState.SERIALIZING,
                        CdsExportState.VALIDATING,
                        CdsExportState.PACKAGING)) ledger.phase(lease, phase);
        var prepared = org.mockito.Mockito.mock(CdsExportClinicalPackageService.Prepared.class);
        org.mockito.Mockito.when(prepared.runId()).thenReturn(lease.runId());
        org.mockito.Mockito.when(prepared.epoch()).thenReturn(lease.epoch());
        org.mockito.Mockito.when(prepared.patients()).thenReturn(1L);
        org.mockito.Mockito.when(prepared.cohortSha256())
                .thenReturn(cohort.sealed().member().plaintextSha256());
        var authorization = artifactFile("DISCLOSURE_MANIFEST");
        var ownership = artifactFile("ATTEMPT_MEMBERS");
        var readMe = artifactFile("SUPPORTING_README");
        var eventLog = artifactFile("SUPPORTING_EVENT_LOG");
        var zip =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageDto(
                        artifactFile("PACKAGE_ZIP"), 3, 3, "d".repeat(64));
        org.mockito.Mockito.when(prepared.authorization()).thenReturn(authorization);
        org.mockito.Mockito.when(prepared.ownership()).thenReturn(ownership);
        org.mockito.Mockito.when(prepared.readMe()).thenReturn(readMe);
        org.mockito.Mockito.when(prepared.eventLog()).thenReturn(eventLog);
        org.mockito.Mockito.when(prepared.archive()).thenReturn(zip);
        var encrypted = org.mockito.Mockito.mock(CdsExportDeliveryService.Encrypted.class);
        var cipher = artifactFile("DELIVERY_CMS");
        var proof =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDeliveryDto(
                        "cms-aes256gcm-rsa-oaep-sha256-v1",
                        lease.document().recipientFingerprint(),
                        zip.archive().plaintextSha256(),
                        zip.archive().plaintextBytes(),
                        cipher.plaintextSha256(),
                        cipher.plaintextBytes());
        org.mockito.Mockito.when(encrypted.prepared()).thenReturn(prepared);
        org.mockito.Mockito.when(encrypted.ciphertext()).thenReturn(cipher);
        org.mockito.Mockito.when(encrypted.proof()).thenReturn(proof);
        return new ArtifactFixture(lease, prepared, encrypted);
    }

    private static io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto
            artifactFile(String purpose) {
        return new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto(
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                purpose,
                "e".repeat(64),
                1);
    }

    private CdsExportPublicationService publication(ArtifactFixture f, Runnable finalAction)
            throws Exception {
        var engine =
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportEngine
                                .class);
        org.mockito.Mockito.when(engine.compatibilityDigest()).thenReturn(COMPATIBLE);
        org.mockito.Mockito.when(engine.qualifiedProfiles())
                .thenReturn(Set.of(f.lease().document().command().profile()));
        var scopes = org.mockito.Mockito.mock(CdsExportDisclosureScopeService.class);
        var scope = org.mockito.Mockito.mock(CdsExportDisclosureScopeService.Verified.class);
        org.mockito.Mockito.when(scope.runId()).thenReturn(f.lease().runId());
        org.mockito.Mockito.when(scope.epoch()).thenReturn(f.lease().epoch());
        org.mockito.Mockito.when(scope.patients()).thenReturn(1L);
        String cohortSha = f.prepared().cohortSha256(),
                authSha = f.prepared().authorization().plaintextSha256();
        org.mockito.Mockito.when(scope.cohortSha256()).thenReturn(cohortSha);
        org.mockito.Mockito.when(scope.authorizationSha256()).thenReturn(authSha);
        // The final no-op checkpoint is outside metadata transactions; only READY's commit
        // acknowledgement is lost.
        org.mockito.Mockito.when(
                        scopes.verify(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        call -> {
                            finalAction.run();
                            return scope;
                        });
        return new CdsExportPublicationService(
                new CdsExportEngineRegistry(List.of(engine)),
                ledger,
                scopes,
                org.mockito.Mockito.mock(CdsExportDisclosurePolicyService.class));
    }

    @Test
    void shouldAuditACompleteDownload_withAnIndependentRetainedArtifactLease() throws Exception {
        var f = readyArtifact();
        var download = ledger.beginDownload(f.lease().runId(), operatorKey());
        assertThat(download.context().encrypted()).isTrue();
        ledger.downloadCheckpoint(download);
        ledger.finishDownload(download, true);
        assertThat(
                        sql.queryForObject(
                                "select download_token from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isNull();
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='DOWNLOAD_COMPLETE'",
                                Long.class,
                                f.lease().runId()))
                .isEqualTo(1);
        assertThatThrownBy(() -> ledger.downloadCheckpoint(download))
                .hasMessage("CDS_EXPORT_DOWNLOAD_FENCED");
    }

    @Test
    void shouldPreventConcurrentDownloads_insteadOfOverwritingTheActiveLease() throws Exception {
        var f = readyArtifact();
        var download = ledger.beginDownload(f.lease().runId(), operatorKey());
        assertThatThrownBy(() -> ledger.beginDownload(f.lease().runId(), operatorKey()))
                .hasMessage("CDS_EXPORT_ARTIFACT_BUSY");
        ledger.finishDownload(download, false);
        assertThat(ledger.beginDownload(f.lease().runId(), operatorKey()).token())
                .isNotEqualTo(download.token());
    }

    @Test
    void shouldStopDownloadsImmediately_afterDurableCancellation() throws Exception {
        var f = readyArtifact();
        var download = ledger.beginDownload(f.lease().runId(), operatorKey());
        ledger.cancel(f.lease().runId(), operatorKey());
        assertThatThrownBy(() -> ledger.downloadCheckpoint(download))
                .hasMessage("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
        assertThatThrownBy(() -> ledger.finishDownload(download, true))
                .hasMessage("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
        ledger.finishDownload(download, false);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from cds_export_event where run_id=? and"
                                        + " event_type='DOWNLOAD_COMPLETE'",
                                Long.class,
                                f.lease().runId()))
                .isZero();
    }

    @Test
    void shouldFenceAnExpiredDownload_evenIfNoSuccessorHasClaimedIt() throws Exception {
        var f = readyArtifact();
        var download = ledger.beginDownload(f.lease().runId(), operatorKey());
        sql.update(
                "update cds_export_attempt set download_until=? where run_id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                f.lease().runId());
        assertThatThrownBy(() -> ledger.downloadCheckpoint(download))
                .hasMessage("CDS_EXPORT_DOWNLOAD_FENCED");
        var successor = ledger.beginDownload(f.lease().runId(), operatorKey());
        assertThatThrownBy(() -> ledger.finishDownload(download, false))
                .hasMessage("CDS_EXPORT_DOWNLOAD_FENCED");
        ledger.finishDownload(successor, false);
    }

    private ArtifactFixture readyArtifact() throws Exception {
        var f = artifactFixture();
        ledger.retainPrepared(f.lease(), f.prepared());
        ledger.phase(f.lease(), CdsExportState.ENCRYPTING);
        publication(f, () -> {}).publish(f.lease(), f.encrypted(), 3600, cohortControl(f.lease()));
        return f;
    }

    @Test
    void shouldExcludeALiveDownloadFromCleanup_evenWhenItsRetentionIsDue() throws Exception {
        var f = readyArtifact();
        var download = ledger.beginDownload(f.lease().runId(), operatorKey());
        sql.update(
                "update cds_export_attempt set expires_at=? where run_id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                f.lease().runId());
        assertThat(ledger.beginCleanup(f.lease().runId(), f.lease().epoch())).isNull();
        ledger.finishDownload(download, false);
        var cleanup = ledger.beginCleanup(f.lease().runId(), f.lease().epoch());
        assertThat(cleanup).isNotNull();
        assertThat(ledger.status(f.lease().runId()).state()).isEqualTo(CdsExportState.EXPIRED);
        assertThatThrownBy(() -> ledger.beginDownload(f.lease().runId(), operatorKey()))
                .hasMessage("CDS_EXPORT_ARTIFACT_UNAVAILABLE");
        ledger.cleanupCheckpoint(cleanup);
        ledger.finishCleanup(cleanup, true);
        assertThat(
                        sql.queryForObject(
                                "select state from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isEqualTo("PURGED");
        assertThat(
                        sql.queryForObject(
                                "select context_document from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isNull();
    }

    @Test
    void shouldExcludeTheLiveWorkerFromCleanup_evenWhenAttemptRetentionIsDue() {
        var lease = boundLease();
        sql.update(
                "update cds_export_attempt set expires_at=? where run_id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                lease.runId());
        assertThat(ledger.beginCleanup(lease.runId(), lease.epoch())).isNull();
    }

    @Test
    void shouldRetainFailureEvidence_andAllowAnExplicitCleanupRetry() throws Exception {
        var f = readyArtifact();
        sql.update(
                "update cds_export_attempt set expires_at=? where run_id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                f.lease().runId());
        var first = ledger.beginCleanup(f.lease().runId(), f.lease().epoch());
        ledger.finishCleanup(first, false);
        assertThat(
                        sql.queryForObject(
                                "select state from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isEqualTo("CLEANUP_FAILED");
        assertThat(
                        sql.queryForObject(
                                "select context_document from cds_export_attempt where run_id=?",
                                String.class,
                                f.lease().runId()))
                .isNotNull();
        assertThat(ledger.beginCleanup(f.lease().runId(), f.lease().epoch())).isNull();
        sql.update(
                "update cds_export_attempt set expires_at=? where run_id=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                f.lease().runId());
        var retry = ledger.beginCleanup(f.lease().runId(), f.lease().epoch());
        assertThat(retry.token()).isNotEqualTo(first.token());
        assertThatThrownBy(() -> ledger.finishCleanup(first, true))
                .hasMessage("CDS_EXPORT_CLEANUP_FENCED");
        ledger.finishCleanup(retry, true);
    }

    @Test
    void shouldExposeOnlyBoundedMetadataDiagnostics_withoutRequiringAnArtifactOrClinicalEngine()
            throws Exception {
        var job = ledger.accept(document("recipient"));
        var report = ledger.diagnostic(job.id());
        assertThat(report.totalPatients()).isNull();
        assertThat(report.complete()).isFalse();
        assertThat(report.artifactAvailable()).isFalse();
        assertThat(report.recentEvents())
                .extracting(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto
                                        .Audit
                                ::type)
                .containsExactly("ACCEPTED");
        var encoded = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report);
        assertThat(encoded)
                .contains("DIAGNOSTIC_REPORT")
                .doesNotContain(
                        operator,
                        "999998",
                        "recipient",
                        "submissionKey",
                        "context",
                        "token",
                        "member");
    }

    @Test
    void shouldExplicitlyMarkTruncatedDiagnosticHistory_insteadOfSilentlyDroppingEvents() {
        var job = ledger.accept(document("recipient"));
        for (int i = 0; i < 65; i++) {
            var lease = ledger.claim(job.id(), COMPATIBLE);
            ledger.release(lease);
        }
        var report = ledger.diagnostic(job.id());
        assertThat(report.eventsTruncated()).isTrue();
        assertThat(report.recentEvents()).hasSize(128);
    }

    @Test
    void shouldKeepCorruptArtifactEvidenceUnavailable_inMetadataObservation() throws Exception {
        var f = readyArtifact();
        sql.update(
                "update cds_export_attempt set context_document=? where run_id=?",
                "{}",
                f.lease().runId());
        var observed = ledger.status(f.lease().runId());
        assertThat(observed.complete()).isFalse();
        assertThat(observed.artifactAvailable()).isFalse();
        assertThat(observed.state()).isEqualTo(CdsExportState.READY);
    }

    @Test
    void shouldAttributeOldAttemptCleanup_toItsOwnEpochWithoutChangingTheCurrentWorker() {
        var first = boundLease();
        ledger.release(first);
        var current = ledger.claim(first.runId(), COMPATIBLE);
        sql.update(
                "update cds_export_attempt set expires_at=? where run_id=? and epoch=?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")),
                first.runId(),
                first.epoch());
        var cleanup = ledger.beginCleanup(first.runId(), first.epoch());
        assertThat(cleanup).isNotNull();
        ledger.finishCleanup(cleanup, true);
        assertThat(ledger.status(first.runId()).epoch()).isEqualTo(current.epoch());
        assertThat(ledger.status(first.runId()).workerOwned()).isTrue();
        assertThat(
                        sql.queryForList(
                                "select epoch from cds_export_event where run_id=? and event_type"
                                        + " in ('CLEANUP_STARTED','CLEANUP_COMPLETE')",
                                Long.class,
                                first.runId()))
                .containsOnly(first.epoch())
                .hasSize(2);
    }

    private record CohortFixture(
            CdsExportCohortService service, CdsExportCohortService.Sealed sealed) {}

    private io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto boundLease() {
        var original = document("recipient");
        var document =
                new CdsExportJobDocumentDto(
                        2,
                        original.actor(),
                        original.command(),
                        original.compatibilityDigest(),
                        original.senderNamespace(),
                        original.recipientFingerprint(),
                        original.auditPlanDigest(),
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportStorageBindingDto("primary", "primary", "f".repeat(64)));
        var job = ledger.accept(document);
        return ledger.claim(job.id(), COMPATIBLE);
    }

    private CdsExportRunControlForCohort cohortControl(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto lease) {
        return new CdsExportRunControlForCohort(ledger, lease);
    }

    private static final class CdsExportRunControlForCohort
            implements io.github.carlos_emr.carlos.demographic.cdsexport.engine
                    .CdsExportRunControl {
        private final CdsExportLedgerService ledger;
        private final io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto lease;

        CdsExportRunControlForCohort(
                CdsExportLedgerService ledger,
                io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto lease) {
            this.ledger = ledger;
            this.lease = lease;
        }

        @Override
        public void checkpoint() {
            ledger.checkpoint(lease);
        }

        @Override
        public void captureCheckpoint() {
            throw new AssertionError("Cohort I/O must remain outside source transactions");
        }

        @Override
        public void authorizePatient(int patient) {}

        @Override
        public void authorizeCategory(int patient, CdsCategory category, boolean sensitive) {}
    }

    private CohortFixture preparedCohort(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto lease)
            throws Exception {
        java.nio.file.Files.setPosixFilePermissions(
                cohortRoot, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var store =
                new CdsExportStagingService(
                        cohortRoot,
                        lease.runId(),
                        lease.epoch(),
                        new javax.crypto.spec.SecretKeySpec(new byte[32], "AES"),
                        1L << 20,
                        0,
                        () -> {});
        var factory =
                new CdsExportStagingFactory(new Properties(), ledger) {
                    @Override
                    public CdsExportStagingService openExisting(
                            String run,
                            long epoch,
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportStorageBindingDto
                                    binding,
                            Runnable checkpoint) {
                        assertThat(run).isEqualTo(lease.runId());
                        assertThat(epoch).isEqualTo(lease.epoch());
                        return store;
                    }
                };
        var service = new CdsExportCohortService(factory, ledger);
        var inventory =
                store.stage(
                        "PATIENT_SELECTION",
                        output ->
                                output.write(
                                        "{\"demographicNo\":27,\"physicianNo\":\"999998\"}\n"
                                                .getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var selection =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto(
                        now, now, 1, inventory);
        return new CohortFixture(service, service.prepare(lease, selection, cohortControl(lease)));
    }

    private String operatorKey() {
        return "unix:" + operator;
    }

    private CdsExportJobDocumentDto document(String recipient) {
        return document(recipient, "fixture");
    }

    private CdsExportJobDocumentDto document(String recipient, String submissionKey) {
        var command =
                new CdsExportCommand(
                        submissionKey,
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        EnumSet.allOf(CdsCategory.class),
                        recipient,
                        "retained-source");
        return new CdsExportJobDocumentDto(
                1,
                new CdsExportActorDto(operator, "999998", null, null),
                command,
                COMPATIBLE,
                "fixture-sender",
                "c".repeat(64),
                "d".repeat(64));
    }
}
