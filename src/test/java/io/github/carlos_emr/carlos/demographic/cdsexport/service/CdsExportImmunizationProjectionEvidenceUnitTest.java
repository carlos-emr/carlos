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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class CdsExportImmunizationProjectionEvidenceUnitTest {
    @Test
    void recipeMatchesAllThirteenExistingPersisterKeysWithoutInventingMissingRows()
            throws Exception {
        var source = source(CdsTemporalDto.unknown());
        var expected = CdsExportImmunizationProjectionEvidence.expectedExtensions(source);
        assertThat(expected).hasSize(13);
        assertThat(expected.get("manufacture")).isEqualTo("Maker");
        assertThat(expected.get("location")).isEqualTo("Left arm");
        assertThat(expected.get("comments")).isEqualTo("Notes");
        assertThat(expected.get("cdsDateOriginal")).isNull();
        assertThat(expected.get("cdsDatePrecision")).isEqualTo("UNKNOWN");
        assertThat(expected.get("lot")).isEmpty();
        assertThatThrownBy(() -> expected.put("comments", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void originalDateHintStaysOriginalWhilePreparedClinicalDateKeepsItsShift() throws Exception {
        var shifted =
                CdsTemporal.shift(
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"), 1, "Date");
        var source = source(shifted);
        var hints = CdsExportImmunizationProjectionEvidence.expectedExtensions(source);
        assertThat(hints.get("cdsDateOriginal")).isEqualTo("2024-02-29");
        assertThat(new CdsExportPreparedTemporalMapper().map(source.date(), true).lexical())
                .isEqualTo("2024-03-01");
    }

    @Test
    void offsetAndFractionHintsStayExactRatherThanUsingTheCurrentJvmZone() throws Exception {
        var date = CdsTemporal.parse(null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date");
        assertThat(
                        CdsExportImmunizationProjectionEvidence.expectedExtensions(source(date))
                                .get("cdsDateOriginal"))
                .isEqualTo("2024-02-29T12:34:56.1200-05:00");
    }

    @Test
    void exactUtf8RowWitnessAcceptsUnicodeCrLfAndEmptyButDistinguishesSqlNull() throws Exception {
        var row = extension("café 😀\r\n");
        var state = state(row, "preventionsExt");
        CdsExportImmunizationProjectionEvidence.verifyCurrent(row, receipt(row, state));
        assertThat(CdsExportImmunizationProjectionEvidence.matches(state, "val", "café 😀\r\n"))
                .isTrue();
        assertThat(CdsExportImmunizationProjectionEvidence.matches(state, "val", "café 😀\n"))
                .isFalse();
        var empty = extension("");
        assertThat(
                        CdsExportImmunizationProjectionEvidence.matches(
                                state(empty, "preventionsExt"), "val", null))
                .isFalse();
        var absent = extension(null);
        assertThat(
                        CdsExportImmunizationProjectionEvidence.matches(
                                state(absent, "preventionsExt"), "val", ""))
                .isFalse();
    }

    @Test
    void changedPayloadCannotBorrowAnOldWholeRowReceipt() throws Exception {
        var old = extension("Original");
        var row = extension("Edited");
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        row, receipt(old, state(old, "preventionsExt"))))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void precisionSourceAliasMustMatchTheExistingPhysicalReceiptIdentity() throws Exception {
        var row =
                new CdsExportCapturedTextRowDto(
                        "prevention_precision",
                        CdsExportPatientTableSourceReader.key(30),
                        Map.of(
                                "id",
                                "30",
                                "table_id",
                                "17",
                                "table_name",
                                "4",
                                "field_name",
                                "7",
                                "format",
                                "YYYY"));
        var state = state(row, "partial_date");
        CdsExportImmunizationProjectionEvidence.verifyCurrent(row, receipt(row, state));
        var falseIdentity = state(row, "prevention_precision");
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        row, receipt(row, falseIdentity)))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void aPartialRowProjectionCannotBeUsedAsAFullImmunizationWitness() throws Exception {
        var row = extension("Original");
        var full = receipt(row, state(row, "preventionsExt"));
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeRowReceiptDto(
                                        full.table(),
                                        full.id(),
                                        full.recordKey(),
                                        full.digest(),
                                        full.state(),
                                        true))
                .hasMessage("CDS_EXPORT_NATIVE_PARTIAL_RECEIPT_INVALID");
        var unavailable =
                new CdsExportNativeRowReceiptDto(
                        full.table(), full.id(), full.recordKey(), full.digest());
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        row, unavailable))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void foreignIdWrongKeyAndMissingFieldCannotBorrowAValidWitness() throws Exception {
        var row = extension("Original");
        var receipt = receipt(row, state(row, "preventionsExt"));
        var missing = new TreeMap<>(row.fields());
        missing.remove("keyval");
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        new CdsExportCapturedTextRowDto(
                                                row.table(), row.recordKey(), missing),
                                        receipt))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
        var wrongKey =
                new CdsExportCapturedTextRowDto(
                        row.table(), CdsExportPatientTableSourceReader.key(31), row.fields());
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.verifyCurrent(
                                        wrongKey, receipt))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void unknownColumnsAndUnqualifiedFloatMetadataCannotBecomeAStringProjection() throws Exception {
        var row = extension("Original");
        var original = state(row, "preventionsExt");
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.matches(
                                        original, "unknown", "Original"))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
        var bad =
                new CdsNativeRowState(
                        "preventionsExt",
                        "30",
                        List.of(new CdsNativeRowDigest.Column("val", "FLOAT", 12, 0)),
                        List.of(
                                original.cells()
                                        .get(
                                                original.columns().stream()
                                                        .map(CdsNativeRowDigest.Column::name)
                                                        .toList()
                                                        .indexOf("val"))));
        assertThatThrownBy(
                        () ->
                                CdsExportImmunizationProjectionEvidence.matches(
                                        bad, "val", "Original"))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    private static CdsImmunizationDto source(CdsTemporalDto date) {
        return new CdsImmunizationDto(
                "Name",
                "Td",
                "Maker",
                "",
                "IM",
                "Left arm",
                "0.5 mL",
                "00123456",
                "DIN",
                false,
                "Instructions",
                "Notes",
                date);
    }

    private static CdsExportCapturedTextRowDto extension(String value) {
        var fields = new TreeMap<String, String>();
        fields.put("id", "30");
        fields.put("prevention_id", "17");
        fields.put("keyval", "comments");
        fields.put("val", value);
        return new CdsExportCapturedTextRowDto(
                "preventionsExt", CdsExportPatientTableSourceReader.key(30), fields);
    }

    private static CdsNativeRowState state(CdsExportCapturedTextRowDto row, String physical) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(row.fields()).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            var bytes =
                    entry.getValue() == null
                            ? null
                            : entry.getValue().getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            bytes == null,
                            bytes == null ? 0 : bytes.length,
                            bytes == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    io.github.carlos_emr.carlos.utility
                                                            .CdsImportKeys.digest()
                                                            .digest(bytes))));
        }
        return new CdsNativeRowState(physical, row.fields().get("id"), columns, cells);
    }

    private static CdsExportNativeRowReceiptDto receipt(
            CdsExportCapturedTextRowDto row, CdsNativeRowState state) {
        return new CdsExportNativeRowReceiptDto(
                state.table(), state.id(), row.recordKey(), state.digest(), state, false);
    }
}
