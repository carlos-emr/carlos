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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryOccurrenceServiceUnitTest {
    private static final String HASH = "a".repeat(64), PATIENT = "b".repeat(64);
    private final CdsExportNativeCaptureService captures =
            mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader receipts =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportNoteRevisionInventoryService revisions =
            mock(CdsExportNoteRevisionInventoryService.class);
    private final CdsExportRetainedValuesService values =
            mock(CdsExportRetainedValuesService.class);
    private final CdsExportRetainedReceiptVerificationService verification =
            mock(CdsExportRetainedReceiptVerificationService.class);
    private final CdsExportCohortService.Patient patient =
            mock(CdsExportCohortService.Patient.class);
    private final CdsExportLeaseDto lease = mock(CdsExportLeaseDto.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportRetainedSectionWriter.Values streams =
            mock(CdsExportRetainedSectionWriter.Values.class);
    private final Set<CdsCategory> categories =
            EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
    private final CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(1000, 1L << 20, 100, 1L << 20, 100, 100);
    private CdsExportPatientCaptureDto source;
    private Set<String> tables;
    private CdsExportStagedFileDto payload;
    private CdsExportHistoryOccurrenceService service;

    @BeforeEach
    void setup() throws Exception {
        categories.add(CdsCategory.DEMOGRAPHICS);
        when(patient.patient()).thenReturn(27);
        var document = mock(CdsExportJobDocumentDto.class);
        var command = mock(CdsExportCommand.class);
        when(lease.document()).thenReturn(document);
        when(document.command()).thenReturn(command);
        when(command.categories()).thenReturn(categories);
        service =
                new CdsExportHistoryOccurrenceService(
                        captures, receipts, revisions, values, verification);
        prepareCapture(Map.of());
        when(revisions.inventory(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(
                        new CdsExportNoteRevisionPlanner.Result(27, HASH, List.of(), Map.of(), 0));
        var index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                HASH, Map.of(), Set.of(), Set.of()));
        doAnswer(
                        call -> {
                            call.<CdsExportRetainedValuesService.Work>getArgument(10)
                                    .use(index, List.of(), streams);
                            return null;
                        })
                .when(values)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
    }

    /** An empty capture of the history tables plus any extra (empty) captured tables. */
    private void prepareCapture(Map<String, Set<String>> extra) throws Exception {
        prepareCapture(extra, true);
    }

    private void prepareCapture(Map<String, Set<String>> extra, boolean noteLinks)
            throws Exception {
        var expected = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        for (var entry : extra.entrySet()) {
            expected.put(entry.getKey(), new CdsExportNativeCaptureReader.Table(entry.getValue(), 0));
            scopes.put(entry.getKey(), new CdsExportCapturedTableDto(entry.getValue(), 0, HASH));
        }
        for (var plan :
                List.of(
                        Plan.CASE_NOTES,
                        Plan.CASE_NOTE_EXT,
                        Plan.CASE_NOTE_LINK,
                        Plan.CASE_NOTE_ISSUE_LINK,
                        Plan.CASE_ISSUES,
                        Plan.ISSUE_CATALOGUE,
                        Plan.DEMOGRAPHIC_MERGES)) {
            if (plan == Plan.CASE_NOTE_LINK && !noteLinks) continue;
            var columns = Set.copyOf(plan.columns());
            expected.put(plan.table(), new CdsExportNativeCaptureReader.Table(columns, 0));
            scopes.put(plan.table(), new CdsExportCapturedTableDto(columns, 0, HASH));
        }
        tables = Set.copyOf(scopes.keySet());
        var capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(scopes);
        when(capture.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", HASH, 1));
        when(capture.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", HASH, 1));
        source =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", HASH, 1));
        payload = new CdsExportStagedFileDto("4".repeat(64), "PAYLOAD_MANIFEST", HASH, 1);
        var bytes = new ByteArrayOutputStream();
        var indexBytes = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        bytes,
                        indexBytes,
                        tables,
                        1000,
                        1L << 20,
                        () -> {},
                        writer -> {
                            for (var entry : expected.entrySet()) {
                                writer.beginTable(entry.getKey(), entry.getValue().columns(), 0);
                                writer.endTable();
                            }
                        });
        doAnswer(
                        call -> {
                            new CdsExportNativeCaptureReader()
                                    .scan(
                                            new ByteArrayInputStream(bytes.toByteArray()),
                                            new ByteArrayInputStream(indexBytes.toByteArray()),
                                            expected,
                                            1000,
                                            1L << 20,
                                            () -> {},
                                            call.getArgument(7));
                            return capture;
                        })
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        var counts = new HashMap<String, Long>();
        for (String table : tables) if (!table.equals("demographic_merged")) counts.put(table, 0L);
        doReturn(new CdsExportNativeRowReceiptsDto(HASH, counts, 0, 0, Set.of()))
                .when(receipts)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    private static Map<String, Set<String>> screeningTables(boolean catalogue) {
        var result = new TreeMap<String, Set<String>>();
        for (String table : CdsExportPreventionSources.SCREENING_REQUIRED)
            result.put(table, Set.of("id"));
        if (catalogue) {
            result.put("clinical_source_catalogue", Set.of("catalogueSha256"));
            // The catalogue makes ProblemList capture the Disease Registry and episodes too.
            result.putAll(registryTables());
        }
        return result;
    }

    private static Map<String, Set<String>> registryTables() {
        var result = new TreeMap<String, Set<String>>();
        for (String table : CdsExportProblemRegistrySources.TABLES) result.put(table, Set.of("id"));
        return result;
    }

    private CdsExportXmlComposer.Result compose(
            CdsExportHistoryOccurrenceService.Work work, boolean swallow) throws Exception {
        return new CdsExportXmlComposer()
                .composeFromSource(
                        new ByteArrayOutputStream(),
                        HASH,
                        PATIENT,
                        "history-session-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        categories,
                        xml -> {
                            xml.emit(
                                    new CdsExportXmlComposer.Section(
                                            new CdsExportIdentityDto(
                                                    PATIENT, CdsCategory.DEMOGRAPHICS, HASH),
                                            body -> {}));
                            try {
                                service.withSource(
                                        lease, patient, PATIENT, tables, source, payload, bounds,
                                        control, xml, work);
                            } catch (IOException | RuntimeException failure) {
                                if (!swallow) throw failure;
                            }
                        },
                        ignored -> {},
                        1L << 20,
                        () -> {});
    }

    private static void all(CdsExportHistoryOccurrenceService.Source source) throws IOException {
        for (var category : CdsCategory.values())
            if (CdsExportNoteSourceReader.CATEGORIES.contains(category)) {
                var result = source.append(category, ignored -> {});
                assertThat(result.nativeRows()).isZero();
            }
    }

    @Test
    void shouldReadOneHistorySource_forAllSevenWirePositions() throws Exception {
        var result = compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false);
        assertThat(result.occurrences().get(CdsCategory.DEMOGRAPHICS)).isEqualTo(1);
        verify(revisions, times(1))
                .inventory(any(), any(), anySet(), any(), anyLong(), anyLong(), anyLong(), any());
        verify(values, times(1))
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
        verify(captures, times(2))
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void shouldEmitCapturedScreenings_atThePastHealthWirePosition() throws Exception {
        var screenings = mock(CdsExportScreeningOccurrenceService.class);
        when(screenings.append(
                        any(), any(), anyString(), anySet(), any(), any(), any(), any(), any(),
                        any()))
                .thenReturn(2L);
        var registries = mock(CdsExportProblemRegistryOccurrenceService.class);
        service =
                new CdsExportHistoryOccurrenceService(
                        captures, receipts, revisions, values, verification, screenings,
                        registries);
        prepareCapture(screeningTables(true));
        var results = new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                CdsCategory.class);
        compose(
                source -> {
                    for (var category : CdsCategory.values())
                        if (CdsExportNoteSourceReader.CATEGORIES.contains(category))
                            results.put(category, source.append(category, ignored -> {}));
                },
                false);
        assertThat(results.get(CdsCategory.PAST_HEALTH).nativeRows()).isEqualTo(2);
        assertThat(results.get(CdsCategory.PAST_HEALTH).nativeOnlyRows()).isEqualTo(2);
        assertThat(results.get(CdsCategory.PROBLEM_LIST).nativeRows()).isZero();
        verify(screenings, times(1))
                .append(
                        eq(lease), eq(patient), eq(PATIENT), eq(tables), eq(source), any(),
                        eq(bounds), eq(control), any(), any());
    }

    @Test
    void shouldEmitRegistryEntriesAndEpisodes_atTheProblemListWirePosition() throws Exception {
        var registries = mock(CdsExportProblemRegistryOccurrenceService.class);
        when(registries.append(
                        any(), any(), anyString(), anySet(), any(), any(), any(), any(), any(),
                        any()))
                .thenReturn(3L);
        service =
                new CdsExportHistoryOccurrenceService(
                        captures, receipts, revisions, values, verification, null, registries);
        prepareCapture(registryTables());
        var results = new EnumMap<CdsCategory, CdsExportClinicalOccurrenceService.Result>(
                CdsCategory.class);
        compose(
                source -> {
                    for (var category : CdsCategory.values())
                        if (CdsExportNoteSourceReader.CATEGORIES.contains(category))
                            results.put(category, source.append(category, ignored -> {}));
                },
                false);
        assertThat(results.get(CdsCategory.PROBLEM_LIST).nativeRows()).isEqualTo(3);
        assertThat(results.get(CdsCategory.PROBLEM_LIST).nativeOnlyRows()).isEqualTo(3);
        assertThat(results.get(CdsCategory.PAST_HEALTH).nativeRows()).isZero();
        verify(registries, times(1))
                .append(
                        eq(lease), eq(patient), eq(PATIENT), eq(tables), eq(source), any(),
                        eq(bounds), eq(control), any(), any());
    }

    @Test
    void shouldRefuseCapturedRegistryEntries_withoutARegistryAssembler() throws Exception {
        prepareCapture(registryTables());
        assertThatThrownBy(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseACataloguedProblemListCapture_thatLeftOutTheRegistry() throws Exception {
        var extra = new TreeMap<String, Set<String>>();
        for (String table : CdsExportPreventionSources.SCREENING_REQUIRED)
            extra.put(table, Set.of("id"));
        extra.put("clinical_source_catalogue", Set.of("catalogueSha256"));
        service =
                new CdsExportHistoryOccurrenceService(
                        captures,
                        receipts,
                        revisions,
                        values,
                        verification,
                        mock(CdsExportScreeningOccurrenceService.class),
                        mock(CdsExportProblemRegistryOccurrenceService.class));
        prepareCapture(extra);
        assertThatThrownBy(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseACataloguedHistoryCapture_thatLeftOutTheNoteLinks() throws Exception {
        // The catalogue requires casemgmt_note_link with every history category: without it an
        // attached note would be exported without naming the record it is attached to.
        service =
                new CdsExportHistoryOccurrenceService(
                        captures,
                        receipts,
                        revisions,
                        values,
                        verification,
                        mock(CdsExportScreeningOccurrenceService.class),
                        mock(CdsExportProblemRegistryOccurrenceService.class));
        prepareCapture(screeningTables(true), false);
        assertThatThrownBy(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .hasMessage("CDS_EXPORT_HISTORY_CURRENT_CONTEXT_INVALID");
        prepareCapture(screeningTables(true), true);
        assertThatCode(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseCapturedScreenings_withoutAScreeningAssembler() throws Exception {
        prepareCapture(screeningTables(false));
        assertThatThrownBy(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseACataloguedPastHealthCapture_thatLeftOutTheScreenings() throws Exception {
        prepareCapture(Map.of("clinical_source_catalogue", Set.of("catalogueSha256")));
        assertThatThrownBy(() -> compose(CdsExportHistoryOccurrenceServiceUnitTest::all, false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRejectAnOmittedWirePosition_evenWhenThatCategoryIsEmpty() {
        assertThatThrownBy(
                        () ->
                                compose(
                                        source ->
                                                source.append(
                                                        CdsCategory.FAMILY_HISTORY, ignored -> {}),
                                        false))
                .hasMessage("CDS_EXPORT_HISTORY_SOURCE_WORK_INCOMPLETE");
    }

    @Test
    void shouldKeepTheXmlFailed_whenAnIncompleteSourceErrorIsCaught() {
        assertThatThrownBy(
                        () ->
                                compose(
                                        source ->
                                                source.append(
                                                        CdsCategory.FAMILY_HISTORY, ignored -> {}),
                                        true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }

    @Test
    void shouldRejectARepeatedCategory_insteadOfIssuingDuplicateDispositions() {
        assertThatThrownBy(
                        () ->
                                compose(
                                        source -> {
                                            source.append(
                                                    CdsCategory.FAMILY_HISTORY, ignored -> {});
                                            source.append(
                                                    CdsCategory.FAMILY_HISTORY, ignored -> {});
                                        },
                                        false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAReusedSource_afterItsRetainedValueScopeHasClosed() throws Exception {
        var escaped = new AtomicReference<CdsExportHistoryOccurrenceService.Source>();
        compose(
                source -> {
                    escaped.set(source);
                    all(source);
                },
                false);
        assertThatThrownBy(() -> escaped.get().append(CdsCategory.FAMILY_HISTORY, ignored -> {}))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAnUnselectedCategory_beforeReadingOrEmittingItsContent() {
        assertThatThrownBy(
                        () ->
                                compose(
                                        source ->
                                                source.append(
                                                        CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                                        ignored -> {}),
                                        false))
                .hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRejectCrossThreadUse_withoutReturningACompleteSource() {
        var denied = new AtomicReference<Throwable>();
        assertThatThrownBy(
                        () ->
                                compose(
                                        source -> {
                                            var thread =
                                                    Thread.ofPlatform()
                                                            .start(
                                                                    () -> {
                                                                        try {
                                                                            source.append(
                                                                                    CdsCategory
                                                                                            .FAMILY_HISTORY,
                                                                                    ignored -> {});
                                                                        } catch (
                                                                                Throwable failure) {
                                                                            denied.set(failure);
                                                                        }
                                                                    });
                                            try {
                                                thread.join();
                                            } catch (InterruptedException interrupted) {
                                                Thread.currentThread().interrupt();
                                                throw new IOException(interrupted);
                                            }
                                        },
                                        false))
                .hasMessage("CDS_EXPORT_HISTORY_SOURCE_WORK_INCOMPLETE");
        assertThat(denied.get()).hasMessage("CDS_EXPORT_HISTORY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldReauthorizeAfterTheWorkCallback_beforeAcceptingCompleteHistoryCoverage() {
        var revoked = new AtomicBoolean();
        doAnswer(
                        call -> {
                            if (revoked.get())
                                throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
                            return null;
                        })
                .when(captures)
                .authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(
                        () ->
                                compose(
                                        source -> {
                                            all(source);
                                            revoked.set(true);
                                        },
                                        false))
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }

    @Test
    void shouldKeepTheDocumentFailed_whenFinalAuthorizationFailureIsCaughtByTheCaller() {
        var revoked = new AtomicBoolean();
        doAnswer(
                        call -> {
                            if (revoked.get())
                                throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
                            return null;
                        })
                .when(captures)
                .authorizeCapturedSources(any(), any(), anySet(), any(), any());
        assertThatThrownBy(
                        () ->
                                compose(
                                        source -> {
                                            all(source);
                                            revoked.set(true);
                                        },
                                        true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }
}
