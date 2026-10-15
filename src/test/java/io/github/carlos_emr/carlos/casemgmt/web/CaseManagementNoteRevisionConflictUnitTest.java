/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteSaveResult;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
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
import java.util.List;
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
        ProgramManager programManager = mock(ProgramManager.class);
        AdmissionManager admissionManager = mock(AdmissionManager.class);
        when(context.getBean(ProgramManager.class)).thenReturn(programManager);
        when(context.getBean(AdmissionManager.class)).thenReturn(admissionManager);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSignAppointmentOnlyAfterRevisionSucceeds_whenJsonSignAndExitIsRequested(boolean conflict) throws Exception {
        request.setParameter("demographic_no", "101");
        request.setParameter("noteId", "501");
        request.setParameter("value", "Signed draft");
        request.setParameter("appointment_no", "42");
        request.setParameter("sign", "true");
        request.setParameter("signAndExit", "true");
        note.setRevision("1");
        createAndRegisterMock(ProgramProviderDAO.class);
        createAndRegisterMock(ProviderDefaultProgramDao.class);
        createAndRegisterMock(ProgramAccessDAO.class);
        createAndRegisterMock(SecroleDao.class);
        createAndRegisterMock(RoleProgramAccessDAO.class);
        createAndRegisterMock(AdmissionDao.class);
        OscarAppointmentDao appointments = createAndRegisterMock(OscarAppointmentDao.class);
        Appointment appointment = new Appointment();
        appointment.setStatus("t");
        when(appointments.find(42)).thenReturn(appointment);
        if (conflict) {
            when(manager.saveNoteRevision(any(), any(), any(), any(), any(), any())).thenThrow(snapshotConflict());
        } else {
            when(manager.saveNoteRevision(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new CaseManagementNoteSaveResult(note, ""));
        }

        assertThat(action.issueNoteSaveJson()).isNull();
        if (conflict) {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(appointment.getStatus()).isEqualTo("t");
            verifyNoInteractions(appointments);
        } else {
            assertThat(response.getStatus()).isEqualTo(200);
            var order = inOrder(manager, appointments);
            order.verify(manager).saveNoteRevision(any(), any(), any(), any(), any(), any());
            order.verify(appointments).find(42);
            order.verify(appointments).merge(appointment);
            assertThat(appointment.getStatus()).isEqualTo("tS");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "duplicate", "existing"})
    void shouldDeferCheckedIssuePersistenceToRevisionTransaction_whenFullFormSaveConflicts(String kind) throws Exception {
        request.getSession().setAttribute("newCaseManagement", "true");
        request.setParameter("includeIssue", "on");
        Issue issue = new Issue();
        issue.setId(73L);
        issue.setCode("TEST73");
        issue.setDescription("Newly checked test issue");
        issue.setRole("doctor");
        CaseManagementIssueDAO patientIssues = mock(CaseManagementIssueDAO.class);
        IssueDAO issues = mock(IssueDAO.class);
        injectDependency(action, "caseManagementIssueDao", patientIssues);
        injectDependency(action, "issueDao", issues);
        when(issues.findIssueByCode("TEST73")).thenReturn(issue);
        CaseManagementIssue existing = new CaseManagementIssue();
        existing.setId(74L);
        existing.setDemographic_no(101);
        existing.setIssue_id(73L);
        existing.setIssue(issue);
        if ("existing".equals(kind)) when(patientIssues.getIssuebyId("101", "73")).thenReturn(existing);
        int count = "duplicate".equals(kind) ? 2 : 1;
        for (int i = 0; i < count; i++) {
            CheckBoxBean checkbox = new CheckBoxBean();
            checkbox.getIssueDisplay().code = "TEST73";
            checkbox.getIssueDisplay().certain = "certain";
            form.getIssueCheckList().add(checkbox);
            request.setParameter("issueCheckList[" + i + "].checked", "on");
        }
        doAnswer(call -> {
            List<CaseManagementIssue> pending = call.getArgument(2);
            assertThat(pending).hasSize(1);
            CaseManagementIssue selected = pending.getFirst();
            assertThat(selected.getIssue()).isSameAs(issue);
            assertThat(selected.getDemographic_no()).isEqualTo(101);
            assertThat(selected.isCertain()).isTrue();
            if ("existing".equals(kind)) assertThat(selected).isSameAs(existing);
            else assertThat(selected.getId()).isNull();
            CaseManagementNote submitted = call.getArgument(1);
            assertThat(submitted.getIssues()).hasSize(1);
            assertThat(submitted.getIssues().iterator().next()).isSameAs(selected);
            assertThat(submitted.getNote()).contains(issue.getDescription());
            throw snapshotConflict();
        }).when(manager).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any());

        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(response.getStatus()).isEqualTo(409);
        verify(patientIssues, never()).saveIssue(any());
        verify(patientIssues, never()).saveAndUpdateCaseIssues(any());
        verify(manager, never()).deleteTmpSave(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "different patient"})
    void shouldRejectJsonSaveBeforeMutatingNote_whenNoteDoesNotBelongToLockedChart(String kind) {
        request.setParameter("demographic_no", "101");
        request.setParameter("noteId", "501");
        request.setParameter("value", "Attempted edit");
        request.setParameter("sign", "true");
        if ("missing".equals(kind)) when(manager.getNote("501")).thenReturn(null);
        else note.setDemographic_no("102");
        assertThatThrownBy(() -> action.issueNoteSaveJson()).isInstanceOf(SecurityException.class);
        assertThat(note.getNote()).isEqualTo("Original");
        assertThat(note.isSigned()).isFalse();
        verify(manager, never()).saveNoteRevision(any(), any(), any(), any(), any(), any());
    }
}
