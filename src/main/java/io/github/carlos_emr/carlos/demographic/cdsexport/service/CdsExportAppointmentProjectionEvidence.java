/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact appointment scalar witnesses. No display-normalized comparison or current-setting recipe.
 */
final class CdsExportAppointmentProjectionEvidence {
    private CdsExportAppointmentProjectionEvidence() {}

    static boolean matches(CdsNativeRowState state, String name, String value) throws IOException {
        int found = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(name)) found = i;
        if (found < 0) invalid();
        var column = state.columns().get(found);
        var cell = state.cells().get(found);
        if (CdsExactNumeric.codec(column.type()) != null || cell.exactNumeric() != null) invalid();
        if (value == null) return cell.sqlNull();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }

    static void verifyCurrent(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (row == null
                || witness == null
                || witness.state() == null
                || witness.partial()
                || !row.table().equals("appointment")
                || !witness.table().equals("appointment")
                || !row.exactNumbers().isEmpty()
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENTS
                                                .columns()))
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))
                || !row.recordKey().equals(witness.recordKey())
                || !Objects.equals(row.fields().get("appointment_no"), witness.id())) invalid();
        try {
            int id = Integer.parseInt(witness.id());
            if (!Integer.toString(id).equals(witness.id())
                    || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey()))
                invalid();
        } catch (NumberFormatException failed) {
            invalid();
        }
        for (String name : row.fields().keySet())
            if (!matches(witness.state(), name, row.fields().get(name))) invalid();
    }

    /**
     * A pre-Location-List receipt may lack only the new nullable picker identity. Its original
     * digest is still checked by the caller. Compare the common physical fields only when the
     * captured added value is SQL NULL; no stored receipt is rewritten. A populated added value,
     * another missing column or any metadata change stays review.
     */
    static CdsNativeRowComparison compare(CdsNativeRowState original, CdsNativeRowState current) {
        var compatible = withoutAddedNullLocation(current);
        if (compatible != null
                && original.table().equals("appointment")
                && original.columns().equals(compatible.columns()))
            return CdsNativeRowComparison.compare(original, compatible);
        return CdsNativeRowComparison.compare(original, current);
    }

    /**
     * Derivation is evidence only when its exact old physical digest matches the recorded receipt.
     */
    static CdsNativeRowState legacyNullLocationBaseline(
            CdsNativeRowState current, String recordedDigest) {
        var compatible = withoutAddedNullLocation(current);
        return compatible != null && compatible.digest().equals(recordedDigest) ? compatible : null;
    }

    private static CdsNativeRowState withoutAddedNullLocation(CdsNativeRowState current) {
        var expected = Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns());
        var names = current.columns().stream().map(CdsNativeRowDigest.Column::name).toList();
        if (!current.table().equals("appointment") || !Set.copyOf(names).equals(expected))
            return null;
        int added = names.indexOf("locationCode");
        if (added < 0) return null; // This release has no Location List schema addition.
        if (!current.cells().get(added).sqlNull()
                || CdsExactNumeric.codec(current.columns().get(added).type()) != null) return null;
        var columns = new ArrayList<>(current.columns());
        var cells = new ArrayList<>(current.cells());
        columns.remove(added);
        cells.remove(added);
        return new CdsNativeRowState(current.table(), current.id(), columns, cells);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_PROJECTION_EVIDENCE_INVALID");
    }
}
