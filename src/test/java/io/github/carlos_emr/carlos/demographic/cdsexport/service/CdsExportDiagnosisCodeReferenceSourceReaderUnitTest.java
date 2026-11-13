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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportDiagnosisCodeReadSupport;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDiagnosisCodeReferenceDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * The logical code-table capture is a reviewed TRUSTED_PUBLIC_REFERENCE recipe over the fixed
 * allowlist: its rows are written in key order and its count equals what it captures.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDiagnosisCodeReferenceSourceReaderUnitTest {
    private final DxresearchDAO source = mock(DxresearchDAO.class);
    private final CdsExportDiagnosisCodeReferenceSourceReader reader =
            new CdsExportDiagnosisCodeReferenceSourceReader(source);

    @Test
    void shouldMatchItsReviewedSchemaContract_forTrustedPublicReference() throws Exception {
        assertThat(new CdsExportSourceSchemaValidator().verify(reader.schema(), reader.columns()))
                .matches("[0-9a-f]{64}");
        assertThat(reader.table()).isEqualTo("diagnosis_code_reference");
        assertThat(reader.categories()).containsExactly(CdsCategory.PROBLEM_LIST);
        assertThat(reader.requiredTables()).isEqualTo(CdsExportProblemRegistrySources.TABLES);
        assertThat(reader.receiptColumns()).isEmpty();
    }

    @Test
    void shouldCaptureEveryMatchingRowInKeyOrder_withItsCountEqualToTheRowsWritten()
            throws Exception {
        var rows =
                List.of(
                        new CdsExportDiagnosisCodeReferenceDto(
                                "icd9", "2500", "icd9", "1301", "2500", "DIABETES MELLITUS"),
                        new CdsExportDiagnosisCodeReferenceDto(
                                "msp", "V03", "diagnosticcode", "10010", "V03", null));
        when(source.readCdsExportDiagnosisCodeReferences(27)).thenReturn(rows);
        assertThat(reader.count(27)).isEqualTo(2);
        var columns = new TreeSet<>(reader.columns());
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        1000,
                        1L << 20,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), columns, 2);
                            reader.capture(27, writer);
                            writer.endTable();
                        });
        assertThat(values.size()).isPositive();
        verify(source, times(2)).readCdsExportDiagnosisCodeReferences(27);
    }

    @Test
    void shouldRefuseTwoRowsWithOneKey_insteadOfCapturingAnAmbiguousDuplicate() {
        var row =
                new CdsExportDiagnosisCodeReferenceDto(
                        "icd9", "2500", "icd9", "1301", "2500", "DIABETES MELLITUS");
        when(source.readCdsExportDiagnosisCodeReferences(27)).thenReturn(List.of(row, row));
        assertThatThrownBy(() -> reader.count(27))
                .hasMessage("CDS_EXPORT_DIAGNOSIS_REFERENCE_INVALID");
    }

    @Test
    void shouldAuthorizeOnlyTheProblemListCategory_forReferenceReadScope() {
        var control = mock(CdsExportRunControl.class);
        reader.authorizeReadScope(27, control);
        verify(control).authorizeCategory(27, CdsCategory.PROBLEM_LIST, false);
        verifyNoMoreInteractions(control);
    }

    @Test
    void shouldAllowOnlyTheFourReviewedCodeTables_forAnyStoredSystemCase() {
        assertThat(CdsExportDiagnosisCodeReadSupport.Reference.of("icd9"))
                .isEqualTo(CdsExportDiagnosisCodeReadSupport.Reference.ICD9);
        assertThat(CdsExportDiagnosisCodeReadSupport.Reference.of("ICD10"))
                .isEqualTo(CdsExportDiagnosisCodeReadSupport.Reference.ICD10);
        assertThat(CdsExportDiagnosisCodeReadSupport.Reference.of("ichppccode").table())
                .isEqualTo("ichppccode");
        assertThat(CdsExportDiagnosisCodeReadSupport.Reference.of("msp").table())
                .isEqualTo("diagnosticcode");
        for (String unlisted : Arrays.asList("SnomedCore", "ichppc", "icd9 ", "icd9`", "", null))
            assertThat(CdsExportDiagnosisCodeReadSupport.Reference.of(unlisted)).isNull();
    }
}
