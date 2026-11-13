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

import io.github.carlos_emr.carlos.commn.dao.CdsExportHospitalReportReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dao.CdsExportProviderReadSupport;
import io.github.carlos_emr.carlos.commn.dao.CdsExportSourceCatalogueDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The HRM report capture plans on actual MariaDB, over rows written the way CARLOS writes them:
 * the report and its patient link as the HRM inbox files them
 * ({@code HRMReportParser.routeReportToDemographic}), its accompanying subclasses
 * ({@code routeReportToSubClass}), its provider routes and sign-offs
 * ({@code HRMModifyDocument2Action.signOff}) and its comments ({@code addComment}). Each plan reads
 * exactly the selected patient's rows; another chart's, an unmatched report's and a dangling
 * link's report stay out, and a linked chart is no longer held for its reports.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportHospitalReportReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private CdsExportHospitalReportReadDao reports;
    private CdsExportSourceCatalogueDao catalogue;
    private final int patient =
            580_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final int other = patient + 1;
    private final String marker = "SyntheticHrm" + patient;
    private Integer chart;

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
        context.register(CdsExportSourceCatalogueDao.class, CdsExportHospitalReportReadDao.class);
        context.refresh();
        reports = context.getBean(CdsExportHospitalReportReadDao.class);
        catalogue = context.getBean(CdsExportSourceCatalogueDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
    }

    @AfterEach
    void cleanup() {
        try {
            sql.update(
                    "delete from HRMDocumentComment where id between ? and ? and providerNo='999998'",
                    patient,
                    patient + 99);
            sql.update(
                    "delete from HRMDocumentSubClass where id between ? and ? and"
                            + " sendingFacilityId='4205'",
                    patient,
                    patient + 99);
            sql.update(
                    "delete from HRMDocumentToProvider where id between ? and ?",
                    patient,
                    patient + 99);
            sql.update(
                    "delete from HRMDocumentToDemographic where id between ? and ?",
                    patient,
                    patient + 99);
            sql.update(
                    "delete from HRMDocument where id between ? and ? and description=?",
                    patient,
                    patient + 99,
                    marker);
            if (chart != null)
                sql.update(
                        "delete from demographic where demographic_no=? and last_name=?",
                        chart,
                        "SYNTHETIC HRM");
        } finally {
            if (context != null) context.close();
            if (source != null)
                try {
                    source.close();
                } catch (java.sql.SQLException ignored) {
                    // The pool holds no clinical state.
                }
        }
    }

    /** A received report as HRMReportParser stores it: the OSCAR-era HRMDocument columns. */
    private void report(int id, String reportFile) {
        sql.update(
                "insert into"
                    + " HRMDocument(id,timeReceived,reportType,reportHash,reportLessTransactionInfoHash,"
                    + "reportStatus,reportFile,unmatchedProviders,numDuplicatesReceived,reportDate,"
                    + "reportLessDemographicInfoHash,sourceFacility,description,formattedName,dob,"
                    + "gender,hcn,recipientId,recipientName,className,subClassName,"
                    + "sourceFacilityReportNo) values(?,'2026-03-05 10:14:00','Diagnostic Imaging"
                    + " Report',?,?,'S',?,'|FAKE-Raven, Melanie (D104412)',1,'2026-03-05"
                    + " 09:30:00',?,'4205',?,'FAKE-FOREMAN, GEORGE','1985-04-05','M','0000000000',"
                    + "'D104412','FAKE-Raven, Melanie','Diagnostic Imaging"
                    + " Report',NULL,'CGF-0001')",
                id,
                "a".repeat(64),
                "b".repeat(64),
                reportFile,
                "c".repeat(64),
                marker);
    }

    /** HRMReportParser.routeReportToDemographic: the patient and report numbers as text. */
    private void link(int id, int owner, int report) {
        sql.update(
                "insert into HRMDocumentToDemographic(id,demographicNo,hrmDocumentId,timeAssigned)"
                        + " values(?,?,?,'2026-03-05 10:15:00')",
                id,
                Integer.toString(owner),
                Integer.toString(report));
    }

    private void comment(int id, int report, int deleted) {
        sql.update(
                "insert into HRMDocumentComment(id,providerNo,hrmDocumentId,comment,commentTime,deleted)"
                        + " values(?,'999998',?,'Follow up in two weeks 😀','2026-03-06 09:00:00',?)",
                id,
                report,
                deleted);
    }

    private void subClass(int id, int report) {
        sql.update(
                "insert into"
                    + " HRMDocumentSubClass(id,hrmDocumentId,subClass,subClassMnemonic,subClassDescription,"
                    + "subClassDateTime,isActive,sendingFacilityId) values(?,?,'CR','ANK3LX','Ankle"
                    + " - 3 views','2026-03-05',1,'4205')",
                id,
                report);
    }

    /** A route: unsigned as the inbox creates it, or signed off by HRMModifyDocument2Action. */
    private void route(int id, int report, String provider, boolean signed) {
        sql.update(
                "insert into"
                    + " HRMDocumentToProvider(id,providerNo,hrmDocumentId,signedOff,signedOffTimestamp,viewed)"
                    + " values(?,?,?,?,?,1)",
                id,
                provider,
                Integer.toString(report),
                signed ? 1 : 0,
                signed ? "2026-03-06 11:30:00" : null);
    }

    private List<Long> ids(Plan plan, int owner) {
        return read.execute(
                status ->
                        reports.page(plan, owner, null, 32).stream()
                                .map(CdsExportRawRowDto::id)
                                .toList());
    }

    @Test
    void shouldReadEveryRowOfThePatientsHrmReports_andNoOtherChartsRows() {
        int own = patient, shared = patient + 1, foreign = patient + 2, unmatched = patient + 3;
        for (int id : List.of(own, shared, foreign, unmatched))
            report(id, "LabUpload." + id + ".xml.1690313901104");
        link(patient, patient, own);
        link(patient + 1, patient, shared);
        link(patient + 2, other, shared);
        link(patient + 3, other, foreign);
        // A link naming a report that no longer exists is captured, never hidden by a join.
        link(patient + 4, patient, patient + 50);
        comment(patient, own, 0);
        comment(patient + 1, own, 1);
        comment(patient + 2, foreign, 0);
        subClass(patient, own);
        subClass(patient + 1, unmatched);
        route(patient, own, "999998", true);
        route(patient + 1, own, "-1", false);
        route(patient + 2, foreign, "999998", true);
        route(patient + 3, unmatched, "999998", false);

        assertThat(ids(Plan.HRM_REPORT_LINKS, patient))
                .containsExactly((long) patient, patient + 1L, patient + 4L);
        assertThat(ids(Plan.HRM_REPORTS, patient)).containsExactly((long) own, (long) shared);
        assertThat(ids(Plan.HRM_REPORT_FILES, patient)).containsExactly((long) own, (long) shared);
        assertThat(ids(Plan.HRM_REPORT_COMMENTS, patient))
                .containsExactly((long) patient, patient + 1L);
        assertThat(ids(Plan.HRM_REPORT_SUBCLASSES, patient)).containsExactly((long) patient);
        assertThat(ids(Plan.HRM_REPORT_ROUTES, patient))
                .containsExactly((long) patient, patient + 1L);
        // A report linked to two charts belongs to both.
        assertThat(ids(Plan.HRM_REPORTS, other)).containsExactly((long) shared, (long) foreign);
        Long count = read.execute(status -> reports.count(Plan.HRM_REPORT_ROUTES, patient));
        assertThat(count).isEqualTo(2L);

        var report =
                read.execute(status -> reports.page(Plan.HRM_REPORTS, patient, null, 32))
                        .getFirst();
        assertThat(report.cells().keySet())
                .containsExactlyInAnyOrderElementsOf(Plan.HRM_REPORTS.columns());
        assertThat(new String(report.cells().get("reportFile").inlineBytes(), StandardCharsets.UTF_8))
                .isEqualTo("LabUpload." + own + ".xml.1690313901104");
        var comment =
                read.execute(status -> reports.page(Plan.HRM_REPORT_COMMENTS, patient, null, 32))
                        .getFirst();
        assertThat(new String(comment.cells().get("comment").inlineBytes(), StandardCharsets.UTF_8))
                .isEqualTo("Follow up in two weeks 😀");

        // The public provider lookup names the signers and the comment authors.
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        List<String> providers =
                read.execute(
                        status ->
                                CdsExportProviderReadSupport.page(
                                                EntityManagerFactoryUtils
                                                        .getTransactionalEntityManager(factory),
                                                patient,
                                                Set.of("Reports"),
                                                null,
                                                32)
                                        .stream()
                                        .map(row -> row.id())
                                        .toList());
        assertThat(providers).contains("999998");
        // Nothing is read outside the caller's read-only patient snapshot.
        assertThatThrownBy(() -> reports.count(Plan.HRM_REPORTS, patient))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldVerifyEveryHrmSourceContract_onTheInstalledSchema() {
        var readers =
                List.of(
                        new CdsExportHospitalReportScopeSourceReader(reports),
                        new CdsExportHospitalReportSourceReader(reports),
                        new CdsExportHospitalReportCommentSourceReader(reports),
                        new CdsExportHospitalReportSubClassSourceReader(reports),
                        new CdsExportHospitalReportRouteSourceReader(reports));
        read.executeWithoutResult(
                status -> {
                    for (var reader : readers) {
                        assertThatCode(
                                        () ->
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(reader.schema(), reader.columns()))
                                .as(reader.table())
                                .doesNotThrowAnyException();
                        assertThat(reader.count(patient)).as(reader.table()).isZero();
                        assertThat(reader.requiredTables())
                                .isEqualTo(CdsExportHospitalReportSources.REQUIRED);
                    }
                });
        var files = new CdsExportHospitalReportFileSourceReader(reports, "", "1024");
        assertThatCode(
                        () ->
                                new CdsExportSourceSchemaValidator()
                                        .verify(files.schema(), files.columns()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldCaptureLegacyPatientSpellings_forTheMapperToHold() {
        report(patient, "LabUpload.legacy.xml");
        // Positional inserts still work beside the invisible derived key, and every numeric
        // alias the key derives is captured, so the report mapper sees it and holds the chart
        // instead of the alias being missed.
        sql.update(
                "INSERT INTO HRMDocumentToDemographic VALUES(?,?,?,CURRENT_TIMESTAMP)",
                patient,
                "0" + patient,
                Integer.toString(patient));
        for (String alias : List.of("0" + patient, "+" + patient, " " + patient, patient + "x")) {
            sql.update(
                    "update HRMDocumentToDemographic set demographicNo=? where id=?",
                    alias,
                    patient);
            assertThat(ids(Plan.HRM_REPORT_LINKS, patient)).as(alias).containsExactly((long) patient);
            var cell =
                    read.execute(status -> reports.page(Plan.HRM_REPORT_LINKS, patient, null, 32))
                            .getFirst()
                            .cells()
                            .get("demographicNo");
            assertThat(new String(cell.inlineBytes(), StandardCharsets.UTF_8)).isEqualTo(alias);
        }
    }

    @Test
    void shouldUseTheNumericPatientIndex_forTheLinkPlan() {
        var plan =
                sql.queryForMap(
                        "EXPLAIN SELECT 1,1 FROM HRMDocumentToDemographic d WHERE"
                                + " d.cds_patient_key=? LIMIT 1",
                        patient);
        assertThat(plan.get("key")).isEqualTo("idx_cds_hrm_patient_key");
    }

    @Test
    void shouldKeepTheSnapshot_whenALinkArrivesDuringTheCapture() {
        report(patient, "LabUpload.snapshot.xml");
        read.executeWithoutResult(
                status -> {
                    assertThat(reports.count(Plan.HRM_REPORTS, patient)).isZero();
                    try (var writer = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                        writer.submit(() -> link(patient, patient, patient))
                                .get(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                    assertThat(reports.count(Plan.HRM_REPORTS, patient)).isZero();
                });
        assertThat(ids(Plan.HRM_REPORTS, patient)).containsExactly((long) patient);
    }

    @Test
    void shouldNoLongerHoldAChartForItsHrmReports_butHoldEveryChartForAReportLinkedNowhere() {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        sql.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(
                                    "INSERT INTO"
                                        + " demographic(last_name,first_name,year_of_birth,month_of_birth,"
                                        + "date_of_birth,sex,patient_status,provider_no,lastUpdateDate)"
                                        + " VALUES('SYNTHETIC HRM','Fixture','2000','01','02','F','AC',"
                                        + "'999998',NOW())",
                                    java.sql.Statement.RETURN_GENERATED_KEYS);
                    return statement;
                },
                key);
        chart = key.getKey().intValue();
        report(patient, "LabUpload.linked.xml");
        link(patient, chart, patient);
        comment(patient, patient, 0);
        subClass(patient, patient);
        route(patient, patient, "999998", true);
        stableLegacy(
                () ->
                        read.executeWithoutResult(
                                status -> catalogue.requireUnclassifiedSourcesEmpty(chart, () -> {})));
        // A second link of the same report naming no existing chart takes nothing from any
        // chart: the report is still this chart's and is exported with it.
        int nowhere = 2_000_000_000 - patient % 1000;
        link(patient + 1, nowhere, patient);
        stableLegacy(
                () ->
                        read.executeWithoutResult(
                                status -> catalogue.requireUnclassifiedSourcesEmpty(chart, () -> {})));
        // A report whose only link names a chart that does not exist could be anyone's.
        report(patient + 1, "LabUpload.broken.xml");
        link(patient + 2, nowhere, patient + 1);
        stableLegacy(
                () ->
                        assertThatThrownBy(
                                        () ->
                                                read.executeWithoutResult(
                                                        status ->
                                                                catalogue
                                                                        .requireUnclassifiedSourcesEmpty(
                                                                                chart, () -> {})))
                                .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED"));
    }

    private void stableLegacy(Runnable work) {
        try (var held =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                        .acquireUnpooled(
                                java.sql.DriverManager.getConnection(
                                        source.getUrl(),
                                        source.getUsername(),
                                        source.getPassword()))) {
            work.run();
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException("OWNED_SOURCE_SNAPSHOT_LOCK_FAILED", failed);
        }
    }
}
