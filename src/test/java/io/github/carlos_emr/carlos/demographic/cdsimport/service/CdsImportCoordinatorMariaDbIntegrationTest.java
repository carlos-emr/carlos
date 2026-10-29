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

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportCheckpointDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDependencyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportEventDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportLineageDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportUnitDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReportEntryDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliSpringConfiguration;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real native control rows and physical connections; no clinical table is written. */
@Tag("integration") @Tag("mariadb")
@Isolated("Exclusive synthetic coordinator and server-global SQL counter measurement")
@EnabledIfSystemProperty(named="cds.test.properties", matches=".+")
class CdsImportCoordinatorMariaDbIntegrationTest {
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    static class PrefetchJpaConfiguration { }

    /** Observes the real Hibernate statement boundary, rather than a mocked DAO invocation. */
    private static final class BindingStatementProbe implements StatementInspector {
        private static final long serialVersionUID=1L;
        private transient EntityManagerFactory factory;
        private transient EntityManager lastManager;
        private Boolean expectedActualTransaction;
        private int statements;
        private int jdbcExecutions;

        private Connection observe(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments)-> {
                        Object value=invoke(delegate, method, arguments);
                        if (method.getName().equals("prepareStatement") && arguments!=null
                                && arguments[0] instanceof String sql && sql.contains("cds_import_run")) {
                            PreparedStatement statement=(PreparedStatement) value;
                            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                    (prepared, operation, values)-> {
                                        if (operation.getName().equals("executeQuery") && Boolean.FALSE.equals(expectedActualTransaction)) {
                                            assertThat(statement.getQueryTimeout()).as("Nontransactional reads retain the explicit JDBC timeout").isEqualTo(120);
                                            assertThat(delegate.getAutoCommit()).isTrue();
                                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                                            jdbcExecutions++;
                                        }
                                        return invoke(statement, operation, values);
                                    });
                        }
                        return value;
                    });
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try {return method.invoke(target, arguments); }
            catch (InvocationTargetException failure) {throw failure.getCause(); }
        }

        @Override public String inspect(String sql) {
            if (expectedActualTransaction!=null && sql.contains("cds_import_run")) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .isEqualTo(expectedActualTransaction);
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                if (!expectedActualTransaction) {
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                }
                Object resource=TransactionSynchronizationManager.getResource(factory);
                assertThat(resource).isInstanceOf(EntityManagerHolder.class);
                lastManager=((EntityManagerHolder) resource).getEntityManager();
                assertThat(lastManager.isOpen()).isTrue();
                statements++;
            }
            return sql;
        }
    }

    private OscarTrackingBasicDataSource source;
    private Connection observer;
    private CdsImportCoordinatorService coordinator;
    private String run;
    private String alternate;
    private String lockName;

    @BeforeEach void prepareOwnControlRows() throws Exception {
        Properties jdbc=new Properties();
        try (InputStream input=Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {jdbc.load(input); }
        String url=jdbc.getProperty("jdbcUrl", "").replace("jdbc:mariadb:", "jdbc:mysql:");
        assertThat(url).matches("jdbc:mysql://[^/]+/(?:carlos_cds_test|cds_control_[0-9a-f]{12}_test)(?:\\?.*)?");
        Properties cli=new Properties();
        cli.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        int database=url.indexOf('/', "jdbc:mysql://".length());
        cli.setProperty("db_uri", url.substring(0, database+1)); cli.setProperty("db_name", url.substring(database+1));
        cli.setProperty("db_username", jdbc.getProperty("jdbcUser")); cli.setProperty("db_password", jdbc.getProperty("jdbcPassword"));
        source=new CdsCliSpringConfiguration().dataSource(cli);
        observer=source.openUnpooledCdsMaintenanceConnection();
        try (var query=observer.createStatement(); var rows=query.executeQuery("SELECT owner_run_id,reserved_bytes FROM cds_import_admission WHERE id='global'")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isNull(); assertThat(rows.getLong(2)).isZero();
        }
        run=UUID.randomUUID().toString(); alternate=UUID.randomUUID().toString();
        try (var query=observer.prepareStatement("""
                INSERT INTO cds_import_run(id,submission_key,dry_run,report_state,cleanup_state,
                    review_version,discovery_complete,discovered_files,patient_xml_files,source_bytes,
                    operator,provider_no,source_namespace,profile,source_digest,options_digest,ruleset_digest,
                    snapshot_location,state,expected_patients,expected_units,owner_epoch,cancel_requested,
                    report_generation,created_at,updated_at)
                VALUES(?,?,0,'NOT_STARTED','NOT_STARTED',0,0,0,0,0,'control-probe','999998','control-probe',
                    'pcon-2024-02',?,?,?,'/opaque/control-probe','RUNNING',0,0,7,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """)) {
            query.setString(1, run); query.setString(2, run);
            for (int index=3; index<=5; index++)query.setString(index, "a".repeat(64));
            assertThat(query.executeUpdate()).isEqualTo(1);
        }
        update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'", run);
        lockName="cds.coordinator."+CdsImportKeys.hash(observer.getCatalog()).substring(0, 40);
        coordinator=new CdsImportCoordinatorService(source);
    }

    @AfterEach void retireOnlyOwnControlRows() throws Exception {
        if (observer!=null) {
            try {
                if (run!=null) {
                    try (var query=observer.createStatement(); var rows=query.executeQuery("SELECT owner_run_id FROM cds_import_admission WHERE id='global'")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isIn(run, alternate, (String) null);
                    }
                    update("UPDATE cds_import_admission SET owner_run_id=NULL WHERE id='global'");
                    update("DELETE FROM cds_import_run WHERE id=?", run);
                }
            } finally {observer.close(); }
        }
        if (source!=null)source.close();
    }

    @Test void eachForcedControlIsOneActualSqlQuery_withoutPoolBorrowOrRetainedTransaction() throws Exception {
        try (var owner=coordinator.acquire()) {
            long connection=lockConnection();
            // Warm the one prepared statement before taking the measured window.
            assertThat(owner.control(run).admissionOwned()).isTrue();
            long before=questions();
            for (int index=0; index<12; index++) {
                var current=owner.control(run);
                assertThat(current.coordinatorOwned()).isTrue(); assertThat(current.runPresent()).isTrue();
                assertThat(current.ownerEpoch()).isEqualTo(7); assertThat(current.cancelRequested()).isFalse();
                assertThat(current.admissionOwned()).isTrue();
            }
            assertThat(questions()-before-1).as("12 real control SELECTs; exclude only this observer's final SHOW").isEqualTo(12);
            try (var query=observer.prepareStatement("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=?")) {
                query.setLong(1, connection);
                try (var rows=query.executeQuery()){rows.next(); assertThat(rows.getLong(1)).isZero(); }
            }
            assertThat(lockConnection()).isEqualTo(connection);
            assertThat(source.getNumActive()).isZero(); assertThat(source.getNumIdle()).isZero();
        }
    }

    @Test void cancellationEpochAdmissionAndMissingRunAreObservedFromFreshSnapshots() throws Exception {
        try (var owner=coordinator.acquire(); var scope=scope(owner)) {
            update("UPDATE cds_import_run SET cancel_requested=1 WHERE id=?", run);
            assertThatThrownBy(()-> scope.check(true)).hasMessage("IMPORT_CANCELLED");
            update("UPDATE cds_import_run SET cancel_requested=0,owner_epoch=8 WHERE id=?", run);
            assertThatThrownBy(()-> scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_run SET owner_epoch=7 WHERE id=?", run);
            update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'", alternate);
            assertThatThrownBy(()-> scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_admission SET owner_run_id=NULL WHERE id='global'");
            assertThatThrownBy(()-> scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'", run);
            update("DELETE FROM cds_import_run WHERE id=?", run);
            assertThat(owner.control(run).runPresent()).isFalse();
            assertThatThrownBy(()-> scope.check(true)).hasMessage("OWNER_FENCED");
        }
    }

    @Test void malformedNativeBooleanFailsClosed() throws Exception {
        try (var owner=coordinator.acquire(); var scope=scope(owner)) {
            update("UPDATE cds_import_run SET cancel_requested=2 WHERE id=?", run);
            assertThatThrownBy(()-> scope.check(true)).hasMessage("IMPORT_CONTROL_UNAVAILABLE");
            update("UPDATE cds_import_run SET cancel_requested=0 WHERE id=?", run);
        }
    }

    @Test void killingTheActualOwnedPhysicalConnectionFencesItsScope_andAnotherNodeCanAcquire() throws Exception {
        var owner=coordinator.acquire(); boolean killed=false;
        try (var scope=scope(owner)) {
            long connection=lockConnection();
            try (var query=observer.createStatement()){query.execute("KILL CONNECTION "+connection); }
            killed=true;
            assertThatThrownBy(()-> scope.check(true)).hasMessage("IMPORT_COORDINATOR_LOST");
            try (var replacement=new CdsImportCoordinatorService(source).acquire()) {
                replacement.check(); assertThat(lockConnection()).isNotEqualTo(connection);
            }
        } finally {
            if (killed)assertThatThrownBy(owner::close).hasMessage("IMPORT_COORDINATOR_RELEASE_FAILED");
            else owner.close();
        }
    }

    @Test void aCompetingNodeCannotAcquireUntilTheOriginalPhysicalOwnerRetires() throws Exception {
        try (var owner=coordinator.acquire()) {
            assertThatThrownBy(()-> new CdsImportCoordinatorService(source).acquire()).hasMessage("IMPORT_COORDINATOR_BUSY");
            owner.check(); assertThat(owner.control(run).coordinatorOwned()).isTrue();
        }
        try (var next=coordinator.acquire()){next.check(); }
    }

    @Test void configuredCliPoolObservesFreshCommittedRowsAndRestoresSpringTransactionState() throws Exception {
        TransactionTemplate transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setReadOnly(true); transaction.setTimeout(10);
        long[] physical={0};
        transaction.executeWithoutResult(status-> {
            Connection connection=DataSourceUtils.getConnection(source);
            try {
                physical[0]=scalar(connection, "SELECT CONNECTION_ID()");
                assertThat(connection.getAutoCommit()).isFalse(); assertThat(connection.isReadOnly()).isTrue();
                assertThat(scalar(connection, "SELECT @@session.autocommit")).isZero();
                assertThat(scalar(connection, "SELECT @@session.tx_read_only")).isEqualTo(1);
                assertThat(isolation(connection)).isEqualTo("READ-COMMITTED");
                assertThat(cancelRequested(connection)).isZero();
                update("UPDATE cds_import_run SET cancel_requested=1 WHERE id=?", run);
                assertThat(cancelRequested(connection)).as("A second READ COMMITTED statement sees the committed control update").isEqualTo(1);
            } catch (java.sql.SQLException failed) {throw new IllegalStateException(failed); }
            finally {DataSourceUtils.releaseConnection(connection, source); }
        });
        assertThat(source.getNumActive()).isZero();
        assertNoTransaction(physical[0]);
        try (Connection connection=source.getConnection()) {
            assertThat(scalar(connection, "SELECT CONNECTION_ID()")).isEqualTo(physical[0]);
            assertThat(connection.getAutoCommit()).isTrue(); assertThat(connection.isReadOnly()).isFalse();
            assertThat(scalar(connection, "SELECT @@session.autocommit")).isEqualTo(1);
            assertThat(isolation(connection)).isEqualTo("READ-COMMITTED");
            assertThat(scalar(connection, "SELECT @@session.tx_read_only")).isZero();
        }
    }

    @Test void configuredCliPoolRollsBackUncommittedWritesAndResetsIsolationOnReuse() throws Exception {
        long physical;
        try (Connection connection=source.getConnection()) {
            physical=scalar(connection, "SELECT CONNECTION_ID()");
            assertThat(isolation(connection)).isEqualTo("READ-COMMITTED");
            assertThat(scalar(connection, "SELECT @@session.autocommit")).isEqualTo(1);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            assertThat(isolation(connection)).isEqualTo("REPEATABLE-READ");
            assertThat(scalar(connection, "SELECT @@session.autocommit")).isZero();
            try (var query=connection.prepareStatement("UPDATE cds_import_run SET cancel_requested=1 WHERE id=?")) {
                query.setString(1, run); assertThat(query.executeUpdate()).isEqualTo(1);
            }
            assertThat(cancelRequested(connection)).isEqualTo(1);
            // Returning an unfinished borrower must roll back, never implicitly commit it.
        }
        assertThat(cancelRequested(observer)).isZero(); assertNoTransaction(physical);
        try (Connection connection=source.getConnection()) {
            assertThat(scalar(connection, "SELECT CONNECTION_ID()")).isEqualTo(physical);
            assertThat(connection.getAutoCommit()).isTrue(); assertThat(connection.isReadOnly()).isFalse();
            assertThat(scalar(connection, "SELECT @@session.autocommit")).isEqualTo(1);
            assertThat(isolation(connection)).isEqualTo("READ-COMMITTED");
            assertThat(cancelRequested(connection)).isZero();
        }
    }

    @Test void configuredCliPoolValidationDiscardsItsKilledIdleConnectionBeforeReplacement() throws Exception {
        long killed;
        try (Connection connection=source.getConnection()) {killed=scalar(connection, "SELECT CONNECTION_ID()"); }
        try (var query=observer.prepareStatement("SELECT DB FROM information_schema.PROCESSLIST WHERE ID=?")) {
            query.setLong(1, killed);
            try (var rows=query.executeQuery()) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(observer.getCatalog());
                assertThat(rows.next()).isFalse();
            }
        }
        assertThat(source.getNumActive()).isZero(); assertThat(source.getNumIdle()).isEqualTo(1);
        try (var query=observer.createStatement()) {query.execute("KILL CONNECTION "+killed); }
        try (Connection replacement=source.getConnection()) {
            long current=scalar(replacement, "SELECT CONNECTION_ID()");
            assertThat(current).isNotEqualTo(killed);
            assertThat(scalar(replacement, "SELECT 1")).isEqualTo(1);
            assertThat(replacement.getAutoCommit()).isTrue(); assertThat(isolation(replacement)).isEqualTo("READ-COMMITTED");
            assertThat(scalar(replacement, "SELECT @@session.autocommit")).isEqualTo(1);
            assertNoTransaction(current);
        }
    }

    @Test void shouldPrefetchOneImmutableBinding_withoutTransactionOrRetainedConnection() throws Exception {
        source.enableCdsMetadataGuard();
        BindingStatementProbe probe=new BindingStatementProbe();
        Path metadata=Files.createTempFile("cds-binding-prefetch-", ".json");
        Files.writeString(metadata, "private bounded metadata fixture");
        try (AnnotationConfigApplicationContext context=realJpaContext(probe)) {
            var factory=context.getBean(EntityManagerFactory.class);
            var dao=context.getBean(CdsImportRunDao.class);
            var ledger=context.getBean(CdsImportLedgerService.class);
            var manager=context.getBean(PlatformTransactionManager.class);
            var transaction=CdsImportLedgerService.template(manager);
            var statistics=factory.unwrap(SessionFactory.class).getStatistics();
            assertThat(AopUtils.isAopProxy(dao)).as("SUPPORTS must override the real inherited REQUIRED advice").isTrue();

            probe.expectedActualTransaction=true;
            CdsImportJobBindingDto expected=transaction.execute(status-> {
                var row=dao.get(run); return new CdsImportJobBindingDto(CdsImportLedgerService.dto(row), row.getSnapshotLocation());
            });
            probe.expectedActualTransaction=false;
            assertThat(ledger.jobBinding(run)).isEqualTo(expected);
            assertThat(expected.documentLocation()).isEqualTo("/opaque/control-probe");
            assertThat(expected.job().ownerEpoch()).isEqualTo(7);
            assertThat(expected.job().manifestDigest()).isNull();
            assertBindingResourcesRetired(factory, probe);

            statistics.clear(); probe.statements=0; probe.jdbcExecutions=0;
            long prefetchedBefore=questions();
            for (int index=0; index<12; index++) {
                assertThat(ledger.jobBinding(run)).isEqualTo(expected);
                assertBindingResourcesRetired(factory, probe);
                // The actual file read occurs only after the connection and owned EntityManager retired.
                assertThat(Files.readString(metadata)).isEqualTo("private bounded metadata fixture");
            }
            long prefetchedAfter=questions();
            long prefetchedQuestions=prefetchedAfter-prefetchedBefore;
            assertThat(probe.statements).isEqualTo(12);
            assertThat(probe.jdbcExecutions).isEqualTo(12);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(12);
            assertThat(statistics.getQueryExecutionCount()).isEqualTo(12);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getTransactionCount()).isZero();

            statistics.clear(); probe.statements=0; probe.expectedActualTransaction=true;
            long legacyBefore=questions();
            for (int index=0; index<12; index++) {
                CdsImportJobBindingDto legacy=transaction.execute(status-> {
                    var row=dao.get(run); return new CdsImportJobBindingDto(CdsImportLedgerService.dto(row), row.getSnapshotLocation());
                });
                assertThat(legacy).isEqualTo(expected);
                assertBindingResourcesRetired(factory, probe);
            }
            long legacyAfter=questions();
            long legacyQuestions=legacyAfter-legacyBefore;
            assertThat(probe.statements).isEqualTo(12);
            assertThat(statistics.getEntityLoadCount()).isEqualTo(12);
            assertThat(statistics.getTransactionCount()).isEqualTo(12);
            assertThat(prefetchedQuestions).as("Raw Questions windows both include the final observer SHOW; no subtraction")
                    .isLessThan(legacyQuestions);
            assertThat(legacyQuestions-prefetchedQuestions).as("At least two fixed session/transaction statements saved per binding")
                    .isGreaterThanOrEqualTo(24);
            System.out.println("CDS_BINDING_PREFETCH_SQL rawNewBefore="+prefetchedBefore+" rawNewAfter="+prefetchedAfter
                    +" rawLegacyBefore="+legacyBefore+" rawLegacyAfter="+legacyAfter
                    +" rawNew="+prefetchedQuestions+" rawLegacy="+legacyQuestions+" calls=12");

            // Exercise both public APIs independently. Keep the earlier R5 windows
            // intact and compare against the exact former transaction/entity reads.
            for (boolean location:java.util.List.of(false,true)) {
                Object publicExpected=location?expected.documentLocation():expected.job();
                probe.expectedActualTransaction=false;
                statistics.clear(); probe.statements=0; probe.jdbcExecutions=0;
                long publicBefore=questions();
                for (int index=0; index<12; index++) {
                    Object actual=location?ledger.snapshotLocation(run):ledger.status(run);
                    assertThat(actual).isEqualTo(publicExpected);
                    assertBindingResourcesRetired(factory, probe);
                    assertThat(Files.readString(metadata)).isEqualTo("private bounded metadata fixture");
                }
                long publicAfter=questions();
                assertThat(probe.statements).isEqualTo(12);
                assertThat(probe.jdbcExecutions).isEqualTo(12);
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(12);
                assertThat(statistics.getQueryExecutionCount()).isEqualTo(12);
                assertThat(statistics.getEntityLoadCount()).isZero();
                assertThat(statistics.getTransactionCount()).isZero();

                probe.expectedActualTransaction=true;
                statistics.clear(); probe.statements=0; probe.jdbcExecutions=0;
                long publicLegacyBefore=questions();
                for (int index=0; index<12; index++) {
                    Object legacy=transaction.execute(status-> {
                        var row=dao.get(run);
                        return location?row.getSnapshotLocation():CdsImportLedgerService.dto(row);
                    });
                    assertThat(legacy).isEqualTo(publicExpected);
                    assertBindingResourcesRetired(factory, probe);
                }
                long publicLegacyAfter=questions();
                assertThat(probe.statements).isEqualTo(12);
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(12);
                assertThat(statistics.getEntityLoadCount()).isEqualTo(12);
                assertThat(statistics.getTransactionCount()).isEqualTo(12);
                assertThat(publicAfter-publicBefore)
                        .as("Both raw windows include their final observer SHOW; no subtraction")
                        .isLessThan(publicLegacyAfter-publicLegacyBefore);
                assertThat(publicLegacyAfter-publicLegacyBefore-(publicAfter-publicBefore))
                        .as("At least two fixed session/transaction statements saved per public read")
                        .isGreaterThanOrEqualTo(24);
                System.out.println((location?"CDS_SNAPSHOT_LOCATION_SQL":"CDS_STANDALONE_STATUS_SQL")
                        +" rawNewBefore="+publicBefore+" rawNewAfter="+publicAfter
                        +" rawLegacyBefore="+publicLegacyBefore+" rawLegacyAfter="+publicLegacyAfter
                        +" rawNew="+(publicAfter-publicBefore)+" rawLegacy="+(publicLegacyAfter-publicLegacyBefore)
                        +" calls=12 entities=0 transactions=0");
            }

            probe.expectedActualTransaction=true;
            CdsImportReportEntryDto entryExpected=transaction.execute(status-> {
                var row=dao.get(run);
                return new CdsImportReportEntryDto("DELIVERED".equals(row.getReportState()), row.isDiscoveryComplete(),
                        row.isDryRun(), row.getExecutionDigest()!=null, row.getSnapshotLocation());
            });
            probe.expectedActualTransaction=false;
            statistics.clear(); probe.statements=0; probe.jdbcExecutions=0;
            long entryBefore=questions();
            for (int index=0; index<12; index++) {
                assertThat(ledger.reportEntry(run)).isEqualTo(entryExpected);
                assertBindingResourcesRetired(factory, probe);
                assertThat(Files.readString(metadata)).isEqualTo("private bounded metadata fixture");
            }
            long entryAfter=questions();
            assertThat(probe.statements).isEqualTo(12);
            assertThat(probe.jdbcExecutions).isEqualTo(12);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(12);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getTransactionCount()).isZero();
            assertThat(entryAfter-entryBefore).as("Report entry uses the already proven scalar binding path")
                    .isEqualTo(prefetchedQuestions);
            assertThat(entryExpected.toString()).isEqualTo("CdsImportReportEntryDto[protected]");

            // Compare the actual replaced entry-summary/path calls, including raw
            // session and transaction statements. The later fresh job-binding read
            // is covered independently above; no owner probe or observer is subtracted.
            probe.expectedActualTransaction=true;
            statistics.clear(); probe.statements=0;
            long entryLegacyBefore=questions();
            for (int index=0; index<12; index++) {
                var previous=ledger.summary(run);
                String legacyLocation=transaction.execute(status->dao.get(run).getSnapshotLocation());
                var legacyEntry=new CdsImportReportEntryDto(Boolean.TRUE.equals(previous.get("reportsDelivered")),
                        Boolean.TRUE.equals(previous.get("discoveryComplete")), Boolean.TRUE.equals(previous.get("dryRun")),
                        "SEALED".equals(previous.get("inventoryState")), legacyLocation);
                assertThat(legacyEntry).isEqualTo(entryExpected);
                assertBindingResourcesRetired(factory, probe);
            }
            long entryLegacyAfter=questions();
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(60);
            assertThat(statistics.getEntityLoadCount()).isEqualTo(24);
            assertThat(statistics.getTransactionCount()).isEqualTo(24);
            assertThat(entryLegacyAfter-entryLegacyBefore-(entryAfter-entryBefore))
                    .as("At least four unused SELECTs removed per entry; raw transaction/session savings also remain visible")
                    .isGreaterThanOrEqualTo(48);

            // The projection is fresh on each invocation; none of these flags authorizes
            // later completion, and an earlier immutable value cannot change in place.
            probe.expectedActualTransaction=false;
            update("UPDATE cds_import_run SET report_state='DELIVERED',discovery_complete=1,dry_run=1,execution_digest=? WHERE id=?",
                    "b".repeat(64), run);
            var changed=ledger.reportEntry(run);
            assertThat(changed).isEqualTo(new CdsImportReportEntryDto(true, true, true, true, entryExpected.documentLocation()));
            assertThat(entryExpected).isEqualTo(new CdsImportReportEntryDto(false, false, false, false, "/opaque/control-probe"));
            assertBindingResourcesRetired(factory, probe);
            for (String reportState:java.util.List.of("delivered", "DELIVERED ")) {
                update("UPDATE cds_import_run SET report_state=?,discovery_complete=1,dry_run=0,execution_digest=NULL WHERE id=?",
                        reportState, run);
                assertThat(ledger.reportEntry(run)).isEqualTo(new CdsImportReportEntryDto(false, true, false, false, entryExpected.documentLocation()));
                assertBindingResourcesRetired(factory, probe);
            }
            update("UPDATE cds_import_run SET report_state='FAILED',discovery_complete=0,dry_run=1,execution_digest=NULL,manifest_digest=? WHERE id=?",
                    "c".repeat(64), run);
            assertThat(ledger.reportEntry(run)).isEqualTo(new CdsImportReportEntryDto(false, false, true, false, entryExpected.documentLocation()));
            assertBindingResourcesRetired(factory, probe);
            update("UPDATE cds_import_run SET report_generation=9,report_location='/opaque/later-report',report_digest=? WHERE id=?",
                    "d".repeat(64), run);
            var freshBinding=ledger.jobBinding(run);
            assertThat(freshBinding.job().reportGeneration()).isEqualTo(9);
            assertBindingResourcesRetired(factory, probe);
            probe.expectedActualTransaction=true;
            var freshExpected=transaction.execute(status->CdsImportLedgerService.dto(dao.get(run)));
            assertThat(freshBinding.job()).isEqualTo(freshExpected);
            assertBindingResourcesRetired(factory, probe);
            probe.expectedActualTransaction=false;
            assertThatThrownBy(()->ledger.reportEntry(alternate)).isInstanceOf(CdsImportException.class).hasMessage("RUN_NOT_FOUND");
            assertBindingResourcesRetired(factory, probe);
            int entryObserved=probe.statements;
            transaction.executeWithoutResult(status-> {
                assertThatThrownBy(()->ledger.reportEntry(run)).isInstanceOf(CdsImportException.class).hasMessage("AMBIENT_TRANSACTION_REFUSED");
                assertThat(probe.statements).isEqualTo(entryObserved);
            });
            System.out.println("CDS_REPORT_ENTRY_SQL rawBefore="+entryBefore+" rawAfter="+entryAfter
                    +" raw="+(entryAfter-entryBefore)+" rawLegacyBefore="+entryLegacyBefore
                    +" rawLegacyAfter="+entryLegacyAfter+" rawLegacy="+(entryLegacyAfter-entryLegacyBefore)
                    +" calls=12 entities=0 transactions=0");

            probe.expectedActualTransaction=false;
            update("UPDATE cds_import_run SET cancel_requested=1 WHERE id=?", run);
            assertThat(ledger.jobBinding(run).job().cancelRequested()).isTrue();
            assertThat(expected.job().cancelRequested()).as("An earlier immutable DTO cannot change in place").isFalse();
            assertBindingResourcesRetired(factory, probe);
            // The maintenance observer writes on an independent physical connection;
            // each subsequent public read must see that committed current row.
            try (Connection connection=source.getConnection()) {
                assertThat(scalar(connection,"SELECT CONNECTION_ID()"))
                        .isNotEqualTo(scalar(observer,"SELECT CONNECTION_ID()"));
            }
            var priorStatus=ledger.status(run);
            assertBindingResourcesRetired(factory, probe);
            String priorLocation=ledger.snapshotLocation(run);
            assertBindingResourcesRetired(factory, probe);
            update("UPDATE cds_import_run SET owner_epoch=8,cancel_requested=0,state='FINALIZING',report_generation=10,report_location='/opaque/current-report',report_digest=?,snapshot_location='/opaque/current-job' WHERE id=?",
                    "e".repeat(64), run);
            var currentStatus=ledger.status(run);
            assertThat(currentStatus.ownerEpoch()).isEqualTo(8);
            assertThat(currentStatus.cancelRequested()).isFalse();
            assertThat(currentStatus.state()).isEqualTo("FINALIZING");
            assertThat(currentStatus.reportGeneration()).isEqualTo(10);
            assertThat(currentStatus.reportLocation()).isEqualTo("/opaque/current-report");
            assertThat(currentStatus.reportDigest()).isEqualTo("e".repeat(64));
            assertBindingResourcesRetired(factory, probe);
            assertThat(ledger.snapshotLocation(run)).isEqualTo("/opaque/current-job");
            assertBindingResourcesRetired(factory, probe);
            assertThat(priorStatus.ownerEpoch()).isEqualTo(7);assertThat(priorStatus.cancelRequested()).isTrue();
            assertThat(priorStatus.reportGeneration()).isEqualTo(9);
            assertThat(priorLocation).isEqualTo("/opaque/control-probe");
            probe.expectedActualTransaction=true;
            assertThat(currentStatus).isEqualTo(transaction.execute(status->CdsImportLedgerService.dto(dao.get(run))));
            assertBindingResourcesRetired(factory, probe);
            probe.expectedActualTransaction=false;
            for (boolean location:java.util.List.of(false,true)) {
                for (String invalid:java.util.Arrays.asList(null,""," ","x".repeat(37))) {
                    int beforeInvalid=probe.statements;
                    assertThatThrownBy(()-> {if(location)ledger.snapshotLocation(invalid);else ledger.status(invalid);})
                            .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid run id");
                    assertThat(probe.statements).isEqualTo(beforeInvalid);
                    assertBindingResourcesRetired(factory, probe);
                }
                assertThatThrownBy(()-> {if(location)ledger.snapshotLocation(alternate);else ledger.status(alternate);})
                        .isInstanceOf(CdsImportException.class).hasMessage("RUN_NOT_FOUND");
                assertBindingResourcesRetired(factory, probe);
                int beforeAmbient=probe.statements;
                transaction.executeWithoutResult(status-> {
                    for (String value:java.util.Arrays.asList(null,run))
                        assertThatThrownBy(()-> {if(location)ledger.snapshotLocation(value);else ledger.status(value);})
                                .isInstanceOf(CdsImportException.class).hasMessage("AMBIENT_TRANSACTION_REFUSED");
                    assertThat(probe.statements).isEqualTo(beforeAmbient);
                });
                assertBindingResourcesRetired(factory, probe);
            }
            assertThatThrownBy(()-> ledger.jobBinding(alternate)).isInstanceOf(CdsImportException.class).hasMessage("RUN_NOT_FOUND");
            assertBindingResourcesRetired(factory, probe);
            int observed=probe.statements;
            transaction.executeWithoutResult(status-> {
                assertThatThrownBy(()-> ledger.jobBinding(run)).isInstanceOf(CdsImportException.class).hasMessage("AMBIENT_TRANSACTION_REFUSED");
                assertThat(probe.statements).isEqualTo(observed);
            });
            assertThat(source.getNumActive()).isZero();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        } finally {Files.deleteIfExists(metadata); }
    }

    @Test void shouldKeepCompositeStatusCoherent_whenReportDeliveryCommitsBetweenAuthorizationAndAggregates() throws Exception {
        source.enableCdsMetadataGuard();
        update("UPDATE cds_import_run SET state='INCOMPLETE',report_generation=1,report_state='PENDING',cleanup_state='PENDING' WHERE id=?", run);
        BindingStatementProbe probe=new BindingStatementProbe();
        try (AnnotationConfigApplicationContext context=realJpaContext(probe)) {
            var factory=context.getBean(EntityManagerFactory.class);
            var ledger=context.getBean(CdsImportLedgerService.class);
            var manager=context.getBean(PlatformTransactionManager.class);
            var statistics=factory.unwrap(SessionFactory.class).getStatistics();
            var viewer=org.mockito.Mockito.mock(LoggedInInfo.class);
            var access=org.mockito.Mockito.mock(CdsImportAccessService.class);
            var reviews=org.mockito.Mockito.mock(CdsImportReviewService.class);
            var observations=new CdsImportObservationService(ledger, reviews, access, manager);
            AtomicInteger authorizations=new AtomicInteger();
            org.mockito.Mockito.when(reviews.summaryInReadTransaction(run)).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(authorizations.get()).isBetween(1, 2);
                return java.util.Map.of();
            });
            org.mockito.Mockito.doAnswer(call -> {
                var job=call.getArgument(1, CdsImportJobDto.class);
                int attempt=authorizations.incrementAndGet();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                        .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                assertThat(statistics.getPrepareStatementCount())
                        .as("Authorization precedes every aggregate; only the current run row has been read").isEqualTo(1);
                var holder=(EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
                assertThat(holder).isNotNull();
                holder.getEntityManager().unwrap(org.hibernate.Session.class).doWork(connection -> {
                    assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                    assertThat(isolation(connection)).isEqualTo("REPEATABLE-READ");
                    assertThat(connection.getAutoCommit()).isFalse();
                    assertThat(scalar(connection, "SELECT CONNECTION_ID()"))
                            .as("The committed writer uses a separate physical connection").isNotEqualTo(scalar(observer, "SELECT CONNECTION_ID()"));
                    if (attempt==1) {
                        assertThat(job.reportGeneration()).isEqualTo(1);
                        assertThat(job.cancelRequested()).isFalse();
                        assertThat(reportGeneration(connection)).isEqualTo(1);
                        assertThat(observer.getAutoCommit()).isTrue();
                        update("UPDATE cds_import_run SET report_generation=2,report_state='DELIVERED',cleanup_state='COMPLETE',cancel_requested=1 WHERE id=?", run);
                        assertThat(reportGeneration(observer)).as("The external delivery really committed").isEqualTo(2);
                        assertThat(reportGeneration(connection))
                                .as("A fresh JDBC SELECT, not the first-level entity cache, retains this read's snapshot").isEqualTo(1);
                        assertThat(cancelRequested(connection)).isZero();
                    } else {
                        assertThat(attempt).isEqualTo(2);
                        assertThat(job.reportGeneration()).isEqualTo(2);
                        assertThat(job.cancelRequested()).isTrue();
                        assertThat(reportGeneration(connection)).isEqualTo(2);
                        assertThat(cancelRequested(connection)).isEqualTo(1);
                    }
                });
                return null;
            }).when(access).requireView(org.mockito.ArgumentMatchers.same(viewer), org.mockito.ArgumentMatchers.any(CdsImportJobDto.class));

            probe.expectedActualTransaction=true;
            statistics.clear();
            var before=observations.status(viewer, run);
            assertThat(before.job().reportGeneration()).isEqualTo(1);
            assertThat(before.job().cancelRequested()).isFalse();
            assertThat(before.summary()).containsEntry("state", "INCOMPLETE")
                    .containsEntry("reportState", "PENDING").containsEntry("reportsDelivered", false)
                    .containsEntry("cleanupState", "PENDING").containsEntry("operationTerminal", false)
                    .containsEntry("resumeRequested", false).containsEntry("cleanComplete", false);
            assertBindingResourcesRetired(factory, probe);
            assertReadCommittedPoolBaseline();

            statistics.clear();
            var after=observations.status(viewer, run);
            assertThat(after.job().reportGeneration()).isEqualTo(2);
            assertThat(after.job().cancelRequested()).isTrue();
            assertThat(after.summary()).containsEntry("state", "INCOMPLETE")
                    .containsEntry("reportState", "DELIVERED").containsEntry("reportsDelivered", true)
                    .containsEntry("cleanupState", "COMPLETE").containsEntry("cleanupComplete", true)
                    .containsEntry("operationTerminal", true).containsEntry("resumeRequested", false)
                    .containsEntry("clinicalComplete", false).containsEntry("cleanComplete", false);
            assertThat(before.job().reportGeneration()).as("The earlier protected DTO remains immutable").isEqualTo(1);
            assertThat(before.summary()).containsEntry("reportsDelivered", false).containsEntry("operationTerminal", false);
            assertThat(authorizations).hasValue(2);
            org.mockito.Mockito.verify(access, org.mockito.Mockito.times(2))
                    .requireView(org.mockito.ArgumentMatchers.same(viewer), org.mockito.ArgumentMatchers.any(CdsImportJobDto.class));
            org.mockito.Mockito.verify(reviews, org.mockito.Mockito.times(2)).summaryInReadTransaction(run);
            assertBindingResourcesRetired(factory, probe);
            assertReadCommittedPoolBaseline();
        }
    }

    private AnnotationConfigApplicationContext realJpaContext(BindingStatementProbe probe) {
        var context=new AnnotationConfigApplicationContext();
        try {
            context.register(PrefetchJpaConfiguration.class);
            // Each test owns the configured pool; closing the small JPA context must not close it.
            context.registerBean("dataSource", OscarTrackingBasicDataSource.class, ()-> source,
                    definition-> definition.setDestroyMethodName(""));
            context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, ()-> {
                var factory=new LocalContainerEntityManagerFactoryBean();
                factory.setPersistenceUnitName("entityManagerFactory");
                factory.setDataSource(new DelegatingDataSource(source) {
                    @Override public Connection getConnection() throws java.sql.SQLException {
                        return probe.observe(super.getConnection());
                    }
                    @Override public Connection getConnection(String username, String password) throws java.sql.SQLException {
                        return probe.observe(super.getConnection(username, password));
                    }
                });
                factory.setManagedTypes(PersistenceManagedTypes.of(CdsImportRun.class.getName(),
                        io.github.carlos_emr.carlos.commn.model.CdsImportPatient.class.getName(),
                        io.github.carlos_emr.carlos.commn.model.CdsImportUnit.class.getName(),
                        io.github.carlos_emr.carlos.commn.model.CdsImportDiagnostic.class.getName()));
                factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                var settings=new HashMap<String, Object>();
                settings.put("hibernate.hbm2ddl.auto", "none");
                settings.put("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
                settings.put("hibernate.generate_statistics", "true");
                settings.put("hibernate.cache.use_second_level_cache", "false");
                settings.put("hibernate.cache.use_query_cache", "false");
                settings.put("hibernate.jdbc.time_zone", "UTC");
                settings.put("hibernate.session_factory.statement_inspector", probe);
                factory.setJpaPropertyMap(settings); return factory;
            });
            context.registerBean("transactionManager", PlatformTransactionManager.class,
                    ()-> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
            context.registerBean("persistenceAnnotationBeanPostProcessor", PersistenceAnnotationBeanPostProcessor.class);
            context.register(CdsImportRunDao.class, CdsImportPatientDao.class, CdsImportUnitDao.class,
                    CdsImportDependencyDao.class, CdsImportLineageDao.class, CdsImportCheckpointDao.class,
                    CdsImportDiagnosticDao.class, CdsImportAdmissionDao.class, CdsImportEventDao.class,
                    CdsImportIdentityDao.class, CdsImportLedgerService.class);
            context.refresh();
            probe.factory=context.getBean(EntityManagerFactory.class);
            return context;
        } catch (RuntimeException | Error failure) {
            context.close(); throw failure;
        }
    }

    private void assertReadCommittedPoolBaseline() throws java.sql.SQLException {
        try (Connection connection=source.getConnection()) {
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(isolation(connection)).isEqualTo("READ-COMMITTED");
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.isReadOnly()).isFalse();
        }
        assertThat(source.getNumActive()).isZero();
    }

    private void assertBindingResourcesRetired(EntityManagerFactory factory, BindingStatementProbe probe) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(factory)).isFalse();
        assertThat(probe.lastManager).isNotNull(); assertThat(probe.lastManager.isOpen()).isFalse();
        assertThat(source.getNumActive()).isZero();
    }

    @Test void shouldAddStrictAllTablesToAnEmptyMode_whenConfiguringCliPool() throws Exception {
        configureSqlMode("");
        String global=sqlMode(observer, "GLOBAL");
        try (Connection unpooled=source.openUnpooledCdsMaintenanceConnection()) {
            assertThat(sqlModes(unpooled)).isEmpty();
        }
        try (Connection connection=source.getConnection()) {
            assertThat(sqlModes(connection)).containsExactly("STRICT_ALL_TABLES");
        }
        assertThat(sqlMode(observer, "GLOBAL")).isEqualTo(global);
    }

    @Test void shouldPreserveEveryConfiguredMode_whenAddingStrictCliBaseline() throws Exception {
        configureSqlMode("ANSI_QUOTES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION");
        java.util.Set<String> configured;
        try (Connection unpooled=source.openUnpooledCdsMaintenanceConnection()) {
            configured=sqlModes(unpooled);
            assertThat(configured).containsExactlyInAnyOrder("ANSI_QUOTES", "NO_BACKSLASH_ESCAPES", "NO_ENGINE_SUBSTITUTION");
        }
        var expected=new java.util.HashSet<>(configured); expected.add("STRICT_ALL_TABLES");
        try (Connection connection=source.getConnection()) {
            assertThat(sqlModes(connection)).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test void shouldKeepAlreadyStrictMode_whenGuardedTruncationRollsBackAndConnectionIsReused() throws Exception {
        configureSqlMode("STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION,ERROR_FOR_DIVISION_BY_ZERO");
        java.util.Set<String> configured;
        try (Connection unpooled=source.openUnpooledCdsMaintenanceConnection()) {configured=sqlModes(unpooled); }
        source.enableCdsMetadataGuard();
        long physical;
        try (Connection connection=source.getConnection()) {
            physical=scalar(connection, "SELECT CONNECTION_ID()");
            assertThat(sqlModes(connection)).containsExactlyInAnyOrderElementsOf(configured);
            connection.setAutoCommit(false);
            try (var query=connection.prepareStatement("UPDATE cds_import_run SET operator=? WHERE id=?")) {
                query.setString(1, "x".repeat(129)); query.setString(2, run);
                assertThatThrownBy(query::executeUpdate).isInstanceOfSatisfying(java.sql.SQLException.class, failure -> {
                    assertThat(failure.getSQLState()).isEqualTo("22001");
                    assertThat(failure.getErrorCode()).isEqualTo(1406);
                });
            }
            connection.rollback();
            assertThat(sqlModes(connection)).containsExactlyInAnyOrderElementsOf(configured);
        }
        try (Connection reused=source.getConnection()) {
            assertThat(scalar(reused, "SELECT CONNECTION_ID()")).isEqualTo(physical);
            assertThat(sqlModes(reused)).containsExactlyInAnyOrderElementsOf(configured);
            assertThat(scalar(reused, "SELECT @@SESSION.autocommit")).isEqualTo(1);
        }
        try (var query=observer.prepareStatement("SELECT operator FROM cds_import_run WHERE id=?")) {
            query.setString(1, run);
            try (var rows=query.executeQuery()) {assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("control-probe"); }
        }
        assertNoTransaction(physical);
    }

    @Test void shouldRefuseBorrow_whenInitializationSqlModeIsRejected() throws Exception {
        var initialization=new java.util.ArrayList<>(source.getConnectionInitSqls());
        initialization.add("SET SESSION sql_mode='CDS_INVALID_SQL_MODE'");
        source.setConnectionInitSqls(initialization);
        assertThatThrownBy(source::getConnection).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CDS_INVALID_SQL_MODE");
        assertThat(source.getNumActive()).isZero(); assertThat(source.getNumIdle()).isZero();
        assertThat(cancelRequested(observer)).isZero();
    }

    private void configureSqlMode(String modes) {
        source.addConnectionProperty("sessionVariables", "sql_mode='"+modes+"'");
        // The real server must reject truncation even when the driver does not add strictness.
        source.addConnectionProperty("jdbcCompliantTruncation", "false");
    }
    private static java.util.Set<String> sqlModes(Connection connection) throws java.sql.SQLException {
        String modes=sqlMode(connection, "SESSION");
        return modes.isEmpty()?java.util.Set.of():java.util.Set.copyOf(java.util.Arrays.asList(modes.split(",")));
    }
    private static String sqlMode(Connection connection, String scope) throws java.sql.SQLException {
        String statement=switch (scope) {
            case "SESSION" -> "SELECT @@SESSION.sql_mode";
            case "GLOBAL" -> "SELECT @@GLOBAL.sql_mode";
            default -> throw new IllegalArgumentException("Unsupported SQL mode scope");
        };
        try (var query=connection.createStatement(); var rows=query.executeQuery(statement)) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }

    private int cancelRequested(Connection connection) throws java.sql.SQLException {
        try (var query=connection.prepareStatement("SELECT cancel_requested FROM cds_import_run WHERE id=?")) {
            query.setString(1, run);
            try (var rows=query.executeQuery()) {assertThat(rows.next()).isTrue(); return rows.getInt(1); }
        }
    }
    private long reportGeneration(Connection connection) throws java.sql.SQLException {
        try (var query=connection.prepareStatement("SELECT report_generation FROM cds_import_run WHERE id=?")) {
            query.setString(1, run);
            try (var rows=query.executeQuery()) {assertThat(rows.next()).isTrue(); return rows.getLong(1); }
        }
    }
    private static String isolation(Connection connection) throws java.sql.SQLException {
        try (var query=connection.createStatement(); var rows=query.executeQuery("SELECT @@session.transaction_isolation")) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }
    private void assertNoTransaction(long physical) throws java.sql.SQLException {
        try (var query=observer.prepareStatement("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=?")) {
            query.setLong(1, physical);
            try (var rows=query.executeQuery()) {assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero(); }
        }
    }
    private static long scalar(Connection connection, String statement) throws java.sql.SQLException {
        try (var query=connection.createStatement(); var rows=query.executeQuery(statement)) {
            assertThat(rows.next()).isTrue(); return rows.getLong(1);
        }
    }

    private CdsImportExecutionControl scope(CdsImportCoordinatorService.Owner owner) {
        return new CdsImportExecutionControl(run, 7, owner, "IMPORT_FILE_VERIFICATION_TIMEOUT", 10000,
                System::nanoTime, ()-> {}, new CdsFieldParser());
    }
    private void update(String statement, String... values) throws java.sql.SQLException {
        try (var query=observer.prepareStatement(statement)) {
            for (int index=0; index<values.length; index++)query.setString(index+1, values[index]);
            query.executeUpdate();
        }
    }
    private long questions() throws Exception {
        try (var query=observer.createStatement(); var rows=query.executeQuery("SHOW GLOBAL STATUS LIKE 'Questions'")) {
            assertThat(rows.next()).isTrue(); return rows.getLong(2);
        }
    }
    private long lockConnection() throws Exception {
        try (var query=observer.prepareStatement("SELECT IS_USED_LOCK(?)")) {
            query.setString(1, lockName);
            try (var rows=query.executeQuery()){assertThat(rows.next()).isTrue(); long result=rows.getLong(1); assertThat(rows.wasNull()).isFalse(); return result; }
        }
    }
}
