/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Checks the real dispatch path before the clinical issue write is reached. */
@Tag("unit")
@Tag("release-review")
class CaseManagementIssueWriteAuthorizationUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldRefuseIssueChangeBeforePersistence_whenCallerHasOnlyDemographicRead() throws Exception {
        var action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        var request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        action.request = request;
        action.response = new MockHttpServletResponse();
        request.setParameter("method", "issueChange");
        request.setParameter("demographicNo", "101");
        request.setParameter("issueCheckList[0].issue.resolved", "true");
        request.getSession().setAttribute("userrole", "read-only");
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        var permissions = mock(SecurityInfoManager.class);
        when(permissions.hasPrivilege(user, "_demographic", "r", null)).thenReturn(true);
        injectDependency(action, "securityInfoManager", permissions);
        var manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        var form = new CaseManagementEntryFormBean();
        form.setIssueCheckList(new ArrayList<>(List.of(new CheckBoxBean())));
        request.getSession().setAttribute("caseManagementEntryForm101", form);
        action.setLineId("0");
        // Stop at the first attempted write so unrelated rendering cannot hide a missing gate.
        doThrow(new SecurityException("Synthetic unauthorized storage access"))
                .when(manager).saveAndUpdateCaseIssues(any());

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(manager, never()).saveAndUpdateCaseIssues(any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"makeIssue","issueAdd","issueDelete","issueChange","submitChangeDiagnosis","ajaxChangeDiagnosis"})
    void shouldRefuseEveryIssueMutationBeforeRestoringSession_whenCallerHasNoIssueWrite(String method) throws Exception {
        var action=mock(CaseManagementEntry2Action.class,CALLS_REAL_METHODS);
        action.request=new MockHttpServletRequest("POST","/CaseManagementEntry");
        action.response=new MockHttpServletResponse();
        ((MockHttpServletRequest)action.request).setParameter("method",method);((MockHttpServletRequest)action.request).setParameter("demographicNo","101");
        var user=mock(LoggedInInfo.class);LoggedInInfo.setLoggedInInfoIntoSession(action.request.getSession(),user);
        var permissions=mock(SecurityInfoManager.class);
        when(permissions.hasPrivilege(user,"_demographic","r",null)).thenReturn(true);
        when(permissions.hasPrivilege(user,"_demographic","r","101")).thenReturn(true);
        injectDependency(action,"securityInfoManager",permissions);
        action.caseManagementMgr=mock(CaseManagementManager.class);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(action.caseManagementMgr);
        org.assertj.core.api.Assertions.assertThat(action.request.getSession().getAttribute("issueStatusChanged")).isNull();
    }

    @Test
    void shouldRefuseLockedPatientBeforeSessionMutation_whenCallerHasIssueWrite() throws Exception {
        var fixture=issueFixture(false);
        assertThatThrownBy(fixture.action::issueChange).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fixture.manager);
        org.assertj.core.api.Assertions.assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isFalse();
    }

    @Test
    void shouldPersistIssueChange_whenCallerHasWriteAndPatientAccess() throws Exception {
        var fixture=issueFixture(true);
        org.assertj.core.api.Assertions.assertThat(fixture.action.issueChange()).isEqualTo("view");
        verify(fixture.manager).saveAndUpdateCaseIssues(any());
        org.assertj.core.api.Assertions.assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isTrue();
    }

    @Test
    void shouldRefuseCrossPatientSessionIssue_evenWithWriteAccess() throws Exception {
        var fixture=issueFixture(true);fixture.form.getIssueCheckList().get(0).getIssue().setDemographic_no(102);
        assertThatThrownBy(fixture.action::issueChange).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fixture.manager);
    }

    @Test
    void shouldRefuseCrossPatientSessionNote_evenWithWriteAccess() throws Exception {
        var fixture = issueFixture(true);
        var note = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote();
        note.setDemographic_no("102");
        fixture.form.setCaseNote(note);
        assertThatThrownBy(fixture.action::issueChange).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fixture.manager);
    }

    @Test
    void shouldRefuseMalformedPatientScope_beforeSessionOrClinicalWrite() throws Exception {
        var fixture = issueFixture(true);
        ((MockHttpServletRequest) fixture.action.request).setParameter("demographicNo", "101<script>");
        assertThatThrownBy(fixture.action::issueChange).isInstanceOf(SecurityException.class);
        verifyNoInteractions(fixture.manager);
        org.assertj.core.api.Assertions.assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"GET","HEAD","PUT"})
    void shouldRefuseNonPostIssueChanges_beforeAnyWrite(String method) throws Exception {
        var fixture=issueFixture(true);((MockHttpServletRequest)fixture.action.request).setMethod(method);
        org.assertj.core.api.Assertions.assertThat(fixture.action.issueChange()).isEqualTo("none");
        org.assertj.core.api.Assertions.assertThat(fixture.action.response.getStatus()).isEqualTo(405);
        verifyNoInteractions(fixture.manager);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"_eChart", "_casemgmt.notes"})
    void shouldRefuseIssueDeletionBeforeAnyMutation_whenAnExistingNoteCannotBeSaved(String deniedObject) throws Exception {
        var fixture = issueFixture(true);
        var permissions = (SecurityInfoManager) org.springframework.test.util.ReflectionTestUtils
                .getField(fixture.action, "securityInfoManager");
        var user = LoggedInInfo.getLoggedInInfoFromSession(fixture.action.request);
        when(permissions.hasPrivilege(user, "_eChart", "w", "101")).thenReturn(true);
        when(permissions.hasPrivilege(user, "_casemgmt.notes", "w", "101")).thenReturn(true);
        when(permissions.hasPrivilege(user, deniedObject, "w", "101")).thenReturn(false);
        CaseManagementNote note = new CaseManagementNote();
        note.setId(501L);
        note.setDemographic_no("101");
        note.setNote("Existing clinical text");
        fixture.form.setCaseNote(note);
        fixture.action.setDeleteId("0");
        var existingIssues = fixture.form.getIssueCheckList();

        assertThat(fixture.action.issueDelete()).isEqualTo("none");

        assertThat(fixture.action.response.getStatus()).isEqualTo(403);
        verifyNoInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList()).isSameAs(existingIssues).hasSize(1);
        assertThat(note.getNote()).isEqualTo("Existing clinical text");
        assertThat(fixture.action.request.getAttribute("change_flag")).isNull();
    }

    @Test
    void shouldKeepIssueOnlyDeletionAvailable_whenTheEditorHasNoPersistedNote() throws Exception {
        var fixture = issueFixture(true);
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no("101");
        note.setNote("Unsaved editor text");
        fixture.form.setCaseNote(note);
        fixture.action.setDeleteId("0");
        doReturn("").when(fixture.action).getDemoName(anyString());
        doReturn("").when(fixture.action).getDemoAge(anyString());
        doReturn("").when(fixture.action).getDemoDOB(anyString());
        var issue = fixture.form.getIssueCheckList().getFirst().getIssue();
        var properties = mock(io.github.carlos_emr.CarlosProperties.class);
        when(properties.isCaisiLoaded()).thenReturn(true);
        try (var config = mockStatic(io.github.carlos_emr.CarlosProperties.class)) {
            config.when(io.github.carlos_emr.CarlosProperties::getInstance).thenReturn(properties);

            assertThat(fixture.action.issueDelete()).isEqualTo("view");
        }

        assertThat(fixture.action.response.getStatus()).isEqualTo(200);
        verify(fixture.manager).deleteIssueById(issue);
        verify(fixture.manager).removeIssueFromCPP("101", issue);
        assertThat(fixture.form.getIssueCheckList()).isEmpty();
        assertThat(note.getNote()).isEqualTo("Unsaved editor text");
        verify(fixture.manager, never()).saveCaseManagementNote(any(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missingSession", "missingDatabase", "takenOver", "rotatedSession"})
    void shouldRefuseIssueDeletionBeforeAnyMutation_whenTheExistingNoteLockIsInvalid(String scenario) throws Exception {
        var fixture = issueFixture(true);
        var permissions = (SecurityInfoManager) org.springframework.test.util.ReflectionTestUtils
                .getField(fixture.action, "securityInfoManager");
        var user = LoggedInInfo.getLoggedInInfoFromSession(fixture.action.request);
        when(permissions.hasPrivilege(user, "_eChart", "w", "101")).thenReturn(true);
        when(permissions.hasPrivilege(user, "_casemgmt.notes", "w", "101")).thenReturn(true);
        CaseManagementNote note = new CaseManagementNote();
        note.setId(501L);
        note.setDemographic_no("101");
        note.setNote("Existing clinical text");
        note.setHourOfEncounterTime(1);
        fixture.form.setCaseNote(note);
        fixture.action.setDeleteId("0");
        ((MockHttpServletRequest) fixture.action.request).setParameter("hourOfEncounterTime", "3");
        var existingIssues = fixture.form.getIssueCheckList();
        var locks = mock(CasemgmtNoteLockDao.class);
        injectDependency(fixture.action, "casemgmtNoteLockDao", locks);
        var captured = new CasemgmtNoteLock();
        captured.setId(75L);
        captured.setNoteId(501L);
        captured.setSessionId("rotatedSession".equals(scenario) ? "previous-session" : fixture.action.request.getSession().getId());
        if (!"missingSession".equals(scenario)) {
            fixture.action.request.getSession().setAttribute("casemgmtNoteLock101", captured);
        }
        if (!"missingDatabase".equals(scenario)) {
            var stored = new CasemgmtNoteLock();
            stored.setSessionId("takenOver".equals(scenario) ? "another-session" : captured.getSessionId());
            when(locks.find(75L)).thenReturn(stored);
        }

        assertThat(fixture.action.issueDelete()).isEqualTo("windowCloseError");

        assertThat(fixture.action.response.getStatus()).isEqualTo(409);
        verify(fixture.manager).hasAccessRight("note-read-only", "access", "999998", "101", "0");
        verifyNoMoreInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList()).isSameAs(existingIssues).hasSize(1);
        assertThat(note.getNote()).isEqualTo("Existing clinical text");
        assertThat(note.getHourOfEncounterTime()).isEqualTo(1);
        assertThat(fixture.action.request.getAttribute("change_flag")).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "\t\r\n"})
    void shouldRefuseIssueDeletionBeforeAnyMutation_whenTheRequiredNoteTextIsBlank(String text) throws Exception {
        var fixture = issueFixture(true);
        var permissions = (SecurityInfoManager) org.springframework.test.util.ReflectionTestUtils
                .getField(fixture.action, "securityInfoManager");
        var user = LoggedInInfo.getLoggedInInfoFromSession(fixture.action.request);
        when(permissions.hasPrivilege(user, "_eChart", "w", "101")).thenReturn(true);
        when(permissions.hasPrivilege(user, "_casemgmt.notes", "w", "101")).thenReturn(true);
        CaseManagementNote note = new CaseManagementNote();
        note.setId(501L);
        note.setDemographic_no("101");
        note.setNote("Existing clinical text");
        fixture.form.setCaseNote(note);
        fixture.action.setDeleteId("0");
        doReturn(text).when(fixture.action).getCaseNote_note();
        var captured = new CasemgmtNoteLock();
        captured.setId(75L);
        captured.setNoteId(501L);
        captured.setSessionId(fixture.action.request.getSession().getId());
        fixture.action.request.getSession().setAttribute("casemgmtNoteLock101", captured);
        var locks = mock(CasemgmtNoteLockDao.class);
        when(locks.find(75L)).thenReturn(captured);
        injectDependency(fixture.action, "casemgmtNoteLockDao", locks);
        var existingIssues = fixture.form.getIssueCheckList();

        assertThat(fixture.action.issueDelete()).isEqualTo("none");

        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        verify(fixture.manager).hasAccessRight("note-read-only", "access", "999998", "101", "0");
        verifyNoMoreInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList()).isSameAs(existingIssues).hasSize(1);
        assertThat(fixture.form.getCaseNote()).isSameAs(note);
        assertThat(note.getNote()).isEqualTo("Existing clinical text");
        assertThat(fixture.action.request.getAttribute("change_flag")).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"issueChange", "issueDelete", "ajaxChangeDiagnosis", "submitChangeDiagnosis"})
    void shouldRefuseIssueMutationWithoutModelMutation_whenFreshIssuePermissionIsDenied(String operation) {
        var fixture = issueFixture(true);
        fixture.action.request.getSession().setAttribute(
                io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.class.getName() + ".101", "23");
        var rendered = fixture.form.getIssueCheckList().get(0).getIssue();
        var fresh = spy(new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue());
        fresh.setId(91L);
        fresh.setDemographic_no(101);
        doReturn(false).when(fresh).isWriteAccess("999998", 23);
        var issues = (io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO)
                org.springframework.test.util.ReflectionTestUtils.getField(fixture.action, "caseManagementIssueDao");
        when(issues.findById(91L)).thenReturn(fresh);

        var note = new CaseManagementNote();
        note.setDemographic_no("101");
        fixture.form.setCaseNote(note);
        fixture.action.setDeleteId("0");
        fixture.action.setIssueCheckList(fixture.form.getIssueCheckList());
        ((MockHttpServletRequest) fixture.action.request).setParameter("change_diagnosis_id", "0");
        ((MockHttpServletRequest) fixture.action.request).setParameter("newIssueId", "52");
        doReturn("").when(fixture.action).getDemoName(anyString());
        doReturn("").when(fixture.action).getDemoAge(anyString());
        doReturn("").when(fixture.action).getDemoDOB(anyString());
        ThrowingCallable mutation = switch (operation) {
            case "issueChange" -> fixture.action::issueChange;
            case "issueDelete" -> fixture.action::issueDelete;
            case "ajaxChangeDiagnosis" -> fixture.action::ajaxChangeDiagnosis;
            default -> fixture.action::submitChangeDiagnosis;
        };
        assertThatThrownBy(mutation).isInstanceOf(SecurityException.class);

        verify(fresh).isWriteAccess("999998", 23);
        verifyNoInteractions(fixture.manager);
        assertThat(fresh.isResolved()).isFalse();
        assertThat(rendered.isResolved()).isFalse();
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue()).isSameAs(rendered);
    }

    record Fixture(CaseManagementEntry2Action action,CaseManagementManager manager,CaseManagementEntryFormBean form) {}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" "})
    void shouldUseAuthenticatedDefaultProgram_whenLegacyEditorHasNoCapturedProgram(String capturedProgram) {
        var fixture = diagnosisFixture();
        var request = (MockHttpServletRequest) fixture.action.request;
        request.getSession().removeAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.class.getName() + ".101");
        if (capturedProgram != null) request.getSession().setAttribute(
                io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.class.getName() + ".101", capturedProgram);
        var programs = mock(io.github.carlos_emr.carlos.encounter.data.EctProgramManager.class);
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of(new io.github.carlos_emr.carlos.util.LabelValueBean("Default", "23")));
        when(programs.getDefaultProgramId("999998")).thenReturn(23);
        var context = mock(org.springframework.web.context.WebApplicationContext.class);
        when(context.getBean("ectProgramManager")).thenReturn(programs);
        request.getServletContext().setAttribute(org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        var issue = fixture.form.getIssueCheckList().get(0).getIssue();
        doReturn(true).when(issue).isWriteAccess("999998", 23);
        fixture.action.setNewIssueCheckList(null);
        assertThat(fixture.action.submitChangeDiagnosis()).isEqualTo("view");
        verify(issue).isWriteAccess("999998", 23);
        verify(issue, never()).isWriteAccess("999998", 0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldPersistDiagnosisReplacement_whenCurrentIssueWriteIsAllowed(boolean ajax) {
        var fixture = diagnosisFixture();
        var replacement = new io.github.carlos_emr.carlos.casemgmt.model.Issue();
        replacement.setId(52L);
        replacement.setRole("nurse");
        replacement.setDescription("Replacement diagnosis");
        replacement.setCode("R52");
        when(fixture.manager.getIssue("52")).thenReturn(replacement);

        assertThat(ajax ? fixture.action.ajaxChangeDiagnosis() : fixture.action.submitChangeDiagnosis())
                .isEqualTo(ajax ? "issueList_ajax" : "view");

        var issue = fixture.form.getIssueCheckList().get(0).getIssue();
        verify(issue).isWriteAccess("999998", 0);
        verify(fixture.manager).saveCaseIssue(issue);
        assertThat(issue.getIssue()).isSameAs(replacement);
        assertThat(issue.getIssue_id()).isEqualTo(52L);
        if (ajax) assertThat(fixture.form.getCaseNote().getIssues()).containsExactly(issue);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldRetainDiagnosisWithoutWriting_whenReplacementDoesNotExist(boolean ajax) {
        var fixture = diagnosisFixture();
        var issue = fixture.form.getIssueCheckList().get(0).getIssue();
        var original = issue.getIssue();

        assertThat(ajax ? fixture.action.ajaxChangeDiagnosis() : fixture.action.submitChangeDiagnosis()).isEqualTo("none");

        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        assertThat(issue.getIssue()).isSameAs(original);
        verify(fixture.manager, never()).saveCaseIssue(any());
        verify(fixture.manager, never()).changeIssueInCPP(any(), any(), any());
    }

    private Fixture diagnosisFixture() {
        var fixture = issueFixture(true);
        var memberships = createAndRegisterMock(io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO.class);
        var access = createAndRegisterMock(io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO.class);
        var role = new io.github.carlos_emr.carlos.model.security.Secrole();
        role.setId(22L);
        role.setRoleName("nurse");
        var membership = new io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider();
        membership.setRole(role);
        when(memberships.getProgramProviderByProviderProgramId("999998", 0L)).thenReturn(List.of(membership));
        when(access.getAccessListByProgramId(0L)).thenReturn(List.of());
        fixture.form.setCaseNote(new CaseManagementNote());
        fixture.form.getIssueCheckList().get(0).setChecked("on");
        var original = new io.github.carlos_emr.carlos.casemgmt.model.Issue();
        original.setDescription("Original diagnosis");
        original.setRole("nurse");
        fixture.form.getIssueCheckList().get(0).getIssue().setIssue(original);
        fixture.action.setIssueCheckList(fixture.form.getIssueCheckList());
        var selected = new CheckIssueBoxBean();
        selected.setChecked(true);
        selected.getIssue().setId(52L);
        fixture.action.setNewIssueCheckList(new CheckIssueBoxBean[]{selected});
        ((MockHttpServletRequest) fixture.action.request).setParameter("change_diagnosis_id", "0");
        ((MockHttpServletRequest) fixture.action.request).setParameter("newIssueId", "52");
        doReturn("").when(fixture.action).getDemoName(anyString());
        doReturn("").when(fixture.action).getDemoAge(anyString());
        doReturn("").when(fixture.action).getDemoDOB(anyString());
        return fixture;
    }

    Fixture issueFixture(boolean accessible) {
        var action=mock(CaseManagementEntry2Action.class,CALLS_REAL_METHODS);
        var request=new MockHttpServletRequest("POST","/CaseManagementEntry");action.request=request;
        action.response=new MockHttpServletResponse();request.setParameter("demographicNo","101");
        request.setParameter("issueCheckList[0].issue.resolved","true");request.getSession().setAttribute("userrole","doctor");
        var user=mock(LoggedInInfo.class);when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),user);
        var permissions=mock(SecurityInfoManager.class);
        when(permissions.hasPrivilege(user,"_demographic","r","101")).thenReturn(true);
        when(permissions.hasPrivilege(user,"_casemgmt.issues","w","101")).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(user,101)).thenReturn(accessible);
        injectDependency(action,"securityInfoManager",permissions);
        var manager=mock(CaseManagementManager.class);action.caseManagementMgr=manager;
        var form=new CaseManagementEntryFormBean();var box=new CheckBoxBean();
        var issue = spy(box.getIssue());
        issue.setId(91L);
        issue.setDemographic_no(101);
        doReturn(true).when(issue).isWriteAccess("999998", 0);
        box.setIssue(issue);
        var issues = mock(io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO.class);
        when(issues.findById(91L)).thenReturn(issue);
        injectDependency(action, "caseManagementIssueDao", issues);
        form.setIssueCheckList(new ArrayList<>(List.of(box)));
        request.getSession().setAttribute("caseManagementEntryForm101",form);action.setLineId("0");
        request.getSession().setAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.class.getName() + ".101", "0");
        return new Fixture(action,manager,form);
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void shouldRefuseUnwritableReplacementBeforeMutation_whenOnlyTheCurrentRoleIsAllowed(boolean ajax) {
        var fixture = diagnosisFixture();
        var original = fixture.form.getIssueCheckList().get(0).getIssue();
        var diagnosis = original.getIssue();
        var replacement = new io.github.carlos_emr.carlos.casemgmt.model.Issue();
        replacement.setId(52L);
        replacement.setRole("doctor");
        when(fixture.manager.getIssue("52")).thenReturn(replacement);
        org.assertj.core.api.ThrowableAssert.ThrowingCallable change = ajax
                ? fixture.action::ajaxChangeDiagnosis : fixture.action::submitChangeDiagnosis;
        assertThatThrownBy(change)
                .isInstanceOf(SecurityException.class);
        assertThat(original.getIssue()).isSameAs(diagnosis);
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue()).isSameAs(original);
        verify(fixture.manager, never()).saveCaseIssue(any());
        verify(fixture.manager, never()).changeIssueInCPP(any(), any(), any());
    }

    @Test
    void shouldRejectMissingSessionNoteBeforeMutation_whenChangingDiagnosisViaAjax() {
        var fixture = diagnosisFixture();
        fixture.form.setCaseNote(null);
        assertThat(fixture.action.ajaxChangeDiagnosis()).isEqualTo("none");
        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        verify(fixture.manager, never()).saveCaseIssue(any());
    }

    @Test
    void shouldIgnoreMissingAndSparseSelections_whenNoReplacementWasSelected() {
        var fixture = diagnosisFixture();
        var unchecked = new CheckIssueBoxBean();
        var missingIssue = mock(CheckIssueBoxBean.class);
        when(missingIssue.isChecked()).thenReturn(true);
        when(missingIssue.getIssue()).thenReturn(null);
        for (CheckIssueBoxBean[] choices : new CheckIssueBoxBean[][]{null, {null}, {unchecked, missingIssue}}) {
            fixture.action.setNewIssueCheckList(choices);
            assertThat(fixture.action.submitChangeDiagnosis()).isEqualTo("view");
        }
        verify(fixture.manager, never()).saveCaseIssue(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing-selection", "missing-id", "deleted", "other-patient"})
    void shouldRefuseStaleDiagnosisSelectionWithoutWrites_whenStoredIdentityIsUnavailable(String scenario) {
        var fixture = diagnosisFixture();
        var original = fixture.form.getIssueCheckList().get(0).getIssue();
        var dao = (io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO)
                org.springframework.test.util.ReflectionTestUtils.getField(fixture.action, "caseManagementIssueDao");
        switch (scenario) {
            case "missing-selection" -> {
                var detachedSelection = new CheckBoxBean();
                detachedSelection.setIssue(null);
                // Represent an already-restored editor snapshot, independently of the current session form.
                injectDependency(fixture.action, "issueCheckList", List.of(detachedSelection));
            }
            case "missing-id" -> original.setId(null);
            case "deleted" -> when(dao.findById(91L)).thenReturn(null);
            default -> {
                var otherPatient = new io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue();
                otherPatient.setDemographic_no(202);
                when(dao.findById(91L)).thenReturn(otherPatient);
            }
        }

        assertThatThrownBy(fixture.action::submitChangeDiagnosis).isInstanceOf(SecurityException.class);

        verifyNoInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue()).isSameAs(original);
        assertThat(original.isResolved()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"submit,not-an-index", "submit,-1", "submit,1",
            "ajax,not-an-index", "ajax,-1", "ajax,1", "status,not-an-index", "status,-1", "status,1"})
    void shouldRejectInvalidIssueIndexWithoutWrites_whenChangingDiagnosisOrStatus(String operation, String index) throws Exception {
        var fixture = diagnosisFixture();
        ((MockHttpServletRequest) fixture.action.request).setParameter("change_diagnosis_id", index);
        fixture.action.setLineId(index);

        String result = switch (operation) {
            case "submit" -> fixture.action.submitChangeDiagnosis();
            case "ajax" -> fixture.action.ajaxChangeDiagnosis();
            default -> fixture.action.issueChange();
        };

        assertThat(result).isEqualTo("none");
        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        verifyNoInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"submit,missing-list", "ajax,missing-form", "ajax,missing-list",
            "status,missing-form", "status,missing-list"})
    void shouldRejectMissingIssueEditorWithoutWrites_whenTheSessionOrBoundListIsStale(String operation, String scenario) throws Exception {
        var fixture = diagnosisFixture();
        if (scenario.equals("missing-form")) fixture.action.request.getSession().removeAttribute("caseManagementEntryForm101");
        else if (operation.equals("submit")) injectDependency(fixture.action, "issueCheckList", null);
        else fixture.form.setIssueCheckList(null);

        String result = switch (operation) {
            case "submit" -> fixture.action.submitChangeDiagnosis();
            case "ajax" -> fixture.action.ajaxChangeDiagnosis();
            default -> fixture.action.issueChange();
        };

        assertThat(result).isEqualTo("none");
        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        verifyNoInteractions(fixture.manager);
    }

    @Test
    void shouldLeaveDiagnosisUnchanged_whenAjaxHasNoReplacement() {
        var fixture = diagnosisFixture();
        ((MockHttpServletRequest) fixture.action.request).removeParameter("newIssueId");
        assertThat(fixture.action.ajaxChangeDiagnosis()).isEqualTo("issueList_ajax");
        verify(fixture.manager, never()).saveCaseIssue(any());
    }

    @Test
    void shouldRejectInvalidDurationBeforeChangingIssueStatus() throws Exception {
        var fixture = diagnosisFixture();
        ((MockHttpServletRequest) fixture.action.request).setParameter("hourOfEncounterTime", "invalid");
        assertThat(fixture.action.issueChange()).isEqualTo("none");
        assertThat(fixture.action.response.getStatus()).isEqualTo(400);
        verifyNoInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isFalse();
    }

    @Test
    void shouldReturnForbiddenWithoutWriting_whenTheIssueEditorSessionRoleHasExpired() throws Exception {
        var fixture = issueFixture(true);
        fixture.action.request.getSession().removeAttribute("userrole");

        assertThat(fixture.action.issueChange()).isEqualTo("none");

        assertThat(fixture.action.response.getStatus()).isEqualTo(403);
        verifyNoInteractions(fixture.manager);
        assertThat(fixture.form.getIssueCheckList().get(0).getIssue().isResolved()).isFalse();
    }

    @Test
    void shouldPersistAllIssueFlagsWithoutANote_whenIssueOnlyEditingIsAuthorized() throws Exception {
        var fixture = diagnosisFixture();
        fixture.form.setCaseNote(null);
        var request = (MockHttpServletRequest) fixture.action.request;
        request.setParameter("issueCheckList[0].issue.acute", "true");
        request.setParameter("issueCheckList[0].issue.major", "true");

        assertThat(fixture.action.issueChange()).isEqualTo("view");

        var box = fixture.form.getIssueCheckList().get(0);
        assertThat(box.getIssue().isAcute()).isTrue();
        assertThat(box.getIssue().isMajor()).isTrue();
        assertThat(box.getIssueDisplay().getAcute()).isEqualTo("acute");
        assertThat(box.getIssueDisplay().getMajor()).isEqualTo("major");
        verify(fixture.manager).saveAndUpdateCaseIssues(List.of(box.getIssue()));
    }
}
