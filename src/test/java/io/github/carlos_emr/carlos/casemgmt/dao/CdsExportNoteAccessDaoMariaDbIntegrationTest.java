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
package io.github.carlos_emr.carlos.casemgmt.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Physical program-access query tests. Every fixture change rolls back; no clinic records are used. */
@Tag("integration") @Tag("cds-export")
class CdsExportNoteAccessDaoMariaDbIntegrationTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass = true) static class Config { }
    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private CdsExportNoteAccessDao notes;
    private TransactionTemplate transaction;
    private final int id = 470_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000_000);
    private final String provider = "987651";

    @BeforeEach void setup() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) { properties.load(input); }
        if (!"true".equals(properties.getProperty("ownedDisposableDatabase")) || !properties.getProperty("jdbcUrl", "").matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned fixture database only");
        source = new BasicDataSource(); source.setMaxTotal(3); source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser")); source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source); context = new AnnotationConfigApplicationContext(); context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source); factory.setPersistenceUnitName("entityManagerFactory");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setManagedTypes(PersistenceManagedTypes.of());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.MariaDBDialect", "hibernate.show_sql", "false")); return factory;
        });
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean("transactionManager", JpaTransactionManager.class, () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.register(CdsExportNoteAccessDao.class); context.refresh(); notes = context.getBean(CdsExportNoteAccessDao.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); transaction.setTimeout(30);
    }
    @AfterEach void cleanup() throws Exception { try { if (context != null) context.close(); } finally { if (source != null) source.close(); } }

    @Test void shouldRequireProviderMembership_evenWhenItsRoleHasADefaultGrant() {
        fixture(() -> {
            defaultGrant(); assertThat(read(scope())).isFalse();
            membership(id, id + 1); assertThat(read(scope())).isTrue();
            sql.update("delete from program_provider where id=?", id); assertThat(read(scope())).isFalse();
        });
    }
    @Test void shouldAllowTheSameRoleDefault_withoutAnExplicitAccessGrant() {
        fixture(() -> { membership(id, id); assertThat(read(scope())).isTrue(); });
    }
    @Test void shouldUseExplicitRoleAndAllRoleGrants_onlyInTheNotesProgram() {
        fixture(() -> {
            membership(id, id + 1); programGrant(id + 1, 1); assertThat(read(scope())).isFalse();
            sql.update("update program_access set program_id=?,all_roles=0 where id=?", id, id);
            assertThat(read(scope())).isFalse();
            sql.update("insert into program_access_roles(id,role_id) values(?,?)", id, id); assertThat(read(scope())).isFalse();
            sql.update("update program_access_roles set role_id=? where id=?", id + 1, id); assertThat(read(scope())).isTrue();
            sql.update("delete from program_access_roles where id=?", id); sql.update("update program_access set all_roles=1 where id=?", id);
            assertThat(read(scope())).isTrue();
        });
    }
    @Test void shouldMatchTheActiveReadersRoleName_preservingSpacesAndIgnoringAccessType() {
        fixture(() -> {
            membership(id, id + 1); defaultGrant();
            sql.update("update secRole set role_name=? where role_no=?", "Cds" + id + " Care Worker", id);
            sql.update("update access_type set name=? where access_id=?", "Cds" + id + "Care Workernotes", id); assertThat(read(scope())).isFalse();
            sql.update("update access_type set name=?,type='read' where access_id=?", "READ cDS" + id + " CARE WORKER NOTES", id); assertThat(read(scope())).isTrue();
        });
    }
    @Test void shouldNormalizeProgramAccessMapKeys_asTheActiveReaderDoes() {
        fixture(() -> {
            membership(id, id + 1); programGrant(id, 1);
            sql.update("update access_type set name=?,type='read' where access_id=?", "READ CDS" + id + " NOTES", id);
            assertThat(read(scope())).isTrue();
            sql.update("insert into access_type(access_id,name,type) values(?,?,'read')", id + 1, "read Cds" + id + " notes");
            sql.update("insert into program_access(id,program_id,access_type_id,all_roles) values(?,?,?,0)", id + 1, id, Integer.toString(id + 1));
            assertThat(read(scope())).isFalse();
        });
    }
    @Test void shouldBindPhysicalScopeToItsPatient_andRejectMalformedDependencies() {
        fixture(() -> {
            assertThat(notes.findScope(id, id)).isEqualTo(scope()); assertThat(notes.findScope(id + 1, id)).isNull();
            assertThat(notes.findScope(id, id + 1)).isNull();
            for (String invalid : java.util.List.of("01", "1x", "4294967296")) {
                sql.update("update casemgmt_note set program_no=? where note_id=?", invalid, id); assertThat(notes.findScope(id, id)).isNull();
            }
            sql.update("update casemgmt_note set program_no='',reporter_caisi_role='1' where note_id=?", id);
            assertThat(notes.findScope(id, id).program()).isEmpty();
            sql.update("update casemgmt_note set program_no=?,reporter_caisi_role='0' where note_id=?", Integer.toString(id), id);
            assertThat(notes.findScope(id, id)).isNull();
        });
    }
    @Test void shouldDenyMissingReporterRoles_withoutSubstitutingAnotherRole() {
        fixture(() -> {
            membership(id, id); assertThat(read(scope())).isTrue();
            assertThat(read(new CdsExportNoteAccessDto(id, id, Integer.toString(id), Integer.toString(id + 2)))).isFalse();
        });
    }
    @Test void shouldUseHistoricalProgramDomain_forUnscopedAndZeroProgramNotes() {
        fixture(() -> {
            var empty = new CdsExportNoteAccessDto(id, id, "", Integer.toString(id));
            var zero = new CdsExportNoteAccessDto(id, id, "0", Integer.toString(id));
            assertThat(read(empty)).isTrue(); assertThat(read(zero)).isTrue();
            admission(id + 1); membership(id, id);
            assertThat(read(empty)).isFalse(); assertThat(read(zero)).isFalse();
            sql.update("update program_provider set program_id=? where id=?", id + 1, id);
            assertThat(read(empty)).isTrue(); assertThat(read(zero)).isTrue();
            sql.update("update admission set discharge_date='2001-01-01' where am_id=?", id); assertThat(read(zero)).isTrue();
            sql.update("update admission set admission_date='9999-01-01',discharge_date=NULL where am_id=?", id); assertThat(read(zero)).isTrue();
        });
    }
    @Test void shouldAcceptAnActiveReferral_intoTheProvidersProgramDomain() {
        fixture(() -> {
            var zero = new CdsExportNoteAccessDto(id, id, "0", Integer.toString(id));
            admission(id + 1); membership(id, id); assertThat(read(zero)).isFalse();
            sql.update("insert into program_queue(queue_id,client_id,program_id,provider_no,status) values(?,?,?,?,'active')", id, id, id, provider);
            assertThat(read(zero)).isTrue();
            sql.update("update program_queue set status='inactive' where queue_id=?", id); assertThat(read(zero)).isFalse();
        });
    }
    @Test void shouldEnforceTheConfiguredFacilityBoundary_afterProgramRights() {
        fixture(() -> {
            membership(id, id);
            sql.update("insert into Facility(id,name,disabled,displayAllVacancies,enableAnonymous,enableCbiForm,enableDigitalSignatures,enableEncounterTime,enableEncounterTransportationTime,enableHealthNumberRegistry,enableOcanForms,enablePhoneEncounter,hic,lastUpdated,orgId,rxInteractionWarningLevel,sectorId) values(?,?,0,0,0,0,0,0,0,0,0,0,0,CURRENT_TIMESTAMP,0,0,0)", id, "Cds" + id);
            sql.update("insert into program(id,name,ageMax,ageMin,alcohol,exclusiveView,facilityId,firstNation,housing,maxAllowed,mentalHealth,physicalHealth,programStatus,transgender) values(?,?,0,0,0,'',?,0,0,100,0,0,'active',0)", id, "Cds" + id, id);
            assertThat(notes.canRead(provider, scope(), null, true)).isFalse();
            assertThat(notes.canRead(provider, scope(), id + 1, true)).isFalse();
            assertThat(notes.canRead(provider, scope(), id, true)).isTrue();
            assertThat(notes.canRead(provider, scope(), null, false)).isTrue();
        });
    }
    @Test void shouldDenyAmbiguousMembershipRoles_insteadOfDependingOnRowOrder() {
        fixture(() -> {
            membership(id, id); assertThat(read(scope())).isTrue();
            sql.update("insert into program_provider(id,program_id,provider_no,role_id) values(?,?,?,?)", id + 1, id, provider, id + 1);
            assertThat(read(scope())).isFalse();
        });
    }
    @Test void shouldRequireAnExistingTransaction_forMetadataAndPermissionReads() {
        assertThatThrownBy(() -> notes.findScope(id, id)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> read(scope())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test void shouldRefuseLockedAndUnknownLockFlags_evenWhenTheProgramAllowsReading() {
        fixture(() -> {
            membership(id, id); assertThat(notes.findScope(id, id)).isEqualTo(scope());
            sql.update("update casemgmt_note set locked='0' where note_id=?", id); assertThat(notes.findScope(id, id)).isEqualTo(scope());
            for (String flag : java.util.List.of("1", "Y", "x", "")) {
                sql.update("update casemgmt_note set locked=? where note_id=?", flag, id); assertThat(notes.findScope(id, id)).isNull();
            }
        });
    }
    @Test void shouldRefuseARevisionUuidSharedWithAnotherPatient_withoutReadingItsPayload() {
        fixture(() -> {
            String uuid = java.util.UUID.randomUUID().toString();
            sql.update("update casemgmt_note set uuid=? where note_id=?", uuid, id);
            sql.update("insert into casemgmt_note(note_id,demographic_no,note,history,program_no,reporter_caisi_role,uuid,update_date) values(?,?,'Foreign synthetic note','',?,?,?,CURRENT_TIMESTAMP)", id + 1, id + 1, Integer.toString(id), Integer.toString(id), uuid);
            assertThat(notes.findScope(id, id)).isNull();
            assertThatThrownBy(() -> notes.readScopes(id, 10)).hasMessage("CDS_EXPORT_NOTE_AUTHORIZATION_DENIED");
            sql.update("update casemgmt_note set demographic_no=? where note_id=?", id, id + 1);
            assertThat(notes.findScope(id, id)).isEqualTo(scope()); assertThat(notes.readScopes(id, 10)).hasSize(2);
            sql.update("update casemgmt_note set demographic_no=0 where note_id=?", id + 1);
            assertThat(notes.findScope(id, id)).isNull();
        });
    }
    private boolean read(CdsExportNoteAccessDto scope) { return notes.canRead(provider, scope, null, false); }
    private void admission(int program) {
        sql.update("insert into admission(am_id,client_id,program_id,provider_no,admission_date,discharge_date,admission_from_transfer,discharge_from_transfer,lastUpdateDate) values(?,?,?,?,'2000-01-01',NULL,0,0,CURRENT_TIMESTAMP)", id, id, program, provider);
    }
    private CdsExportNoteAccessDto scope() { return new CdsExportNoteAccessDto(id, id, Integer.toString(id), Integer.toString(id)); }
    private void fixture(Runnable assertions) {
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            for (String table : java.util.List.of("casemgmt_note", "secRole", "program_provider", "default_role_access", "program_access", "access_type", "admission")) {
                String key = switch (table) { case "casemgmt_note" -> "note_id"; case "secRole" -> "role_no"; case "access_type" -> "access_id"; case "admission" -> "am_id"; default -> "id"; };
                assertThat(sql.queryForObject("select count(*) from " + table + " where " + key + "=?", Integer.class, id)).isZero();
            }
            assertThat(sql.queryForObject("select count(*) from provider where provider_no=?", Integer.class, provider)).isZero();
            sql.update("insert into provider(provider_no,first_name,last_name,provider_type,status,lastUpdateDate) values(?,'Synthetic','CDS note access','doctor','1',CURRENT_TIMESTAMP)", provider);
            sql.update("insert into casemgmt_note(note_id,demographic_no,note,history,program_no,reporter_caisi_role,update_date) values(?,?,'Synthetic private body','',?,?,CURRENT_TIMESTAMP)", id, id, Integer.toString(id), Integer.toString(id));
            sql.update("insert into secRole(role_no,role_name,description) values(?,?,'Synthetic CDS access fixture')", id, "Cds" + id);
            sql.update("insert into secRole(role_no,role_name,description) values(?,?,'Synthetic CDS access fixture')", id + 1, "CdsOther" + id);
            assertions.run();
        });
    }
    private void defaultGrant() {
        sql.update("insert into access_type(access_id,name,type) values(?,?,'access')", id, "read Cds" + id + " notes");
        sql.update("insert into default_role_access(id,role_id,access_id) values(?,?,?)", id, id + 1, id);
    }
    private void membership(int program, int role) { sql.update("insert into program_provider(id,program_id,provider_no,role_id) values(?,?,?,?)", id, program, provider, role); }
    private void programGrant(int program, int all) {
        sql.update("insert into access_type(access_id,name,type) values(?,?,'access')", id, "read Cds" + id + " notes");
        sql.update("insert into program_access(id,program_id,access_type_id,all_roles) values(?,?,?,?)", id, program, Integer.toString(id), all);
    }
}
