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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

/** Frozen-generation/EOF/late-authority integration; pure classification and actual DAO/security transactions have separate independent tests. */
class CdsExportPreventionClassificationServiceUnitTest {
    private static final Set<String> TABLES =
            Set.of("preventions_scope", "CVCImmunization", "CVCMapping");
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportPreventionConfigurationService configuration =
            mock(CdsExportPreventionConfigurationService.class);
    private final CdsExportCapturedTextRowsReader rows =
            mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private CdsExportLeaseDto lease;
    private CdsExportPatientCaptureDto source;
    private CdsExportNativeCaptureDto generation;
    private CdsExportPreventionConfigurationDto receipt;
    private CdsExportPreventionClassificationService service;
    private List<CdsExportCapturedTextRowDto> metadata;
    private boolean failAtEof;

    @BeforeEach
    void prepare() throws Exception {
        var command =
                new CdsExportCommand(
                        "classify",
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
                        "a".repeat(64),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "b".repeat(64),
                                "sender",
                                "c".repeat(64),
                                "d".repeat(64),
                                new CdsExportStorageBindingDto(
                                        "primary", "primary", "a".repeat(64))));
        when(patient.patient()).thenReturn(27);
        generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables())
                .thenReturn(
                        Map.of(
                                "preventions_scope",
                                new CdsExportCapturedTableDto(
                                        CdsExportPreventionScopePlanner.COLUMNS, 2, "a".repeat(64)),
                                "CVCImmunization",
                                new CdsExportCapturedTableDto(Set.of("id"), 0, "a".repeat(64)),
                                "CVCMapping",
                                new CdsExportCapturedTableDto(Set.of("id"), 0, "a".repeat(64))));
        when(generation.values())
                .thenReturn(
                        new CdsExportStagedFileDto(
                                "e".repeat(64), "SOURCE_VALUES", "a".repeat(64), 1));
        when(generation.index())
                .thenReturn(
                        new CdsExportStagedFileDto(
                                "f".repeat(64), "SOURCE_INDEX", "a".repeat(64), 1));
        source =
                new CdsExportPatientCaptureDto(
                        generation,
                        new CdsExportStagedFileDto(
                                "a".repeat(64), "SOURCE_MANIFEST", "b".repeat(64), 1));
        receipt =
                new CdsExportPreventionConfigurationDto(
                        source.manifest().plaintextSha256(),
                        "c".repeat(64),
                        1,
                        new CdsExportStagedFileDto(
                                "c".repeat(64), "SOURCE_PREVENTION_CONFIG", "d".repeat(64), 77));
        when(configuration.restore(
                        lease, patient, TABLES, source, receipt, 1000, 4L << 20, control))
                .thenReturn(classifier(source.manifest().plaintextSha256()));
        metadata = List.of(row(1, "Td"), row(2, "PAP"));
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq("preventions_scope"),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenAnswer(
                        call -> {
                            CdsExportCapturedTextRowsReader.Rows sink = call.getArgument(9);
                            for (var row : metadata) sink.accept(row);
                            if (failAtEof) throw new IOException("LATE_SOURCE_EOF_FAILURE");
                            return generation;
                        });
        service = new CdsExportPreventionClassificationService(captures, configuration, rows);
    }

    private CdsExportPreventionScopePlanner.Result classify() throws Exception {
        return service.classify(lease, patient, TABLES, source, receipt, 1000, 4L << 20, control);
    }

    @Test
    void shouldVerifyPartitionsTheCompleteFrozenMetadataAndChecksAuthority_afterPlanning() throws Exception {
        var result = classify();
        assertThat(result.immunizations()).containsExactly(1);
        assertThat(result.otherPreventions()).containsExactly(2);
        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.representable()).isTrue();
        verify(captures).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldVerifyUnknownTypesAreAnExplicitReviewPartitionRatherThanOmitted_forPreventionClassificationServiceContract() {
        metadata = List.of(row(1, "Td"), row(2, "UNCONFIGURED"));
        assertThatCode(
                        () -> {
                            var result = classify();
                            assertThat(result.reviews())
                                    .containsEntry(
                                            2, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE);
                            assertThat(result.rows()).isEqualTo(2);
                            assertThat(result.representable()).isFalse();
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldVerifyAForeignConfigurationGenerationIsRefused_beforeAnyMetadataCallbacks() throws Exception {
        when(configuration.restore(
                        lease, patient, TABLES, source, receipt, 1000, 4L << 20, control))
                .thenReturn(classifier("c".repeat(64)));
        assertThatThrownBy(this::classify).hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        verifyNoInteractions(rows);
    }

    @Test
    void shouldVerifyLateAuthenticatedSourceFailureCannotBecomeAPartialClassification_forPreventionClassificationServiceContract() {
        failAtEof = true;
        assertThatThrownBy(this::classify).hasMessage("LATE_SOURCE_EOF_FAILURE");
        verifyNoInteractions(captures);
    }

    @Test
    void shouldVerifyCurrentPermissionRevocation_afterPlanningPreventsReturningThePartition() {
        doThrow(new SecurityException("ACCESS_REVOKED_AFTER_PLAN"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(this::classify).hasMessage("ACCESS_REVOKED_AFTER_PLAN");
    }

    @Test
    void shouldVerifyACapturedGenerationSubstitutionIsRefused_beforePartitioning() throws Exception {
        when(rows.forEach(
                        eq(lease),
                        eq(patient),
                        eq(TABLES),
                        eq("preventions_scope"),
                        eq(source.manifest()),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        eq(control),
                        any()))
                .thenReturn(mock(CdsExportNativeCaptureDto.class));
        assertThatThrownBy(this::classify).hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyScopeMustIncludeTheMetadataAliasAndExactlyMatchTheNativeManifest_forPreventionClassificationServiceContract() {
        assertThatThrownBy(
                        () ->
                                service.classify(
                                        lease,
                                        patient,
                                        Set.of("CVCImmunization", "CVCMapping"),
                                        source,
                                        receipt,
                                        1000,
                                        4L << 20,
                                        control))
                .hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        verifyNoInteractions(configuration, rows);
    }

    @Test
    void shouldVerifyAForeignPatientRowCannotBeHidden_byAValidClassifier() {
        var fields = new TreeMap<>(row(2, "PAP").fields());
        fields.put("demographic_no", "28");
        metadata =
                List.of(
                        row(1, "Td"),
                        new CdsExportCapturedTextRowDto(
                                "preventions_scope",
                                CdsExportPatientTableSourceReader.key(2),
                                fields));
        assertThatThrownBy(this::classify).hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyACancellationAtTheLastBoundaryRemainsCancellation_forPreventionClassificationServiceContract() {
        doThrow(new java.util.concurrent.CancellationException("CANCELLED_AFTER_PLAN"))
                .when(control)
                .checkpoint();
        assertThatThrownBy(this::classify)
                .isInstanceOf(java.util.concurrent.CancellationException.class)
                .hasMessage("CANCELLED_AFTER_PLAN");
    }

    @Test
    void shouldVerifyACapturedProgramRestrictionIsStillAReviewPartitionRatherThanAGrant_forPreventionClassificationServiceContract() {
        var fields = new TreeMap<>(row(1, "Td").fields());
        fields.put("restrictToProgram", "1");
        fields.put("programNo", "1");
        metadata =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "preventions_scope",
                                CdsExportPatientTableSourceReader.key(1),
                                fields),
                        row(2, "PAP"));
        assertThatCode(
                        () -> {
                            var result = classify();
                            assertThat(result.reviews())
                                    .containsEntry(
                                            1,
                                            CdsExportPreventionScopePlanner.Review.PROGRAM_SCOPE);
                            assertThat(result.immunizations()).isEmpty();
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldVerifyEmbeddedClassificationUsesTheExplicitSnapshotApiAndPreservesTheCompletePartition_forPreventionClassificationServiceContract()
            throws Exception {
        when(configuration.readEmbedded(lease, patient, TABLES, source, 1000, 4L << 20, control))
                .thenReturn(classifier(source.manifest().plaintextSha256()));
        var result =
                service.classifyEmbedded(lease, patient, TABLES, source, 1000, 4L << 20, control);
        assertThat(result.immunizations()).containsExactly(1);
        assertThat(result.otherPreventions()).containsExactly(2);
        verify(configuration, never())
                .restore(any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any());
        verify(captures).authorizeSources(lease, patient, TABLES, control);
    }

    @Test
    void shouldVerifyEmbeddedClassificationCannotReturnAForeignGenerationOrARevokedPartition_forPreventionClassificationServiceContract()
            throws Exception {
        when(configuration.readEmbedded(lease, patient, TABLES, source, 1000, 4L << 20, control))
                .thenReturn(classifier("c".repeat(64)));
        assertThatThrownBy(
                        () ->
                                service.classifyEmbedded(
                                        lease, patient, TABLES, source, 1000, 4L << 20, control))
                .hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        verifyNoInteractions(rows);
        when(configuration.readEmbedded(lease, patient, TABLES, source, 1000, 4L << 20, control))
                .thenReturn(classifier(source.manifest().plaintextSha256()));
        doThrow(new SecurityException("EMBEDDED_PLAN_REVOKED"))
                .when(captures)
                .authorizeSources(lease, patient, TABLES, control);
        assertThatThrownBy(
                        () ->
                                service.classifyEmbedded(
                                        lease, patient, TABLES, source, 1000, 4L << 20, control))
                .hasMessage("EMBEDDED_PLAN_REVOKED");
    }

    private static CdsExportPreventionClassifier classifier(String digest) throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                digest,
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='PAP' layout='PAPMAM' headingName='Screenings'/></preventions>"
                        .getBytes(StandardCharsets.UTF_8),
                List.of());
    }

    private static CdsExportCapturedTextRowDto row(int id, String type) {
        var fields = new TreeMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("demographic_no", "27");
        fields.put("prevention_type", type);
        fields.put("snomedId", null);
        fields.put("restrictToProgram", null);
        fields.put("programNo", null);
        return new CdsExportCapturedTextRowDto(
                "preventions_scope", CdsExportPatientTableSourceReader.key(id), fields);
    }
}
