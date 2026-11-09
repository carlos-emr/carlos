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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Field witness decisions only; captured row transport and real original receipt SQL have independent tests. */
@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedReceiptFieldComparisonUnitTest {
    private static final String SOURCE = "a".repeat(64),
            RUN = "11111111-1111-4111-8111-111111111111",
            UNIT = "b".repeat(64),
            ADDRESS = RUN + ":" + UNIT;

    @Test
    void shouldReportTheChangedField_withoutChoosingEitherClinicalValue() throws Exception {
        var previous = row("VARCHAR", "old reaction");
        var current = row("VARCHAR", "current reaction");
        var checked = verify(previous, current, true).units().get(ADDRESS).getFirst();
        assertThat(checked.reason())
                .isEqualTo(CdsExportRetainedReceiptVerificationDto.Reason.CHANGED_ROW);
        assertThat(checked.fields().changedColumns()).containsExactly("reaction");
        assertThat(checked.fields().schemaChanged()).isFalse();
        assertThat(checked.fields().unqualifiedColumns()).isEmpty();
    }

    @Test
    void shouldKeepLegacyMissingBaselineExplicit_insteadOfInventingFieldEquivalence()
            throws Exception {
        var previous = row("VARCHAR", "old reaction");
        var checked = verify(previous, previous, false).units().get(ADDRESS).getFirst();
        assertThat(checked.reason())
                .isEqualTo(CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
        assertThat(checked.fields()).isNull();
    }

    @Test
    void shouldRequireReviewForAnUnqualifiedNativeCodec_evenWhenTheSqlHashIsUnchanged()
            throws Exception {
        var previous = row("FLOAT", "1.00000");
        var result = verify(previous, previous, true);
        assertThat(result.unchangedUnits()).isEmpty();
        assertThat(result.reviewUnits()).containsExactly(ADDRESS);
        var checked = result.units().get(ADDRESS).getFirst();
        assertThat(checked.reason())
                .isEqualTo(
                        CdsExportRetainedReceiptVerificationDto.Reason
                                .RECEIPT_FIELD_CODEC_UNQUALIFIED);
        assertThat(checked.fields().unqualifiedColumns()).containsExactly("reaction");
    }

    private static CdsExportRetainedReceiptVerificationDto verify(
            CdsNativeRowState previous, CdsNativeRowState current, boolean baseline)
            throws Exception {
        var rows = mock(CdsExportNativeRowReceiptReader.class);
        var captures = mock(CdsExportNativeCaptureService.class);
        var lease = mock(CdsExportLeaseDto.class);
        var patient = mock(CdsExportCohortService.Patient.class);
        var control = mock(CdsExportRunControl.class);
        var source = mock(CdsExportPatientCaptureDto.class);
        var manifest = mock(CdsExportStagedFileDto.class);
        when(source.manifest()).thenReturn(manifest);
        when(manifest.plaintextSha256()).thenReturn(SOURCE);
        when(rows.scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            CdsExportNativeRowReceiptReader.Receipts output = call.getArgument(7);
                            output.accept(
                                    new CdsExportNativeRowReceiptDto(
                                            "allergies",
                                            "51",
                                            CdsExportPatientTableSourceReader.key(51),
                                            current.digest(),
                                            current));
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE, Map.of("allergies", 1L), 1, 0, Set.of());
                        });
        String references =
                "{\"protocol\":2,\"refs\":[{\"table\":\"allergies\",\"id\":\"51\",\"digest\":\""
                        + previous.digest()
                        + "\"}]}";
        var fields = new HashMap<String, String>();
        fields.put("run_id", RUN);
        fields.put("unit_key", UNIT);
        fields.put("target_references", references);
        fields.put("target_digest", sha(references));
        if (baseline)
            fields.put(
                    "native_baseline",
                    CdsNativeBaselineCodec.encode(references, sha(references), List.of(previous)));
        var unit = new CdsExportCapturedTextRowDto("cds_import_unit", "c".repeat(64), fields);
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        1,
                        4096,
                        Map.of("cds_import_unit", Map.of(unit.recordKey(), unit)),
                        Set.of(ADDRESS),
                        Map.of(
                                ADDRESS,
                                List.of(
                                        new CdsExportRetainedIndexDto.Reference(
                                                "allergies", "51", previous.digest()))),
                        Map.of());
        return new CdsExportRetainedReceiptVerificationService(rows, captures)
                .verify(
                        lease,
                        patient,
                        Set.of("allergies"),
                        source,
                        index,
                        1000,
                        1L << 20,
                        100,
                        control);
    }

    private static CdsNativeRowState row(String type, String value) throws Exception {
        return new CdsNativeRowState(
                "allergies",
                "51",
                List.of(
                        new CdsNativeRowDigest.Column("allergyid", "INT", 10, 0),
                        new CdsNativeRowDigest.Column("reaction", type, 100, 0)),
                List.of(
                        new CdsNativeRowState.Cell(false, 2, sha("51")),
                        new CdsNativeRowState.Cell(
                                false, value.getBytes(StandardCharsets.UTF_8).length, sha(value))));
    }

    private static String sha(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
