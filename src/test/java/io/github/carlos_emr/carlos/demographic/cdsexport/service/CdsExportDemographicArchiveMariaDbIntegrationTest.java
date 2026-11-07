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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real archived patient snapshots, keyset isolation, metadata, public provider joins and concurrent edits. */
@Tag("integration") @Tag("cds-export")
@EnabledIfSystemProperty(named="cds.export.test.properties", matches=".+")
class CdsExportDemographicArchiveMariaDbIntegrationTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Config { }
    private BasicDataSource source; private AnnotationConfigApplicationContext context; private JdbcTemplate sql; private TransactionTemplate read;
    private final int patient=470_000_000+java.util.concurrent.ThreadLocalRandom.current().nextInt(30_000_000);
    private final long firstId=-9_000_000_000L-patient*100L;
    private final String prefix="h"+UUID.randomUUID().toString().replace("-", "").substring(0, 4);
    private final List<Integer> mergeIds=new ArrayList<>();
    private final List<Integer> mergePatients=new ArrayList<>();
    private final List<String> providers=new ArrayList<>(); private boolean patientInserted;
    private final String address="é😀".repeat(80);
    @BeforeEach void prepareOwnedRows()throws Exception {
        var properties=new Properties();try(var input=Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))){properties.load(input);}
        if(!properties.getProperty("jdbcUrl", "").matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?") || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException("Owned CDS database required");
        source=new BasicDataSource();source.setMaxTotal(4);source.setUrl(properties.getProperty("jdbcUrl"));source.setUsername(properties.getProperty("jdbcUser"));source.setPassword(properties.getProperty("jdbcPassword"));
        sql=new JdbcTemplate(source);assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");context=new AnnotationConfigApplicationContext();context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, ()->source);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, ()->{
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPersistenceUnitName("entityManagerFactory");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(DemographicArchive.class.getName(), DemographicMerged.class.getName(), Provider.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.MariaDBDialect", "hibernate.generate_statistics", "true"));return factory;
        });
        context.registerBean("transactionManager", PlatformTransactionManager.class, ()->new JpaTransactionManager(context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);context.register(DemographicMergedDaoImpl.class, DemographicArchiveDaoImpl.class, io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class);context.refresh();
        read=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(120);
        assertThat(sql.queryForObject("select count(*) from demographic where demographic_no=?", Integer.class, patient)).isZero();
        sql.update("insert into demographic(demographic_no,first_name,last_name,patient_status,lastUpdateDate) values(?,'Synthetic','ArchiveFixture','AC',CURRENT_TIMESTAMP)", patient);patientInserted=true;
        for(int i=0;i<5;i++){
            String id=prefix+i;assertThat(sql.queryForObject("select count(*) from provider where provider_no=?", Integer.class, id)).isZero();
            sql.update("insert into provider(provider_no,first_name,last_name,ohip_no,comments,status,lastUpdateDate) values(?,'Synthetic','Archived clinician','001234','PRIVATE_PROVIDER_SENTINEL',?,CURRENT_TIMESTAMP)", id, i%2==0?"0":"1");providers.add(id);
        }
        sql.update("update demographic set provider_no=?,roster_enrolled_to=? where demographic_no=?", providers.get(0), providers.get(1), patient);
        for(int i=0;i<35;i++)sql.update("insert into demographicArchive(id,demographic_no,first_name,last_name,roster_status,roster_date,patient_status,previousAddress,provider_no,roster_enrolled_to,lastUpdateDate) values(?,?,'Synthetic','ArchiveFixture',?,'2020-02-29',?,?,?,?,'2024-02-29 12:00:00')",
                firstId+i, patient, i%2==0?"RO":"TE", i==0?null:i==1?"":"IN", i==0?address:null, i==33?providers.get(2):null, i==34?providers.get(3):null);
        sql.update("insert into demographicArchive(id,demographic_no,first_name,last_name,provider_no,lastUpdateDate) values(?,?,'Foreign','ArchiveFixture',?,'2024-03-01 12:00:00')", firstId+40, patient+1, providers.get(4));
    }
    @AfterEach void cleanOnlyOwnedRows()throws Exception {
        try{if(sql!=null){
            for(int id:mergeIds)sql.update("delete from demographic_merged where id=? and lastUpdateUser='CDSTST'", id);
            for(int id:mergePatients)sql.update("delete from demographic where demographic_no=? and first_name='Synthetic' and last_name='MergeFixture'", id);
            sql.update("delete from demographicArchive where id between ? and ? and demographic_no in (?,?) and last_name='ArchiveFixture'", firstId, firstId+40, patient, patient+1);
            if(patientInserted)sql.update("delete from demographic where demographic_no=? and first_name='Synthetic' and last_name='ArchiveFixture'", patient);
            for(String id:providers)sql.update("delete from provider where provider_no=? and first_name='Synthetic' and last_name='Archived clinician'", id);
        }}finally{try{if(context!=null)context.close();}finally{if(source!=null)source.close();}}
    }
    @Test void verifiesFullPhysicalSchemaAndActualDriverMetadata() {
        var dao=context.getBean(DemographicArchiveDaoImpl.class);read.executeWithoutResult(status->{
            var schema=dao.readCdsExportSourceSchema();assertThat(schema.columns()).hasSize(58);assertThat(schema.primaryKeyColumns()).containsExactly("id");
            try{new CdsExportSourceSchemaValidator().verify(schema, Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE.columns()));}catch(IOException failure){throw new UncheckedIOException(failure);}
            var metadata=dao.readCdsExportReceiptColumns();assertThat(metadata).hasSize(58);assertThat(metadata.stream().filter(column->column.name().equals("id")).findFirst().orElseThrow().type()).contains("BIGINT");
        });
    }
    @Test void keysetPagesEverySnapshotIncludingRepeatedStatesAndNullOrEmptyValues() {
        var dao=context.getBean(DemographicArchiveDaoImpl.class);read.executeWithoutResult(status->{
            assertThat(dao.countCdsExportRows(patient)).isEqualTo(35);var first=dao.findCdsExportRowsAfter(patient, null, 32);assertThat(first).hasSize(32);
            assertThat(first.getFirst().id()).isEqualTo(firstId);assertThat(first.getFirst().cells()).hasSize(58);
            assertThat(first.getFirst().cells().get("patient_status").sqlNull()).isTrue();assertThat(first.get(1).cells().get("patient_status").sqlNull()).isFalse();
            assertThat(first.get(1).cells().get("patient_status").bytes()).isZero();var rest=dao.findCdsExportRowsAfter(patient, first.getLast().id(), 32);assertThat(rest).hasSize(3);
            assertThat(rest.getLast().id()).isEqualTo(firstId+34);assertThat(dao.findCdsExportRowsAfter(patient, rest.getLast().id(), 32)).isEmpty();
        });
    }
    @Test void rawChunkReadsPreserveUtf8BoundariesAndRejectForeignPatientRowsOrUndeclaredColumns() {
        var dao=context.getBean(DemographicArchiveDaoImpl.class);
        read.executeWithoutResult(status->assertThat(dao.readCdsExportValueChunk(patient, firstId, "previousAddress", 1, 17)).isEqualTo(Arrays.copyOfRange(address.getBytes(StandardCharsets.UTF_8), 1, 18)));
        assertThatThrownBy(()->read.executeWithoutResult(status->dao.readCdsExportValueChunk(patient, firstId+40, "first_name", 0, 10))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->read.executeWithoutResult(status->dao.readCdsExportValueChunk(patient, firstId, "password", 0, 10))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->read.executeWithoutResult(status->dao.findCdsExportRowsAfter(patient, null, 33))).isInstanceOf(IllegalStateException.class);
    }
    @Test void streamsEveryPhysicalFieldWithoutEntityHydrationOrEnrollmentDeduplication()throws Exception {
        var reader=new CdsExportDemographicArchiveSourceReader(context.getBean(DemographicArchiveDaoImpl.class));var values=new ByteArrayOutputStream();var index=new ByteArrayOutputStream();
        var stats=context.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
        read.executeWithoutResult(status->{try{
            new CdsExportNativeCaptureComposer().compose(values, index, Set.of("demographicArchive"), 10000, 1L<<20, ()->{}, writer->{
                writer.beginTable(reader.table(), reader.columns(), reader.count(patient));reader.capture(patient, writer);writer.endTable();
            });
        }catch(IOException failure){throw new UncheckedIOException(failure);}});
        var proof=new CdsExportNativeCaptureReader().verify(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()),
                Map.of("demographicArchive", new CdsExportNativeCaptureReader.Table(reader.columns(), 35)), 10000, 1L<<20, ()->{});
        assertThat(proof.fields()).isEqualTo(35*58);assertThat(stats.getEntityLoadCount()).isZero();assertThat(values.toString(StandardCharsets.UTF_8)).contains(address).doesNotContain("Foreign");
    }
    @Test void keepsTheSameRepeatableReadSnapshotAcrossASeparateCommittedArchiveEdit() {
        var dao=context.getBean(DemographicArchiveDaoImpl.class);read.executeWithoutResult(status->{
            var initial=dao.findCdsExportRowsAfter(patient, null, 1).getFirst();
            try(var separate=source.getConnection();var edit=separate.prepareStatement("update demographicArchive set first_name='ChangedAfterSnapshot' where id=? and demographic_no=?")){
                edit.setLong(1, firstId);edit.setInt(2, patient);assertThat(edit.executeUpdate()).isEqualTo(1);
            }catch(java.sql.SQLException failure){throw new IllegalStateException(failure);}
            assertThat(dao.findCdsExportRowsAfter(patient, null, 1).getFirst()).isEqualTo(initial);
        });
        String updated=read.execute(status->new String(dao.findCdsExportRowsAfter(patient, null, 1).getFirst().cells().get("first_name").inlineBytes(), StandardCharsets.UTF_8));assertThat(updated).isEqualTo("ChangedAfterSnapshot");
    }
    @Test void capturesCurrentAndArchivedPrimaryOrEnrollmentProvidersWithOnlyPublicFields() {
        var dao=context.getBean(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class);read.executeWithoutResult(status->{
            var rows=dao.findCdsExportPublicProvidersAfter(patient, Set.of("Demographics"), null, 32);
            assertThat(rows.stream().map(row->row.id()).toList()).containsExactlyInAnyOrderElementsOf(providers.subList(0, 4));
            assertThat(rows).allSatisfy(row->assertThat(row.cells()).hasSize(6).doesNotContainKeys("comments", "dob"));
            assertThat(dao.countCdsExportPublicProviders(patient, Set.of("Demographics"))).isEqualTo(4);
            assertThat(dao.findCdsExportPublicProvidersAfter(patient, Set.of("AllergiesAndAdverseReactions"), null, 32)).isEmpty();
        });
    }
    @Test void capturesBothMergeDirectionsAndDeletedHistoryWithoutIncludingForeignLinks()throws Exception {
        var dao=context.getBean(DemographicMergedDaoImpl.class);
        for(int id:List.of(patient+2, patient+3, patient+10, patient+12)) {
            assertThat(sql.queryForObject("select count(*) from demographic where demographic_no=?", Integer.class, id)).isZero();
            sql.update("insert into demographic(demographic_no,first_name,last_name,patient_status,lastUpdateDate) values(?,'Synthetic','MergeFixture','AC',CURRENT_TIMESTAMP)", id);mergePatients.add(id);
        }
        for(int i=0;i<3;i++) {
            int id=-patient-i;assertThat(sql.queryForObject("select count(*) from demographic_merged where id=?", Integer.class, id)).isZero();
            int from=i==0?patient:patient+1+i, to=i==1?patient:patient+10+i;
            sql.update("insert into demographic_merged(id,demographic_no,merged_to,deleted,lastUpdateUser,lastUpdateDate) values(?,?,?,?, 'CDSTST','2024-02-29')", id, from, to, i==1?1:0);mergeIds.add(id);
        }
        read.executeWithoutResult(status->{
            try{new CdsExportSourceSchemaValidator().verify(dao.readCdsExportSourceSchema(), Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()));}
            catch(IOException failed){throw new UncheckedIOException(failed);}
            assertThat(dao.readCdsExportReceiptColumns()).hasSize(6);assertThat(dao.countCdsExportRows(patient)).isEqualTo(2);
            var first=dao.findCdsExportRowsAfter(patient, null, 1);assertThat(first).hasSize(1);var rest=dao.findCdsExportRowsAfter(patient, first.getLast().id(), 32);assertThat(rest).hasSize(1);
            var reader=new CdsExportDemographicMergeSourceReader(dao);var values=new ByteArrayOutputStream();var index=new ByteArrayOutputStream();
            try {
                new CdsExportNativeCaptureComposer().compose(values, index, Set.of("demographic_merged"), 1000, 100000, ()->{}, writer->{writer.beginTable(reader.table(), reader.columns(), 2);reader.capture(patient, writer);writer.endTable();});
                var proof=new CdsExportNativeCaptureReader().verify(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()), Map.of("demographic_merged", new CdsExportNativeCaptureReader.Table(reader.columns(), 2)), 1000, 100000, ()->{});
                assertThat(proof.fields()).isEqualTo(12);
            }catch(IOException failed){throw new UncheckedIOException(failed);}
        });
    }
    @Test void refusesRawArchiveReadsOutsideTheOwningSourceTransaction() {
        assertThatThrownBy(()->context.getBean(DemographicArchiveDaoImpl.class).countCdsExportRows(patient)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
