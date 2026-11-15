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
import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNoteSourceReaderUnitTest {
    private final CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
    private final CdsExportNoteAccessDao scopes = mock(CdsExportNoteAccessDao.class);
    private final CdsExportNoteScopeProjectionService projection =
            mock(CdsExportNoteScopeProjectionService.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportNoteAccessDto scope = new CdsExportNoteAccessDto(91, 27, "17", "1");
    private CdsExportNoteSourceReader reader;

    @BeforeEach
    void setup() {
        reader = new CdsExportNoteSourceReader(notes, scopes, projection);
        when(projection.read(27, 8192)).thenReturn(List.of(scope));
        when(scopes.readScopes(27, 8192)).thenReturn(List.of(scope));
        when(notes.countCdsExportRows(27)).thenReturn(1L);
    }

    @Test
    void shouldRefuseUnpreparedPayloadReads_beforeCallingTheOwningDao() {
        assertThatThrownBy(() -> reader.count(27))
                .hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        assertThatThrownBy(() -> reader.capture(27, null))
                .hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        verifyNoInteractions(notes, scopes, projection);
    }

    @Test
    void shouldBindOneImmutablePatientScope_andRequireItsLiveNoteCapability() throws Exception {
        var planned = reader.forCapture(27, control);
        planned.authorizeReadScope(27, control);
        verify(control).authorizeNote(CdsCategory.CLINICAL_NOTES, scope);
        assertThat(planned.noteAccess()).containsExactly(scope);
        assertThat(planned.count(27)).isEqualTo(1);
        verify(notes).countCdsExportRows(27);
        assertThatThrownBy(() -> planned.noteAccess().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRefuseAChangedProgramOrAddedNote_beforeReadingAnyPayload() throws Exception {
        var planned = reader.forCapture(27, control);
        when(scopes.readScopes(27, 8192))
                .thenReturn(List.of(new CdsExportNoteAccessDto(91, 27, "18", "1")));
        assertThatThrownBy(() -> planned.capture(27, null)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        when(scopes.readScopes(27, 8192))
                .thenReturn(List.of(scope, new CdsExportNoteAccessDto(92, 27, "17", "1")));
        assertThatThrownBy(() -> planned.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        verifyNoInteractions(notes);
    }

    @Test
    void shouldRefuseACountMismatch_andNeverSilentlyDropAnUnexpectedPhysicalRow() throws Exception {
        var planned = reader.forCapture(27, control);
        when(notes.countCdsExportRows(27)).thenReturn(2L);
        assertThatThrownBy(() -> planned.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        verify(notes, never()).findCdsExportRowsAfter(anyInt(), nullable(Long.class), anyInt());
    }

    @Test
    void shouldRefuseCrossPatientReuse_beforeAnyAdditionalDiscoveryOrPayloadRead()
            throws Exception {
        var planned = reader.forCapture(27, control);
        clearInvocations(notes, scopes, projection);
        assertThatThrownBy(() -> planned.count(28))
                .hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        assertThatThrownBy(() -> planned.authorizeReadScope(28, control))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes, scopes, projection);
    }

    @Test
    void shouldRefuseUnorderedDuplicateOrForeignPatientScopes_beforeSealingACapturePlan() {
        for (var invalid :
                List.of(
                        List.of(scope, scope),
                        List.of(new CdsExportNoteAccessDto(91, 28, "17", "1")),
                        List.of(new CdsExportNoteAccessDto(92, 27, "17", "1"), scope))) {
            when(projection.read(27, 8192)).thenReturn(invalid);
            assertThatThrownBy(() -> reader.forCapture(27, control))
                    .hasMessage("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
        }
        verifyNoInteractions(notes, scopes);
    }

    /**
     * A chart of 16,391 notes (two full pages and a part) through the real scope DAO, the real
     * projection service and the reader. The scope read used to stop at 8,192 notes and hold
     * the patient ({@code CDS_EXPORT_RESOURCE_LIMIT}); it now reads a page of notes a
     * statement, each continuing after the last note of the one before.
     */
    @Test
    void shouldReadEveryNoteScopeOfALongChart_aPageOfNotesAStatement() throws Exception {
        int count = 2 * 8192 + 7;
        var statements = new ArrayList<long[]>();
        var manager = mock(jakarta.persistence.EntityManager.class);
        when(manager.createNativeQuery(anyString(), eq(Object[].class)))
                .thenAnswer(
                        call -> {
                            String sql = call.getArgument(0);
                            assertThat(sql).contains("note_id > :after ORDER BY note_id");
                            var query = mock(jakarta.persistence.Query.class, RETURNS_SELF);
                            long[] read = new long[2];
                            when(query.setParameter(eq("after"), any()))
                                    .thenAnswer(
                                            bound -> {
                                                read[0] = bound.<Long>getArgument(1);
                                                return query;
                                            });
                            when(query.setMaxResults(anyInt()))
                                    .thenAnswer(
                                            bound -> {
                                                read[1] = bound.<Integer>getArgument(0);
                                                return query;
                                            });
                            when(query.getResultList())
                                    .thenAnswer(
                                            ignored -> {
                                                statements.add(read.clone());
                                                var page = new ArrayList<Object[]>();
                                                for (long id = read[0] + 1;
                                                        id <= count && page.size() < read[1];
                                                        id++)
                                                    page.add(new Object[] {id, "17", "1", "0"});
                                                return page;
                                            });
                            return query;
                        });
        // The foreign-revision probe finds nothing.
        when(manager.createNativeQuery(anyString()))
                .thenAnswer(
                        call -> {
                            var query = mock(jakarta.persistence.Query.class, RETURNS_SELF);
                            when(query.getResultList()).thenReturn(List.of());
                            return query;
                        });
        var dao = new CdsExportNoteAccessDao();
        org.springframework.test.util.ReflectionTestUtils.setField(dao, "entityManager", manager);
        var chartReader =
                new CdsExportNoteSourceReader(
                        notes, dao, new CdsExportNoteScopeProjectionService(dao));
        when(notes.countCdsExportRows(27)).thenReturn((long) count);

        var planned = chartReader.forCapture(27, control);

        assertThat(planned.noteAccess()).hasSize(count);
        assertThat(planned.noteAccess().getFirst().noteId()).isEqualTo(1);
        assertThat(planned.noteAccess().getLast().noteId()).isEqualTo(count);
        // Three statements: after 0, after 8192, after 16384; none asks for more than a page.
        assertThat(statements).extracting(read -> read[0]).containsExactly(0L, 8192L, 16384L);
        assertThat(statements).allMatch(read -> read[1] == 8192);
        // Every note is authorized, one by one, and the snapshot's own scope read agrees.
        planned.authorizeReadScope(27, control);
        verify(control, times(count)).authorizeNote(eq(CdsCategory.CLINICAL_NOTES), any());
        assertThat(planned.count(27)).isEqualTo(count);
    }

    @Test
    void shouldLeaveRestoredGenerationsToTheirSealedScopes_insteadOfReadingTodaysNoteUniverse() {
        reader.authorizeReadScope(27, control);
        verifyNoInteractions(notes, scopes, projection, control);
    }
}
