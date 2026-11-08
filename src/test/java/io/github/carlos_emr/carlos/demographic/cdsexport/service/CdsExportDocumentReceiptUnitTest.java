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
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit") @Tag("cds-export")
class CdsExportDocumentReceiptUnitTest {
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private String owner = "73", document = "91", module = "demographic", key = CdsExportPatientTableSourceReader.key(91);
    private CdsExportPatientCaptureDto source;
    private void prepare() throws Exception {
        var columns = List.of(new CdsNativeRowDigest.Column("module_id", "INT", 10, 0), new CdsNativeRowDigest.Column("module", "VARCHAR", 30, 0), new CdsNativeRowDigest.Column("document_no", "INT", 20, 0), new CdsNativeRowDigest.Column("status", "CHAR", 1, 0));
        var names = Set.of("module_id", "module", "document_no", "status");
        var actual = mock(CdsExportNativeCaptureDto.class); when(actual.tables()).thenReturn(Map.of("ctl_document", new CdsExportCapturedTableDto(names, 1, HASH, columns)));
        when(actual.values()).thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_VALUES", HASH, 1)); when(actual.index()).thenReturn(new CdsExportStagedFileDto("3".repeat(64), "SOURCE_INDEX", HASH, 1));
        source = new CdsExportPatientCaptureDto(actual, new CdsExportStagedFileDto("1".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        var values = new ByteArrayOutputStream(); var index = new ByteArrayOutputStream(); new CdsExportNativeCaptureComposer().compose(values, index, Set.of("ctl_document"), 10, 1024, () -> {}, out -> {
            out.beginTable("ctl_document", names, 1); out.row(key, fields -> { fields.text("module_id", owner); fields.text("module", module); fields.text("document_no", document); fields.text("status", "A"); }); out.endTable();
        });
        doAnswer(call -> { new CdsExportNativeCaptureReader().scan(new ByteArrayInputStream(values.toByteArray()), new ByteArrayInputStream(index.toByteArray()), Map.of("ctl_document", new CdsExportNativeCaptureReader.Table(names, 1)), 10, 1024, () -> {}, call.getArgument(7)); return actual; }).when(captures).scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }
    private CdsExportNativeRowReceiptDto read() throws Exception { var results = new ArrayList<CdsExportNativeRowReceiptDto>(); new CdsExportNativeRowReceiptReader(captures).scan(lease, patient, Set.of("ctl_document"), source, 10, 1024, control, results::add); assertThat(results).hasSize(1); return results.getFirst(); }
    @Test void shouldRetainOriginalPatientDocumentCompositeIdentity_withWholeRowWitness() throws Exception {
        prepare(); var result = read(); assertThat(result.id()).isEqualTo("73_91"); assertThat(result.recordKey()).isEqualTo(key); assertThat(result.state().id()).isEqualTo("73_91"); assertThat(result.digest()).isEqualTo(result.state().digest()); assertThat(result.partial()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "073", "x"})
    void shouldRefuseNoncanonicalPatientIdentities(String value) throws Exception { owner = value; prepare(); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID"); }
    @ParameterizedTest @ValueSource(strings = {"provider", "Demographic", "demographic ", ""})
    void shouldRefuseAnotherModuleOrCollationAlias(String value) throws Exception { module = value; prepare(); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID"); }
    @Test void shouldRefuseARecordKeyForAnotherDocument() throws Exception { key = CdsExportPatientTableSourceReader.key(92); prepare(); assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPT_SCOPE_INVALID"); }
    @Test void shouldRecheckAuthorization_afterTheFinalLinkReceipt() throws Exception { prepare(); doThrow(new SecurityException("REVOKED")).when(captures).authorizeSources(lease, patient, Set.of("ctl_document"), control); assertThatThrownBy(this::read).hasMessage("REVOKED"); }
}
