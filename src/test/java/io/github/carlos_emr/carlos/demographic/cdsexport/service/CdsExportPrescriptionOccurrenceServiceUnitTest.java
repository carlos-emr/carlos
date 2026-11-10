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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportPrescriptionOccurrenceServiceUnitTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;

    private record Captured(
            CdsExportPatientPipelineFixture fixture,
            CdsExportPatientCaptureDto source,
            java.util.Set<String> tables) {}

    private Captured captured(boolean includeSupporting, boolean includeAuthor) throws Exception {
        var categories =
                java.util.EnumSet.of(
                        CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        if (includeSupporting) categories.add(CdsCategory.NEW_CATEGORY);
        var f = new CdsExportPatientPipelineFixture(root, categories);
        var parentFields = new java.util.LinkedHashMap<>(parent(27).fields());
        parentFields.put("provider_no", "p27");
        var parent =
                new CdsExportCapturedTextRowDto(
                        "prescription", CdsExportPatientTableSourceReader.key(71), parentFields);
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        var author =
                new CdsExportCapturedTextRowDto(
                        "provider",
                        CdsExportPublicProviderSourceReader.key("p27"),
                        Map.of(
                                "provider_no",
                                "p27",
                                "first_name",
                                "Native",
                                "last_name",
                                "Script Author",
                                "ohip_no",
                                "001234",
                                "practitionerNo",
                                "",
                                "practitionerNoType",
                                ""));
        var input = new java.util.ArrayList<CdsExportCapturedTextRowDto>(List.of(drug, parent));
        if (includeAuthor) input.add(author);
        var scopes = new java.util.TreeMap<String, CdsExportCapturedTableDto>();
        for (var row : List.of(drug, parent, author))
            scopes.put(
                    row.table(),
                    new CdsExportCapturedTableDto(
                            row.fields().keySet(),
                            row.table().equals("provider") && !includeAuthor ? 0 : 1,
                            CdsExportPatientPipelineFixture.HASH));
        long count = scopes.values().stream().mapToLong(t -> t.rows() * t.columns().size()).sum();
        var bytes = new java.io.ByteArrayOutputStream();
        for (var row : input)
            for (String value : row.fields().values())
                if (value != null)
                    bytes.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var values = f.store.stage("SOURCE_VALUES", out -> out.write(bytes.toByteArray()));
        var original = f.source.capture();
        var capture =
                new CdsExportNativeCaptureDto(
                        2,
                        f.run,
                        1,
                        27,
                        original.cohortSha256(),
                        original.readStartedAt(),
                        original.readCompletedAt(),
                        scopes,
                        count,
                        values.plaintextBytes(),
                        values,
                        original.index());
        var source = new CdsExportPatientCaptureDto(capture, f.source.manifest());
        when(f.captures.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            var sink = (CdsExportNativeCaptureReader.Fields) call.getArgument(7);
                            long offset = 0;
                            for (var row : input)
                                for (var entry : row.fields().entrySet()) {
                                    var raw = entry.getValue();
                                    byte[] value =
                                            raw == null
                                                    ? new byte[0]
                                                    : raw.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8);
                                    var field =
                                            new CdsExportCapturedFieldDto(
                                                    row.table(),
                                                    row.recordKey(),
                                                    entry.getKey(),
                                                    raw == null
                                                            ? CdsExportCapturedFieldDto.Kind
                                                                    .SQL_NULL
                                                            : CdsExportCapturedFieldDto.Kind.UTF8,
                                                    offset,
                                                    value.length,
                                                    CdsExportPatientPipelineFixture.HASH);
                                    sink.output(field).write(value);
                                    sink.verified(field);
                                    offset += value.length;
                                }
                            return capture;
                        });
        return new Captured(f, source, scopes.keySet());
    }

    @Test
    void shouldReadCompleteFrozenParentsAndAuthors_withFreshCategoryAuthorization()
            throws Exception {
        var f = captured(true, true);
        var service = new CdsExportPrescriptionOccurrenceService(f.fixture.captures);
        var result =
                service.sources(
                        f.fixture.lease,
                        f.fixture.patient,
                        f.tables,
                        f.source,
                        f.fixture.bounds,
                        f.fixture.control);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().author().fields()).containsEntry("last_name", "Script Author");
        assertThat(result.getFirst().prescription().fields())
                .containsEntry("rx_comments", "Clinical parent comment");
        verify(f.fixture.control)
                .authorizeCategory(27, CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
        verify(f.fixture.captures, times(3)).authorizeSources(any(), any(), eq(f.tables), any());
    }

    @Test
    void shouldRefuseExcludingSupportingScriptHistory_insteadOfDroppingItsComments()
            throws Exception {
        var f = captured(false, true);
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService(f.fixture.captures)
                                        .sources(
                                                f.fixture.lease,
                                                f.fixture.patient,
                                                f.tables,
                                                f.source,
                                                f.fixture.bounds,
                                                f.fixture.control))
                .hasMessage("CDS_EXPORT_PRESCRIPTION_SUPPORTING_CATEGORY_REQUIRED");
    }

    @Test
    void shouldRefuseAnUnresolvedPublicScriptAuthor_insteadOfInventingTheExportingClinician()
            throws Exception {
        var f = captured(true, false);
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService(f.fixture.captures)
                                        .sources(
                                                f.fixture.lease,
                                                f.fixture.patient,
                                                f.tables,
                                                f.source,
                                                f.fixture.bounds,
                                                f.fixture.control))
                .hasMessage("CDS_EXPORT_PRESCRIPTION_OWNERSHIP_REQUIRES_REVIEW");
    }

    @Test
    void shouldStopBeforeReadingWhenMedicationAccessHasBeenRevoked() throws Exception {
        var f = captured(true, true);
        doThrow(new SecurityException("synthetic revoked scope"))
                .when(f.fixture.control)
                .authorizeCategory(27, CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService(f.fixture.captures)
                                        .sources(
                                                f.fixture.lease,
                                                f.fixture.patient,
                                                f.tables,
                                                f.source,
                                                f.fixture.bounds,
                                                f.fixture.control))
                .isInstanceOf(SecurityException.class);
        verify(f.fixture.captures, never())
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void shouldRefuseAnUnboundCaptureScope_beforeReadingAnyScriptValues() throws Exception {
        var f = captured(true, true);
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService(f.fixture.captures)
                                        .sources(
                                                f.fixture.lease,
                                                f.fixture.patient,
                                                Set.of("prescription"),
                                                f.source,
                                                f.fixture.bounds,
                                                f.fixture.control))
                .hasMessage("CDS_EXPORT_PRESCRIPTION_OWNERSHIP_REQUIRES_REVIEW");
        verify(f.fixture.captures, never())
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    static CdsExportCapturedTextRowDto parent(int patient) {
        var fields = new LinkedHashMap<String, String>();
        Plan.PRESCRIPTIONS.columns().forEach(c -> fields.put(c, null));
        fields.put("script_no", "71");
        fields.put("demographic_no", Integer.toString(patient));
        fields.put("textView", "Exact printed café 😀 prescription\r\nSIG");
        fields.put("rx_comments", "Clinical parent comment");
        return new CdsExportCapturedTextRowDto(
                "prescription", CdsExportPatientTableSourceReader.key(71), fields);
    }

    @Test
    void shouldFreezeTheExactPublicParentAuthor_withoutExposingPrivateProviderFields()
            throws Exception {
        var fields = new LinkedHashMap<>(parent(27).fields());
        fields.put("provider_no", "p27");
        var parent =
                new CdsExportCapturedTextRowDto(
                        "prescription", CdsExportPatientTableSourceReader.key(71), fields);
        var author =
                new CdsExportCapturedTextRowDto(
                        "provider",
                        CdsExportPublicProviderSourceReader.key("p27"),
                        Map.of(
                                "provider_no",
                                "p27",
                                "first_name",
                                "Native",
                                "last_name",
                                "Script Author",
                                "ohip_no",
                                "001234",
                                "practitionerNo",
                                "",
                                "practitionerNoType",
                                ""));
        var source = new CdsExportPrescriptionOccurrenceService.Source(parent, author);
        var assessments = CdsExportPrescriptionOccurrenceService.assessments(source);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), assessments, 1000, () -> {});
        render(
                CdsCategory.NEW_CATEGORY,
                xml -> CdsExportPrescriptionOccurrenceService.write(source, xml));
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService.Source(
                                        parent,
                                        new CdsExportCapturedTextRowDto(
                                                "provider",
                                                "pbad",
                                                Map.of("provider_no", "different"))))
                .hasMessage("CDS_EXPORT_PRESCRIPTION_AUTHOR_SCOPE_INVALID");
    }

    @Test
    void shouldKeepLinkedAndStandaloneScriptHistoryAsIndependentlyAccountedClinicalContext()
            throws Exception {
        var parent = parent(27);
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        CdsExportPrescriptionOccurrenceService.requireOwnership(27, List.of(parent), List.of(drug));
        CdsExportPrescriptionOccurrenceService.requireOwnership(27, List.of(parent), List.of());
        var fields =
                CdsExportPrescriptionOccurrenceService.assessments(
                        new CdsExportPrescriptionOccurrenceService.Source(parent, null));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(parent), fields, 1000, () -> {});
        render(
                CdsCategory.NEW_CATEGORY,
                xml ->
                        CdsExportPrescriptionOccurrenceService.write(
                                new CdsExportPrescriptionOccurrenceService.Source(parent, null),
                                xml));
    }

    @Test
    void shouldRefuseOrphanCrossPatientDuplicateAndSpoofedParents() throws Exception {
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        for (var parents :
                List.of(
                        List.<CdsExportCapturedTextRowDto>of(),
                        List.of(parent(28)),
                        List.of(parent(27), parent(27))))
            assertThatThrownBy(
                            () ->
                                    CdsExportPrescriptionOccurrenceService.requireOwnership(
                                            27, parents, List.of(drug)))
                    .hasMessage("CDS_EXPORT_PRESCRIPTION_OWNERSHIP_REQUIRES_REVIEW");
        var spoofed =
                new CdsExportCapturedTextRowDto("prescription", "0000000072", parent(27).fields());
        assertThatThrownBy(
                        () ->
                                CdsExportPrescriptionOccurrenceService.requireOwnership(
                                        27, List.of(spoofed), List.of(drug)))
                .isInstanceOf(java.io.IOException.class);
    }
}
