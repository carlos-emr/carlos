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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * A chart of imported laboratory results and native HL7 reports, and the LaboratoryResults
 * assembly over it: the rows, witnesses, inbox routes, import units and their references are
 * real; the retained content of an imported result (its preparation, source fields and XML,
 * which have their own tests) is a stub that names the result. The readers hand the rows out
 * table by table in record-key order, a fresh copy on every pass, as a read of the capture does.
 */
abstract class CdsExportLaboratoryWindowFixture {
    static final String PATIENT = "b".repeat(64),
            SOURCE = "9".repeat(64),
            RUN = "11111111-1111-4111-8111-111111111111";
    static final List<String> REPORT_TABLES =
            List.of("cds_laboratory_result", "hl7TextInfo", "hl7TextMessage", "patientLabRouting");

    final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    final CdsExportNativeRowReceiptReader witnessReader =
            mock(CdsExportNativeRowReceiptReader.class);
    final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    final CdsExportClinicalZoneService zones = mock(CdsExportClinicalZoneService.class);
    final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
    final CdsExportRetainedReceiptVerificationService verification =
            mock(CdsExportRetainedReceiptVerificationService.class);
    final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    final CdsExportRunControl control = mock(CdsExportRunControl.class);

    /** The capture: each table's rows by record key, as the capture holds them. */
    final Map<String, SortedMap<String, CdsExportCapturedTextRowDto>> tables = new TreeMap<>();

