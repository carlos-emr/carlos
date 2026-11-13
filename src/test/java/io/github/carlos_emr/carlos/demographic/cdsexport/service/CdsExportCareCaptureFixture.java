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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * A patient's captured care tables as the authenticated capture and witness scans present them,
 * without composing an encrypted capture: rows table by table in record-key order, and one
 * native-row witness per row computed from its values. Counts every scan, so a test can tell a
 * chart read in one pass from one read window by window.
 */
final class CdsExportCareCaptureFixture {
    static final String HASH = "a".repeat(64);
    static final int PATIENT = 27;
    final CdsExportLeaseDto lease;
    final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    final CdsExportRunControl control = mock(CdsExportRunControl.class);
    final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    final CdsExportNativeRowReceiptReader witnesses = mock(CdsExportNativeRowReceiptReader.class);
    final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
    final CdsExportRetainedReceiptVerificationService verification =
            mock(CdsExportRetainedReceiptVerificationService.class);
    /** Captured rows by table, each table in record-key order. */
    final Map<String, SortedMap<String, CdsExportCapturedTextRowDto>> tables = new TreeMap<>();
    /** Witness overrides by {@code table:id}; every other row's witness is computed from it. */
    final Map<String, CdsExportNativeRowReceiptDto> witnessOverrides = new HashMap<>();
    int rowScans, witnessScans;
    private CdsExportPatientCaptureDto source;

