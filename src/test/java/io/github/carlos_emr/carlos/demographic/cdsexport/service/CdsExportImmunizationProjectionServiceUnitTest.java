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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class CdsExportImmunizationProjectionServiceUnitTest {
    private static final Set<String> TABLES = CdsExportImmunizationCaptureValidator.REQUIRED;
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportImmunizationCaptureValidator closure =
            mock(CdsExportImmunizationCaptureValidator.class);
    private final CdsExportPreventionConfigurationService configurations =
            mock(CdsExportPreventionConfigurationService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final List<CdsExportCapturedTextRowDto> children = new ArrayList<>(),
            parents = new ArrayList<>();
    private final List<CdsExportImmunizationMappingDto> output = new ArrayList<>();
    private CdsExportPatientCaptureDto source;
    private CdsExportPreventionConfigurationDto config;
    private CdsExportNativeCaptureDto generation;
    private CdsExportImmunizationProjectionService service;
    private boolean failAfterCallbacks, wrongAuxiliary, wrongParents;
    private int maximumRows = 1000;
    private long maximumBytes = 1L << 20;

    @BeforeEach
    void prepare() throws Exception {
        when(patient.patient()).thenReturn(27);
        generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables())
                .thenAnswer(
                        call -> {
                            var result = new HashMap<String, CdsExportCapturedTableDto>();
                            for (String table : TABLES) {
                                var columns = Set.of("id");
                                long count = 0;
                                if (table.equals("preventions")) {
                                    columns =
                                            Set.copyOf(
                                                    CdsExportPatientTableReadSupport.Plan
                                                            .PREVENTIONS_FULL
                                                            .columns());
                                    count = 1;
                                }
                                if (table.equals("preventionsExt")) {
                                    columns =
                                            Set.copyOf(
                                                    CdsExportPatientTableReadSupport.Plan
                                                            .PREVENTIONS_EXT
                                                            .columns());
                                    count =
                                            children.stream()
                                                    .filter(row -> row.table().equals(table))
                                                    .count();
                                }
                                if (table.equals("prevention_precision")) {
                                    columns =
                                            Set.copyOf(
                                                    CdsExportPatientTableReadSupport.Plan
                                                            .PREVENTION_PRECISION
                                                            .columns());
                                    count =
                                            children.stream()
                                                    .filter(row -> row.table().equals(table))
                                                    .count();
                                }
                                result.put(
                                        table, new CdsExportCapturedTableDto(columns, count, HASH));
                            }
                            return result;
                        });
        when(generation.values())
                .thenReturn(new CdsExportStagedFileDto("d".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(generation.index())
                .thenReturn(new CdsExportStagedFileDto("e".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        generation,
                        new CdsExportStagedFileDto("b".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        config =
                new CdsExportPreventionConfigurationDto(
                        HASH,
                        HASH,
                        1,
                        new CdsExportStagedFileDto(
                                "c".repeat(64), "SOURCE_PREVENTION_CONFIG", HASH, 77));
        when(closure.verify(lease, patient, TABLES, source, config, 10000, 8L << 20, control))
                .thenReturn(
                        new CdsExportPreventionScopePlanner.Result(
                                27, HASH, HASH, HASH, HASH, Set.of(17), Set.of(), Map.of(), 1));
        var classifier =
                CdsExportPreventionClassifier.fromCaptured(
                        HASH,
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        when(configurations.restore(
                        lease, patient, TABLES, source, config, 10000, 8L << 20, control))
                .thenReturn(classifier);
        var root = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                .columns()
                .forEach(column -> root.put(column, null));
        root.putAll(
                Map.of(
                        "id",
                        "17",
                        "demographic_no",
                        "27",
                        "prevention_type",
                        "Td",
                        "prevention_date",
                        "2024-02-29 12:34:56",
                        "deleted",
                        "0",
                        "never",
                        "0",
                        "refused",
                        "0"));
        parents.add(
                new CdsExportCapturedTextRowDto(
                        "preventions", CdsExportPatientTableSourceReader.key(17), root));
        children.add(
                new CdsExportCapturedTextRowDto(
                        "preventionsExt",
                        CdsExportPatientTableSourceReader.key(30),
                        Map.of(
                                "id",
                                "30",
                                "prevention_id",
                                "17",
                                "keyval",
                                "comments",
                                "val",
                                "Exact café 😀 notes")));
        children.add(
                new CdsExportCapturedTextRowDto(
                        "prevention_precision",
                        CdsExportPatientTableSourceReader.key(40),
                        Map.of(
                                "id",
                                "40",
                                "table_id",
                                "17",
                                "table_name",
                                "4",
                                "field_name",
                                "7",
                                "format",
                                "YYYY")));
        when(rows.forEachTable(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq(Set.of("preventionsExt", "prevention_precision")),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            for (var row : children) sink.accept(row);
                            return wrongAuxiliary
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : generation;
                        });
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq("preventions"),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            for (var row : parents) sink.accept(row);
                            if (failAfterCallbacks)
                                throw new IOException("LATE_AUTHENTICATED_PARENT_EOF_FAILURE");
                            return wrongParents
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : generation;
                        });
        service =
                new CdsExportImmunizationProjectionService(captures, closure, configurations, rows);
    }

    private CdsExportImmunizationProjectionDto project() throws Exception {
        return service.project(
                lease,
                patient,
                TABLES,
                source,
                config,
                10000,
                8L << 20,
                maximumRows,
                maximumBytes,
                control,
                output::add);
    }

    @Test
    void joinsEveryCapturedChildAndCountsEveryPhysicalFieldBeforeReturning() throws Exception {
        var result = project();
        assertThat(result.rows()).isEqualTo(1);
        assertThat(result.blockedRows()).isZero();
        assertThat(result.assessedFields()).isEqualTo(25);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst().value().notes()).isEqualTo("Exact café 😀 notes");
        assertThat(output.getFirst().value().date().lexical()).isEqualTo("2024");
        verify(captures).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void unknownClinicalExtensionsRemainABlockedOccurrenceWithTheirFields() throws Exception {
        var fields = new TreeMap<>(children.getFirst().fields());
        fields.put("keyval", "unknownClinicalValue");
        children.set(
                0,
                new CdsExportCapturedTextRowDto(
                        "preventionsExt", children.getFirst().recordKey(), fields));
        var result = project();
        assertThat(result.blockedRows()).isEqualTo(1);
        assertThat(result.assessedFields()).isEqualTo(25);
        assertThat(output.getFirst().value()).isNull();
    }

    @Test
    void unselectedForeignChildCannotPassEvenIfAnUpstreamClosureWasSubstituted() {
        var fields = new TreeMap<>(children.getFirst().fields());
        fields.put("prevention_id", "18");
        children.set(
                0,
                new CdsExportCapturedTextRowDto(
                        "preventionsExt", children.getFirst().recordKey(), fields));
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
        assertThat(output).isEmpty();
    }

    @Test
    void missingSelectedParentCannotBecomeAnEmptySuccessfulProjection() {
        parents.clear();
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void duplicateParentsCannotEmitTwoSuccessfulOccurrences() {
        parents.add(parents.getFirst());
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
    }

    @Test
    void wrongAuxiliaryGenerationIsRejectedBeforeAnyClinicalCallbacks() {
        wrongAuxiliary = true;
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
        assertThat(output).isEmpty();
    }

    @Test
    void wrongParentGenerationCannotReturnAProjectionEvenAfterPrivateCallbacks() {
        wrongParents = true;
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
        verify(captures, never()).authorizeSources(any(), any(), anySet(), any());
    }

    @Test
    void lateAuthenticatedEofFailureCannotReturnSuccessfulPrivateCallbacks() {
        failAfterCallbacks = true;
        assertThatThrownBy(this::project).hasMessage("LATE_AUTHENTICATED_PARENT_EOF_FAILURE");
        assertThat(output).hasSize(1);
        verify(captures, never()).authorizeSources(any(), any(), anySet(), any());
    }

    @Test
    void revocationAtTheLastBoundaryPreventsReturningAProjection() {
        doThrow(new SecurityException("REVOKED_AFTER_PROJECTION"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::project).hasMessage("REVOKED_AFTER_PROJECTION");
    }

    @Test
    void boundedAuxiliaryRowsAndBytesFailExplicitlyWithoutPartialSuccess() {
        maximumRows = 1;
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_AUXILIARY_RESOURCE_LIMIT");
        assertThat(output).isEmpty();
        maximumRows = 1000;
        maximumBytes = 1;
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_AUXILIARY_RESOURCE_LIMIT");
    }

    @Test
    void aForeignDefinitionGenerationCannotSupplyNativeTypeMeaning() throws Exception {
        var foreign =
                CdsExportPreventionClassifier.fromCaptured(
                        "b".repeat(64),
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        when(configurations.restore(
                        lease, patient, TABLES, source, config, 10000, 8L << 20, control))
                .thenReturn(foreign);
        assertThatThrownBy(this::project)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
        verifyNoInteractions(rows);
    }

    @Test
    void currentRowsAreNeverReadAfterUpstreamProgramRefusal() throws Exception {
        when(closure.verify(lease, patient, TABLES, source, config, 10000, 8L << 20, control))
                .thenThrow(new SecurityException("PROGRAM_REFUSED"));
        assertThatThrownBy(this::project).hasMessage("PROGRAM_REFUSED");
        verifyNoInteractions(rows, configurations);
    }
}
