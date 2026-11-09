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
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportImportOptionsSourceReaderUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            HASH = "a".repeat(64),
            COPY = "course-7-student-123456-program-11",
            OTHER = "course-7-student-123457-program-12";
    private final CdsImportRunDao dao = mock(CdsImportRunDao.class);
    private final CdsExportConfigurationService configuration =
            mock(CdsExportConfigurationService.class);
    private final CdsExportRetainedImportOptionsReader originals =
            mock(CdsExportRetainedImportOptionsReader.class);
    private CdsExportImportOptionsSourceReader reader;
    private CdsExportRetainedImportOptionsReader.Options options;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private CdsExportRawKeyRowDto row;

    @BeforeEach
    void setup() throws Exception {
        Plan.IMPORT_RUNS.columns().forEach(column -> fields.put(column, null));
        fields.put("id", RUN);
        fields.put("dry_run", "0");
        fields.put("profile", "omd-5.2");
        fields.put("options_digest", HASH);
        fields.put("ruleset_digest", HASH);
        fields.put("snapshot_location", "/owned/job-" + HASH + ".json");
        row = raw();
        when(dao.countCdsExportRetainedRows(eq(27), anySet())).thenReturn(1L);
        when(dao.findCdsExportRetainedRowsAfter(eq(27), anySet(), isNull(), eq(32)))
                .thenReturn(List.of(row));
        when(dao.readAppliedCdsExportCopyKeys(eq(27), eq(RUN), anySet())).thenReturn(List.of(COPY));
        options =
                new CdsExportRetainedImportOptionsReader.Options(
                        RUN,
                        HASH,
                        HASH,
                        HASH,
                        "omd-5.2",
                        true,
                        365,
                        Map.of(COPY, "b".repeat(64), OTHER, "c".repeat(64)),
                        "d".repeat(64));
        when(configuration.retainedImportRoot()).thenReturn(Path.of("/owned"));
        when(originals.readProtected(any(), any(), any())).thenAnswer(call -> options);
        reader =
                new CdsExportImportOptionsSourceReader(
                        dao, configuration, originals, Set.of("Demographics"));
    }

    private CdsExportRawKeyRowDto raw() {
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        fields.forEach(
                (key, value) -> {
                    byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                    cells.put(
                            key,
                            new CdsExportRawCellDto(
                                    value == null,
                                    bytes == null ? 0 : bytes.length,
                                    bytes == null
                                            ? CdsExportRawCellDto.EMPTY_SHA256
                                            : HexFormat.of()
                                                    .formatHex(
                                                            CdsImportKeys.digest().digest(bytes)),
                                    bytes));
                });
        return new CdsExportRawKeyRowDto(RUN, cells);
    }

    private String capture() throws Exception {
        return capture(() -> {});
    }

    private String capture(Runnable checkpoint) throws Exception {
        var output = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        output,
                        index,
                        Set.of(reader.table()),
                        100000,
                        8L << 20,
                        checkpoint,
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), 1);
                            reader.capture(27, writer, checkpoint);
                            writer.endTable();
                        });
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void shouldCaptureOnlyThisPatientsAppliedCopyKeys_fromTheVerifiedOriginalPlan()
            throws Exception {
        String encoded = capture();
        assertThat(encoded)
                .contains(COPY)
                .doesNotContain(OTHER, "input", "snapshot_location", "command", "/owned");
        var decoded =
                CdsExportRetainedImportOptionsReader.decodeCaptured(
                        new CdsExportCapturedTextRowDto("cds_import_run", RUN, fields), encoded);
        assertThat(decoded.copyOptionsSha256())
                .containsExactlyEntriesOf(Map.of(COPY, "b".repeat(64)));
        assertThat(decoded.timeshiftDays()).isEqualTo(365);
    }

    @Test
    void shouldAvoidOriginalFiles_forUnappliedImportAttempts() throws Exception {
        when(dao.readAppliedCdsExportCopyKeys(eq(27), eq(RUN), anySet())).thenReturn(List.of());
        assertThat(capture()).isEmpty();
        verifyNoInteractions(originals, configuration);
    }

    @Test
    void shouldCaptureClinicalImports_withoutCourseCopyMetadata() throws Exception {
        options =
                new CdsExportRetainedImportOptionsReader.Options(
                        RUN, HASH, HASH, HASH, "omd-5.2", false, 0);
        when(dao.readAppliedCdsExportCopyKeys(eq(27), eq(RUN), anySet()))
                .thenReturn(List.of("clinical"));
        assertThat(capture())
                .contains("\"synthetic\":false", "\"timeshiftDays\":0")
                .doesNotContain("course-");
    }

    @Test
    void shouldBindAppliedEvidence_toTheRequestsSelectedCategories() throws Exception {
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var command =
                new CdsExportCommand(
                        "source",
                        "989879",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        selected,
                        "recipient",
                        "audit");
        var request = reader.forRequest(command);
        var values = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        new ByteArrayOutputStream(),
                        Set.of(reader.table()),
                        100000,
                        8L << 20,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), 1);
                            request.capture(27, writer);
                            writer.endTable();
                        });
        verify(dao)
                .readAppliedCdsExportCopyKeys(
                        27, RUN, Set.of("Demographics", "AllergiesAndAdverseReactions"));
    }

    @Test
    void shouldRefuseMissingCopyBindings_insteadOfBorrowingAnotherStudentsProjection() {
        when(dao.readAppliedCdsExportCopyKeys(eq(27), eq(RUN), anySet()))
                .thenReturn(List.of("course-7-student-123458-program-13"));
        assertThatThrownBy(this::capture).hasMessage("CDS_EXPORT_RETAINED_COPY_OPTIONS_MISMATCH");
    }

    @Test
    void shouldRefuseAChangedOrUnavailableOriginal_withoutEmittingAFrozenOptionsRow()
            throws Exception {
        when(originals.readProtected(any(), any(), any()))
                .thenThrow(new java.io.IOException("ORIGINAL_UNAVAILABLE"));
        assertThatThrownBy(this::capture).hasMessage("ORIGINAL_UNAVAILABLE");
    }

    @Test
    void shouldRejectDuplicateRunRows_insteadOfReducingTheDeclaredSourceCount() {
        when(dao.findCdsExportRetainedRowsAfter(eq(27), anySet(), isNull(), eq(32)))
                .thenReturn(List.of(row, row));
        assertThatThrownBy(this::capture).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void shouldPropagateCancellation_duringMetadataReadsBeforePrivateFileAccess() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(
                        () ->
                                capture(
                                        () -> {
                                            if (calls.incrementAndGet() == 8)
                                                throw new IllegalStateException("CANCELLED_SOURCE");
                                        }))
                .hasMessage("CANCELLED_SOURCE");
        verifyNoInteractions(originals);
    }

    @Test
    void shouldBoundRunDiscovery_beforeCapture() {
        when(dao.countCdsExportRetainedRows(eq(27), anySet())).thenReturn(4097L);
        assertThatThrownBy(() -> reader.count(27))
                .hasMessage("CDS_EXPORT_IMPORT_OPTIONS_RUN_LIMIT");
    }

    @Test
    void shouldUseAnExplicitTrustedProvenanceSchema_withAllRequiredMetadataDependencies()
            throws Exception {
        assertThat(new CdsExportSourceSchemaValidator().verify(reader.schema(), reader.columns()))
                .matches("[0-9a-f]{64}");
        assertThat(reader.schema().engine()).isEqualTo("TRUSTED_IMPORT_PROVENANCE");
        assertThat(reader.requiredTables())
                .containsExactlyInAnyOrder(
                        "cds_import_run", "cds_import_patient", "cds_import_unit");
        assertThat(reader.receiptColumns()).isEmpty();
    }
}
