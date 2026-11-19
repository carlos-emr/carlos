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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

class CdsExportClinicalZoneServiceUnitTest {
    private static final String HASH = "a".repeat(64);
    private static final Set<String> TABLES = Set.of("clinical_timezone");
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private CdsExportClinicalZoneService service;
    private CdsExportNativeCaptureDto generation;
    private CdsExportPatientCaptureDto source;
    private List<CdsExportCapturedTextRowDto> data;
    private boolean complete, eofFailure, foreignGeneration;

    @BeforeEach
    void setup() throws Exception {
        generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables())
                .thenReturn(
                        Map.of(
                                "clinical_timezone",
                                new CdsExportCapturedTableDto(
                                        Set.of("sha256", "tzdbVersion", "zoneId"), 1, HASH)));
        when(generation.values()).thenReturn(file("1", "SOURCE_VALUES"));
        when(generation.index()).thenReturn(file("2", "SOURCE_INDEX"));
        source = new CdsExportPatientCaptureDto(generation, file("3", "SOURCE_MANIFEST"));
        var zone = CdsExportClinicalZone.capture("America/Toronto");
        data =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "clinical_timezone",
                                "configuration",
                                Map.of(
                                        "zoneId",
                                        zone.zoneId(),
                                        "tzdbVersion",
                                        zone.tzdbVersion(),
                                        "sha256",
                                        zone.sha256())));
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq("clinical_timezone"),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            for (var row : data)
                                ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                        .accept(row);
                            complete = true;
                            if (eofFailure) throw new IOException("ZONE_FINAL_EOF_FAILED");
                            return foreignGeneration
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : generation;
                        });
        service = new CdsExportClinicalZoneService(captures, rows);
    }

    @Test
    void shouldVerifyReturnsOnlyTheAuthenticatedGenerationAndChecksCurrentAuthority_afterEof() throws Exception {
        var result = read();
        assertThat(result.sourceManifestSha256()).isEqualTo(HASH);
        assertThat(result.zone()).isEqualTo(ZoneId.of("America/Toronto"));
        verify(captures, times(2)).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldVerifyManifestOnlyReaderStillRequiresTheSameCompleteZoneTable_forClinicalZoneServiceContract() throws Exception {
        assertThat(
                        service.read(
                                        lease,
                                        patient,
                                        TABLES,
                                        source.manifest(),
                                        1000,
                                        1L << 20,
                                        control)
                                .zone())
                .isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void shouldVerifyOldCapturesCannotFallBack_toASettingOrTheJvmZone() {
        assertThatThrownBy(
                        () ->
                                service.read(
                                        lease,
                                        patient,
                                        Set.of("appointment"),
                                        source,
                                        1000,
                                        1L << 20,
                                        control))
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID");
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyMissingAndDuplicateRowsNeverProduceAZone_forClinicalZoneServiceContract() {
        data = List.of();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID");
        try {
            setup();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        data = List.of(data.getFirst(), data.getFirst());
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID");
    }

    @Test
    void shouldVerifyAlteredGenerationAndLateSourceFailureCannotReturnABinding_forClinicalZoneServiceContract() {
        foreignGeneration = true;
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID");
        foreignGeneration = false;
        eofFailure = true;
        assertThatThrownBy(this::read).hasMessage("ZONE_FINAL_EOF_FAILED");
    }

    @Test
    void shouldVerifyFinalAuthorityRevocationCannotBecomeACompletedZoneRead_forClinicalZoneServiceContract() {
        doAnswer(
                        call -> {
                            if (complete) throw new SecurityException("ZONE_AUTHORITY_REVOKED");
                            return null;
                        })
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::read).hasMessage("ZONE_AUTHORITY_REVOKED");
    }

    @Test
    void shouldVerifyNullMetadataAndWrongIdentityCannotBecomeAUsableDefault_forClinicalZoneServiceContract() {
        var values = new HashMap<>(data.getFirst().fields());
        values.put("zoneId", null);
        data =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "clinical_timezone", "configuration", values));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CLINICAL_ZONE_INVALID");
        data =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "clinical_timezone", "not-configuration", values));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID");
    }

    @Test
    void shouldVerifyFinalCancellationKeepsItsOriginalMeaning_forClinicalZoneServiceContract() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(
                        call -> {
                            if (calls.incrementAndGet() == 2)
                                throw new java.util.concurrent.CancellationException(
                                        "ZONE_FINAL_CANCEL");
                            return null;
                        })
                .when(control)
                .checkpoint();
        assertThatThrownBy(this::read)
                .isInstanceOf(java.util.concurrent.CancellationException.class)
                .hasMessage("ZONE_FINAL_CANCEL");
    }

    private CdsExportClinicalZoneService.Bound read() throws Exception {
        return service.read(lease, patient, TABLES, source, 1000, 1L << 20, control);
    }

    private static CdsExportStagedFileDto file(String name, String purpose) {
        return new CdsExportStagedFileDto(name.repeat(64), purpose, HASH, 1);
    }
}
