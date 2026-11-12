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
package io.github.carlos_emr.carlos.casemgmt.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #5856: a print that leaves the chart for someone else (a Messenger attachment) leaves locked notes out
 * of its notes body; the chart's own Print is unchanged. FAKE notes only.
 */
@Tag("unit")
@DisplayName("Chart print leaves locked notes out when asked")
class CaseManagementPrintLockedNotesUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 900011;

    @TempDir
    Path documentDirectory;

    private CaseManagementNote open;
    private CaseManagementNote locked;
    private CarlosProperties properties;
    private LoggedInInfo login;

    private static CaseManagementNote note(long id, boolean isLocked) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setDemographic_no(String.valueOf(PATIENT));
        note.setProviderNo("999998");
        note.setObservation_date(new Date(1_700_000_000_000L + id));
        note.setNote("FAKE note " + id);
        note.setLocked(isLocked);
        return note;
    }

    @BeforeEach
    void setUp() {
        CaseManagementManager notes = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        open = note(21L, false);
        locked = note(22L, true);
        when(notes.getNotes(String.valueOf(PATIENT))).thenReturn(List.of(open, locked));
        when(notes.getNote("21")).thenReturn(open);
        when(notes.getNote("22")).thenReturn(locked);
        properties = mock(CarlosProperties.class);
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
    }

    /** The notes body the print hands its PDF writer, for an "All notes" print. */
    private List<CaseManagementNote> printedNotes(CaseManagementPrint print) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", Integer.toString(PATIENT));
        ChartProgramContext.remember(request, null);
        try (var configuration = mockStatic(CarlosProperties.class);
             var pdfs = mockConstruction(CaseManagementPrintPdf.class);
             var _ = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);

            print.doPrint(login, PATIENT, true, new String[0], false, false, false, false, false, false,
                    null, null, request, new ByteArrayOutputStream());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CaseManagementNote>> printed = ArgumentCaptor.forClass(List.class);
            verify(pdfs.constructed().getFirst()).printNotes(printed.capture());
            return printed.getValue();
        }
    }

    @Test
    @DisplayName("should leave locked notes out of the notes body when asked")
    void shouldLeaveOutLockedNotes_whenAsked() throws Exception {
        CaseManagementPrint print = new CaseManagementPrint();
        print.leaveOutLockedNotes();

        assertThat(printedNotes(print)).containsExactly(open);
    }

    @Test
    @DisplayName("should print the notes body as before when not asked")
    void shouldPrintEveryNoteAsBefore_whenNotAsked() throws Exception {
        assertThat(printedNotes(new CaseManagementPrint())).containsExactlyInAnyOrder(open, locked);
    }
}
