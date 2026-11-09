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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;
import org.junit.jupiter.api.*;

/** Source joins/EOF/final-authority tests; these mocks do not replace the installed database proof. */
@Tag("unit")
@Tag("cds-export")
class CdsExportEnrolmentProjectionServiceUnitTest {
    private static final String HASH = "a".repeat(64);
    private static final Set<String> TABLES =
            Set.of(
                    "demographic",
                    "demographicArchive",
                    "demographiccust",
                    "demographicExt",
                    "provider",
                    "demographic_merged",
                    "cds_import_unit",
                    "cds_import_run",
                    "cds_import_patient",
                    "cds_import_preparation",
                    "cds_import_field",
                    "cds_import_dependency");
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportRetainedIndexService retained =
            mock(CdsExportRetainedIndexService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportDemographicPhoneProjectionService phones =
            mock(CdsExportDemographicPhoneProjectionService.class);
    private final CdsExportPatientNoteProjectionReader notes =
            mock(CdsExportPatientNoteProjectionReader.class);
    private final CdsExportPatientCaptureDto source = mock(CdsExportPatientCaptureDto.class);
    private final CdsExportStagedFileDto manifest = mock(CdsExportStagedFileDto.class);
    private final CdsExportNativeCaptureDto captured = mock(CdsExportNativeCaptureDto.class);
    private final CdsExportRetainedIndexDto index = mock(CdsExportRetainedIndexDto.class);
    private final Map<String, CdsExportCapturedTableDto> tables = new HashMap<>();
    private final List<CdsExportCapturedTextRowDto> archiveRows = new ArrayList<>();
    private CdsExportCapturedTextRowDto current;
    private CdsExportEnrolmentProjectionService service;
    private CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    100000, 8L << 20, 1000, 1L << 20, 100, 100);

