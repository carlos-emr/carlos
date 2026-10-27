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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import static org.assertj.core.api.Assertions.*;

/** Real native control rows and physical connections; no clinical table is written. */
@Tag("integration") @Tag("mariadb")
@Isolated("Exclusive synthetic coordinator and server-global SQL counter measurement")
@EnabledIfSystemProperty(named="cds.test.properties",matches=".+")
class CdsImportCoordinatorMariaDbIntegrationTest {
    private OscarTrackingBasicDataSource source;
    private Connection observer;
    private CdsImportCoordinatorService coordinator;
    private String run;
    private String alternate;
    private String lockName;

    @BeforeEach void prepareOwnControlRows() throws Exception {
        Properties jdbc=new Properties();
        try(InputStream input=Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {jdbc.load(input);}
        String url=jdbc.getProperty("jdbcUrl","").replace("jdbc:mariadb:","jdbc:mysql:");
        assertThat(url).matches("jdbc:mysql://[^/]+/(?:carlos_cds_test|cds_control_[0-9a-f]{12}_test)(?:\\?.*)?");
        source=new OscarTrackingBasicDataSource();source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);source.setUsername(jdbc.getProperty("jdbcUser"));source.setPassword(jdbc.getProperty("jdbcPassword"));
        source.setMaxTotal(4);source.setDefaultAutoCommit(true);
        observer=source.openUnpooledCdsMaintenanceConnection();
        try(var query=observer.createStatement();var rows=query.executeQuery("SELECT owner_run_id,reserved_bytes FROM cds_import_admission WHERE id='global'")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isNull();assertThat(rows.getLong(2)).isZero();
        }
        run=UUID.randomUUID().toString();alternate=UUID.randomUUID().toString();
        try(var query=observer.prepareStatement("""
                INSERT INTO cds_import_run(id,submission_key,dry_run,report_state,cleanup_state,
                    review_version,discovery_complete,discovered_files,patient_xml_files,source_bytes,
                    operator,provider_no,source_namespace,profile,source_digest,options_digest,ruleset_digest,
                    snapshot_location,state,expected_patients,expected_units,owner_epoch,cancel_requested,
                    report_generation,created_at,updated_at)
                VALUES(?,?,0,'NOT_STARTED','NOT_STARTED',0,0,0,0,0,'control-probe','999998','control-probe',
                    'pcon-2024-02',?,?,?,'/opaque/control-probe','RUNNING',0,0,7,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """)) {
            query.setString(1,run);query.setString(2,run);
            for(int index=3;index<=5;index++)query.setString(index,"a".repeat(64));
            assertThat(query.executeUpdate()).isEqualTo(1);
        }
        update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'",run);
        lockName="cds.coordinator."+CdsImportKeys.hash(observer.getCatalog()).substring(0,40);
        coordinator=new CdsImportCoordinatorService(source);
    }

    @AfterEach void retireOnlyOwnControlRows() throws Exception {
        if(observer!=null) {
            try {
                if(run!=null) {
                    try(var query=observer.createStatement();var rows=query.executeQuery("SELECT owner_run_id FROM cds_import_admission WHERE id='global'")) {
                        assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isIn(run,alternate,(String)null);
                    }
                    update("UPDATE cds_import_admission SET owner_run_id=NULL WHERE id='global'");
                    update("DELETE FROM cds_import_run WHERE id=?",run);
                }
            } finally {observer.close();}
        }
        if(source!=null)source.close();
    }

    @Test void eachForcedControlIsOneActualSqlQuery_withoutPoolBorrowOrRetainedTransaction() throws Exception {
        try(var owner=coordinator.acquire()) {
            long connection=lockConnection();
            // Warm the one prepared statement before taking the measured window.
            assertThat(owner.control(run).admissionOwned()).isTrue();
            long before=questions();
            for(int index=0;index<12;index++) {
                var current=owner.control(run);
                assertThat(current.coordinatorOwned()).isTrue();assertThat(current.runPresent()).isTrue();
                assertThat(current.ownerEpoch()).isEqualTo(7);assertThat(current.cancelRequested()).isFalse();
                assertThat(current.admissionOwned()).isTrue();
            }
            assertThat(questions()-before-1).as("12 real control SELECTs; exclude only this observer's final SHOW").isEqualTo(12);
            try(var query=observer.prepareStatement("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=?")) {
                query.setLong(1,connection);
                try(var rows=query.executeQuery()){rows.next();assertThat(rows.getLong(1)).isZero();}
            }
            assertThat(lockConnection()).isEqualTo(connection);
            assertThat(source.getNumActive()).isZero();assertThat(source.getNumIdle()).isZero();
        }
    }

