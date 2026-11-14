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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportLaboratoryReceiptUnitTest {
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private CdsExportPatientCaptureDto source;
    private String table = "cds_laboratory_result", column = "id", id = HASH, key = HASH;
    private boolean metadata = true;

    private CdsNativeRowDigest.Column identity() {
        return new CdsNativeRowDigest.Column(
                column,
                table.equals("cds_laboratory_result") ? "CHAR" : "INT",
                table.equals("cds_laboratory_result") ? 64 : 10,
                0);
    }

    private void prepare() throws Exception {
        var columns = metadata ? List.of(identity()) : List.<CdsNativeRowDigest.Column>of();
        var plan = new CdsExportCapturedTableDto(Set.of(column), 1, HASH, columns);
        var actual = mock(CdsExportNativeCaptureDto.class);
        when(actual.tables()).thenReturn(Map.of(table, plan));
        when(actual.values())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(actual.index())
                .thenReturn(new CdsExportStagedFileDto("3".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        actual,
                        new CdsExportStagedFileDto("1".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(table),
                        10,
                        1024,
                        () -> {},
                        output -> {
                            output.beginTable(table, Set.of(column), 1);
                            output.row(key, fields -> fields.text(column, id));
                            output.endTable();
                        });
        doAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            Map.of(
                                                    table,
                                                    new CdsExportNativeCaptureReader.Table(
                                                            Set.of(column), 1)),
                                            10,
                                            1024,
                                            () -> {},
                                            call.getArgument(7));
                            return actual;
                        })
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    private CdsExportNativeRowReceiptDto read() throws Exception {
        var result = new ArrayList<CdsExportNativeRowReceiptDto>();
        var summary =
                new CdsExportNativeRowReceiptReader(captures)
                        .scan(
                                lease,
                                patient,
                                Set.of(table),
                                source,
                                10,
                                1024,
                                control,
                                result::add);
        assertThat(summary.rows()).containsEntry(table, 1L);
        assertThat(result).hasSize(1);
        return result.getFirst();
    }

    @Test
    void shouldReproduceTheStructuredResultDigest_withItsOriginalSha256Identity() throws Exception {
        prepare();
        var receipt = read();
        byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
        var expected = CdsNativeRowDigest.hasher(List.of(identity()));
        expected.append(
                false,
                bytes.length,
                HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
        assertThat(receipt.digest()).isEqualTo(expected.finish());
        assertThat(receipt.state().id()).isEqualTo(id);
        assertThat(receipt.partial()).isFalse();
    }

    @Test
    void shouldRecognizeEveryNativeHl7Target_withItsActualPrimaryKeyColumn() throws Exception {
        for (String value : List.of("hl7TextMessage", "hl7TextInfo", "patientLabRouting")) {
            table = value;
            column = table.equals("hl7TextMessage") ? "lab_id" : "id";
            id = "91";
            key = CdsExportPatientTableSourceReader.key(91);
            prepare();
            var receipt = read();
            assertThat(receipt.table()).isEqualTo(table);
            assertThat(receipt.id()).isEqualTo("91");
            assertThat(receipt.digest()).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void shouldRefuseAResultKey_forAnotherNativeIdentity() throws Exception {
        key = "b".repeat(64);
        prepare();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAUuidOrNumericIdentity_forTheStructuredDigestTarget() throws Exception {
        for (String value : List.of(UUID.randomUUID().toString(), "91")) {
            id = value;
            key = value;
            prepare();
            assertThatThrownBy(this::read)
                    .hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
        }
    }

    @Test
    void shouldRefuseANumericHl7Identity_withNoncanonicalSpelling() throws Exception {
        table = "hl7TextMessage";
        column = "lab_id";
        id = "091";
        key = CdsExportPatientTableSourceReader.key(91);
        prepare();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID");
    }

    @Test
    void shouldLeaveMissingDriverMetadataUnavailable_insteadOfClaimingAnUnchangedReceipt()
            throws Exception {
        metadata = false;
        prepare();
        var receipt = read();
        assertThat(receipt.digest()).isNull();
        assertThat(receipt.state()).isNull();
    }

    @Test
    void shouldRecheckLiveLaboratoryAuthority_afterTheLastReceiptCallback() throws Exception {
        prepare();
        doThrow(new SecurityException("REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, Set.of(table), control);
        assertThatThrownBy(this::read).hasMessage("REVOKED");
    }
}
