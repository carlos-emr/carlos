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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact scalar row evidence for the finite case-management projections; no clinical value precedence is inferred. */
final class CdsExportCaseManagementProjectionEvidence {
    private CdsExportCaseManagementProjectionEvidence() {}

    static void verify(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (row == null
                || witness == null
                || witness.state() == null
                || witness.partial()
                || witness.digest() == null
                || !row.table().equals(witness.table())
                || !row.recordKey().equals(witness.recordKey())
                || !row.exactNumbers().isEmpty()) invalid();
        var plan = plan(row.table());
        String primary = primary(row.table());
        if (!row.fields().keySet().equals(Set.copyOf(plan.columns()))
                || !Objects.equals(
                        row.table().equals("casemgmt_issue_notes")
                                ? row.fields().get("note_id") + "_" + row.fields().get("id")
                                : row.fields().get(primary),
                        witness.id())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) invalid();
        try {
            if (row.table().equals("casemgmt_issue_notes")) {
                long note = Long.parseLong(row.fields().get("note_id")),
                        issue = Long.parseLong(row.fields().get("id"));
                if (note < 1
                        || issue < 1
                        || !Long.toString(note).equals(row.fields().get("note_id"))
                        || !Long.toString(issue).equals(row.fields().get("id"))
                        || !(CdsExportPatientTableSourceReader.key(note)
                                        + CdsExportPatientTableSourceReader.key(issue))
                                .equals(row.recordKey())) invalid();
            } else {
                long id = Long.parseLong(witness.id());
                if (id < 1
                        || !Long.toString(id).equals(witness.id())
                        || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey()))
                    invalid();
            }
        } catch (NumberFormatException malformed) {
            invalid();
        }
        for (var field : row.fields().entrySet())
            if (!matches(witness.state(), field.getKey(), field.getValue())) invalid();
    }

    static boolean matches(CdsNativeRowState state, String column, String value)
            throws IOException {
        int position = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) position = i;
        if (position < 0) invalid();
        var type = state.columns().get(position);
        var cell = state.cells().get(position);
        if (cell.exactNumeric() != null || CdsExactNumeric.codec(type.type()) != null) invalid();
        if (value == null) return cell.sqlNull();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    private static CdsExportPatientTableReadSupport.Plan plan(String table) throws IOException {
        return switch (table) {
            case "casemgmt_note" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTES;
            case "casemgmt_issue_notes" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTE_ISSUE_LINK;
            case "casemgmt_note_ext" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTE_EXT;
            case "casemgmt_note_link" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTE_LINK;
            case "casemgmt_issue" -> CdsExportPatientTableReadSupport.Plan.CASE_ISSUES;
            case "issue" -> CdsExportPatientTableReadSupport.Plan.ISSUE_CATALOGUE;
            default -> throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
        };
    }

    private static String primary(String table) {
        return switch (table) {
            case "casemgmt_note" -> "note_id";
            case "issue" -> "issue_id";
            default -> "id";
        };
    }

    /**
     * A row's identity as two numbers: its id and zero, or for a note-to-issue link the note and
     * the patient issue it joins.
     */
    record Key(long first, long second) {}

    /** What {@link #verify} compares of one row or one witness, reduced to its key and a digest. */
    record Reduced(Key key, byte[] digest) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Reduced reduced
                    && key.equals(reduced.key)
                    && Arrays.equals(digest, reduced.digest);
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + Arrays.hashCode(digest);
        }

        @Override
        public String toString() {
            return "CdsExportCaseManagementProjectionEvidence.Reduced[protected]";
        }
    }

    /**
     * Reduces a whole-row witness to its key and one SHA-256 over every cell {@link #verify}
     * compares (each column's name, null flag, byte length and digest, in column-name order), so
     * a chart read in windows keeps 48 bytes for a row instead of its receipt. Everything
     * {@link #verify} requires of the witness alone is checked here. A row {@link #reduce(
     * CdsExportCapturedTextRowDto) reduced} the same way is the row {@link #verify} accepts for
     * this witness exactly when the two reductions are equal.
     */
    static Reduced reduce(CdsExportNativeRowReceiptDto witness) throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || witness.digest() == null) invalid();
        plan(witness.table());
        var key = key(witness.table(), witness.id());
        if (!recordKey(witness.table(), key).equals(witness.recordKey())) invalid();
        var cells = new TreeMap<String, CdsNativeRowState.Cell>();
        var columns = witness.state().columns();
        for (int i = 0; i < columns.size(); i++) {
            var cell = witness.state().cells().get(i);
            if (cell.exactNumeric() != null
                    || CdsExactNumeric.codec(columns.get(i).type()) != null
                    || cells.putIfAbsent(columns.get(i).name(), cell) != null) invalid();
        }
        var digest = CdsImportKeys.digest();
        for (var cell : cells.entrySet()) {
            var value = cell.getValue();
            if (!value.sqlNull()
                    && (value.sha256() == null || !value.sha256().matches("[0-9a-f]{64}")))
                invalid();
            cell(digest, cell.getKey(), value.sqlNull(), value.bytes(), value.sha256());
        }
        return new Reduced(key, digest.digest());
    }

    /**
     * Reduces a captured row as {@link #reduce(CdsExportNativeRowReceiptDto)} reduces its
     * witness. Everything {@link #verify} requires of the row alone is checked here.
     */
    static Reduced reduce(CdsExportCapturedTextRowDto row) throws IOException {
        if (row == null || !row.exactNumbers().isEmpty()) invalid();
        var plan = plan(row.table());
        if (!row.fields().keySet().equals(Set.copyOf(plan.columns()))) invalid();
        String id =
                row.table().equals("casemgmt_issue_notes")
                        ? row.fields().get("note_id") + "_" + row.fields().get("id")
                        : row.fields().get(primary(row.table()));
        if (id == null) invalid();
        var key = key(row.table(), id);
        if (!recordKey(row.table(), key).equals(row.recordKey())) invalid();
        var digest = CdsImportKeys.digest();
        var hash = CdsImportKeys.digest();
        for (var field : new TreeMap<>(row.fields()).entrySet()) {
            String value = field.getValue();
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            cell(
                    digest,
                    field.getKey(),
                    value == null,
                    value == null ? 0 : bytes.length,
                    value == null ? null : HexFormat.of().formatHex(hash.digest(bytes)));
        }
        return new Reduced(key, digest.digest());
    }

    /** The key a witness id states: canonical positive numbers only. */
    static Key key(String table, String id) throws IOException {
        if (id == null) invalid();
        try {
            if (table.equals("casemgmt_issue_notes")) {
                int split = id.indexOf('_');
                if (split < 1) invalid();
                long note = Long.parseLong(id.substring(0, split)),
                        issue = Long.parseLong(id.substring(split + 1));
                if (note < 1 || issue < 1 || !(note + "_" + issue).equals(id)) invalid();
                return new Key(note, issue);
            }
            long number = Long.parseLong(id);
            if (number < 1 || !Long.toString(number).equals(id)) invalid();
            return new Key(number, 0);
        } catch (NumberFormatException malformed) {
            throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
        }
    }

    private static String recordKey(String table, Key key) {
        return table.equals("casemgmt_issue_notes")
                ? CdsExportPatientTableSourceReader.key(key.first())
                        + CdsExportPatientTableSourceReader.key(key.second())
                : CdsExportPatientTableSourceReader.key(key.first());
    }

    private static void cell(
            java.security.MessageDigest digest,
            String column,
            boolean missing,
            long bytes,
            String sha256) {
        byte[] name = column.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(name.length).array());
        digest.update(name);
        digest.update((byte) (missing ? 1 : 0));
        if (missing) return;
        digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes).array());
        digest.update(sha256.getBytes(StandardCharsets.US_ASCII));
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
    }
}
