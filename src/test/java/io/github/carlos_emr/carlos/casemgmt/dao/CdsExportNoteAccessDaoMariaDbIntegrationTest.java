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
            defaultGrant(); assertThat(notes.canRead(provider, scope())).isFalse();
            membership(id, id); assertThat(notes.canRead(provider, scope())).isTrue();
            sql.update("delete from program_provider where id=?", id); assertThat(notes.canRead(provider, scope())).isFalse();
        });
    }
    @Test void shouldUseExplicitRoleAndAllRoleGrants_onlyInTheNotesProgram() {
        fixture(() -> {
            membership(id, id); programGrant(id + 1, 1); assertThat(notes.canRead(provider, scope())).isFalse();
            sql.update("update program_access set program_id=?,all_roles=0 where id=?", id, id);
            assertThat(notes.canRead(provider, scope())).isFalse();
            sql.update("insert into program_access_roles(id,role_id) values(?,?)", id, id + 1); assertThat(notes.canRead(provider, scope())).isFalse();
            sql.update("update program_access_roles set role_id=? where id=?", id, id); assertThat(notes.canRead(provider, scope())).isTrue();
            sql.update("delete from program_access_roles where id=?", id); sql.update("update program_access set all_roles=1 where id=?", id);
            assertThat(notes.canRead(provider, scope())).isTrue();
        });
    }
    @Test void shouldMatchTheOwningReadersFirstSpaceRule_andAccessType() {
        fixture(() -> {
            membership(id, id); sql.update("update secRole set role_name=? where role_no=?", "Cds" + id + " Care Worker", id); defaultGrant();
            sql.update("update access_type set name=? where access_id=?", "Cds" + id + "CareWorkernotes", id); assertThat(notes.canRead(provider, scope())).isFalse();
            sql.update("update access_type set name=?,type='ACCESS' where access_id=?", "cDS" + id + "cARE Workernotes", id); assertThat(notes.canRead(provider, scope())).isTrue();
            sql.update("update access_type set type='read' where access_id=?", id); assertThat(notes.canRead(provider, scope())).isFalse();
        });
    }
    @Test void shouldBindPhysicalScopeToItsPatient_andRejectMissingOrMalformedDependencies() {
        fixture(() -> {
            assertThat(notes.findScope(id, id)).isEqualTo(scope()); assertThat(notes.findScope(id + 1, id)).isNull();
            assertThat(notes.findScope(id, id + 1)).isNull();
            for (String invalid : java.util.List.of("", "01", "1x", "4294967296")) {
                sql.update("update casemgmt_note set program_no=? where note_id=?", invalid, id); assertThat(notes.findScope(id, id)).isNull();
            }
            sql.update("update casemgmt_note set program_no=?,reporter_caisi_role='0' where note_id=?", Integer.toString(id), id);
            assertThat(notes.findScope(id, id)).isNull();
        });
    }
    @Test void shouldDenyMissingReporterRoles_withoutLoadingOrSubstitutingAnotherRole() {
        fixture(() -> {
            membership(id, id); defaultGrant(); assertThat(notes.canRead(provider, scope())).isTrue();
            assertThat(notes.canRead(provider, new CdsExportNoteAccessDto(id, id, Integer.toString(id), Integer.toString(id + 1)))).isFalse();
        });
    }
    @Test void shouldUseCurrentAdmissionIntervals_forProgramZero() {
        fixture(() -> {
            membership(id, id); defaultGrant(); var zero = new CdsExportNoteAccessDto(id, id, "0", Integer.toString(id));
            assertThat(notes.canRead(provider, zero)).isFalse();
            sql.update("insert into admission(am_id,client_id,program_id,provider_no,admission_date,discharge_date,admission_from_transfer,discharge_from_transfer,lastUpdateDate) values(?,?,?,?,'2000-01-01',NULL,0,0,CURRENT_TIMESTAMP)", id, id, id, provider);
            assertThat(notes.canRead(provider, zero)).isTrue();
            sql.update("update admission set discharge_date='2001-01-01' where am_id=?", id); assertThat(notes.canRead(provider, zero)).isFalse();
            sql.update("update admission set discharge_date='0001-01-01' where am_id=?", id); assertThat(notes.canRead(provider, zero)).isTrue();
            sql.update("update admission set admission_date='9999-01-01',discharge_date=NULL where am_id=?", id); assertThat(notes.canRead(provider, zero)).isFalse();
        });
    }
    @Test void shouldRequireAnExistingTransaction_forBothMetadataAndPermissionReads() {
        assertThatThrownBy(() -> notes.findScope(id, id)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> notes.canRead(provider, scope())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
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
            assertions.run();
        });
    }
    private void defaultGrant() {
        sql.update("insert into access_type(access_id,name,type) values(?,?,'access')", id, "Cds" + id + "notes");
        sql.update("insert into default_role_access(id,role_id,access_id) values(?,?,?)", id, id, id);
    }
    private void membership(int program, int role) { sql.update("insert into program_provider(id,program_id,provider_no,role_id) values(?,?,?,?)", id, program, provider, role); }
    private void programGrant(int program, int all) {
        sql.update("insert into access_type(access_id,name,type) values(?,?,'access')", id, "Cds" + id + "notes");
        sql.update("insert into program_access(id,program_id,access_type_id,all_roles) values(?,?,?,?)", id, program, Integer.toString(id), all);
    }
}