    CdsExportCareCaptureFixture(Set<CdsCategory> categories) throws Exception {
        var command =
                new CdsExportCommand(
                        "care-windows",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(PATIENT),
                        categories,
                        "receiver",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        UUID.randomUUID().toString(),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
        when(patient.patient()).thenReturn(PATIENT);
        for (String table : CdsExportCapturedCareRowsReader.TABLES) tables.put(table, new TreeMap<>());
        add(demographic());
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        anySet(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            Set<String> selected = call.getArgument(3);
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            rowScans++;
                            for (var table : tables.entrySet())
                                if (selected.contains(table.getKey()))
                                    for (var row : table.getValue().values()) sink.accept(row);
                            return source().capture();
                        });
        when(witnesses.scan(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportNativeRowReceiptReader.Receipts sink = call.getArgument(7);
                            witnessScans++;
                            var counts = new HashMap<String, Long>();
                            for (var table : tables.values())
                                for (var row : table.values()) {
                                    String id = CdsExportCapturedCareRowsReader.id(row);
                                    var override = witnessOverrides.get(row.table() + ":" + id);
                                    sink.accept(override != null ? override : witness(row));
                                    counts.merge(row.table(), 1L, Long::sum);
                                }
                            long total = counts.values().stream().mapToLong(Long::longValue).sum();
                            return new CdsExportNativeRowReceiptsDto(
                                    source().manifest().plaintextSha256(),
                                    counts,
                                    total,
                                    0,
                                    Set.of());
                        });
        // The merge-scope check reads demographic_merged, which this chart does not have.
        when(captures.scan(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenAnswer(call -> source().capture());
    }

    void add(CdsExportCapturedTextRowDto row) {
        tables.get(row.table()).put(row.recordKey(), row);
        source = null;
    }

    /** The capture receipt of the rows added so far, with an empty demographic_merged table. */
    CdsExportPatientCaptureDto source() {
        if (source != null) return source;
        var scopes = new HashMap<String, CdsExportCapturedTableDto>();
        long fields = 0;
        for (var table : tables.entrySet()) {
            var columns = columns(table.getKey());
            scopes.put(
                    table.getKey(),
                    new CdsExportCapturedTableDto(columns, table.getValue().size(), HASH));
            fields += (long) columns.size() * table.getValue().size();
        }
        scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DEMOGRAPHIC_MERGES.columns()), 0, HASH));
        var now = Instant.EPOCH;
        source =
                new CdsExportPatientCaptureDto(
                        new CdsExportNativeCaptureDto(
                                2,
                                lease.runId(),
                                1,
                                PATIENT,
                                HASH,
                                now,
                                now,
                                scopes,
                                fields,
                                0,
                                file("1", "SOURCE_VALUES"),
                                file("2", "SOURCE_INDEX")),
                        file("3", "SOURCE_MANIFEST"));
        return source;
    }

    Set<String> requiredTables() {
        return Set.copyOf(source().capture().tables().keySet());
    }

    CdsExportCareOccurrenceService service() {
        return new CdsExportCareOccurrenceService(values, verification, witnesses, captures, rows);
    }

    CdsExportCapturedCareRowsReader reader() {
        return new CdsExportCapturedCareRowsReader(
                PATIENT,
                consumer -> {
                    rowScans++;
                    for (var table : tables.values())
                        for (var row : table.values()) consumer.accept(row);
                    return source().capture();
                },
                consumer ->
                        witnesses.scan(
                                lease, patient, requiredTables(), source(), 1, 1, control, consumer),
                source().capture(),
                source().manifest().plaintextSha256(),
                () -> {});
    }

    /** One CareElements composition of this chart, as the patient document service runs it. */
    record Composed(
            CdsExportClinicalOccurrenceService.Result result,
            CdsExportXmlComposer.Result xml,
            List<CdsExportClinicalOccurrenceService.Receipt> receipts) {}

    /**
     * Runs the care assembly through the real streaming composer, after the one Demographics
     * section every patient document starts with.
     */
    Composed compose(
            CdsExportCareOccurrenceService service,
            CdsExportClinicalOccurrenceService.Bounds bounds)
            throws java.io.IOException {
        String key = "c".repeat(64);
        var receipts = new ArrayList<CdsExportClinicalOccurrenceService.Receipt>();
        var result = new CdsExportClinicalOccurrenceService.Result[1];
        var xml =
                new CdsExportXmlComposer()
                        .composeFromSource(
                                java.io.OutputStream.nullOutputStream(),
                                HASH,
                                key,
                                "2026.11.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                lease.document().command().categories(),
                                emitter -> {
                                    emitter.emit(
                                            new CdsExportXmlComposer.Section(
                                                    new CdsExportIdentityDto(
                                                            key,
                                                            CdsCategory.DEMOGRAPHICS,
                                                            CdsImportKeys.hash(
                                                                    "carlos-native-occurrence-v1",
                                                                    "demographic",
                                                                    Integer.toString(PATIENT))),
                                                    writer -> {}));
                                    result[0] =
                                            service.append(
                                                    lease,
                                                    patient,
                                                    key,
                                                    requiredTables(),
                                                    source(),
                                                    file("4", "PAYLOAD_MANIFEST"),
                                                    bounds,
                                                    control,
                                                    emitter,
                                                    receipts::add);
                                },
                                disposition -> {},
                                64L << 30,
                                () -> {});
        return new Composed(result[0], xml, receipts);
    }

    static CdsExportClinicalOccurrenceService.Bounds bounds(int nativeRows) {
        return new CdsExportClinicalOccurrenceService.Bounds(
                100_000_000, 2L << 30, 1_000_000, 64L << 20, nativeRows, 131_072);
    }

    private static CdsExportStagedFileDto file(String digit, String purpose) {
        return new CdsExportStagedFileDto(digit.repeat(64), purpose, HASH, 0);
    }

    private static Set<String> columns(String table) {
        if (table.equals("demographic")) {
            var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            columns.add("demographic_no");
            return Set.copyOf(columns);
        }
        return Set.copyOf(
                switch (table) {
                    case "measurements" -> Plan.MEASUREMENTS_CURRENT.columns();
                    case "measurementsExt" -> Plan.MEASUREMENTS_EXT.columns();
                    case "measurementsDeleted" -> Plan.MEASUREMENTS_DELETED.columns();
                    default -> Plan.MEASUREMENT_TYPES.columns();
                });
    }

    static CdsExportCapturedTextRowDto demographic() {
        var fields = new HashMap<String, String>();
        for (String column : CdsExportDemographicRowDto.COLUMNS) fields.put(column, null);
        fields.put("demographic_no", Integer.toString(PATIENT));
        return new CdsExportCapturedTextRowDto(
                "demographic", String.format(Locale.ROOT, "%010d", PATIENT), fields);
    }

    /** A weight saved from a flowsheet: a standard CareElements value. */
    static CdsExportCapturedTextRowDto measurement(long id, String type, String value) {
        var fields = new HashMap<String, String>();
        Plan.MEASUREMENTS_CURRENT.columns().forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id", Long.toString(id),
                        "demographicNo", Integer.toString(PATIENT),
                        "type", type,
                        "dataField", value,
                        "measuringInstruction", "in kg",
                        "comments", "",
                        "dateObserved", "2020-02-28 05:00:00.0",
                        "dateEntered", "2020-02-28 05:01:00.0",
                        "providerNo", "999998",
                        "appointmentNo", "0"));
        return row(Plan.MEASUREMENTS_CURRENT.table(), id, fields);
    }

    /** EctDeleteData2Action's archive of an observation deleted in error. */
    static CdsExportCapturedTextRowDto deleted(long id, long original) {
        var fields = new HashMap<String, String>();
        Plan.MEASUREMENTS_DELETED.columns().forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id", Long.toString(id),
                        "originalId", Long.toString(original),
                        "demographicNo", Integer.toString(PATIENT),
                        "type", "WT",
                        "dataField", "800",
                        "measuringInstruction", "in kg",
                        "comments", "entered in error",
                        "dateObserved", "2020-02-28 05:00:00.0",
                        "dateEntered", "2020-02-28 05:01:00.0",
                        "dateDeleted", "2020-02-28 06:00:00.0"));
        fields.put("providerNo", "999998");
        return row(Plan.MEASUREMENTS_DELETED.table(), id, fields);
    }

    static CdsExportCapturedTextRowDto extension(long id, long owner, String key, String value) {
        return row(
                Plan.MEASUREMENTS_EXT.table(),
                id,
                Map.of(
                        "id",
                        Long.toString(id),
                        "measurement_id",
                        Long.toString(owner),
                        "keyval",
                        key,
                        "val",
                        value));
    }

    static CdsExportCapturedTextRowDto vocabulary(long id, String type) {
        var fields = new HashMap<String, String>();
        Plan.MEASUREMENT_TYPES.columns().forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id", Long.toString(id),
                        "type", type,
                        "typeDisplayName", type,
                        "typeDescription", type,
                        "measuringInstruction", "in kg",
                        "validation", "",
                        "createDate", "2020-01-01 00:00:00.0"));
        return row(Plan.MEASUREMENT_TYPES.table(), id, fields);
    }

    private static CdsExportCapturedTextRowDto row(
            String table, long id, Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                table, CdsExportPatientTableSourceReader.key(id), new HashMap<>(fields));
    }

    /** The native-row witness the capture records for exactly these values. */
    static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) throws Exception {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var column : new TreeSet<>(row.fields().keySet())) {
            columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
            String value = row.fields().get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    value.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        String id = CdsExportCapturedCareRowsReader.id(row);
        var state = new CdsNativeRowState(row.table(), id, columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), id, row.recordKey(), state.digest(), state);
    }
}
