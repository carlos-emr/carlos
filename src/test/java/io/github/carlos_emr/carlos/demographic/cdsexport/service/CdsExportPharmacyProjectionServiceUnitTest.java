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
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportPharmacyProjectionServiceUnitTest {
    private static final Set<String> TABLES=Set.of("demographicPharmacy", "pharmacyInfo");
    private static final String HASH="a".repeat(64);
    private final CdsExportNativeCaptureService captures=mock(CdsExportNativeCaptureService.class);
    private final CdsExportCapturedTextRowsReader rows=mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportLeaseDto lease=mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient=mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control=mock(CdsExportRunControl.class);
    private final CdsExportPatientCaptureDto source=mock(CdsExportPatientCaptureDto.class);
    private final CdsExportNativeCaptureDto captured=mock(CdsExportNativeCaptureDto.class);
    private final CdsExportStagedFileDto manifest=mock(CdsExportStagedFileDto.class);
    private final Map<String, CdsExportCapturedTableDto> tables=new HashMap<>();
    private final Map<String, List<CdsExportCapturedTextRowDto>> data=new HashMap<>();
    private CdsExportPharmacyProjectionService service;
    private CdsExportClinicalOccurrenceService.Bounds bounds=new CdsExportClinicalOccurrenceService.Bounds(10000, 1L<<20, 1000, 1L<<20, 100, 100);
    @BeforeEach void setup()throws Exception {
        when(patient.patient()).thenReturn(27); when(source.capture()).thenReturn(captured); when(source.manifest()).thenReturn(manifest); when(manifest.plaintextSha256()).thenReturn(HASH);
        for (String table:TABLES)tables.put(table, new CdsExportCapturedTableDto(Set.of("fixture"), 1, HASH)); when(captured.tables()).thenReturn(tables);
        data.put("demographicPharmacy", new ArrayList<>(List.of(CdsExportPharmacyMapperUnitTest.link(-1, 9)))); data.put("pharmacyInfo", new ArrayList<>(List.of(CdsExportPharmacyMapperUnitTest.pharmacy(9))));
        when(rows.forEach(any(), any(), anySet(), anyString(), any(), anyLong(), anyLong(), anyInt(), any(), any())).thenAnswer(call-> {CdsExportCapturedTextRowsReader.Rows sink=call.getArgument(9); for (var row:data.get(call.<String>getArgument(3)))sink.accept(row); return captured; });
        service=new CdsExportPharmacyProjectionService(captures, rows);
    }
    @Test void oneGenerationAndCompleteFieldEvidenceEndWithFreshAuthorization()throws Exception {var result=project(); assertThat(result.sourceManifestSha256()).isEqualTo(HASH); assertThat(result.mapping().nativeRepresentable()).isTrue(); assertThat(result.mapping().fields()).hasSize(22); var order=inOrder(rows, captures, patient, control); order.verify(rows, times(2)).forEach(any(), any(), anySet(), anyString(), any(), anyLong(), anyLong(), anyInt(), any(), any()); order.verify(captures).authorizeSources(lease, patient, TABLES, control); order.verify(patient).require(lease); order.verify(control).checkpoint(); }
    @Test void missingTableIsNotTheSameAsKnownZeroRows(){assertThatThrownBy(()-> service.project(lease, patient, Set.of("pharmacyInfo"), source, bounds, control)).hasMessage("CDS_EXPORT_PHARMACY_PROJECTION_SCOPE_INVALID"); verifyNoInteractions(rows); }
    @Test void incompleteRowsCannotBeMistakenForCompleteEof(){data.get("pharmacyInfo").clear(); assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_PHARMACY_PROJECTION_SCOPE_INVALID"); }
    @Test void aDifferentGenerationCannotBeJoined()throws Exception {when(rows.forEach(any(), any(), anySet(), eq("pharmacyInfo"), any(), anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(mock(CdsExportNativeCaptureDto.class)); assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_PHARMACY_PROJECTION_SCOPE_INVALID"); }
    @Test void finalRevocationCannotReturnAQualifiedProjection(){doThrow(new SecurityException("REVOKED")).when(captures).authorizeSources(lease, patient, TABLES, control); assertThatThrownBy(this::project).hasMessage("REVOKED"); }
    @Test void actualRowsAndBytesAreBounded(){bounds=new CdsExportClinicalOccurrenceService.Bounds(10000, 1L<<20, 1000, 1L<<20, 1, 100); assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT"); bounds=new CdsExportClinicalOccurrenceService.Bounds(10000, 1L<<20, 1000, 64, 100, 100); assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT"); }
    @Test void callbacksCannotHideAnUnderlyingEofFailure()throws Exception {when(rows.forEach(any(), any(), anySet(), eq("pharmacyInfo"), any(), anyLong(), anyLong(), anyInt(), any(), any())).thenAnswer(call-> {CdsExportCapturedTextRowsReader.Rows sink=call.getArgument(9); sink.accept(data.get("pharmacyInfo").getFirst()); throw new IOException("EOF_FAILED"); }); assertThatThrownBy(this::project).hasMessage("EOF_FAILED"); verifyNoInteractions(captures); }
    @Test void explicitlyCapturedEmptyTablesRemainDistinctFromMissingEvidence()throws Exception {for (String table:TABLES){data.get(table).clear(); tables.put(table, new CdsExportCapturedTableDto(Set.of("fixture"), 0, HASH)); }var result=project(); assertThat(result.mapping().nativeRepresentable()).isTrue(); assertThat(result.mapping().preferred()).isNull(); }
    private CdsExportPharmacyProjectionService.Result project()throws Exception{return service.project(lease, patient, TABLES, source, bounds, control); }
}
