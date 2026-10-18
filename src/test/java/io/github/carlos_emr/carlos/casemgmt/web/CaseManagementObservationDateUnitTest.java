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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The encounter date a note save reads back from the eChart editor (issue #4711).
 *
 * <p>Editing a stored note posted its observation date as {@code java.sql.Timestamp.toString()}
 * ({@code 2026-10-15 19:14:08.0}) when the browser's language had no date format, and Sign &amp;
 * Save answered HTTP 500 from an unhandled {@code ParseException}. A date the server cannot read is
 * now refused before anything is changed: nothing is saved, the chart window shows why and keeps the
 * draft to copy, and the note lock and session note are left as they were. A date the editor
 * legitimately writes — the English canonical form, or the interface language's months from the
 * localized calendar — is saved by both the full-form save and the note-switch save.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("CaseManagementEntry2Action encounter date")
class CaseManagementObservationDateUnitTest extends CarlosUnitTestBase {
    private static final Date STORED_DATE = Timestamp.valueOf("2026-10-15 19:14:08.0");

    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private CasemgmtNoteLockDao locks;
    private CasemgmtNoteLock lock;
    private CaseManagementEntryFormBean form;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.setParameter("demographicNo", "101");
        request.setParameter("nId", "501");
        request.setParameter("noteTxt", "Edited FAKE note");
        request.setParameter("numIssues", "0");
        request.setParameter("sign", "on");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("case_program_id", "7");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        var permissions = mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        when(permissions.hasPrivilege(eq(user), anyString(), anyString(), any())).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        injectDependency(action, "securityInfoManager", permissions);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        locks = mock(CasemgmtNoteLockDao.class);
        injectDependency(action, "casemgmtNoteLockDao", locks);
        lock = new CasemgmtNoteLock();
        lock.setId(10L);
        lock.setNoteId(501L);
        lock.setSessionId(request.getSession().getId());
        request.getSession().setAttribute("casemgmtNoteLock101", lock);
        when(locks.find(10L)).thenReturn(lock);
        note = new CaseManagementNote();
        note.setId(501L);
        note.setUuid(UUID.randomUUID().toString());
        note.setDemographic_no("101");
        note.setProgram_no("7");
        note.setNote("Original FAKE note");
        note.setHistory("Original FAKE note");
        note.setObservation_date(STORED_DATE);
        when(manager.getNote("501")).thenReturn(note);
        form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        form.setIssueCheckList(new ArrayList<>());
        request.getSession().setAttribute("caseManagementEntryForm101", form);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean(ProgramManager.class)).thenReturn(mock(ProgramManager.class));
        when(context.getBean(AdmissionManager.class)).thenReturn(mock(AdmissionManager.class));
        doReturn(context).when(action).getSpringContext();
        doReturn("Edited FAKE note").when(action).getCaseNote_note();
        doReturn("Note saved").when(action).getText("note.saved");
        doNothing().when(action).addActionMessage(anyString());
    }

    private static Date at(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant());
    }

    private CaseManagementNote savedFullFormNote() throws Exception {
        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).saveCaseManagementNote(any(), saved.capture(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any());
        return saved.getValue();
    }

    private void stubFullFormSave() throws Exception {
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            CaseManagementNote revision = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            revision.setId(600L);
            return revision;
        });
    }

    private void assertRefusedWithoutChange(String rawDraft) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("observationDateRejected")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(rawDraft);
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any());
        verify(manager, never()).deleteTmpSave(any(), any(), any());
        verify(locks, never()).merge(any());
        verify(locks, never()).removeForSession(any(), any(), any(), any());
        // The session note is the stored revision the chart still shows: nothing on it may change.
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(note.getNote()).isEqualTo("Original FAKE note");
        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);
        assertThat(note.isSigned()).isFalse();
        assertThat(request.getSession().getAttribute("casemgmtNoteLock101")).isSameAs(lock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-15 19:14:08.0", "15-Okt.-2026 19:14", "31-Feb-2026 10:00"})
    void shouldRefuseSignAndSaveWithDraft_whenEncounterDateIsUnreadable(String posted) throws Exception {
        String rawDraft = "Edited FAKE note\r\nwith an addendum  ";
        request.setParameter("caseNote_note", rawDraft);
        action.setObservation_date(posted);

        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");

        assertRefusedWithoutChange(rawDraft);
    }

    @Test
    void shouldRefuseSaveWithDraft_whenEncounterDateIsUnreadable() throws Exception {
        request.setParameter("caseNote_note", "Edited FAKE note");
        action.setObservation_date("2026-10-15 19:14:08.0");

        assertThat(action.save()).isEqualTo("windowCloseError");

        assertRefusedWithoutChange("Edited FAKE note");
    }

    @Test
    @Tag("release-review")
    void shouldRefuseFutureDateBeforeWriting_whenSavingWithoutExit() throws Exception {
        stubFullFormSave();
        request.setParameter("caseNote_note", "Edited FAKE note");
        action.setObservation_date("01-Jan-2099 10:00");

        assertThat(action.save()).isEqualTo("windowCloseError");
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any());
        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);
    }

    @Test
    void shouldSignAndSaveWithPostedDate_whenEncounterDateIsCanonical() throws Exception {
        stubFullFormSave();
        action.setObservation_date("15-Oct-2026 19:14");

        assertThat(action.saveAndExit()).isEqualTo("windowClose");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("observationDateRejected")).isNull();
        CaseManagementNote saved = savedFullFormNote();
        assertThat(saved.getObservation_date()).isEqualTo(at(2026, 10, 15, 19, 14));
        assertThat(saved.isSigned()).isTrue();
    }

    @Test
    void shouldSaveCalendarDate_whenInterfaceIsFrench() throws Exception {
        stubFullFormSave();
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        action.setObservation_date("03-févr.-2026 08:30");

        assertThat(action.saveAndExit()).isEqualTo("windowClose");

        assertThat(savedFullFormNote().getObservation_date()).isEqualTo(at(2026, 2, 3, 8, 30));
    }

    @Test
    void shouldKeepStoredDate_whenEditorPostsNoDate() throws Exception {
        stubFullFormSave();
        action.setObservation_date("");

        assertThat(action.saveAndExit()).isEqualTo("windowClose");

        assertThat(savedFullFormNote().getObservation_date()).isEqualTo(STORED_DATE);
    }

    private String addIssue() throws Exception {
        doReturn("").when(action).getDemoName(anyString());
        doReturn("").when(action).getDemoAge(anyString());
        doReturn("").when(action).getDemoDOB(anyString());
        return action.issueAdd();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "2026-10-15 19:14:08.0"})
    void shouldKeepStoredDate_whenIssueAddPostsNoReadableDate(String posted) throws Exception {
        // IssueSearch.jsp, the only page that adds an issue this way, posts no date at all. Nulling
        // the note's date here made the next save stamp "now" over the stored encounter date.
        action.setObservation_date(posted);

        assertThat(addIssue()).isEqualTo("view");

        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);
    }

    @Test
    void shouldTakePostedDate_whenIssueAddPostsReadableDate() throws Exception {
        action.setObservation_date("14-Oct-2026 10:05");

        assertThat(addIssue()).isEqualTo("view");

        assertThat(note.getObservation_date()).isEqualTo(at(2026, 10, 14, 10, 5));
    }

    @Test
    void shouldRefuseNoteSwitchSaveBeforeLoadingNote_whenEncounterDateIsUnreadable() throws Exception {
        request.setParameter("obsDate", "2026-10-15 19:14:08.0");

        assertThat(action.ajaxsave()).isNull();

        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).getNote(any());
        verify(manager, never()).saveNoteRevisionSimple(any());
        verify(manager, never()).saveAndUpdateCaseIssues(any());
        verify(locks, never()).merge(any());
        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);
    }

    @Test
    void shouldSaveCanonicalDateOnNoteSwitch_whenInterfaceIsFrench() throws Exception {
        // The editor now renders English months; the switch save used to read them in the
        // browser's language, where "Oct" is not a month.
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        request.setParameter("obsDate", "15-Oct-2026 19:14");
        when(manager.saveNoteRevisionSimple(any())).thenAnswer(call -> call.getArgument(0));

        action.ajaxsave();

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).saveNoteRevisionSimple(saved.capture());
        assertThat(saved.getValue().getObservation_date()).isEqualTo(at(2026, 10, 15, 19, 14));
        assertThat(response.getStatus()).isEqualTo(200);
    }
    @ParameterizedTest
    @ValueSource(strings={"save","sign","verify"})
    void shouldRefuseFutureDateWithDraftBeforePersistence(String operation) throws Exception {
        String draft="FAKE future visit draft\r\nretained";request.setParameter("caseNote_note",draft);
        action.setObservation_date("01-Jan-2099 10:00");
        if ("verify".equals(operation)) request.setParameter("verify","on");
        String result="save".equals(operation)?action.save():action.saveAndExit();
        assertThat(result).isEqualTo("windowCloseError");assertRefusedWithoutChange(draft);
        assertThat(request.getAttribute("DateError")).isNotNull();
    }

    @Test
    void shouldRefuseFutureNoteSwitchDate_beforeLoadingOrChangingNote() throws Exception {
        request.setParameter("obsDate","01-Jan-2099 10:00");
        assertThat(action.ajaxsave()).isNull();assertThat(response.getStatus()).isEqualTo(400);
        verify(manager,never()).getNote(any());verify(manager,never()).saveNoteRevisionSimple(any());
        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);assertThat(note.isSigned()).isFalse();
    }

    @Test
    void shouldKeepDraftDate_whenIssueAddPostsFutureDate() throws Exception {
        action.setObservation_date("01-Jan-2099 10:00");
        assertThat(addIssue()).isEqualTo("windowCloseError");assertThat(response.getStatus()).isEqualTo(400);
        assertThat(note.getObservation_date()).isEqualTo(STORED_DATE);
        verify(manager,never()).saveAndUpdateCaseIssues(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01-Jan-2099 10:00", "2026-10-15 19:14:08.0"})
    void shouldRefuseInvalidDateBeforeDeletingIssueOrChangingSession(String posted) throws Exception {
        var issue = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue();
        issue.setId(71L);
        issue.setDemographic_no(101);
        var entry = new CheckBoxBean();
        entry.setIssue(issue);
        form.getIssueCheckList().add(entry);
        action.setDeleteId("0");
        action.setObservation_date(posted);

        assertThat(action.issueDelete()).isEqualTo("windowCloseError");

        assertRefusedWithoutChange("Edited FAKE note");
        assertThat(form.getIssueCheckList()).containsExactly(entry);
        verify(manager, never()).deleteIssueById(any());
        verify(manager, never()).removeIssueFromCPP(any(), any());
        verify(manager, never()).saveAndUpdateCaseIssues(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "14-Oct-2026 10:05"})
    void shouldKeepIssueDeletionAvailableWithValidOrOmittedDate(String posted) throws Exception {
        var issue = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue();
        issue.setId(71L);
        issue.setDemographic_no(101);
        var entry = new CheckBoxBean();
        entry.setIssue(issue);
        form.getIssueCheckList().add(entry);
        // Issues can be removed while the editor holds a new, as-yet-unsaved note.
        note.setId(null);
        action.setDeleteId("0");
        action.setObservation_date(posted);
        doReturn("").when(action).getDemoName(anyString());
        doReturn("").when(action).getDemoAge(anyString());
        doReturn("").when(action).getDemoDOB(anyString());
        var properties = mock(io.github.carlos_emr.CarlosProperties.class);
        try (var config = mockStatic(io.github.carlos_emr.CarlosProperties.class)) {
            config.when(io.github.carlos_emr.CarlosProperties::getInstance).thenReturn(properties);

            assertThat(action.issueDelete()).isEqualTo("view");

            assertThat(response.getStatus()).isEqualTo(200);
            verify(manager).deleteIssueById(issue);
            assertThat(form.getIssueCheckList()).isEmpty();
        }
    }

}
