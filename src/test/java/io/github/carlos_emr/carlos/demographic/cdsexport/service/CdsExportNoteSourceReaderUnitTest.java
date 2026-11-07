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

@Tag("unit") @Tag("cds-export")
class CdsExportNoteSourceReaderUnitTest {
    private final CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
    private final CdsExportNoteAccessDao scopes = mock(CdsExportNoteAccessDao.class);
    private final CdsExportNoteScopeProjectionService projection = mock(CdsExportNoteScopeProjectionService.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportNoteAccessDto scope = new CdsExportNoteAccessDto(91, 27, "17", "1");
    private CdsExportNoteSourceReader reader;
    @BeforeEach void setup() {
        reader = new CdsExportNoteSourceReader(notes, scopes, projection);
        when(projection.read(27, 8192)).thenReturn(List.of(scope)); when(scopes.readScopes(27, 8192)).thenReturn(List.of(scope)); when(notes.countCdsExportRows(27)).thenReturn(1L);
    }
    @Test void shouldRefuseUnpreparedPayloadReads_beforeCallingTheOwningDao() {
        assertThatThrownBy(() -> reader.count(27)).hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        assertThatThrownBy(() -> reader.capture(27, null)).hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED"); verifyNoInteractions(notes, scopes, projection);
    }
    @Test void shouldBindOneImmutablePatientScope_andRequireItsLiveNoteCapability() throws Exception {
        var planned = reader.forCapture(27, control); planned.authorizeReadScope(27, control);
        verify(control).authorizeNote(CdsCategory.CLINICAL_NOTES, scope); assertThat(planned.noteAccess()).containsExactly(scope);
        assertThat(planned.count(27)).isEqualTo(1); verify(notes).countCdsExportRows(27);
        assertThatThrownBy(() -> planned.noteAccess().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void shouldRefuseAChangedProgramOrAddedNote_beforeReadingAnyPayload() throws Exception {
        var planned = reader.forCapture(27, control);
        when(scopes.readScopes(27, 8192)).thenReturn(List.of(new CdsExportNoteAccessDto(91, 27, "18", "1")));
        assertThatThrownBy(() -> planned.capture(27, null)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        when(scopes.readScopes(27, 8192)).thenReturn(List.of(scope, new CdsExportNoteAccessDto(92, 27, "17", "1")));
        assertThatThrownBy(() -> planned.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED"); verifyNoInteractions(notes);
    }
    @Test void shouldRefuseACountMismatch_andNeverSilentlyDropAnUnexpectedPhysicalRow() throws Exception {
        var planned = reader.forCapture(27, control); when(notes.countCdsExportRows(27)).thenReturn(2L);
        assertThatThrownBy(() -> planned.count(27)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        verify(notes, never()).findCdsExportRowsAfter(anyInt(), nullable(Long.class), anyInt());
    }
    @Test void shouldRefuseCrossPatientReuse_beforeAnyAdditionalDiscoveryOrPayloadRead() throws Exception {
        var planned = reader.forCapture(27, control); clearInvocations(notes, scopes, projection);
        assertThatThrownBy(() -> planned.count(28)).hasMessage("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        assertThatThrownBy(() -> planned.authorizeReadScope(28, control)).isInstanceOf(SecurityException.class); verifyNoInteractions(notes, scopes, projection);
    }
    @Test void shouldRefuseUnorderedDuplicateOrForeignPatientScopes_beforeSealingACapturePlan() {
        for (var invalid : List.of(List.of(scope, scope), List.of(new CdsExportNoteAccessDto(91, 28, "17", "1")),
                List.of(new CdsExportNoteAccessDto(92, 27, "17", "1"), scope))) {
            when(projection.read(27, 8192)).thenReturn(invalid);
            assertThatThrownBy(() -> reader.forCapture(27, control)).hasMessage("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
        }
        verifyNoInteractions(notes, scopes);
    }
    @Test void shouldLeaveRestoredGenerationsToTheirSealedScopes_insteadOfReadingTodaysNoteUniverse() {
        reader.authorizeReadScope(27, control); verifyNoInteractions(notes, scopes, projection, control);
    }
}
