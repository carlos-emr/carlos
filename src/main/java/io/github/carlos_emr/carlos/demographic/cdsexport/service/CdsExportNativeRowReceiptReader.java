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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Recomputes existing receipt digests from verified captured bytes, never today's database.
 * Only one row's small field headers and primary key enter memory; large values stream to
 * discard while their lengths/hashes/UTF-8 and both source EOFs remain mandatory. Every
 * callback stays private until this entire scan and final authorization complete.
 */
@Service
public class CdsExportNativeRowReceiptReader {
    @FunctionalInterface
    public interface Receipts {
        void accept(CdsExportNativeRowReceiptDto receipt) throws IOException;
    }

    private static final Map<String, String> KEYS =
            Map.ofEntries(
                    Map.entry("document", "document_no"),
                    Map.entry("ctl_document", "document_no"),
                    Map.entry("measurements", "id"),
                    Map.entry("measurementsExt", "id"),
                    Map.entry("measurementsDeleted", "id"),
                    Map.entry("measurementType", "id"),
                    Map.entry("cds_laboratory_result", "id"),
                    Map.entry("hl7TextMessage", "lab_id"),
                    Map.entry("hl7TextInfo", "id"),
                    Map.entry("patientLabRouting", "id"),
                    Map.entry("cds_import_file", "id"),
                    Map.entry("cds_import_file_reference", "id"),
                    Map.entry("Contact", "id"),
                    Map.entry("DemographicContact", "id"),
                    Map.entry("demographicPharmacy", "id"),
                    Map.entry("pharmacyInfo", "recordID"),
                    Map.entry("demographicArchive", "id"),
                    Map.entry("drugs", "drugid"),
                    Map.entry("demographic", "demographic_no"),
                    Map.entry("allergies", "allergyid"),
                    Map.entry("partial_date", "id"),
                    Map.entry("appointment", "appointment_no"),
                    Map.entry("demographicExt", "id"),
                    Map.entry("demographiccust", "demographic_no"),
                    Map.entry("cds_import_field", "id"),
                    Map.entry("casemgmt_issue_notes", "id"),
                    Map.entry("casemgmt_issue", "id"),
                    Map.entry("issue", "issue_id"),
                    Map.entry("casemgmt_note", "note_id"),
                    Map.entry("casemgmt_note_ext", "id"),
                    Map.entry("preventions", "id"),
                    Map.entry("preventionsExt", "id"),
                    Map.entry("prevention_precision", "id"),
                    // Screening aliases keep their logical names as witnesses, so a screening row
                    // can never be counted as, or matched to, a vaccine row of the same table.
                    Map.entry(CdsExportPreventionSources.SCREENINGS, "id"),
                    Map.entry(CdsExportPreventionSources.SCREENING_EXTENSIONS, "id"),
                    Map.entry(CdsExportPreventionSources.SCREENING_PRECISION, "id"));
    private final CdsExportNativeCaptureService captures;

