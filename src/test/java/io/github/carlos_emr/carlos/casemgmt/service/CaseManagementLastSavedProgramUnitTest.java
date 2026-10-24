/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.casemgmt.common.EChartNoteEntry;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A readable note in another program must never become the automatic editor or Rx append target.
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementLastSavedProgramUnitTest extends CarlosUnitTestBase {
    private static final String PATIENT = "1001";
    private static final String PROVIDER = "999998";
    private CaseManagementManagerImpl manager;
    private CaseManagementNoteDAO dao;

    @BeforeEach
    void setUp() {
        dao = mock(CaseManagementNoteDAO.class);
        manager = spy(new CaseManagementManagerImpl());
        manager.setCaseManagementNoteDAO(dao);
        // Both programs are readable. Read permission must not make a note an append target.
        doAnswer(invocation -> new ArrayList<>(invocation.<Collection<EChartNoteEntry>>getArgument(1)))
                .when(manager).filterNotes1(eq(PROVIDER), any(), anyString());
        doAnswer(invocation -> {
            CaseManagementNote note = mock(CaseManagementNote.class);
            when(note.getProviderNo()).thenReturn(PROVIDER);
            return note;
        }).when(manager).getNote(anyString());
    }

    @Test
    void shouldSelectOlderNoteInRequestedProgram_whenNewerOtherProgramIsReadable() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(
                row(31L, "other", "8", PROVIDER, 3000), row(30L, "owned", "7", PROVIDER, 2000)));
        CaseManagementNote expected = note("30", PROVIDER);

        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isSameAs(expected);

        verify(manager, never()).getNote("31");
    }

    @Test
    void shouldReturnNoNote_whenOnlyAnotherProgramHasReadableUnsignedNotes() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenReturn(List.of(row(31L, "other", "8", PROVIDER, 3000)));

        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isNull();

        verify(manager, never()).getNote(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "0")
    void shouldKeepNoProgramSentinelIsolated_whenRequestedProgramIsLegacy(String requestedProgram) {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(
                row(31L, "other", "8", PROVIDER, 3000), row(30L, "owned", "0", PROVIDER, 2000)));
        CaseManagementNote expected = note("30", PROVIDER);

        assertThat(manager.getLastSaved(requestedProgram, PATIENT, PROVIDER)).isSameAs(expected);

        verify(manager, never()).getNote("31");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRetainLegacyUnassignedNotes_whenStoredProgramIsMissing(String storedProgram) {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenReturn(List.of(row(30L, "legacy", storedProgram, PROVIDER, 2000)));
        CaseManagementNote expected = note("30", PROVIDER);

        assertThat(manager.getLastSaved("0", PATIENT, PROVIDER)).isSameAs(expected);
    }

    @Test
    void shouldKeepProviderIsolation_whenAnotherProviderHasTheNewestNoteInSameProgram() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(
                row(31L, "other", "7", "999997", 3000), row(30L, "owned", "7", PROVIDER, 2000)));
        note("31", "999997");
        CaseManagementNote expected = note("30", PROVIDER);

        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isSameAs(expected);
    }

    @Test
    void shouldNotRestoreSupersededRevision_whenLatestRevisionBelongsToAnotherProgram() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(
                row(31L, "same-note", "8", PROVIDER, 3000), row(30L, "same-note", "7", PROVIDER, 2000)));

        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isNull();

        verify(manager, never()).getNote(anyString());
    }

    @Test
    void shouldStillRequireReadAccess_whenMatchingProgramHasAnUnsignedNote() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenReturn(List.of(row(30L, "owned", "7", PROVIDER, 2000)));
        doReturn(new ArrayList<EChartNoteEntry>()).when(manager).filterNotes1(eq(PROVIDER), any(), eq("7"));

        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isNull();

        verify(manager, never()).getNote(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "0007", "2147483647"})
    void shouldCompareProgramIdentifiersNumerically_withoutTruncatingSupportedIds(String program) {
        String stored = Integer.toString(Integer.parseInt(program));
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenReturn(List.of(row(30L, "owned", stored, PROVIDER, 2000)));
        CaseManagementNote expected = note("30", PROVIDER);

        assertThat(manager.getLastSaved(program, PATIENT, PROVIDER)).isSameAs(expected);
    }

    @Test
    void shouldReturnNoMatchingNote_whenLegacyProgramArgumentExceedsIntegerRange() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenReturn(List.of(row(30L, "owned", "7", PROVIDER, 2000)));

        assertThat(manager.getLastSaved(Long.toString(Long.MAX_VALUE), PATIENT, PROVIDER)).isNull();

        verify(manager, never()).getNote(anyString());
    }

    @Test
    void shouldPropagateDatabaseFailure_insteadOfSilentlyCreatingAnotherNote() {
        when(dao.getUnsignedRawNoteInfoMapByDemographic(PATIENT))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> manager.getLastSaved("7", PATIENT, PROVIDER))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }

    private CaseManagementNote note(String id, String provider) {
        CaseManagementNote note = mock(CaseManagementNote.class);
        when(note.getProviderNo()).thenReturn(provider);
        doReturn(note).when(manager).getNote(id);
        return note;
    }

    private Map<String, Object> row(long id, String uuid, String program, String provider, long timestamp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("uuid", uuid);
        row.put("program_no", program);
        row.put("providerNo", provider);
        row.put("observation_date", new Date(timestamp));
        row.put("reporter_caisi_role", "1");
        return row;
    }
}
