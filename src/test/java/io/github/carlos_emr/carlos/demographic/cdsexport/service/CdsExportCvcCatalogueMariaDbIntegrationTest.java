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

/** Actual bounded public CVC reads and frozen category decisions. Only owned rows are modified. */
@Tag("integration") @Tag("cds-export")
@EnabledIfSystemProperty(named="cds.export.test.properties", matches=".+")
class CdsExportCvcCatalogueMariaDbIntegrationTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Config { }
    private BasicDataSource source; private AnnotationConfigApplicationContext context; private JdbcTemplate sql; private TransactionTemplate read;
    private final int first=-600_000_000-java.util.concurrent.ThreadLocalRandom.current().nextInt(50_000_000);
    private final String prefix="CDS-CVC-"+UUID.randomUUID();
    private final List<Integer> vaccineIds=new ArrayList<>(), mappingIds=new ArrayList<>();
    private CVCImmunizationDaoImpl vaccines; private CVCMappingDaoImpl mappings;
    @BeforeEach void prepare()throws Exception {
        var properties=new Properties(); try (var input=Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))){properties.load(input); }
        if (!properties.getProperty("jdbcUrl", "").matches("jdbc:mysql://[^/]+/cds_export_test(?:\\?.*)?")||!"true".equals(properties.getProperty("ownedDisposableDatabase")))throw new IllegalArgumentException("Owned CDS database required");
        source=new BasicDataSource(); source.setMaxTotal(4); source.setUrl(properties.getProperty("jdbcUrl")); source.setUsername(properties.getProperty("jdbcUser")); source.setPassword(properties.getProperty("jdbcPassword")); sql=new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB"); context=new AnnotationConfigApplicationContext(); context.register(Config.class); context.registerBean("dataSource", DataSource.class, ()-> source);
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, ()-> {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source); factory.setPersistenceUnitName("entityManagerFactory"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setManagedTypes(PersistenceManagedTypes.of(CVCImmunization.class.getName(), CVCMapping.class.getName())); factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.MariaDBDialect", "hibernate.generate_statistics", "true")); return factory;
        });
        context.registerBean("transactionManager", PlatformTransactionManager.class, ()-> new JpaTransactionManager(context.getBean(jakarta.persistence.EntityManagerFactory.class))); context.registerBean(PersistenceAnnotationBeanPostProcessor.class); context.register(CVCImmunizationDaoImpl.class, CVCMappingDaoImpl.class); context.refresh();
        vaccines=context.getBean(CVCImmunizationDaoImpl.class); mappings=context.getBean(CVCMappingDaoImpl.class); read=new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(120);
        for (int i=0; i<35; i++){
            int id=first+i; assertThat(sql.queryForObject("select count(*) from CVCImmunization where id=?", Integer.class, id)).isZero(); assertThat(sql.queryForObject("select count(*) from CVCMapping where id=?", Integer.class, id)).isZero();
            sql.update("insert into CVCImmunization(id,versionId,snomedConceptId,displayName,picklistName,generic,prevalence,parentConceptId,ispa) values(?,42,?,?,?, ?,NULL,NULL,0)", id, "123456789"+i, i==0?null:i==1?"":"Frozen display café 😀", prefix+"-vaccine-"+i, i%2); vaccineIds.add(id);
            sql.update("insert into CVCMapping(id,oscarName,cvcSnomedId,preferCVC) values(?,?,?,?)", id, prefix+"-historic-"+i, "123456789"+i, i%2); mappingIds.add(id);
        }
    }
    @AfterEach void cleanup()throws Exception {
        try {if (sql!=null){for (int id:mappingIds)sql.update("delete from CVCMapping where id=? and oscarName like ?", id, prefix+"%"); for (int id:vaccineIds)sql.update("delete from CVCImmunization where id=? and picklistName like ?", id, prefix+"%"); }}
        finally {try {if (context!=null)context.close(); }finally {if (source!=null)source.close(); }}
    }
    @Test void exactPhysicalSchemaAndActualDriverColumnsArePinnedForBothPublicCatalogues() {
        read.executeWithoutResult(status-> {for (var dao:List.<CdsExportPatientTableReadDao>of(vaccines, mappings))try {
            var schema=dao.readCdsExportSourceSchema(); var columns=dao==vaccines?CdsExportPatientTableReadSupport.Plan.CVC_IMMUNIZATION.columns():CdsExportPatientTableReadSupport.Plan.CVC_MAPPING.columns();
            assertThat(schema.columns()).hasSize(columns.size()); assertThat(schema.primaryKeyColumns()).containsExactly("id"); new CdsExportSourceSchemaValidator().verify(schema, Set.copyOf(columns)); assertThat(dao.readCdsExportReceiptColumns()).hasSize(columns.size());
        }catch (IOException failed){throw new UncheckedIOException(failed); }});
    }
    @Test void boundedKeysetsIncludeGenericTradeAndNullOrEmptyDefinitionsWithoutHydration() {
        var stats=context.getBean(jakarta.persistence.EntityManagerFactory.class).unwrap(org.hibernate.SessionFactory.class).getStatistics(); stats.clear();
        read.executeWithoutResult(status-> {for (var dao:List.<CdsExportPatientTableReadDao>of(vaccines, mappings)){
            long expected=dao.countCdsExportRows(27), total=0; Long after=null; Set<Long> ids=new HashSet<>();
            while (true){var page=dao.findCdsExportRowsAfter(27, after, 32); assertThat(page.size()).isLessThanOrEqualTo(32); for (var row:page){assertThat(ids.add(row.id())).isTrue(); if (after!=null)assertThat(row.id()).isGreaterThan(after); after=row.id(); total++; }if (page.size()<32)break; }
            assertThat(total).isEqualTo(expected).isGreaterThanOrEqualTo(35); assertThat(ids).containsAll(vaccineIds.stream().map(Integer::longValue).toList());
        }
            var firstRows=vaccines.findCdsExportRowsAfter(27, (long) first-1, 32).stream().filter(row-> row.id()==first||row.id()==first+1).toList(); assertThat(firstRows).hasSize(2);
            assertThat(firstRows.get(0).cells().get("displayName").sqlNull()).isTrue(); assertThat(firstRows.get(1).cells().get("displayName").sqlNull()).isFalse(); assertThat(firstRows.get(1).cells().get("displayName").bytes()).isZero();
        }); assertThat(stats.getEntityLoadCount()).isZero();
    }
    @Test void fullComposerAndIndependentReaderAgreeOnEveryCapturedVocabularyField()throws Exception {
        var inputs=List.of(new CdsExportCvcImmunizationSourceReader(vaccines), new CdsExportCvcMappingSourceReader(mappings)); var values=new ByteArrayOutputStream(); var index=new ByteArrayOutputStream(); var expected=new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        read.executeWithoutResult(status-> {try {
            for (var input:inputs)expected.put(input.table(), new CdsExportNativeCaptureReader.Table(input.columns(), input.count(27)));
            new CdsExportNativeCaptureComposer().compose(values, index, expected.keySet(), 100000, 4L<<20, ()-> {}, writer-> {for (var input:inputs){writer.beginTable(input.table(), input.columns(), expected.get(input.table()).rows()); input.capture(27, writer); writer.endTable(); }});
        }catch (IOException failed){throw new UncheckedIOException(failed); }});
        var proof=new CdsExportNativeCaptureReader().verify(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()), expected, 100000, 4L<<20, ()-> {});
        assertThat(proof.fields()).isEqualTo(expected.values().stream().mapToLong(table-> table.rows()*table.columns().size()).sum()); assertThat(values.toString(StandardCharsets.UTF_8)).contains("Frozen display café 😀", prefix+"-historic-");
    }
    @Test void publicVocabularyReadsAreIdenticalForIssuedPositivePatientScopesAndRejectInvalidReads() {
        read.executeWithoutResult(status-> {assertThat(vaccines.findCdsExportRowsAfter(27, null, 32)).isEqualTo(vaccines.findCdsExportRowsAfter(28, null, 32)); });
        assertThatThrownBy(()-> read.executeWithoutResult(status-> vaccines.countCdsExportRows(0))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()-> read.executeWithoutResult(status-> mappings.findCdsExportRowsAfter(27, null, 33))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()-> read.executeWithoutResult(status-> vaccines.readCdsExportValueChunk(27, first, "password", 0, 10))).isInstanceOf(IllegalStateException.class);
    }
    @Test void committedCatalogueChangesDoNotAlterTheOwningRepeatableReadSnapshot() {
        read.executeWithoutResult(status-> {var before=vaccines.findCdsExportRowsAfter(27, (long) first-1, 1).getFirst(); try (var connection=source.getConnection(); var edit=connection.prepareStatement("update CVCImmunization set displayName='ChangedAfterSnapshot' where id=? and picklistName=?")){edit.setInt(1, first); edit.setString(2, prefix+"-vaccine-0"); assertThat(edit.executeUpdate()).isEqualTo(1); }catch (java.sql.SQLException failed){throw new IllegalStateException(failed); }assertThat(vaccines.findCdsExportRowsAfter(27, (long) first-1, 1).getFirst()).isEqualTo(before); });
        assertThat(sql.queryForObject("select displayName from CVCImmunization where id=?", String.class, first)).isEqualTo("ChangedAfterSnapshot");
    }
    @Test void frozenClassifierUsesCapturedCatalogueDefinitionsAfterALaterCommittedEdit()throws Exception {
        var rows=read.execute(status-> {var captured=new ArrayList<CdsExportCapturedTextRowDto>(); for (var dao:List.<CdsExportPatientTableReadDao>of(vaccines, mappings)){
            Long after=null; while (true){var page=dao.findCdsExportRowsAfter(27, after, 32); for (var row:page){var fields=new TreeMap<String, String>(); for (var cell:row.cells().entrySet())fields.put(cell.getKey(), cell.getValue().sqlNull()?null:new String(cell.getValue().inlineBytes(), StandardCharsets.UTF_8)); captured.add(new CdsExportCapturedTextRowDto(dao==vaccines?"CVCImmunization":"CVCMapping", CdsExportPatientTableSourceReader.key(row.id()), fields)); after=row.id(); }if (page.size()<32)break; }
        }return captured; });
        sql.update("update CVCMapping set cvcSnomedId='CHANGED_AFTER_CAPTURE' where id=? and oscarName=?", first, prefix+"-historic-0");
        byte[] config="<preventions><item name='PAP' layout='PAPMAM' headingName='Screenings'/></preventions>".getBytes(StandardCharsets.UTF_8); var classifier=CdsExportPreventionClassifier.fromCaptured("a".repeat(64), config, rows);
        assertThat(classifier.classify(prefix+"-historic-0", null).kind()).isEqualTo(CdsExportPreventionClassifier.Kind.IMMUNIZATION); assertThat(classifier.classify("PAP", null).kind()).isEqualTo(CdsExportPreventionClassifier.Kind.NON_IMMUNIZATION);
    }
    @Test void existingNativeCvcFindersKeepTheirHistoricalContractsBesideTheRawReaders() {
        read.executeWithoutResult(status-> {
            var mapped=mappings.findByOscarName(prefix+"-historic-0"); assertThat(mapped).isNotNull(); assertThat(mapped.getCvcSnomedId()).isEqualTo("1234567890"); assertThat(mapped.getPreferCVC()).isFalse();
            assertThat(mappings.findMultipleByOscarName(prefix+"-historic-0")).hasSize(1);
            var vaccine=vaccines.findBySnomedConceptId("1234567890"); assertThat(vaccine).isNotNull(); assertThat(vaccine.getId()).isEqualTo(first); assertThat(vaccine.getPicklistName()).isEqualTo(prefix+"-vaccine-0");
            assertThat(vaccines.findAllGeneric().stream().map(CVCImmunization::getId).toList()).contains(first+1).doesNotContain(first);
        });
    }
    @Test void noRawVocabularyReadCanEscapeTheOwningReadOnlyTransaction() {
        assertThatThrownBy(()-> vaccines.countCdsExportRows(27)).isInstanceOf(IllegalTransactionStateException.class);
        var write=new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); assertThatThrownBy(()-> write.executeWithoutResult(status-> mappings.countCdsExportRows(27))).isInstanceOf(IllegalStateException.class);
    }
}
