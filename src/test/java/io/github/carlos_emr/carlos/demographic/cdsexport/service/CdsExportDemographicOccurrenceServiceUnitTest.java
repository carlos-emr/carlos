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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicOccurrenceServiceUnitTest {
    private static final String HASH = "a".repeat(64);
    private final String runId = UUID.randomUUID().toString();
    private final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
    private final CdsExportRetainedReceiptVerificationService verification = mock(CdsExportRetainedReceiptVerificationService.class);
    private final CdsExportNativeRowReceiptReader witnesses = mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportDemographicOccurrenceService service = new CdsExportDemographicOccurrenceService(values, verification, witnesses, captures, rows);
    private CdsExportXmlComposer.SectionEmitter emitter() throws Exception {
        var emitter = mock(CdsExportXmlComposer.SectionEmitter.class);
        doAnswer(call -> { ((CdsExportXmlComposer.Sections) call.getArgument(0)).write(emitter); return null; }).when(emitter).source(any());
        return emitter;
    }
    private CdsExportLeaseDto lease() {
        when(patient.patient()).thenReturn(27);
        var command = new CdsExportCommand("test", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(), List.of(27), Set.of(CdsCategory.DEMOGRAPHICS), "receiver", "audit");
        return new CdsExportLeaseDto(runId, 1, HASH, new CdsExportJobDocumentDto(2,
                new CdsExportActorDto("agent", "999998", null, null), command, HASH, "sender", HASH, HASH, new CdsExportStorageBindingDto("primary", "primary", HASH)));
    }
    private CdsExportPatientCaptureDto source(Map<String, CdsExportCapturedTableDto> tables) {
        var capture = mock(CdsExportNativeCaptureDto.class); when(capture.tables()).thenReturn(tables);
        when(capture.runId()).thenAnswer(call -> runId); when(capture.epoch()).thenReturn(1L); when(capture.patient()).thenReturn(27);
        when(capture.values()).thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(capture.index()).thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        return new CdsExportPatientCaptureDto(capture, new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
    }
    @Test void shouldRejectMissingNativeDomains_beforeOpeningAnySourceOrRetainedValues() throws Exception {
        var source = source(Map.of("demographic", new CdsExportCapturedTableDto(Set.of("demographic_no"), 1, HASH)));
        assertThatThrownBy(() -> service.append(lease(), patient, HASH, source.capture().tables().keySet(), source,
                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                mock(CdsExportRunControl.class), emitter(), receipt -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID");
        verifyNoInteractions(values, verification, witnesses, captures, rows);
    }
    @Test void shouldRejectInvalidPatientIdentity_beforeEmittingAnyXml() throws Exception {
        var source = source(Map.of("demographic", new CdsExportCapturedTableDto(Set.of("demographic_no"), 1, HASH)));
        assertThatThrownBy(() -> service.append(lease(), patient, "bad", source.capture().tables().keySet(), source,
                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                mock(CdsExportRunControl.class), emitter(), receipt -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID");
        verifyNoInteractions(values, verification, witnesses, captures, rows);
    }
    @Test void shouldEnforceEachTableRowBudget_beforeTheMergeOrClinicalSourceScan() throws Exception {
        var tables = new LinkedHashMap<String, CdsExportCapturedTableDto>();
        for (String table : List.of("demographic", "demographicExt", "demographiccust", "demographicArchive", "provider", "Contact", "DemographicContact", "pharmacyInfo", "demographicPharmacy"))
            tables.put(table, new CdsExportCapturedTableDto(Set.of("id"), table.equals("Contact") ? 11 : 0, HASH));
        var source = source(tables);
        assertThatThrownBy(() -> service.append(lease(), patient, HASH, tables.keySet(), source,
                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                mock(CdsExportRunControl.class), emitter(), receipt -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_RESOURCE_LIMIT");
        verifyNoInteractions(values, verification, witnesses, captures, rows);
    }    @Test void shouldPoisonTheWholeDocument_whenTheCallerSwallowsASourceScopeFailure() {
        var source = source(Map.of("demographic", new CdsExportCapturedTableDto(Set.of("demographic_no"), 1, HASH)));
        var output = new java.io.ByteArrayOutputStream();
        assertThatThrownBy(() -> new CdsExportXmlComposer().composeFromSource(output, HASH, HASH, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2,
                Set.of(CdsCategory.DEMOGRAPHICS), xml -> {
                    try {
                        service.append(lease(), patient, HASH, source.capture().tables().keySet(), source,
                                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                                mock(CdsExportRunControl.class), xml, receipt -> {});
                    } catch (java.io.IOException refused) { /* The composer must remain poisoned. */ }
                }, ignored -> {}, 1L << 20, () -> {})).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }
    @Test void shouldRefuseAnotherPatientCapture_beforeAnySourceRead() throws Exception {
        var tables = new LinkedHashMap<String, CdsExportCapturedTableDto>();
        for (String table : List.of("demographic", "demographicExt", "demographiccust", "demographicArchive", "provider", "Contact", "DemographicContact", "pharmacyInfo", "demographicPharmacy"))
            tables.put(table, new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        var source = source(tables); when(source.capture().patient()).thenReturn(28);
        assertThatThrownBy(() -> service.append(lease(), patient, HASH, tables.keySet(), source,
                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                mock(CdsExportRunControl.class), emitter(), receipt -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_OCCURRENCE_SCOPE_INVALID");
        verifyNoInteractions(values, verification, witnesses, captures, rows);
    }
    @Test void shouldRejectAChangedMergeGeneration_beforeOpeningRetainedValues() throws Exception {
        var tables = new LinkedHashMap<String, CdsExportCapturedTableDto>();
        for (String table : List.of("demographic", "demographicExt", "demographiccust", "demographicArchive", "provider", "Contact", "DemographicContact", "pharmacyInfo", "demographicPharmacy", "demographic_merged"))
            tables.put(table, new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        var source = source(tables); when(rows.forEach(any(), any(), any(), eq("demographic_merged"), any(), anyLong(), anyLong(), anyInt(), any(), any()))
                .thenReturn(mock(CdsExportNativeCaptureDto.class));
        assertThatThrownBy(() -> service.append(lease(), patient, HASH, tables.keySet(), source,
                new CdsExportStagedFileDto("4".repeat(64), "RETAINED_MANIFEST", HASH, 1), new CdsExportClinicalOccurrenceService.Bounds(100, 100, 100, 100, 10, 10),
                mock(CdsExportRunControl.class), emitter(), receipt -> {})).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID");
        verifyNoInteractions(values, verification, witnesses);
    }

}
