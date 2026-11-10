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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportPatientInventoryServiceUnitTest {
    @TempDir Path root;

    private CdsExportPatientPipelineFixture fixture(CdsCategory... selected) throws Exception {
        var categories = EnumSet.of(CdsCategory.DEMOGRAPHICS);
        categories.addAll(List.of(selected));
        return new CdsExportPatientPipelineFixture(root, categories);
    }

    private CdsExportPatientInventoryService.Inventory capture(
            CdsExportPatientPipelineFixture fixture) throws IOException {
        return fixture.inventories.capture(
                fixture.lease,
                fixture.patient,
                "c".repeat(64),
                fixture.tables,
                fixture.source,
                fixture.bounds,
                fixture.control);
    }

    @Test
    void shouldCaptureExpectedNativeOccurrences_withoutInvokingMappersOrSerialization()
            throws Exception {
        var f = fixture(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "12", "12", CdsExportPatientPipelineFixture.HASH));
        var result = capture(f);
        assertThat(result.identities())
                .hasSize(3)
                .contains(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "allergies", "11")));
        verifyNoInteractions(f.demographics, f.clinical);
        assertThat(result.toString()).isEqualTo("CdsExportPatientInventory[protected]");
        assertThatThrownBy(() -> result.identities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldKeepDistinctRetainedOccurrences_withEqualClinicalValues() throws Exception {
        var f = fixture(CdsCategory.NEW_CATEGORY);
        f.unit(CdsCategory.NEW_CATEGORY, "d".repeat(64), List.of());
        f.unit(CdsCategory.NEW_CATEGORY, "e".repeat(64), List.of());
        assertThat(capture(f).identities()).hasSize(3);
    }

    @Test
    void shouldExcludeUnappliedRetainedUnits_withoutCreatingGhostOccurrences() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.unit(CdsCategory.REPORTS, "d".repeat(64), List.of());
        f.applied.clear();
        assertThat(capture(f).identities()).hasSize(1);
    }

    @Test
    void shouldRefuseAnUnclassifiedNativeNote_insteadOfOmittingIt() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldClassifyHistoricalNotes_fromTheirAppliedSourceUnit() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        f.unit(
                CdsCategory.CLINICAL_NOTES,
                "d".repeat(64),
                List.of(
                        new CdsExportRetainedIndexDto.Reference(
                                "casemgmt_note", "11", CdsExportPatientPipelineFixture.HASH)));
        assertThat(capture(f).identities()).hasSize(2);
    }

    @Test
    void shouldRefuseANoteAssigned_toCompetingSourceUnits() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        var refs =
                List.of(
                        new CdsExportRetainedIndexDto.Reference(
                                "casemgmt_note", "11", CdsExportPatientPipelineFixture.HASH));
        f.unit(CdsCategory.CLINICAL_NOTES, "d".repeat(64), refs);
        f.unit(CdsCategory.CLINICAL_NOTES, "e".repeat(64), refs);
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAnUnexpectedCategory_insteadOfLeakingItIntoTheInventory() throws Exception {
        var f = fixture();
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldRefuseDuplicateNativeIdentities_evenWhenWitnessesMatch() throws Exception {
        var f = fixture();
        f.nativeRows.add(f.nativeRows.getFirst());
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldRequireTheCurrentDemographic_forPatientInventoryServiceContract() throws Exception {
        var f = fixture();
        f.nativeRows.clear();
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldNotReturnInventory_afterFinalAuthorityRevocation() throws Exception {
        var f = fixture();
        doThrow(new SecurityException("REVOKED")).when(f.control).authorizePatient(27);
        assertThatThrownBy(() -> capture(f)).hasMessage("REVOKED");
    }
}
