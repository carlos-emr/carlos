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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportCheckpointDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFileDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFileReferenceDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportLineageDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportReviewCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Real review control, strict metadata connection, JPA and migrated storage; auth/staged job inputs are synthetic. */
@Tag("integration")
@Tag("mariadb")
@Isolated("Owned disposable CDS review database")
@EnabledIfSystemProperty(named = "cds.test.properties", matches = ".+")
class CdsReviewAcceptanceMariaDbIntegrationTest {
    private static final String A = "a".repeat(64);
    private static final String PATIENT = "b".repeat(64);
    private static final String REVISION = "APPROVE_UNWRITTEN_REVISION";

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions { }

    /** Inject a strict audit failure after Hibernate has flushed the review and version in the same real transaction. */
    public static class Audit extends CdsImportEventDao {
        private boolean failAfterFlush;
        private Runnable acknowledgementFault;
        void loseNextAcknowledgement(Runnable action) { acknowledgementFault = action; }
        void failNextAfterFlush() { failAfterFlush = true; }
        @Override public void persist(AbstractModel<?> value) {
            super.persist(value);
            if (acknowledgementFault != null) {
                acknowledgementFault.run();
                acknowledgementFault = null;
            }
            if (failAfterFlush) {
                failAfterFlush = false;
                entityManager.flush();
                throw new org.springframework.dao.DataIntegrityViolationException("Synthetic strict review audit failure");
            }
        }
    }

