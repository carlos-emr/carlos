/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
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
import org.hibernate.exception.SnapshotIsolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("casemgmt")
class CaseManagementNoteRevisionConflictUnitTest extends CarlosUnitTestBase {
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
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.setParameter("demographicNo", "101");
        request.setParameter("nId", "501");
        request.setParameter("noteTxt", "Unsaved draft");
        request.setParameter("numIssues", "0");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("case_program_id", "7");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
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
        note.setAppointmentNo(42);
        note.setNote("Original");
        note.setHistory("Original");
        when(manager.getNote("501")).thenReturn(note);
        form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        form.setIssueCheckList(new ArrayList<>());
        request.getSession().setAttribute("caseManagementEntryForm101", form);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean(ProgramManager.class)).thenReturn(mock(ProgramManager.class));
        when(context.getBean(AdmissionManager.class)).thenReturn(mock(AdmissionManager.class));
        doReturn(context).when(action).getSpringContext();
        doReturn("Unsaved draft").when(action).getCaseNote_note();
        doReturn("Note saved").when(action).getText("note.saved");
        doNothing().when(action).addActionMessage(anyString());
    }

    private RuntimeException snapshotConflict() {
        return new JpaSystemException(new SnapshotIsolationException("snapshot conflict",
                new SQLException("record changed", "HY000", 1020), "select 1"));
    }

    private void assertDraftRetained() {
        assertDraftRetained("Unsaved draft");
    }

    private void assertDraftRetained(String expectedSessionText) {
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(form.getCaseNote().getId()).isEqualTo(501L);
        assertThat(form.getCaseNote().getNote()).isEqualTo(expectedSessionText);
        assertThat(request.getSession().getAttribute("caseManagementEntryForm101")).isSameAs(form);
        assertThat(request.getSession().getAttribute("casemgmtNoteLock101")).isSameAs(lock);
        assertThat(lock.getNoteId()).isEqualTo(501L);
        verify(locks, never()).merge(any());
        verify(manager, never()).deleteTmpSave(any(), any(), any());
        verify(manager, never()).getEditors(any(CaseManagementNote.class));
    }

    @Test
    void shouldKeepDraftAndLock_whenAjaxSaveHasWrappedSnapshotConflict() throws Exception {
        when(manager.saveNoteRevisionSimple(any())).thenThrow(snapshotConflict());
        assertThat(action.ajaxsave()).isNull();
        assertDraftRetained();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sign", "verify", "toBill"})
    void shouldReturnRecoveryViewWithRawDraft_whenSaveAndExitHasWrappedSnapshotConflict(String operation) throws Exception {
        String rawDraft = "  </textarea><script>window.draftExecuted=true</script>&literal\r\nSecond line\n  ";
        String processedDraft = "Unsaved draft\n[[Added signature before conflict]]";
        request.setParameter("caseNote_note", rawDraft);
        request.setParameter(operation, "toBill".equals(operation) ? "true" : "on");
        doAnswer(call -> {
            CaseManagementNote submitted = call.getArgument(1);
            submitted.setNote(processedDraft);
            throw snapshotConflict();
        }).when(manager).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any());
        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(request.getAttribute("noteRevisionConflict")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(rawDraft);
        assertDraftRetained(processedDraft);
    }

    @Test
    void shouldCaptureGetterDraftBeforeSaving_whenRawRequestParameterIsAbsent() throws Exception {
        String rawDraft = "  Original untrimmed draft\n  ";
        doReturn(rawDraft, "Unsaved draft").when(action).getCaseNote_note();
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any())).thenThrow(snapshotConflict());
        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(request.getAttribute("noteRevisionConflict")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(rawDraft);
        assertDraftRetained();
    }

    @Test
    void shouldReturnWindowCloseError_whenSaveAndExitHasLostItsSessionLock() throws Exception {
        when(locks.find(10L)).thenReturn(null);
        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("noteRevisionConflict")).isNull();
        assertThat(request.getAttribute("unsavedNoteText")).isNull();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldPropagateOtherDatabaseFailures_insteadOfReportingARevisionConflict() {
        RuntimeException failure = new DataIntegrityViolationException("invalid row");
        when(manager.saveNoteRevisionSimple(any())).thenThrow(failure);
        assertThatThrownBy(() -> action.ajaxsave()).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(manager, never()).deleteTmpSave(any(), any(), any());
    }
}