    @BeforeEach
    void fixture() throws Exception {
        var document = mock(CdsExportJobDocumentDto.class);
        when(lease.document()).thenReturn(document);
        when(document.command())
                .thenReturn(
                        new CdsExportCommand(
                                "fixture",
                                "27",
                                null,
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportCommand.Selection.SELECTED_PATIENTS,
                                List.of(),
                                List.of(27),
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                "fixture",
                                "fixture"));
        when(patient.patient()).thenReturn(27);
        when(source.capture()).thenReturn(captured);
        when(source.manifest()).thenReturn(manifest);
        when(manifest.plaintextSha256()).thenReturn(HASH);
        for (String table : TABLES)
            tables.put(table, new CdsExportCapturedTableDto(Set.of("fixture"), 0, HASH));
        for (var entry :
                Map.of(
                                "demographic",
                                1L,
                                "demographicArchive",
                                2L,
                                "provider",
                                1L,
                                "demographiccust",
                                1L)
                        .entrySet()) count(entry.getKey(), entry.getValue());
        when(captured.tables()).thenReturn(tables);
        when(index.sourceManifestSha256()).thenReturn(HASH);
        when(index.tables()).thenReturn(Map.of("cds_import_unit", Map.of()));
        when(index.appliedUnits()).thenReturn(Set.of());
        when(retained.index(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), anyInt(), anyLong(),
                        any()))
                .thenReturn(index);
        archiveRows.add(
                CdsExportEnrolmentHistoryUnitTest.archive(1, "RO", "2020-01-01", null, null, "A"));
        archiveRows.add(
                CdsExportEnrolmentHistoryUnitTest.archive(
                        2, "TE", "2020-01-01", "2021-01-01", "12", "A"));
        current = CdsExportEnrolmentHistoryUnitTest.current("RO", "2022-01-01", null, null, "A");
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        anyString(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows output = call.getArgument(9);
                            switch ((String) call.getArgument(3)) {
                                case "demographic_merged" -> {}
                                case "provider" ->
                                        output.accept(
                                                CdsExportEnrolmentHistoryUnitTest.provider("A"));
                                case "demographicArchive" -> {
                                    for (var row : archiveRows) output.accept(row);
                                }
                                case "demographic" -> output.accept(current);
                                default -> throw new AssertionError("Unexpected table");
                            }
                            return captured;
                        });
        var note =
                new CdsExportPatientNoteMapper()
                        .map(
                                27,
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                CdsExportPatientNoteMapperUnitTest.row("Exact patient note"));
        when(notes.read(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(new CdsExportPatientNoteProjectionReader.Result(HASH, 1, note));
        when(phones.project(any(), any(), anySet(), any(), any(), any()))
                .thenAnswer(
                        call -> {
                            var nativeFields = new TreeMap<>(current.fields());
                            nativeFields.remove("demographic_no");
                            var base =
                                    new CdsExportDemographicMapper()
                                            .map(
                                                    new io.github.carlos_emr.carlos.commn.dto
                                                            .CdsExportDemographicRowDto(
                                                            27, nativeFields));
                            return new CdsExportDemographicPhoneProjectionService.Result(
                                    HASH, 0, base);
                        });
        service = new CdsExportEnrolmentProjectionService(captures, retained, rows, notes, phones);
    }

    @Test
    void joinsAllNativeHistoryAndNotesWithinOneGenerationAndEndsWithFreshAuthority()
            throws Exception {
        var result = project();
        assertThat(result.representable()).isTrue();
        assertThat(result.events()).hasSize(3);
        assertThat(result.sourceRows()).isEqualTo(3);
        assertThat(result.enrolmentFields()).hasSize(21);
        assertThat(result.note().text()).isEqualTo("Exact patient note");
        var order = inOrder(notes, captures, patient, control);
        order.verify(notes).read(any(), any(), anySet(), any(), anyLong(), anyLong(), any());
        order.verify(captures).authorizeSources(lease, patient, TABLES, control);
        order.verify(patient).require(lease);
        order.verify(control).checkpoint();
    }

    @Test
    void capturedArchiveCountCannotBeSatisfiedByAPartialScan() throws Exception {
        archiveRows.removeFirst();
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
        verifyNoInteractions(notes);
    }

    @Test
    void differingFrozenGenerationsNeverReturnAProjection() throws Exception {
        var other = mock(CdsExportNativeCaptureDto.class);
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("provider"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(other);
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_PROJECTION_SCOPE_INVALID");
        verifyNoInteractions(notes);
    }

    @Test
    void appliedImportedDemographicsRequireRetainedReconciliationEvenWhenCurrentRosterLooksValid()
            throws Exception {
        String address = "123e4567-e89b-12d3-a456-426614174000:" + "f".repeat(64);
        var unit =
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        "e".repeat(64),
                        Map.of(
                                "run_id",
                                address.substring(0, 36),
                                "unit_key",
                                address.substring(37),
                                "category",
                                "Demographics"));
        when(index.tables()).thenReturn(Map.of("cds_import_unit", Map.of(unit.recordKey(), unit)));
        when(index.appliedUnits()).thenReturn(Set.of(address));
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_RETAINED_RECONCILIATION_REQUIRED");
        verifyNoInteractions(notes);
    }

    @Test
    void noImportedUnitIsInferredFromAnUnwrittenProposal() throws Exception {
        String run = "123e4567-e89b-12d3-a456-426614174000";
        var unit =
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        "e".repeat(64),
                        Map.of(
                                "run_id",
                                run,
                                "unit_key",
                                "f".repeat(64),
                                "category",
                                "Demographics"));
        when(index.tables()).thenReturn(Map.of("cds_import_unit", Map.of(unit.recordKey(), unit)));
        assertThat(project().events()).hasSize(3);
    }

    @Test
    void lateSourceAuthorizationCannotReturnSuccess() throws Exception {
        doThrow(new SecurityException("CDS_EXPORT_ACCESS_DENIED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_ACCESS_DENIED");
        verify(notes).read(any(), any(), anySet(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void lookupAndHistoryShareTheMetadataByteBudget() throws Exception {
        bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        100000, 8L << 20, 1000, 1024, 100, 100);
        assertThatThrownBy(this::project).hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_RESOURCE_LIMIT");
        verifyNoInteractions(notes);
    }

    @Test
    void absentExactPhysicianLookupNeverBecomesAnAnonymousEvent() throws Exception {
        when(rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        eq("provider"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(captured);
        count("provider", 0);
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_REQUIRES_REVIEW");
        verifyNoInteractions(notes);
    }

    @Test
    void patientNoteCountAndGenerationMustBothAgree() throws Exception {
        when(notes.read(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(
                        new CdsExportPatientNoteProjectionReader.Result("b".repeat(64), 0, null));
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void aDifferentRetainedIndexCannotBorrowCurrentCapturedNativeRows() throws Exception {
        when(index.sourceManifestSha256()).thenReturn("b".repeat(64));
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_PROJECTION_SCOPE_INVALID");
        verifyNoInteractions(notes);
    }

    @Test
    void currentDemographicReviewObligationsArePreserved() throws Exception {
        var fields = new TreeMap<>(current.fields());
        fields.put("citizenship", "Additional native value");
        current = new CdsExportCapturedTextRowDto(current.table(), current.recordKey(), fields);
        assertThat(project().representable()).isFalse();
    }

    @Test
    void skippedCurrentRowsAndWrongDeclaredCurrentMultiplicityRefuse() throws Exception {
        count("demographic", 2);
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_ENROLMENT_PROJECTION_SCOPE_INVALID");
        verifyNoInteractions(notes);
    }

    private void count(String table, long value) {
        tables.put(table, new CdsExportCapturedTableDto(Set.of("fixture"), value, HASH));
    }

    private CdsExportDemographicEnrolmentProjectionDto project() throws Exception {
        return service.project(lease, patient, TABLES, source, bounds, control);
    }
}
