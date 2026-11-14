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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.junit.jupiter.api.*;

/**
 * A chart's notes and issues read window by window through the history occurrence service, with
 * the real witness reader, the real captured-row reader and the real revision inventory over a
 * capture that hands out every field of every row on each pass, as an authenticated read of the
 * capture does: the same bytes and receipts as the chart read whole, past every former bound, one
 * window of rows held at a time, and every incomplete or altered chart still refused.
 *
 * <p>The capture is made, not stored: each pass computes each field's bytes and digest again from
 * the fixture's rows (or, for a very long table, from a function of the row's position), so a
 * chart of hundreds of thousands of rows costs the test nothing between passes.
 */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportHistoryWindowUnitTest {
    private static final String HASH = "a".repeat(64), PATIENT = "b".repeat(64);
    private static final List<String> TAGS =
            List.of("FamHistory", "MedHistory", "Concerns", "RiskFactors", "Reminders", "SocHistory");
    private static final List<Plan> PLANS =
            List.of(
                    Plan.CASE_NOTES,
                    Plan.CASE_NOTE_EXT,
                    Plan.CASE_NOTE_LINK,
                    Plan.CASE_NOTE_ISSUE_LINK,
                    Plan.CASE_ISSUES,
                    Plan.ISSUE_CATALOGUE,
                    Plan.DEMOGRAPHIC_MERGES);
    private static final String ZONE = "clinical_timezone";

    // The mocks record nothing: a long chart makes millions of calls (every checkpoint).
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class, withSettings().stubOnly());
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class, withSettings().stubOnly());
    private final CdsExportRunControl control =
            mock(CdsExportRunControl.class, withSettings().stubOnly());
    private final CdsExportRetainedValuesService values =
            mock(CdsExportRetainedValuesService.class, withSettings().stubOnly());
    private final CdsExportRetainedReceiptVerificationService verification =
            mock(CdsExportRetainedReceiptVerificationService.class, withSettings().stubOnly());
    private final Set<CdsCategory> categories = EnumSet.of(CdsCategory.DEMOGRAPHICS);
    /** Each table's rows as the test states them, in any order; a pass reads them by key. */
    private final Map<String, List<Map<String, String>>> rows = new TreeMap<>();
    /** A table made on every pass from the position of its row, instead of held. */
    private final Map<String, Generated> generated = new HashMap<>();
    private final List<CdsExportNoteAccessDto> scopes = new ArrayList<>();
    private CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                    1_000_000,
                    64L << 20,
                    8192,
                    131_072);
    private CdsExportLeaseDto lease;
    private CdsExportCommand command;
    private CdsExportPatientCaptureDto source;
    private Map<String, CdsExportCapturedTableDto> tables;
    /** Each table's stated rows in key order, fixed when the capture is made. */
    private Map<String, List<Map<String, String>>> ordered;
    private CdsExportClinicalZone.Snapshot zone;
    private long scans;
    private Runnable afterScan = () -> {};
    /** What the witness reader hands on for each witness it read: the witness, unless a test says. */
    private Function<CdsExportNativeRowReceiptDto, List<CdsExportNativeRowReceiptDto>> witnessHook =
            List::of;

    /** A table of {@code count} rows; {@code row} makes the row at a position, in key order. */
    private record Generated(int count, IntFunction<Map<String, String>> row) {}

    @BeforeEach
    void setup() throws Exception {
        categories.addAll(CdsExportNoteSourceReader.CATEGORIES);
        zone = CdsExportClinicalZone.capture("America/Toronto");
        when(patient.patient()).thenReturn(27);
        when(control.issueProgramReadable(anyInt(), any(), any())).thenReturn(true);
        command =
                new CdsExportCommand(
                        "history-windows",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        categories,
                        "receiver",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        HASH,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
        var retained =
                new CdsExportRetainedIndexDto(
                        HASH,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        var streams = mock(CdsExportRetainedSectionWriter.Values.class, withSettings().stubOnly());
        doAnswer(
                        call -> {
                            call.<CdsExportRetainedValuesService.Work>getArgument(8)
                                    .use(retained, List.of(), streams);
                            return null;
                        })
                .when(values)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any(), any());
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                HASH, Map.of(), Set.of(), Set.of()));
        when(captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(call -> scan(call.getArgument(7)));
        for (var plan : PLANS) rows.put(plan.table(), new ArrayList<>());
    }

    // ---- fixture ------------------------------------------------------------------------------

    /** A row of {@code plan}'s table: every column, null unless stated. */
    private static Map<String, String> row(Plan plan, long id, Map<String, String> supplied) {
        var fields = new TreeMap<String, String>();
        for (String name : plan.columns()) fields.put(name, null);
        fields.put(
                plan == Plan.CASE_NOTES
                        ? "note_id"
                        : plan == Plan.ISSUE_CATALOGUE ? "issue_id" : "id",
                Long.toString(id));
        fields.putAll(supplied);
        return fields;
    }

    private Map<String, String> add(Plan plan, long id, Map<String, String> supplied) {
        var fields = row(plan, id, supplied);
        rows.get(plan.table()).add(fields);
        return fields;
    }

    private void note(long id, String uuid, String text) {
        var fields = new HashMap<String, String>();
        fields.put("demographic_no", "27");
        fields.put("uuid", uuid);
        fields.put("note", text);
        fields.put("provider_no", "17");
        fields.put("signed", "1");
        fields.put("archived", "0");
        fields.put("signing_provider_no", "17");
        fields.put("update_date", "2026-07-10 12:30:00");
        fields.put("observation_date", "2026-07-09 00:00:00");
        fields.put("program_no", "");
        fields.put("reporter_caisi_role", "1");
        add(Plan.CASE_NOTES, id, fields);
        scopes.add(new CdsExportNoteAccessDto(id, 27, "", "1"));
    }

    private void link(long note, long issue) {
        rows.get("casemgmt_issue_notes")
                .add(
                        new TreeMap<>(
                                Map.of("note_id", Long.toString(note), "id", Long.toString(issue))));
    }

    private void diagnosis(long issue, long definition, String code) {
        add(
                Plan.CASE_ISSUES,
                issue,
                Map.of(
                        "demographic_no", "27",
                        "issue_id", Long.toString(definition),
                        "resolved", issue % 2 == 0 ? "1" : "0"));
        add(
                Plan.ISSUE_CATALOGUE,
                definition,
                Map.of("code", code, "description", "Synthetic diagnosis " + code, "type", "icd9"));
    }

    private static String uuid(long number) {
        return String.format(Locale.ROOT, "%08x-aaaa-4aaa-8aaa-aaaaaaaaaaaa", number);
    }

    private void clear() {
        rows.values().forEach(List::clear);
        generated.clear();
        scopes.clear();
    }

    /**
     * A chart of {@code count} notes: encounter notes and every CPP section, one chain in ten
     * with two revisions, some notes with a dated extension or an attached record, a diagnosis a
     * family-history item carries, one that encounter notes link and one that nothing links.
     */
    private void chart(int count) {
        clear();
        for (int tag = 0; tag < TAGS.size(); tag++) {
            add(
                    Plan.CASE_ISSUES,
                    41 + tag,
                    Map.of("demographic_no", "27", "issue_id", Integer.toString(51 + tag)));
            add(
                    Plan.ISSUE_CATALOGUE,
                    51 + tag,
                    Map.of("code", TAGS.get(tag), "description", "Chart category", "type", "system"));
        }
        diagnosis(61, 71, "2500");
        diagnosis(62, 72, "4019");
        diagnosis(63, 73, "49390");
        long extension = 5000, attached = 7000;
        for (int i = 1; i <= count; i++) {
            long id = 1000 + i;
            // Every tenth note revises the note before it: one chain of two revisions.
            boolean revision = i % 10 == 0;
            int kind = (revision ? i - 1 : i) % 7;
            note(
                    id,
                    uuid(revision ? id - 1 : id),
                    "Synthetic item " + (revision ? i - 1 : i) + (revision ? " revised" : ""));
            if (kind > 0) link(id, 40 + kind);
            // A family-history item carries its own coded diagnosis.
            if (kind == 1 && i % 3 == 0) link(id, 61);
            // Encounter notes link a diagnosis that is the patient's own problem.
            if (kind == 0 && i % 2 == 0) link(id, 62);
            if (kind == 6 && i % 2 == 0)
                add(
                        Plan.CASE_NOTE_EXT,
                        ++extension,
                        Map.of(
                                "note_id", Long.toString(id),
                                "key_val", "Start Date",
                                "value", "YYYY",
                                "date_value", "2010-01-01 00:00:00"));
            if (kind == 0 && i % 4 == 1) {
                var fields = new HashMap<String, String>();
                fields.put("note_id", Long.toString(id));
                fields.put("table_name", "5");
                fields.put("table_id", Long.toString(880000 + i));
                add(Plan.CASE_NOTE_LINK, ++attached, fields);
            }
        }
    }

    /** States the capture: each table's columns, count, receipt recipe and the notes' scopes. */
    private void prepare() {
        var composed = new TreeMap<String, CdsExportCapturedTableDto>();
        ordered = new HashMap<>();
        for (var plan : PLANS) {
            String table = plan.table();
            var columns = Set.copyOf(plan.columns());
            var receipt = new ArrayList<CdsNativeRowDigest.Column>();
            for (String column : new TreeSet<>(columns))
                receipt.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
            var made = generated.get(table);
            if (made != null && !rows.get(table).isEmpty())
                throw new IllegalStateException("A table is either stated or made");
            var sorted = new ArrayList<>(rows.get(table));
            sorted.sort(Comparator.comparing(row -> key(table, row)));
            ordered.put(table, sorted);
            composed.put(
                    table,
                    new CdsExportCapturedTableDto(
                            columns,
                            made != null ? made.count() : sorted.size(),
                            HASH,
                            receipt,
                            table.equals("casemgmt_note") ? scopes : List.of()));
        }
        composed.put("provider", new CdsExportCapturedTableDto(CdsExportProviderRowDto.COLUMNS, 1, HASH));
        composed.put(
                ZONE, new CdsExportCapturedTableDto(Set.of("sha256", "tzdbVersion", "zoneId"), 1, HASH));
        tables = composed;
        var capture = mock(CdsExportNativeCaptureDto.class, withSettings().stubOnly());
        when(capture.tables()).thenReturn(tables);
        when(capture.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(capture.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
    }

    /**
     * One pass over the capture: every field of every row of every table, in table and key
     * order, each with its bytes and the digest of its bytes, as the capture reader hands them
     * out after checking them.
     */
    private CdsExportNativeCaptureDto scan(CdsExportNativeCaptureReader.Fields fields)
            throws IOException, java.security.NoSuchAlgorithmException {
        scans++;
        // The digest stays local to this pass and is handed down; it is never kept in a field.
        var digest = MessageDigest.getInstance("SHA-256");
        var pass = new Pass(fields);
        for (String table : tables.keySet()) {
            var columns = new TreeSet<>(tables.get(table).columns());
            if (table.equals("provider")) {
                var provider = new TreeMap<String, String>();
                for (String column : columns) provider.put(column, null);
                provider.put("provider_no", "17");
                provider.put("first_name", "Synthetic");
                provider.put("last_name", "Fixture");
                provider.put("ohip_no", "001234");
                pass.row(
                        table,
                        CdsExportPublicProviderSourceReader.key("17"),
                        columns,
                        provider,
                        digest);
            } else if (table.equals(ZONE)) {
                pass.row(
                        table,
                        "configuration",
                        columns,
                        Map.of(
                                "sha256", zone.sha256(),
                                "tzdbVersion", zone.tzdbVersion(),
                                "zoneId", zone.zoneId()),
                        digest);
            } else if (generated.containsKey(table)) {
                var made = generated.get(table);
                String previous = null;
                for (int position = 0; position < made.count(); position++) {
                    var row = made.row().apply(position);
                    String key = key(table, row);
                    if (previous != null && previous.compareTo(key) >= 0)
                        throw new IllegalStateException("A made table is made in key order");
                    previous = key;
                    pass.row(table, key, columns, row, digest);
                }
            } else
                for (var row : ordered.get(table))
                    pass.row(table, key(table, row), columns, row, digest);
        }
        afterScan.run();
        return source.capture();
    }

    /**
     * One pass's hand-out of fields: each field's bytes, its offset and the SHA-256 of its bytes,
     * remembered for the few values a long table repeats on every row.
     */
    private static final class Pass {
        private final CdsExportNativeCaptureReader.Fields fields;
        private final Map<String, String> repeated =
                new LinkedHashMap<>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > 256;
                    }
                };
        private long offset;

        private Pass(CdsExportNativeCaptureReader.Fields fields) {
            this.fields = fields;
        }

        private void row(
                String table,
                String key,
                Set<String> columns,
                Map<String, String> row,
                MessageDigest digest)
                throws IOException {
            for (String column : columns) {
                String value = row.get(column);
                byte[] bytes =
                        value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                var field =
                        new CdsExportCapturedFieldDto(
                                table,
                                key,
                                column,
                                value == null
                                        ? CdsExportCapturedFieldDto.Kind.SQL_NULL
                                        : CdsExportCapturedFieldDto.Kind.UTF8,
                                offset,
                                bytes.length,
                                sha256(value, bytes, digest));
                fields.output(field).write(bytes);
                fields.verified(field);
                offset += bytes.length;
            }
        }

        private String sha256(String value, byte[] bytes, MessageDigest digest) {
            String text = value == null ? "" : value;
            var known = repeated.get(text);
            if (known != null) return known;
            String computed = HexFormat.of().formatHex(digest.digest(bytes));
            repeated.put(text, computed);
            return computed;
        }
    }

    private static String key(String table, Map<String, String> row) {
        if (table.equals("casemgmt_issue_notes"))
            return CdsExportPatientTableSourceReader.key(Long.parseLong(row.get("note_id")))
                    + CdsExportPatientTableSourceReader.key(Long.parseLong(row.get("id")));
        return CdsExportPatientTableSourceReader.key(
                Long.parseLong(
                        row.get(
                                table.equals("casemgmt_note")
                                        ? "note_id"
                                        : table.equals("issue") ? "issue_id" : "id")));
    }

    /** The real witness reader, handing on what {@link #witnessHook} makes of each witness. */
    private CdsExportNativeRowReceiptReader witnesses() {
        return new CdsExportNativeRowReceiptReader(captures) {
            @Override
            public CdsExportNativeRowReceiptsDto scan(
                    CdsExportLeaseDto lease,
                    CdsExportCohortService.Patient patient,
                    Set<String> requiredTables,
                    CdsExportPatientCaptureDto source,
                    long maximumFields,
                    long maximumBytes,
                    CdsExportRunControl control,
                    Receipts output)
                    throws IOException {
                return super.scan(
                        lease,
                        patient,
                        requiredTables,
                        source,
                        maximumFields,
                        maximumBytes,
                        control,
                        witness -> {
                            for (var handed : witnessHook.apply(witness)) output.accept(handed);
                        });
            }
        };
    }

    private CdsExportHistoryOccurrenceService service() {
        var witnesses = witnesses();
        return new CdsExportHistoryOccurrenceService(
                captures,
                witnesses,
                new CdsExportNoteRevisionInventoryService(captures, witnesses),
                values,
                verification);
    }

    /** What one run of the section emitted. */
    private record Emitted(
            byte[] xml,
            List<CdsExportClinicalOccurrenceService.Receipt> receipts,
            Map<CdsCategory, CdsExportClinicalOccurrenceService.Result> results) {}

    private Emitted emit() throws Exception {
        var receipts = new ArrayList<CdsExportClinicalOccurrenceService.Receipt>();
        var results =
                new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                        CdsCategory.class);
        var output = new ByteArrayOutputStream();
        emit(output, receipts::add, results);
        return new Emitted(output.toByteArray(), receipts, results);
    }

    private void emit(
            OutputStream output,
            CdsExportClinicalOccurrenceService.Receipts receipts,
            Map<CdsCategory, CdsExportClinicalOccurrenceService.Result> results)
            throws Exception {
        prepare();
        scans = 0;
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        HASH,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        categories,
                        emitter -> {
                            emitter.emit(demographic());
                            service()
                                    .withSource(
                                            lease,
                                            patient,
                                            PATIENT,
                                            tables.keySet(),
                                            source,
                                            new CdsExportStagedFileDto(
                                                    "4".repeat(64), "PAYLOAD_MANIFEST", HASH, 1),
                                            bounds,
                                            control,
                                            emitter,
                                            session -> {
                                                for (var category : CdsCategory.values())
                                                    if (CdsExportNoteSourceReader.CATEGORIES
                                                            .contains(category))
                                                        results.put(
                                                                category,
                                                                session.append(category, receipts));
                                            });
                        },
                        ignored -> {},
                        Long.MAX_VALUE,
                        () -> {});
    }

    private CdsExportHistoryCurrentContext read(CdsExportClinicalOccurrenceService.Bounds stated)
            throws IOException {
        return CdsExportHistoryCurrentContext.read(
                captures, witnesses(), lease, patient, tables.keySet(), source, stated, control);
    }

    private static CdsExportXmlComposer.Section demographic() {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(PATIENT, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
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
                    xml.element("cds", "UniqueVendorIdSequence", "27");
                    xml.start("cds", "PersonStatusCode");
                    xml.element("cds", "PersonStatusAsEnum", "A");
                    xml.end();
                });
    }

    // ---- equivalence --------------------------------------------------------------------------

    @Test
    void shouldEmitTheSameBytesAndReceipts_whenAChartIsReadInSmallWindows() throws Exception {
        chart(45);
        var whole = emit();
        long wholeScans = scans;
        // 41 chains (four of two revisions), the two standalone diagnoses and nothing else.
        assertThat(whole.receipts()).hasSize(41 + 2);
        assertThat(whole.results().values())
                .allSatisfy(result -> assertThat(result.nativeRows()).isPositive());
        assertThat(whole.receipts())
                .extracting(CdsExportClinicalOccurrenceService.Receipt::table)
                .contains("casemgmt_issue", "casemgmt_note");
        for (int window : new int[] {1, 2, 3, 7, 40}) {
            bounds = bounds.withWindow(window, 64L << 20);
            var windowed = emit();
            assertThat(windowed.xml()).as("window of " + window).isEqualTo(whole.xml());
            assertThat(windowed.receipts()).as("window of " + window).isEqualTo(whole.receipts());
            assertThat(windowed.results()).as("window of " + window).isEqualTo(whole.results());
            // The chart was read in windows: more passes than the whole read made.
            assertThat(scans).isGreaterThan(wholeScans);
        }
        // A chart of few rows that passes the window's bytes is read in windows too, each
        // window too large for its bytes read again smaller.
        bounds = bounds.withWindow(8192, 6_000);
        var small = emit();
        assertThat(small.xml()).isEqualTo(whole.xml());
        assertThat(small.receipts()).isEqualTo(whole.receipts());
        assertThat(scans).isGreaterThan(wholeScans);
        // And a chart that fits is still read whole, by the passes it always had.
        bounds = bounds.withWindow(8192, 64L << 20);
        emit();
        assertThat(scans).isEqualTo(wholeScans);
    }

    @Test
    void shouldEmitTheSameReceipts_whenOneCategoryIsAppendedAlone() throws Exception {
        chart(30);
        var whole = emit();
        bounds = bounds.withWindow(3, 64L << 20);
        prepare();
        for (var category : CdsExportNoteSourceReader.CATEGORIES) {
            var receipts = new ArrayList<CdsExportClinicalOccurrenceService.Receipt>();
            new CdsExportXmlComposer()
                    .composeFromSource(
                            new ByteArrayOutputStream(),
                            HASH,
                            PATIENT,
                            "2026.08.0-test",
                            CdsLogicalProfile.ONTARIO_MD_5_2,
                            categories,
                            emitter -> {
                                emitter.emit(demographic());
                                service()
                                        .append(
                                                category,
                                                lease,
                                                patient,
                                                PATIENT,
                                                tables.keySet(),
                                                source,
                                                new CdsExportStagedFileDto(
                                                        "4".repeat(64), "PAYLOAD_MANIFEST", HASH, 1),
                                                bounds,
                                                control,
                                                emitter,
                                                receipts::add);
                            },
                            ignored -> {},
                            Long.MAX_VALUE,
                            () -> {});
            assertThat(receipts)
                    .as(category.wireName())
                    .isEqualTo(
                            whole.receipts().stream()
                                    .filter(receipt -> receipt.identity().category() == category)
                                    .toList());
        }
    }

    /**
     * What the field reconciler and the native mapper read of the context, owner by owner: a
     * window of one note gives that note the same row, extensions, issue links, attached records,
     * patient issues and catalogue entries as the chart read whole, and every row an import unit
     * names (here rows of other notes, a patient issue and a row the chart no longer holds) is
     * the same row or the same absence. The facts the plan reads of a chart read in windows are
     * those of the chart read whole.
     */
    @Test
    void shouldGiveEachOwnerTheSameRows_whenTheContextIsReadInWindows() throws Exception {
        chart(45);
        prepare();
        var whole = read(bounds);
        var windowed = read(bounds.withWindow(4, 64L << 20));
        assertThat(whole.windowed()).isFalse();
        assertThat(windowed.windowed()).isTrue();
        assertThat(windowed.noteIds()).isEqualTo(whole.noteIds());
        assertThat(new TreeSet<>(windowed.patientIssueIds()))
                .isEqualTo(new TreeSet<>(whole.patientIssueIds()));
        var definitions = new TreeSet<String>();
        for (String issue : whole.patientIssueIds()) {
            assertThat(windowed.definitionOf(issue)).isEqualTo(whole.definitionOf(issue));
            definitions.add(whole.definitionOf(issue));
        }
        assertThat(windowed.definitionOf("999")).isNull();
        assertThat(windowed.definitionsAre(definitions)).isTrue();
        assertThat(windowed.definitionsAre(Set.of("51"))).isFalse();
        for (String definition : definitions) {
            assertThat(windowed.hasDefinition(definition)).isTrue();
            assertThat(windowed.systemDefinition(definition))
                    .isEqualTo(whole.systemDefinition(definition));
            assertThat(windowed.issueCategory(definition))
                    .isEqualTo(whole.issueCategory(definition));
        }
        var references =
                List.of(
                        new CdsExportRetainedIndexDto.Reference("casemgmt_note_ext", "5001", HASH),
                        new CdsExportRetainedIndexDto.Reference("casemgmt_note", "1002", HASH),
                        new CdsExportRetainedIndexDto.Reference("casemgmt_issue", "63", HASH),
                        new CdsExportRetainedIndexDto.Reference(
                                "casemgmt_issue_notes", "1002_42", HASH),
                        new CdsExportRetainedIndexDto.Reference("casemgmt_note_ext", "99999", HASH));
        for (long note : whole.noteIds()) {
            assertThat(windowed.category(note)).isEqualTo(whole.category(note));
            assertThat(new TreeSet<>(windowed.linkedIssues(note)))
                    .isEqualTo(new TreeSet<>(whole.linkedIssues(note)));
            assertThat(windowed.nativeContextNote(note)).isEqualTo(whole.nativeContextNote(note));
            var window =
                    windowed.window(
                            Set.of(note),
                            Set.of(),
                            references,
                            CdsExportSectionWindowPlanner.wholeBudget(1, bounds.window()));
            var expected = whole.note(note);
            var actual = window.note(note);
            assertThat(actual.note()).isEqualTo(expected.note());
            assertThat(actual.extensions()).isEqualTo(expected.extensions());
            assertThat(actual.links()).isEqualTo(expected.links());
            assertThat(actual.noteLinks()).isEqualTo(expected.noteLinks());
            for (var link : expected.links()) {
                var issue = expected.patientIssues().get(link.fields().get("id"));
                assertThat(actual.patientIssues().get(link.fields().get("id"))).isEqualTo(issue);
                String definition = issue.value().fields().get("issue_id");
                assertThat(actual.definitions().get(definition))
                        .isEqualTo(expected.definitions().get(definition));
            }
            for (var reference : references)
                assertThat(window.original(reference.table(), reference.id()))
                        .as(reference.table() + "/" + reference.id())
                        .isEqualTo(whole.original(reference.table(), reference.id()));
            assertThat(window.taken()).containsExactly(
                    expected.extensions().size(),
                    expected.links().size(),
                    expected.noteLinks().size());
            // A note a reference names is loaded as that row, never as an owner of the window.
            if (note != 1002)
                assertThatThrownBy(() -> window.note(1002))
                        .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        }
        // A standalone patient issue is read with its catalogue entry and no note.
        var issues =
                windowed.window(
                        Set.of(),
                        Set.of("62", "63"),
                        List.of(),
                        CdsExportSectionWindowPlanner.wholeBudget(2, bounds.window()));
        for (String issue : List.of("62", "63")) {
            assertThat(issues.original("casemgmt_issue", issue))
                    .isEqualTo(whole.original("casemgmt_issue", issue));
            String definition = whole.definitionOf(issue);
            assertThat(issues.original("issue", definition))
                    .isEqualTo(whole.original("issue", definition));
        }
    }

    // ---- past the former bounds ---------------------------------------------------------------

    @Test
    void shouldExportTheChart_pastTheFormerRowBound() throws Exception {
        // 8,193 notes: one more than the former cds.export.maxNativeRows, which stopped the job
        // with CDS_EXPORT_RESOURCE_LIMIT before a single note was read.
        chart(8_193);
        long[] count = {0};
        var digest = MessageDigest.getInstance("SHA-256");
        var results =
                new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                        CdsCategory.class);
        emit(new DigestOutputStream(OutputStream.nullOutputStream(), digest), receipt -> count[0]++, results);
        long windowedScans = scans;
        // 819 notes revise the note before them; two diagnoses stand alone.
        assertThat(count[0]).isEqualTo(8_193 - 819 + 2);
        long emitted = 0;
        for (var result : results.values()) emitted += result.nativeRows();
        assertThat(emitted).isEqualTo(count[0]);
        byte[] windowed = digest.digest();
        // The same chart with a window that holds it whole writes the same document, by fewer
        // passes over the capture.
        bounds = bounds.withWindow(16_384, 1L << 30);
        emit(new DigestOutputStream(OutputStream.nullOutputStream(), digest), receipt -> {}, results);
        assertThat(digest.digest()).isEqualTo(windowed);
        assertThat(scans).isLessThan(windowedScans);
    }

    @Test
    void shouldPlanAndEmitAChart_pastTheFormerLookupBound() throws Exception {
        // 131,073 catalogue entries: one more than the former cds.export.maxLookupRows, which
        // stopped the job before a note was read. Each is a diagnosis of the patient's that no
        // note links, so each is its own ProblemList entry. The two tables are made on every
        // pass from the position of the row; nothing of them is held between passes.
        clear();
        int count = 131_073;
        generated.put(
                "casemgmt_issue",
                new Generated(
                        count,
                        position ->
                                made(
                                        Plan.CASE_ISSUES,
                                        position + 1L,
                                        "demographic_no",
                                        "27",
                                        "issue_id",
                                        Long.toString(200_001L + position))));
        generated.put(
                "issue",
                new Generated(
                        count,
                        position ->
                                made(
                                        Plan.ISSUE_CATALOGUE,
                                        200_001L + position,
                                        "code",
                                        "2500",
                                        "type",
                                        "icd9")));
        // Three windows of 43,691 diagnoses. Every pass over this chart reads its 262,146 rows,
        // so a smaller window would only repeat the same proof more slowly; the default window
        // is proved past its bound by a chart of notes (shouldExportTheChart_pastTheFormerRowBound).
        bounds = bounds.withWindow(43_691, 1L << 30);
        long[] emitted = {0};
        var results =
                new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                        CdsCategory.class);
        emit(
                OutputStream.nullOutputStream(),
                receipt -> {
                    assertThat(receipt.table()).isEqualTo("casemgmt_issue");
                    emitted[0]++;
                },
                results);
        assertThat(emitted[0]).isEqualTo(count);
        assertThat(results.get(CdsCategory.PROBLEM_LIST).nativeRows()).isEqualTo(count);
        // The merge check, the revision inventory, the chart-wide facts, then one row pass and
        // one witness pass for each of the three windows: the plan the history inventory makes
        // (CdsExportHistoryInventoryService) is this plan, of these facts.
        assertThat(scans).isLessThanOrEqualTo(12);
    }

    /** A row of {@code plan}'s table made for one pass: every column, null unless stated. */
    private static Map<String, String> made(Plan plan, long id, String... stated) {
        var fields = new HashMap<String, String>(32);
        for (String name : plan.columns()) fields.put(name, null);
        fields.put(plan == Plan.ISSUE_CATALOGUE ? "issue_id" : "id", Long.toString(id));
        for (int i = 0; i < stated.length; i += 2) fields.put(stated[i], stated[i + 1]);
        return fields;
    }

    // ---- memory -------------------------------------------------------------------------------

    @Test
    void shouldHoldOneWindowOfNoteWitnesses_whileAChartOfManyNotesIsEmitted() throws Exception {
        // Every note witness a pass hands out is watched weakly. After any pass, the witnesses
        // still reachable are those of at most one window; read whole, the chart holds all of
        // them, which shows the watch sees what is held.
        int count = 512, window = 32;
        clear();
        for (int i = 1; i <= count; i++) note(1000 + i, uuid(1000 + i), "Encounter note " + i);
        var handed = new ArrayList<WeakReference<CdsExportNativeRowReceiptDto>>();
        witnessHook =
                witness -> {
                    if (witness.table().equals("casemgmt_note"))
                        handed.add(new WeakReference<>(witness));
                    return List.of(witness);
                };
        long[] most = {0};
        afterScan =
                () -> {
                    System.gc();
                    most[0] =
                            Math.max(
                                    most[0],
                                    handed.stream().filter(held -> held.get() != null).count());
                };
        long[] emitted = {0};
        var results =
                new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                        CdsCategory.class);
        bounds = bounds.withWindow(window, 64L << 20);
        emit(OutputStream.nullOutputStream(), receipt -> emitted[0]++, results);
        assertThat(emitted[0]).isEqualTo(count);
        assertThat(handed).hasSizeGreaterThan(count * 3);
        assertThat(most[0]).isPositive().isLessThanOrEqualTo(window);

        handed.clear();
        most[0] = 0;
        bounds = bounds.withWindow(8192, 64L << 20);
        emit(OutputStream.nullOutputStream(), receipt -> {}, results);
        assertThat(most[0]).isEqualTo(count);
    }

    // ---- completeness -------------------------------------------------------------------------

    private void windows() {
        bounds = bounds.withWindow(4, 64L << 20);
    }

    /** Refused with this code read in windows, and with the same code read whole. */
    private void assertRefused(String code) {
        windows();
        assertThatThrownBy(this::emit).hasMessage(code);
        bounds = bounds.withWindow(8192, 64L << 20);
        assertThatThrownBy(this::emit).hasMessage(code);
    }

    @Test
    void shouldRefuseAnExtensionOfNoCapturedNote_whenTheChartIsReadInWindows() {
        chart(20);
        add(
                Plan.CASE_NOTE_EXT,
                9_999,
                Map.of("note_id", "777777", "key_val", "Start Date", "value", "YYYY"));
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseAnAttachedRecordOfNoCapturedNote_whenTheChartIsReadInWindows() {
        chart(20);
        add(
                Plan.CASE_NOTE_LINK,
                9_999,
                Map.of("note_id", "777777", "table_name", "5", "table_id", "1"));
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseALinkToNoPatientIssue_whenTheChartIsReadInWindows() {
        chart(20);
        link(1001, 99);
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseALinkOfNoCapturedNote_whenTheChartIsReadInWindows() {
        chart(20);
        link(777_777, 62);
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseAPatientIssueOfNoCatalogueEntry_whenTheChartIsReadInWindows() {
        chart(20);
        add(Plan.CASE_ISSUES, 64, Map.of("demographic_no", "27", "issue_id", "999"));
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseAnotherPatientsIssue_whenTheChartIsReadInWindows() {
        chart(20);
        rows.get("casemgmt_issue").get(7).put("demographic_no", "28");
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseANoteOutsideItsScope_whenTheChartIsReadInWindows() {
        chart(20);
        rows.get("casemgmt_note").get(11).put("program_no", "5");
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseAnUnwitnessedRow_whenTheChartIsReadInWindows() {
        chart(20);
        // The capture holds the extension; its witness never reaches the section.
        witnessHook =
                witness ->
                        witness.table().equals("casemgmt_note_ext") && witness.id().equals("5001")
                                ? List.of()
                                : List.of(witness);
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseARowChangedSinceItsWitness_whenTheChartIsReadInWindows() {
        chart(20);
        // The witness froze another value of the extension than the capture now holds.
        witnessHook =
                witness -> {
                    if (!witness.table().equals("casemgmt_note_ext") || !witness.id().equals("5001"))
                        return List.of(witness);
                    var state = witness.state();
                    var cells = new ArrayList<>(state.cells());
                    for (int i = 0; i < state.columns().size(); i++)
                        if (state.columns().get(i).name().equals("value"))
                            cells.set(
                                    i,
                                    new CdsNativeRowState.Cell(
                                            false,
                                            4,
                                            HexFormat.of()
                                                    .formatHex(
                                                            sha256(
                                                                    "YYMM".getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
                    var changed =
                            new CdsNativeRowState(state.table(), state.id(), state.columns(), cells);
                    return List.of(
                            new CdsExportNativeRowReceiptDto(
                                    witness.table(),
                                    witness.id(),
                                    witness.recordKey(),
                                    changed.digest(),
                                    changed));
                };
        assertRefused("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
    }

    @Test
    void shouldRefuseAWitnessOfNoCapturedRow_whenTheChartIsReadInWindows() {
        chart(20);
        // A witness of an extension the capture does not hold: its row is missing.
        witnessHook =
                witness -> {
                    if (!witness.table().equals("casemgmt_note_ext") || !witness.id().equals("5001"))
                        return List.of(witness);
                    var state = witness.state();
                    var absent =
                            new CdsNativeRowState(
                                    state.table(), "99999", state.columns(), state.cells());
                    return List.of(
                            witness,
                            new CdsExportNativeRowReceiptDto(
                                    witness.table(),
                                    "99999",
                                    CdsExportPatientTableSourceReader.key(99_999),
                                    absent.digest(),
                                    absent));
                };
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldRefuseARowWithTwoWitnesses_whenTheChartIsReadInWindows() {
        chart(20);
        // One issue link owned twice: the section must not let either witness pass for it.
        witnessHook =
                witness ->
                        witness.table().equals("casemgmt_issue_notes")
                                ? List.of(witness, witness)
                                : List.of(witness);
        assertRefused("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
    }

    @Test
    void shouldHoldACatalogueEntryNoPatientIssueNames_whenTheChartIsReadInWindows() {
        chart(20);
        add(Plan.ISSUE_CATALOGUE, 999, Map.of("code", "4019", "type", "icd9"));
        // The same outcome as the chart read whole: held for review, not exported short.
        assertRefused("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldHoldANoteFiledUnderTwoSections_whenTheChartIsReadInWindows() {
        chart(20);
        link(1001, 42);
        assertRefused("CDS_EXPORT_NATIVE_HISTORY_CATEGORY_AMBIGUOUS");
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
