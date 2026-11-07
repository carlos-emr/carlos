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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;

/** Selected-column evidence must never upgrade an excluded alert into whole-row equivalence. */
@Tag("unit") @Tag("cds-export")
class CdsExportPartialRowComparatorUnitTest {
    private static final List<String> FULL=List.of("demographic_no", "cust1", "cust2", "cust3", "cust4", "content");
    private static final String SOURCE="a".repeat(64), RUN="22222222-2222-4222-8222-222222222222", UNIT="b".repeat(64), ADDRESS=RUN+":"+UNIT;
    @Test void verifiesExactlySelectedFieldsWhileKeepingTheAlertOmitted() throws Exception {
        var full=full();var current=project(full);var result=CdsExportPartialRowComparator.compare(full, current);
        assertThat(result.unchangedSelectedFields()).isTrue();assertThat(result.comparedColumns()).containsExactlyInAnyOrder("demographic_no", "cust1", "cust2", "cust4", "content");
        assertThat(result.omittedColumns()).containsExactly("cust3");assertThat(current.digest()).isNotEqualTo(full.digest());
        assertThat(result.toString()).doesNotContain("PRIVATE", "Patient");
    }
    @Test void detectsChangedClearedAndEmptySelectedValuesWithoutConfusingNullAndEmpty() throws Exception {
        var previous=full();var current=project(previous);
        for(String value:Arrays.asList("Changed café 😀", "", null)) {
            var result=CdsExportPartialRowComparator.compare(previous, edit(current, "content", "VARCHAR", value));
            assertThat(result.unchangedSelectedFields()).isFalse();assertThat(result.fields().changedColumns()).containsExactly("content");
        }
    }
    @Test void excludesAlertHashChangesFromSelectedFieldEquivalenceWithoutClaimingTheWholeRow() throws Exception {
        var previous=full();var changedAlert=edit(previous, "cust3", "VARCHAR", "Changed PRIVATE alert");
        var result=CdsExportPartialRowComparator.compare(changedAlert, project(previous));
        assertThat(result.unchangedSelectedFields()).isTrue();assertThat(result.omittedColumns()).containsExactly("cust3");
    }
    @Test void refusesTypeOrIncompleteBaselineSchemaInsteadOfMatchingHashesOnly() throws Exception {
        var previous=full();var current=edit(project(previous), "content", "MEDIUMTEXT", "Original patient note");
        assertThat(CdsExportPartialRowComparator.compare(previous, current).fields().schemaChanged()).isTrue();
        assertThat(CdsExportPartialRowComparator.compare(project(previous), project(previous)).fields().schemaChanged()).isTrue();
    }
    @Test void rejectsForeignPatientsTablesAndUnreviewedSubsets() throws Exception {
        var previous=full();var current=project(previous);
        assertThatThrownBy(()->CdsExportPartialRowComparator.compare(previous, new CdsNativeRowState("demographiccust", "28", current.columns(), current.cells())))
                .hasMessage("CDS_EXPORT_PARTIAL_ROW_COMPARISON_SCOPE_INVALID");
        assertThatThrownBy(()->CdsExportPartialRowComparator.compare(previous, new CdsNativeRowState("allergies", "27", current.columns(), current.cells())))
                .hasMessage("CDS_EXPORT_PARTIAL_ROW_COMPARISON_SCOPE_INVALID");
        assertThatThrownBy(()->CdsExportPartialRowComparator.compare(previous, previous)).hasMessage("CDS_EXPORT_PARTIAL_ROW_COMPARISON_SCOPE_INVALID");
    }
    @Test void requiresExplicitPartialFlagAndRejectsEveryOtherPartialTable() throws Exception {
        var current=project(full());
        assertThatThrownBy(()->new CdsExportNativeRowReceiptDto("demographiccust", "27", "key", current.digest(), current, false))
                .hasMessage("CDS_EXPORT_NATIVE_PARTIAL_RECEIPT_INVALID");
        var other=new CdsNativeRowState("allergies", "27", current.columns(), current.cells());
        assertThatThrownBy(()->new CdsExportNativeRowReceiptDto("allergies", "27", "key", other.digest(), other, true)).hasMessage("CDS_EXPORT_NATIVE_PARTIAL_RECEIPT_INVALID");
    }
    @Test void neverMarksAUnitUnchangedWhenOnlySelectedFieldsMatch() throws Exception {
        var previous=full();var checked=verify(previous, project(previous), true);
        assertThat(checked.unchangedUnits()).isEmpty();assertThat(checked.reviewUnits()).containsExactly(ADDRESS);
        var ref=checked.units().get(ADDRESS).getFirst();assertThat(ref.reason()).isEqualTo(Reason.RECEIPT_PARTIAL_PROJECTION);
        assertThat(ref.fields()).isNull();assertThat(ref.projection().unchangedSelectedFields()).isTrue();
        assertThat(ref.currentDigest()).isNotEqualTo(ref.expected().digest());
    }
    @Test void reportsChangedSelectedFieldWithoutCallingItAChangedWholeRow() throws Exception {
        var previous=full();var checked=verify(previous, edit(project(previous), "cust4", "VARCHAR", "Changed"), true).units().get(ADDRESS).getFirst();
        assertThat(checked.reason()).isEqualTo(Reason.RECEIPT_PARTIAL_PROJECTION);assertThat(checked.projection().fields().changedColumns()).containsExactly("cust4");
    }
    @Test void legacyMissingBaselineDoesNotInventSelectedFieldEvidence() throws Exception {
        var previous=full();var checked=verify(previous, project(previous), false).units().get(ADDRESS).getFirst();
        assertThat(checked.reason()).isEqualTo(Reason.RECEIPT_PARTIAL_PROJECTION);assertThat(checked.projection()).isNull();assertThat(checked.fields()).isNull();
    }
    @Test void countsPartialRowsSeparatelyFromWholeReceiptsAndMissingMetadata() {
        var counts=new CdsExportNativeRowReceiptsDto(SOURCE, Map.of("demographiccust", 1L), 0, 0, 1, Set.of());
        assertThat(counts.available()).isZero();assertThat(counts.unavailable()).isZero();assertThat(counts.partialRows()).isEqualTo(1);
        assertThatThrownBy(()->new CdsExportNativeRowReceiptsDto(SOURCE, Map.of("demographiccust", 1L), 1, 0, 1, Set.of())).hasMessage("CDS_EXPORT_NATIVE_ROW_RECEIPTS_INVALID");
    }
    @Test void cannotAttachPartialEvidenceToAWholeReceiptDecision() throws Exception {
        var previous=full();var current=project(previous);var projection=CdsExportPartialRowComparator.compare(previous, current);
        var ref=new CdsExportRetainedIndexDto.Reference("demographiccust", "27", current.digest());
        assertThatThrownBy(()->new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref, current.digest(), Reason.UNCHANGED_ROW, null, projection))
                .hasMessage("CDS_EXPORT_RETAINED_RECEIPT_CHECK_INVALID");
        var checked=new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref, current.digest(), Reason.RECEIPT_PARTIAL_PROJECTION, null, projection);
        assertThatThrownBy(()->new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(ADDRESS, List.of(checked)), Set.of(ADDRESS), Set.of()))
                .hasMessage("CDS_EXPORT_RETAINED_RECEIPT_VERIFICATION_INVALID");
    }
    private CdsExportRetainedReceiptVerificationDto verify(CdsNativeRowState previous, CdsNativeRowState current, boolean baseline)throws Exception {
        var rows=mock(CdsExportNativeRowReceiptReader.class);var captures=mock(CdsExportNativeCaptureService.class);var lease=mock(CdsExportLeaseDto.class);
        var patient=mock(CdsExportCohortService.Patient.class);var control=mock(CdsExportRunControl.class);var source=mock(CdsExportPatientCaptureDto.class);var manifest=mock(CdsExportStagedFileDto.class);
        when(source.manifest()).thenReturn(manifest);when(manifest.plaintextSha256()).thenReturn(SOURCE);
        when(rows.scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(call->{
            ((CdsExportNativeRowReceiptReader.Receipts)call.getArgument(7)).accept(new CdsExportNativeRowReceiptDto("demographiccust", "27", CdsExportPatientTableSourceReader.key(27), current.digest(), current, true));
            return new CdsExportNativeRowReceiptsDto(SOURCE, Map.of("demographiccust", 1L), 0, 0, 1, Set.of());
        });
        String references="{\"protocol\":2,\"refs\":[{\"table\":\"demographiccust\",\"id\":\"27\",\"digest\":\""+previous.digest()+"\"}]}";
        var fields=new HashMap<String, String>();fields.put("run_id", RUN);fields.put("unit_key", UNIT);fields.put("target_references", references);fields.put("target_digest", sha(references));
        if(baseline)fields.put("native_baseline", CdsNativeBaselineCodec.encode(references, sha(references), List.of(previous)));
        var row=new CdsExportCapturedTextRowDto("cds_import_unit", "c".repeat(64), fields);
        var index=new CdsExportRetainedIndexDto(SOURCE, 1, 4096, Map.of("cds_import_unit", Map.of(row.recordKey(), row)), Set.of(ADDRESS),
                Map.of(ADDRESS, List.of(new CdsExportRetainedIndexDto.Reference("demographiccust", "27", previous.digest()))), Map.of());
        return new CdsExportRetainedReceiptVerificationService(rows, captures).verify(lease, patient, Set.of("demographiccust"), source, index, 1000, 1L<<20, 100, control);
    }
    private CdsNativeRowState full()throws Exception {
        var columns=new ArrayList<CdsNativeRowDigest.Column>();var cells=new ArrayList<CdsNativeRowState.Cell>();
        for(String name:FULL){String text=name.equals("demographic_no")?"27":name.equals("cust3")?"PRIVATE ALERT":name.equals("content")?"Original patient note":null;
            columns.add(new CdsNativeRowDigest.Column(name, name.equals("demographic_no")?"INT":"VARCHAR", 100, 0));cells.add(cell(text));}
        return new CdsNativeRowState("demographiccust", "27", columns, cells);
    }
    private CdsNativeRowState project(CdsNativeRowState row) {
        var columns=new ArrayList<CdsNativeRowDigest.Column>();var cells=new ArrayList<CdsNativeRowState.Cell>();
        for(int i=0;i<row.columns().size();i++)if(!row.columns().get(i).name().equals("cust3")){columns.add(row.columns().get(i));cells.add(row.cells().get(i));}
        return new CdsNativeRowState(row.table(), row.id(), columns, cells);
    }
    private CdsNativeRowState edit(CdsNativeRowState row, String name, String type, String text)throws Exception {
        var columns=new ArrayList<>(row.columns());var cells=new ArrayList<>(row.cells());for(int i=0;i<columns.size();i++)if(columns.get(i).name().equals(name)){
            var column=columns.get(i);columns.set(i, new CdsNativeRowDigest.Column(name, type, column.precision(), column.scale()));cells.set(i, cell(text));}
        return new CdsNativeRowState(row.table(), row.id(), columns, cells);
    }
    private CdsNativeRowState.Cell cell(String text)throws Exception {return text==null?new CdsNativeRowState.Cell(true, 0, null):new CdsNativeRowState.Cell(false, text.getBytes(StandardCharsets.UTF_8).length, sha(text));}
    private String sha(String text)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
}
