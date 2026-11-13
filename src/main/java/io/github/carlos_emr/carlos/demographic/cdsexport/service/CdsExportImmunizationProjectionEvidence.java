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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact field witnesses and the reviewed immunization persister's extension recipe. These
 * helpers establish neither unchanged projection nor source precedence on their own. NULL
 * preparation fields mean no extension was written; they do not mean a SQL-NULL child row.
 */
final class CdsExportImmunizationProjectionEvidence {
    private CdsExportImmunizationProjectionEvidence() {}

    static Map<String, String> expectedExtensions(CdsImmunizationDto value) throws IOException {
        Objects.requireNonNull(value);
        new CdsExportPreparedTemporalMapper().map(value.date(), true);
        var result = new LinkedHashMap<String, String>();
        result.put("manufacture", value.manufacturer());
        result.put("lot", value.lot());
        result.put("route", value.route());
        result.put("location", value.site());
        result.put("dose", value.dose());
        result.put("comments", value.notes());
        result.put("instructions", value.instructions());
        result.put("cdsDateOriginal", value.date().original());
        result.put("cdsDatePrecision", value.date().precision().name());
        result.put("cdsCode", value.code());
        result.put("cdsCodeSystem", value.codeSystem());
        result.put("cdsType", value.type());
        result.put("cdsName", value.name());
        return Collections.unmodifiableMap(result);
    }

    static boolean matches(CdsNativeRowState state, String column, String expected)
            throws IOException {
        int at = -1;
        for (int i = 0; i < state.columns().size(); i++)
            if (state.columns().get(i).name().equals(column)) at = i;
        if (at < 0) scope();
        var cell = state.cells().get(at);
        if (CdsExactNumeric.codec(state.columns().get(at).type()) != null
                || cell.exactNumeric() != null) scope();
        if (expected == null) return cell.sqlNull();
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        return !cell.sqlNull()
                && cell.bytes() == bytes.length
                && cell.sha256()
                        .equals(
                                HexFormat.of()
                                        .formatHex(
                                                io.github.carlos_emr.carlos.utility.CdsImportKeys
                                                        .digest()
                                                        .digest(bytes)));
    }

    /**
     * Receipt identity stays physical partial_date; source-field identity keeps its explicit
     * prevention alias. Screening aliases are witnessed under their own logical names.
     */
    static void verifyCurrent(CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto receipt)
            throws IOException {
        if (row == null
                || receipt == null
                || receipt.state() == null
                || !row.exactNumbers().isEmpty()) scope();
        var columns =
                switch (row.table()) {
                    case "preventions", CdsExportPreventionSources.SCREENINGS ->
                            CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL.columns();
                    case "preventionsExt", CdsExportPreventionSources.SCREENING_EXTENSIONS ->
                            CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT.columns();
                    case "prevention_precision", CdsExportPreventionSources.SCREENING_PRECISION ->
                            CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION.columns();
                    default ->
                            throw new IOException(
                                    "CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
                };
        String physical = row.table().equals("prevention_precision") ? "partial_date" : row.table(),
                id = row.fields().get("id");
        try {
            int number = Integer.parseInt(id);
            if (!Integer.toString(number).equals(id)
                    || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                scope();
        } catch (NumberFormatException invalid) {
            scope();
        }
        if (!row.fields().keySet().equals(Set.copyOf(columns))
                || !receipt.table().equals(physical)
                || !receipt.id().equals(id)
                || !receipt.recordKey().equals(row.recordKey())
                || receipt.partial()
                || !receipt.state().table().equals(physical)
                || !receipt.state().id().equals(id)
                || !receipt.state().digest().equals(receipt.digest())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        receipt.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) scope();
        for (var column : receipt.state().columns())
            if (!matches(receipt.state(), column.name(), row.fields().get(column.name()))) scope();
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }
}