    final Map<String, CdsExportNativeRowReceiptDto> witnesses = new LinkedHashMap<>();
    final Map<String, CdsExportCapturedTextRowDto> providers = new HashMap<>();
    final SortedMap<String, CdsExportCapturedTextRowDto> units = new TreeMap<>();
    final Map<String, List<CdsExportRetainedIndexDto.Reference>> references = new HashMap<>();
    final List<CdsExportRetainedFieldDto> retained = new ArrayList<>();
    final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
    Set<CdsCategory> categories =
            EnumSet.of(
                    CdsCategory.DEMOGRAPHICS,
                    CdsCategory.LABORATORY_RESULTS,
                    CdsCategory.NEW_CATEGORY);
    CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                    1_000_000,
                    64L << 20,
                    8192,
                    131_072);
    CdsExportPatientCaptureDto source;
    CdsExportLeaseDto lease;
    CdsExportRetainedIndexDto index;
    CdsExportClinicalOccurrenceService.Result result;
    /** Passes over the report tables, the witnesses and the routes of the last assembly. */
    int rowPasses, witnessPasses, routePasses, messagePasses;
    /** Called before a pass over the report tables and before a pass over the witnesses. */
    Runnable beforeRows = () -> {}, beforeWitnesses = () -> {};
    /** Report messages handed out by the passes, when a test asks for them. */
    List<java.lang.ref.WeakReference<CdsExportCapturedTextRowDto>> handed;

    @BeforeEach
    void prepare() throws Exception {
        when(patient.patient()).thenReturn(27);
        for (String table : REPORT_TABLES) tables.put(table, new TreeMap<>());
        tables.put(CdsExportLaboratoryOccurrenceService.ROUTES, new TreeMap<>());
        for (String number : List.of("101", "102"))
            providers.put(
                    number,
                    CdsExportLaboratoryReviewerMappingUnitTest.provider(
                            number, "Ada" + number, "Reviewer", "123456"));
        when(zones.read(
                        any(),
                        any(),
                        anySet(),
                        any(CdsExportPatientCaptureDto.class),
                        anyLong(),
                        anyLong(),
                        any()))
                .thenReturn(
                        new CdsExportClinicalZoneService.Bound(
                                "c".repeat(64), ZoneId.of("America/Toronto"), "fixture"));
    }

    // ---- the chart ----------------------------------------------------------------------------

    static CdsExportCapturedTextRowDto row(Plan plan, String key, Map<String, String> values) {
        var fields = new LinkedHashMap<String, String>();
        plan.columns().forEach(column -> fields.put(column, null));
        fields.putAll(values);
        return new CdsExportCapturedTextRowDto(plan.table(), key, fields);
    }

    void put(CdsExportCapturedTextRowDto row) {
        assertThat(tables.get(row.table()).put(row.recordKey(), row)).isNull();
        if (!row.table().equals(CdsExportLaboratoryOccurrenceService.ROUTES)) {
            var witness = witness(row);
            witnesses.put(row.table() + ":" + witness.id(), witness);
        }
    }

    static String id(CdsExportCapturedTextRowDto row) {
        return row.fields().get(row.table().equals("hl7TextMessage") ? "lab_id" : "id");
    }

    static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : new TreeMap<>(row.fields()).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0));
            var text = field.getValue();
            byte[] bytes = text == null ? null : text.getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : bytes.length,
                            text == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(CdsImportKeys.digest().digest(bytes))));
        }
        var state = new CdsNativeRowState(row.table(), id(row), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), state.id(), row.recordKey(), state.digest(), state);
    }

    /** The three rows of one HL7 report; its results differ from every other report's. */
    void report(long lab, String text) {
        put(
                row(
                        Plan.LABORATORY_MESSAGES,
                        CdsExportPatientTableSourceReader.key(lab),
                        Map.of(
                                "lab_id",
                                Long.toString(lab),
                                "message",
                                Base64.getEncoder()
                                        .encodeToString(text.getBytes(StandardCharsets.UTF_8)),
                                "type",
                                "HL7")));
        put(
                row(
                        Plan.LABORATORY_INFORMATION,
                        CdsExportPatientTableSourceReader.key(5_000_000 + lab),
                        Map.of(
                                "id",
                                Long.toString(5_000_000 + lab),
                                "lab_no",
                                Long.toString(lab),
                                "sending_facility",
                                "Native Lab",
                                "accessionNum",
                                "ACC-" + lab)));
        put(
                row(
                        Plan.LABORATORY_ROUTING,
                        CdsExportPatientTableSourceReader.key(7_000_000 + lab),
                        Map.of(
                                "id",
                                Long.toString(7_000_000 + lab),
                                "lab_no",
                                Long.toString(lab),
                                "demographic_no",
                                "27",
                                "lab_type",
                                "HL7")));
    }

    static String message(long lab) {
        return CdsExportNativeLaboratoryMapperUnitTest.message()
                .replace("native-1", "native-" + lab)
                .replace("|5.4|", "|" + (lab % 97) + "." + (lab % 10) + "|");
    }

    void nativeReport(long lab) {
        report(lab, message(lab));
    }

    static String resultId(int number) {
        return String.format(Locale.ROOT, "%064x", number);
    }

    static String fieldId(String result) {
        return CdsImportKeys.hash("fixture-field", result);
    }

    /**
     * One applied imported result: its structured row, the generated report it mirrors (none for
     * a blocked result), its import unit and the references of that unit.
     */
    void importedResult(int number, Long lab) {
        String id = resultId(number);
        var fields = new HashMap<String, String>();
        fields.put("id", id);
        fields.put("demographicNo", "27");
        fields.put("runId", RUN);
        fields.put("nativeLabNo", lab == null ? null : Long.toString(lab));
        fields.put("sourceBlocked", lab == null ? "Y" : null);
        fields.put("laboratoryName", "Imported " + number);
        put(row(Plan.LABORATORY_RESULTS, id, fields));
        if (lab != null) report(lab, "MSH|generated mirror of " + number);
        String address = RUN + ":" + id, key = CdsImportKeys.hash(RUN, id);
        units.put(
                key,
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        key,
                        Map.of(
                                "category",
                                CdsCategory.LABORATORY_RESULTS.wireName(),
                                "run_id",
                                RUN,
                                "unit_key",
                                id)));
        var named = new ArrayList<CdsExportRetainedIndexDto.Reference>();
        named.add(reference("cds_laboratory_result", id));
        if (lab != null) {
            named.add(reference("hl7TextMessage", Long.toString(lab)));
            named.add(reference("hl7TextInfo", Long.toString(5_000_000 + lab)));
            named.add(reference("patientLabRouting", Long.toString(7_000_000 + lab)));
        }
        references.put(address, named);
        retained.add(
                new CdsExportRetainedFieldDto(
                        fieldId(id),
                        RUN,
                        "c".repeat(64),
                        id,
                        CdsCategory.LABORATORY_RESULTS,
                        "/OmdCds/PatientRecord/LaboratoryResults[" + number + "]/LaboratoryName[1]",
                        0,
                        "",
                        "{http://www.w3.org/2001/XMLSchema}string",
                        null,
                        "Imported " + number,
                        null,
                        null));
    }

    CdsExportRetainedIndexDto.Reference reference(String table, String id) {
        return new CdsExportRetainedIndexDto.Reference(
                table, id, witnesses.get(table + ":" + id).digest());
    }

    void route(long id, long lab, String provider, String status, String time, String comment) {
        var fields = new LinkedHashMap<String, String>();
        Plan.LABORATORY_REVIEWERS.columns().forEach(column -> fields.put(column, null));
        fields.put("id", Long.toString(id));
        fields.put("lab_no", Long.toString(lab));
        fields.put("lab_type", "HL7");
        fields.put("provider_no", provider);
        fields.put("status", status);
        fields.put("timestamp", CdsExportLaboratoryReviewerMappingUnitTest.instant(time));
        fields.put("comment", comment);
        put(
                new CdsExportCapturedTextRowDto(
                        CdsExportLaboratoryOccurrenceService.ROUTES,
                        CdsExportPatientTableSourceReader.key(id),
                        fields));
    }

    /**
     * A chart of imported results (one blocked, the rest mirrored by a generated report) and
     * native reports whose numbers sort differently as text and as numbers, with inbox routes on
     * both kinds.
     */
    void mixedChart(int imported, int natives) {
        for (int number = 1; number <= imported; number++)
            importedResult(number, number % 5 == 0 ? null : 2_000L + number);
        for (int report = 0; report < natives; report++) nativeReport(8 + 13L * report);
        long route = 1;
        for (int number = 1; number <= imported; number += 3)
            if (number % 5 != 0)
                route(route++, 2_000L + number, "101", "A", "2026-07-11 09:30:00", null);
        for (int report = 0; report < natives; report += 2) {
            route(route++, 8 + 13L * report, "101", "A", "2026-07-12 10:00:00", null);
            if (report % 4 == 0)
                route(route++, 8 + 13L * report, "102", "A", "2026-07-12 11:00:00", "Repeat in 3 months");
        }
    }

    // ---- the readers --------------------------------------------------------------------------

    void capture() throws Exception {
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        for (var plan :
                List.of(
                        Plan.LABORATORY_RESULTS,
                        Plan.LABORATORY_MESSAGES,
                        Plan.LABORATORY_INFORMATION,
                        Plan.LABORATORY_ROUTING,
                        Plan.LABORATORY_REVIEWERS))
            scopes.put(
                    plan.table(),
                    new CdsExportCapturedTableDto(
                            Set.copyOf(plan.columns()), tables.get(plan.table()).size(), SOURCE));
        scopes.put("provider", new CdsExportCapturedTableDto(Set.of("provider_no"), 0, SOURCE));
        scopes.put("demographic_merged", new CdsExportCapturedTableDto(Set.of("id"), 0, SOURCE));
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(scopes);
        when(generation.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", SOURCE, 1));
        when(generation.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", SOURCE, 1));
        source =
                new CdsExportPatientCaptureDto(
                        generation,
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", SOURCE, 1));
        reset(rows, witnessReader, values, verification);
        when(rows.forEachTable(
                        any(), any(), anySet(), anySet(), any(), anyLong(), anyLong(), anyInt(),
                        any(), any()))
                .thenAnswer(
                        call -> {
                            rowPasses++;
                            beforeRows.run();
                            hand(
                                    new TreeSet<String>(call.<Set<String>>getArgument(3)),
                                    call.getArgument(9));
                            return generation;
                        });
        when(rows.forEach(
                        any(), any(), anySet(), anyString(), any(), anyLong(), anyLong(), anyInt(),
                        any(), any()))
                .thenAnswer(
                        call -> {
                            String table = call.getArgument(3);
                            if (table.equals(CdsExportLaboratoryOccurrenceService.ROUTES))
                                routePasses++;
                            if (table.equals("hl7TextMessage")) messagePasses++;
                            hand(List.of(table), call.getArgument(9));
                            return generation;
                        });
        when(witnessReader.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            witnessPasses++;
                            beforeWitnesses.run();
                            for (var witness : witnesses.values())
                                call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE,
                                    Map.of("fixture", (long) witnesses.size()),
                                    witnesses.size(),
                                    0,
                                    Set.of());
                        });
        index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", units, "cds_import_field", Map.of()),
                        references.keySet(),
                        references,
                        Map.of());
        var checked = new HashMap<String, List<CdsExportRetainedReceiptVerificationDto.CheckedReference>>();
        references.forEach(
                (address, named) ->
                        checked.put(
                                address,
                                named.stream()
                                        .map(
                                                reference ->
                                                        new CdsExportRetainedReceiptVerificationDto
                                                                .CheckedReference(
                                                                reference,
                                                                reference.digest(),
                                                                CdsExportRetainedReceiptVerificationDto
                                                                        .Reason.UNCHANGED_ROW))
                                        .toList()));
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                SOURCE, checked, references.keySet(), Set.of()));
        var streams = mock(CdsExportRetainedSectionWriter.Values.class);
        doAnswer(
                        call -> {
                            call.<CdsExportRetainedValuesService.Work>getArgument(10)
                                    .use(index, List.copyOf(retained), streams);
                            return null;
                        })
                .when(values)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
        var command =
                new CdsExportCommand(
                        "laboratory-windows",
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
                        SOURCE,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                SOURCE,
                                "sender",
                                SOURCE,
                                SOURCE,
                                new CdsExportStorageBindingDto("primary", "primary", SOURCE)));
    }

    void hand(Collection<String> wanted, CdsExportCapturedTextRowsReader.Rows consumer)
            throws IOException {
        for (String table : wanted)
            for (var held : tables.getOrDefault(table, new TreeMap<>()).values()) {
                // A pass hands out its own copy of each row, as a read of the capture does.
                var row =
                        new CdsExportCapturedTextRowDto(
                                held.table(), held.recordKey(), new HashMap<>(held.fields()));
                if (handed != null && row.table().equals("hl7TextMessage"))
                    handed.add(new java.lang.ref.WeakReference<>(row));
                consumer.accept(row);
            }
    }

    /** The imported result's content: its laboratory, then the reviewers CARLOS appended. */
    CdsExportLaboratoryOccurrenceService service() {
        String[] current = {null};
        return new CdsExportLaboratoryOccurrenceService(
                values, verification, witnessReader, captures, rows, zones, (l, p, t, s, b, c) -> providers) {
            @Override
            Retained retained(
                    int patient,
                    String address,
                    CdsExportCapturedTextRowDto row,
                    CdsExportRetainedIndexDto index,
                    CdsExportRetainedReceiptVerificationDto checked,
                    List<CdsExportRetainedFieldDto> supplied,
                    CdsExportRetainedSectionWriter.Values streams,
                    CdsExportRunControl control) {
                current[0] = row.fields().get("id");
                return new Retained(List.of(), Set.of(fieldId(current[0])), List.of());
            }

            @Override
            CdsExportRetainedSectionWriter.Prepared content(
                    List<CdsExportRetainedFieldDto> fields,
                    CdsExportRetainedSectionWriter.Values streams,
                    List<CdsExportRetainedSectionWriter.Change> changes,
                    Runnable checkpoint) {
                String result = current[0];
                return new CdsExportRetainedSectionWriter.Prepared(
                        xml -> {
                            xml.element("cds", "LaboratoryName", "Imported " + result.substring(56));
                            xml.start("cds", "CollectionDateTime");
                            xml.element("cds_dt", "FullDate", "2020-02-28");
                            xml.end();
                            for (var change : changes) {
                                xml.start("cds", "ResultReviewer");
                                change.content().write(xml);
                                xml.end();
                            }
                            xml.start("cds", "ResultNormalAbnormalFlag");
                            xml.element("cds_dt", "resultNormalAbnormalFlagAsEnum", "N");
                            xml.end();
                        },
                        Set.of(fieldId(result)),
                        Set.of());
            }
        };
    }

    byte[] compose() throws Exception {
        return compose(true);
    }

    byte[] compose(boolean keep) throws Exception {
        capture();
        receipts.clear();
        rowPasses = witnessPasses = routePasses = messagePasses = 0;
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS);
        var output = new ByteArrayOutputStream();
        long[] count = {0};
        new CdsExportXmlComposer()
                .composeFromSource(
                        keep ? output : OutputStream.nullOutputStream(),
                        SOURCE,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        emitter -> {
                            emitter.emit(demographic());
                            result =
                                    service()
                                            .append(
                                                    lease,
                                                    patient,
                                                    PATIENT,
                                                    source.capture().tables().keySet(),
                                                    source,
                                                    new CdsExportStagedFileDto(
                                                            "4".repeat(64),
                                                            "RETAINED_PAYLOAD",
                                                            SOURCE,
                                                            1),
                                                    bounds,
                                                    control,
                                                    emitter,
                                                    receipt -> {
                                                        if (keep) receipts.add(receipt);
                                                        count[0]++;
                                                    });
                        },
                        ignored -> {},
                        1L << 40,
                        () -> {});
        assertThat(result.nativeRows()).isEqualTo(count[0]);
        if (keep)
            new CdsExportXmlValidator()
                    .validate(
                            new ByteArrayInputStream(output.toByteArray()),
                            "27",
                            selected,
                            "2026.08.0-test",
                            CdsLogicalProfile.ONTARIO_MD_5_2,
                            CdsExportXmlLimitsDto.defaults(),
                            ignored -> {},
                            () -> {});
        return output.toByteArray();
    }

    static CdsExportXmlComposer.Section demographic() {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(PATIENT, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                xml -> {
                    xml.start("cds", "Names");
                    xml.start("cds_dt", "LegalName");
                    xml.attribute("", "namePurpose", "L");
                    for (var part : List.of(Map.entry("FirstName", "GIV"), Map.entry("LastName", "FAMC"))) {
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

    static String nativeKey(String id) {
        return CdsImportKeys.hash("carlos-native-occurrence-v1", "hl7TextMessage", id);
    }
}
