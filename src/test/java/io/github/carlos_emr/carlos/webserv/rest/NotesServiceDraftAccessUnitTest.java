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
package io.github.carlos_emr.carlos.webserv.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.NoteDisplayLocal;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Draft REST requests authorize their patient and never dereference another chart's note. */
@Tag("unit")
@Tag("security")
class NotesServiceDraftAccessUnitTest extends CarlosUnitTestBase {
    private static final ObjectMapper JSON = new ObjectMapper();
    private NotesService service;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private CaseManagementNote original;

    @BeforeEach
    void prepare() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("userrole", "doctor");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        login = new LoggedInInfo();
        login.setLoggedInProvider(provider);
        login.setSession(request.getSession());
        service = new NotesService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return login; }
            @Override protected HttpServletRequest getHttpServletRequest() { return request; }
        };
        manager = mock(CaseManagementManager.class);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), anyString(), anyString(), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
        ProgramManager2 programs = mock(ProgramManager2.class);
        ProgramProvider membership = new ProgramProvider();
        membership.setProgramId(11L);
        when(programs.getCurrentProgramInDomain(login, "999998")).thenReturn(membership);
        injectDependency(service, "caseManagementMgr", manager);
        injectDependency(service, "securityInfoManager", security);
        injectDependency(service, "programManager2", programs);
        injectDependency(service, "programMgr", mock(ProgramManager.class));
        original = new CaseManagementNote();
        original.setId(501L);
        original.setDemographic_no("101");
        original.setProgram_no("22");
        original.setNote("Previously saved text");
        when(manager.getNote("501")).thenReturn(original);
        when(manager.filterNotes(login, "999998", java.util.List.of(original), "11"))
                .thenReturn(java.util.List.of(original));
    }

    private NoteTo1 draft(Integer reference, String text) {
        NoteTo1 note = new NoteTo1();
        note.setNoteId(reference);
        note.setNote(text);
        return note;
    }

    private void refused(Runnable request, int status) {
        WebApplicationException error = catchThrowableOfType(request::run, WebApplicationException.class);
        assertThat(error).isNotNull();
        assertThat(error.getResponse().getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Updated text"})
    void shouldPersistExactDraftIncludingEmptyText_whenPatientWriteAndReferenceAccessAllowIt(String text) {
        if (text == null) {
            refused(() -> service.tmpSaveNote(101, draft(null, null)), 400);
            verifyNoInteractions(manager);
            return;
        }
        NoteTo1 submitted = draft(null, text);
        assertThat(service.tmpSaveNote(101, submitted)).isSameAs(submitted);
        verify(manager).replaceTmpSave("999998", "101", "11", "0", text);
    }

    @Test
    void shouldRetainSelectedCrossProgramNote_whenItIsVisibleInTheCurrentProgram() {
        service.tmpSaveNote(101, draft(501, "Cross-program edit"));
        verify(manager).replaceTmpSave("999998", "101", "11", "501", "Cross-program edit");
        assertThat(original.getProgram_no()).isEqualTo("22");
        assertThat(original.getNote()).isEqualTo("Previously saved text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_eChart", "_casemgmt.notes", "record"})
    void shouldRefuseDraftReadAndWriteBeforeLoadingClinicalRows_whenPatientAccessIsDenied(String boundary) {
        if ("record".equals(boundary)) when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        else when(security.hasPrivilege(eq(login), eq(boundary), anyString(), eq("101"))).thenReturn(false);
        refused(() -> service.tmpSaveNote(101, draft(501, "Rejected text")), 403);
        refused(() -> service.getCurrentNote(101, JSON.createObjectNode()), 403);
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "hidden", "missing"})
    void shouldPreserveExistingDraft_whenReferenceCannotBeUsedByThePatient(String boundary) {
        if ("foreign".equals(boundary)) original.setDemographic_no("202");
        if ("hidden".equals(boundary)) when(manager.filterNotes(login, "999998", java.util.List.of(original), "11"))
                .thenReturn(java.util.List.of());
        if ("missing".equals(boundary)) when(manager.getNote("501")).thenReturn(null);
        refused(() -> service.tmpSaveNote(101, draft(501, "Rejected text")), "missing".equals(boundary) ? 409 : 403);
        verify(manager, never()).replaceTmpSave(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(original.getNote()).isEqualTo("Previously saved text");
    }

    @Test
    void shouldRefuseForeignDraftRestorationBeforeChangingTheReferencedNote() {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setNoteId(501);
        draft.setNote("Foreign replacement");
        original.setDemographic_no("202");
        when(manager.restoreTmpSave("999998", "101", "11")).thenReturn(draft);

        refused(() -> service.getCurrentNote(101, JSON.createObjectNode()), 403);

        assertThat(original.getNote()).isEqualTo("Previously saved text");
        verify(manager, never()).getEditors(any(CaseManagementNote.class));
        verify(manager, never()).saveNoteSimple(any());
    }

    @Test
    void shouldRecoverOrphanedDraftAsANewPatientNote_withoutReusingItsMissingReference() {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setNoteId(999);
        draft.setNote("Preserved orphan draft");
        when(manager.restoreTmpSave("999998", "101", "11")).thenReturn(draft);
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            service.getCurrentNote(101, JSON.createObjectNode());
            assertThat(displays.constructed()).hasSize(1);
        }
        ArgumentCaptor<CaseManagementNote> recovered = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).getEditors(recovered.capture());
        assertThat(recovered.getValue().getId()).isNull();
        assertThat(recovered.getValue().getDemographic_no()).isEqualTo("101");
        assertThat(recovered.getValue().getNote()).isEqualTo("Preserved orphan draft");
    }

    @Test
    void shouldRestoreAuthorizedCrossProgramDraftIntoACopy_withoutChangingTheStoredNote() {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setNoteId(501);
        draft.setNote("Authorized draft text");
        when(manager.restoreTmpSave("999998", "101", "11")).thenReturn(draft);
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            service.getCurrentNote(101, JSON.createObjectNode());
            assertThat(displays.constructed()).hasSize(1);
        }
        ArgumentCaptor<CaseManagementNote> restored = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).getEditors(restored.capture());
        assertThat(restored.getValue()).isNotSameAs(original);
        assertThat(restored.getValue().getId()).isEqualTo(501L);
        assertThat(restored.getValue().getProgram_no()).isEqualTo("22");
        assertThat(restored.getValue().getNote()).isEqualTo("Authorized draft text");
        assertThat(original.getNote()).isEqualTo("Previously saved text");
        verify(manager, never()).saveNoteSimple(any());
    }

    @Test
    void shouldInitializeOnlyTheEditorCopy_whenAnAuthorizedReadFindsEmptyStoredHistory() {
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            service.getCurrentNote(101, JSON.createObjectNode().put("noteId", 501));
            assertThat(displays.constructed()).hasSize(1);
        }
        ArgumentCaptor<CaseManagementNote> displayed = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).getEditors(displayed.capture());
        assertThat(displayed.getValue()).isNotSameAs(original);
        assertThat(displayed.getValue().getHistory()).isEqualTo("Previously saved text");
        assertThat(original.getHistory()).isNull();
        verify(manager, never()).saveNoteSimple(any());
    }

    @Test
    void shouldRefuseExplicitForeignNoteBeforeInitializingItsHistory() {
        original.setDemographic_no("202");
        refused(() -> service.getCurrentNote(101, JSON.createObjectNode().put("noteId", 501)), 403);
        assertThat(original.getHistory()).isNull();
        verify(manager, never()).saveNoteSimple(any());
    }

    @Test
    void shouldRefuseIncompleteRequestsWithoutLoadingDrafts_whenBodyIsMissing() {
        refused(() -> service.tmpSaveNote(101, null), 400);
        refused(() -> service.getCurrentNote(101, null), 400);
        verifyNoInteractions(manager);
    }

    @Test
    void shouldReturnFailureWithoutAcknowledgingDraft_whenReplacementFails() {
        doThrow(new IllegalStateException("database unavailable")).when(manager)
                .replaceTmpSave("999998", "101", "11", "0", "New draft");
        refused(() -> service.tmpSaveNote(101, draft(null, "New draft")), 500);
    }
}