    @Test void cancellationEpochAdmissionAndMissingRunAreObservedFromFreshSnapshots() throws Exception {
        try(var owner=coordinator.acquire();var scope=scope(owner)) {
            update("UPDATE cds_import_run SET cancel_requested=1 WHERE id=?",run);
            assertThatThrownBy(()->scope.check(true)).hasMessage("IMPORT_CANCELLED");
            update("UPDATE cds_import_run SET cancel_requested=0,owner_epoch=8 WHERE id=?",run);
            assertThatThrownBy(()->scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_run SET owner_epoch=7 WHERE id=?",run);
            update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'",alternate);
            assertThatThrownBy(()->scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_admission SET owner_run_id=NULL WHERE id='global'");
            assertThatThrownBy(()->scope.check(true)).hasMessage("OWNER_FENCED");
            update("UPDATE cds_import_admission SET owner_run_id=? WHERE id='global'",run);
            update("DELETE FROM cds_import_run WHERE id=?",run);
            assertThat(owner.control(run).runPresent()).isFalse();
            assertThatThrownBy(()->scope.check(true)).hasMessage("OWNER_FENCED");
        }
    }

    @Test void malformedNativeBooleanFailsClosed() throws Exception {
        try(var owner=coordinator.acquire();var scope=scope(owner)) {
            update("UPDATE cds_import_run SET cancel_requested=2 WHERE id=?",run);
            assertThatThrownBy(()->scope.check(true)).hasMessage("IMPORT_COORDINATOR_LOST");
            update("UPDATE cds_import_run SET cancel_requested=0 WHERE id=?",run);
        }
    }

    @Test void killingTheActualOwnedPhysicalConnectionFencesItsScope_andAnotherNodeCanAcquire() throws Exception {
        var owner=coordinator.acquire();boolean killed=false;
        try(var scope=scope(owner)) {
            long connection=lockConnection();
            try(var query=observer.createStatement()){query.execute("KILL CONNECTION "+connection);}
            killed=true;
            assertThatThrownBy(()->scope.check(true)).hasMessage("IMPORT_COORDINATOR_LOST");
            try(var replacement=new CdsImportCoordinatorService(source).acquire()) {
                replacement.check();assertThat(lockConnection()).isNotEqualTo(connection);
            }
        } finally {
            if(killed)assertThatThrownBy(owner::close).hasMessage("IMPORT_COORDINATOR_RELEASE_FAILED");
            else owner.close();
        }
    }

    @Test void aCompetingNodeCannotAcquireUntilTheOriginalPhysicalOwnerRetires() throws Exception {
        try(var owner=coordinator.acquire()) {
            assertThatThrownBy(()->new CdsImportCoordinatorService(source).acquire()).hasMessage("IMPORT_COORDINATOR_BUSY");
            owner.check();assertThat(owner.control(run).coordinatorOwned()).isTrue();
        }
        try(var next=coordinator.acquire()){next.check();}
    }

    private CdsImportExecutionControl scope(CdsImportCoordinatorService.Owner owner) {
        return new CdsImportExecutionControl(run,7,owner,"IMPORT_FILE_VERIFICATION_TIMEOUT",10000,
                System::nanoTime,()->{},new CdsFieldParser());
    }
    private void update(String statement,String... values) throws Exception {
        try(var query=observer.prepareStatement(statement)) {
            for(int index=0;index<values.length;index++)query.setString(index+1,values[index]);
            query.executeUpdate();
        }
    }
    private long questions() throws Exception {
        try(var query=observer.createStatement();var rows=query.executeQuery("SHOW GLOBAL STATUS LIKE 'Questions'")) {
            assertThat(rows.next()).isTrue();return rows.getLong(2);
        }
    }
    private long lockConnection() throws Exception {
        try(var query=observer.prepareStatement("SELECT IS_USED_LOCK(?)")) {
            query.setString(1,lockName);
            try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();long result=rows.getLong(1);assertThat(rows.wasNull()).isFalse();return result;}
        }
    }
}