    public CdsExportNativeRowReceiptReader(CdsExportNativeCaptureService captures) {
        this.captures = java.util.Objects.requireNonNull(captures);
    }

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
        java.util.Objects.requireNonNull(source);
        java.util.Objects.requireNonNull(output);
        patient.require(lease);
        if (!source.capture().tables().keySet().equals(requiredTables))
            throw new IOException("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
        try (var fields = new Fields(source.capture().tables(), output)) {
            var actual =
                    captures.scan(
                            lease,
                            patient,
                            requiredTables,
                            source.manifest(),
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            if (!actual.equals(source.capture()))
                throw new IOException("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
            fields.finishRow();
            var counts = new HashMap<String, Long>();
            var missing = new HashSet<String>();
            for (var entry : actual.tables().entrySet())
                if (KEYS.containsKey(entry.getKey())) {
                    if (fields.counts.getOrDefault(entry.getKey(), 0L) != entry.getValue().rows())
                        scope();
                    counts.merge(physical(entry.getKey()), entry.getValue().rows(), Math::addExact);
                    if (entry.getValue().receiptColumns().isEmpty())
                        missing.add(physical(entry.getKey()));
                }
            // finishRow emits the last private callback after the underlying scan.
            captures.authorizeSources(lease, patient, requiredTables, control);
            if (actual.tables().containsKey("casemgmt_note"))
                captures.authorizeCapturedSources(lease, patient, requiredTables, actual, control);
            patient.require(lease);
            control.checkpoint();
            return new CdsExportNativeRowReceiptsDto(
                    source.manifest().plaintextSha256(),
                    counts,
                    fields.available,
                    fields.unavailable,
                    fields.partialRows,
                    missing);
        }
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final Map<String, CdsExportCapturedTableDto> scopes;
        private final Receipts consumer;
        private final Map<String, CdsExportCapturedFieldDto> headers = new HashMap<>();
        private final Map<String, Long> counts = new HashMap<>();
        private String table, key, id, noteId;
        private long available, unavailable, partialRows;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream identity;
        private boolean closed;

        private Fields(Map<String, CdsExportCapturedTableDto> scopes, Receipts consumer) {
            this.scopes = Map.copyOf(scopes);
            this.consumer = consumer;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (closed || pending != null) scope();
            if (!KEYS.containsKey(field.table())) return OutputStream.nullOutputStream();
            if (!field.table().equals(table) || !field.recordKey().equals(key)) {
                finishRow();
                table = field.table();
                key = field.recordKey();
            }
            pending = field;
            if (KEYS.get(table).equals(field.column())
                    || table.equals("casemgmt_issue_notes") && field.column().equals("note_id")
                    || table.equals("ctl_document") && field.column().equals("module_id")) {
                if (field.kind() != CdsExportCapturedFieldDto.Kind.UTF8 || field.bytes() > 128)
                    scope();
                identity = new ByteArrayOutputStream((int) field.bytes());
                return identity;
            }
            return OutputStream.nullOutputStream();
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!KEYS.containsKey(field.table())) return;
            if (closed || pending != field || headers.putIfAbsent(field.column(), field) != null)
                scope();
            if (identity != null) {
                if (table.equals("casemgmt_issue_notes") && field.column().equals("note_id")
                        || table.equals("ctl_document") && field.column().equals("module_id"))
                    noteId = identity.toString(StandardCharsets.UTF_8);
                else id = identity.toString(StandardCharsets.UTF_8);
                identity = null;
            }
            pending = null;
        }

        private void finishRow() throws IOException {
            if (closed || pending != null) scope();
            if (table == null) return;
            var plan = scopes.get(table);
            if (plan == null
                    || !headers.keySet().equals(plan.columns())
                    || id == null
                    || !id.matches("[A-Za-z0-9_-]{1,64}")) scope();
            if (table.equals("cds_import_file")) {
                if (!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") || !id.equals(key))
                    scope();
            } else if (Set.of(
                            "cds_laboratory_result",
                            "cds_import_field",
                            "cds_import_file_reference")
                    .contains(table)) {
                if (!id.matches("[0-9a-f]{64}") || !id.equals(key)) scope();
            } else if (table.equals("ctl_document")) {
                try {
                    long patient = Long.parseLong(noteId), document = Long.parseLong(id);
                    var module = headers.get("module");
                    String expectedModule =
                            java.util.HexFormat.of()
                                    .formatHex(
                                            io.github.carlos_emr.carlos.utility.CdsImportKeys
                                                    .digest()
                                                    .digest(
                                                            "demographic"
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)));
                    if (patient < 1
                            || document < 1
                            || !Long.toString(patient).equals(noteId)
                            || !Long.toString(document).equals(id)
                            || !CdsExportPatientTableSourceReader.key(document).equals(key)
                            || module == null
                            || module.kind() != CdsExportCapturedFieldDto.Kind.UTF8
                            || module.bytes() != 11
                            || !expectedModule.equals(module.sha256())) scope();
                    id = noteId + "_" + id;
                } catch (NumberFormatException malformed) {
                    scope();
                }
            } else if (table.equals("casemgmt_issue_notes")) {
                try {
                    long note = Long.parseLong(noteId), issue = Long.parseLong(id);
                    if (note < 1
                            || issue < 1
                            || !Long.toString(note).equals(noteId)
                            || !Long.toString(issue).equals(id)
                            || !(CdsExportPatientTableSourceReader.key(note)
                                            + CdsExportPatientTableSourceReader.key(issue))
                                    .equals(key)) scope();
                    id = noteId + "_" + id;
                } catch (NumberFormatException malformed) {
                    scope();
                }
            } else {
                try {
                    long number = Long.parseLong(id);
                    if (!Long.toString(number).equals(id)) scope();
                    String expected =
                            table.equals("demographic")
                                    ? String.format(Locale.ROOT, "%010d", number)
                                    : CdsExportPatientTableSourceReader.key(number);
                    if (!expected.equals(key)) scope();
                } catch (NumberFormatException invalid) {
                    scope();
                }
            }
            String digest = null;
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState state = null;
            if (!plan.receiptColumns().isEmpty()) {
                var hash = CdsNativeRowDigest.hasher(plan.receiptColumns());
                var cells =
                        new java.util.ArrayList<
                                io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsNativeRowState.Cell>();
                for (var column : plan.receiptColumns()) {
                    var field = headers.get(column.name());
                    hash.append(
                            field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL,
                            field.bytes(),
                            field.sha256());
                    cells.add(
                            new io.github.carlos_emr.carlos.demographic.cds.contract
                                    .CdsNativeRowState.Cell(
                                    field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL,
                                    field.bytes(),
                                    field.kind() == CdsExportCapturedFieldDto.Kind.SQL_NULL
                                            ? null
                                            : field.sha256(),
                                    field.exactNumeric() == null
                                            ? null
                                            : field.exactNumeric().witness()));
                }
                digest = hash.finish();
                state =
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                                physical(table), id, plan.receiptColumns(), cells);
            } else unavailable++;
            boolean partial =
                    table.equals("demographiccust")
                            && !plan.columns().contains("cust3")
                            && state != null;
            if (state != null) {
                if (partial) partialRows++;
                else available++;
            }
            consumer.accept(
                    new CdsExportNativeRowReceiptDto(
                            physical(table), id, key, digest, state, partial));
            counts.merge(table, 1L, Math::addExact);
            table = null;
            key = null;
            id = null;
            noteId = null;
            headers.clear();
        }

        @Override
        public void close() {
            closed = true;
            headers.clear();
            identity = null;
            pending = null;
            id = null;
        }
    }

    /** Precision scopes partition rows of the same physical table; historical receipt identities remain unchanged. */
    private static String physical(String table) {
        return table.equals("prevention_precision") ? "partial_date" : table;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }
}
