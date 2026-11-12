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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

/** Independently valid transport can still contain a wrong clinical selection or dependency. */
class CdsExportImmunizationCaptureValidatorUnitTest {
    private static final String HASH = "a".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportPreventionClassificationService classifications =
            mock(CdsExportPreventionClassificationService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final Map<String, Set<String>> columns = new TreeMap<>();
    private final Map<String, List<Map<String, String>>> data = new TreeMap<>();
    private final List<CdsExportCapturedTextRowDto> metadata = new ArrayList<>();
    private CdsExportLeaseDto lease;
    private CdsExportPatientCaptureDto source;
    private CdsExportPreventionConfigurationDto config;
    private CdsExportImmunizationCaptureValidator validator;
    private boolean lateFailure, foreignGeneration, review, embedded;

    @BeforeEach
    void prepare() throws Exception {
        var command =
                new CdsExportCommand(
                        "capture-closure",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS),
                        "recipient",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        HASH,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
        when(patient.patient()).thenReturn(27);
        columns.put(
                "preventions",
                Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL.columns()));
        columns.put(
                "preventionsExt",
                Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT.columns()));
        columns.put(
                "prevention_precision",
                Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION.columns()));
        columns.put("preventions_scope", CdsExportPreventionScopePlanner.COLUMNS);
        columns.put("prevention_configuration", Set.of("xml", "sha256"));
        columns.put("CVCImmunization", Set.of("id"));
        columns.put("CVCMapping", Set.of("id"));
        columns.keySet().forEach(table -> data.put(table, new ArrayList<>()));
        var parent = blank(columns.get("preventions"));
        parent.putAll(
                Map.of("id", "1", "demographic_no", "27", "prevention_type", "Td", "deleted", "1"));
        data.get("preventions").add(parent);
        var meta = blank(CdsExportPreventionScopePlanner.COLUMNS);
        for (String key : meta.keySet()) meta.put(key, parent.get(key));
        metadata.add(
                new CdsExportCapturedTextRowDto(
                        "preventions_scope", CdsExportPatientTableSourceReader.key(1), meta));
        data.get("preventions_scope").add(meta);
        data.get("preventionsExt")
                .add(
                        new TreeMap<>(
                                Map.of(
                                        "id",
                                        "11",
                                        "prevention_id",
                                        "1",
                                        "keyval",
                                        "comments",
                                        "val",
                                        "café 😀\r\n".repeat(20000))));
        var extra = new TreeMap<>(data.get("preventionsExt").getFirst());
        extra.put("id", "12");
        extra.put("val", null);
        data.get("preventionsExt").add(extra);
        data.get("prevention_precision")
                .add(
                        new TreeMap<>(
                                Map.of(
                                        "id",
                                        "21",
                                        "table_id",
                                        "1",
                                        "table_name",
                                        "4",
                                        "field_name",
                                        "7",
                                        "format",
                                        "YYYY")));
        data.get("prevention_configuration")
                .add(new TreeMap<>(Map.of("xml", "<preventions/>", "sha256", HASH)));
        config =
                new CdsExportPreventionConfigurationDto(
                        HASH, HASH, 1, file("c", "SOURCE_PREVENTION_CONFIG", 77));
        when(classifications.classify(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        any(),
                        eq(config),
                        anyLong(),
                        anyLong(),
                        eq(control)))
                .thenAnswer(
                        call ->
                                new CdsExportPreventionScopePlanner.Result(
                                        27,
                                        HASH,
                                        HASH,
                                        HASH,
                                        HASH,
                                        review ? Set.of() : Set.of(1),
                                        Set.of(),
                                        review
                                                ? Map.of(
                                                        1,
                                                        CdsExportPreventionScopePlanner.Review
                                                                .UNKNOWN_TYPE)
                                                : Map.of(),
                                        1));
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        eq("preventions_scope"),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            for (var row : metadata) sink.accept(row);
                            return source.capture();
                        });
        when(captures.scan(
                        eq(lease),
                        eq(patient),
                        anySet(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            var values = new ByteArrayOutputStream();
                            var index = new ByteArrayOutputStream();
                            var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
                            for (var entry : columns.entrySet())
                                plan.put(
                                        entry.getKey(),
                                        new CdsExportNativeCaptureReader.Table(
                                                entry.getValue(), data.get(entry.getKey()).size()));
                            new CdsExportNativeCaptureComposer()
                                    .compose(
                                            values,
                                            index,
                                            columns.keySet(),
                                            100000,
                                            8L << 20,
                                            () -> {},
                                            writer -> {
                                                for (var entry : columns.entrySet()) {
                                                    writer.beginTable(
                                                            entry.getKey(),
                                                            entry.getValue(),
                                                            data.get(entry.getKey()).size());
                                                    for (var row : data.get(entry.getKey()))
                                                        writer.row(
                                                                entry.getKey()
                                                                                .equals(
                                                                                        "prevention_configuration")
                                                                        ? "configuration"
                                                                        : CdsExportPatientTableSourceReader
                                                                                .key(
                                                                                        Long
                                                                                                .parseLong(
                                                                                                        row
                                                                                                                .get(
                                                                                                                        "id"))),
                                                                row);
                                                    writer.endTable();
                                                }
                                            });
                            CdsExportNativeCaptureReader.Fields sink = call.getArgument(7);
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(values.toByteArray()),
                                            new ByteArrayInputStream(index.toByteArray()),
                                            plan,
                                            100000,
                                            8L << 20,
                                            () -> {},
                                            sink);
                            if (lateFailure)
                                throw new IOException("LATE_AUTHENTICATED_EOF_FAILURE");
                            return foreignGeneration
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : source.capture();
                        });
        validator = new CdsExportImmunizationCaptureValidator(captures, classifications, rows);
    }

    private CdsExportPreventionScopePlanner.Result verifyCapture() throws Exception {
        var tables = new TreeMap<String, CdsExportCapturedTableDto>();
        long fields = 0;
        for (var entry : columns.entrySet()) {
            int count = data.get(entry.getKey()).size();
            tables.put(
                    entry.getKey(), new CdsExportCapturedTableDto(entry.getValue(), count, HASH));
            fields += count * entry.getValue().size();
        }
        source =
                new CdsExportPatientCaptureDto(
                        new CdsExportNativeCaptureDto(
                                2,
                                lease.runId(),
                                1,
                                27,
                                HASH,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                tables,
                                fields,
                                1,
                                file("d", "SOURCE_VALUES", 1),
                                file("e", "SOURCE_INDEX", 1)),
                        file("f", "SOURCE_MANIFEST", 1));
        return embedded
                ? validator.verifyEmbedded(
                        lease,
                        patient,
                        Set.copyOf(columns.keySet()),
                        source,
                        100000,
                        8L << 20,
                        control)
                : validator.verify(
                        lease,
                        patient,
                        Set.copyOf(columns.keySet()),
                        source,
                        config,
                        100000,
                        8L << 20,
                        control);
    }

    @Test
    void shouldVerifyCompleteArchivedParentDuplicateLargeExtensionsNullAndPrecisionRemainOneSelectedOccurrence_forImmunizationCaptureValidatorContract()
            throws Exception {
        assertThat(verifyCapture().immunizations()).containsExactly(1);
        verify(captures).authorizeSources(eq(lease), eq(patient), anySet(), eq(control));
    }

    @Test
    void shouldVerifyMissingSelectedParentCannotBecomeAnEmptySuccessfulExport_forImmunizationCaptureValidatorContract() {
        data.get("preventions").clear();
        refused();
    }

    @Test
    void shouldVerifyAnUnselectedScreeningParentCannotBecomeAnImmunization_forImmunizationCaptureValidatorContract() {
        var extra = new TreeMap<>(data.get("preventions").getFirst());
        extra.put("id", "2");
        extra.put("prevention_type", "PAP");
        data.get("preventions").add(extra);
        refused();
    }

    @Test
    void shouldVerifySelectedParentTypeMustAgree_withItsFrozenClassificationMetadata() {
        data.get("preventions").getFirst().put("prevention_type", "PAP");
        refused();
    }

    @Test
    void shouldVerifySelectedParentCodeMustAgree_withItsFrozenClassificationMetadata() {
        data.get("preventions").getFirst().put("snomedId", "12345");
        refused();
    }

    @Test
    void shouldVerifySelectedParentProgramScopeMustAgree_withItsFrozenMetadata() {
        data.get("preventions").getFirst().put("restrictToProgram", "1");
        refused();
    }

    @Test
    void shouldVerifySelectedParentCannotChangeItsClinicalOwner_forImmunizationCaptureValidatorContract() {
        data.get("preventions").getFirst().put("demographic_no", "28");
        refused();
    }

    @Test
    void shouldVerifyAnExtensionCannotAttach_toAnUnselectedScreeningOrForeignParent() {
        data.get("preventionsExt").getFirst().put("prevention_id", "2");
        refused();
    }

    @Test
    void shouldVerifyPrecisionCannotAttach_toAnUnselectedParent() {
        data.get("prevention_precision").getFirst().put("table_id", "2");
        refused();
    }

    @Test
    void shouldVerifyPrecisionCannotPretendAnAllergyDateIsAVaccineDate_forImmunizationCaptureValidatorContract() {
        data.get("prevention_precision").getFirst().put("table_name", "1");
        refused();
    }

    @Test
    void shouldVerifyAPhysicalPrecisionRowCannotAppear_inBothCapturedAliases() {
        columns.put("partial_date", columns.get("prevention_precision"));
        data.put(
                "partial_date",
                new ArrayList<>(
                        List.of(
                                new TreeMap<>(
                                        Map.of(
                                                "id",
                                                "21",
                                                "table_id",
                                                "6",
                                                "table_name",
                                                "1",
                                                "field_name",
                                                "1",
                                                "format",
                                                "YYYY")))));
        refused();
    }

    @Test
    void shouldVerifyMalformedForeignKeyCannotBeTreatedAsAbsent_forImmunizationCaptureValidatorContract() {
        data.get("preventionsExt").getFirst().put("prevention_id", "01");
        refused();
    }

    @Test
    void shouldVerifyLateSourceAuthenticationFailureCannotReturnACompletedSelection_forImmunizationCaptureValidatorContract() {
        lateFailure = true;
        assertThatThrownBy(this::verifyCapture).hasMessage("LATE_AUTHENTICATED_EOF_FAILURE");
        verify(captures, never()).authorizeSources(any(), any(), anySet(), any());
    }

    @Test
    void shouldVerifyASubstitutedCaptureGenerationCannotReturnACompletedSelection_forImmunizationCaptureValidatorContract() {
        foreignGeneration = true;
        refused();
    }

    @Test
    void shouldVerifyRevocation_afterFullClosureVerificationPreventsReturningTheSelection() {
        doThrow(new SecurityException("ACCESS_REVOKED_AFTER_CAPTURE_VALIDATION"))
                .when(captures)
                .authorizeSources(any(), any(), anySet(), any());
        assertThatThrownBy(this::verifyCapture)
                .hasMessage("ACCESS_REVOKED_AFTER_CAPTURE_VALIDATION");
    }

    @Test
    void shouldVerifyReviewIsNotReportedUntilTheWholeCaptureAndFinalAuthorityPass_forImmunizationCaptureValidatorContract() {
        review = true;
        data.get("preventions").clear();
        data.get("preventionsExt").clear();
        data.get("prevention_precision").clear();
        assertThatThrownBy(this::verifyCapture).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        verify(captures).authorizeSources(any(), any(), anySet(), any());
    }

    @Test
    void shouldVerifyAMissingCapturedConfigurationCannotBeReplaced_withCurrentSettings() {
        columns.remove("prevention_configuration");
        data.remove("prevention_configuration");
        refused();
        verifyNoInteractions(classifications, rows);
    }

    @Test
    void shouldVerifyAPartialParentProjectionCannotClaimCompleteClinicalCapture_forImmunizationCaptureValidatorContract() {
        columns.put("preventions", Set.of("id"));
        data.get("preventions").set(0, new TreeMap<>(Map.of("id", "1")));
        refused();
        verifyNoInteractions(classifications, rows);
    }

    @Test
    void shouldVerifyEmbeddedClosureUsesTheSnapshotClassificationAndStillChecksAllParentsAndAuthority_forImmunizationCaptureValidatorContract()
            throws Exception {
        embedded = true;
        when(classifications.classifyEmbedded(
                        eq(lease), eq(patient), anySet(), any(), anyLong(), anyLong(), eq(control)))
                .thenReturn(
                        new CdsExportPreventionScopePlanner.Result(
                                27, HASH, HASH, HASH, HASH, Set.of(1), Set.of(), Map.of(), 1));
        assertThat(verifyCapture().immunizations()).containsExactly(1);
        verify(classifications, never())
                .classify(any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any());
        data.get("preventionsExt").getFirst().put("prevention_id", "2");
        refused();
    }

    @Test
    void shouldVerifyEmbeddedClosureCannotReturn_afterLateAuthenticationFailure() throws Exception {
        embedded = true;
        lateFailure = true;
        when(classifications.classifyEmbedded(
                        eq(lease), eq(patient), anySet(), any(), anyLong(), anyLong(), eq(control)))
                .thenReturn(
                        new CdsExportPreventionScopePlanner.Result(
                                27, HASH, HASH, HASH, HASH, Set.of(1), Set.of(), Map.of(), 1));
        assertThatThrownBy(this::verifyCapture).hasMessage("LATE_AUTHENTICATED_EOF_FAILURE");
        verify(captures, never()).authorizeSources(any(), any(), anySet(), any());
    }

    private void refused() {
        assertThatThrownBy(this::verifyCapture)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_CAPTURE_INVALID");
    }

    private static TreeMap<String, String> blank(Set<String> columns) {
        var result = new TreeMap<String, String>();
        columns.forEach(column -> result.put(column, null));
        return result;
    }

    private static CdsExportStagedFileDto file(String id, String purpose, long bytes) {
        return new CdsExportStagedFileDto(id.repeat(64), purpose, HASH, bytes);
    }
}
