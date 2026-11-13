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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Synthetic {@code cds_import_unit} rows as the CARLOS importer writes them for one imported
 * vaccine: a protocol-2 receipt naming the parent prevention row and its extension, and a
 * native-baseline witness sealed under the given installation keys. No real patient data.
 */
final class CdsExportImmunizationImportReceiptFixture {
    static final String RUN = "123e4567-e89b-12d3-a456-426614174000";
    static final String PATIENT_KEY = "d".repeat(64);
    private static final ObjectMapper JSON = new ObjectMapper();

    private CdsExportImmunizationImportReceiptFixture() {}

    /** An applied Immunizations unit whose sealed witness records row {@code id} of the patient. */
    static Map<String, String> unit(int patient, int id, CdsNativeBaselineKeys keys)
            throws Exception {
        return unit(patient, id, "Immunizations", "COMMITTED", keys);
    }

    static Map<String, String> unit(
            int patient, int id, String category, String state, CdsNativeBaselineKeys keys)
            throws Exception {
        return unit(RUN, patient, id, category, state, keys, true);
    }

    /** An applied Immunizations unit of the given import run, for an owned database fixture. */
    static Map<String, String> unit(String run, int patient, int id, CdsNativeBaselineKeys keys)
            throws Exception {
        return unit(run, patient, id, "Immunizations", "COMMITTED", keys, true);
    }

    /** A legacy unit: the receipt names the row, but no sealed witness was ever stored. */
    static Map<String, String> unwitnessed(int patient, int id) throws Exception {
        return unit(RUN, patient, id, "Immunizations", "COMMITTED", null, false);
    }

    /**
     * A database writer's forgery: an authentic unit for row {@code original} whose receipt is
     * rewritten to name row {@code forged}, with a recomputed digest and the original witness.
     */
    static Map<String, String> forged(
            int patient, int original, int forged, CdsNativeBaselineKeys keys) throws Exception {
        var authentic = unit(patient, original, keys);
        var states = states(patient, forged);
        String references = references(states);
        var unit = new LinkedHashMap<>(authentic);
        unit.put("target_references", references);
        unit.put("target_digest", sha(references));
        return unit;
    }

    private static Map<String, String> unit(
            String run,
            int patient,
            int id,
            String category,
            String state,
            CdsNativeBaselineKeys keys,
            boolean sealed)
            throws Exception {
        var states = states(patient, id);
        String references = references(states);
        var unit = skeleton(run, id, category, state);
        unit.put("target_references", references);
        unit.put("target_digest", sha(references));
        unit.put(
                "native_baseline",
                sealed ? CdsNativeBaselineCodec.seal(references, sha(references), states, keys) : null);
        return unit;
    }

    static CdsExportCapturedTextRowDto captured(Map<String, String> unit) {
        return new CdsExportCapturedTextRowDto("cds_import_unit", unit.get("id"), unit);
    }

    /** The same unit as the owning DAO's bounded page returns it, every value inline. */
    static CdsExportRawKeyRowDto raw(Map<String, String> unit) {
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        for (var entry : unit.entrySet()) {
            byte[] bytes =
                    entry.getValue() == null
                            ? null
                            : entry.getValue().getBytes(StandardCharsets.UTF_8);
            cells.put(
                    entry.getKey(),
                    new CdsExportRawCellDto(
                            bytes == null,
                            bytes == null ? 0 : bytes.length,
                            bytes == null
                                    ? CdsExportRawCellDto.EMPTY_SHA256
                                    : HexFormat.of()
                                            .formatHex(CdsImportKeys.digest().digest(bytes)),
                            bytes));
        }
        return new CdsExportRawKeyRowDto(unit.get("id"), cells);
    }

    private static LinkedHashMap<String, String> skeleton(
            String run, int id, String category, String state) {
        var unit = new LinkedHashMap<String, String>();
        for (String column : CdsExportPatientTableReadSupport.Plan.IMPORT_UNITS.columns())
            unit.put(column, null);
        String unitKey = sha("unit-" + id + "-" + category);
        unit.put("id", CdsImportKeys.hash(run, unitKey));
        unit.put("run_id", run);
        unit.put("unit_key", unitKey);
        unit.put("patient_key", PATIENT_KEY);
        unit.put("lineage_key", "e".repeat(64));
        unit.put("category", category);
        unit.put("ordinal", "1");
        unit.put("source_digest", "c".repeat(64));
        unit.put("spool_location", "/synthetic/" + run + "/" + id);
        unit.put("state", state);
        return unit;
    }

    /** The importer's parent prevention row and its cdsName extension, as receipted states. */
    private static List<CdsNativeRowState> states(int patient, int id) {
        var parent = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                .columns()
                .forEach(column -> parent.put(column, null));
        parent.put("id", Integer.toString(id));
        parent.put("demographic_no", Integer.toString(patient));
        parent.put("prevention_type", "OtherA");
        parent.put("provider_no", "");
        parent.put("deleted", "0");
        parent.put("never", "0");
        parent.put("refused", "0");
        parent.put("creator", "AUDIT_ACTOR");
        var extension = new TreeMap<String, String>();
        extension.put("id", Integer.toString(id + 1000));
        extension.put("prevention_id", Integer.toString(id));
        extension.put("keyval", "cdsName");
        extension.put("val", "Synthetic imported vaccine");
        return List.of(state("preventions", parent), state("preventionsExt", extension));
    }

    private static String references(List<CdsNativeRowState> states) throws Exception {
        var refs =
                states.stream()
                        .map(
                                value ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                value.table(), value.id(), value.digest()))
                        .toList();
        return JSON.writeValueAsString(Map.of("protocol", 2, "refs", refs));
    }

    private static CdsNativeRowState state(String table, Map<String, String> value) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(value).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String text = entry.getValue();
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length,
                            text == null ? null : sha(text)));
        }
        return new CdsNativeRowState(table, value.get("id"), columns, cells);
    }

    static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