    public static class CommitAcknowledgementFault implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;
        private boolean fail;
        CommitAcknowledgementFault(PlatformTransactionManager delegate) { this.delegate = delegate; }
        void failNextCommitAcknowledgement() { fail = true; }
        @Override public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            return delegate.getTransaction(definition);
        }
        @Override public void commit(org.springframework.transaction.TransactionStatus status) {
            boolean owning = status.isNewTransaction();
            delegate.commit(status);
            if (owning && fail) {
                fail = false;
                throw new org.springframework.transaction.TransactionSystemException("Synthetic lost review acknowledgement");
            }
        }
        @Override public void rollback(org.springframework.transaction.TransactionStatus status) { delegate.rollback(status); }
    }

    @TempDir Path directory;
    private AnnotationConfigApplicationContext context;
    private OscarTrackingBasicDataSource dataSource;
    private JdbcTemplate sql;
    private CdsImportLedgerService ledger;
    private CdsImportReviewService reviews;
    private Audit audit;
    private CommitAcknowledgementFault commits;
    private final CdsImportJobService jobs = mock(CdsImportJobService.class);
    private final CdsImportAccessService access = mock(CdsImportAccessService.class);
    private final LoggedInInfo viewer = mock(LoggedInInfo.class);
    private final CdsImportActorDto actor = new CdsImportActorDto("web-42", "999998", "999998", 42, null, null);

    @BeforeEach
    void setUp() throws Exception {
        Properties jdbc = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("ownedDisposableDatabase")).isEqualTo("true");
        assertThat(jdbc.getProperty("jdbcUrl", "")).matches("jdbc:(mysql|mariadb)://[^/]+/carlos_cds_test(?:\\?.*)?");
        dataSource = new OscarTrackingBasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(jdbc.getProperty("jdbcUrl").replace("jdbc:mariadb:", "jdbc:mysql:"));
        dataSource.setUsername(jdbc.getProperty("jdbcUser"));
        dataSource.setPassword(jdbc.getProperty("jdbcPassword"));
        dataSource.setDefaultAutoCommit(true);
        new CdsJdbcAdmissionInitializer(dataSource, true, "validate").initialize();
        sql = new JdbcTemplate(dataSource);
        assertThat(sql.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cds_import_review' AND COLUMN_NAME='kind'", Integer.class)).isGreaterThanOrEqualTo(32);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.0.59.25' AND success=1", Long.class)).isEqualTo(1L);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_patient WHERE held=TRUE", Long.class)).isZero();
        assertThat(sql.queryForObject("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'", Boolean.class)).isFalse();
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("dataSource", DataSource.class, () -> dataSource);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            String[] names = {"Run", "Patient", "Unit", "Dependency", "Lineage", "Checkpoint", "Diagnostic", "Event", "Admission", "Identity", "Review", "File", "FileReference"};
            factory.setManagedTypes(PersistenceManagedTypes.of(Arrays.stream(names)
                    .map(name -> "io.github.carlos_emr.carlos.commn.model.CdsImport" + name).toArray(String[]::new)));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.MariaDBDialect", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        });
        context.registerBean("transactionManager", PlatformTransactionManager.class,
                () -> new CommitAcknowledgementFault(new JpaTransactionManager(context.getBean(EntityManagerFactory.class))));
        context.registerBean("persistenceAnnotationBeanPostProcessor", PersistenceAnnotationBeanPostProcessor.class);
        context.register(CdsImportRunDao.class, CdsImportPatientDao.class, CdsImportUnitDao.class, CdsImportDependencyDao.class,
                CdsImportLineageDao.class, CdsImportCheckpointDao.class, CdsImportDiagnosticDao.class, CdsImportAdmissionDao.class,
                CdsImportIdentityDao.class, CdsImportReviewDao.class, CdsImportFileDao.class, CdsImportFileReferenceDao.class,
                CdsImportLedgerService.class, CdsImportFileService.class);
        context.registerBean(CdsImportEventDao.class, Audit::new);
        context.registerBean(CdsImportReviewService.class, () -> new CdsImportReviewService(
                context.getBean(CdsImportLedgerService.class), jobs, new CdsImportConfigurationService(new Properties()), access,
                context.getBean(CdsImportReviewDao.class), context.getBean(CdsImportRunDao.class), context.getBean(CdsImportDiagnosticDao.class),
                mock(CdsSnapshotService.class), new CdsXmlParser(), new CdsFieldParser(), mock(CdsCorrectionLookupService.class),
                mock(CdsImportCoordinatorService.class), mock(CdsUnwrittenRevisionReviewService.class), context.getBean(PlatformTransactionManager.class)));
        context.refresh();
        ledger = context.getBean(CdsImportLedgerService.class);
        reviews = context.getBean(CdsImportReviewService.class);
        audit = context.getBean(Audit.class);
        commits = (CommitAcknowledgementFault) context.getBean(PlatformTransactionManager.class);
        when(access.webActor(any(), any())).thenReturn(actor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) context.close();
        if (dataSource != null) dataSource.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CORRECT_VALUE", "DECLINE_PATIENT", REVISION})
    void shouldAcceptEveryExactSupportedKind_throughRealServiceAndMigratedOrm(String kind) throws Exception {
        Fixture fixture = stopped();
        String reviewId = reviews.accept(viewer, fixture.run(), command(fixture, kind, 0));
        assertThat(sql.queryForObject("SELECT kind FROM cds_import_review WHERE id=?", String.class, reviewId)).isEqualTo(kind);
        assertThat(sql.queryForObject("SELECT CHAR_LENGTH(kind) FROM cds_import_review WHERE id=?", Integer.class, reviewId)).isEqualTo(kind.length());
        assertThat(sql.queryForObject("SELECT state FROM cds_import_review WHERE id=?", String.class, reviewId)).isEqualTo("REQUESTED");
        assertThat(sql.queryForObject("SELECT review_version FROM cds_import_run WHERE id=?", Long.class, fixture.run())).isEqualTo(1L);
        assertThat(sql.queryForObject("SELECT review_version FROM cds_import_review WHERE id=?", Long.class, reviewId)).isEqualTo(1L);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND event_type='REVIEW_REQUESTED'", Long.class, fixture.run())).isEqualTo(1L);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_unit WHERE run_id=?", Long.class, fixture.run())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_patient WHERE run_id=? AND (held=TRUE OR target_demographic_no IS NOT NULL)", Long.class, fixture.run())).isZero();
        assertThat(reviews.pending(50)).contains(reviewId);
    }

    @Test
    void shouldRollBackFlushedKindVersionAndAuditTogether_whenStrictAuditFails() throws Exception {
        Fixture fixture = stopped();
        audit.failNextAfterFlush();
        CdsImportReviewCommand request = command(fixture, REVISION, 0);
        assertThatThrownBy(() -> reviews.accept(viewer, fixture.run(), request)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_review WHERE run_id=?", Long.class, fixture.run())).isZero();
        assertThat(sql.queryForObject("SELECT review_version FROM cds_import_run WHERE id=?", Long.class, fixture.run())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND event_type='REVIEW_REQUESTED'", Long.class, fixture.run())).isZero();
        String retried = reviews.accept(viewer, fixture.run(), request);
        assertThat(sql.queryForObject("SELECT kind FROM cds_import_review WHERE id=?", String.class, retried)).isEqualTo(REVISION);
    }

    @Test
    void shouldRejectStaleVersion_withoutReplacingAlreadyAcceptedExactKind() throws Exception {
        Fixture fixture = stopped();
        CdsImportReviewCommand request = command(fixture, REVISION, 0);
        String reviewId = reviews.accept(viewer, fixture.run(), request);
        assertThatThrownBy(() -> reviews.accept(viewer, fixture.run(), request)).isInstanceOf(CdsImportException.class)
                .extracting(error -> ((CdsImportException) error).getCode()).isEqualTo("REVIEW_VERSION_CONFLICT");
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_review WHERE run_id=?", Long.class, fixture.run())).isEqualTo(1L);
        assertThat(sql.queryForObject("SELECT kind FROM cds_import_review WHERE id=?", String.class, reviewId)).isEqualTo(REVISION);
        assertThat(sql.queryForObject("SELECT review_version FROM cds_import_run WHERE id=?", Long.class, fixture.run())).isEqualTo(1L);
    }

    @Test
    void shouldReportRealAcceptanceRollbackAsUnconfirmed_withoutChangingReviewFlags() throws Exception {
        Fixture fixture = stopped();
        var evidence = new CdsImportFailureEvidenceService(jobs, context.getBean(CdsImportFileService.class));
        try (var facade = facade(evidence)) {
            audit.failNextAfterFlush();
            CdsImportReviewCommand request = command(fixture, REVISION, 0);
            assertThatThrownBy(() -> facade.resolveReview(viewer, fixture.run(), request))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_review WHERE run_id=?", Long.class, fixture.run())).isZero();
            assertThat(sql.queryForObject("SELECT review_version FROM cds_import_run WHERE id=?", Long.class, fixture.run())).isZero();
            var diagnostic = ledger.diagnostics(fixture.run(), 0, 20).stream()
                    .filter(row -> "REVIEW_ACCEPTANCE_OUTCOME_UNCONFIRMED".equals(row.code())).findFirst().orElseThrow();
            assertThat(diagnostic.blocking()).isFalse();
            assertThat(diagnostic.protectedDetailLocation()).isNotNull();
            assertThat(evidence.read(fixture.run(), diagnostic.protectedDetailLocation())).containsKey("causes");
        }
    }

    @Test
    void shouldPreserveAcceptedReviewWithoutResubmit_whenCommitAcknowledgementIsLost() throws Exception {
        Fixture fixture = stopped();
        var evidence = new CdsImportFailureEvidenceService(jobs, context.getBean(CdsImportFileService.class));
        try (var facade = facade(evidence)) {
            audit.loseNextAcknowledgement(commits::failNextCommitAcknowledgement);
            CdsImportReviewCommand request = command(fixture, REVISION, 0);
            assertThatThrownBy(() -> facade.resolveReview(viewer, fixture.run(), request))
                    .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_review WHERE run_id=? AND kind=? AND state='REQUESTED'", Long.class, fixture.run(), REVISION)).isEqualTo(1L);
            assertThat(sql.queryForObject("SELECT review_version FROM cds_import_run WHERE id=?", Long.class, fixture.run())).isEqualTo(1L);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND event_type='REVIEW_REQUESTED'", Long.class, fixture.run())).isEqualTo(1L);
            var diagnostic = ledger.diagnostics(fixture.run(), 0, 20).stream()
                    .filter(row -> "REVIEW_ACCEPTANCE_OUTCOME_UNCONFIRMED".equals(row.code())).findFirst().orElseThrow();
            assertThat(diagnostic.blocking()).isFalse();
            assertThat(evidence.read(fixture.run(), diagnostic.protectedDetailLocation()).toString())
                    .doesNotContain("Synthetic lost review acknowledgement", "SQL", "password");
        }
    }

    @Test
    void shouldPreservePrimaryFailure_whenEvidenceDeliveryAlsoFails() throws Exception {
        Fixture fixture = stopped();
        var evidence = mock(CdsImportFailureEvidenceService.class);
        var unavailable = new IllegalStateException("Synthetic private evidence failure");
        org.mockito.Mockito.doThrow(unavailable).when(evidence).write(any(), org.mockito.ArgumentMatchers.anyLong(), any());
        try (var facade = facade(evidence)) {
            audit.loseNextAcknowledgement(commits::failNextCommitAcknowledgement);
            CdsImportReviewCommand request = command(fixture, REVISION, 0);
            assertThatThrownBy(() -> facade.resolveReview(viewer, fixture.run(), request))
                    .isInstanceOf(org.springframework.transaction.TransactionSystemException.class)
                    .satisfies(primary -> assertThat(primary.getSuppressed()).containsExactly(unavailable));
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM cds_import_review WHERE run_id=?", Long.class, fixture.run())).isEqualTo(1L);
            var diagnostic = ledger.diagnostics(fixture.run(), 0, 20).stream()
                    .filter(row -> "REVIEW_ACCEPTANCE_OUTCOME_UNCONFIRMED".equals(row.code())).findFirst().orElseThrow();
            assertThat(diagnostic.protectedDetailLocation()).isNull();
            assertThat(diagnostic.blocking()).isFalse();
        }
    }

    private ClosingFacade facade(CdsImportFailureEvidenceService evidence) {
        return new ClosingFacade(ledger, jobs, access, reviews, evidence);
    }

    /** No dispatcher is started; a failed acceptance must not schedule a review. */
    private static final class ClosingFacade extends CdsImportService implements AutoCloseable {
        ClosingFacade(CdsImportLedgerService ledger, CdsImportJobService jobs, CdsImportAccessService access,
                CdsImportReviewService reviews, CdsImportFailureEvidenceService evidence) {
            super(ledger, jobs, mock(CdsImportExecutionService.class), mock(CdsImportResultService.class), access,
                    new CdsImportConfigurationService(new Properties()), mock(CdsImportCleanupService.class), reviews, evidence);
        }
    }

    private Fixture stopped() throws Exception {
        String submission = UUID.randomUUID().toString();
        String run = ledger.accept(new CdsImportJobCommand(submission, actor.operator(), actor.providerNo(), "fixture", "omd-5.2", A, A, A, "/opaque/review")).id();
        var command = new CdsImportCommand(directory.resolve("source.xml"), submission, actor.providerNo(), null, null,
                "fixture", "omd-5.2", false, false, true, 0, null, false);
        when(jobs.root(run)).thenReturn(directory);
        when(jobs.load(run)).thenReturn(new CdsImportJobDocumentDto(command, actor, null, null, directory));
        ledger.diagnostic(run, PATIENT, null, "ERROR", "SOURCE_REVISION_REVIEW_REQUIRED", "PREFLIGHT",
                "/OmdCds[1]/PatientRecord[1]/Demographics[1]", "Synthetic revision requires explicit review", "/opaque/context.json", true);
        ledger.failBeforeExecution(run, "SOURCE_REVISION_REVIEW_REQUIRED");
        long diagnostic = ledger.diagnostics(run, 0, 20).stream()
                .filter(row -> "PREFLIGHT".equals(row.stage())).findFirst().orElseThrow().id();
        return new Fixture(run, diagnostic);
    }

    private static CdsImportReviewCommand command(Fixture fixture, String kind, long version) {
        return new CdsImportReviewCommand(kind, fixture.diagnostic(), PATIENT, null, version,
                "CORRECT_VALUE".equals(kind) ? "/OmdCds[1]/PatientRecord[1]/Demographics[1]/Names[1]/LegalName[1]/FirstName[1]" : null,
                "CORRECT_VALUE".equals(kind) ? "Synthetic replacement" : null, "Synthetic audited review on an unwritten import");
    }

    private record Fixture(String run, long diagnostic) { }
}
