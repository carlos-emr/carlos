/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class RxWriteToEncounter2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private CaseManagementManager notes;
    private CaseManagementTmpSaveDao tmpDao;
    private LoggedInInfo login;
    private RxSessionBean sessionBean;
    private CaseManagementNote note;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedConstruction<EctProgram> program;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/WriteToEncounter");
        response = new MockHttpServletResponse();
        request.addParameter("expectedDemographicNo", "42");
        request.addParameter("body", "exact prescription text");
        request.getSession().setAttribute("user", "999998");
        request.getSession().setAttribute("case_program_id", "0");
        sessionBean = new RxSessionBean();
        sessionBean.setDemographicNo(42);
        request.getSession().setAttribute("RxSessionBean", sessionBean);
        security = mock(SecurityInfoManager.class);
        notes = mock(CaseManagementManager.class);
        tmpDao = mock(CaseManagementTmpSaveDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(login), eq("_rx"), eq("w"), nullable(String.class))).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        registerMock(CaseManagementManager.class, notes);
        registerMock(CaseManagementTmpSaveDao.class, tmpDao);
        note = new CaseManagementNote();
        note.setNote("existing text");
        when(notes.getLastSaved("0", "42", "999998")).thenReturn(note);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        program = mockConstruction(EctProgram.class, (mock, context) ->
                when(mock.getProgram(anyString())).thenReturn("0"));
    }

    @AfterEach
    void tearDown() {
        program.close();
        loggedIn.close();
        servlet.close();
    }

    @Test
    void rejectsChangedPatientBeforeAnyNoteAccess() throws Exception {
        sessionBean.setDemographicNo(43);
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void requiresOriginatingPatientEvenWhenSessionExists() throws Exception {
        request.removeParameter("expectedDemographicNo");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void rejectsMissingRxSessionWithoutLoginRedirect() throws Exception {
        request.getSession().removeAttribute("RxSessionBean");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void rejectsNonPostBeforeNoteAccess() throws Exception {
        request.setMethod("GET");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void rejectsAnonymousBeforeMethodDetails() {
        request.setMethod("GET");
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        assertThatThrownBy(() -> new RxWriteToEncounter2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(security, notes, tmpDao);
    }

    @Test
    void rejectsPatientScopedWriteDenialBeforeNoteAccess() {
        when(security.hasPrivilege(login, "_rx", "w", "42")).thenReturn(false);
        assertThatThrownBy(() -> new RxWriteToEncounter2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void acknowledgesOnlyAfterSavingTheBoundPatientsNote() throws Exception {
        new RxWriteToEncounter2Action().execute();
        verify(notes).saveNoteSimple(note);
        assertThat(note.getNote()).isEqualTo("existing text\nexact prescription text");
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @Test
    void postSaveCleanupFailureNeverClaimsNoWrite() {
        CaseManagementTmpSave tmp = mock(CaseManagementTmpSave.class);
        when(tmp.getNoteId()).thenReturn(7);
        when(tmp.getUpdateDate()).thenReturn(new Date(1));
        when(tmp.getNote()).thenReturn("draft text");
        when(tmp.getProviderNo()).thenReturn("999998");
        when(tmp.getDemographicNo()).thenReturn(42);
        when(tmp.getProgramId()).thenReturn(0);
        note.setUpdate_date(new Date(2));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(tmp);
        when(notes.getNote("7")).thenReturn(note);
        doThrow(new IllegalStateException("cleanup failed after commit"))
                .when(tmpDao).remove("999998", 42, 0);
        assertThatThrownBy(() -> new RxWriteToEncounter2Action().execute()).isInstanceOf(IllegalStateException.class);
        verify(notes).saveNoteSimple(note);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isNull();
    }
}
