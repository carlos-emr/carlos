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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #5856 (#6161 review): the Messenger attachment of a patient's "current" chart notes, sent to a third
 * party, leaves out archived notes and every CPP entry the chart hides (archived, locked, or "Hide
 * Cpp"), while the chart's own Print All keeps printing what it always printed. Also: the attachment's
 * notes go through the chart's program filter, with the program taken from the chart context the
 * session remembers, since the Messenger request carries no parameters. FAKE notes only.
 */
@Tag("unit")
@DisplayName("Chart print for the Messenger attachment leaves out archived and hidden entries")
class CaseManagementPrintMessengerNotesUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 900012;
    private static final String PROVIDER = "999998";
    private static final String PROGRAM = "10016";

    @TempDir
    Path documentDirectory;

    private CaseManagementManager notes;
    private CarlosProperties properties;
    private LoggedInInfo login;

    // Notes of the patient: the body's own, and CPP (Social History) entries.
    private final CaseManagementNote current = note(41L, false, false);
    private final CaseManagementNote locked = note(42L, true, false);
    private final CaseManagementNote archived = note(43L, false, true);
    private final CaseManagementNote cppShown = note(51L, false, false);
    private final CaseManagementNote cppArchived = note(52L, false, true);
    private final CaseManagementNote cppHidden = note(53L, false, false);
    private final CaseManagementNote cppLocked = note(54L, true, false);
    // An older revision of the hidden CPP entry (same note UUID, lower row id): the body can hold it
    // when the entry is edited while a print runs, though the CPP query returns only the latest row.
    private final CaseManagementNote cppHiddenOlderRevision = note(50L, false, false);

    private static CaseManagementNote note(long id, boolean isLocked, boolean isArchived) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setDemographic_no(String.valueOf(PATIENT));
        note.setProviderNo(PROVIDER);
        note.setObservation_date(new Date(1_700_000_000_000L + id));
        note.setNote("FAKE note " + id);
        note.setLocked(isLocked);
        note.setArchived(isArchived);
        return note;
    }

    @BeforeEach
    void setUp() {
        notes = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        cppHidden.setUuid("11111111-1111-4111-8111-111111111153");
        cppHiddenOlderRevision.setUuid(cppHidden.getUuid());
        List<CaseManagementNote> all = List.of(current, locked, archived, cppShown, cppArchived, cppHidden, cppLocked,
                cppHiddenOlderRevision);
        when(notes.getNotes(String.valueOf(PATIENT))).thenReturn(all);
        for (CaseManagementNote note : all) {
            when(notes.getNote(note.getId().toString())).thenReturn(note);
        }
        // Social History is the one CPP issue with entries; the other CPP codes have none.
        Issue socialHistory = new Issue();
        socialHistory.setId(501L);
        when(notes.getIssueInfoByCode(eq(PROVIDER), anyString())).thenReturn(List.of());
        when(notes.getIssueInfoByCode(PROVIDER, "SocHistory")).thenReturn(List.of(socialHistory));
        when(notes.getNotes(eq(String.valueOf(PATIENT)), any(String[].class))).thenAnswer(call -> {
            String[] issues = call.getArgument(1);
            return issues.length == 1 && "501".equals(issues[0])
                    ? List.of(cppShown, cppArchived, cppHidden, cppLocked) : List.of();
        });
        CaseManagementNoteExt hideCpp = new CaseManagementNoteExt();
        hideCpp.setNoteId(cppHidden.getId());
        hideCpp.setKeyVal(CaseManagementNoteExt.HIDECPP);
        hideCpp.setValue("1");
        when(notes.getExtByNote(cppHidden.getId())).thenReturn(List.of(hideCpp));
        properties = mock(CarlosProperties.class);
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn(PROVIDER);
    }

    /** As the Messenger composer asks for it: locked, archived and hidden entries left out. */
    private static CaseManagementPrint messengerPrint() {
        CaseManagementPrint print = new CaseManagementPrint();
        print.leaveOutLockedNotes();
        print.leaveOutArchivedAndHiddenNotes();
        return print;
    }

    /** The notes body the print hands its PDF writer, for an "All notes" print with the CPP. */
    private List<CaseManagementNote> printedNotes(CaseManagementPrint print, HttpServletRequest request)
            throws Exception {
        try (var configuration = mockStatic(CarlosProperties.class);
             var pdfs = mockConstruction(CaseManagementPrintPdf.class);
             var _ = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);

            print.doPrint(login, PATIENT, true, new String[0], true, false, false, false, false, false,
                    null, null, request, new ByteArrayOutputStream());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CaseManagementNote>> printed = ArgumentCaptor.forClass(List.class);
            verify(pdfs.constructed().getFirst()).printNotes(printed.capture());
            return printed.getValue();
        }
    }

    /** A chart print request of this patient with no program chosen (the print then filters nothing). */
    private static MockHttpServletRequest chartRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", Integer.toString(PATIENT));
        ChartProgramContext.remember(request, null);
        return request;
    }

    /** Like the Messenger composer's request: the same session, and no parameter at all. */
    private static HttpServletRequest parameterless(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return null;
            }

            @Override
            public String[] getParameterValues(String name) {
                return null;
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return Map.of();
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.emptyEnumeration();
            }
        };
    }

    @Test
    @DisplayName("should leave archived notes and hidden, archived or locked CPP entries out of the Messenger attachment")
    void shouldLeaveOutArchivedNotesAndHiddenCppEntries_forTheMessengerAttachment() throws Exception {
        // The shown CPP entry is printed in its CPP section, not in the body.
        assertThat(printedNotes(messengerPrint(), chartRequest())).containsExactly(current);
    }

    @Test
    @DisplayName("should leave an older revision of a hidden CPP entry out of the Messenger attachment too")
    void shouldLeaveOutAnOlderRevisionOfAHiddenCppEntry_forTheMessengerAttachment() throws Exception {
        // The body holds revision 50 of the hidden entry; the CPP query returns only its latest row, 53.
        assertThat(printedNotes(messengerPrint(), chartRequest()))
                .doesNotContain(cppHiddenOlderRevision, cppHidden)
                .containsExactly(current);
    }

    @Test
    @DisplayName("should keep printing archived notes and hidden CPP entries in the chart's own Print All")
    void shouldKeepArchivedNotesAndHiddenCppEntries_inTheChartsOwnPrintAll() throws Exception {
        assertThat(printedNotes(new CaseManagementPrint(), chartRequest()))
                .containsExactlyInAnyOrder(current, locked, archived, cppArchived, cppHidden, cppLocked,
                        cppHiddenOlderRevision);
    }

    @Test
    @DisplayName("should refuse the Messenger switch without the CPP, where hidden entries are recognised")
    void shouldRefuseTheMessengerSwitch_withoutTheCpp() {
        CaseManagementPrint print = messengerPrint();
        try (var configuration = mockStatic(CarlosProperties.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(() ->
                    print.doPrint(login, PATIENT, true, new String[0], false, false, false, false, false, false,
                            null, null, chartRequest(), new ByteArrayOutputStream()));
        }
    }

    @Test
    @DisplayName("should filter the Messenger attachment's notes by the program the session remembers for the chart")
    void shouldFilterTheMessengerAttachment_byTheChartsRememberedProgram() throws Exception {
        MockHttpServletRequest chart = new MockHttpServletRequest();
        chart.setParameter("demographicNo", Integer.toString(PATIENT));
        ChartProgramContext.remember(chart, PROGRAM);
        // The program shows the user only the current note and the shown CPP entry.
        Set<Long> readable = Set.of(current.getId(), cppShown.getId());
        when(notes.filterNotes(eq(login), eq(PROVIDER), anyCollection(), eq(PROGRAM))).thenAnswer(call -> {
            Collection<CaseManagementNote> given = call.getArgument(2);
            return given.stream().filter(note -> readable.contains(note.getId())).toList();
        });
        CaseManagementPrint print = messengerPrint();
        // The composer's request is another request of the same session: no parameters, and none of the
        // chart request's attributes, so the program can only come from what the session remembers.
        MockHttpServletRequest messenger = new MockHttpServletRequest("POST", "/messenger/Doc2PDF");
        messenger.setSession(chart.getSession());

        List<CaseManagementNote> printed = printedNotes(print, parameterless(messenger));

        assertThat(printed).containsExactly(current);
        // Once for the body and once per CPP issue code.
        verify(notes, atLeastOnce()).filterNotes(eq(login), eq(PROVIDER), anyCollection(), eq(PROGRAM));
    }
}
