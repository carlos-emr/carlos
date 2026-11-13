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
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
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
 * Note links and group-note links written the way CARLOS writes them (the link and group-note
 * DAOs' own save paths, beside note rows shaped as the document, discontinuation, e-mail and
 * legacy import writers leave them), read through the real read plans of the pinned physical
 * schema, no longer holding the chart at the source gate, and exported through the real history
 * occurrence service as schema-valid ClinicalNotes that name the record each note is attached to.
 */
@Tag("integration")
@Tag("cds-export")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cds.export.test.properties",
        matches = ".+")
class CdsExportNoteLinkMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private static final String MANIFEST = "b".repeat(64), KEY = "c".repeat(64);
    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read;
    private TransactionTemplate write;
    private CaseManagementNoteLinkDAO links;
    private final int patient =
            561_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9_000_000);
    private final int other = patient + 1;
    private final int note =
            1_700_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000);
    private final String marker = "NoteLink" + patient;
    private boolean owned;

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
            throw new IllegalArgumentException("Owned disposable note-link database required");
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
                DemographicMergedDaoImpl.class,
                GroupNoteDaoImpl.class,
                CdsExportSourceCatalogueDao.class);
        context.refresh();
        links = context.getBean(CaseManagementNoteLinkDAO.class);
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        for (int id : List.of(patient, other))
            assertThat(
                            sql.queryForObject(
                                    "select count(*) from demographic where demographic_no=?",
                                    Integer.class,
                                    id))
                    .isZero();
        assertThat(
                        sql.queryForObject(
                                "select count(*) from casemgmt_note where note_id between ? and ?",
                                Integer.class,
                                note,
                                note + 6))
                .isZero();
        owned = true;
        chart();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            if (owned) {
                // Links first: a link left behind without its note would hold every chart.
                sql.update("delete from GroupNoteLink where noteId between ? and ?", note, note + 6);
                sql.update(
                        "delete from casemgmt_note_link where note_id between ? and ?",
                        note,
                        note + 6);
                sql.update(
                        "delete from casemgmt_note where note_id between ? and ? and"
                                + " demographic_no in (?,?)",
                        note,
                        note + 6,
                        patient,
                        other);
                sql.update(
                        "delete from demographic where demographic_no in (?,?) and last_name=?",
                        patient,
                        other,
                        marker);
            }
        } finally {
            if (context != null) context.close();
            if (source != null) source.close();
        }
    }

    /**
     * One chart with every kind of attached note CARLOS writes today, a legacy import note
     * attached to another note, and another chart's tickler note shared with it as a CAISI group
     * note. Note rows carry the columns their writers set; links and the group-note share are
     * saved through the writers' own DAOs.
     */
    private void chart() {
        for (int id : List.of(patient, other))
            sql.update(
                    "insert into demographic(demographic_no,first_name,last_name,patient_status,"
                            + "lastUpdateDate) values(?,'Synthetic',?,'AC',CURRENT_TIMESTAMP)",
                    id,
                    marker);
        String insert =
                "insert into casemgmt_note(note_id,update_date,observation_date,demographic_no,"
                        + "provider_no,note,signed,signing_provider_no,program_no,"
                        + "reporter_caisi_role,reporter_program_team,history,locked,position,uuid)"
                        + " values(?,?,?,?,?,?,1,?,'','1','0',?,'0',0,?)";
        String documentNote =
                "Document Referral letter created at Wed Nov 11 09:00:00 EST 2026 by Synthetic"
                        + " Provider.";
        String revision = UUID.randomUUID().toString();
        // ManageDocument2Action.saveDocNote: the system provider files the document's note.
        note(insert, note, patient, "-1", documentNote, UUID.randomUUID().toString());
        // RxDeleteRx2Action: the discontinuation reason, then an edit of it (same UUID).
        note(insert, note + 1, patient, "999998", "Discontinued: rash", revision);
        note(insert, note + 2, patient, "999998", "Discontinued: rash and hives", revision);
        // EmailManager: the note recording a sent e-mail.
        note(
                insert,
                note + 3,
                patient,
                "999998",
                "[Email Log ID 660001: The following message was emailed]\n\nSubject: Results",
                UUID.randomUUID().toString());
        // An encounter note and the legacy CDS import's data note attached to it.
        note(insert, note + 4, patient, "999998", "Seen for cough", UUID.randomUUID().toString());
        note(
                insert,
                note + 5,
                patient,
                "999998",
                "imported.cms4.2011.06\nReaction Type=AL",
                UUID.randomUUID().toString());
        // DbTicklerAdd2Action in another chart; that note is shared with this one below.
        note(insert, note + 6, other, "999998", "Tickler: call back", UUID.randomUUID().toString());
        write.executeWithoutResult(
                status -> {
                    links.save(link(CaseManagementNoteLink.DOCUMENT, 880001, note));
                    links.save(link(CaseManagementNoteLink.DRUGS, 770001, note + 1));
                    // CaseManagementManagerImpl.copyRevisionMetadata copies it to the revision.
                    links.save(link(CaseManagementNoteLink.DRUGS, 770001, note + 2));
                    links.save(link(CaseManagementNoteLink.EMAIL, 660001, note + 3));
                    links.save(link(CaseManagementNoteLink.CASEMGMTNOTE, note + 4, note + 5));
                    links.save(link(CaseManagementNoteLink.TICKLER, 550001, note + 6));
                    var shared = new GroupNoteLink();
                    shared.setNoteId(note + 6);
                    shared.setDemographicNo(patient);
                    shared.setActive(true);
                    context.getBean(GroupNoteDao.class).persist(shared);
                });
    }

    private void note(
            String insert, int id, int owner, String provider, String text, String uuid) {
        sql.update(
                insert,
                id,
                "2026-11-11 09:00:00",
                "2026-11-11 09:00:00",
                owner,
                provider,
                text,
                provider,
                text,
                uuid);
    }

    private static CaseManagementNoteLink link(Integer type, long record, long note) {
        return new CaseManagementNoteLink(type, record, note);
    }

    @Test
    void shouldReadTheLinksOfThisChartsNotes_throughThePinnedPhysicalSchema() {
        read.executeWithoutResult(
                status -> {
                    try {
                        assertThat(links.countCdsExportRows(patient)).isEqualTo(5);
                        assertThat(links.countCdsExportRows(other)).isEqualTo(1);
                        var rows = links.findCdsExportRowsAfter(patient, null, 32);
                        assertThat(rows).hasSize(5);
                        assertThat(rows)
                                .extracting(row -> text(row, "note_id"))
                                .containsExactly(
                                        Integer.toString(note),
                                        Integer.toString(note + 1),
                                        Integer.toString(note + 2),
                                        Integer.toString(note + 3),
                                        Integer.toString(note + 5));
                        assertThat(rows.getFirst().cells().get("other_id").sqlNull()).isTrue();
                        assertThat(text(rows.getFirst(), "table_name")).isEqualTo("5");
                        var reader = new CdsExportNoteLinkSourceReader(links);
                        assertThatCode(
                                        () ->
                                                new CdsExportSourceSchemaValidator()
                                                        .verify(
                                                                reader.table(),
                                                                reader.schema(),
                                                                reader.columns()))
                                .doesNotThrowAnyException();
                        assertThat(reader.receiptColumns())
                                .extracting(column -> column.name())
                                .containsExactlyInAnyOrderElementsOf(reader.columns());
                    } catch (Exception failed) {
                        throw new IllegalStateException(failed);
                    }
                });
    }

    private static String text(CdsExportRawRowDto row, String column) {
        return new String(
                row.cells().get(column).inlineBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void shouldNoLongerHoldEitherChart_forNoteLinksOrASharedGroupNote() {
        var catalogue = context.getBean(CdsExportSourceCatalogueDao.class);
        stableLegacy(
                () ->
                        read.executeWithoutResult(
                                status -> {
                                    catalogue.readSourceSchema();
                                    // Both tables used to be REQUIRE_EMPTY: each chart was held.
                                    assertThatCode(
                                                    () ->
                                                            catalogue.requireUnclassifiedSourcesEmpty(
                                                                    patient, () -> {}))
                                            .doesNotThrowAnyException();
                                    assertThatCode(
                                                    () ->
                                                            catalogue.requireUnclassifiedSourcesEmpty(
                                                                    other, () -> {}))
                                            .doesNotThrowAnyException();
                                }));
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

    /**
     * The real rows, captured by the real readers as the export captures them, read back and
     * witnessed, and emitted by the history occurrence service at all seven positions: each
     * attached note is one ClinicalNotes entry that opens by naming its record, every link field
     * is accounted, and the other chart's shared note is not part of this chart.
     */
    @Test
    void shouldExportEachAttachedNote_namingItsRecordThroughTheHistoryService() throws Exception {
        var categories = EnumSet.of(CdsCategory.DEMOGRAPHICS);
        categories.addAll(CdsExportNoteSourceReader.CATEGORIES);
        var command =
                new CdsExportCommand(
                        "note-link-capture",
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
        readers.add(new CdsExportNoteLinkSourceReader(links));
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
                                        100_000,
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
        // Five links of this chart's notes; the other chart's note and its link are not read.
        assertThat(scopes.get("casemgmt_note_link").rows()).isEqualTo(5);
        assertThat(scopes.get("casemgmt_note").rows()).isEqualTo(6);
        var capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(scopes);
        when(capture.values())
                .thenReturn(
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", MANIFEST, 1));
        when(capture.index())
                .thenReturn(
                        new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", MANIFEST, 1));
        var captures = mock(CdsExportNativeCaptureService.class);
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            expected,
                                            100_000,
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
                            call.<CdsExportRetainedValuesService.Work>getArgument(10)
                                    .use(
                                            retained,
                                            List.of(),
                                            mock(CdsExportRetainedSectionWriter.Values.class));
                            return null;
                        })
                .when(retainedValues)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
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
        var receipts = new ArrayList<CdsExportClinicalOccurrenceService.Receipt>();
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
                                    new CdsExportClinicalOccurrenceService.Bounds(
                                            100_000, 64L << 20, 1000, 8L << 20, 100, 1000),
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
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        Integer.toString(patient),
                        categories,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        // Five chains (the revised discontinuation note is one), all ClinicalNotes.
        assertThat(receipts).hasSize(5);
        assertThat(receipts)
                .allSatisfy(
                        receipt ->
                                assertThat(receipt.identity().category())
                                        .isEqualTo(CdsCategory.CLINICAL_NOTES));
        var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
        var notes = xml.getElementsByTagNameNS("cds", "MyClinicalNotesContent");
        var texts = new ArrayList<String>();
        for (int i = 0; i < notes.getLength(); i++) texts.add(notes.item(i).getTextContent());
        String prefix = "[CARLOS: this note is attached to another record in the sending system: ";
        assertThat(texts)
                .anySatisfy(
                        text ->
                                assertThat(text)
                                        .startsWith(prefix + "document 880001.]\n\nDocument Referral"))
                .anySatisfy(
                        text ->
                                assertThat(text)
                                        .startsWith(prefix + "prescription 770001.]")
                                        .contains("Discontinued: rash", "rash and hives"))
                .anySatisfy(
                        text ->
                                assertThat(text)
                                        .startsWith(prefix + "e-mail 660001.]")
                                        .contains("Subject: Results"))
                .anySatisfy(
                        text ->
                                assertThat(text)
                                        .startsWith(prefix + "clinical note " + (note + 4) + ".]")
                                        .contains("Reaction Type=AL"))
                .anySatisfy(text -> assertThat(text).startsWith("Seen for cough"));
        assertThat(String.join("\n", texts)).doesNotContain("Tickler: call back", "550001");
        // Every column of every captured link row is decided, by the occurrence of its note.
        var decided = new HashSet<String>();
        for (var receipt : receipts)
            receipt.fields()
                    .forEach(
                            (field, decision) -> {
                                if (field.table().equals("casemgmt_note_link"))
                                    decided.add(field.recordKey() + "/" + field.column());
                            });
        assertThat(decided).hasSize(25);
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
