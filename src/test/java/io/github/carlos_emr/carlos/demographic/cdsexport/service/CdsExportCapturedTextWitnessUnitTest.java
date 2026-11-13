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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsExportCapturedTextWitnessUnitTest {

    private static CdsExportCapturedTextRowDto row(String table, Map<String, String> values) {
        return new CdsExportCapturedTextRowDto(
                table, CdsExportPatientTableSourceReader.key(5), values);
    }

    /** A SELECT * witness of the row: every column except the ones named in {@code absent}. */
    private static CdsExportNativeRowReceiptDto witness(
            CdsExportCapturedTextRowDto row, Set<String> absent) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : row.fields().entrySet()) {
            if (absent.contains(entry.getKey())) continue;
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            false,
                            bytes.length,
                            HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes))));
        }
        var state = new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), state.id(), row.recordKey(), state.digest(), state);
    }

    private static Map<String, String> extension() {
        var values = new LinkedHashMap<String, String>();
        values.put("id", "5");
        values.put("demographic_no", "27");
        values.put("key_val", "cell");
        values.put("dashboard_single_value_key", "cell");
        values.put("value", "555-0100");
        return values;
    }

    @Test
    void shouldProveAMasterRecordExtension_withoutItsDerivedVirtualColumn() {
        var row = row("demographicExt", extension());
        var witness = witness(row, Set.of("dashboard_single_value_key"));

        assertThatCode(() -> CdsExportCapturedTextWitness.require(row, witness, "id", () -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldStillRefuseAnExtensionValueTheWitnessDoesNotProve() {
        var row = row("demographicExt", extension());
        var changed = new LinkedHashMap<>(extension());
        changed.put("value", "555-0199");
        var witness = witness(row("demographicExt", changed), Set.of("dashboard_single_value_key"));

        assertThatThrownBy(() -> CdsExportCapturedTextWitness.require(row, witness, "id", () -> {}))
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }

    @Test
    void shouldStillRefuseAnUnprovedColumn_onEveryOtherTable() {
        var values = new LinkedHashMap<String, String>();
        values.put("id", "5");
        values.put("demographic_no", "27");
        values.put("dashboard_single_value_key", "x");
        var row = row("demographiccust", values);
        var witness = witness(row, Set.of("dashboard_single_value_key"));

        assertThatThrownBy(() -> CdsExportCapturedTextWitness.require(row, witness, "id", () -> {}))
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }
}
