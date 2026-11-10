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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto.Reference;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Reference/mutation/alias/control checks; the encrypted native scanner is exercised by the capture suite. */
@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedReceiptVerificationServiceUnitTest {
    private static final String SOURCE = "a".repeat(64),
            OLD = "b".repeat(64),
            DIFFERENT = "c".repeat(64);
    private static final Set<String> TABLES = Set.of("allergies", "demographiccust");
    private final CdsExportNativeRowReceiptReader rows =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportPatientCaptureDto source = mock(CdsExportPatientCaptureDto.class);
    private final CdsExportStagedFileDto manifest = mock(CdsExportStagedFileDto.class);

    @BeforeEach
    void prepare() throws Exception {
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
                                            OLD));
                            output.accept(
                                    new CdsExportNativeRowReceiptDto(
                                            "demographiccust",
                                            "27",
                                            CdsExportPatientTableSourceReader.key(27),
                                            null));
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE,
                                    Map.of("allergies", 1L, "demographiccust", 1L),
                                    1,
                                    1,
                                    Set.of("demographiccust"));
                        });
    }

    @Test
    void
            shouldKeepDuplicateReferencesAndReplayAliasesDistinct_andNeverChooseTheNewOrOldClinicalValue()
                    throws Exception {
        var expected = new LinkedHashMap<String, List<Reference>>();
        expected.put("original", List.of(ref("allergies", "51", OLD), ref("allergies", "51", OLD)));
        expected.put("replay-alias", List.of(ref("allergies", "51", DIFFERENT)));
        expected.put("not-captured", List.of(ref("allergies", "99", OLD)));
        expected.put("no-adapter", List.of(ref("drugs", "101", OLD)));
        expected.put("no-metadata", List.of(ref("demographiccust", "27", OLD)));
        var result = verify(index(SOURCE, expected), 20);
        assertThat(result.unchangedUnits()).containsExactly("original");
        assertThat(result.reviewUnits())
                .containsExactlyInAnyOrder(
                        "replay-alias", "not-captured", "no-adapter", "no-metadata");
        assertThat(result.units().get("original"))
                .hasSize(2)
                .allMatch(ref -> ref.reason() == Reason.UNCHANGED_ROW);
        assertThat(result.units().get("replay-alias").getFirst().reason())
                .isEqualTo(Reason.CHANGED_ROW);
        assertThat(result.units().get("replay-alias").getFirst().currentDigest()).isEqualTo(OLD);
        assertThat(result.units().get("not-captured").getFirst().reason())
                .isEqualTo(Reason.ROW_NOT_IN_CAPTURE);
        assertThat(result.units().get("no-adapter").getFirst().reason())
                .isEqualTo(Reason.RECEIPT_ADAPTER_UNAVAILABLE);
        assertThat(result.units().get("no-metadata").getFirst().reason())
                .isEqualTo(Reason.RECEIPT_METADATA_UNAVAILABLE);
        assertThatThrownBy(() -> result.units().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.toString()).doesNotContain("51", "original");
        org.mockito.Mockito.verify(captures).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldRefuseDifferentSourceGenerations_orAnIncompleteAppliedUnitIndexBeforeScanning() {
        assertThatThrownBy(
                        () ->
                                verify(
                                        index(
                                                DIFFERENT,
                                                Map.of(
                                                        "unit",
                                                        List.of(ref("allergies", "51", OLD)))),
                                        10))
                .hasMessage("CDS_EXPORT_RETAINED_RECEIPT_SCOPE_INVALID");
        var incomplete =
                new CdsExportRetainedIndexDto(
                        SOURCE, 0, 0, Map.of(), Set.of("missing"), Map.of(), Map.of());
        assertThatThrownBy(() -> verify(incomplete, 10))
                .hasMessage("CDS_EXPORT_RETAINED_RECEIPT_SCOPE_INVALID");
        verifyNoInteractions(rows, captures);
    }

    @Test
    void shouldBoundAllReferences_includingDuplicatesWithoutProducingPartialVerification() {
        var expected =
                index(
                        SOURCE,
                        Map.of(
                                "unit",
                                List.of(ref("allergies", "51", OLD), ref("allergies", "51", OLD))));
        assertThatThrownBy(() -> verify(expected, 1))
                .hasMessage("CDS_EXPORT_RETAINED_RECEIPT_RESOURCE_LIMIT");
        verifyNoInteractions(rows, captures);
    }

    @Test
    void shouldRequireFreshFinalAuthority_evenAfterEveryReceiptMatched() {
        doThrow(new SecurityException("ACCESS_REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(
                        () ->
                                verify(
                                        index(
                                                SOURCE,
                                                Map.of(
                                                        "unit",
                                                        List.of(ref("allergies", "51", OLD)))),
                                        10))
                .hasMessage("ACCESS_REVOKED");
    }

    @Test
    void shouldPropagateCancellation_withoutReturningOrCachingAResult() {
        doThrow(new IllegalStateException("CDS_EXPORT_CANCELLED")).when(control).checkpoint();
        assertThatThrownBy(
                        () ->
                                verify(
                                        index(
                                                SOURCE,
                                                Map.of(
                                                        "unit",
                                                        List.of(ref("allergies", "51", OLD)))),
                                        10))
                .hasMessage("CDS_EXPORT_CANCELLED");
        verifyNoInteractions(rows, captures);
    }

    @Test
    void shouldCompareReplayBaselinesIndividually_whenDifferentFloatBitsShareOneLegacyDigest()
            throws Exception {
        var low =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric.Codec
                                .IEEE754_BINARY32_V1,
                        "3dcccccd");
        var high =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsExactNumeric(
                        low.codec(), "3dccccce");
        var columns =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column("takemin", "FLOAT", 12, 31));
        String displayHash =
                java.util.HexFormat.of()
                        .formatHex(
                                io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                        .digest(
                                                "0.1"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)));
        var before =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        "drugs",
                        "51",
                        columns,
                        List.of(
                                new io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsNativeRowState.Cell(
                                        false, 3, displayHash, low.witness())));
        var current =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        "drugs",
                        "51",
                        columns,
                        List.of(
                                new io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsNativeRowState.Cell(
                                        false, 3, displayHash, high.witness())));
        var legacy =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        "drugs",
                        "51",
                        columns,
                        List.of(
                                new io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsNativeRowState.Cell(false, 3, displayHash)));
        String refs =
                "{\"protocol\":2,\"refs\":[{\"table\":\"drugs\",\"id\":\"51\",\"digest\":\""
                        + before.digest()
                        + "\"}]}";
        String targetDigest =
                java.util.HexFormat.of()
                        .formatHex(
                                io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                        .digest(
                                                refs.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        var metadata = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var references = new LinkedHashMap<String, List<Reference>>();
        for (var entry : Map.of("a", before, "b", current, "c", legacy).entrySet()) {
            var fields = new LinkedHashMap<String, String>();
            fields.put("run_id", "run");
            fields.put("unit_key", entry.getKey());
            fields.put("target_references", refs);
            fields.put("target_digest", targetDigest);
            fields.put(
                    "native_baseline",
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec
                            .encode(refs, targetDigest, List.of(entry.getValue())));
            metadata.put(
                    entry.getKey(),
                    new CdsExportCapturedTextRowDto("cds_import_unit", entry.getKey(), fields));
            references.put("run:" + entry.getKey(), List.of(ref("drugs", "51", before.digest())));
        }
        references.put("no-baseline", List.of(ref("drugs", "51", before.digest())));
        doAnswer(
                        call -> {
                            ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                    .accept(
                                            new CdsExportNativeRowReceiptDto(
                                                    "drugs",
                                                    "51",
                                                    CdsExportPatientTableSourceReader.key(51),
                                                    current.digest(),
                                                    current));
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE, Map.of("drugs", 1L), 1, 0, Set.of());
                        })
                .when(rows)
                .scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", metadata),
                        references.keySet(),
                        references,
                        Map.of());
        var result = verify(index, 20);
        assertThat(result.units().get("run:a").getFirst().reason()).isEqualTo(Reason.CHANGED_ROW);
        assertThat(result.units().get("run:b").getFirst().reason()).isEqualTo(Reason.UNCHANGED_ROW);
        assertThat(result.units().get("run:c").getFirst().reason())
                .isEqualTo(Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED);
        assertThat(result.units().get("no-baseline").getFirst().reason())
                .isEqualTo(Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED);
    }

    private CdsExportRetainedReceiptVerificationDto verify(
            CdsExportRetainedIndexDto index, int maximum) throws Exception {
        return new CdsExportRetainedReceiptVerificationService(rows, captures)
                .verify(lease, patient, TABLES, source, index, 1000, 1L << 20, maximum, control);
    }

    private static Reference ref(String table, String id, String digest) {
        return new Reference(table, id, digest);
    }

    private static CdsExportRetainedIndexDto index(
            String hash, Map<String, List<Reference>> references) {
        return new CdsExportRetainedIndexDto(
                hash, 0, 0, Map.of(), references.keySet(), references, Map.of());
    }
}
