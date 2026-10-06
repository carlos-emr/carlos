/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Read methods must stop before loading notes, changing locks, or persisting chart state. */
@Tag("unit")
class CaseManagementNoteMethodUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"GET, issue", "HEAD, issue", "PUT, issue", "DELETE, issue",
            "GET, json", "HEAD, json", "PUT, json", "DELETE, json",
            "GET, tickler", "HEAD, tickler", "PUT, tickler", "DELETE, tickler"})
    void shouldRefuseBeforeDataAccess_whenNoteSaveIsNotPost(String method, String target) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        var notes = mock(CaseManagementNoteDAO.class);
        var issues = mock(CaseManagementIssueDAO.class);
        var extensions = mock(CaseManagementNoteExtDAO.class);
        var issueDefinitions = mock(IssueDAO.class);
        var locks = mock(CasemgmtNoteLockDao.class);
        var ticklers = mock(TicklerManager.class);
        var security = mock(SecurityInfoManager.class);
        registerMock(CaseManagementNoteDAO.class, notes);
        registerMock(CaseManagementIssueDAO.class, issues);
        registerMock(CaseManagementNoteExtDAO.class, extensions);
        registerMock(IssueDAO.class, issueDefinitions);
        registerMock(CasemgmtNoteLockDao.class, locks);
        registerMock(TicklerManager.class, ticklers);
        registerMock(SecurityInfoManager.class, security);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new CaseManagementEntry2Action();
            String result = switch (target) {
                case "issue" -> action.issueNoteSave();
                case "json" -> action.issueNoteSaveJson();
                case "tickler" -> action.ticklerSaveNote();
                default -> throw new IllegalArgumentException(target);
            };
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(notes, issues, extensions, issueDefinitions, locks, ticklers, security);
        }
    }
    @ParameterizedTest
    @CsvSource({"missing", "takenOver", "raced"})
    void shouldReturnConflictBeforeLoadingOrSavingNote_whenLockIsMissingOrTakenOver(String state) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("demographicNo", "42");
        request.getSession().setAttribute("userrole", "doctor");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var locks = mock(CasemgmtNoteLockDao.class);
        registerMock(CasemgmtNoteLockDao.class, locks);
        if (!"missing".equals(state)) {
            var owned = mock(io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock.class);
            when(owned.getId()).thenReturn(7L);
            when(owned.getSessionId()).thenReturn(request.getSession().getId());
            request.getSession().setAttribute("casemgmtNoteLock42", owned);
            var current = mock(io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock.class);
            when(current.getSessionId()).thenReturn("other-session");
            if ("raced".equals(state)) {
                when(locks.find(7L)).thenReturn(owned, current);
                var login = mock(io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
                io.github.carlos_emr.carlos.utility.LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
            } else {
                when(locks.find(7L)).thenReturn(current);
            }
        }
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = spy(new CaseManagementEntry2Action());
            if ("raced".equals(state)) {
                doReturn("patient").when(action).getDemoName("42");
                doReturn("40").when(action).getDemoAge("42");
                doReturn("1986-01-01").when(action).getDemoDOB("42");
            }
            assertThat(action.save()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(409);
            if (!"raced".equals(state)) verify(action, never()).getDemoName(anyString());
        }
    }

}
