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

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicPhoneProjectionServiceUnitTest {
    private static final Set<String> TABLES = Set.of("demographic", "provider", "demographicExt");
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportDemographicProjectionService demographic =
            mock(CdsExportDemographicProjectionService.class);
    private final CdsExportDemographicCaptureReader current =
            mock(CdsExportDemographicCaptureReader.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportPatientCaptureDto source = mock(CdsExportPatientCaptureDto.class);
    private final CdsExportNativeCaptureDto captured = mock(CdsExportNativeCaptureDto.class);
    private final CdsExportStagedFileDto manifest = mock(CdsExportStagedFileDto.class);
    private final Map<String, CdsExportCapturedTableDto> tables = new HashMap<>();
    private final List<CdsExportCapturedTextRowDto> properties = new ArrayList<>();
    private CdsExportDemographicPhoneProjectionService service;
    private CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    10000, 1L << 20, 1000, 1L << 20, 100, 100);

    @BeforeEach
    void setup() throws Exception {
        when(patient.patient()).thenReturn(27);
        when(source.capture()).thenReturn(captured);
        when(source.manifest()).thenReturn(manifest);
        when(manifest.plaintextSha256()).thenReturn(HASH);
        tables.put("demographic", new CdsExportCapturedTableDto(Set.of("fixture"), 1, HASH));
        tables.put("provider", new CdsExportCapturedTableDto(Set.of("fixture"), 0, HASH));
        count(2);
        when(captured.tables()).thenReturn(tables);
        var raw = CdsExportDemographicPhonesMapperUnitTest.raw();
        when(current.read(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(raw);
        when(demographic.project(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(new CdsExportDemographicMapper().map(raw));
        properties.add(CdsExportDemographicPhonesMapperUnitTest.property(1, "hPhoneExt", "12"));
        properties.add(CdsExportDemographicPhonesMapperUnitTest.property(2, "wPhoneExt", "5"));
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("demographicExt"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            for (var row : properties) output.accept(row);
                            return captured;
                        });
        service =
                new CdsExportDemographicPhoneProjectionService(
                        captures, demographic, current, rows);
    }

    @Test
    void shouldVerifyJoinsOnlyOneGenerationAndEnds_withFreshAuthorization() throws Exception {
        var result = project();
        assertThat(result.sourceManifestSha256()).isEqualTo(HASH);
        assertThat(result.propertyRows()).isEqualTo(2);
        assertThat(result.demographic().phones())
                .extracting(CdsExportDemographicMappingDto.Phone::extension)
                .containsExactly("12", "5");
        var order = inOrder(demographic, current, rows, captures, patient, control);
        order.verify(demographic)
                .project(any(), any(), anySet(), any(), anyLong(), anyLong(), any());
        order.verify(current).read(any(), any(), anySet(), any(), anyLong(), anyLong(), any());
        order.verify(rows)
                .forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("demographicExt"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any());
        order.verify(captures).authorizeSources(lease, patient, TABLES, control);
        order.verify(patient).require(lease);
        order.verify(control).checkpoint();
    }

    @Test
    void shouldVerifyIncompletePropertyEofCannotBeMistaken_forACompleteProjection() throws Exception {
        properties.removeLast();
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyAFinalRevocationCannotReturnSuccess_forDemographicPhoneProjectionServiceContract() throws Exception {
        doThrow(new SecurityException("REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::project).hasMessage("REVOKED");
    }

    @Test
    void shouldVerifyADifferentSourceGenerationIsRejected_forDemographicPhoneProjectionServiceContract() throws Exception {
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("demographicExt"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(mock(CdsExportNativeCaptureDto.class));
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyMissingTablesAreNotReportedAsKnownZeroRows_forDemographicPhoneProjectionServiceContract() {
        assertThatThrownBy(
                        () ->
                                service.project(
                                        lease,
                                        patient,
                                        Set.of("demographic", "provider"),
                                        source,
                                        bounds,
                                        control))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROJECTION_SCOPE_INVALID");
        verifyNoInteractions(demographic, current, rows);
    }

    @Test
    void shouldVerifyAnExplicitlyCapturedEmptyPropertyTableKeepsTheBaseNumbers_forDemographicPhoneProjectionServiceContract() throws Exception {
        properties.clear();
        count(0);
        var result = project();
        assertThat(result.propertyRows()).isZero();
        assertThat(result.demographic().phones())
                .hasSize(2)
                .allSatisfy(phone -> assertThat(phone.extension()).isNull());
    }

    @Test
    void shouldVerifyDeclaredAndActualResourceLimitsAreBothEnforced_forDemographicPhoneProjectionServiceContract() throws Exception {
        bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        10000, 1L << 20, 1000, 1L << 20, 1, 100);
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_RESOURCE_LIMIT");
        bounds = new CdsExportClinicalOccurrenceService.Bounds(10000, 1L << 20, 1000, 64, 100, 100);
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_RESOURCE_LIMIT");
    }

    @Test
    void shouldVerifyBaseNumbersCannotBeDetached_fromTheirRawCapturedGeneration() throws Exception {
        var raw = CdsExportDemographicPhonesMapperUnitTest.raw();
        var fields = new TreeMap<>(raw.fields());
        fields.put("phone", "A changed number");
        when(current.read(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(
                        new io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto(
                                27, fields));
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyUnrelatedPopulatedPropertiesRemainVisibleCarriedDecisions_forDemographicPhoneProjectionServiceContract() throws Exception {
        properties.clear();
        properties.add(
                CdsExportDemographicPhonesMapperUnitTest.property(
                        1, "UnmappedProperty", "Additional value"));
        count(1);
        var result = project();
        // Carried in the native demographic NewCategory record, never dropped or held.
        assertThat(result.demographic().baseRowRepresentable()).isTrue();
        assertThat(result.demographic().relatedFields()).hasSize(8);
        assertThat(result.demographic().relatedFields().values())
                .filteredOn(field -> CdsExportDemographicSupportingMapper.DESTINATION.equals(field.destination()))
                .hasSize(2);
        verify(captures).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldVerifyCallbackDataCannotHideAFailureAtTheEndOfTheUnderlyingRead_forDemographicPhoneProjectionServiceContract() throws Exception {
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("demographicExt"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            for (var row : properties) output.accept(row);
                            throw new IOException("SOURCE_EOF_FAILED");
                        });
        assertThatThrownBy(this::project).hasMessage("SOURCE_EOF_FAILED");
        verifyNoInteractions(captures);
    }

    private void count(long value) {
        tables.put("demographicExt", new CdsExportCapturedTableDto(Set.of("fixture"), value, HASH));
    }

    private CdsExportDemographicPhoneProjectionService.Result project() throws Exception {
        return service.project(lease, patient, TABLES, source, bounds, control);
    }
}
