/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Exercises the real autosave action's program binding and persistence-failure response. */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementAutosaveContextUnitTest extends CarlosUnitTestBase {
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private CasemgmtNoteLockDao locks;
    private CaseManagementNote note;
    private LoggedInInfo user;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        request.setParameter("programId", "22");
        request.setParameter("note_id", "501");
        request.setParameter("note", "Current unsaved text");
        ChartProgramContext.remember(request, "11");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        injectDependency(action, "securityInfoManager", security);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        locks = mock(CasemgmtNoteLockDao.class);
        injectDependency(action, "casemgmtNoteLockDao", locks);
        CasemgmtNoteLock lock = new CasemgmtNoteLock();
        lock.setId(10L);
        lock.setNoteId(501L);
        lock.setSessionId(request.getSession().getId());
        request.getSession().setAttribute("casemgmtNoteLock101", lock);
        when(locks.find(10L)).thenReturn(lock);
        note = new CaseManagementNote();
        note.setId(501L);
        note.setDemographic_no("101");
        note.setProgram_no("11");
        note.setNote("Previous persisted text");
        doReturn(note).when(action).getCaseNote();
        when(manager.getNote("501")).thenReturn(note);
        when(manager.filterNotes(user, "999998", java.util.List.of(note), "11")).thenReturn(java.util.List.of(note));
    }

    @Test
    void shouldReplaceOnlyCapturedProgramDraft_whenAnotherWindowAndRawParameterSelectAnotherProgram() {
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setSession(request.getSession());
        other.setParameter("demographicNo", "101");
        ChartProgramContext.remember(other, "22");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(manager).replaceTmpSave("999998", "101", "11", "501", "Current unsaved text");
        verify(manager).getNote("501");
        verify(manager).filterNotes(user, "999998", java.util.List.of(note), "11");
        verify(manager).hasAccessRight("note-read-only", "access", "999998", "101", "11");
        verifyNoMoreInteractions(manager);
        assertThat(note.getNote()).isEqualTo("Current unsaved text");
    }

    @Test
    void shouldReportFailureWithoutChangingEditorModel_whenDraftReplacementFails() {
        doThrow(new IllegalStateException("database unavailable")).when(manager)
                .replaceTmpSave("999998", "101", "11", "501", "Current unsaved text");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(note.getNote()).isEqualTo("Previous persisted text");
    }

    @Test
    void shouldReplaceDraftWithEmptyText_whenClinicianClearsTheEditor() {
        request.setParameter("note", "");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(manager).replaceTmpSave("999998", "101", "11", "501", "");
        assertThat(note.getNote()).isEmpty();
    }

    @Test
    void shouldRefuseMissingTextWithoutReadingLocks_whenAutosaveBodyIsIncomplete() {
        request.removeParameter("note");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(locks);
        verifyOnlyProgramAuthorization("11");
        assertThat(note.getNote()).isEqualTo("Previous persisted text");
    }

    @Test
    void shouldRefuseAutosaveBeforeNoteOrLockAccess_whenTheCapturedProgramIsReadOnly() {
        when(manager.hasAccessRight("note-read-only", "access", "999998", "101", "11")).thenReturn(true);
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setSession(request.getSession());
        other.setParameter("demographicNo", "101");
        ChartProgramContext.remember(other, "22");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(note.getNote()).isEqualTo("Previous persisted text");
        verifyNoInteractions(locks);
        verifyOnlyProgramAuthorization("11");
    }

    @Test
    void shouldKeepTheOtherProgramWritable_whenOneChartForTheSamePatientIsReadOnly() {
        when(manager.hasAccessRight("note-read-only", "access", "999998", "101", "11")).thenReturn(true);
        action.autosave();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(note.getNote()).isEqualTo("Previous persisted text");

        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setSession(request.getSession());
        other.setParameter("demographicNo", "101");
        ChartProgramContext.remember(other, "22");
        request.setParameter(ChartProgramContext.PARAMETER, ChartProgramContext.token(other));
        request.setParameter("programId", "11");
        when(manager.filterNotes(user, "999998", java.util.List.of(note), "22")).thenReturn(java.util.List.of(note));
        response = new MockHttpServletResponse();
        action.response = response;

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(note.getNote()).isEqualTo("Current unsaved text");
        assertThat(note.getProgram_no()).isEqualTo("11");
        verify(manager).hasAccessRight("note-read-only", "access", "999998", "101", "22");
        verify(manager).replaceTmpSave("999998", "101", "22", "501", "Current unsaved text");
        verify(manager, never()).replaceTmpSave("999998", "101", "11", "501", "Current unsaved text");
    }

    @Test
    void shouldRefuseDraftReplacement_whenReferencedNoteBelongsToAnotherPatient() {
        note.setDemographic_no("202");

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(manager, never()).replaceTmpSave(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(note.getNote()).isEqualTo("Previous persisted text");
    }

    @Test
    void shouldRefuseDraftReplacement_whenCapturedProgramCannotReadReferencedNote() {
        when(manager.filterNotes(user, "999998", java.util.List.of(note), "11")).thenReturn(java.util.List.of());

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(manager, never()).replaceTmpSave(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRefuseStaleEditorDraft_whenSelectedFormHasAdvancedToAnotherNote() {
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        CaseManagementNote current = new CaseManagementNote();
        current.setId(502L);
        current.setProgram_no("11");
        form.setCaseNote(current);
        request.getSession().setAttribute(ChartProgramContext.formKey(request, "101"), form);

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(409);
        verify(manager, never()).replaceTmpSave(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRetainAuthorizedCrossProgramEditing_whenSelectedNoteIsVisibleInOriginatingChart() {
        note.setProgram_no("22");
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        request.getSession().setAttribute(ChartProgramContext.formKey(request, "101"), form);

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(manager).replaceTmpSave("999998", "101", "11", "501", "Current unsaved text");
        assertThat(note.getProgram_no()).isEqualTo("22");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-note", "2147483648", "9999999999999999999999999"})
    void shouldRefuseMalformedReferenceWithoutReplacingDraft_whenNoteIdentifierIsInvalid(String reference) {
        request.setParameter("note_id", reference);

        action.autosave();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyOnlyProgramAuthorization("11");
        assertThat(note.getNote()).isEqualTo("Previous persisted text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseAutosaveWithoutReadingLocks_whenRequestIsNotPost(String method) {
        request.setMethod(method);
        action.autosave();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(locks, manager);
    }

    @Test
    void shouldRefuseAutosaveWithoutReadingLocks_whenPatientWriteAccessIsDenied() {
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);
        action.autosave();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(locks, manager);
    }

    @Test
    void shouldRefuseAutosaveWithoutReadingLocks_whenPatientAliasesConflict() {
        request.setParameter("demographic_no", "202");
        action.autosave();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(locks, manager);
    }

    @Test
    void shouldRefuseAutosave_whenNoCapturedProgramExists() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        request.setParameter("programId", "22");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        action.request = request;
        action.autosave();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(locks, manager);
    }

    private void verifyOnlyProgramAuthorization(String program) {
        verify(manager).hasAccessRight("note-read-only", "access", "999998", "101", program);
        verifyNoMoreInteractions(manager);
    }
}
