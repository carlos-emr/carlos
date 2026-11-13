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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportSourceCatalogueDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportListedSourceDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A chart with ticklers, a consent, a Rourke revision with checkbox values, an eForm, a
 * consultation, an e-mail, a patient photo and a removed-feature record, written as CARLOS writes
 * them, now passes the source gate; every data-summary table is captured from the pinned physical
 * contract into the authenticated capture, grouped into records, rendered to validated PDFs and
 * emitted as schema-valid Reports with every field assessed; the removed-feature rows are counted
 * for the ReadMe. A chart of an installation upgraded from an older OSCAR release (eChart,
 * immunization schedule, relationships, MDS and CML lab reports, requisition link, program
 * admission narrative) is exported the same way instead of being held.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportDataSummaryMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    /** Larger than any page's inline share, so both reads use verified byte ranges. */
    private static final String LONG_TEXT = "Ongoing concern \u00e9 \ud83d\ude00 ".repeat(4_000) + "END";

    private static final String EFORM_HTML =
            "<html><body><input name=\"weight\" value=\"70\">" + LONG_TEXT + "</body></html>";

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private CdsExportSourceCatalogueDao catalogue;
    private CdsExportDataSummaryReadDao summaries;
    private final int patient =
            580_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);

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
                CdsExportSourceCatalogueDao.class,
                CdsExportDataSummaryReadDao.class,
                io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao.class);
        context.refresh();
        catalogue = context.getBean(CdsExportSourceCatalogueDao.class);
        summaries = context.getBean(CdsExportDataSummaryReadDao.class);
        read =
                new TransactionTemplate(
                        context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (sql != null) {
                sql.update("delete from tickler_comments where id=?", patient);
                sql.update("delete from tickler_update where id=?", patient);
                sql.update("delete from tickler where tickler_no=?", patient);
                sql.update("delete from Consent where id=?", patient);
                sql.update("delete from form_boolean_value where form_id=?", patient);
                sql.update("delete from formRourke2020 where ID=?", patient);
                sql.update("delete from formRourke2009 where ID=? and demographic_no=?", patient, patient);
                sql.update("delete from eform_values where id=?", patient);
                sql.update("delete from eform_data where fdid=?", patient);
                sql.update("delete from consultationRequestExt where id=?", patient);
                sql.update("delete from consultdocs where id=?", patient);
                sql.update("delete from consultationRequests where requestId=?", patient);
                sql.update("delete from emailAttachment where id=?", patient);
                sql.update("delete from emailLog where id=?", patient);
                sql.update("delete from client_image where image_id=?", patient);
                sql.update("delete from CdsHospitalisationDays where id=?", patient);
                sql.update("delete from DrugProduct where name=?", "synthetic-" + patient);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    /** Rows shaped as the CARLOS screens and DAOs write them, all for one synthetic patient. */
    private void chart() {
        sql.update(
                "insert into tickler(tickler_no,demographic_no,program_id,message,status,update_date,"
                        + "service_date,creator,priority,task_assigned_to,creation_date)"
                        + " values(?,?,0,?,'A',now(),'2024-03-10 09:30:00','999998','Normal','999998',now())",
                patient,
                patient,
                "Call about results <b>unusual</b>\r\nsecond line 😀");
        sql.update(
                "insert into tickler_comments(id,tickler_no,message,provider_no,update_date)"
                        + " values(?,?,'Left a message','999998',now())",
                patient,
                patient);
        sql.update(
                "insert into tickler_update(id,tickler_no,status,provider_no,update_date)"
                        + " values(?,?,'C','999998',now())",
                patient,
                patient);
        sql.update(
                "insert into Consent(id,demographic_no,consent_type_id,explicit,optout,consent_date,"
                        + "last_entered_by,deleted) values(?,?,1,1,0,'2024-04-01 10:00:00','999998',0)",
                patient,
                patient);
        sql.update(
                "insert into formRourke2020(ID,demographic_no,provider_no,formCreated,formEdited)"
                        + " values(?,?,'999998','2024-01-01',now())",
                patient,
                patient);
        sql.update(
                "insert into form_boolean_value(form_name,form_id,field_name,value)"
                        + " values('formRourke2020',?,'p1_hearing',1),('formRourke2020',?,'p1_vision',null),"
                        + "('formRourke2017',?,'p1_hearing',1)",
                patient,
                patient,
                patient);
        sql.update(
                "insert into eform_data(fdid,fid,form_name,subject,demographic_no,status,form_date,"
                        + "form_time,form_provider,form_data,patient_independent,showLatestFormOnly,roleType)"
                        + " values(?,1,'Synthetic eForm','',?,1,'2024-05-06','13:00:00','999998',?,0,0,'')",
                patient,
                patient,
                EFORM_HTML);
        // The legacy 1,227-column form (Aria on ON) is read in column groups, and a value
        // larger than its group's inline share is read in verified byte ranges.
        sql.update(
                "insert into formRourke2009(ID,demographic_no,formCreated,formEdited,p4_problems48m)"
                        + " values(?,?,'2023-06-07',now(),?)",
                patient,
                patient,
                LONG_TEXT);
        sql.update(
                "insert into eform_values(id,fdid,fid,demographic_no,var_name,var_value)"
                        + " values(?,?,1,?,'weight','70')",
                patient,
                patient,
                patient);
        sql.update(
                "insert into consultationRequests(requestId,demographicNo,referalDate,reason,"
                        + "lastUpdateDate,status,urgency,providerNo,serviceId)"
                        + " values(?,?,'2024-07-08','Synthetic reason',now(),'1','2','999998',1)",
                patient,
                patient);
        sql.update(
                "insert into consultationRequestExt(id,requestId,name,value,dateCreated)"
                        + " values(?,?,'ebs','true','2024-07-08')",
                patient,
                patient);
        sql.update(
                "insert into consultdocs(id,requestId,document_no,doctype,provider_no,attach_date)"
                        + " values(?,?,1,'D','999998','2024-07-08')",
                patient,
                patient);
        sql.update(
                "insert into emailLog(id,demographicNo,fromEmail,toEmail,subject,body,status,"
                        + "timestamp,providerNo) values(?,?,'clinic@example.invalid',"
                        + "'patient@example.invalid','Synthetic subject',?,'SUCCESS','2024-11-03 01:30:00','999998')",
                patient,
                patient,
                Base64.getEncoder().encode("Your results are ready.".getBytes(StandardCharsets.UTF_8)));
        sql.update(
                "insert into emailAttachment(id,logId,fileName,filePath,documentType,documentId)"
                        + " values(?,?,'letter.pdf','/synthetic/letter.pdf','DOC',1)",
                patient,
                patient);
        sql.update(
                "insert into client_image(image_id,demographic_no,image_type,contents,update_date)"
                        + " values(?,?,'png',?,now())",
                patient,
                patient,
                Base64.getEncoder().encode(PNG));
        sql.update(
                "insert into CdsHospitalisationDays(id,clientId,admitted) values(?,?,'2024-02-29')",
                patient,
                patient);
        sql.update(
                "insert into DrugProduct(name,code,amount,lastUpdateDate,lastUpdateUser)"
                        + " values(?,'synthetic',1,now(),'999998')",
                "synthetic-" + patient);
    }

    private void stableLegacy(Runnable work) {
        try (var held =
                io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceReadLock
                        .acquireUnpooled(
                                java.sql.DriverManager.getConnection(
                                        source.getUrl(), source.getUsername(), source.getPassword()))) {
            work.run();
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException("OWNED_SOURCE_SNAPSHOT_LOCK_FAILED", failed);
        }
    }

    private record Captured(
            Map<String, List<Row>> rows,
            List<CdsExportListedSourceDto> listed,
            List<CdsExportListedSourceDto> clinic) {}

    /** The same steps as the native capture, on the actual database, read back from the sealed bytes. */
    private Captured capture() {
        var result = new Captured[1];
        stableLegacy(
                () ->
                        result[0] =
                                read.execute(
                                        status -> {
                                            try {
                                                catalogue.readSourceSchema();
                                                // The chart is no longer refused at the source gate.
                                                catalogue.requireUnclassifiedSourcesEmpty(patient, () -> {});
                                                return captureInside(patient);
                                            } catch (java.io.IOException failed) {
                                                throw new java.io.UncheckedIOException(failed);
                                            }
                                        }));
        return result[0];
    }

    private Captured captureInside(int chart) throws java.io.IOException {
        var readers = new TreeMap<String, CdsExportNativeSourceReader>();
        for (var reader : CdsExportDataSummarySourceReader.readers(summaries))
            readers.put(reader.table(), reader);
        assertThat(readers.keySet()).containsExactlyInAnyOrderElementsOf(CdsExportDataSummaryRegistry.tables());
        var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        var validator = new CdsExportSourceSchemaValidator();
        for (var reader : readers.values()) {
            String digest = validator.verify(reader.table(), reader.schema(), reader.columns());
            assertThat(validator.acceptedSha256(reader.table(), reader.columns())).contains(digest);
            long rows = reader.count(chart);
            reader.requireCapacity(rows, 1_000_000);
            plan.put(reader.table(), new CdsExportNativeCaptureReader.Table(reader.columns(), rows));
        }
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        readers.keySet(),
                        1_000_000,
                        1L << 30,
                        () -> {},
                        writer -> {
                            for (var reader : readers.values()) {
                                writer.beginTable(reader.table(), reader.columns(), plan.get(reader.table()).rows());
                                reader.capture(chart, writer, () -> {});
                                writer.endTable();
                            }
                        });
        var collector = new CdsExportDataSummaryRowsReader.Collector(readers.keySet());
        new CdsExportNativeCaptureReader()
                .scan(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        plan,
                        1_000_000,
                        1L << 30,
                        () -> {},
                        collector);
        collector.finish();
        var listed = summaries.listed(chart, () -> {});
        var clinic = summaries.listedClinicSources();
        return new Captured(collector.rows(), listed, clinic);
    }

    @Test
    void shouldExportAChartWithFormsTicklersMessagesAndPhoto_asDataSummaryReports() throws Exception {
        chart();
        var captured = capture();
        var groups = CdsExportDataSummaryPlanner.plan(captured.rows(), ZoneId.of("America/Toronto"));
        var byKind = new LinkedHashMap<Kind, List<Group>>();
        for (var group : groups) byKind.computeIfAbsent(group.kind(), k -> new ArrayList<>()).add(group);
        assertThat(byKind.keySet())
                .containsExactlyInAnyOrder(
                        Kind.ENCOUNTER_FORM,
                        Kind.EFORM,
                        Kind.CONSULTATION_REQUEST,
                        Kind.TICKLER,
                        Kind.EMAIL,
                        Kind.PATIENT_PHOTO,
                        Kind.CONSENT);
        assertThat(groups).noneMatch(Group::detached);
        var tickler = byKind.get(Kind.TICKLER).getFirst();
        assertThat(tickler.rows()).extracting(Row::table).containsExactly("tickler", "tickler_comments", "tickler_update");
        assertThat(tickler.rows().getFirst().text("message"))
                .isEqualTo("Call about results <b>unusual</b>\r\nsecond line 😀");
        var rourke =
                byKind.get(Kind.ENCOUNTER_FORM).stream()
                        .filter(group -> group.table().equals("formRourke2020"))
                        .findFirst()
                        .orElseThrow();
        var legacy =
                byKind.get(Kind.ENCOUNTER_FORM).stream()
                        .filter(group -> group.table().equals("formRourke2009"))
                        .findFirst()
                        .orElseThrow();
        assertThat(legacy.subClass()).isEqualTo("formRourke2009 revision 1 of 1");
        assertThat(legacy.rows().getFirst().values()).hasSize(1227);
        assertThat(legacy.rows().getFirst().text("p4_problems48m")).isEqualTo(LONG_TEXT);
        assertThat(byKind.get(Kind.EFORM).getFirst().rows().getFirst().text("form_data"))
                .isEqualTo(EFORM_HTML);
        assertThat(rourke.subClass()).isEqualTo("formRourke2020 revision 1 of 1");
        assertThat(rourke.rows()).extracting(Row::table)
                .containsExactly("formRourke2020", "form_boolean_value", "form_boolean_value");
        // The checkbox naming the 2017 form with the same id belongs to no revision of this chart
        // (exact form_name bytes), so it is not captured for this patient at all.
        assertThat(captured.rows().get("form_boolean_value"))
                .extracting(row -> row.text("form_name"))
                .containsOnly("formRourke2020");
        // SQL NULL stays distinct from a stored value in the captured checkbox.
        assertThat(rourke.rows().get(2).values().get("value").storage())
                .isEqualTo(CdsExportDataSummaryPlanner.Storage.SQL_NULL);
        assertThat(byKind.get(Kind.EFORM).getFirst().rows()).extracting(Row::table)
                .containsExactly("eform_data", "eform_values");
        assertThat(byKind.get(Kind.CONSULTATION_REQUEST).getFirst().rows()).extracting(Row::table)
                .containsExactly("consultationRequests", "consultationRequestExt", "consultdocs");
        assertThat(byKind.get(Kind.EMAIL).getFirst().rows()).extracting(Row::table)
                .containsExactly("emailLog", "emailAttachment");
        var photo = byKind.get(Kind.PATIENT_PHOTO).getFirst();
        assertThat(photo.hasImage()).isTrue();
        assertThat(photo.rows().getFirst().values().get("contents").content())
                .isEqualTo(Base64.getEncoder().encode(PNG));

        assertThat(emit(groups)).isEqualTo((long) groups.size());

        // The current consultation request and Rourke 2020 revision also print as CARLOS prints
        // them, from the captured rows and this installation's reference tables.
        var references =
                context.getBean(io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao.class);
        var demographic =
                Map.of(
                        "demographic_no", Integer.toString(patient),
                        "last_name", "FIXTURE",
                        "first_name", "SYNTHETIC",
                        "year_of_birth", "2024",
                        "month_of_birth", "01",
                        "date_of_birth", "15");
        var plan =
                new CdsExportDataSummaryOccurrenceService.Plan(
                        groups, CdsExportDataSummaryHtmlComposer.Patient.of(demographic), demographic);
        var consultation = byKind.get(Kind.CONSULTATION_REQUEST).getFirst();
        var letterComposer = new CdsExportConsultationLetterComposer(references);
        assertThat(letterComposer.prints(consultation)).isTrue();
        var letter = letterComposer.print(consultation, plan);
        CdsExportDataSummaryPdfService.requirePdf(letter.pdf());
        assertThat(letter.event().lexical()).isEqualTo("2024-07-08");
        var chartComposer = new CdsExportRourkeChartComposer(references);
        assertThat(chartComposer.prints(rourke)).isTrue();
        var chart = chartComposer.print(rourke, plan);
        CdsExportDataSummaryPdfService.requirePdf(chart.pdf());
        assertThat(chart.event().lexical()).isEqualTo("2024-01-01");
        assertThat(chart.subClass()).isEqualTo("Rourke Baby Record 2020");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(chart.pdf())) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("FIXTURE, SYNTHETIC");
        }

        // The removed-feature record is listed for this chart and never refused; the unlinked one
        // is listed for the whole job.
        assertThat(captured.listed()).contains(new CdsExportListedSourceDto("CdsHospitalisationDays", 1));
        assertThat(captured.clinic()).anySatisfy(listed -> assertThat(listed.table()).isEqualTo("DrugProduct"));
    }

    /**
     * Renders every record to a validated PDF and composes it as a Reports entry whose every
     * captured field is assessed; returns the Reports count of the schema-valid patient file.
     */
    private long emit(List<Group> groups) throws Exception {
        var pdfs = new CdsExportDataSummaryPdfService(HtmlPdfTestServices.newService());
        var header =
                CdsExportDataSummaryHtmlComposer.Patient.of(
                        Map.of("demographic_no", Integer.toString(patient), "last_name", "Fixture", "first_name", "Synthetic"));
        String key = "a".repeat(64);
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        var patientRow = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patientRow.put(column, null));
        patientRow.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patientRow));
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(key, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        writer -> new CdsExportDemographicXmlWriter().write(demographics, writer)));
        for (var group : groups) {
            byte[] pdf = pdfs.render(group, header);
            CdsExportDataSummaryPdfService.requirePdf(pdf);
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(
                            group.rows().stream().map(Row::assessed).toList(),
                            CdsExportDataSummaryOccurrenceService.assessments(group),
                            1_000_000,
                            () -> {});
            sections.add(
                    new CdsExportXmlComposer.Section(
                            CdsExportDataSummaryOccurrenceService.identity(key, group),
                            writer ->
                                    CdsExportDataSummaryOccurrenceService.write(
                                            group, "Reports/" + group.table() + "-" + group.id() + ".pdf", writer)));
        }
        var xml = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS);
        new CdsExportXmlComposer()
                .compose(
                        xml,
                        "d".repeat(64),
                        key,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        16L << 20,
                        () -> {});
        var checked =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(xml.toByteArray()),
                                "27",
                                selected,
                                "2026.08.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
        return checked.occurrences().get(CdsCategory.REPORTS);
    }

    /**
     * An Ontario chart of an installation upgraded from an older OSCAR release: two saved eChart
     * revisions, the encounter immunization schedule with an archived revision, a patient
     * relationship and its inverse, an MDS lab report with keyless segments (two identical
     * results), a CML report with its upload header, test lines and requisition link, and a
     * program admission with a discharge narrative beside one without.
     */
    private void legacyChart() {
        int other = patient + 1;
        sql.update(
                "insert into eChart(demographicNo,providerNo,subject,socialHistory,familyHistory,"
                        + "medicalHistory,ongoingConcerns,reminders,encounter,timeStamp)"
                        + " values(?,'999998','',?,?,?,?,?,?,'2004-03-02 10:00:00')",
                patient,
                "Smoker 😀",
                "Mother: diabetes",
                "Appendectomy 1990",
                "Hypertension",
                "Flu shot in fall",
                "[02-Mar-2004 .: annual] BP 150/95");
        sql.update(
                "insert into eChart(demographicNo,providerNo,subject,encounter,timeStamp)"
                        + " values(?,'999998','SPLIT CHART',?,'2005-07-08 11:00:00')",
                patient,
                LONG_TEXT);
        sql.update(
                "insert into immunizations(demographic_no,provider_no,immunizations,save_date,archived)"
                        + " values(?,'999998','<immunizationSet/>','2003-01-01',1),"
                        + "(?,'999998','<immunizationSet><row given=\"2003-02-03\"/></immunizationSet>','2003-02-03',0)",
                patient,
                patient);
        sql.update(
                "insert into relationships(facility_id,demographic_no,relation_demographic_no,relation,"
                        + "sub_decision_maker,emergency_contact,notes,creator,creation_date,deleted)"
                        + " values(0,?,?,'Mother','1','1','Call after 5','999998','2006-01-02 03:04:05','0'),"
                        + "(0,?,?,'Son','0','0','inverse row','999998','2006-01-02 03:04:05','0')",
                patient,
                other,
                other,
                patient);
        // MDS: the route names the chart; the header's demographic_no copy names another one.
        sql.update(
                "insert into mdsMSH(segmentID,sendingApp,dateTime,type,demographic_no)"
                        + " values(?,'MDS','2002-04-05 06:07:08','ORU^R01',?)",
                patient,
                other);
        sql.update("insert into mdsPID(segmentID,patientName,healthNumber) values(?,'SYNTHETIC^LEGACY','0000000000')", patient);
        sql.update("insert into mdsOBR(segmentID,obrID,universalServiceID) values(?,1,'CBC')", patient);
        sql.update(
                "insert into mdsOBX(segmentID,obxID,observationIden,observationValue,associatedOBR)"
                        + " values(?,1,'HGB','140',1),(?,1,'HGB','140',1),(?,2,'WBC',NULL,1)",
                patient,
                patient,
                patient);
        sql.update("insert into mdsNTE(segmentID,sourceOfComment,comment,associatedOBX) values(?,'L','Fasting',1)", patient);
        sql.update("insert into mdsZRG(segmentID,reportGroupDesc) values(?,'HEMATOLOGY')", patient);
        // CML: one upload header, one report routed to the chart, two test lines.
        sql.update(
                "insert into labReportInformation(id,location_id,print_date,print_time) values(?,'CML','20020405','0607')",
                patient);
        sql.update(
                "insert into labPatientPhysicianInfo(id,labReportInfo_id,accession_num,service_date,"
                        + "patient_last_name,patient_first_name,lastUpdateDate)"
                        + " values(?,?,'A1','20020405','LEGACY','SYNTHETIC',now())",
                patient,
                patient);
        sql.update(
                "insert into labTestResults(id,labPatientPhysicianInfo_id,line_type,test_name,result,units)"
                        + " values(?,?,'C','Glucose','5.1','mmol/L'),(?,?,'D',NULL,NULL,NULL)",
                patient,
                patient,
                patient + 1,
                patient);
        sql.update(
                "insert into patientLabRouting(lab_no,lab_type,demographic_no,created)"
                        + " values(?,'MDS',?,now()),(?,'CML',?,now())",
                patient,
                patient,
                patient,
                patient);
        // The requisition link of the CML report, and one whose table differs only by case.
        sql.update(
                "insert into labRequestReportLink(id,request_table,request_id,request_date,report_table,report_id)"
                        + " values(?,'formLabReq10',1,'2002-04-01 00:00:00','labPatientPhysicianInfo',?),"
                        + "(?,'formLabReq10',1,'2002-04-01 00:00:00','labpatientphysicianinfo',?)",
                patient,
                patient,
                patient + 1,
                patient);
        // A discharge narrative is clinical; a bare membership row is not a record.
        sql.update(
                "insert into admission(am_id,client_id,program_id,provider_no,admission_date,"
                        + "admission_from_transfer,discharge_date,discharge_from_transfer,discharge_notes,"
                        + "admission_status,radioDischargeReason,lastUpdateDate)"
                        + " values(?,?,0,'999998','2010-01-01 09:00:00',0,'2010-02-01 09:00:00',0,"
                        + "'Discharged home, follow up in clinic','discharged',NULL,now()),"
                        + "(?,?,0,'999998','2011-01-01 09:00:00',0,NULL,0,NULL,'current',NULL,now())",
                patient,
                patient,
                patient + 1,
                patient);
    }

    private void cleanupLegacy() {
        sql.update("delete from eChart where demographicNo=?", patient);
        sql.update("delete from immunizations where demographic_no=?", patient);
        sql.update("delete from relationships where demographic_no in (?,?)", patient, patient + 1);
        for (String table :
                List.of("mdsMSH", "mdsPID", "mdsOBR", "mdsOBX", "mdsNTE", "mdsZRG"))
            sql.update("delete from " + table + " where segmentID=?", patient);
        sql.update("delete from labTestResults where labPatientPhysicianInfo_id=?", patient);
        sql.update("delete from labPatientPhysicianInfo where id=?", patient);
        sql.update("delete from labReportInformation where id=?", patient);
        sql.update("delete from labRequestReportLink where id in (?,?)", patient, patient + 1);
        sql.update("delete from patientLabRouting where lab_no=? and demographic_no=?", patient, patient);
        sql.update("delete from admission where am_id in (?,?)", patient, patient + 1);
    }

    @Test
    void shouldExportAnUpgradedInstallationsLegacyRecords_asDataSummaryReportsInsteadOfHolding()
            throws Exception {
        try {
            legacyChart();
            var captured = capture();
            var groups = CdsExportDataSummaryPlanner.plan(captured.rows(), ZoneId.of("America/Toronto"));
            var byKind = new LinkedHashMap<Kind, List<Group>>();
            for (var group : groups) byKind.computeIfAbsent(group.kind(), k -> new ArrayList<>()).add(group);
            assertThat(byKind.keySet())
                    .containsExactly(
                            Kind.PROGRAM_ADMISSION,
                            Kind.LEGACY_ENCOUNTER,
                            Kind.IMMUNIZATION_SCHEDULE,
                            Kind.PATIENT_RELATIONSHIP,
                            Kind.MDS_LAB_REPORT,
                            Kind.CML_LAB_REPORT,
                            Kind.LAB_REQUISITION_LINK);
            assertThat(groups).noneMatch(Group::detached);
            // Only the admission with a discharge narrative is a record.
            var admission = byKind.get(Kind.PROGRAM_ADMISSION);
            assertThat(admission).singleElement().extracting(Group::id).isEqualTo(Integer.toString(patient));
            assertThat(admission.getFirst().rows().getFirst().text("discharge_notes"))
                    .isEqualTo("Discharged home, follow up in clinic");
            var encounters = byKind.get(Kind.LEGACY_ENCOUNTER);
            assertThat(encounters).extracting(Group::subClass)
                    .containsExactly("eChart revision 1 of 2", "eChart revision 2 of 2");
            assertThat(encounters.getFirst().rows().getFirst().text("socialHistory")).isEqualTo("Smoker 😀");
            assertThat(encounters.get(1).rows().getFirst().text("encounter")).isEqualTo(LONG_TEXT);
            assertThat(byKind.get(Kind.IMMUNIZATION_SCHEDULE)).extracting(Group::subClass)
                    .containsExactly("immunizations revision 1 of 2", "immunizations revision 2 of 2");
            // The chart's own relationship row only; the related patient's inverse row is theirs.
            var relationship = byKind.get(Kind.PATIENT_RELATIONSHIP);
            assertThat(relationship).singleElement();
            assertThat(relationship.getFirst().rows().getFirst().text("relation")).isEqualTo("Mother");
            var mds = byKind.get(Kind.MDS_LAB_REPORT);
            assertThat(mds).singleElement();
            assertThat(mds.getFirst().rows()).extracting(Row::table)
                    .containsExactly("mdsMSH", "mdsPID", "mdsOBR", "mdsOBX", "mdsOBX", "mdsOBX", "mdsNTE", "mdsZRG");
            // The two identical keyless results stay two rows; SQL NULL stays NULL.
            assertThat(captured.rows().get("mdsOBX")).extracting(row -> row.text("observationValue"))
                    .containsExactly("140", "140", null);
            assertThat(mds.getFirst().event())
                    .isEqualTo(new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto.Precision.DATE_TIME,
                            "2002-04-05T06:07:08-05:00"));
            var cml = byKind.get(Kind.CML_LAB_REPORT);
            assertThat(cml).singleElement();
            assertThat(cml.getFirst().rows()).extracting(Row::table)
                    .containsExactly("labPatientPhysicianInfo", "labTestResults", "labTestResults", "labReportInformation");
            assertThat(cml.getFirst().event())
                    .isEqualTo(new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto(
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto.Precision.DATE,
                            "2002-04-05"));
            // A report table spelled only by case names no chart.
            assertThat(byKind.get(Kind.LAB_REQUISITION_LINK)).singleElement().extracting(Group::id)
                    .isEqualTo(Integer.toString(patient));
            assertThat(emit(groups)).isEqualTo((long) groups.size());
            // The other chart gets its inverse relationship only: the MDS header's legacy copy of
            // its number does not move the report there.
            int other = patient + 1;
            var others = new Captured[1];
            stableLegacy(
                    () ->
                            others[0] =
                                    read.execute(
                                            status -> {
                                                try {
                                                    return captureInside(other);
                                                } catch (java.io.IOException failed) {
                                                    throw new java.io.UncheckedIOException(failed);
                                                }
                                            }));
            assertThat(CdsExportDataSummaryPlanner.plan(others[0].rows(), ZoneId.of("America/Toronto")))
                    .extracting(Group::kind)
                    .containsExactly(Kind.PATIENT_RELATIONSHIP);
            // Keyless segments are paged by position within the snapshot: one row per page reads
            // all three results, then nothing.
            var plan = CdsExportDataSummaryReadDao.plan("mdsOBX");
            List<List<Object>> keys =
                    read.execute(
                            status -> {
                                var result = new ArrayList<List<Object>>();
                                List<Object> after = null;
                                while (true) {
                                    var page = summaries.page(plan, patient, after, 1);
                                    if (page.isEmpty()) return result;
                                    after = page.getLast().key();
                                    result.add(after);
                                }
                            });
            assertThat(keys).containsExactly(List.of(1L), List.of(2L), List.of(3L));
        } finally {
            cleanupLegacy();
        }
    }
}
