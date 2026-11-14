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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
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

/**
 * A chart of notes stored in MariaDB (encounter notes, CPP items under the chart's own tags,
 * revised items, dated extensions, attached records, diagnoses linked by encounter notes and a
 * diagnosis nothing links), captured by the real note, extension, link, issue and catalogue
 * readers in one REPEATABLE READ snapshot, read back through the real capture reader and witness
 * reader and exported by the history occurrence service: the seven categories are the same
 * schema-valid bytes and the same receipts read whole and read in windows of a few notes.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportHistoryWindowMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private static final String MANIFEST = "b".repeat(64), KEY = "c".repeat(64);
    /** The chart's CPP tags in the installed catalogue, and two user-defined issues. */
    private static final List<String> TAGS =
            List.of("FamHistory", "MedHistory", "Concerns", "RiskFactors", "Reminders", "SocHistory");

    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private final int patient =
            562_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final int note =
            1_800_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000);
    private final String marker = "HistoryWindow" + patient;
    private boolean owned;
    private int notes;

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
            throw new IllegalArgumentException("Owned disposable history database required");
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
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    CaseManagementNoteLink.class.getName(),
                                    GroupNoteLink.class.getName()));
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
                CaseManagementNoteDAOImpl.class,
                CaseManagementNoteExtDAOImpl.class,
                CaseManagementNoteLinkDAOImpl.class,
                CdsExportNoteAccessDao.class,
                CaseManagementIssueDAOImpl.class,
                IssueDAOImpl.class,
                io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl.class,
                DemographicMergedDaoImpl.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(300);
        assertThat(
                        sql.queryForObject(
                                "select count(*) from demographic where demographic_no=?",
                                Integer.class,
                                patient))
                .isZero();
        for (String check :
                List.of(
                        "select count(*) from casemgmt_note where note_id between ? and ?",
                        "select count(*) from casemgmt_issue where id between ? and ?",
                        "select count(*) from casemgmt_note_ext where id between ? and ?",
                        "select count(*) from casemgmt_note_link where id between ? and ?"))
            assertThat(sql.queryForObject(check, Integer.class, note, note + 999))
                    .as("unused id block")
                    .isZero();
        owned = true;
        chart();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                // The rows that name a note first: one left behind would hold every chart.
                sql.update(
                        "delete from casemgmt_issue_notes where note_id between ? and ?",
                        note,
                        note + 999);
                sql.update(
                        "delete from casemgmt_note_link where id between ? and ? and note_id"
                                + " between ? and ?",
                        note,
                        note + 999,
                        note,
                        note + 999);
                sql.update(
                        "delete from casemgmt_note_ext where id between ? and ? and note_id"
                                + " between ? and ?",
                        note,
                        note + 999,
                        note,
                        note + 999);
                sql.update(
                        "delete from casemgmt_issue where id between ? and ? and demographic_no=?",
                        note,
                        note + 999,
                        patient);
                sql.update(
                        "delete from casemgmt_note where note_id between ? and ? and"
                                + " demographic_no=?",
                        note,
                        note + 999,
                        patient);
                sql.update(
                        "delete from demographic where demographic_no=? and last_name=?",
                        patient,
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    /**
     * Thirty-two notes: encounter notes (some attached to a document, some linking a diagnosis
     * of the chart's issue list), items of every CPP section filed under the chart's own tag
     * issues, CPP items with a dated extension, three items revised once (one chain each), and a
     * diagnosis of the chart's issue list that no note links.
     */
    private void chart() {
        sql.update(
                "insert into demographic(demographic_no,first_name,last_name,patient_status,"
                        + "lastUpdateDate) values(?,'Synthetic',?,'AC',CURRENT_TIMESTAMP)",
                patient,
                marker);
        var catalogue = new HashMap<String, Integer>();
        for (String code : TAGS)
            catalogue.put(
                    code,
                    sql.queryForObject(
                            "select issue_id from issue where code=? and type='system'",
                            Integer.class,
                            code));
        // The patient's issue list: one row a CPP tag, and two user-defined issues (Health,
        // Physical Health) the chart lists as diagnoses.
        String issue =
                "insert into casemgmt_issue(id,demographic_no,issue_id,acute,certain,major,"
                        + "resolved,program_id,type,update_date) values(?,?,?,0,0,0,?,NULL,'nurse',"
                        + "'2026-11-11 09:00:00')";
        for (int tag = 0; tag < TAGS.size(); tag++)
            sql.update(issue, note + 900 + tag, patient, catalogue.get(TAGS.get(tag)), 0);
        int health = sql.queryForObject("select issue_id from issue where code='CTCMM2000'", Integer.class);
        int physical =
                sql.queryForObject("select issue_id from issue where code='CTCMM14000'", Integer.class);
        sql.update(issue, note + 950, patient, health, 0);
        sql.update(issue, note + 951, patient, physical, 1);
        String insert =
                "insert into casemgmt_note(note_id,update_date,observation_date,demographic_no,"
                        + "provider_no,note,signed,signing_provider_no,program_no,"
                        + "reporter_caisi_role,reporter_program_team,history,locked,position,uuid)"
                        + " values(?,?,?,?,'999998',?,1,'999998','','1','0',?,'0',0,?)";
        int extension = note + 500, attached = note + 700;
        for (int i = 0; i < 32; i++) {
            int id = note + i;
            // Every eighth note revises the one before it: one chain of two revisions.
            boolean revision = i % 8 == 7;
            int kind = (revision ? i - 1 : i) % 7;
            String uuid = uuid(revision ? id - 1 : id);
            String text =
                    "Synthetic history item " + (revision ? i - 1 : i) + (revision ? " (revised)" : "");
            sql.update(
                    insert,
                    id,
                    "2026-11-11 09:" + String.format(Locale.ROOT, "%02d", i) + ":00",
                    "2026-11-10 08:00:00",
                    patient,
                    text,
                    text,
                    uuid);
            if (kind > 0)
                sql.update(
                        "insert into casemgmt_issue_notes(id,note_id) values(?,?)",
                        note + 900 + kind - 1,
                        id);
            // Encounter notes link the Health diagnosis.
            if (kind == 0 && i % 2 == 0)
                sql.update("insert into casemgmt_issue_notes(id,note_id) values(?,?)", note + 950, id);
            // Past-health items carry the start date of the condition.
            if (kind == 2)
                sql.update(
                        "insert into casemgmt_note_ext(id,note_id,key_val,value,date_value)"
                                + " values(?,?,'Start Date','YYYY','2010-01-01')",
                        extension++,
                        id);
            // Some encounter notes file a document.
            if (kind == 0 && i % 3 == 0)
                sql.update(
                        "insert into casemgmt_note_link(id,table_name,table_id,note_id,other_id)"
                                + " values(?,5,?,?,NULL)",
                        attached++,
                        880_000 + i,
                        id);
            notes++;
        }
    }

    private static String uuid(long number) {
        return String.format(Locale.ROOT, "%08x-bbbb-4bbb-8bbb-bbbbbbbbbbbb", number);
    }

    @Test
    void shouldEmitTheSameHistory_whenAStoredChartIsReadInSeveralWindows() throws Exception {
        var categories = EnumSet.of(CdsCategory.DEMOGRAPHICS);
        categories.addAll(CdsExportNoteSourceReader.CATEGORIES);
        var command =
                new CdsExportCommand(
                        "history-window-capture",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(patient),
                        categories,
                        "receiver",
                        "audit");
        var control = mock(CdsExportRunControl.class);
        when(control.issueProgramReadable(anyInt(), any(), any())).thenReturn(true);
        var access = context.getBean(CdsExportNoteAccessDao.class);
        var readers = new ArrayList<CdsExportNativeSourceReader>();
        // The note scope is prepared in its own read, as the capture service prepares it.
        readers.add(
                read.execute(
                        status -> {
                            try {
                                return new CdsExportNoteSourceReader(
                                                context.getBean(CaseManagementNoteDAO.class),
                                                access,
                                                new CdsExportNoteScopeProjectionService(access))
                                        .forCapture(patient, control);
                            } catch (IOException failed) {
                                throw new UncheckedIOException(failed);
                            }
                        }));
        readers.add(
                new CdsExportNoteExtensionSourceReader(
                        context.getBean(CaseManagementNoteExtDAO.class)));
        readers.add(new CdsExportNoteLinkSourceReader(context.getBean(CaseManagementNoteLinkDAO.class)));
        readers.add(
                new CdsExportNoteIssueLinkSourceReader(
                        context.getBean(CaseManagementNoteDAO.class)));
        readers.add(
                new CdsExportCaseIssueSourceReader(context.getBean(CaseManagementIssueDAO.class)));
        readers.add(new CdsExportIssueCatalogueSourceReader(context.getBean(IssueDAO.class)));
        readers.add(
                new CdsExportDemographicMergeSourceReader(
                        context.getBean(DemographicMergedDao.class)));
        readers.add(
                new CdsExportPublicProviderSourceReader(
                                context.getBean(
                                        io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class))
                        .forRequest(command));
        readers.add(new CdsExportClinicalZoneSourceReader("America/Toronto").forRequest(command));
        readers.sort(Comparator.comparing(CdsExportNativeSourceReader::table));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        var expected = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        read.executeWithoutResult(
                status -> {
                    try {
                        var tables = new TreeSet<String>();
                        for (var reader : readers) tables.add(reader.table());
                        new CdsExportNativeCaptureComposer()
                                .compose(
                                        values,
                                        index,
                                        tables,
                                        1_000_000,
                                        64L << 20,
                                        () -> {},
                                        writer -> {
                                            for (var reader : readers) {
                                                long count = reader.count(patient);
                                                writer.beginTable(
                                                        reader.table(), reader.columns(), count);
                                                reader.capture(patient, writer);
                                                writer.endTable();
                                                scopes.put(
                                                        reader.table(),
                                                        new CdsExportCapturedTableDto(
                                                                reader.columns(),
                                                                count,
                                                                "a".repeat(64),
                                                                reader.receiptColumns(),
                                                                reader.noteAccess()));
                                                expected.put(
                                                        reader.table(),
                                                        new CdsExportNativeCaptureReader.Table(
                                                                reader.columns(), count));
                                            }
                                        });
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        // Every row of the chart, read from one snapshot, and no other chart's.
        assertThat(scopes.get("casemgmt_note").rows()).isEqualTo(notes);
        assertThat(scopes.get("casemgmt_issue").rows()).isEqualTo(TAGS.size() + 2);
        assertThat(scopes.get("issue").rows()).isEqualTo(TAGS.size() + 2);
        assertThat(scopes.get("casemgmt_note_ext").rows()).isPositive();
        assertThat(scopes.get("casemgmt_note_link").rows()).isPositive();
        var capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(scopes);
        when(capture.values())
                .thenReturn(
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", MANIFEST, 1));
        when(capture.index())
                .thenReturn(
                        new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", MANIFEST, 1));
        long[] scans = {0};
        var captures = mock(CdsExportNativeCaptureService.class);
        byte[] capturedValues = values.toByteArray(), capturedIndex = index.toByteArray();
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            scans[0]++;
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(capturedValues),
                                            new ByteArrayInputStream(capturedIndex),
                                            expected,
                                            1_000_000,
                                            64L << 20,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        });
        var captured =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto(
                                "3".repeat(64), "SOURCE_MANIFEST", MANIFEST, 1));
        var lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        MANIFEST,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                MANIFEST,
                                "sender",
                                MANIFEST,
                                MANIFEST,
                                new CdsExportStorageBindingDto("primary", "primary", MANIFEST)));
        var cohort = mock(CdsExportCohortService.Patient.class);
        when(cohort.patient()).thenReturn(patient);
        var retained =
                new CdsExportRetainedIndexDto(
                        MANIFEST,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        var retainedValues = mock(CdsExportRetainedValuesService.class);
        doAnswer(
                        call -> {
                            call.<CdsExportRetainedValuesService.Work>getArgument(8)
                                    .use(
                                            retained,
                                            List.of(),
                                            mock(CdsExportRetainedSectionWriter.Values.class));
                            return null;
                        })
                .when(retainedValues)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any(), any());
        var verification = mock(CdsExportRetainedReceiptVerificationService.class);
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                MANIFEST, Map.of(), Set.of(), Set.of()));
        var witnesses = new CdsExportNativeRowReceiptReader(captures);
        var service =
                new CdsExportHistoryOccurrenceService(
                        captures,
                        witnesses,
                        new CdsExportNoteRevisionInventoryService(captures, witnesses),
                        retainedValues,
                        verification);
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        1_000_000, 64L << 20, 1000, 8L << 20, 8192, 131_072);

        // Read whole: the chart fits one window.
        var receipts = new ArrayList<CdsExportClinicalOccurrenceService.Receipt>();
        byte[] whole = compose(service, lease, cohort, scopes, captured, bounds, control, categories, receipts);
        long wholeScans = scans[0];
        var expectedReceipts = List.copyOf(receipts);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(whole),
                        Integer.toString(patient),
                        categories,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        // Twenty-eight chains (four revised once), the user-defined diagnosis no note links and
        // the one linked only by encounter notes, each its own ProblemList entry.
        assertThat(expectedReceipts).hasSize(notes - 4 + 2);
        assertThat(expectedReceipts)
                .extracting(receipt -> receipt.identity().category())
                .contains(
                        CdsCategory.CLINICAL_NOTES,
                        CdsCategory.FAMILY_HISTORY,
                        CdsCategory.PAST_HEALTH,
                        CdsCategory.PROBLEM_LIST,
                        CdsCategory.RISK_FACTORS,
                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS,
                        CdsCategory.PERSONAL_HISTORY);

        // Read in windows of two, then three occurrences: every window's rows are checked
        // against the witnesses of the capture MariaDB's snapshot wrote, and the output is the
        // same.
        for (int window : new int[] {2, 3}) {
            receipts.clear();
            scans[0] = 0;
            assertThat(
                            compose(
                                    service,
                                    lease,
                                    cohort,
                                    scopes,
                                    captured,
                                    bounds.withWindow(window, 64L << 20),
                                    control,
                                    categories,
                                    receipts))
                    .as("window of " + window)
                    .isEqualTo(whole);
            assertThat(receipts).as("window of " + window).isEqualTo(expectedReceipts);
            assertThat(scans[0]).as("window of " + window).isGreaterThan(wholeScans);
        }
    }

    private byte[] compose(
            CdsExportHistoryOccurrenceService service,
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient cohort,
            Map<String, CdsExportCapturedTableDto> scopes,
            CdsExportPatientCaptureDto captured,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Set<CdsCategory> categories,
            List<CdsExportClinicalOccurrenceService.Receipt> receipts)
            throws IOException {
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        MANIFEST,
                        KEY,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        categories,
                        emitter -> {
                            emitter.emit(demographic(KEY, Integer.toString(patient)));
                            service.withSource(
                                    lease,
                                    cohort,
                                    KEY,
                                    scopes.keySet(),
                                    captured,
                                    new CdsExportStagedFileDto(
                                            "4".repeat(64), "PAYLOAD_MANIFEST", MANIFEST, 1),
                                    bounds,
                                    control,
                                    emitter,
                                    session -> {
                                        for (var category : CdsCategory.values())
                                            if (CdsExportNoteSourceReader.CATEGORIES.contains(
                                                    category)) session.append(category, receipts::add);
                                    });
                        },
                        ignored -> {},
                        8L << 20,
                        () -> {});
        return output.toByteArray();
    }

    private static CdsExportXmlComposer.Section demographic(String key, String vendorId) {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(key, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                xml -> {
                    xml.start("cds", "Names");
                    xml.start("cds_dt", "LegalName");
                    xml.attribute("", "namePurpose", "L");
                    for (var part :
                            List.of(Map.entry("FirstName", "GIV"), Map.entry("LastName", "FAMC"))) {
                        xml.start("cds_dt", part.getKey());
                        xml.element("cds_dt", "Part", "Fixture");
                        xml.element("cds_dt", "PartType", part.getValue());
                        xml.end();
                    }
                    xml.end();
                    xml.end();
                    xml.element("cds", "DateOfBirth", "1980-01-02");
                    xml.element("cds", "Gender", "U");
                    xml.element("cds", "UniqueVendorIdSequence", vendorId);
                    xml.start("cds", "PersonStatusCode");
                    xml.element("cds", "PersonStatusAsEnum", "A");
                    xml.end();
                });
    }
}
