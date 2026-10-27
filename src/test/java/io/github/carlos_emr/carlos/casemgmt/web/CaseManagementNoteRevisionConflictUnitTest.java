/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteSaveResult;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.log.LogAction;
import org.hibernate.exception.SnapshotIsolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for rejected note saves and draft recovery.
 *
 * @since 2026-10-14
 */
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
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.setParameter("demographicNo", "101");
        request.setParameter("nId", "501");
        request.setParameter("noteId", "501");
        request.setParameter("noteTxt", "Unsaved draft");
        request.setParameter("numIssues", "0");
        request.getSession().setAttribute("userrole", "doctor");
        io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.remember(request, "7");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        var security = mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        when(security.hasPrivilege(user, "_eChart", "w", "101")).thenReturn(true);
        when(security.hasPrivilege(user, "_casemgmt.notes", "w", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        injectDependency(action, "securityInfoManager", security);
        manager = mock(CaseManagementManager.class);
        when(manager.filterNotes(any(), any(), anyCollection(), any())).thenAnswer(call ->
                new ArrayList<>(call.<java.util.Collection<CaseManagementNote>>getArgument(2)));
        action.caseManagementMgr = manager;
        locks = mock(CasemgmtNoteLockDao.class);
        injectDependency(action, "casemgmtNoteLockDao", locks);
        lock = new CasemgmtNoteLock();
        lock.setId(10L);
        lock.setNoteId(501L);
        lock.setDemographicNo(101);
        lock.setProviderNo("999998");
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
        request.getSession().setAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(request, "101"), form);
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
        assertThat(request.getSession().getAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(request, "101"))).isSameAs(form);
        assertThat(request.getSession().getAttribute("casemgmtNoteLock101")).isSameAs(lock);
        assertThat(lock.getNoteId()).isEqualTo(501L);
        verify(locks, never()).merge(any());
        verify(manager, never()).deleteTmpSave(any(), any(), any());
        verify(manager, never()).getEditors(any(CaseManagementNote.class));
    }

    @Test
    void shouldKeepDraftAndLock_whenAjaxSaveHasWrappedSnapshotConflict() throws Exception {
        when(manager.saveCompactNoteRevision(any(), any(), anyList(), any())).thenThrow(snapshotConflict());
        assertThat(action.ajaxsave()).isNull();
        assertDraftRetained("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "sign", "verify", "toBill"})
    void shouldReturnRecoveryViewWithRawDraft_whenFullFormSaveHasWrappedSnapshotConflict(String operation) throws Exception {
        String rawDraft = "  </textarea><script>window.draftExecuted=true</script>&literal\r\nSecond line\n  ";
        String processedDraft = "Unsaved draft\n[[Added signature before conflict]]";
        request.setParameter("caseNote_note", rawDraft);
        if (!"save".equals(operation)) request.setParameter(operation, "toBill".equals(operation) ? "true" : "on");
        doAnswer(call -> {
            CaseManagementNote submitted = call.getArgument(1);
            submitted.setNote(processedDraft);
            throw snapshotConflict();
        }).when(manager).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        assertThat("save".equals(operation) ? action.save() : action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(request.getAttribute("noteRevisionConflict")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(rawDraft);
        assertDraftRetained(processedDraft);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCaptureGetterDraftBeforeSaving_whenRawRequestParameterIsAbsent(boolean exit) throws Exception {
        String rawDraft = "  Original untrimmed draft\n  ";
        doReturn(rawDraft, "Unsaved draft").when(action).getCaseNote_note();
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenThrow(snapshotConflict());
        assertThat(exit ? action.saveAndExit() : action.save()).isEqualTo("windowCloseError");
        assertThat(request.getAttribute("noteRevisionConflict")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(rawDraft);
        assertDraftRetained();
    }

    @Test
    void shouldKeepEditorWithoutSuccessMessage_whenFullFormSaveHasNoText() throws Exception {
        doReturn("   ").when(action).getCaseNote_note();

        assertThat(action.save()).isEqualTo("view");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("noteRevisionConflict")).isNull();
        assertThat(request.getAttribute("unsavedNoteText")).isNull();
        verify(action, never()).addActionMessage(anyString());
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        assertThat(request.getSession().getAttribute("casemgmtNoteLock101")).isSameAs(lock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "issueList_ajax"})
    void shouldKeepSuccessResultAndMessage_whenFullFormSaveSucceeds(String chain) throws Exception {
        request.setParameter("chain", chain);
        CaseManagementNote saved = note.copyForRevision();
        saved.setId(600L);
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);

        assertThat(action.save()).isEqualTo(chain.isEmpty() ? "view" : chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(action).addActionMessage("Note saved");
        assertThat(form.getCaseNote()).isSameAs(saved);
        assertThat(lock.getNoteId()).isEqualTo(600L);
        assertThat(request.getAttribute("noteRevisionConflict")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldClearOnlyOriginatingChartDraft_whenSavingAnExplicitCrossProgramNote(boolean legacy) throws Exception {
        // This chart's context is program7. It explicitly edits a visible note belonging
        // to program8, while another window still holds program8's independent draft.
        prepareOriginatingChart(legacy);
        note.setProgram_no("8");
        CaseManagementNote saved = note.copyForRevision();
        saved.setId(600L);
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);

        assertThat(action.save()).isEqualTo("view");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(saved.getProgram_no()).isEqualTo("8");
        verify(manager).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), eq("7"));
        // The manager owns revision persistence and draft cleanup in one transaction.
        verify(manager, never()).deleteTmpSave(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"compact,false", "compact,true", "cancel,false", "cancel,true"})
    void shouldKeepTheOtherWindowDraft_whenUsingCompactSaveOrCancel(String route, boolean legacy) throws Exception {
        prepareOriginatingChart(legacy);
        note.setProgram_no("8");
        Map<String, String> drafts = new java.util.HashMap<>(Map.of(
                "7", "Originating draft", "8", "Independent other program draft"));
        doAnswer(call -> drafts.remove((String) call.getArgument(2))).when(manager)
                .deleteTmpSave(eq("999998"), eq("101"), anyString());
        when(manager.saveCompactNoteRevision(any(), any(), anyList(), any())).thenAnswer(call -> {
            CaseManagementNote saved = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            saved.setId(600L);
            drafts.remove((String) call.getArgument(3));
            return new CaseManagementManager.CompactSaveResult(saved, call.getArgument(2));
        });

        if ("compact".equals(route)) {
            assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");
            assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        } else {
            action.setDemographicNo("101");
            assertThat(action.cancel()).isEqualTo("windowClose");
            verify(manager, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        }

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(drafts).containsExactlyEntriesOf(Map.of("8", "Independent other program draft"));
        if ("cancel".equals(route)) verify(manager).deleteTmpSave("999998", "101", "7");
        else {
            assertThat(form.getCaseNote().getProgram_no()).isEqualTo("8");
            verify(manager).saveCompactNoteRevision(any(), any(), anyList(), eq("7"));
            verify(manager, never()).deleteTmpSave(any(), any(), any());
        }
        verify(manager, never()).deleteTmpSave("999998", "101", "8");
    }

    private void prepareOriginatingChart(boolean legacy) {
        if (legacy) {
            request.removeAttribute(ChartProgramContext.class.getName() + ".requestContext");
            request.getSession().setAttribute(ChartProgramContext.formKey(request, "101"), form);
        } else {
            request.setParameter(ChartProgramContext.PARAMETER, ChartProgramContext.token(request));
            MockHttpServletRequest otherWindow = new MockHttpServletRequest();
            otherWindow.setSession(request.getSession());
            otherWindow.setParameter("demographicNo", "101");
            ChartProgramContext.remember(otherWindow, "8");
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "invalid", "-1", "0bad", "9223372036854775808"})
    void shouldRefuseMalformedCompactReference_beforeLoadingOrMutatingTheSelectedNote(String raw) throws Exception {
        if (raw == null) request.removeParameter("nId");
        else request.setParameter("nId", raw);
        assertThat(action.ajaxsave()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(manager, never()).getNote(anyString());
        verify(manager, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        assertThat(note.getNote()).isEqualTo("Original");
        assertThat(form.getCaseNote()).isSameAs(note);
    }

    @ParameterizedTest
    @ValueSource(strings = {"502", "01"})
    void shouldRefuseCompactSave_whenTheRequestDoesNotNameTheSelectedEditor(String raw) throws Exception {
        request.setParameter("nId", raw);
        assertThat(action.ajaxsave()).isNull();
        assertThat(response.getStatus()).isEqualTo(409);
        verify(manager, never()).getNote(anyString());
        verify(manager, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        assertThat(note.getNote()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign", "hidden", "program-changed"})
    void shouldRefuseCompactSave_beforeMutatingAnUnavailableSelectedRevision(String boundary) throws Exception {
        CaseManagementNote stored = note.copyForRevision();
        stored.setId(note.getId());
        if ("foreign".equals(boundary)) stored.setDemographic_no("202");
        if ("program-changed".equals(boundary)) stored.setProgram_no("8");
        when(manager.getNote("501")).thenReturn("missing".equals(boundary) ? null : stored);
        if ("hidden".equals(boundary)) when(manager.filterNotes(any(), any(), anyCollection(), any())).thenReturn(List.of());

        assertThat(action.ajaxsave()).isNull();

        assertThat(response.getStatus()).isEqualTo(switch (boundary) {
            case "missing", "foreign" -> 404;
            case "hidden" -> 403;
            default -> 409;
        });
        verify(manager, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
        assertThat(note.getNote()).isEqualTo("Original");
        assertThat(stored.getNote()).isEqualTo("Original");
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "0.1"})
    void shouldAcceptCanonicalNewEditorIds_withoutTreatingThemAsExistingNotes(String raw) throws Exception {
        note.setId(null);
        lock.setNoteId(0L);
        request.setParameter("nId", raw);
        when(manager.saveCompactNoteRevision(any(), any(), anyList(), any())).thenAnswer(call -> {
            CaseManagementNote saved = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            saved.setId(600L);
            return new CaseManagementManager.CompactSaveResult(saved, call.getArgument(2));
        });

        assertThat(action.ajaxsave()).isEqualTo("issueList_ajax");

        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        assertThat(form.getCaseNote().getProgram_no()).isEqualTo("7");
        assertThat(form.getCaseNote().getNote()).isEqualTo("Unsaved draft");
        assertThat(note.getNote()).isEqualTo("Original");
        verify(manager, never()).getNote(anyString());
        verify(manager).saveCompactNoteRevision(any(), any(), anyList(), eq("7"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void shouldUseTheAuthorizedProgramForNewCompactNotes_whenTheEditorIsLegacyOrCaptured(
            boolean captured, boolean readOnly) throws Exception {
        if (!captured) {
            request.removeAttribute(ChartProgramContext.class.getName() + ".requestContext");
            request.getSession().removeAttribute(ChartProgramContext.class.getName() + ".101");
        }
        String formKey = ChartProgramContext.formKey(request, "101");
        CaseManagementNote editor = new CaseManagementNote();
        editor.setDemographic_no("101");
        editor.setNote("Unsaved compact text");
        lock.setNoteId(0L);
        form.setCaseNote(editor);
        request.getSession().setAttribute(formKey, form);
        request.setParameter("nId", "0");
        request.setParameter("programId", "22");
        request.setParameter("providerNo", "another-provider");
        EctProgramManager programs = mock(EctProgramManager.class);
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("Authenticated default", "11"), new LabelValueBean("Other program", "22")));
        when(programs.getDefaultProgramId("999998")).thenReturn(11);
        WebApplicationContext context = action.getSpringContext();
        when(context.getBean("ectProgramManager")).thenReturn(programs);
        request.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        String expectedProgram = captured ? "7" : "11";
        when(manager.hasAccessRight("note-read-only", "access", "999998", "101", expectedProgram)).thenReturn(readOnly);
        when(manager.saveCompactNoteRevision(any(), any(), anyList(), any())).thenAnswer(call -> {
            CaseManagementNote submitted = call.getArgument(1);
            assertThat(submitted.getId()).isNull();
            assertThat(submitted.getProgram_no()).isEqualTo(expectedProgram);
            assertThat(call.<String>getArgument(3)).isEqualTo(expectedProgram);
            CaseManagementNote saved = submitted.copyForRevision();
            saved.setId(600L);
            return new CaseManagementManager.CompactSaveResult(saved, call.getArgument(2));
        });

        assertThat(action.ajaxsave()).isEqualTo(readOnly ? "none" : "issueList_ajax");

        assertThat(response.getStatus()).isEqualTo(readOnly ? 403 : 200);
        assertThat(ChartProgramContext.formKey(request, "101")).isEqualTo(formKey);
        assertThat(request.getSession().getAttribute(formKey)).isSameAs(form);
        if (!captured) assertThat(ChartProgramContext.resolve(request)).isNull();
        else verifyNoInteractions(programs);
        if (readOnly) {
            assertThat(form.getCaseNote()).isSameAs(editor);
            assertThat(editor.getNote()).isEqualTo("Unsaved compact text");
            verify(manager, never()).saveCompactNoteRevision(any(), any(), anyList(), any());
            verifyNoInteractions(locks);
        } else {
            assertThat(form.getCaseNote().getId()).isEqualTo(600L);
            assertThat(form.getCaseNote().getProgram_no()).isEqualTo(expectedProgram);
            verify(manager).saveCompactNoteRevision(any(), any(), anyList(), eq(expectedProgram));
        }
    }

    @Test
    void shouldReturnWindowCloseError_whenSaveAndExitHasLostItsSessionLock() throws Exception {
        when(locks.find(10L)).thenReturn(null);
        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("noteRevisionConflict")).isEqualTo(Boolean.TRUE);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo("Unsaved draft");
        verify(manager, times(2)).hasAccessRight("note-read-only", "access", "999998", "101", "7");
        verifyNoMoreInteractions(manager);
    }

    @Test
    void shouldPropagateOtherDatabaseFailures_insteadOfReportingARevisionConflict() {
        RuntimeException failure = new DataIntegrityViolationException("invalid row");
        when(manager.saveCompactNoteRevision(any(), any(), anyList(), any())).thenThrow(failure);
        assertThatThrownBy(() -> action.ajaxsave()).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(manager, never()).deleteTmpSave(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"true, 101", "false, 101", "false, 102"})
    void shouldSignOnlyMatchingAppointmentAfterRevisionSucceeds_whenJsonSignAndExitIsRequested(
            boolean conflict, int appointmentDemographic) throws Exception {
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
        AppointmentArchiveDao archives = createAndRegisterMock(AppointmentArchiveDao.class);
        Appointment appointment = new Appointment();
        appointment.setStatus("t");
        appointment.setDemographicNo(appointmentDemographic);
        when(appointments.find(42)).thenReturn(appointment);
        doAnswer(call -> {
            Appointment archived = call.getArgument(0);
            assertThat(archived.getStatus()).isEqualTo("t");
            return null;
        }).when(archives).archiveAppointment(appointment);
        if (conflict) {
            when(manager.saveNoteRevision(any(), any(), any(), any(), any(), any())).thenThrow(snapshotConflict());
        } else {
            when(manager.saveNoteRevision(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new CaseManagementNoteSaveResult(note, ""));
        }

        assertThat(action.issueNoteSaveJson()).isNull();
        assertThat(note.getNote()).isEqualTo("Original");
        assertThat(note.isSigned()).isFalse();
        if (conflict) {
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(appointment.getStatus()).isEqualTo("t");
            verifyNoInteractions(appointments, archives);
        } else if (appointmentDemographic == 101) {
            assertThat(response.getStatus()).isEqualTo(200);
            var order = inOrder(manager, appointments, archives);
            order.verify(manager).saveNoteRevision(any(), any(), any(), any(), any(), any());
            order.verify(appointments).find(42);
            order.verify(archives).archiveAppointment(appointment);
            order.verify(appointments).merge(appointment);
            assertThat(appointment.getStatus()).isEqualTo("tS");
        } else {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(appointment.getStatus()).isEqualTo("t");
            verify(appointments).find(42);
            verify(appointments, never()).merge(any());
            verifyNoInteractions(archives);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldLeaveNewJsonIssuesUnpersisted_whenRevisionSaveFails(boolean conflict) throws Exception {
        request.setParameter("demographic_no", "101");
        request.setParameter("noteId", "0");
        request.setParameter("value", "New JSON note");
        request.setParameter("issue_id", "73");
        request.setParameter("appointment_no", "42");
        request.setParameter("sign", "true");
        request.setParameter("signAndExit", "true");
        createAndRegisterMock(ProgramProviderDAO.class);
        createAndRegisterMock(ProviderDefaultProgramDao.class);
        createAndRegisterMock(ProgramAccessDAO.class);
        createAndRegisterMock(SecroleDao.class);
        createAndRegisterMock(RoleProgramAccessDAO.class);
        createAndRegisterMock(AdmissionDao.class);
        OscarAppointmentDao appointments = createAndRegisterMock(OscarAppointmentDao.class);
        AppointmentArchiveDao archives = createAndRegisterMock(AppointmentArchiveDao.class);
        Issue issue = new Issue();
        issue.setId(73L);
        issue.setCode("SocHistory");
        issue.setRole("doctor");
        when(manager.getIssue("73")).thenReturn(issue);
        RuntimeException failure = conflict ? snapshotConflict() : new DataIntegrityViolationException("late write failure");
        when(manager.saveNoteRevision(any(), any(), any(), any(), any(), any())).thenThrow(failure);

        if (conflict) {
            assertThat(action.issueNoteSaveJson()).isNull();
            assertThat(response.getStatus()).isEqualTo(409);
        } else {
            assertThatThrownBy(() -> action.issueNoteSaveJson()).isSameAs(failure);
        }

        ArgumentCaptor<CaseManagementNote> submitted = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).saveNoteRevision(any(), submitted.capture(), any(), any(), any(), any());
        assertThat(submitted.getValue().getId()).isNull();
        assertThat(submitted.getValue().getIssues()).singleElement().satisfies(assigned -> {
            assertThat(assigned.getId()).isNull();
            assertThat(assigned.getIssue_id()).isEqualTo(73L);
            assertThat(assigned.getProgram_id()).isEqualTo(10016);
        });
        verify(manager, never()).saveAndUpdateCaseIssues(any());
        verifyNoInteractions(appointments, archives);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldDeferAllCppWritesAndSessionChanges_whenAtomicSaveFails(boolean conflict) throws Exception {
        CaseManagementNote sibling = prepareCppSave();
        var oldIssueList = form.getIssueCheckList();
        RuntimeException failure = conflict ? snapshotConflict() : new DataIntegrityViolationException("late extension failure");
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any())).thenThrow(failure);

        if (conflict) {
            assertThat(action.issueNoteSave()).isNull();
            assertThat(response.getStatus()).isEqualTo(409);
        } else {
            assertThatThrownBy(() -> action.issueNoteSave()).isSameAs(failure);
        }

        ArgumentCaptor<CaseManagementNote> draft = ArgumentCaptor.forClass(CaseManagementNote.class);
        ArgumentCaptor<Map<Long, Integer>> positions = ArgumentCaptor.captor();
        ArgumentCaptor<List<CaseManagementNoteExt>> submitted = ArgumentCaptor.captor();
        verify(manager).saveCppNoteRevision(draft.capture(), any(), eq("Previous chart text"),
                positions.capture(), submitted.capture());
        assertThat(draft.getValue()).isNotSameAs(note);
        assertThat(draft.getValue().getId()).isEqualTo(501L);
        assertThat(draft.getValue().getNote()).isEqualTo("CPP draft");
        assertThat(draft.getValue().getIssues()).singleElement().satisfies(assigned -> {
            assertThat(assigned.getId()).isNull();
            assertThat(assigned.getIssue_id()).isEqualTo(73L);
        });
        assertThat(positions.getValue()).containsExactlyEntriesOf(Map.of(502L, 0));
        assertThat(submitted.getValue()).extracting(CaseManagementNoteExt::getKeyVal)
                .containsExactly(CaseManagementNoteExt.STARTDATE, CaseManagementNoteExt.AGEATONSET);
        assertThat(submitted.getValue().getFirst().getDateValueStr()).isEqualTo("2022-01-01");
        assertThat(submitted.getValue().getLast().getValue()).isEqualTo("44");
        assertCppStateUnchanged(sibling, oldIssueList);
    }

    private void assertCppStateUnchanged(CaseManagementNote sibling, List<CheckBoxBean> oldIssueList) {
        assertThat(note.getNote()).isEqualTo("Original");
        assertThat(note.isSigned()).isFalse();
        assertThat(note.getPosition()).isZero();
        assertThat(sibling.getPosition()).isEqualTo(1);
        assertThat(form.getIssueCheckList()).isSameAs(oldIssueList).isEmpty();
        assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Previous chart text");
        verify(manager, never()).saveAndUpdateCaseIssues(any());
        verify(manager, never()).updateNote(any());
        verify(manager, never()).saveCPP(any(), any());
        verify(manager, never()).saveNoteExt(any());
        verify(manager, never()).updateNoteExt(any());
        verify(manager, never()).getEditors(any(CaseManagementNote.class));
    }

    @Test
    void shouldPublishPersistedIssueIdsAndChartText_whenAtomicCppSaveSucceeds() throws Exception {
        prepareCppSave();
        request.setParameter("forward", "none");
        var oldIssueList = form.getIssueCheckList();
        CaseManagementIssue persistedIssue = new CaseManagementIssue();
        persistedIssue.setId(74L);
        persistedIssue.setIssue_id(73L);
        persistedIssue.setIssue(manager.getIssue("73"));
        CaseManagementNote saved = note.copyForRevision();
        saved.setId(600L);
        saved.setNote("CPP draft");
        saved.setIssues(Set.of(persistedIssue));
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any())).thenAnswer(call -> {
            assertThat(form.getIssueCheckList()).isSameAs(oldIssueList).isEmpty();
            assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Previous chart text");
            return new CaseManagementNoteSaveResult(saved, "Committed chart text");
        });

        assertThat(action.issueNoteSave()).isNull();
        logActionMock.verify(() -> LogAction.addLog(eq("999998"), anyString(), anyString(), eq("600"),
                anyString(), eq("101"), anyString()));

        assertThat(response.getContentAsString().trim()).isEqualTo("600");
        assertThat(form.getIssueCheckList()).singleElement()
                .satisfies(checkbox -> assertThat(checkbox.getIssue()).isSameAs(persistedIssue));
        assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Committed chart text");
        assertThat(note.getNote()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldUseTheAuthorizedDefaultForNewCppIssues_whenLegacyProgramContextIsAbsent(boolean readOnly) throws Exception {
        prepareCppSave();
        request.removeAttribute(ChartProgramContext.class.getName() + ".requestContext");
        request.getSession().removeAttribute(ChartProgramContext.class.getName() + ".101");
        String legacyFormKey = ChartProgramContext.formKey(request, "101");
        request.getSession().setAttribute(legacyFormKey, form);
        request.setParameter("noteId", "0");
        request.setParameter("programId", "22");
        request.setParameter("providerNo", "another-provider");
        request.setParameter("forward", "none");
        EctProgramManager programs = mock(EctProgramManager.class);
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("Authenticated default", "11"), new LabelValueBean("Other program", "22")));
        when(programs.getDefaultProgramId("999998")).thenReturn(11);
        WebApplicationContext context = action.getSpringContext();
        when(context.getBean("ectProgramManager")).thenReturn(programs);
        request.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        when(manager.hasAccessRight("note-read-only", "access", "999998", "101", "11")).thenReturn(readOnly);
        CaseManagementIssue persistedIssue = new CaseManagementIssue();
        persistedIssue.setId(74L);
        persistedIssue.setIssue_id(73L);
        persistedIssue.setIssue(manager.getIssue("73"));
        persistedIssue.setProgram_id(11);
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any())).thenAnswer(call -> {
            CaseManagementNote submitted = call.getArgument(0);
            assertThat(submitted.getId()).isNull();
            assertThat(submitted.getProgram_no()).isEqualTo("11");
            assertThat(submitted.getIssues()).singleElement().satisfies(issue -> {
                assertThat(issue.getId()).isNull();
                assertThat(issue.getIssue_id()).isEqualTo(73L);
                assertThat(issue.getProgram_id()).isEqualTo(11);
            });
            CaseManagementNote saved = submitted.copyForRevision();
            saved.setId(600L);
            saved.setIssues(Set.of(persistedIssue));
            return new CaseManagementNoteSaveResult(saved, "Committed chart text");
        });
        clearInvocations(manager, locks);

        assertThat(action.issueNoteSave()).isEqualTo(readOnly ? "none" : null);

        assertThat(response.getStatus()).isEqualTo(readOnly ? 403 : 200);
        verify(manager).hasAccessRight("note-read-only", "access", "999998", "101", "11");
        assertThat(ChartProgramContext.resolve(request)).isNull();
        assertThat(ChartProgramContext.formKey(request, "101")).isEqualTo(legacyFormKey);
        assertThat(request.getSession().getAttribute(legacyFormKey)).isSameAs(form);
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(note.getNote()).isEqualTo("Original");
        if (readOnly) {
            verifyNoMoreInteractions(manager);
            verifyNoInteractions(locks);
            assertThat(form.getIssueCheckList()).isEmpty();
            assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Previous chart text");
        } else {
            verify(manager).saveCppNoteRevision(any(), any(), eq("Previous chart text"), any(), any());
            assertThat(response.getContentAsString().trim()).isEqualTo("600");
            assertThat(form.getIssueCheckList()).singleElement()
                    .satisfies(checkbox -> assertThat(checkbox.getIssue()).isSameAs(persistedIssue));
            assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Committed chart text");
        }
    }

    @Test
    void shouldRemoveOnlyThisPatientsLastEChartText_whenAnotherChartSavedLast() throws Exception {
        prepareCppSave();
        request.setParameter("forward", "none");
        // Another chart window saved after this one: its text must not be looked for, and removed,
        // in this patient's eChart record (issue #4673).
        request.getSession().setAttribute(ChartSessionKeys.lastSavedNoteText("202"), "Other patient's chart text");
        request.getSession().setAttribute("lastSavedNoteString", "Login-wide chart text");
        CaseManagementIssue persistedIssue = new CaseManagementIssue();
        persistedIssue.setId(74L);
        persistedIssue.setIssue_id(73L);
        persistedIssue.setIssue(manager.getIssue("73"));
        CaseManagementNote saved = note.copyForRevision();
        saved.setId(600L);
        saved.setNote("CPP draft");
        saved.setIssues(Set.of(persistedIssue));
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any()))
                .thenReturn(new CaseManagementNoteSaveResult(saved, "Committed chart text"));

        assertThat(action.issueNoteSave()).isNull();

        verify(manager).saveCppNoteRevision(any(), any(), eq("Previous chart text"), any(), any());
        assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("101"))).isEqualTo("Committed chart text");
        assertThat(request.getSession().getAttribute(ChartSessionKeys.lastSavedNoteText("202"))).isEqualTo("Other patient's chart text");
        assertThat(request.getSession().getAttribute("lastSavedNoteString")).isEqualTo("Login-wide chart text");
    }

    @Test
    void shouldSubmitEmptyTextExtensionsButIgnoreAbsentAndInvalidDates_whenCppFieldsAreCleared() {
        prepareCppSave();
        request.setParameter("startdate", "");
        request.setParameter("proceduredate", "not a date");
        request.setParameter("ageatonset", "");
        request.setParameter("treatment", "");
        RuntimeException failure = new DataIntegrityViolationException("stop after capture");
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> action.issueNoteSave()).isSameAs(failure);

        ArgumentCaptor<List<CaseManagementNoteExt>> submitted = ArgumentCaptor.captor();
        verify(manager).saveCppNoteRevision(any(), any(), any(), any(), submitted.capture());
        assertThat(submitted.getValue()).extracting(CaseManagementNoteExt::getKeyVal)
                .containsExactly(CaseManagementNoteExt.AGEATONSET, CaseManagementNoteExt.TREATMENT);
        assertThat(submitted.getValue()).allSatisfy(extension -> {
            assertThat(extension.getValue()).isEmpty();
            assertThat(extension.getDateValue()).isNull();
        });
    }

    @ParameterizedTest
    @CsvSource({"2,0,1,2", "2,1,0,2", "0,2,0,1"})
    void shouldSubmitReorderedPositions_whenOnlyCppPositionChanges(
            int oldPosition, int newPosition, int firstSiblingPosition, int secondSiblingPosition) throws Exception {
        CaseManagementNote firstSibling = prepareCppSave();
        CaseManagementNote secondSibling = new CaseManagementNote();
        secondSibling.setId(503L);
        note.setPosition(oldPosition);
        firstSibling.setPosition(oldPosition == 0 ? 1 : 0);
        secondSibling.setPosition(oldPosition == 0 ? 2 : 1);
        when(manager.getActiveNotes(eq("101"), any(String[].class)))
                .thenReturn(oldPosition == 0 ? List.of(note, firstSibling, secondSibling)
                        : List.of(firstSibling, secondSibling, note));
        request.setParameter("value", note.getNote());
        request.setParameter("issueChange", "false");
        request.setParameter("startdate", "");
        request.setParameter("ageatonset", "");
        request.setParameter("position", String.valueOf(newPosition));
        request.setParameter("forward", "none");
        when(manager.saveCppNoteRevision(any(), any(), any(), any(), any())).thenAnswer(call ->
                new CaseManagementNoteSaveResult(call.getArgument(0), "Committed chart text"));

        assertThat(action.issueNoteSave()).isNull();

        ArgumentCaptor<CaseManagementNote> draft = ArgumentCaptor.forClass(CaseManagementNote.class);
        ArgumentCaptor<Map<Long, Integer>> positions = ArgumentCaptor.captor();
        verify(manager).saveCppNoteRevision(draft.capture(), any(), any(), positions.capture(), any());
        assertThat(draft.getValue().getPosition()).isEqualTo(newPosition);
        assertThat(positions.getValue().getOrDefault(502L, firstSibling.getPosition())).isEqualTo(firstSiblingPosition);
        assertThat(positions.getValue().getOrDefault(503L, secondSibling.getPosition())).isEqualTo(secondSiblingPosition);
        assertThat(note.getPosition()).isEqualTo(oldPosition);
    }

    @Test
    void shouldSkipRevision_whenCppContentAndPositionAreUnchanged() throws Exception {
        prepareCppSave();
        request.setParameter("value", note.getNote());
        request.setParameter("issueChange", "false");
        request.setParameter("startdate", "");
        request.setParameter("ageatonset", "");
        request.setParameter("position", "0");

        assertThat(action.issueNoteSave()).isNull();

        verify(manager, never()).saveCppNoteRevision(any(), any(), any(), any(), any());
    }

    private CaseManagementNote prepareCppSave() {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Test");
        provider.setLastName("Clinician");
        when(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProvider()).thenReturn(provider);
        request.setParameter("noteId", "501");
        request.setParameter("value", "CPP draft");
        request.setParameter("issueChange", "true");
        request.setParameter("issue_id", "73");
        request.setParameter("reloadUrl", "issue_code=SocHistory");
        request.setParameter("position", "1");
        request.setParameter("startdate", "2022-01-01");
        request.setParameter("resolutiondate", "not a date");
        request.setParameter("ageatonset", "44");
        request.getSession().setAttribute(ChartSessionKeys.lastSavedNoteText("101"), "Previous chart text");
        CaseManagementNoteExtDAO extensions = mock(CaseManagementNoteExtDAO.class);
        injectDependency(action, "caseManagementNoteExtDao", extensions);
        Issue issue = new Issue();
        issue.setId(73L);
        issue.setCode("SocHistory");
        issue.setDescription("Social History");
        issue.setRole("doctor");
        when(manager.getIssue("73")).thenReturn(issue);
        when(manager.getIssueInfoByCode("999998", "SocHistory")).thenReturn(List.of(issue));
        CaseManagementNote sibling = new CaseManagementNote();
        sibling.setId(502L);
        sibling.setPosition(1);
        when(manager.getActiveNotes(eq("101"), any(String[].class))).thenReturn(List.of(note, sibling));
        return sibling;
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
        existing.setProgram_id(8);
        if ("existing".equals(kind)) when(patientIssues.getIssuebyId("101", "73")).thenReturn(existing);
        int count = "duplicate".equals(kind) ? 2 : 1;
        for (int i = 0; i < count; i++) {
            CheckBoxBean checkbox = new CheckBoxBean();
            checkbox.getIssueDisplay().code = "TEST73";
            checkbox.getIssueDisplay().acute = "acute";
            checkbox.getIssueDisplay().certain = "certain";
            checkbox.getIssueDisplay().major = "major";
            checkbox.getIssueDisplay().resolved = "resolved";
            form.getIssueCheckList().add(checkbox);
            request.setParameter("issueCheckList[" + i + "].checked", "on");
        }
        doAnswer(call -> {
            List<CaseManagementIssue> pending = call.getArgument(2);
            assertThat(pending).hasSize(1);
            CaseManagementIssue selected = pending.getFirst();
            assertThat(selected.getIssue()).isSameAs(issue);
            assertThat(selected.getIssue_id()).isEqualTo(73L);
            assertThat(selected.getDemographic_no()).isEqualTo(101);
            assertThat(selected.isAcute()).isTrue();
            assertThat(selected.isCertain()).isTrue();
            assertThat(selected.isMajor()).isTrue();
            assertThat(selected.isResolved()).isTrue();
            if ("existing".equals(kind)) {
                assertThat(selected).isNotSameAs(existing);
                assertThat(selected.getId()).isEqualTo(existing.getId());
                assertThat(selected.getProgram_id()).isEqualTo(existing.getProgram_id());
            } else assertThat(selected.getId()).isNull();
            CaseManagementNote submitted = call.getArgument(1);
            assertThat(submitted.getIssues()).hasSize(1);
            assertThat(submitted.getIssues().iterator().next()).isSameAs(selected);
            assertThat(submitted.getNote()).contains(issue.getDescription());
            throw snapshotConflict();
        }).when(manager).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());

        assertThat(action.saveAndExit()).isEqualTo("windowCloseError");
        assertThat(response.getStatus()).isEqualTo(409);
        if ("existing".equals(kind)) {
            assertThat(existing.getId()).isEqualTo(74L);
            assertThat(existing.getProgram_id()).isEqualTo(8);
            assertThat(existing.getIssue()).isSameAs(issue);
            assertThat(existing.isAcute()).isFalse();
            assertThat(existing.isCertain()).isFalse();
            assertThat(existing.isMajor()).isFalse();
            assertThat(existing.isResolved()).isFalse();
        }
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

    @ParameterizedTest
    @ValueSource(strings = {"save", "saveAndExit"})
    void shouldRefuseStaleFullForm_beforeChangingAnotherWindowsSelectedNote(String operation) throws Exception {
        String draft = "Window A clinical draft\n  Preserve exact spacing ";
        request.setParameter("caseNote_note", draft);
        doReturn(draft).when(action).getCaseNote_note();
        note.setId(502L);
        note.setUuid(UUID.randomUUID().toString());
        note.setNote("Window B existing note");
        lock.setNoteId(502L);

        assertThat("save".equals(operation) ? action.save() : action.saveAndExit()).isEqualTo("windowCloseError");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(draft);
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(note.getId()).isEqualTo(502L);
        assertThat(note.getNote()).isEqualTo("Window B existing note");
        assertThat(lock.getNoteId()).isEqualTo(502L);
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"staleSelection", "expiredLock", "ajaxStaleSelection"})
    void shouldRetainIssueDeletionDraft_beforeAnyMutationOfAnotherWindowsEditor(String scenario) throws Exception {
        var permissions = (io.github.carlos_emr.carlos.managers.SecurityInfoManager)
                org.springframework.test.util.ReflectionTestUtils.getField(action, "securityInfoManager");
        var login = LoggedInInfo.getLoggedInInfoFromSession(request);
        when(permissions.hasPrivilege(login, "_demographic", "r", "101")).thenReturn(true);
        when(permissions.hasPrivilege(login, "_casemgmt.issues", "w", "101")).thenReturn(true);
        String draft = "Window A issue-deletion draft\n  Exact spacing ";
        request.setParameter("caseNote_note", draft);
        doReturn(draft).when(action).getCaseNote_note();
        note.setId(502L);
        note.setNote("Window B existing note");
        lock.setNoteId(502L);
        if ("expiredLock".equals(scenario)) when(locks.find(10L)).thenReturn(null);
        if ("ajaxStaleSelection".equals(scenario)) request.setParameter("ajax", "true");
        var issue = new CaseManagementIssue();
        issue.setId(71L);
        issue.setDemographic_no(101);
        var checkbox = new CheckBoxBean();
        checkbox.setIssue(issue);
        form.getIssueCheckList().add(checkbox);
        var originalIssues = form.getIssueCheckList();
        action.setDeleteId("0");

        assertThat(action.issueDelete()).isEqualTo("ajaxStaleSelection".equals(scenario) ? "none" : "windowCloseError");

        assertThat(response.getStatus()).isEqualTo(409);
        if (!"ajaxStaleSelection".equals(scenario)) assertThat(request.getAttribute("unsavedNoteText")).isEqualTo(draft);
        assertThat(note.getNote()).isEqualTo("Window B existing note");
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(form.getIssueCheckList()).isSameAs(originalIssues).containsExactly(checkbox);
        verify(manager, never()).deleteIssueById(any());
        verify(manager, never()).removeIssueFromCPP(any(), any());
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "invalid", "0", "01", "9223372036854775808"})
    void shouldRefuseFullForm_beforeChangingAPersistedNoteWithMissingOrInvalidIdentity(String identity) throws Exception {
        if (identity == null) request.removeParameter("noteId");
        else request.setParameter("noteId", identity);

        assertThat(action.save()).isEqualTo("windowCloseError");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo("Unsaved draft");
        assertThat(note.getNote()).isEqualTo("Original");
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"note", "patient", "provider", "session"})
    void shouldRefuseFullForm_whenTheDatabaseLockDoesNotOwnTheSelectedNote(String mismatch) throws Exception {
        CasemgmtNoteLock stored = new CasemgmtNoteLock();
        stored.setId(10L);
        stored.setNoteId("note".equals(mismatch) ? 502L : 501L);
        stored.setDemographicNo("patient".equals(mismatch) ? 202 : 101);
        stored.setProviderNo("provider".equals(mismatch) ? "999997" : "999998");
        stored.setSessionId("session".equals(mismatch) ? "another-session" : request.getSession().getId());
        when(locks.find(10L)).thenReturn(stored);

        assertThat(action.save()).isEqualTo("windowCloseError");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(request.getAttribute("unsavedNoteText")).isEqualTo("Unsaved draft");
        assertThat(note.getNote()).isEqualTo("Original");
        verify(manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "saveAndExit"})
    void shouldKeepAnotherProgramsCachedLock_whenSavingTheOwnedSelectedNote(String operation) throws Exception {
        CasemgmtNoteLock other = new CasemgmtNoteLock();
        other.setId(11L);
        other.setNoteId(502L);
        other.setDemographicNo(101);
        other.setProviderNo("999998");
        other.setSessionId(request.getSession().getId());
        request.getSession().setAttribute("casemgmtNoteLock101", other);
        when(locks.findBySession(request.getSession().getId())).thenReturn(List.of(other, lock));
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            CaseManagementNote saved = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            saved.setId(600L);
            return saved;
        });

        if ("save".equals(operation)) action.save(); else action.saveAndExit();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        assertThat(lock.getNoteId()).isEqualTo(600L);
        assertThat(request.getSession().getAttribute("casemgmtNoteLock101")).isSameAs(other);
        assertThat(other.getNoteId()).isEqualTo(502L);
        verify(locks).merge(lock);
        verify(locks, never()).merge(other);
        if ("saveAndExit".equals(operation)) verify(locks).removeForSession("999998", 101, 600L, request.getSession().getId());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"0", "01", "0.1"})
    void shouldSaveANewFullForm_withCanonicalNewNoteReferences(String identity) throws Exception {
        note.setId(null);
        note.setUuid(null);
        lock.setNoteId(0L);
        if (identity == null) request.removeParameter("noteId");
        else request.setParameter("noteId", identity);
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            CaseManagementNote saved = ((CaseManagementNote) call.getArgument(1)).copyForRevision();
            saved.setId(600L);
            return saved;
        });

        assertThat(action.save()).isEqualTo("view");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        assertThat(form.getCaseNote().getNote()).isEqualTo("Unsaved draft");
    }
}
