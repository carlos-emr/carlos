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
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real owning-DAO patient scopes, snapshot chunks, physical receipts and adverse deleted/current ownership. */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportDocumentReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private DocumentDao documents;
    private CtlDocumentDao links;
    private CdsExportSourceCatalogueDao catalogue;
    private io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao
            hospitalReports;
    private final int patient =
            560_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final String marker = "OwnedReport" + patient;
    private final String body = "Complete document 😀 & exact text\r\n".repeat(3000);
    private boolean owned;
    private boolean ownedReviewer;

    @BeforeEach
    void setup() throws Exception {
        var settings = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            settings.load(input);
        }
        if (!"true".equals(settings.getProperty("ownedDisposableDatabase"))
                || !settings.getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Owned disposable care database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(settings.getProperty("jdbcUrl"));
        source.setUsername(settings.getProperty("jdbcUser"));
        source.setPassword(settings.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(source);
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(PersistenceManagedTypes.of());
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect",
                                    "hibernate.show_sql",
                                    "false"));
                    return factory;
                });
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.registerBean(
                "transactionManager",
                JpaTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(
                "cacheManager",
                org.springframework.cache.CacheManager.class,
                org.springframework.cache.support.NoOpCacheManager::new);
        context.register(
                CdsExportSourceCatalogueDao.class,
                io.github
                        .carlos_emr
                        .carlos
                        .hospitalReportManager
                        .dao
                        .HRMDocumentToDemographicDao
                        .class,
                DocumentDaoImpl.class,
                CtlDocumentDaoImpl.class,
                io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTargetReceiptService
                        .class);
        context.refresh();
        catalogue = context.getBean(CdsExportSourceCatalogueDao.class);
        hospitalReports =
                context.getBean(
                        io.github
                                .carlos_emr
                                .carlos
                                .hospitalReportManager
                                .dao
                                .HRMDocumentToDemographicDao
                                .class);
        documents = context.getBean(DocumentDao.class);
        links = context.getBean(CtlDocumentDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(
                        status -> {
                            for (int i = 0; i < 36; i++) {
                                sql.update(
                                        "insert into"
                                            + " document(document_no,docdesc,doctype,docfilename,doccreator,contenttype,responsible,public1,abnormal,status,restrictToProgram,docxml,source)"
                                            + " values(?,?,?,'owned-source.txt','','text/plain','','0','0','A',0,?,?)",
                                        patient + i,
                                        marker,
                                        marker,
                                        body,
                                        i == 0 ? null : "");
                                sql.update(
                                        "insert into"
                                            + " ctl_document(module,module_id,document_no,status)"
                                            + " values('demographic',?,?,'A')",
                                        i == 35 ? patient + 1 : patient,
                                        patient + i);
                            }
                        });
        owned = true;
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from HRMDocumentToDemographic where id=? and hrmDocumentId=?",
                        patient,
                        Integer.toString(patient + 500));
                sql.update(
                        "delete from casemgmt_cpp where id=? and demographic_no=?",
                        patient,
                        Integer.toString(patient));
                sql.update(
                        "delete from formRourke2009 where ID=? and demographic_no=?",
                        patient,
                        patient);
                sql.update(
                        "delete from document_review where id=? and document_no=? and"
                                + " provider_no='989875'",
                        patient,
                        patient);
                sql.update(
                        "delete from DocumentExtraReviewer where id=? and documentNo=? and"
                                + " reviewerProviderNo='989879'",
                        patient,
                        patient);
                sql.update(
                        "delete from document_storage where id=? and documentNo=?",
                        patient,
                        patient);
                sql.update(
                        "delete c from ctl_document c join document d on"
                            + " d.document_no=c.document_no where d.document_no between ? and ? and"
                            + " d.doctype=?",
                        patient,
                        patient + 35,
                        marker);
                sql.update(
                        "delete from ctl_document where module='demographic' and module_id=? and"
                                + " document_no=?",
                        patient,
                        patient + 100);
                sql.update(
                        "delete from document where document_no between ? and ? and doctype=?",
                        patient,
                        patient + 35,
                        marker);
                if (ownedReviewer)
                    assertThat(
                                    sql.update(
                                            "delete from provider where provider_no='989875' and"
                                                + " first_name='OwnedDocSource' and last_name='CDS"
                                                + " source fixture'"))
                            .isEqualTo(1);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    @Test
    void shouldAllowTheInstalledClinicSiteDirectory_withoutHidingUnsupportedClinicalSources() {
        boolean siteCreated = false;
        boolean linkCreated = false;
        try {
            assertThat(
                            sql.update(
                                    "insert into site(site_id,name,short_name,bg_color,status)"
                                            + " values(?,?,?,'white',1)",
                                    patient,
                                    marker,
                                    "CDS-owned"))
                    .isEqualTo(1);
            siteCreated = true;
            assertThat(
                            sql.update(
                                    "insert into providersite(provider_no,site_id)"
                                            + " values('999998',?)",
                                    patient))
                    .isEqualTo(1);
            linkCreated = true;
            stableLegacy(
                    () ->
                            read.executeWithoutResult(
                                    status -> {
                                        catalogue.readSourceSchema();
                                        catalogue.requireUnclassifiedSourcesEmpty(
                                                patient, () -> {});
                                    }));
            // A clinical source remains subject to the same fail-closed closure check.
            sql.update(
                    "insert into casemgmt_cpp(id,demographic_no,socialHistory,provider_no)"
                            + " values(?,?,?,'989879')",
                    patient,
                    Integer.toString(patient),
                    "Owned unsupported clinical fact");
            try {
                stableLegacy(
                        () ->
                                assertThatThrownBy(
                                                () ->
                                                        read.executeWithoutResult(
                                                                status ->
                                                                        catalogue
                                                                                .requireUnclassifiedSourcesEmpty(
                                                                                        patient,
                                                                                        () -> {})))
                                        .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED"));
            } finally {
                sql.update(
                        "delete from casemgmt_cpp where id=? and demographic_no=?",
                        patient,
                        Integer.toString(patient));
            }
        } finally {
            if (linkCreated)
                assertThat(
                                sql.update(
                                        "delete from providersite where provider_no='999998' and"
                                                + " site_id=?",
                                        patient))
                        .isEqualTo(1);
            if (siteCreated)
                assertThat(
                                sql.update(
                                        "delete from site where site_id=? and name=?",
                                        patient,
                                        marker))
                        .isEqualTo(1);
        }
    }

    @Test
    void shouldPageEveryOwnedDocument_andLinkIncludingInactiveRows() {
        sql.update(
                "update document set status='D' where document_no=? and doctype=?",
                patient,
                marker);
        sql.update(
                "update ctl_document set status='D' where module='demographic' and module_id=? and"
                        + " document_no=?",
                patient,
                patient);
        read.executeWithoutResult(
                status -> {
                    for (var dao : List.of((CdsExportPatientTableReadDao) documents, links)) {
                        assertThat(dao.countCdsExportRows(patient)).isEqualTo(35);
                        var first = dao.findCdsExportRowsAfter(patient, null, 32);
                        var last = dao.findCdsExportRowsAfter(patient, first.getLast().id(), 32);
                        assertThat(first).hasSize(32);
                        assertThat(last).hasSize(3);
                        assertThat(first.getFirst().id()).isEqualTo(patient);
                        assertThat(last.getLast().id()).isEqualTo(patient + 34);
                        assertThat(dao.findCdsExportRowsAfter(patient, last.getLast().id(), 32))
                                .isEmpty();
                    }
                });
    }

    @Test
    void shouldReadOnlyFilesTheChartShows_leavingRemovedAndStoredHtmlDocumentsUnopened() {
        // Every fixture row starts as status A with stored HTML in docxml, which the chart
        // renders instead of any file. Make some of them ordinary uploads (empty or NULL
        // docxml), then soft-delete, unlink, convert to an Add HTML document (status H) or
        // give a status that differs only in case (reviews R3 and R6).
        sql.update(
                "update document set docxml='' where document_no in (?,?,?,?,?) and doctype=?",
                patient + 1,
                patient + 3,
                patient + 4,
                patient + 6,
                patient + 7,
                marker);
        sql.update(
                "update document set docxml=NULL where document_no=? and doctype=?",
                patient + 2,
                marker);
        sql.update(
                "update document set status='D' where document_no=? and doctype=?",
                patient + 3,
                marker);
        sql.update(
                "update ctl_document set status='D' where module='demographic' and module_id=?"
                        + " and document_no=?",
                patient,
                patient + 4);
        sql.update(
                "update document set status='H' where document_no=? and doctype=?",
                patient + 5,
                marker);
        sql.update(
                "update document set status='a' where document_no=? and doctype=?",
                patient + 6,
                marker);
        sql.update(
                "update ctl_document set status=NULL where module='demographic' and module_id=?"
                        + " and document_no=?",
                patient,
                patient + 7);
        read.executeWithoutResult(
                status -> {
                    assertThat(documents.countCdsExportFileRows(patient)).isEqualTo(3);
                    var rows = documents.findCdsExportFileRowsAfter(patient, null, 32);
                    assertThat(rows)
                            .extracting(io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto::id)
                            .containsExactly(
                                    (long) patient + 1, (long) patient + 2, (long) patient + 7);
                    assertThat(rows.getFirst().cells().keySet())
                            .containsExactly("docfilename", "document_no");
                    assertThat(
                                    documents.findCdsExportFileRowsAfter(
                                            patient, rows.getLast().id(), 32))
                            .isEmpty();
                    // The metadata domain still captures every row for its explicit disposition.
                    assertThat(documents.countCdsExportRows(patient)).isEqualTo(35);
                    // Another patient's file is never in this patient's file domain.
                    assertThat(documents.countCdsExportFileRows(patient + 1)).isZero();
                });
    }

    @Test
    void shouldPreserveCompleteDocumentXml_andNullVersusEmptySource() {
        read.executeWithoutResult(
                status -> {
                    var rows = documents.findCdsExportRowsAfter(patient, null, 32);
                    assertThat(rows.getFirst().cells().get("source").sqlNull()).isTrue();
                    assertThat(rows.get(1).cells().get("source").sqlNull()).isFalse();
                    assertThat(rows.get(1).cells().get("source").bytes()).isZero();
                    var cell = rows.getFirst().cells().get("docxml");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(bytes.length);
                    var hash = CdsImportKeys.digest();
                    for (int offset = 0; offset < bytes.length; offset += 65536)
                        hash.update(
                                documents.readCdsExportValueChunk(
                                        patient,
                                        patient,
                                        "docxml",
                                        offset,
                                        Math.min(65536, bytes.length - offset)));
                    assertThat(HexFormat.of().formatHex(hash.digest())).isEqualTo(cell.sha256());
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                documents.readCdsExportValueChunk(
                                                        patient, patient + 35, "docxml", 0, 10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                links.readCdsExportValueChunk(
                                                        patient, patient + 35, "status", 0, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldVerifyBothPhysicalSchemas_andWholeOriginalDriverRecipes() {
        read.executeWithoutResult(
                status -> {
                    for (var reader :
                            List.of(
                                    new CdsExportDocumentSourceReader(documents),
                                    new CdsExportDocumentLinkSourceReader(links))) {
                        assertThatCode(
                                        () ->
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(reader.schema(), reader.columns()))
                                .doesNotThrowAnyException();
                        assertThat(reader.receiptColumns())
                                .extracting(column -> column.name())
                                .containsExactlyInAnyOrderElementsOf(reader.columns());
                    }
                });
        assertThatThrownBy(() -> documents.readCdsExportReceiptColumns())
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void shouldRefuseSharedDocuments_forEitherPatientBeforeReturningHeaders() {
        sql.update(
                "insert into ctl_document(module,module_id,document_no,status)"
                        + " values('demographic',?,?,'A')",
                patient + 1,
                patient);
        assertThatThrownBy(() -> read.execute(status -> documents.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                links.findCdsExportRowsAfter(
                                                        patient + 1, null, 32)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldRefuseDanglingLinks_insteadOfSilentlyDroppingAnOccurrence() {
        sql.update(
                "insert into ctl_document(module,module_id,document_no,status)"
                        + " values('demographic',?,?,'A')",
                patient,
                patient + 100);
        assertThatThrownBy(() -> read.execute(status -> documents.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldRequireReviewForUnqualifiedProgramRestrictions_beforeAnyDocumentPayload() {
        sql.update(
                "update document set restrictToProgram=1,program_id=57 where document_no=? and"
                        + " doctype=?",
                patient,
                marker);
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                documents.findCdsExportRowsAfter(
                                                        patient, null, 32)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(() -> read.execute(status -> links.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRequireReview_forSeparateNativeReviewerHistory() {
        assertThat(
                        sql.queryForObject(
                                "select count(*) from provider where provider_no='989875'",
                                Long.class))
                .isZero();
        sql.update(
                "insert into"
                    + " provider(provider_no,first_name,last_name,provider_type,specialty,sex,status,lastUpdateDate)"
                    + " values('989875','OwnedDocSource','CDS source"
                    + " fixture','doctor','','U','1',CURRENT_TIMESTAMP)");
        ownedReviewer = true;
        sql.update(
                "insert into document_review(id,document_no,provider_no,date_reviewed)"
                        + " values(?,?,'989875',CURRENT_TIMESTAMP)",
                patient,
                patient);
        assertThatThrownBy(() -> read.execute(status -> documents.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRequireReview_forAdditionalNativeReviewerAssociations() {
        sql.update(
                "insert into DocumentExtraReviewer(id,documentNo,reviewerProviderNo,reviewDateTime)"
                        + " values(?,?,'989879',CURRENT_TIMESTAMP)",
                patient,
                patient);
        assertThatThrownBy(() -> read.execute(status -> links.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRequireReview_forUnqualifiedDatabaseFileStorage() {
        sql.update(
                "insert into document_storage(id,documentNo,fileContents,uploadDate)"
                        + " values(?,?,?,CURRENT_TIMESTAMP)",
                patient,
                patient,
                body.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                documents.findCdsExportRowsAfter(
                                                        patient, null, 32)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldReproduceOriginalDocumentAndCompositeLinkReceipts_forDocumentReadContract() {
        read.executeWithoutResult(
                status -> {
                    var original =
                            context.getBean(
                                            io.github.carlos_emr.carlos.demographic.cdsimport
                                                    .service.CdsTargetReceiptService.class)
                                    .capture(
                                            Map.of(
                                                    "document",
                                                    Integer.toString(patient),
                                                    "ctl_document",
                                                    patient + "_" + patient));
                    try {
                        var refs =
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(original.targetReferences())
                                        .get("refs");
                        assertThat(refs.size()).isEqualTo(2);
                        for (var ref : refs) {
                            var dao =
                                    ref.get("table").asText().equals("document")
                                            ? (CdsExportPatientTableReadDao) documents
                                            : links;
                            var row = dao.findCdsExportRowsAfter(patient, null, 32).getFirst();
                            var columns = dao.readCdsExportReceiptColumns();
                            var hash = CdsNativeRowDigest.hasher(columns);
                            for (var column : columns) {
                                var cell = row.cells().get(column.name());
                                hash.append(cell.sqlNull(), cell.bytes(), cell.sha256());
                            }
                            assertThat(hash.finish()).isEqualTo(ref.get("digest").asText());
                        }
                    } catch (java.io.IOException failed) {
                        throw new java.io.UncheckedIOException(failed);
                    }
                });
    }

    @Test
    void shouldKeepChunksInTheirCapturedSnapshot_afterConcurrentDocumentEdit() {
        try {
            read.executeWithoutResult(
                    status -> {
                        var cell =
                                documents
                                        .findCdsExportRowsAfter(patient, null, 32)
                                        .getFirst()
                                        .cells()
                                        .get("docxml");
                        try (var writer =
                                java.util.concurrent.Executors.newSingleThreadExecutor()) {
                            writer.submit(
                                            () ->
                                                    sql.update(
                                                            "update document set docxml='Owned"
                                                                + " concurrent edit' where"
                                                                + " document_no=? and doctype=?",
                                                            patient,
                                                            marker))
                                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception failed) {
                            throw new IllegalStateException(failed);
                        }
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        var hash = CdsImportKeys.digest();
                        for (int offset = 0; offset < bytes.length; offset += 65536)
                            hash.update(
                                    documents.readCdsExportValueChunk(
                                            patient,
                                            patient,
                                            "docxml",
                                            offset,
                                            Math.min(65536, bytes.length - offset)));
                        assertThat(HexFormat.of().formatHex(hash.digest()))
                                .isEqualTo(cell.sha256());
                    });
        } finally {
            sql.update(
                    "update document set docxml=? where document_no=? and doctype=?",
                    body,
                    patient,
                    marker);
        }
    }

    private void hospitalReportLink(String owner) {
        sql.update(
                "insert into HRMDocumentToDemographic(id,demographicNo,hrmDocumentId,timeAssigned)"
                        + " values(?,?,?,CURRENT_TIMESTAMP)",
                patient,
                owner,
                Integer.toString(patient + 500));
    }

    @Test
    void shouldVerifyThePhysicalHospitalReportLinkContract_andRecordExactAbsence() {
        read.executeWithoutResult(
                status -> {
                    var reader = new CdsExportHospitalReportScopeSourceReader(hospitalReports);
                    assertThatCode(
                                    () ->
                                            new CdsExportSourceSchemaValidator()
                                                    .verify(reader.schema(), reader.columns()))
                            .doesNotThrowAnyException();
                    assertThat(reader.count(patient)).isZero();
                    reader.capture(patient, null);
                });
        assertThatThrownBy(() -> hospitalReports.requireCdsExportAbsent(patient))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void shouldRefuseDanglingHospitalReports_beforeTheyCanAppearAsAnEmptyDocumentDomain() {
        hospitalReportLink(Integer.toString(patient));
        var reader = new CdsExportHospitalReportScopeSourceReader(hospitalReports);
        assertThatThrownBy(() -> read.execute(status -> reader.count(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(() -> read.executeWithoutResult(status -> reader.capture(patient, null)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void
            shouldConservativelyRefuseLegacyNumericPatientAliases_withoutLoadingHospitalReportContent() {
        hospitalReportLink("0" + patient);
        for (String alias :
                List.of(
                        "0" + patient,
                        "+" + patient,
                        patient + "suffix",
                        " " + patient,
                        "\t" + patient,
                        "\r\n+0" + patient + "x",
                        patient + ".9",
                        patient + "e2")) {
            sql.update(
                    "update HRMDocumentToDemographic set demographicNo=? where id=? and"
                            + " hrmDocumentId=?",
                    alias,
                    patient,
                    Integer.toString(patient + 500));
            assertThatThrownBy(
                            () ->
                                    read.executeWithoutResult(
                                            status ->
                                                    hospitalReports.requireCdsExportAbsent(
                                                            patient)))
                    .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        }
    }

    @Test
    void shouldPreserveLegacyHrmPositionalInsertsAndSelectStar_withTheIndexedDerivedKey() {
        sql.update(
                "INSERT INTO HRMDocumentToDemographic VALUES(?,?,?,CURRENT_TIMESTAMP)",
                patient,
                "0" + patient + "suffix",
                Integer.toString(patient + 500));
        var original =
                sql.queryForMap("SELECT * FROM HRMDocumentToDemographic WHERE id=?", patient);
        assertThat(original.keySet())
                .containsExactlyInAnyOrder("id", "demographicNo", "hrmDocumentId", "timeAssigned");
        assertThat(original.get("demographicNo")).isEqualTo("0" + patient + "suffix");
        assertThat(
                        sql.queryForObject(
                                "SELECT cds_patient_key FROM HRMDocumentToDemographic WHERE id=?",
                                java.math.BigDecimal.class,
                                patient))
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(patient));
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status -> hospitalReports.requireCdsExportAbsent(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldUseTheNumericPatientIndex_forLargeUnrelatedHospitalReportLinkSets() {
        String owner = "perf" + patient;
        try {
            var batch = new java.util.ArrayList<Object[]>();
            for (int n = 0; n < 20_000; n++)
                batch.add(
                        new Object[] {patient + n, owner, Integer.toString(patient + 50_000 + n)});
            sql.batchUpdate(
                    "INSERT INTO"
                        + " HRMDocumentToDemographic(id,hrmDocumentId,demographicNo,timeAssigned)"
                        + " VALUES(?,?,?,CURRENT_TIMESTAMP)",
                    batch);
            var plan =
                    sql.queryForMap(
                            "EXPLAIN SELECT 1,1 FROM HRMDocumentToDemographic d WHERE"
                                    + " d.cds_patient_key=? LIMIT 1",
                            patient);
            assertThat(plan.get("key")).isEqualTo("idx_cds_hrm_patient_key");
            assertThat(plan.get("type")).isEqualTo("ref");
            assertThat(Long.parseLong(plan.get("rows").toString())).isLessThanOrEqualTo(2);
            for (int n = 0; n < 25; n++) {
                int selected = patient + n;
                read.executeWithoutResult(
                        status -> hospitalReports.requireCdsExportAbsent(selected));
            }
        } finally {
            sql.update(
                    "DELETE FROM HRMDocumentToDemographic WHERE id>=? AND id<? AND hrmDocumentId=?",
                    patient,
                    patient + 20_000,
                    owner);
        }
    }

    @Test
    void shouldKeepOtherPatientsHospitalReportsOutsideTheSelectedChart_forDocumentReadContract() {
        hospitalReportLink(Integer.toString(patient + 1));
        assertThatCode(
                        () ->
                                read.executeWithoutResult(
                                        status -> hospitalReports.requireCdsExportAbsent(patient)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRepeatHospitalReportAbsenceInsideTheSamePatientSnapshot_forDocumentReadContract() {
        read.executeWithoutResult(
                status -> {
                    hospitalReports.requireCdsExportAbsent(patient);
                    try (var writer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                        writer.submit(() -> hospitalReportLink(Integer.toString(patient)))
                                .get(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                    hospitalReports.requireCdsExportAbsent(patient);
                });
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status -> hospitalReports.requireCdsExportAbsent(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldVerifyTheWholePhysicalSourceContract_withoutReadingClinicalValues() {
        stableLegacy(
                () ->
                        read.executeWithoutResult(
                                status -> {
                                    var schema = catalogue.readSourceSchema();
                                    assertThatCode(
                                                    () ->
                                                            new CdsExportSourceSchemaValidator()
                                                                    .verify(
                                                                            schema,
                                                                            Set.of(
                                                                                    "catalogueSha256")))
                                            .doesNotThrowAnyException();
                                    catalogue.requireUnclassifiedSourcesEmpty(patient, () -> {});
                                }));
        assertThatThrownBy(catalogue::readSourceSchema)
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void shouldAllowReviewedProviderPreferencesAndReportDefinitions_withoutReadingTheirContents() {
        try {
            sql.update(
                    "INSERT INTO quickListUser(id,providerNo,quickListName,lastUsed)"
                            + " VALUES(?,'989875','owned',CURRENT_TIMESTAMP)",
                    patient);
            sql.update(
                    "INSERT INTO provider_default_program(id,provider_no,program_id,signnote)"
                            + " VALUES(?,'989875',17,0)",
                    patient);
            sql.update(
                    "INSERT INTO"
                        + " reportTemplates(templateid,templatetitle,templatedescription,templatesql,templatexml)"
                        + " VALUES(?,'Owned definition','Owned definition','SELECT"
                        + " private_definition_sentinel','<template/>')",
                    patient);
            sql.update("INSERT INTO messengerMembershipLock(id) VALUES(?)", patient);
            stableLegacy(
                    () ->
                            read.executeWithoutResult(
                                    status ->
                                            catalogue.requireUnclassifiedSourcesEmpty(
                                                    patient, () -> {})));
        } finally {
            sql.update(
                    "DELETE FROM quickListUser WHERE id=? AND providerNo='989875' AND"
                            + " quickListName='owned'",
                    patient);
            sql.update(
                    "DELETE FROM provider_default_program WHERE id=? AND provider_no='989875' AND"
                            + " program_id=17",
                    patient);
            sql.update(
                    "DELETE FROM reportTemplates WHERE templateid=? AND templatetitle='Owned"
                            + " definition'",
                    patient);
            sql.update("DELETE FROM messengerMembershipLock WHERE id=?", patient);
        }
    }

    @Test
    void shouldRefuseUnclassifiedClinicalSourceRows_insteadOfTreatingTheChartAsFullyCovered() {
        sql.update(
                "insert into casemgmt_cpp(id,demographic_no,socialHistory,provider_no)"
                        + " values(?,?,?,'989879')",
                patient,
                Integer.toString(patient),
                "Owned unrepresented clinical source");
        assertThatThrownBy(
                        () ->
                                read.executeWithoutResult(
                                        status ->
                                                catalogue.requireUnclassifiedSourcesEmpty(
                                                        patient, () -> {})))
                .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
    }

    @Test
    void shouldListAnUnknownLocalTableOnlyWhenPopulated_withoutRefusingTheExport() {
        // Decision 20: a local table the release does not know (another tool's bookkeeping, a
        // DBA's copy) neither blocks nor is exported; a populated one is listed by name.
        String table = "cds_export_coverage_probe_" + patient;
        sql.execute("create table `" + table + "` (id int primary key) engine=InnoDB");
        try {
            stableLegacy(
                    () -> {
                        read.executeWithoutResult(status -> catalogue.readSourceSchema());
                        java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto>
                                empty = read.execute(status -> catalogue.unreviewedLocalSources());
                        assertThat(empty)
                                .extracting(
                                        io.github.carlos_emr.carlos.commn.dto
                                                        .CdsExportLocalSourceDto
                                                ::table)
                                .doesNotContain(table);
                    });
            sql.update("insert into `" + table + "` values (1)");
            stableLegacy(
                    () -> {
                        java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto>
                                listed = read.execute(status -> catalogue.unreviewedLocalSources());
                        assertThat(listed)
                                    .anySatisfy(
                                            local -> {
                                                assertThat(local.table()).isEqualTo(table);
                                                assertThat(local.estimatedRows()).isPositive();
                                            });
                    });
        } finally {
            sql.execute("drop table `" + table + "`");
        }
    }

    @Test
    void shouldStopTheJobAndNameTheTable_whenAClinicWideMustBeEmptySourceIsPopulated() {
        // DrugProduct (a dead dispensing table) has no patient link: a row there concerns every
        // patient, so the job stops once with the clinic code instead of holding every patient
        // one by one, and the table is named on the channel the CLI prints (census D5). The
        // recycle bin used here before is now ruled operational.
        sql.update(
                "insert into DrugProduct(name,code,amount,lastUpdateDate,lastUpdateUser)"
                        + " values('synthetic','synthetic',1,now(),'999998')");
        try (var log =
                io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(
                        "carlos.cds.safeTechnical")) {
            stableLegacy(
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    read.executeWithoutResult(
                                                            status ->
                                                                    catalogue
                                                                            .requireUnclassifiedSourcesEmpty(
                                                                                    patient,
                                                                                    () -> {})))
                                    .hasMessage("CDS_EXPORT_CLINIC_SOURCE_UNREVIEWED"));
            assertThat(log.messages())
                    .anySatisfy(
                            message ->
                                    assertThat(message)
                                            .contains(
                                                    "CDS_EXPORT_SOURCE:",
                                                    "CDS_EXPORT_CLINIC_SOURCE_UNREVIEWED",
                                                    "\"DrugProduct\"")
                                            .doesNotContain("synthetic", "999998"));
        } finally {
            sql.update("delete from DrugProduct where name='synthetic'");
        }
    }

    @Test
    void shouldRefuseAnUnknownColumnInAnOtherwiseOperationalTable_beforeExcludingItsValues() {
        String column = "cds_export_probe_" + patient;
        sql.execute("alter table AppDefinition add column `" + column + "` varchar(8) null");
        try {
            // A changed pinned table concerns every patient, so the whole job stops.
            stableLegacy(
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    read.executeWithoutResult(
                                                            status -> catalogue.readSourceSchema()))
                                    .hasMessage("CDS_EXPORT_CLINIC_SOURCE_UNREVIEWED"));
        } finally {
            sql.execute("alter table AppDefinition drop column `" + column + "`");
        }
    }

    private io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
            legacyLock() throws java.sql.SQLException {
        return io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                .acquireUnpooled(
                        java.sql.DriverManager.getConnection(
                                source.getUrl(), source.getUsername(), source.getPassword()));
    }

    private void stableLegacy(Runnable work) {
        try (var held = legacyLock()) {
            work.run();
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException("OWNED_SOURCE_SNAPSHOT_LOCK_FAILED", failed);
        }
    }

    private void requireCatalogueScope() {
        stableLegacy(
                () ->
                        read.executeWithoutResult(
                                status -> {
                                    catalogue.readSourceSchema();
                                    catalogue.requireUnclassifiedSourcesEmpty(patient, () -> {});
                                }));
    }

    @Test
    void shouldIgnoreAnotherPatientsUnsupportedRecord_andRefuseTheSelectedPatientsRecord() {
        sql.update(
                "insert into CdsHospitalisationDays(id,clientId,admitted) values(?,?,'2024-02-29')",
                patient,
                patient + 77);
        try {
            requireCatalogueScope();
            sql.update("update CdsHospitalisationDays set clientId=? where id=?", patient, patient);
            assertThatThrownBy(this::requireCatalogueScope)
                    .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
        } finally {
            sql.update(
                    "delete from CdsHospitalisationDays where id=? and clientId in (?,?)",
                    patient,
                    patient,
                    patient + 77);
        }
    }

    @Test
    void shouldRefuseANumericTextPatientAlias_withoutHidingItBehindCanonicalStringEquality() {
        String alias = "0" + patient;
        sql.update(
                "insert into casemgmt_cpp(id,demographic_no,provider_no,socialHistory)"
                        + " values(?,?,'0',?)",
                patient,
                alias,
                "OWNED_SOURCE_ALIAS");
        try {
            assertThatThrownBy(this::requireCatalogueScope)
                    .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
        } finally {
            sql.update("delete from casemgmt_cpp where id=? and demographic_no=?", patient, alias);
        }
    }

    @Test
    void shouldCheckBothRelationshipEndpoints_beforeClaimingPatientSourceAbsence() {
        sql.update(
                "insert into relationships(id,demographic_no,relation_demographic_no,notes)"
                        + " values(?,?,?,?)",
                patient,
                patient + 77,
                patient,
                "OWNED_RELATED_PATIENT_FACT");
        try {
            assertThatThrownBy(this::requireCatalogueScope)
                    .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
        } finally {
            sql.update(
                    "delete from relationships where id=? and relation_demographic_no=?",
                    patient,
                    patient);
        }
    }

    @Test
    void shouldAllowProgramAccessMetadata_butRefuseEveryPopulatedClinicalRemainder() {
        sql.update(
                "insert into"
                    + " admission(am_id,client_id,program_id,admission_status,admission_date,admission_from_transfer,discharge_from_transfer,lastUpdateDate,radioDischargeReason)"
                    + " values(?, ?, 1, 'admitted', '2024-02-29 00:00:00',0,0,'2024-02-29"
                    + " 00:00:00','')",
                patient,
                patient);
        try {
            requireCatalogueScope();
            for (String update :
                    List.of(
                            "update admission set"
                                + " admission_notes='OWNED',discharge_notes=NULL,radioDischargeReason=NULL"
                                + " where am_id=?",
                            "update admission set"
                                + " admission_notes=NULL,discharge_notes='OWNED',radioDischargeReason=NULL"
                                + " where am_id=?",
                            "update admission set"
                                + " admission_notes=NULL,discharge_notes=NULL,radioDischargeReason='OWNED'"
                                + " where am_id=?")) {
                sql.update(update, patient);
                assertThatThrownBy(this::requireCatalogueScope)
                        .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
            }
        } finally {
            sql.update("delete from admission where am_id=? and client_id=?", patient, patient);
        }
    }

    @Test
    void shouldAcceptALongerFiniteLegacySnapshotBudget_andStillRefuseAnUnboundedBudget()
            throws Exception {
        try (var held =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                        .acquireUnpooled(
                                java.sql.DriverManager.getConnection(
                                        source.getUrl(),
                                        source.getUsername(),
                                        source.getPassword()),
                                600_000)) {
            held.verify();
        }
        var connection =
                java.sql.DriverManager.getConnection(
                        source.getUrl(), source.getUsername(), source.getPassword());
        assertThatThrownBy(
                        () ->
                                io.github.carlos_emr.carlos.demographic.cdsexport.support
                                        .CdsExportSourceReadLock.acquireUnpooled(
                                        connection, 7_230_001))
                .hasMessage("CDS_EXPORT_SOURCE_READ_LOCK_BUDGET_INVALID");
        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    void shouldRefuseAnUnpinnedNontransactionalSource_evenWhenItsCurrentCountIsZero() {
        assertThatThrownBy(() -> read.executeWithoutResult(status -> catalogue.readSourceSchema()))
                .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
    }

    @Test
    void
            shouldKeepTheLegacyTableStableUntilThePatientSnapshotReleasesItsReadLock_forDocumentReadContract()
                    throws Exception {
        sql.update(
                "insert into formRourke2009(ID,demographic_no,provider_no) values(?,?,'989879')",
                patient,
                patient);
        try (var writer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var started = new java.util.concurrent.CountDownLatch(1);
            var writerId = new java.util.concurrent.atomic.AtomicLong();
            java.util.concurrent.Future<Integer> pending;
            try (var held = legacyLock()) {
                pending =
                        writer.submit(
                                () -> {
                                    try (var connection =
                                                    java.sql.DriverManager.getConnection(
                                                            source.getUrl(),
                                                            source.getUsername(),
                                                            source.getPassword());
                                            var id = connection.createStatement();
                                            var ids = id.executeQuery("select CONNECTION_ID()")) {
                                        assertThat(ids.next()).isTrue();
                                        writerId.set(ids.getLong(1));
                                        try (var statement =
                                                connection.prepareStatement(
                                                        "update formRourke2009 set"
                                                            + " formCreated='2000-01-01' where ID=?"
                                                            + " and demographic_no=?")) {
                                            statement.setQueryTimeout(10);
                                            statement.setInt(1, patient);
                                            statement.setInt(2, patient);
                                            started.countDown();
                                            return statement.executeUpdate();
                                        }
                                    }
                                });
                assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                long deadline =
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    var states =
                            sql.queryForList(
                                    "select STATE from information_schema.PROCESSLIST where ID=?",
                                    String.class,
                                    writerId.get());
                    waiting =
                            states.stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(
                                            value ->
                                                    value.toLowerCase(Locale.ROOT)
                                                            .contains("lock"));
                    if (waiting) break;
                    if (pending.isDone())
                        throw new AssertionError("Legacy writer bypassed the owned READ lock");
                    java.util.concurrent.locks.LockSupport.parkNanos(
                            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(20));
                }
                assertThat(waiting).isTrue();
                assertThat(pending.isDone()).isFalse();
                held.verify();
            }
            assertThat(pending.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(
                            sql.queryForObject(
                                    "select CAST(formCreated AS CHAR) from formRourke2009 where"
                                            + " ID=? and demographic_no=?",
                                    String.class,
                                    patient,
                                    patient))
                    .isEqualTo("2000-01-01");
        }
    }

    @Test
    void shouldRefuseAConnectionLostDuringTheLegacyReadLock_beforeItCanQualifySourceAbsence()
            throws Exception {
        var connection =
                java.sql.DriverManager.getConnection(
                        source.getUrl(), source.getUsername(), source.getPassword());
        var held =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                        .acquireUnpooled(connection);
        try {
            held.verify();
            connection.close();
            assertThatThrownBy(held::verify).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(
                            io.github.carlos_emr.carlos.demographic.cdsexport.support
                                            .CdsExportSourceReadLock
                                    ::requirePinned)
                    .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
            assertThatThrownBy(held::close).isInstanceOf(java.sql.SQLException.class);
        } finally {
            try {
                held.close();
            } catch (java.sql.SQLException ignored) {
            }
            connection.close();
        }
    }

    @Test
    void shouldReleaseTheLegacyTableAtItsIndependentDeadline_evenWithoutAnotherCaptureCheckpoint()
            throws Exception {
        sql.update(
                "insert into formRourke2009(ID,demographic_no,provider_no) values(?,?,'989879')",
                patient,
                patient);
        var connection =
                java.sql.DriverManager.getConnection(
                        source.getUrl(), source.getUsername(), source.getPassword());
        var held =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                        .acquireUnpooled(connection, 3000);
        try (var writer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var started = new java.util.concurrent.CountDownLatch(1);
            var pending =
                    writer.submit(
                            () -> {
                                started.countDown();
                                return sql.update(
                                        "update formRourke2009 set formCreated='2000-01-01' where"
                                                + " ID=? and demographic_no=?",
                                        patient,
                                        patient);
                            });
            assertThat(started.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(pending.get(6, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(connection.isClosed()).isTrue();
            assertThatThrownBy(held::verify).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(held::close).isInstanceOf(java.sql.SQLException.class);
        } finally {
            try {
                held.close();
            } catch (java.sql.SQLException ignored) {
            }
            connection.close();
        }
    }
}
