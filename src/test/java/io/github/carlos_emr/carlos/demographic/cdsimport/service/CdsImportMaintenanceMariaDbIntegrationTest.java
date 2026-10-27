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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionGuard;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import static org.assertj.core.api.Assertions.*;

/**
 * Required real-MariaDB tier, configured with a protected jdbcUrl/jdbcUser/jdbcPassword file.
 * Uses two independent datasource/node instances and the actual database lock/commit behavior.
 * The schema must have the real CDS migration applied. No existing clinical table is modified.
 */
@Tag("integration")
@Tag("mariadb")
@Isolated("Physical database-wide CDS maintenance state")
@EnabledIfSystemProperty(named = "cds.test.properties", matches = ".+")
class CdsImportMaintenanceMariaDbIntegrationTest {
    private record AdmissionSnapshot(boolean active,long generation,String owner,Long connection) { }
    private static AdmissionSnapshot initialAdmission;
    private static Properties initialJdbc;

    @BeforeAll
    static void preserveSyntheticAdmission() throws Exception {
        initialJdbc=new Properties();
        try(InputStream input=Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {
            initialJdbc.load(input);
        }
        assertThat(initialJdbc.getProperty("jdbcUrl","")).contains("carlos_cds_test");
        try(var source=dataSource(initialJdbc); Connection raw=source.openUnpooledCdsMaintenanceConnection()) {
            String lockName=new io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionState(raw.getCatalog()).lockName();
            try(var query=raw.prepareStatement("SELECT IS_USED_LOCK(?)")) {
                query.setString(1,lockName);
                try(var rows=query.executeQuery()) { rows.next(); assertThat(rows.getObject(1)).isNull(); }
            }
            try(var query=raw.createStatement(); var rows=query.executeQuery("""
                    SELECT maintenance_active,maintenance_generation,maintenance_owner_token,maintenance_connection_id
                    FROM cds_import_admission WHERE id='clinical-maintenance'
                    """)) {
                assertThat(rows.next()).isTrue();
                initialAdmission=new AdmissionSnapshot(rows.getBoolean(1),rows.getLong(2),rows.getString(3),(Long)rows.getObject(4));
            }
        }
    }

    @AfterAll
    static void restoreSyntheticAdmission() throws Exception {
        if(initialAdmission==null) return;
        // This fixture runs in an exclusive synthetic DB window; retain any stopped CLI's
        // pre-existing durable maintenance decision instead of silently reopening it.
        try(var source=dataSource(initialJdbc); Connection raw=source.openUnpooledCdsMaintenanceConnection();
                var update=raw.prepareStatement("""
                    UPDATE cds_import_admission SET maintenance_active=?,maintenance_generation=?,
                        maintenance_owner_token=?,maintenance_connection_id=? WHERE id='clinical-maintenance'
                    """)) {
            update.setBoolean(1,initialAdmission.active()); update.setLong(2,initialAdmission.generation());
            update.setString(3,initialAdmission.owner()); update.setObject(4,initialAdmission.connection());
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }
    private Properties jdbcProperties;
    private OscarTrackingBasicDataSource nodeOne;
    private OscarTrackingBasicDataSource nodeTwo;
    private CdsImportMaintenanceService first;
    private CdsImportMaintenanceService second;
    private ExecutorService worker;
    private final List<CdsImportMaintenanceService.Owner> owners = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Properties jdbc = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {
            jdbc.load(input);
        }
        assertThat(jdbc.getProperty("jdbcUrl", "").contains("carlos_cds_test")).isTrue();
        jdbcProperties = jdbc;
        nodeOne = dataSource(jdbc);
        nodeTwo = dataSource(jdbc);
        try (Connection raw = nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql = raw.createStatement()) {
            try (ResultSet version = sql.executeQuery("SELECT VERSION()")) {
                version.next(); assertThat(version.getString(1)).contains("MariaDB");
            }
            sql.executeUpdate("UPDATE cds_import_admission SET maintenance_active=FALSE, maintenance_owner_token=NULL, maintenance_connection_id=NULL WHERE id='clinical-maintenance'");
            sql.execute("CREATE TABLE IF NOT EXISTS cds_guard_probe (id INT PRIMARY KEY, probe_value VARCHAR(40) NOT NULL) ENGINE=InnoDB");
            sql.executeUpdate("DELETE FROM cds_guard_probe");
            sql.executeUpdate("INSERT INTO cds_guard_probe VALUES (1,'initial')");
        }
        new CdsJdbcAdmissionInitializer(nodeOne, true, "validate").initialize();
        new CdsJdbcAdmissionInitializer(nodeTwo, true, "validate").initialize();
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.maintenanceMode", "true");
        first = new CdsImportMaintenanceService(nodeOne, new CdsImportConfigurationService(policy));
        second = new CdsImportMaintenanceService(nodeTwo, new CdsImportConfigurationService(policy));
        worker = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (worker != null) { worker.shutdownNow(); worker.awaitTermination(5, TimeUnit.SECONDS); }
        for (var owner : owners) {
            try { owner.close(); } catch (CdsImportException expectedLostOwner) { /* fault tier */ }
        }
        if (nodeOne != null) {
            try (Connection raw = nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql = raw.createStatement()) {
                sql.executeUpdate("UPDATE cds_import_admission SET maintenance_active=FALSE, maintenance_owner_token=NULL, maintenance_connection_id=NULL WHERE id='clinical-maintenance'");
                sql.executeUpdate("DELETE FROM cds_import_preparation WHERE id='cds-maintenance-metadata-test'");
                sql.executeUpdate("DELETE FROM cds_import_patient WHERE id='cds-maintenance-hold-test'");
                sql.executeUpdate("DELETE FROM cds_import_run WHERE id='cds-maintenance-hold-test'");
                sql.execute("DROP TABLE IF EXISTS cds_guard_probe");
            }
            nodeOne.close();
        }
        if (nodeTwo != null) nodeTwo.close();
    }

    @Test
    void shouldDrainAWriterFromAnotherNode_beforeExclusiveAcquisitionReturns() throws Exception {
        try (Connection old = nodeTwo.getConnection()) {
            old.setAutoCommit(false);
            update(old, "old-transaction");
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> acquireOnly(first));
            assertBlocked(entering);
            old.commit();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
                verify(owner);
                assertThat(value()).isEqualTo("old-transaction");
                try (Connection ordinary = nodeTwo.getConnection()) {
                    assertThatThrownBy(() -> update(ordinary, "must-not-commit")).isInstanceOf(SQLException.class);
                    try (Statement deniedQuery = ordinary.createStatement()) {
                        assertThatThrownBy(() -> deniedQuery.executeQuery("SELECT * FROM cds_guard_probe"))
                                .isInstanceOf(SQLException.class);
                    }
                }
                try (Connection imported = CdsJdbcAdmissionGuard.wrap(nodeOne.openUnpooledCdsMaintenanceConnection(),
                        nodeOne.getCdsAdmissionState(), () -> true)) {
                    imported.setAutoCommit(false);
                    update(imported, "owned-unit");
                    imported.commit();
                }
                assertThat(value()).isEqualTo("owned-unit");
                owner.allowReopen();
            }
            try (Connection ordinary = nodeTwo.getConnection()) { update(ordinary, "reopened"); }
            assertThat(value()).isEqualTo("reopened");
        }
    }

    @Test
    void shouldRetainAutocommitQueryAdmission_untilItsResultsAreClosed() throws Exception {
        try (Connection connection = nodeTwo.getConnection(); Statement query = connection.createStatement()) {
            ResultSet rows = query.executeQuery("SELECT * FROM cds_guard_probe");
            assertThat(connection.getAutoCommit()).isTrue();
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> acquireOnly(first));
            assertBlocked(entering);
            rows.close();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
                verify(owner);
                Connection metadataConnection = connection.getMetaData().getConnection().unwrap(Connection.class);
                assertThatThrownBy(() -> update(metadataConnection, "unwrap-escape")).isInstanceOf(SQLException.class);
                Connection statementConnection = query.getConnection();
                assertThatThrownBy(() -> update(statementConnection, "statement-escape")).isInstanceOf(SQLException.class);
                owner.allowReopen();
            }
        }
    }

    @Test
    void shouldDrainActualHibernateWork_beforeMaintenanceAndRefuseFurtherOrmAccess() throws Exception {
        Configuration configuration = new Configuration().addAnnotatedClass(Probe.class);
        configuration.getProperties().put("hibernate.connection.datasource", nodeTwo);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        try (SessionFactory factory = configuration.buildSessionFactory(); Session session = factory.openSession()) {
            var transaction = session.beginTransaction();
            Probe probe = session.find(Probe.class, 1);
            probe.value = "orm-before-window";
            session.flush();
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> acquireOnly(first));
            assertBlocked(entering);
            transaction.commit();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
                verify(owner);
                assertThat(value()).isEqualTo("orm-before-window");
                try (Session rejected = factory.openSession()) {
                    var attempt = rejected.beginTransaction();
                    assertThatThrownBy(() -> rejected.find(Probe.class, 1)).isInstanceOf(RuntimeException.class);
                    attempt.rollback();
                }
                owner.allowReopen();
            }
        }
    }

    @Test
    void shouldFenceNewUnitsAndDrainOldUnits_afterAdvisoryConnectionLoss() throws Exception {
        var previous = own(first);
        try (Connection unit = CdsJdbcAdmissionGuard.wrap(nodeOne.openUnpooledCdsMaintenanceConnection(),
                nodeOne.getCdsAdmissionState(), () -> true)) {
            unit.setAutoCommit(false);
            update(unit, "old-owner-unit");
            try (Connection raw = nodeOne.openUnpooledCdsMaintenanceConnection(); Statement kill = raw.createStatement()) {
                kill.execute("KILL CONNECTION " + previous.connectionId());
            }
            Future<CdsImportMaintenanceService.Owner> takeover = worker.submit(() -> acquireOnly(second));
            assertBlocked(takeover);
            unit.commit();
            try (var successor = takeover.get(5, TimeUnit.SECONDS)) {
                verify(successor);
                assertThat(value()).isEqualTo("old-owner-unit");
                assertThatThrownBy(() -> update(unit, "stale-owner")).isInstanceOf(SQLException.class);
                assertThatThrownBy(previous::close).isInstanceOf(CdsImportException.class);
                successor.allowReopen();
            }
        }
    }

    @Test
    void shouldRetainDurableMaintenance_whenOwnerClosesWithoutSuccessfulReopening() throws Exception {
        var previous = own(first);
        var safety = new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()));
        assertThat(second.reconcileCompletedRelease(safety))
                .isEqualTo(CdsImportMaintenanceService.ReleaseStatus.OWNER_BUSY);
        previous.close();
        assertThat(first.isClinicalAccessBlocked()).isTrue();
        try (Connection ordinary = nodeTwo.getConnection()) {
            assertThatThrownBy(() -> update(ordinary, "must-remain-held")).isInstanceOf(SQLException.class);
        }
        assertThat(second.reconcileCompletedRelease(safety))
                .isEqualTo(CdsImportMaintenanceService.ReleaseStatus.RELEASED);
        assertThat(second.isClinicalAccessBlocked()).isFalse();
    }

    @Test
    void shouldAllowOnlyBoundedCoordinatorReads_whenOwnerReadCapabilityIsUsed() throws Exception {
        try (var owner = own(first)) {
            owner.read(() -> {
                try (Connection connection = nodeOne.getConnection(); Statement statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    try (ResultSet rows = statement.executeQuery("SELECT probe_value FROM cds_guard_probe WHERE id=1")) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("initial");
                    }
                    assertThatThrownBy(() -> update(connection, "read-capability-write")).isInstanceOf(SQLException.class);
                    connection.rollback();
                } catch (SQLException failure) { throw new IllegalStateException(failure); }
                return null;
            });
            var checked=new java.io.IOException("synthetic checked I/O failure");
            assertThatThrownBy(()->owner.readIo(()->{
                try(Connection connection=nodeOne.getConnection(); Statement query=connection.createStatement();
                        ResultSet rows=query.executeQuery("SELECT probe_value FROM cds_guard_probe WHERE id=1")) {
                    assertThat(rows.next()).isTrue();
                } catch(SQLException unexpected) { throw new IllegalStateException(unexpected); }
                throw checked;
            })).isSameAs(checked);
            try(Connection outside=nodeOne.getConnection(); Statement query=outside.createStatement()) {
                assertThatThrownBy(()->query.executeQuery("SELECT probe_value FROM cds_guard_probe WHERE id=1"))
                        .isInstanceOf(SQLException.class);
            }
            assertThat(value()).isEqualTo("initial");
            owner.allowReopen();
        }
    }

    @Test
    void shouldPreserveOrdinaryDatasourceBehavior_whenGuardIsDisabledAndNoWindowIsActive() throws Exception {
        try (var unguarded = dataSource(jdbcProperties)) {
            new CdsJdbcAdmissionInitializer(unguarded, false, "off").initialize();
            assertThat(unguarded.getCdsAdmissionState()).isNull();
            try (Connection connection = unguarded.getConnection()) { update(connection, "guard-disabled"); }
            assertThat(value()).isEqualTo("guard-disabled");
        }
    }

    @Test
    void shouldRefuseAnOffNodeStartup_whenMaintenanceIsEnteringOrActive() throws Exception {
        try (var owner = own(first); var unguarded = dataSource(jdbcProperties)) {
            assertThatThrownBy(() -> new CdsJdbcAdmissionInitializer(unguarded, false, "off").initialize())
                    .isInstanceOf(IllegalStateException.class);
            owner.allowReopen();
        }
    }

    @Test
    void shouldKeepClinicalAccessHeld_whenSuccessfulOwnerStillHasAnIncompleteChart() throws Exception {
        var owner=own(first);
        try (Connection raw=nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql=raw.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO cds_import_run SET id='cds-maintenance-hold-test',submission_key='cds-maintenance-hold-test',
                    dry_run=FALSE,report_state='PENDING',cleanup_state='PENDING',review_version=0,discovery_complete=TRUE,
                    discovered_files=1,patient_xml_files=1,source_bytes=1,operator='maintenance-test',provider_no='test',
                    source_namespace='test',profile='test',source_digest='test',options_digest='test',ruleset_digest='test',
                    snapshot_location='test',state='IMPORTING',expected_patients=1,expected_units=1,owner_epoch=1,
                    cancel_requested=FALSE,report_generation=0,created_at=NOW(),updated_at=NOW()
                    """);
            sql.executeUpdate("""
                    INSERT INTO cds_import_patient SET id='cds-maintenance-hold-test',run_id='cds-maintenance-hold-test',
                    patient_key='test',source_patient_id='test',copy_key='test',lineage_key='test',source_digest='test',
                    state='IMPORTING',held=TRUE,expected_units=1
                    """);
        }
        owner.allowReopen(); owner.close();
        var safety = new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()));
        assertThat(second.reconcileCompletedRelease(safety))
                .isEqualTo(CdsImportMaintenanceService.ReleaseStatus.GLOBAL_HOLDS_REMAIN);
        assertThat(first.isClinicalAccessBlocked()).isTrue();
        try (Connection connection=nodeTwo.getConnection()) {
            assertThatThrownBy(()->update(connection,"held-chart-escape")).isInstanceOf(SQLException.class);
        }
        try (var unguarded=dataSource(jdbcProperties)) {
            assertThatThrownBy(()->new CdsJdbcAdmissionInitializer(unguarded,false,"off").initialize())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldUseStrictImportModeAndRestoreThePhysicalPoolSession_whenLegacySessionAllowsTruncation() throws Exception {
        nodeOne.setConnectionProperties("sessionVariables=sql_mode='';jdbcCompliantTruncation=false");
        nodeOne.setMaxTotal(1); nodeOne.setMaxIdle(1);
        String unicode="\uD83D\uDE00".repeat(39);
        try (var owner=own(first)) {
            assertThat(nodeOne.getNumActive()).isZero(); // Dedicated owner is outside the pool.
            long physical;
            try (Connection connection=nodeOne.getConnection()) {
                assertThat(sessionValue(connection,"sql_mode")).isEmpty();
                physical=Long.parseLong(sessionValue(connection,"connection_id"));
                try (var marker=org.mockito.Mockito.mockStatic(CdsImportUnitService.class)) {
                    marker.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
                    connection.setAutoCommit(false);
                    update(connection,unicode);
                    assertThat(sessionValue(connection,"sql_mode")).contains("STRICT_ALL_TABLES");
                    connection.commit();
                }
                assertThat(sessionValue(connection,"sql_mode")).isEmpty();
            }
            try (Connection reused=nodeOne.getConnection()) {
                assertThat(Long.parseLong(sessionValue(reused,"connection_id"))).isEqualTo(physical);
                assertThat(sessionValue(reused,"sql_mode")).isEmpty();
            }
            assertThat(value()).isEqualTo(unicode);
            owner.allowReopen();
        }
    }

    @Test
    void shouldRejectNativeTruncationAndRestoreMode_whenImportUnitRollsBack() throws Exception {
        nodeOne.setConnectionProperties("sessionVariables=sql_mode='';jdbcCompliantTruncation=false");
        try (var owner=own(first); Connection connection=nodeOne.getConnection()) {
            assertThat(sessionValue(connection,"sql_mode")).isEmpty();
            try (var marker=org.mockito.Mockito.mockStatic(CdsImportUnitService.class)) {
                marker.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
                connection.setAutoCommit(false);
                assertThatThrownBy(()->update(connection,"\uD83D\uDE00".repeat(41))).isInstanceOf(SQLException.class);
                connection.rollback();
            }
            assertThat(sessionValue(connection,"sql_mode")).isEmpty();
            assertThat(value()).isEqualTo("initial");
            owner.allowReopen();
        }
    }

    @Test
    void shouldRefuseLossyClientEncoding_beforeAnyNativeImportWrite() throws Exception {
        configureLossyConnection(nodeOne);
        try (var owner=own(first); Connection connection=nodeOne.getConnection()) {
            assertThat(sessionValue(connection,"character_set_client")).isEqualTo("latin1");
            try (var marker=org.mockito.Mockito.mockStatic(CdsImportUnitService.class)) {
                marker.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
                connection.setAutoCommit(false);
                assertThatThrownBy(()->update(connection,"\uD83D\uDE00"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("utf8mb4");
                connection.rollback();
            }
            assertThat(value()).isEqualTo("initial");
            assertThat(sessionValue(connection,"sql_mode")).isEmpty();
            owner.allowReopen();
        }
    }

    @Test
    void shouldRefuseALossyJdbcEncoder_evenWhenServerSessionVariablesClaimUtf8mb4() throws Exception {
        configureLossyConnection(nodeOne);
        // Connector/J configures its encoder after URL sessionVariables. A pool init SQL runs
        // after that handshake and reproduces the documented SET NAMES/JDBC encoder mismatch.
        nodeOne.setConnectionInitSqls(java.util.List.of("SET NAMES utf8mb4"));
        try (var owner=own(first); Connection connection=nodeOne.getConnection()) {
            assertThat(sessionValue(connection,"character_set_client")).isEqualTo("utf8mb4");
            try (var marker=org.mockito.Mockito.mockStatic(CdsImportUnitService.class)) {
                marker.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
                connection.setAutoCommit(false);
                assertThatThrownBy(()->update(connection,"\uD83D\uDE00"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("encoder");
                connection.rollback();
            }
            assertThat(value()).isEqualTo("initial");
            owner.allowReopen();
        }
    }

    @Test
    void shouldVerifyTheCanonicalNativeCatalogue_beforeAnyClinicalUnitRuns() throws Exception {
        try (var owner=own(first)) {
            new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties())).verify(owner);
            owner.allowReopen();
        }
    }

    @Test
    void shouldRefuseANontransactionalWriteTarget_beforeNativeMutation() throws Exception {
        try (Connection raw=nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql=raw.createStatement()) {
            sql.execute("ALTER TABLE cds_guard_probe ENGINE=MyISAM");
        }
        try (var owner=own(first)) {
            var safety=new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()),java.util.Set.of("cds_guard_probe"));
            assertThatThrownBy(()->safety.verify(owner)).isInstanceOf(CdsImportException.class)
                    .hasMessage("IMPORT_TARGET_ENGINE_UNSUPPORTED");
            assertThat(value()).isEqualTo("initial");
        }
    }

    @Test
    void shouldRefuseAnUnapprovedTargetTrigger_beforeNativeMutation() throws Exception {
        try (Connection raw=nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql=raw.createStatement()) {
            sql.execute("CREATE TRIGGER cds_guard_probe_trigger BEFORE UPDATE ON cds_guard_probe FOR EACH ROW SET NEW.probe_value=NEW.probe_value");
        }
        try (var owner=own(first)) {
            var safety=new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()),java.util.Set.of("cds_guard_probe"));
            assertThatThrownBy(()->safety.verify(owner)).isInstanceOf(CdsImportException.class)
                    .hasMessage("IMPORT_TARGET_TRIGGER_APPROVAL_REQUIRED");
            assertThat(value()).isEqualTo("initial");
        }
    }

    @Test
    void shouldPinVerifiedMetadata_untilTheMaintenanceOwnerReleasesIt() throws Exception {
        var owner=own(first);
        new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties()),java.util.Set.of("cds_guard_probe"))
                .verify(owner);
        Future<Integer> alteration=worker.submit(()->{
            try (Connection raw=nodeTwo.openUnpooledCdsMaintenanceConnection(); Statement sql=raw.createStatement()) {
                sql.setQueryTimeout(10);
                return sql.executeUpdate("ALTER TABLE cds_guard_probe ADD COLUMN catalogue_pin_probe INT");
            }
        });
        assertBlocked(alteration);
        owner.allowReopen(); owner.close();
        assertThat(alteration.get(5,TimeUnit.SECONDS)).isZero();
    }

    @Test
    void shouldRefuseUnitWrites_whenTheOwnerSkippedNativeCatalogueVerification() throws Exception {
        try (var owner=acquireOnly(first);
                Connection unit=CdsJdbcAdmissionGuard.wrap(nodeOne.openUnpooledCdsMaintenanceConnection(),nodeOne.getCdsAdmissionState(),()->true)) {
            unit.setAutoCommit(false);
            assertThatThrownBy(()->update(unit,"unverified-catalogue-write"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("catalogue");
            unit.rollback();
            assertThat(value()).isEqualTo("initial");
            owner.allowReopen();
        }
    }

    @Test
    void shouldRefuseLossyMetadataEncoding_beforePreparedValuesCanBeBoundWithoutMaintenance() throws Exception {
        try(var planning=dataSource(jdbcProperties)) {
            configureLossyConnection(planning);
            new CdsJdbcAdmissionInitializer(planning,false,"off").initialize();
            assertThat(planning.getCdsAdmissionState()).isNull();
            try(Connection connection=planning.getConnection()) {
                assertThatThrownBy(()->connection.prepareStatement("UPDATE cds_import_run SET operator=? WHERE id=?"))
                        .isInstanceOf(SQLException.class).hasMessageContaining("encoder");
            }
        }
    }

    @Test
    void shouldPreservePreparedMetadataUnicode_andRestoreLegacyModeBeforeMaintenanceActivation() throws Exception {
        String payload="{\"patientId\":\"Caf\u00e9 \uD83D\uDE00\",\"reason\":\"\u6f22\u5b57 \uD83E\uDDEC\"}";
        try(var planning=dataSource(jdbcProperties)) {
            planning.setConnectionProperties("sessionVariables=sql_mode='';jdbcCompliantTruncation=false");
            new CdsJdbcAdmissionInitializer(planning,false,"off").initialize();
            assertThat(planning.getCdsAdmissionState()).isNull();
            try(Connection connection=planning.getConnection()) {
                update(connection,"ordinary-before-metadata");
                assertThat(sessionValue(connection,"sql_mode")).isEmpty();
                connection.setAutoCommit(false);
                try(var prepared=connection.prepareStatement("""
                        INSERT INTO cds_import_preparation
                        (id,run_id,unit_key,source_digest,options_digest,ruleset_digest,payload_type,payload,digest)
                        VALUES ('cds-maintenance-metadata-test','metadata-test','unit','source','options','ruleset',?,?,'digest')
                        """)) {
                    assertThat(sessionValue(connection,"sql_mode")).contains("STRICT_ALL_TABLES");
                    prepared.setString(1,"fixture \uD83E\uDDEC"); prepared.setString(2,payload);
                    assertThat(prepared.executeUpdate()).isEqualTo(1);
                    connection.commit();
                }
                assertThat(sessionValue(connection,"sql_mode")).isEmpty();
                try(var query=connection.createStatement(); var rows=query.executeQuery(
                        "SELECT payload FROM cds_import_preparation WHERE id='cds-maintenance-metadata-test'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(payload);
                }
            }
        }
    }

    @Test
    void shouldMatchActualProviderDaoThroughMaintenanceGuard_withLatin1AndUtf8Metadata() throws Exception {
        for (String charset : List.of("latin1", "utf8mb4")) verifyGuardedProviderDirectory(charset);
    }

    private void verifyGuardedProviderDirectory(String charset) throws Exception {
        assertThat(charset).isIn("latin1", "utf8mb4");
        String schema="cds_provider_guard_"+java.util.UUID.randomUUID().toString().replace("-", "");
        assertThat(schema).matches("cds_provider_guard_[a-f0-9]{32}");
        boolean created=false;
        try (Connection control=nodeOne.openUnpooledCdsMaintenanceConnection(); Statement ddl=control.createStatement()) {
            assertThat(control.getCatalog()).isEqualTo("carlos_cds_test");
            try {
                ddl.execute("CREATE DATABASE "+schema+" CHARACTER SET "+charset+" COLLATE "+charset+"_general_ci"); created=true;
                for (String table : List.of("provider", "demographic", "cds_import_admission", "cds_import_patient"))
                    ddl.execute("CREATE TABLE "+schema+"."+table+" LIKE carlos_cds_test."+table);
                ddl.execute("ALTER TABLE "+schema+".provider MODIFY first_name VARCHAR(30) CHARACTER SET "+charset
                        +" COLLATE "+charset+"_general_ci NOT NULL DEFAULT '', MODIFY last_name VARCHAR(30) CHARACTER SET "+charset
                        +" COLLATE "+charset+"_general_ci NOT NULL DEFAULT ''");
                ddl.executeUpdate("INSERT INTO "+schema+".cds_import_admission SELECT * FROM carlos_cds_test.cds_import_admission");
                ddl.executeUpdate("UPDATE "+schema+".cds_import_admission SET maintenance_active=FALSE,maintenance_owner_token=NULL,maintenance_connection_id=NULL");
                String[][] rows={
                    {"G10001","Élodie","Müller","009901","001234","CPSO"},
                    {"G10002","élodie","Müller","009901 ","001234 ","CPSO"},
                    {"G10003","Elodie","Muller","009902","001234","cpso"},
                    {"G10004","Élodie ","Müller","009903","001234","CPSO "},
                    {"G10005","Élodie","Müller","009901","001234","CPSO"},
                    {"G10006","Élodie","Müller","009901","001234","CPSO"}};
                try (var insert=control.prepareStatement("INSERT INTO "+schema+".provider "
                        +"(provider_no,first_name,last_name,ohip_no,practitionerNo,practitionerNoType,lastUpdateDate) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)")) {
                    for (String[] row : rows) {for (int i=0;i<row.length;i++) insert.setString(i+1,row[i]);assertThat(insert.executeUpdate()).isEqualTo(1);}
                    if (charset.equals("utf8mb4")) for (String[] row : List.of(
                            new String[]{"G10007","E\u0301lodie","Müller","009907","001237","CPSO"},
                            new String[]{"G10008","Élodie \uD83D\uDE00","Müller","009908","001238","CPSO"})) {
                        for (int i=0;i<row.length;i++) insert.setString(i+1,row[i]);assertThat(insert.executeUpdate()).isEqualTo(1);
                    }
                }
                Properties selected=new Properties(); selected.putAll(jdbcProperties);
                String configured=selected.getProperty("jdbcUrl");
                String replaced=configured.replaceFirst("/carlos_cds_test(?=\\?|$)", "/"+schema);
                assertThat(replaced).isNotEqualTo(configured); selected.setProperty("jdbcUrl",replaced);
                try (var source=dataSource(selected)) {
                    new CdsJdbcAdmissionInitializer(source,true,"validate").initialize();
                    Configuration configuration=new Configuration().addAnnotatedClass(Probe.class);
                    configuration.getProperties().put("hibernate.connection.datasource",source);
                    configuration.setProperty("hibernate.dialect","org.hibernate.dialect.MariaDBDialect");
                    configuration.setProperty("hibernate.hbm2ddl.auto","none");
                    try (SessionFactory factory=configuration.buildSessionFactory(); Session session=factory.openSession()) {
                        var dao=new io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl() {
                            @Override protected jakarta.persistence.EntityManager entityManager() {return session;}
                        };
                        Properties policy=new Properties(); policy.setProperty("cds.import.enabled","true");
                        policy.setProperty("cds.import.maintenanceMode","true");
                        var maintenance=new CdsImportMaintenanceService(source,new CdsImportConfigurationService(policy));
                        try (var actualOwner=maintenance.acquire()) {
                            try (var query=control.prepareStatement("SELECT maintenance_active,maintenance_generation,maintenance_owner_token,maintenance_connection_id,IS_USED_LOCK(?) FROM "+schema+".cds_import_admission WHERE id='clinical-maintenance'")) {
                                query.setString(1,source.getCdsAdmissionState().lockName());
                                try(var result=query.executeQuery()) {
                                    assertThat(result.next()).isTrue();assertThat(result.getBoolean(1)).isTrue();
                                    assertThat(result.getLong(2)).isEqualTo(actualOwner.generation());
                                    assertThat(result.getString(3)).isEqualTo(source.getCdsAdmissionState().owner().token());
                                    assertThat(result.getLong(4)).isEqualTo(actualOwner.connectionId());
                                    assertThat(result.getLong(5)).isEqualTo(actualOwner.connectionId());
                                }
                            }
                        assertThat(dao.findCdsProviderNosByOhip("009901")).containsExactly("G10001","G10005");
                        assertThat(dao.findCdsProviderNosByOhip("009901 ")).containsExactly("G10002");
                        assertThat(dao.findCdsProviderNosByCpso("001234")).containsExactly("G10001","G10005");
                        assertThat(dao.findCdsProviderNosByCpso("001234 ")).containsExactly("G10002");
                        assertThat(dao.findCdsProviderNosByExactName("Élodie","Müller")).containsExactly("G10001","G10005");
                        assertThat(dao.findCdsProviderNosByExactName("élodie","Müller")).containsExactly("G10002");
                        assertThat(dao.findCdsProviderNosByExactName("Élodie ","Müller")).containsExactly("G10004");
                        assertThat(dao.findCdsProviderNosByExactName("Elodie","Müller")).isEmpty();
                        assertThat(dao.findCdsProviderNosByExactName("Élo%","Müller")).isEmpty();
                        if (charset.equals("utf8mb4")) {
                            assertThat(dao.findCdsProviderNosByExactName("E\u0301lodie","Müller")).containsExactly("G10007");
                            assertThat(dao.findCdsProviderNosByExactName("Élodie \uD83D\uDE00","Müller")).containsExactly("G10008");
                        }
                        assertThatThrownBy(() -> session.createNativeQuery("select demographic_no from demographic",Integer.class).getResultList())
                                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("clinical access");
                        try (var query=control.prepareStatement("SELECT CHARACTER_SET_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME='provider' AND COLUMN_NAME='first_name'")) {
                            query.setString(1,schema);try(var result=query.executeQuery()){assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo(charset);}
                        }
                        try (var query=control.createStatement();var result=query.executeQuery("SELECT HEX(first_name),HEX(last_name) FROM "+schema+".provider WHERE provider_no='G10001'")) {
                            assertThat(result.next()).isTrue();var encoding=java.nio.charset.Charset.forName(charset.equals("latin1")?"ISO-8859-1":"UTF-8");
                            assertThat(result.getString(1)).isEqualTo(java.util.HexFormat.of().withUpperCase().formatHex("Élodie".getBytes(encoding)));
                            assertThat(result.getString(2)).isEqualTo(java.util.HexFormat.of().withUpperCase().formatHex("Müller".getBytes(encoding)));
                        }
                        } // Closing an unverified fixture owner retains its fail-closed flag until the owned schema is dropped.
                    }
                }
            } finally {if(created)ddl.execute("DROP DATABASE "+schema);}
        }
    }

    private static String sessionValue(Connection connection,String name) throws SQLException {
        String sql=name.equals("connection_id") ? "SELECT CONNECTION_ID()" : "SELECT @@SESSION."+name;
        try (Statement query=connection.createStatement(); ResultSet rows=query.executeQuery(sql)) {
            rows.next(); return rows.getString(1);
        }
    }

    private CdsImportMaintenanceService.Owner own(CdsImportMaintenanceService service) {
        var owner=acquireOnly(service); verify(owner); return owner;
    }
    private CdsImportMaintenanceService.Owner acquireOnly(CdsImportMaintenanceService service) {
        var owner=service.acquire(); owners.add(owner); return owner;
    }
    private void verify(CdsImportMaintenanceService.Owner owner) {
        new CdsImportTargetSafetyService(new CdsImportConfigurationService(new Properties())).verify(owner);
    }
    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }
    private String value() throws SQLException {
        try (Connection raw = nodeOne.openUnpooledCdsMaintenanceConnection(); Statement sql = raw.createStatement();
                ResultSet rows = sql.executeQuery("SELECT probe_value FROM cds_guard_probe WHERE id=1")) {
            rows.next(); return rows.getString(1);
        }
    }
    private static void update(Connection connection, String value) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE cds_guard_probe SET probe_value=? WHERE id=1")) {
            statement.setString(1, value); assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }
    private static void configureLossyConnection(OscarTrackingBasicDataSource source) throws SQLException {
        String configured=source.getUrl(); int query=configured.indexOf('?');
        String base=query<0?configured:configured.substring(0,query);
        var parameters=new java.util.ArrayList<String>();
        if(query>=0) for(String parameter:configured.substring(query+1).split("&")) {
            String key=java.net.URLDecoder.decode(parameter.split("=",2)[0],java.nio.charset.StandardCharsets.UTF_8);
            if(!java.util.Set.of("characterencoding","connectioncollation","sessionvariables","jdbccomplianttruncation")
                    .contains(key.toLowerCase(java.util.Locale.ROOT))) parameters.add(parameter);
        }
        parameters.add("characterEncoding=ISO-8859-1"); parameters.add("connectionCollation=latin1_swedish_ci");
        parameters.add("jdbcCompliantTruncation=false");
        source.setUrl(base+"?"+String.join("&",parameters));
        source.setConnectionProperties("sessionVariables=sql_mode='';jdbcCompliantTruncation=false");
        try(Connection raw=source.openUnpooledCdsMaintenanceConnection()) {
            String encoder=raw.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).getPropertySet()
                    .getStringProperty(com.mysql.cj.conf.PropertyKey.characterEncoding).getValue();
            assertThat(java.nio.charset.Charset.forName(encoder)).isEqualTo(java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private static OscarTrackingBasicDataSource dataSource(Properties jdbc) {
        var source = new OscarTrackingBasicDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        String configured = jdbc.getProperty("jdbcUrl");
        source.setUrl(configured.startsWith("jdbc:mariadb:")
                ? "jdbc:mysql:" + configured.substring("jdbc:mariadb:".length()) : configured);
        source.setUsername(jdbc.getProperty("jdbcUser")); source.setPassword(jdbc.getProperty("jdbcPassword"));
        source.setMaxTotal(8); source.setDefaultAutoCommit(true); return source;
    }
    @Entity(name = "CdsAdmissionProbe") @Table(name = "cds_guard_probe")
    public static class Probe {
        @Id private Integer id;
        @Column(name = "probe_value") private String value;
        public Probe() { }
    }
}
