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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementIssueNotesDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code /ws/rs/notes/{demographicNo}/getCurrentNote} answers with a note that is not saved yet
 * instead of failing (issue #4784). Building the answer asked the note's link table whether the
 * note was a document, a prescription or an eForm note, with the unsaved note's null id, which the
 * DAO refuses, so every request for a new note, and every resumed temporary save of a note never
 * saved, answered HTTP 500. The answer is built here with the real {@code NoteDisplayLocal}; the
 * encounter-type tests replace it.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("NotesService answer for a note not saved yet")
class NotesServiceNewNoteResponseUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 900003;
    private static final String FACE_TO_FACE = "face to face encounter with client";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CaseManagementManager manager = mock(CaseManagementManager.class);
    private final CaseManagementNoteLinkDAO links = mock(CaseManagementNoteLinkDAO.class);
    private MockHttpServletRequest request;
    private NotesService service;

    @BeforeEach
    void setUp() throws Exception {
        // As JpqlQueryHelper does: a null positional parameter is refused.
        when(links.getLastLinkByNote(isNull())).thenThrow(new IllegalArgumentException(
                "Null value for positional parameter ?1. Use IS NULL in JPQL instead of binding a null value."));
        registerMock(CaseManagementNoteLinkDAO.class, links);
        registerMock(CaseManagementIssueNotesDao.class, mock(CaseManagementIssueNotesDao.class));

        request = new MockHttpServletRequest();
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setSession(request.getSession());
        service = new NotesService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }

            @Override
            protected HttpServletRequest getHttpServletRequest() {
                return request;
            }
        };
        inject("caseManagementMgr", manager);
        inject("programManager2", mock(ProgramManager2.class));
        inject("programMgr", mock(ProgramManager.class));
    }

    @Test
    @DisplayName("should answer an unsaved note of the patient's own visit type when a new note is requested")
    void shouldAnswerUnsavedNote_whenNewNoteIsRequested() {
        EctSessionBean entry = new EctSessionBean();
        entry.demographicNo = String.valueOf(PATIENT);
        entry.providerNo = "999998";
        entry.encType = FACE_TO_FACE;
        EncounterPatientContext.rememberEncounter(request, entry);
        request.removeAttribute("EctSessionBean");

        NoteTo1 note = service.getCurrentNote(PATIENT, JSON.createObjectNode().put("note_edit", "new"));

        assertThat(note.getNoteId()).isNull();
        assertThat(note.getEncounterType()).isEqualTo(FACE_TO_FACE);
        assertThat(note.isDocument()).isFalse();
        assertThat(note.isRxAnnotation()).isFalse();
        assertThat(note.isEformData()).isFalse();
        verifyNoInteractions(links);
    }

    @Test
    @DisplayName("should answer the temporary save of a note never saved with its text")
    void shouldAnswerTemporarySave_whenItsNoteWasNeverSaved() {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setNoteId(0);
        draft.setNote("draft text");
        when(manager.restoreTmpSave(anyString(), anyString(), any())).thenReturn(draft);
        ObjectNode body = JSON.createObjectNode();

        NoteTo1 note = service.getCurrentNote(PATIENT, body);

        assertThat(note.getNoteId()).isNull();
        assertThat(note.getNote()).isEqualTo("draft text");
        verifyNoInteractions(links);
    }

    @Test
    @DisplayName("should report no link for a note not saved yet without asking the link table")
    void shouldReportNoLink_forNoteNotSavedYet() {
        CaseManagementNote note = new CaseManagementNote();

        assertThat(note.isDocumentNote()).isFalse();
        assertThat(note.isRxAnnotation()).isFalse();
        assertThat(note.isEformData()).isFalse();
        assertThat(note.isEmailNote()).isFalse();
        verifyNoInteractions(links);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = NotesService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
