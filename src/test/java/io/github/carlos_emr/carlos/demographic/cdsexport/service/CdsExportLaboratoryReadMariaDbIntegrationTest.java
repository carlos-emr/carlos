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

/** Physical source/receipt and corrupt-ownership checks against an explicitly disposable migrated database. */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportLaboratoryReadMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private CdsLaboratoryResultDao results;
    private final int patient =
            510_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final String run = UUID.randomUUID().toString(), marker = "OwnedLabSource" + patient;
    private final String body = "Complete 😀 laboratory résultat\r\n".repeat(10000);
    private boolean owned;

    private String key(int ordinal) {
        return CdsImportKeys.hash(run, Integer.toString(ordinal));
    }

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
            throw new IllegalArgumentException("Owned disposable laboratory database required");
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
        context.register(
                CdsLaboratoryResultDao.class,
                Hl7TextMessageDaoImpl.class,
                Hl7TextInfoDaoImpl.class,
                PatientLabRoutingDaoImpl.class,
                CdsExportReviewerReadDao.class);
        context.refresh();
        results = context.getBean(CdsLaboratoryResultDao.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        var write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.executeWithoutResult(
                status -> {
                    for (int i = 0; i < 2; i++) {
                        sql.update(
                                "insert into hl7TextMessage(lab_id,created,fileUploadCheck_id,message,serviceName,type) values(?,CURRENT_TIMESTAMP,0,?,?,'CARLOS-CDS')",
                                patient + i,
                                body,
                                marker);
                        sql.update(
                                "insert into hl7TextInfo(id,lab_no,report_status,label) values(?,?,'F',?)",
                                patient + i,
                                patient + i,
                                marker);
                        sql.update(
                                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'HL7')",
                                patient + i,
                                patient + i,
                                patient + i);
                    }
                    for (int i = 0; i < 37; i++)
                        sql.update(
                                """
                    insert into cds_laboratory_result(id,demographicNo,runId,sourcePath,laboratoryName,abnormalFlag,collectionPrecision,requisitionPrecision,importedBy,sourceBlocked,nativeLabNo,informationFromLab,notesFromLab)
                    values(?,?,?,?,?,'N','UNKNOWN','UNKNOWN','989879',?,?,?,'')
                    """,
                                key(i),
                                i == 36 ? patient + 1 : patient,
                                run,
                                marker,
                                marker,
                                i < 35 ? "Y" : "N",
                                i < 35 ? null : i == 35 ? patient : patient + 1,
                                body);
                });
        owned = true;
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                sql.update(
                        "delete from providerLabRouting where id between ? and ? and lab_no in"
                                + " (?,?)",
                        patient + 10,
                        patient + 19,
                        patient,
                        patient + 1);
                sql.update(
                        "delete from cds_laboratory_result where runId=? and sourcePath=?",
                        run,
                        marker);
                sql.update(
                        "delete from patientLabRouting where id in (?,?,?) and lab_no in (?,?)",
                        patient,
                        patient + 1,
                        patient + 2,
                        patient,
                        patient + 1);
                sql.update(
                        "delete from hl7TextInfo where id in (?,?,?) and label=?",
                        patient,
                        patient + 1,
                        patient + 2,
                        marker);
                sql.update(
                        "delete from hl7TextMessage where lab_id in (?,?) and serviceName=?",
                        patient,
                        patient + 1,
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    private void review() {
        assertThatThrownBy(() -> read.execute(status -> results.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldPageEveryOwnedDigestKey_withoutCoercionOrAnotherPatient() {
        read.executeWithoutResult(
                status -> {
                    assertThat(results.countCdsExportRows(patient)).isEqualTo(36);
                    var first = results.findCdsExportRowsAfter(patient, null, 32);
                    assertThat(first).hasSize(32);
                    var last = results.findCdsExportRowsAfter(patient, first.getLast().id(), 32);
                    assertThat(last).hasSize(4);
                    var ids = new ArrayList<String>();
                    first.forEach(row -> ids.add(row.id()));
                    last.forEach(row -> ids.add(row.id()));
                    assertThat(ids).isSorted().doesNotHaveDuplicates().doesNotContain(key(36));
                    assertThat(results.findCdsExportRowsAfter(patient, last.getLast().id(), 32))
                            .isEmpty();
                    assertThat(first)
                            .allSatisfy(
                                    row ->
                                            assertThat(row.cells().keySet())
                                                    .containsExactlyInAnyOrderElementsOf(
                                                            CdsExportPatientTableReadSupport.Plan
                                                                    .LABORATORY_RESULTS
                                                                    .columns()));
                });
    }

    @Test
    void shouldHashAndStreamTheCompleteBlockedNarrative_andDistinguishNullFromEmpty() {
        read.executeWithoutResult(
                status -> {
                    var page = results.findCdsExportRowsAfter(patient, null, 32);
                    var row = page.getFirst();
                    var cell = row.cells().get("informationFromLab");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    assertThat(cell.inlineBytes()).isNull();
                    assertThat(cell.bytes()).isEqualTo(bytes.length);
                    assertThat(row.cells().get("notesFromLab").sqlNull()).isFalse();
                    assertThat(row.cells().get("notesFromLab").bytes()).isZero();
                    assertThat(row.cells().get("physiciansNotes").sqlNull()).isTrue();
                    var digest = CdsImportKeys.digest();
                    for (int offset = 0; offset < bytes.length; offset += 65536)
                        digest.update(
                                results.readCdsExportValueChunk(
                                        patient,
                                        row.id(),
                                        "informationFromLab",
                                        offset,
                                        Math.min(65536, bytes.length - offset)));
                    assertThat(HexFormat.of().formatHex(digest.digest())).isEqualTo(cell.sha256());
                });
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                results.readCdsExportValueChunk(
                                                        patient,
                                                        key(36),
                                                        "informationFromLab",
                                                        0,
                                                        10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldMatchAllFourPinnedPhysicalSchemas_andReadActualOrderedReceiptColumns() {
        read.executeWithoutResult(
                status -> {
                    var readers =
                            List.of(
                                    new CdsExportLaboratorySourceReader(results),
                                    new CdsExportLaboratoryMessageSourceReader(
                                            context.getBean(Hl7TextMessageDao.class)),
                                    new CdsExportLaboratoryInfoSourceReader(
                                            context.getBean(Hl7TextInfoDao.class)),
                                    new CdsExportLaboratoryRoutingSourceReader(
                                            context.getBean(PatientLabRoutingDao.class)));
                    for (CdsExportNativeSourceReader reader : readers) {
                        assertThatCode(
                                        () ->
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(reader.schema(), reader.columns()))
                                .doesNotThrowAnyException();
                        assertThat(reader.receiptColumns())
                                .extracting(column -> column.name())
                                .containsExactlyInAnyOrderElementsOf(reader.columns());
                        assertThat(reader.count(patient))
                                .isEqualTo(reader.table().equals("cds_laboratory_result") ? 36 : 1);
                    }
                });
        assertThatThrownBy(() -> results.readCdsExportReceiptColumns())
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MDS", "CML", "BCP"})
    void shouldReadTheStructuredResults_besideALegacyFormatReportExportedWithReports(String type) {
        // A legacy-format report is a data summary of Reports; the HL7 projection never reads it
        // and no longer refuses the chart for it. The request projection without Reports asks.
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,?)",
                patient + 2,
                patient,
                patient + 1,
                type);
        Long count = read.execute(status -> results.countCdsExportRows(patient));
        assertThat(count).isEqualTo(36L);
        Long routes =
                read.execute(
                        status ->
                                context.getBean(PatientLabRoutingDao.class)
                                        .countCdsExportRows(patient));
        assertThat(routes).isEqualTo(1L);
        Boolean legacy = read.execute(status -> results.hasCdsExportLegacyReports(patient));
        assertThat(legacy).isTrue();
        Boolean other = read.execute(status -> results.hasCdsExportLegacyReports(patient + 1));
        assertThat(other).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"doc", "hl7", "mds", "XYZ"})
    void shouldRefuseUnmappedOrNoncanonicalRouting_beforeCountingPagingOrStreamingResults(
            String type) {
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,?)",
                patient + 2,
                patient,
                patient + 1,
                type);
        assertThatThrownBy(() -> read.execute(status -> results.countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                results.findCdsExportRowsAfter(patient, null, 32)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                results.readCdsExportValueChunk(
                                                        patient,
                                                        key(0),
                                                        "informationFromLab",
                                                        0,
                                                        1)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThatThrownBy(
                        () ->
                                read.execute(
                                        status ->
                                                context.getBean(PatientLabRoutingDao.class)
                                                        .countCdsExportRows(patient)))
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldReadEveryInboxRouteOfThePatientsHl7Labs_andNoOtherRoute() {
        String insert =
                "insert into"
                    + " providerLabRouting(id,lab_no,lab_type,provider_no,status,comment,timestamp)"
                    + " values(?,?,?,?,?,?,'2026-07-10 16:45:00')";
        sql.update(insert, patient + 10, patient, "HL7", "999998", "A", "Repeat in 3 months");
        sql.update(insert, patient + 11, patient, "HL7", "0", "N", "");
        // A document with the same number, another patient's lab, and a type spelled otherwise.
        sql.update(insert, patient + 12, patient, "DOC", "999998", "N", "");
        sql.update(insert, patient + 13, patient + 1, "HL7", "999998", "A", "");
        sql.update(insert, patient + 14, patient, "hl7", "999998", "A", "");
        var reviewers = context.getBean(CdsExportReviewerReadDao.class);
        var plan = CdsExportPatientTableReadSupport.Plan.LABORATORY_REVIEWERS;
        Long count = read.execute(status -> reviewers.count(plan, patient));
        assertThat(count).isEqualTo(2L);
        var page = read.execute(status -> reviewers.page(plan, patient, null, 32));
        assertThat(page).extracting(row -> row.id())
                .containsExactly((long) patient + 10, (long) patient + 11);
        var first = page.getFirst().cells();
        assertThat(first.keySet()).containsExactlyInAnyOrderElementsOf(plan.columns());
        assertThat(new String(first.get("comment").inlineBytes(), StandardCharsets.UTF_8))
                .isEqualTo("Repeat in 3 months");
        // The TIMESTAMP is captured as its stored instant, whatever the session time zone.
        assertThat(new String(first.get("timestamp").inlineBytes(), StandardCharsets.UTF_8))
                .isEqualTo(
                        sql.queryForObject(
                                "select CAST(UNIX_TIMESTAMP(`timestamp`) AS CHAR) from"
                                        + " providerLabRouting where id=?",
                                String.class,
                                patient + 10));
        String table = read.execute(status -> reviewers.schema(plan).table());
        assertThat(table).isEqualTo("providerLabRouting");
        // The public provider lookup names every provider a routed lab of the patient names.
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        List<String> providers =
                read.execute(
                        status ->
                                CdsExportProviderReadSupport.page(
                                                EntityManagerFactoryUtils
                                                        .getTransactionalEntityManager(factory),
                                                patient,
                                                Set.of("LaboratoryResults"),
                                                null,
                                                32)
                                        .stream()
                                        .map(row -> row.id())
                                        .toList());
        assertThat(providers).containsExactly("999998");
        // Nothing is read outside the caller's read-only patient snapshot.
        assertThatThrownBy(() -> reviewers.count(plan, patient))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReadTheLaboratoryResults_whenTheInboxHasRoutedADocumentToThePatient() {
        // Every document assigned to a patient writes a DOC route; it is not a lab (review LAB-09).
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'DOC')",
                patient + 2,
                patient,
                patient + 1);
        Long resultRows = read.execute(status -> results.countCdsExportRows(patient));
        Long routes =
                read.execute(
                        status ->
                                context.getBean(PatientLabRoutingDao.class)
                                        .countCdsExportRows(patient));
        assertThat(resultRows).isEqualTo(36L);
        assertThat(routes).isEqualTo(1L);
    }

    @Test
    void shouldReportAnEmptyStructuredInventory_forLegacyOnlyLaboratoryDataTheReportsCarry() {
        sql.update("delete from cds_laboratory_result where runId=? and sourcePath=?", run, marker);
        sql.update(
                "delete from patientLabRouting where id=? and demographic_no=?", patient, patient);
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'MDS')",
                patient + 2,
                patient,
                patient);
        // The MDS report is exported as a data summary with Reports; an export without Reports
        // holds the chart in the request projection (CdsExportLaboratorySourceReader).
        Long count = read.execute(status -> results.countCdsExportRows(patient));
        assertThat(count).isZero();
        Boolean legacy = read.execute(status -> results.hasCdsExportLegacyReports(patient));
        assertThat(legacy).isTrue();
    }

    @Test
    void shouldKeepAnotherPatientsLegacyRoutes_outsideTheSelectedPatientScope() {
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'MDS')",
                patient + 2,
                patient + 1,
                patient + 1);
        Long count = read.execute(status -> results.countCdsExportRows(patient));
        assertThat(count).isEqualTo(36L);
    }

    @Test
    void shouldRejectCrossPatientHl7Routing_beforeReadingAnyResultBody() {
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'HL7')",
                patient + 2,
                patient + 1,
                patient);
        review();
    }

    @Test
    void shouldRejectDuplicatePatientHl7Routing_insteadOfDeduplicatingIt() {
        sql.update(
                "insert into patientLabRouting(id,created,demographic_no,lab_no,lab_type) values(?,CURRENT_TIMESTAMP,?,?,'HL7')",
                patient + 2,
                patient,
                patient);
        review();
    }

    @Test
    void shouldRejectADanglingMessage_withoutShrinkingTheCapturedInventory() {
        sql.update("delete from hl7TextMessage where lab_id=? and serviceName=?", patient, marker);
        review();
    }

    @Test
    void shouldRejectMissingLabMetadata_withoutAnInnerJoinOmission() {
        sql.update("delete from hl7TextInfo where id=? and label=?", patient, marker);
        review();
    }

    @Test
    void shouldRejectDuplicateLabMetadata_insteadOfSelectingAnArbitraryCopy() {
        sql.update(
                "insert into hl7TextInfo(id,lab_no,report_status,label) values(?,?,'F',?)",
                patient + 2,
                patient,
                marker);
        review();
    }

    @Test
    void shouldRejectMissingPatientRouting_forAStructuredResult() {
        sql.update(
                "delete from patientLabRouting where id=? and demographic_no=?", patient, patient);
        review();
    }

    @Test
    void shouldRejectABlockedResult_withAnUnrestrictedMirror() {
        sql.update(
                "update cds_laboratory_result set sourceBlocked='Y' where id=? and runId=?",
                key(35),
                run);
        review();
    }

    @Test
    void shouldRejectTwoStructuredResultsSharingOneNativeLab_forLaboratoryReadContract() {
        sql.update(
                "update cds_laboratory_result set sourceBlocked='N',nativeLabNo=? where id=? and runId=?",
                patient,
                key(0),
                run);
        review();
    }

    @Test
    void shouldRejectADeletedNativeReference_forAnUnblockedResult() {
        sql.update(
                "update cds_laboratory_result set nativeLabNo=null where id=? and runId=?",
                key(35),
                run);
        review();
    }
}
