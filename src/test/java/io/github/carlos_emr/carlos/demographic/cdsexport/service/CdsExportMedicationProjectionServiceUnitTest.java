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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTableDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Joins/bounds/orphan tests; real authenticated row transport is covered by capture/native suites. */
@Tag("unit")
@Tag("cds-export")
class CdsExportMedicationProjectionServiceUnitTest {
    private static final Set<String> TABLES = Set.of("drugs", "partial_date", "provider");
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagedFileDto manifest = mock(CdsExportStagedFileDto.class);

    @Test
    void shouldJoinOnlyTheCapturedParent_andReportAllNativeFieldDecisions() throws Exception {
        var reader =
                fixture(
                        List.of(date(11, 51), date(12, 52)),
                        List.of(medication(51), medication(52)),
                        false);
        var mappings =
                new ArrayList<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportMedicationMappingDto>();
        var result =
                new CdsExportMedicationProjectionService(reader)
                        .project(
                                lease,
                                patient,
                                TABLES,
                                manifest,
                                1000,
                                1L << 20,
                                10,
                                control,
                                mappings::add);
        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.blockedRows()).isZero();
        assertThat(result.assessedFields()).isEqualTo(138);
        assertThat(mappings).hasSize(2);
        assertThat(mappings.getFirst().value().written().precision())
                .isEqualTo(CdsExportDateDto.Precision.YEAR);
        assertThat(
                        mappings.get(1)
                                .value()
                                .fields()
                                .get(
                                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                .CdsExportMedicationDto.Field.DrugName))
                .isEqualTo("Synthetic medication 52");
    }

    @Test
    void shouldRefuseAnOrphanPrecisionRow_insteadOfIgnoringItAndClaimingACompleteJoin()
            throws Exception {
        var reader = fixture(List.of(date(11, 52)), List.of(medication(51)), false);
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationProjectionService(reader)
                                        .project(
                                                lease,
                                                patient,
                                                TABLES,
                                                manifest,
                                                1000,
                                                1L << 20,
                                                10,
                                                control,
                                                ignored -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldBoundThePrecisionIndex_withoutReturningPartialSuccess() throws Exception {
        var reader = fixture(List.of(date(11, 51), date(12, 51)), List.of(medication(51)), false);
        var mappings = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationProjectionService(reader)
                                        .project(
                                                lease,
                                                patient,
                                                TABLES,
                                                manifest,
                                                1000,
                                                1L << 20,
                                                1,
                                                control,
                                                ignored -> mappings.incrementAndGet()))
                .hasMessage("CDS_EXPORT_PRECISION_RESOURCE_LIMIT");
        assertThat(mappings).hasValue(0);
    }

    @Test
    void shouldRefuseDifferentCapturedGenerations_evenIfBothIndividualReadsComplete()
            throws Exception {
        var reader = fixture(List.of(date(11, 51)), List.of(medication(51)), true);
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationProjectionService(reader)
                                        .project(
                                                lease,
                                                patient,
                                                TABLES,
                                                manifest,
                                                1000,
                                                1L << 20,
                                                10,
                                                control,
                                                ignored -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldRequireAnIndependentlyCapturedPrecisionTable_beforeInterpretingDatesAsFull() {
        var reader = mock(CdsExportCapturedTextRowsReader.class);
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationProjectionService(reader)
                                        .project(
                                                lease,
                                                patient,
                                                Set.of("drugs"),
                                                manifest,
                                                1000,
                                                1L << 20,
                                                10,
                                                control,
                                                ignored -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_INPUTS_REQUIRED");
        verifyNoInteractions(reader);
    }

    private CdsExportCapturedTextRowsReader fixture(
            List<CdsExportCapturedTextRowDto> dates,
            List<CdsExportCapturedTextRowDto> medications,
            boolean changed)
            throws Exception {
        when(patient.patient()).thenReturn(27);
        when(manifest.plaintextSha256()).thenReturn("a".repeat(64));
        var reader = mock(CdsExportCapturedTextRowsReader.class);
        var capture = mock(CdsExportNativeCaptureDto.class);
        var other = mock(CdsExportNativeCaptureDto.class);
        var tables =
                Map.of(
                        "provider",
                        new CdsExportCapturedTableDto(
                                io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto
                                        .COLUMNS,
                                0,
                                "d".repeat(64)),
                        "drugs",
                        new CdsExportCapturedTableDto(
                                Set.copyOf(CdsExportPatientTableReadSupport.Plan.DRUGS.columns()),
                                medications.size(),
                                "b".repeat(64)),
                        "partial_date",
                        new CdsExportCapturedTableDto(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan
                                                .PARTIAL_DATE_MEDICATIONS
                                                .columns()),
                                dates.size(),
                                "c".repeat(64)));
        when(capture.tables()).thenReturn(tables);
        when(other.tables()).thenReturn(tables);
        when(reader.forEachTable(
                        any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any(),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            for (var row : dates) output.accept(row);
                            return capture;
                        });
        when(reader.forEach(
                        any(),
                        any(),
                        any(),
                        anyString(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            String table = call.getArgument(3);
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            for (var row : table.equals("partial_date") ? dates : medications)
                                output.accept(row);
                            return changed && table.equals("drugs") ? other : capture;
                        });
        return reader;
    }

    private static CdsExportCapturedTextRowDto medication(int id) {
        var values = CdsExportMedicationMapperUnitTest.baseline();
        values.put("drugid", Integer.toString(id));
        values.put("customName", "Synthetic medication " + id);
        values.put("written_date", "1965-01-01");
        return new CdsExportCapturedTextRowDto(
                "drugs", CdsExportPatientTableSourceReader.key(id), values);
    }

    private static CdsExportCapturedTextRowDto date(int id, int parent) {
        return new CdsExportCapturedTextRowDto(
                "partial_date",
                CdsExportPatientTableSourceReader.key(id),
                Map.of(
                        "id",
                        Integer.toString(id),
                        "table_name",
                        "2",
                        "table_id",
                        Integer.toString(parent),
                        "field_name",
                        "3",
                        "format",
                        "YYYY"));
    }
}
