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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicAssemblerUnitTest {
    private static final String HASH = "a".repeat(64);
    private final CdsExportCapturedTextRowDto row =
            new CdsExportCapturedTextRowDto(
                    "demographic", "0000000027", Map.of("demographic_no", "27"));
    private final CdsExportNativeRowReceiptDto witness =
            new CdsExportNativeRowReceiptDto("demographic", "27", "0000000027", HASH);

    private CdsExportDemographicAssembler.NativeRows rows(
            List<CdsExportCapturedTextRowDto> properties,
            List<CdsExportNativeRowReceiptDto> propertyWitnesses,
            CdsExportCapturedTextRowDto custom,
            CdsExportNativeRowReceiptDto customWitness) {
        return new CdsExportDemographicAssembler.NativeRows(
                row,
                witness,
                properties,
                propertyWitnesses,
                custom,
                customWitness,
                List.of(),
                List.of(),
                new CdsExportPublicProviderLookupsDto(HASH, Map.of()),
                new CdsExportRetainedProfessionalContactReconciler.NativeRows(
                        HASH, List.of(), List.of(), List.of()),
                new CdsExportRetainedPharmacyReconciler.NativeRows(
                        HASH, List.of(), List.of(), List.of()));
    }

    @Test
    void shouldFreezeCallerOwnedCollections_beforeAValueScopeCanUseThem() {
        var source = new ArrayList<CdsExportCapturedTextRowDto>();
        source.add(row);
        var receipts = new ArrayList<CdsExportNativeRowReceiptDto>();
        receipts.add(witness);
        var rows = rows(source, receipts, null, null);
        source.clear();
        receipts.clear();
        assertThat(rows.properties()).containsExactly(row);
        assertThat(rows.propertyWitnesses()).containsExactly(witness);
        assertThatThrownBy(() -> rows.properties().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(rows.toString())
                .isEqualTo("CdsExportDemographicAssembler.NativeRows[protected]");
    }

    @Test
    void shouldRefuseUnpairedRowsAndWitnesses_beforeReconciliation() {
        assertThatThrownBy(() -> rows(List.of(row), List.of(), null, null))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ROW_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseOversizedCarriers_beforeCopyingTheirContents() {
        assertThatThrownBy(
                        () ->
                                rows(
                                        Collections.nCopies(8193, row),
                                        Collections.nCopies(8193, witness),
                                        null,
                                        null))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ROW_SCOPE_INVALID");
    }

    @Test
    void shouldRequireBothCustomRowAndWitness_insteadOfInventingAnAbsentNote() {
        assertThatThrownBy(() -> rows(List.of(), List.of(), row, null))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_NOTE_SCOPE_INVALID");
        assertThatThrownBy(() -> rows(List.of(), List.of(), null, witness))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_NOTE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseMissingDemographicSelection_beforeOpeningRetainedValues() {
        var values = mock(CdsExportRetainedSectionWriter.Values.class);
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicAssembler()
                                        .assemble(
                                                27,
                                                Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                                                "parent",
                                                null,
                                                null,
                                                List.of(),
                                                rows(List.of(), List.of(), null, null),
                                                100,
                                                1L << 20,
                                                values,
                                                () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_SCOPE_INVALID");
        org.mockito.Mockito.verifyNoInteractions(values);
    }

    @Test
    void shouldRefuseInvalidResourceBounds_beforeAnyCaptureEvidenceIsUsed() {
        for (long bytes : new long[] {0, (64L << 20) + 1}) {
            assertThatThrownBy(
                            () ->
                                    new CdsExportDemographicAssembler()
                                            .assemble(
                                                    27,
                                                    Set.of(CdsCategory.DEMOGRAPHICS),
                                                    "parent",
                                                    null,
                                                    null,
                                                    List.of(),
                                                    rows(List.of(), List.of(), null, null),
                                                    100,
                                                    bytes,
                                                    mock(
                                                            CdsExportRetainedSectionWriter.Values
                                                                    .class),
                                                    () -> {}))
                    .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_SCOPE_INVALID");
        }
    }

    @Test
    void shouldCloseEmptyCareTeamCarriers_withoutDroppingAnAlertHandoff() throws Exception {
        var values = new LinkedHashMap<String, String>();
        values.put("demographic_no", "27");
        values.put("cust1", null);
        values.put("cust2", "");
        values.put("cust4", "");
        values.put("cust3", "alert");
        var custom = new CdsExportCapturedTextRowDto("demographiccust", "0000000027", values);
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>();
        for (String column : List.of("cust1", "cust2", "cust3", "cust4"))
            deferred.add(
                    new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), column));
        var prior =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH, "parent", Set.of(), Set.of(), Map.of(), deferred, Set.of(),
                        List.of());
        var result = CdsExportDemographicAssembler.accountCareCarriers(prior, custom, () -> {});
        assertThat(result.fields()).hasSize(3);
        assertThat(result.deferredNativeFields())
                .containsExactly(
                        new CdsExportNativeFieldKeyDto(
                                custom.table(), custom.recordKey(), "cust3"));
        assertThat(result.demographicComplete()).isFalse();
    }

    @Test
    void shouldBlockAPopulatedCareTeamCarrier_andWithholdEarlierXmlChanges() throws Exception {
        var values =
                Map.of(
                        "demographic_no",
                        "27",
                        "cust1",
                        "Provider reference",
                        "cust2",
                        "",
                        "cust4",
                        "");
        var custom = new CdsExportCapturedTextRowDto("demographiccust", "0000000027", values);
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>();
        for (String column : List.of("cust1", "cust2", "cust4"))
            deferred.add(
                    new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), column));
        var prior =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH,
                        "parent",
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        deferred,
                        Set.of(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "ChartNumber[1]", Set.of(), xml -> xml.text("fixture"))));
        var result = CdsExportDemographicAssembler.accountCareCarriers(prior, custom, () -> {});
        assertThat(result.coreRepresentable()).isFalse();
        assertThat(result.proposedChanges()).isEmpty();
        assertThat(
                        result.fields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                custom.table(), custom.recordKey(), "cust1"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldRefuseAMissingCareCarrierHandoff_insteadOfTreatingItAsAbsent() {
        var custom =
                new CdsExportCapturedTextRowDto(
                        "demographiccust",
                        "0000000027",
                        Map.of("cust1", "", "cust2", "", "cust4", ""));
        var prior =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH, "parent", Set.of(), Set.of(), Map.of(), Set.of(), Set.of(),
                        List.of());
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicAssembler.accountCareCarriers(
                                        prior, custom, () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_CARE_SCOPE_INVALID");
    }

    @Test
    void shouldCloseOnlyAnExactlyAbsentSelectedLegacyAlertCarrier_forDemographicAssemblerContract() throws Exception {
        for (String value : new String[] {null, ""}) {
            var values = new LinkedHashMap<String, String>();
            values.put("cust3", value);
            var custom = new CdsExportCapturedTextRowDto("demographiccust", "0000000027", values);
            var key = new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), "cust3");
            var prior =
                    new CdsExportDemographicRetainedCoreReconciler.Result(
                            HASH,
                            "parent",
                            Set.of(),
                            Set.of(),
                            Map.of(),
                            Set.of(key),
                            Set.of(),
                            List.of());
            var result =
                    CdsExportDemographicAssembler.accountAlertCarrier(
                            prior,
                            custom,
                            Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                            () -> {});
            assertThat(result.demographicComplete()).isTrue();
            assertThat(result.deferredNativeFields()).isEmpty();
            assertThat(result.fields().get(key).outcome())
                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.ABSENT);
            assertThat(result.fields().get(key).presence())
                    .isEqualTo(
                            value == null
                                    ? CdsExportFieldAssessmentDto.Presence.SQL_NULL
                                    : CdsExportFieldAssessmentDto.Presence.EMPTY);
        }
    }

    @Test
    void shouldWithholdXml_forAnyPopulatedLegacyAlertCarrier() throws Exception {
        for (String value : List.of("Legacy patient alert", " ", "<unotes>café 😀</unotes>")) {
            var custom =
                    new CdsExportCapturedTextRowDto(
                            "demographiccust", "0000000027", Map.of("cust3", value));
            var key = new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), "cust3");
            var prior =
                    new CdsExportDemographicRetainedCoreReconciler.Result(
                            HASH,
                            "parent",
                            Set.of(),
                            Set.of(),
                            Map.of(),
                            Set.of(key),
                            Set.of(),
                            List.of(
                                    new CdsExportRetainedSectionWriter.Change(
                                            "NoteAboutPatient[1]",
                                            Set.of(),
                                            xml -> xml.text("preserved note"))));
            var result =
                    CdsExportDemographicAssembler.accountAlertCarrier(
                            prior,
                            custom,
                            Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                            () -> {});
            assertThat(result.demographicComplete()).isFalse();
            assertThat(result.proposedChanges()).isEmpty();
            assertThat(result.fields().get(key).outcome())
                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        }
    }

    @Test
    void shouldRequireTheSelectedAlertHandoffAndLeaveAnUnselectedProjectionUntouched_forDemographicAssemblerContract()
            throws Exception {
        var custom =
                new CdsExportCapturedTextRowDto(
                        "demographiccust", "0000000027", Map.of("content", "note"));
        var key = new CdsExportNativeFieldKeyDto(custom.table(), custom.recordKey(), "cust3");
        var prior =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH,
                        "parent",
                        Set.of(),
                        Set.of(),
                        Map.of(),
                        Set.of(key),
                        Set.of(),
                        List.of());
        assertThat(
                        CdsExportDemographicAssembler.accountAlertCarrier(
                                prior, custom, Set.of(CdsCategory.DEMOGRAPHICS), () -> {}))
                .isSameAs(prior);
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicAssembler.accountAlertCarrier(
                                        prior,
                                        custom,
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                                        () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ALERT_SCOPE_INVALID");
        var withValue =
                new CdsExportCapturedTextRowDto(
                        "demographiccust", "0000000027", Map.of("cust3", ""));
        var noHandoff =
                new CdsExportDemographicRetainedCoreReconciler.Result(
                        HASH, "parent", Set.of(), Set.of(), Map.of(), Set.of(), Set.of(),
                        List.of());
        assertThatThrownBy(
                        () ->
                                CdsExportDemographicAssembler.accountAlertCarrier(
                                        noHandoff,
                                        withValue,
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                                        () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ASSEMBLY_ALERT_SCOPE_INVALID");
    }
}
