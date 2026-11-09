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
        var plan =
                switch (row.table()) {
                    case "casemgmt_note" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTES;
                    case "casemgmt_issue_notes" ->
                            CdsExportPatientTableReadSupport.Plan.CASE_NOTE_ISSUE_LINK;
                    case "casemgmt_note_ext" -> CdsExportPatientTableReadSupport.Plan.CASE_NOTE_EXT;
                    case "casemgmt_issue" -> CdsExportPatientTableReadSupport.Plan.CASE_ISSUES;
                    case "issue" -> CdsExportPatientTableReadSupport.Plan.ISSUE_CATALOGUE;
                    default ->
                            throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
                };
        String primary =
                switch (row.table()) {
                    case "casemgmt_note" -> "note_id";
                    case "issue" -> "issue_id";
                    default -> "id";
                };
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

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID");
    }
}
