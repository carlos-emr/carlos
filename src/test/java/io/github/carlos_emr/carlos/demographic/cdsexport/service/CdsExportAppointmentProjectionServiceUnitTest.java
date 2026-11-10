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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Joins/resource/control tests; actual authenticated storage and SQL are covered by capture/native suites. */
@Tag("unit")
@Tag("cds-export")
class CdsExportAppointmentProjectionServiceUnitTest {
    private static final Set<String> TABLES =
            Set.of(
                    "appointment",
                    "appointmentArchive",
                    "appointment_status",
                    "provider",
                    "clinical_timezone");
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagedFileDto manifest = mock(CdsExportStagedFileDto.class);

    @Test
    void shouldJoinOnlyTheSameCapturedGeneration_andCountEveryMappingDecision() throws Exception {
        var reader = fixture(false, false);
        var output = new ArrayList<CdsExportAppointmentMappingDto>();
        var result = project(reader, 10, 1L << 20, output);
        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.blockedRows()).isZero();
        assertThat(result.assessedFields()).isEqualTo(41);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst().value().status()).isEqualTo("Exact booked Ω");
        assertThat(output.getFirst().value().provider().ohipNumber()).isEqualTo("001234");
        assertThat(result.captureSha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void shouldReturnExplicitReviewForMissingProvider_ratherThanACompleteClinicalRow()
            throws Exception {
        var fields = CdsExportAppointmentMapperUnitTest.baseline();
        fields.put("provider_no", "MISSING");
        var reader = fixture(false, false, CdsExportAppointmentMapperUnitTest.appointment(fields));
        var output = new ArrayList<CdsExportAppointmentMappingDto>();
        var result = project(reader, 10, 1L << 20, output);
        assertThat(result.blockedRows()).isEqualTo(1);
        assertThat(output.getFirst().value()).isNull();
    }

    @Test
    void shouldBoundTheLookupIndex_byBothRowsAndBytes_beforeEmittingAnyAppointments()
            throws Exception {
        for (long bytes : List.of(1L, 1L << 20)) {
            var output = new ArrayList<CdsExportAppointmentMappingDto>();
            var reader = fixture(false, false);
            assertThatThrownBy(() -> project(reader, bytes == 1 ? 10 : 1, bytes, output))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_LOOKUP_RESOURCE_LIMIT");
            assertThat(output).isEmpty();
        }
    }

    @Test
    void shouldRefuseMixedGenerations_andARevokedFinalRead_withoutReturningASuccessReceipt()
            throws Exception {
        var output = new ArrayList<CdsExportAppointmentMappingDto>();
        assertThatThrownBy(() -> project(fixture(true, false), 10, 1L << 20, output))
                .hasMessage("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
        // Even after a callback, the caller must discard its private result when final source
        // authorization fails.
        output.clear();
        assertThatThrownBy(() -> project(fixture(false, true), 10, 1L << 20, output))
                .hasMessage("CDS_EXPORT_ACCESS_DENIED");
        assertThat(output).hasSize(1);
    }

    @Test
    void shouldRequireExplicitHistoryRepresentation_beforeEmittingCurrentBookings()
            throws Exception {
        var output = new ArrayList<CdsExportAppointmentMappingDto>();
        var reader =
                fixture(
                        false,
                        false,
                        CdsExportAppointmentMapperUnitTest.appointment(
                                CdsExportAppointmentMapperUnitTest.baseline()),
                        true);
        assertThatThrownBy(() -> project(reader, 10, 1L << 20, output))
                .hasMessage("CDS_EXPORT_APPOINTMENT_HISTORY_REQUIRES_REVIEW");
        assertThat(output).isEmpty();
    }

    @Test
    void shouldRequireAllFourIndependentlyCapturedInputs() {
        var reader = mock(CdsExportCapturedTextRowsReader.class);
        assertThatThrownBy(
                        () ->
                                new CdsExportAppointmentProjectionService(reader, zones())
                                        .project(
                                                lease,
                                                patient,
                                                Set.of("appointment"),
                                                manifest,
                                                1000,
                                                1L << 20,
                                                10,
                                                1L << 20,
                                                CdsExportAppointmentMapperUnitTest.ZONE,
                                                control,
                                                ignored -> {}))
                .hasMessage("CDS_EXPORT_APPOINTMENT_INPUTS_REQUIRED");
        verifyNoInteractions(reader);
    }

    @Test
    void anExplicitCallerZoneMustMatchTheCapturedZoneAndGeneration() throws Exception {
        for (var bound :
                List.of(
                        new CdsExportClinicalZoneService.Bound(
                                "a".repeat(64),
                                java.time.ZoneId.of("America/Vancouver"),
                                "fixture"),
                        new CdsExportClinicalZoneService.Bound(
                                "b".repeat(64),
                                CdsExportAppointmentMapperUnitTest.ZONE,
                                "fixture"))) {
            var reader = mock(CdsExportCapturedTextRowsReader.class);
            when(manifest.plaintextSha256()).thenReturn("a".repeat(64));
            var zones = mock(CdsExportClinicalZoneService.class);
            when(zones.read(
                            any(),
                            any(),
                            anySet(),
                            any(CdsExportStagedFileDto.class),
                            anyLong(),
                            anyLong(),
                            any()))
                    .thenReturn(bound);
            assertThatThrownBy(
                            () ->
                                    new CdsExportAppointmentProjectionService(reader, zones)
                                            .project(
                                                    lease,
                                                    patient,
                                                    TABLES,
                                                    manifest,
                                                    1000,
                                                    1L << 20,
                                                    10,
                                                    1L << 20,
                                                    CdsExportAppointmentMapperUnitTest.ZONE,
                                                    control,
                                                    ignored -> {}))
                    .hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_MISMATCH");
            verifyNoInteractions(reader);
        }
    }

    private CdsExportClinicalZoneService zones() {
        var zones = mock(CdsExportClinicalZoneService.class);
        try {
            when(zones.read(
                            any(),
                            any(),
                            anySet(),
                            any(CdsExportStagedFileDto.class),
                            anyLong(),
                            anyLong(),
                            any()))
                    .thenReturn(
                            new CdsExportClinicalZoneService.Bound(
                                    "a".repeat(64),
                                    CdsExportAppointmentMapperUnitTest.ZONE,
                                    "fixture"));
        } catch (java.io.IOException failed) {
            throw new AssertionError(failed);
        }
        return zones;
    }

    private CdsExportAppointmentProjectionDto project(
            CdsExportCapturedTextRowsReader reader,
            int maximumRows,
            long maximumBytes,
            List<CdsExportAppointmentMappingDto> output)
            throws Exception {
        return new CdsExportAppointmentProjectionService(reader, zones())
                .project(
                        lease,
                        patient,
                        TABLES,
                        manifest,
                        1000,
                        1L << 20,
                        maximumRows,
                        maximumBytes,
                        CdsExportAppointmentMapperUnitTest.ZONE,
                        control,
                        output::add);
    }

    private CdsExportCapturedTextRowsReader fixture(boolean changed, boolean revoked)
            throws Exception {
        return fixture(
                changed,
                revoked,
                CdsExportAppointmentMapperUnitTest.appointment(
                        CdsExportAppointmentMapperUnitTest.baseline()));
    }

    private CdsExportCapturedTextRowsReader fixture(
            boolean changed, boolean revoked, CdsExportCapturedTextRowDto appointment)
            throws Exception {
        return fixture(changed, revoked, appointment, false);
    }

    private CdsExportCapturedTextRowsReader fixture(
            boolean changed,
            boolean revoked,
            CdsExportCapturedTextRowDto appointment,
            boolean history)
            throws Exception {
        when(patient.patient()).thenReturn(27);
        when(manifest.plaintextSha256()).thenReturn("a".repeat(64));
        var reader = mock(CdsExportCapturedTextRowsReader.class);
        var capture = mock(CdsExportNativeCaptureDto.class);
        var other = mock(CdsExportNativeCaptureDto.class);
        var tables =
                Map.of(
                        "appointmentArchive",
                        new CdsExportCapturedTableDto(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                                .columns()),
                                history ? 1 : 0,
                                "e".repeat(64)),
                        "appointment",
                        new CdsExportCapturedTableDto(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENTS
                                                .columns()),
                                1,
                                "b".repeat(64)),
                        "appointment_status",
                        new CdsExportCapturedTableDto(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_STATUSES
                                                .columns()),
                                1,
                                "c".repeat(64)),
                        "provider",
                        new CdsExportCapturedTableDto(
                                CdsExportProviderRowDto.COLUMNS, 1, "d".repeat(64)));
        when(capture.tables()).thenReturn(tables);
        when(other.tables()).thenReturn(tables);
        when(reader.forEachTable(
                        any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any(),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows rows = call.getArgument(9);
                            rows.accept(CdsExportAppointmentMapperUnitTest.provider());
                            rows.accept(CdsExportAppointmentMapperUnitTest.status());
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
                            CdsExportCapturedTextRowsReader.Rows rows = call.getArgument(9);
                            rows.accept(appointment);
                            if (revoked) throw new java.io.IOException("CDS_EXPORT_ACCESS_DENIED");
                            return changed ? other : capture;
                        });
        return reader;
    }
}
