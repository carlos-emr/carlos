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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportCaseManagementProjectionEvidenceUnitTest {
    private static final String ERROR = "CDS_EXPORT_CASE_MANAGEMENT_PROJECTION_INVALID";

    private CdsExportCapturedTextRowDto row(CdsExportPatientTableReadSupport.Plan plan) {
        var fields = new LinkedHashMap<String, String>();
        for (String column : plan.columns()) fields.put(column, null);
        fields.put(
                switch (plan) {
                    case CASE_NOTES -> "note_id";
                    case ISSUE_CATALOGUE -> "issue_id";
                    default -> "id";
                },
                "91");
        return new CdsExportCapturedTextRowDto(
                plan.table(), CdsExportPatientTableSourceReader.key(91), fields);
    }

    private CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : row.fields().entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0));
            String value = field.getValue();
            byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
            cells.add(
                    new CdsNativeRowState.Cell(
                            value == null,
                            value == null ? 0 : bytes.length,
                            value == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    io.github.carlos_emr.carlos.utility
                                                            .CdsImportKeys.digest()
                                                            .digest(bytes))));
        }
        var state = new CdsNativeRowState(row.table(), "91", columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), "91", row.recordKey(), state.digest(), state);
    }

    @Test
    void shouldVerifyAllFourCompletePhysicalProjections_withoutNormalizingValues() {
        for (var plan :
                List.of(
                        CdsExportPatientTableReadSupport.Plan.CASE_NOTES,
                        CdsExportPatientTableReadSupport.Plan.CASE_NOTE_EXT,
                        CdsExportPatientTableReadSupport.Plan.CASE_ISSUES,
                        CdsExportPatientTableReadSupport.Plan.ISSUE_CATALOGUE)) {
            var row = row(plan);
            assertThatCode(
                            () ->
                                    CdsExportCaseManagementProjectionEvidence.verify(
                                            row, witness(row)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectEverySubstitutedNoteField_includingHistoryAndCredentials() {
        var original = row(CdsExportPatientTableReadSupport.Plan.CASE_NOTES);
        var witness = witness(original);
        for (String column : original.fields().keySet()) {
            var fields = new LinkedHashMap<>(original.fields());
            fields.put(column, "Different protected value");
            var changed =
                    new CdsExportCapturedTextRowDto(original.table(), original.recordKey(), fields);
            assertThatThrownBy(
                            () ->
                                    CdsExportCaseManagementProjectionEvidence.verify(
                                            changed, witness))
                    .hasMessage(ERROR);
        }
    }

    @Test
    void shouldDistinguishNullEmptyAndUnicode_withoutDisplayCoercion() throws Exception {
        var original = row(CdsExportPatientTableReadSupport.Plan.CASE_NOTE_EXT);
        var fields = new LinkedHashMap<>(original.fields());
        fields.put("value", "Exact 😀 & literal\r\n");
        var row = new CdsExportCapturedTextRowDto(original.table(), original.recordKey(), fields);
        CdsExportCaseManagementProjectionEvidence.verify(row, witness(row));
        assertThat(
                        CdsExportCaseManagementProjectionEvidence.matches(
                                witness(row).state(), "date_value", ""))
                .isFalse();
        assertThat(
                        CdsExportCaseManagementProjectionEvidence.matches(
                                witness(row).state(), "date_value", null))
                .isTrue();
    }

    @Test
    void shouldRejectMissingOrAdditionalColumns_beforeAcceptingAProjection() {
        var original = row(CdsExportPatientTableReadSupport.Plan.CASE_ISSUES);
        var witness = witness(original);
        var missing = new LinkedHashMap<>(original.fields());
        missing.remove("resolved");
        assertThatThrownBy(
                        () ->
                                CdsExportCaseManagementProjectionEvidence.verify(
                                        new CdsExportCapturedTextRowDto(
                                                original.table(), original.recordKey(), missing),
                                        witness))
                .hasMessage(ERROR);
        var extra = new LinkedHashMap<>(original.fields());
        extra.put("new_clinical_column", "Unreviewed");
        assertThatThrownBy(
                        () ->
                                CdsExportCaseManagementProjectionEvidence.verify(
                                        new CdsExportCapturedTextRowDto(
                                                original.table(), original.recordKey(), extra),
                                        witness))
                .hasMessage(ERROR);
    }

    @Test
    void shouldRejectChangedRowIdentity_andUnavailableWholeRowEvidence() {
        var original = row(CdsExportPatientTableReadSupport.Plan.CASE_NOTES);
        var witness = witness(original);
        assertThatThrownBy(
                        () ->
                                CdsExportCaseManagementProjectionEvidence.verify(
                                        new CdsExportCapturedTextRowDto(
                                                original.table(),
                                                CdsExportPatientTableSourceReader.key(92),
                                                original.fields()),
                                        witness))
                .hasMessage(ERROR);
        assertThatThrownBy(
                        () ->
                                CdsExportCaseManagementProjectionEvidence.verify(
                                        original,
                                        new CdsExportNativeRowReceiptDto(
                                                original.table(),
                                                "91",
                                                original.recordKey(),
                                                witness.digest())))
                .hasMessage(ERROR);
    }
}
