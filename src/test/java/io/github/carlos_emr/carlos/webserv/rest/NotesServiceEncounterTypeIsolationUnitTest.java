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
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.NoteDisplayLocal;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

/**
 * A new note's encounter type in {@code /ws/rs/notes/{demographicNo}/getCurrentNote} comes from
 * the requested patient's own chart entry (issue #4673). It used to come from the login-wide
 * {@code EctSessionBean}, which names whichever chart was entered last in any window, so another
 * patient's visit type replaced this note's. As before, an entry's type takes precedence over
 * the type the request names.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("NotesService new-note encounter type patient isolation")
class NotesServiceEncounterTypeIsolationUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 900001;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CaseManagementManager manager = mock(CaseManagementManager.class);
    private MockHttpServletRequest request;
    private NotesService service;

    @BeforeEach
    void enterAnotherChartLast() throws Exception {
        request = new MockHttpServletRequest();
        EctSessionBean other = new EctSessionBean();
        other.demographicNo = "900002";
        other.encType = "telephone encounter with client";
        request.getSession().setAttribute("EctSessionBean", other);

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
    @DisplayName("should not take another chart's encounter type when this patient's chart was not entered")
    void shouldLeaveTypeBlank_whenOnlyAnotherChartWasEntered() {
        assertThat(newNoteEncounterType(null)).isEmpty();
    }

    @Test
    @DisplayName("should default to this patient's own entry type when the request names none")
    void shouldUsePatientsOwnEntryType_whenRequestNamesNone() {
        rememberEntry(String.valueOf(PATIENT), "face to face encounter with client");

        assertThat(newNoteEncounterType(null)).isEqualTo("face to face encounter with client");
    }

    @Test
    @DisplayName("should keep the requested type when only another chart was entered")
    void shouldKeepRequestedType_whenOnlyAnotherChartWasEntered() {
        assertThat(newNoteEncounterType("email encounter with client")).isEqualTo("email encounter with client");
    }

    @Test
    @DisplayName("should apply this patient's own entry type over the requested type, as before")
    void shouldApplyOwnEntryType_overRequestedType() {
        rememberEntry(String.valueOf(PATIENT), "face to face encounter with client");

        assertThat(newNoteEncounterType("email encounter with client")).isEqualTo("face to face encounter with client");
    }

    private void rememberEntry(String patient, String encounterType) {
        EctSessionBean entry = new EctSessionBean();
        entry.demographicNo = patient;
        entry.providerNo = "999998";
        entry.encType = encounterType;
        EncounterPatientContext.rememberEncounter(request, entry);
        // rememberEncounter also exposes the entry to its own request; a later REST call has none.
        request.removeAttribute("EctSessionBean");
    }

    private String newNoteEncounterType(String requestedType) {
        ObjectNode body = JSON.createObjectNode().put("note_edit", "new");
        if (requestedType != null) body.put("encType", requestedType);
        try (var displays = mockConstruction(NoteDisplayLocal.class)) {
            service.getCurrentNote(PATIENT, body);
            assertThat(displays.constructed()).hasSize(1);
        }
        ArgumentCaptor<CaseManagementNote> note = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).getEditors(note.capture());
        assertThat(note.getValue().getDemographic_no()).isEqualTo(String.valueOf(PATIENT));
        return note.getValue().getEncounter_type();
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = NotesService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
