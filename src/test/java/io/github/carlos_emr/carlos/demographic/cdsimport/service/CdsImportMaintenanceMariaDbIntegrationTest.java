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
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> own(first));
            assertBlocked(entering);
            old.commit();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
                assertThat(value()).isEqualTo("old-transaction");
                try (Connection ordinary = nodeTwo.getConnection()) {
                    assertThatThrownBy(() -> update(ordinary, "must-not-commit")).isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> ordinary.createStatement().executeQuery("SELECT * FROM cds_guard_probe"))
                            .isInstanceOf(SQLException.class);
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
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> own(first));
            assertBlocked(entering);
            rows.close();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
                Connection metadataConnection = connection.getMetaData().getConnection().unwrap(Connection.class);
                assertThatThrownBy(() -> update(metadataConnection, "unwrap-escape")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> update(query.getConnection(), "statement-escape")).isInstanceOf(SQLException.class);
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
            Future<CdsImportMaintenanceService.Owner> entering = worker.submit(() -> own(first));
            assertBlocked(entering);
            transaction.commit();
            try (var owner = entering.get(5, TimeUnit.SECONDS)) {
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
            Future<CdsImportMaintenanceService.Owner> takeover = worker.submit(() -> own(second));
            assertBlocked(takeover);
            unit.commit();
            try (var successor = takeover.get(5, TimeUnit.SECONDS)) {
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
        previous.close();
        assertThat(first.isClinicalAccessBlocked()).isTrue();
        try (Connection ordinary = nodeTwo.getConnection()) {
            assertThatThrownBy(() -> update(ordinary, "must-remain-held")).isInstanceOf(SQLException.class);
        }
        try (var recovered = own(second)) { recovered.allowReopen(); }
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
        assertThat(first.isClinicalAccessBlocked()).isTrue();
        try (Connection connection=nodeTwo.getConnection()) {
            assertThatThrownBy(()->update(connection,"held-chart-escape")).isInstanceOf(SQLException.class);
        }
        try (var unguarded=dataSource(jdbcProperties)) {
            assertThatThrownBy(()->new CdsJdbcAdmissionInitializer(unguarded,false,"off").initialize())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private CdsImportMaintenanceService.Owner own(CdsImportMaintenanceService service) {
        var owner = service.acquire(); owners.add(owner); return owner;
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
