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

    record Fixture(CaseManagementEntry2Action action,CaseManagementManager manager,CaseManagementEntryFormBean form) {}
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
        box.getIssue().setDemographic_no(101);form.setIssueCheckList(new ArrayList<>(List.of(box)));
        request.getSession().setAttribute("caseManagementEntryForm101",form);action.setLineId("0");
        request.getSession().setAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.class.getName() + ".101", "0");
        return new Fixture(action,manager,form);
    }

}
